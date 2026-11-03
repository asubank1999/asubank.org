import SwiftUI

/// Files this phone uploaded, shown in Latest uploads right away (before Drive indexes them)
enum MyUploads {
    struct Item: Codable { var name: String; var url: String; var date: String }
    private static let key = "ab.myUploads.v1"

    static var all: [Item] {
        guard let d = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([Item].self, from: d)) ?? []
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
              let (data, _) = try? await URLSession.shared.data(from: url),
              let html = String(data: data, encoding: .utf8) else { return [] }
        return DriveFolderList.parse(html).compactMap { e in
            if e.name.hasPrefix("[TEST]") { return nil }
            let d = isoDate(e.modified)
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
        if !force, let t = loadedAt, Date().timeIntervalSince(t) < 600, !files.isEmpty { return }
        state = files.isEmpty ? 1 : state
        async let folder = UploadFolder.load()
        var indexed: [DriveSearch.Result] = []
        var failed = false
        do {
            let url = URL(string: "\(Self.api)?t=\(Int(Date().timeIntervalSince1970))&sort=date&order=desc")!
            let (data, _) = try await URLSession.shared.data(from: url)
            let r = try JSONDecoder().decode(Reply.self, from: data)
            guard r.success == true, let list = r.files else { throw URLError(.cannotParseResponse) }
            indexed = list.filter { DriveLinks.shared.isBroken($0.url) == nil }.map { f in
                let k = DriveSearch.kind(mime: f.mimeType, url: f.url, name: f.name)
                return DriveSearch.Result(m: Material(u: f.url, t: f.name, s: "Latest uploads", d: f.dateUploaded), kind: k, date: f.dateUploaded ?? "", size: f.size ?? 0)
            }
        } catch {
            failed = true
        }
        let mine = MyUploads.all.map {
            DriveSearch.Result(m: Material(u: $0.url, t: $0.name, s: "Your upload", d: $0.date), kind: AB.kind($0.url, $0.name), date: $0.date, size: 0)
        }
        let fresh = await folder
        // your uploads first, then the upload folder, then the indexed Drive; each file once
        var seen = Set<String>()
        let merged = (mine + fresh + indexed).filter { seen.insert(AB.driveId($0.m.u) ?? $0.m.u).inserted }
        if merged.isEmpty {
            if failed && files.isEmpty { state = 3 }
            return
        }
        files = merged.sorted { $0.date > $1.date }
        loadedAt = Date()
        state = 2
    }
}

struct LatestScreen: View {
    @StateObject private var store = LatestStore()
    @State private var query = ""
    @State private var type = "all"

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
            Section {
                ChipBar(options: types, selection: $type)
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            }
            switch store.state {
            case 0, 1:
                HStack { Spacer(); ProgressView("Loading the newest files…"); Spacer() }.padding(30).listRowBackground(Color.clear)
            case 3:
                VStack(spacing: 10) {
                    Text("Couldn't load the latest files.").font(.headline)
                    Button("Try again") { Task { await store.load(force: true) } }.buttonStyle(.borderedProminent).tint(.abAccent)
                }
                .frame(maxWidth: .infinity).padding(20).listRowBackground(Color.clear)
            default:
                ForEach(groups, id: \.0) { g in
                    Section("\(g.0) · \(g.1.count)") {
                        ForEach(g.1) { r in
                            MaterialRow(m: r.m, kind: r.kind, meta: meta(r))
                        }
                    }
                }
                if filtered.isEmpty {
                    EmptyState(symbol: "doc.text.magnifyingglass", title: "No files", text: "Nothing matches these filters.").listRowBackground(Color.clear)
                }
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $query, prompt: "Search latest uploads")
        .navigationTitle("Latest uploads")
        .refreshable { await store.load(force: true) }
        .task { await store.load() }
    }

    private func meta(_ r: DriveSearch.Result) -> String {
        var parts: [String] = []
        if r.size > 0 { parts.append(ByteCountFormatter.string(fromByteCount: Int64(r.size), countStyle: .file)) }
        if let d = AB.date(r.date) { parts.append(d.formatted(.relative(presentation: .named))) }
        return parts.joined(separator: " · ")
    }
}
