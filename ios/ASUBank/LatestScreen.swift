import SwiftUI

/// Files this phone uploaded, shown in Latest uploads right away (before Drive indexes them)
enum MyUploads {
    struct Item: Codable { var name: String; var url: String; var date: String }
    private static let key = "ab.myUploads.v1"

    static var all: [Item] {
        guard let d = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([Item].self, from: d)) ?? []
    }

    static func remove(_ url: String) {
        UserDefaults.standard.set(try? JSONEncoder().encode(all.filter { $0.url != url }), forKey: key)
    }

    static func add(name: String, url: String) {
        guard !url.isEmpty else { return }
        let now = ISO8601DateFormatter().string(from: Date())
        let list = [Item(name: name, url: url, date: now)] + all.filter { $0.url != url }
        UserDefaults.standard.set(try? JSONEncoder().encode(Array(list.prefix(50))), forKey: key)
    }
}

/// The folder the upload form saves into ("asubank.xyz Upload+"), read like any shared folder.
/// The latest-uploads script indexes the older Drive folders only, so new uploads come from here.
enum UploadFolder {
    static let id = "1ubxr47KfFwm7ocn0ikCpJLp79-dxe8aB"

    /// Drive's folder view shows "3:45 PM" (today), "Nov 2" (this year) or "9/8/25"
    static func isoDate(_ s: String) -> String {
        let t = s.trimmingCharacters(in: .whitespaces)
        let cal = Calendar(identifier: .gregorian)
        let now = Date()
        let day = DateFormatter()
        day.locale = Locale(identifier: "en_US_POSIX")
        day.dateFormat = "yyyy-MM-dd"
        if t.contains(":") { return day.string(from: now) }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "M/d/yy"
        if let d = f.date(from: t) { return day.string(from: d) }
        f.dateFormat = "MMM d yyyy"
        let y = cal.component(.year, from: now)
        if var d = f.date(from: "\(t) \(y)") {
            if d > now, let back = cal.date(byAdding: .year, value: -1, to: d) { d = back }
            return day.string(from: d)
        }
        return ""
    }

    static func load() async -> [DriveSearch.Result] {
        guard let url = URL(string: "https://drive.google.com/embeddedfolderview?id=\(id)"),
              let (data, _) = try? await URLSession.shared.data14(from: url),
              let html = String(data: data, encoding: .utf8) else { return [] }
        return DriveFolderList.parse(html).compactMap { e in
            if e.name.hasPrefix("[TEST]") || e.name.localizedCaseInsensitiveContains("upload-test") { return nil }
            let d = isoDate(e.modified)
            if DriveLinks.shared.isBroken(e.url) != nil { return nil }
            return DriveSearch.Result(m: Material(u: e.url, t: e.name, s: "Shared by students", d: d.isEmpty ? nil : d), kind: e.kind, date: d, size: 0)
        }
    }
}

/// Latest uploads (like /guides/latest): newest files in the shared Google Drive
@MainActor
final class LatestStore: ObservableObject {
    struct File: Decodable {
        var name: String
        var url: String
        var mimeType: String?
        var dateUploaded: String?
        var size: Double?

        enum CodingKeys: String, CodingKey { case name, url, mimeType, dateUploaded, size }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            name = try c.decode(String.self, forKey: .name)
            url = try c.decode(String.self, forKey: .url)
            mimeType = try? c.decode(String.self, forKey: .mimeType)
            dateUploaded = try? c.decode(String.self, forKey: .dateUploaded)
            size = try? c.decode(Double.self, forKey: .size)
        }
    }
    struct Reply: Decodable { var success: Bool?; var files: [File]? }

    @Published private(set) var files: [DriveSearch.Result] = []
    @Published private(set) var state = 0 // 0 idle, 1 loading, 2 done, 3 failed
    private var loadedAt: Date?

    static let api = "https://script.google.com/macros/s/AKfycbyuzws_2LcDC8nb2KU-02swjehyRf1ivY14EbX1qcJC1chCUTLTOAAwtQ5JlXvNcLez/exec"

    func load(force: Bool = false) async {
        if files.isEmpty { let c = Self.readSaved(); if !c.isEmpty { files = c; state = 2 } }
        if !force, let t = loadedAt, Date().timeIntervalSince(t) < 600, !files.isEmpty { return }
        state = files.isEmpty ? 1 : state
        let mine = MyUploads.all.map {
            DriveSearch.Result(m: Material(u: $0.url, t: $0.name, s: "Your upload", d: $0.date), kind: AB.kind($0.url, $0.name), date: $0.date, size: 0)
        }
        // your uploads first, then the upload folder, then the indexed Drive; each file once
        func merge(_ lists: [DriveSearch.Result]...) -> [DriveSearch.Result] {
            var seen = Set<String>()
            return lists.joined().filter { DriveLinks.shared.isBroken($0.m.u) == nil && seen.insert(AB.driveId($0.m.u) ?? $0.m.u).inserted }.sorted { $0.date > $1.date }
        }
        let api = Task { () -> [DriveSearch.Result]? in
            let url = URL(string: "\(Self.api)?t=\(Int(Date().timeIntervalSince1970))&sort=date&order=desc")!
            guard let (data, _) = try? await URLSession.shared.data14(from: url),
                  let r = try? JSONDecoder().decode(Reply.self, from: data), r.success == true, let list = r.files else { return nil }
            return list.filter { DriveLinks.shared.isBroken($0.url) == nil }.map { f in
                let k = DriveSearch.kind(mime: f.mimeType, url: f.url, name: f.name)
                return DriveSearch.Result(m: Material(u: f.url, t: f.name, s: "Latest uploads", d: f.dateUploaded), kind: k, date: f.dateUploaded ?? "", size: f.size ?? 0)
            }
        }
        let removedList = Task { await RemovedFiles.shared.refresh() }
        let fresh = await UploadFolder.load()
        // show the newest uploads right away; the full index (slow to wake up) is merged in when it answers
        if files.isEmpty, !(mine + fresh).isEmpty { files = merge(mine, fresh); state = 2 }
        let indexed = await api.value
        _ = await removedList.value
        let failed = indexed == nil
        let merged = merge(mine, fresh, indexed ?? [])
        if merged.isEmpty {
            if failed && files.isEmpty { state = 3 }
            return
        }
        files = merged.sorted { $0.date > $1.date }
        Self.save(files)
        loadedAt = Date()
        state = 2
    }
}

