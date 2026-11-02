import SwiftUI

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
        do {
            let url = URL(string: "\(Self.api)?t=\(Int(Date().timeIntervalSince1970))&sort=date&order=desc")!
            let (data, _) = try await URLSession.shared.data(from: url)
            let r = try JSONDecoder().decode(Reply.self, from: data)
            guard r.success == true, let list = r.files else { throw URLError(.cannotParseResponse) }
            files = list.map { f in
                let mime = f.mimeType ?? ""
                let k: Kind = mime.contains("folder") ? .folder : mime.contains("pdf") ? .pdf : AB.kind(f.url, f.name)
                return DriveSearch.Result(m: Material(u: f.url, t: f.name, s: "Latest uploads", d: f.dateUploaded), kind: k, date: f.dateUploaded ?? "", size: f.size ?? 0)
            }
            .sorted { $0.date > $1.date }
            loadedAt = Date()
            state = 2
        } catch {
            if files.isEmpty { state = 3 }
        }
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
