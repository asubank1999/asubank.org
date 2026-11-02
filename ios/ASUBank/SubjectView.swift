import SwiftUI

/// One subject's materials with filter chips and in-page search (like /banks/* pages)
struct SubjectView: View {
    let subject: Subject
    @EnvironmentObject private var library: Library
    @EnvironmentObject private var saved: SavedStore
    @State private var filter = "all"
    @State private var query = ""

    private var all: [IndexedMaterial] { library.items(in: subject) }

    private var options: [(String, String)] {
        var o: [(String, String)] = [("all", "All \(all.count)")]
        let folders = all.filter { $0.kind == .folder }.count
        if folders > 0 { o.append(("folder", "Folders \(folders)")) }
        if folders < all.count { o.append(("file", "Files \(all.count - folders)")) }
        let new = all.filter { $0.m.tag == "new" }.count
        if new > 0 { o.append(("new", "New \(new)")) }
        if all.contains(where: { saved.isSaved($0.m.u) }) { o.append(("saved", "★ Saved")) }
        for y in Array(Set(all.map(\.year).filter { !$0.isEmpty })).sorted(by: >) { o.append(("y" + y, y)) }
        return o
    }

    private var shown: [IndexedMaterial] {
        var list = all
        switch filter {
        case "folder": list = list.filter { $0.kind == .folder }
        case "file": list = list.filter { $0.kind != .folder }
        case "new": list = list.filter { $0.m.tag == "new" }
        case "saved": list = list.filter { saved.isSaved($0.m.u) }
        case let f where f.hasPrefix("y"): list = list.filter { $0.year == String(f.dropFirst()) }
        default: break
        }
        let q = AB.norm(query)
        if !q.isEmpty {
            let toks = q.split(separator: " ").map(String.init)
            list = list.filter { it in toks.allSatisfy { it.q.contains($0) } }
        }
        return list
    }

    var body: some View {
        List {
            Section {
                ChipBar(options: options, selection: $filter)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            } header: {
                if let ar = subject.ar { Text(ar).font(.subheadline).textCase(nil) }
            }
            Section {
                ForEach(shown) { it in
                    MaterialRow(m: it.m, kind: it.kind, showSource: false)
                }
            } footer: {
                if shown.isEmpty {
                    EmptyState(symbol: "doc.text.magnifyingglass", title: "Nothing here", text: query.isEmpty ? "No files match this filter." : "No files match “\(query)”.")
                } else {
                    Text("\(shown.count) of \(all.count) · Long-press a file to save or share it.")
                }
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $query, prompt: "Search in \(subject.t)")
        .navigationTitle("\(subject.e ?? "") \(subject.t)")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let url = AB.absolute(subject.p) {
                    ShareLink(item: url, subject: Text(subject.t)) { Image(systemName: "square.and.arrow.up") }
                }
            }
        }
        .refreshable { await library.load() }
    }
}
