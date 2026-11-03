import SwiftUI

/// Instant search over every subject page, plus Google Drive results (like /guides/search)
struct SearchView: View {
    @EnvironmentObject private var app: AppState
    @EnvironmentObject private var library: Library
    @EnvironmentObject private var saved: SavedStore
    @State private var query = ""
    @State private var type = "all"
    @State private var year = ""
    @State private var source = "both"
    @State private var drive: [DriveSearch.Result] = []
    @State private var driveState = DriveState.idle
    @State private var limit = 60

    enum DriveState: Equatable { case idle, loading, done, failed }

    private let types: [(String, String)] = [
        ("all", "All"), ("pdf", "PDF"), ("ppt", "Slides"), ("doc", "Docs"), ("sheet", "Sheets"), ("img", "Images"), ("folder", "Folders"), ("video", "Videos")
    ]

    private func pass(_ k: Kind, _ y: String) -> Bool {
        (type == "all" || k.rawValue == type) && (year.isEmpty || y == year)
    }

    private var local: [IndexedMaterial] {
        // the same link is often listed on several subject pages: show it once
        var seen = Set<String>()
        return source == "drive" ? [] : AB.match(library.items, query, limit: 600).filter { pass($0.kind, $0.year) && seen.insert(AB.driveId($0.m.u) ?? $0.m.u).inserted }
    }

    private var driveShown: [DriveSearch.Result] {
        var seen = Set(local.map { AB.driveId($0.m.u) ?? $0.m.u })
        return source == "pages" ? [] : drive.filter { pass($0.kind, $0.year) && seen.insert(AB.driveId($0.m.u) ?? $0.m.u).inserted }
    }

    var body: some View {
        ABStack {
            List {
                if query.trimmingCharacters(in: .whitespaces).isEmpty {
                    idle
                } else {
                    Section {
                        ChipBar(options: types, selection: $type)
                            .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                    }
                    let l = local
                    if !l.isEmpty {
                        Section {
                            ForEach(l.prefix(limit)) { it in MaterialRow(m: it.m, kind: it.kind) }
                            if l.count > limit {
                                Button("Show \(min(60, l.count - limit)) more") { limit += 60 }
                            }
                        } header: { Text("On subject pages · \(l.count)") }
                    }
                    if source != "pages" { driveSection }
                    if l.isEmpty && driveShown.isEmpty && driveState != .loading && driveState != .failed {
                        EmptyState(symbol: "magnifyingglass", title: "No results", text: "Try fewer words, another spelling, or Arabic/English. You can also request this material.")
                            .listRowBackground(Color.clear)
                        RouteLink(Route.web("Request a material", "/requests/student_request_portal")) {
                            Label("Request “\(query)”", systemImage: "tray.and.arrow.down")
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Search")
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Subjects, courses, doctors, years…")
            .autocorrectionDisabled()
            .onSubmit(of: .search) { saved.searched(query) }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) { filterMenu }
            }
            .task(id: query) { await runDrive() }
            .onChange(of: query) { _ in limit = 60 }
            .onAppear(perform: takePending)
            .onChange(of: app.pendingQuery) { _ in takePending() }
            .abDestinations()
        }
    }

    @ViewBuilder private var idle: some View {
        if !saved.searches.isEmpty {
            Section {
                ForEach(saved.searches, id: \.self) { s in
                    Button { query = s } label: {
                        Label(s, systemImage: "clock.arrow.circlepath").foregroundStyle(.primary)
                    }
                }
            } header: {
                HStack { Text("Recent searches"); Spacer(); Button("Clear") { saved.clearSearches() }.font(.footnote).textCase(nil) }
            }
        }
        Section("Try") {
            ForEach(["Calculus final", "Pharmacology", "Physics lab", "Anatomy midterm", "ملخص", "2025"], id: \.self) { s in
                Button { query = s; saved.searched(s) } label: {
                    Label(s, systemImage: "magnifyingglass").foregroundStyle(.primary)
                }
            }
        }
        Section {
            EmptyState(symbol: "magnifyingglass.circle", title: "Search \(library.items.count) materials",
                       text: "Results from every subject page appear instantly, then files from Google Drive. Arabic and English both work.")
                .listRowBackground(Color.clear)
        }
    }

    @ViewBuilder private var driveSection: some View {
        Section {
            switch driveState {
            case .loading:
                HStack { ProgressView(); Text("Searching Google Drive…").foregroundStyle(.secondary) }
            case .failed:
                VStack(alignment: .leading, spacing: 8) {
                    Text("Drive search is not answering.").font(.headline)
                    Text("Check your connection and try again.").foregroundStyle(.secondary)
                    Button("Try again") { Task { await runDrive(force: true) } }.buttonStyle(.bordered)
                }
            default:
                ForEach(driveShown) { r in
                    MaterialRow(m: r.m, kind: r.kind, meta: [size(r.size), date(r.date)].filter { !$0.isEmpty }.joined(separator: " · "))
                }
                if driveState == .done && driveShown.isEmpty {
                    Text(drive.isEmpty ? "No Drive files match." : "No Drive files match these filters.").foregroundStyle(.secondary)
                }
            }
        } header: {
            Text(driveState == .done ? "Google Drive · \(driveShown.count)" : "Google Drive")
        }
    }

    private var filterMenu: some View {
        Menu {
            Picker("Source", selection: $source) {
                Text("Pages and Drive").tag("both")
                Text("Subject pages only").tag("pages")
                Text("Google Drive only").tag("drive")
            }
            Picker("Year", selection: $year) {
                Text("Any year").tag("")
                ForEach(library.years, id: \.self) { Text($0).tag($0) }
            }
        } label: {
            Image(systemName: source == "both" && year.isEmpty ? "line.3.horizontal.decrease.circle" : "line.3.horizontal.decrease.circle.fill")
        }
    }

    private func takePending() {
        if let q = app.pendingQuery { query = q; app.pendingQuery = nil }
    }

    private func runDrive(force: Bool = false) async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { drive = []; driveState = .idle; return }
        if !force {
            try? await Task.sleep(nanoseconds: 450_000_000) // wait until typing pauses
            if Task.isCancelled { return }
        }
        driveState = .loading
        do {
            let r = try await DriveSearch.search(q)
            if Task.isCancelled { return }
            drive = r
            driveState = .done
        } catch {
            if Task.isCancelled || (error as? URLError)?.code == .cancelled { return }
            driveState = .failed
        }
    }

    private func size(_ b: Double) -> String {
        b > 0 ? ByteCountFormatter.string(fromByteCount: Int64(b), countStyle: .file) : ""
    }

    private func date(_ s: String) -> String {
        guard s.count >= 10 else { return "" }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        guard let d = f.date(from: String(s.prefix(10))) else { return "" }
        return d.formatted(date: .abbreviated, time: .omitted)
    }
}