struct LatestScreen: View {
    @StateObject private var store = LatestStore()
    @State private var query = ""
    @State private var type = "all"
    @ObservedObject private var account = AccountStore.shared
    @State private var deleteError: String?
    @State private var confirming: DriveSearch.Result?

    private let types: [(String, String)] = [("all", "All"), ("pdf", "PDF"), ("ppt", "Slides"), ("doc", "Docs"), ("img", "Images")]

    private var filtered: [DriveSearch.Result] {
        let q = AB.norm(query)
        return store.files.filter { r in
            (type == "all" || r.kind.rawValue == type) && (q.isEmpty || AB.norm(r.m.t).contains(q))
        }
    }

    private var groups: [(String, [DriveSearch.Result])] {
        let now = Date()
        var week: [DriveSearch.Result] = [], month: [DriveSearch.Result] = [], older: [DriveSearch.Result] = []
        for r in filtered.prefix(400) {
            let age = AB.date(r.date).map { now.timeIntervalSince($0) / 86400 } ?? 999
            if age < 7 { week.append(r) } else if age < 30 { month.append(r) } else { older.append(r) }
        }
        return [("This week", week), ("This month", month), ("Earlier", older)].filter { !$0.1.isEmpty }
    }

    var body: some View {
        List {
            Section14 {
                ChipBar(options: types, selection: $type)
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            }
            switch store.state {
            case 0, 1:
                HStack { Spacer(); ABDots("Loading the newest files…"); Spacer() }.padding(30).listRowBackground(Color.clear)
            case 3:
                VStack(spacing: 10) {
                    Text("Couldn't load the latest files.").font(.headline)
                    Button("Try again") { Task { await store.load(force: true) } }.prominentButton14().tint14(.abFill)
                }
                .frame(maxWidth: .infinity).padding(20).listRowBackground(Color.clear)
            default:
                ForEach(groups, id: \.0) { g in
                    Section14("\(g.0) · \(g.1.count)") {
                        ForEach(g.1) { r in
                            MaterialRow(m: r.m, kind: r.kind, meta: meta(r))
                                .swipeActions14(edge: .trailing) {
                                    if account.isAdmin {
                                        Button { confirming = r } label: { Label("Delete", systemImage: "trash") }.tint14(.red)
                                    }
                                }
                        }
                    }
                }
                if filtered.isEmpty {
                    EmptyState(symbol: "doc.text.magnifyingglass", title: "No files", text: "Nothing matches these filters.").listRowBackground(Color.clear)
                }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .searchable14(text: $query, prompt: "Search latest uploads")
        .navigationTitle("Latest uploads")
        .confirmationDialog14(L("Delete from Drive?"), isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }), titleVisibility: .visible, presenting: confirming) { r in
            Button(L("Delete from Drive")) { Task { await deleteUpload(r) } }
        } message: { _ in
            Text(L("It moves to the Drive trash and disappears from UniHub for everyone. A file owned by another Drive account is only removed from UniHub."))
        }
        .alert14("Couldn't delete", isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } })) {
            Button("OK") {}
        } message: { Text(deleteError ?? "") }
        .refreshable14 { await store.load(force: true) }
        .task14 { await store.load() }
    }

    private func meta(_ r: DriveSearch.Result) -> String {
        var parts: [String] = []
        if r.size > 0 { parts.append(ByteCountFormatter.string(fromByteCount: Int64(r.size), countStyle: .file)) }
        if let d = AB.date(r.date) { parts.append(d.rel14()) }
        return parts.joined(separator: " · ")
    }
}

extension LatestScreen {
    func deleteUpload(_ r: DriveSearch.Result) async {
        do {
            try await AdminUploads.delete(r.m.u)
            Haptics.success()
            await store.removeFile(r.m.u)
        } catch {
            deleteError = error.localizedDescription
            Haptics.error()
        }
    }
}

extension LatestStore {
    func removeFile(_ url: String) async {
        files.removeAll { $0.m.u == url }
        MyUploads.remove(url)
        Self.save(files)
    }

    /// The list from last time, so the screen opens with files in it while the new list loads
    private struct SavedFile: Codable { var m: Material; var kind: String; var date: String; var size: Double }
    private static var savedURL: URL { FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("latest-uploads.json") }

    static func readSaved() -> [DriveSearch.Result] {
        guard let d = try? Data(contentsOf: savedURL), let list = try? JSONDecoder().decode([SavedFile].self, from: d) else { return [] }
        return list.filter { DriveLinks.shared.isBroken($0.m.u) == nil }.map {
            DriveSearch.Result(m: $0.m, kind: Kind(rawValue: $0.kind) ?? .file, date: $0.date, size: $0.size)
        }
    }

    static func save(_ files: [DriveSearch.Result]) {
        let list = files.prefix(400).map { SavedFile(m: $0.m, kind: $0.kind.rawValue, date: $0.date, size: $0.size) }
        if let d = try? JSONEncoder().encode(list) { try? d.write(to: savedURL, options: .atomic) }
    }
}
