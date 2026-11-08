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
    @State private var hints = SearchHints()
    @State private var onlineAlt: String?
    @ObservedObject private var help = SearchHelp.shared
    private let narrow = ["Final", "Midterm", "Quiz", "Summary", "Slides", "Test bank"]
    private var otherLanguage: String? { hints.translated ?? onlineAlt }

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
        guard source != "drive" else { return [] }
        var found = AB.match(library.items, query, limit: 600, alts: hints.alts, skip: hints.skip)
        if let alt = otherLanguage { found += AB.match(library.items, alt, limit: 200) }
        return found.filter { pass($0.kind, $0.year) && seen.insert(AB.driveId($0.m.u) ?? $0.m.u).inserted }
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
                    helpSection
                    Section14 {
                        ChipBar(options: types, selection: $type)
                            .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                    }
                    let l = local
                    if !l.isEmpty {
                        Section14 {
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
                        Section14("Still can't find it?") {
                            RouteLink(Route.page("/banks/subjects", "All subjects")) { Label("Browse every subject by college", systemImage: "square.grid.2x2") }
                            Button { app.pendingRoom = "materials"; app.tab = .chat } label: { Label("Ask other students in the chat", systemImage: "bubble.left.and.bubble.right") }
                            RouteLink(Route.web("Request a material", "/requests/student_request_portal")) {
                                Label("Request “\(query)”", systemImage: "tray.and.arrow.down")
                            }
                        }
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle("Search")
            .searchable14(text: $query, prompt: "Subjects, courses, doctors, years…")
            .disableAutocorrection(true)
            .onSubmitSearch14 { saved.searched(query) }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) { filterMenu }
            }
            .task14(id: query) { await runDrive() }
            .task14(id: query) { await refreshHints() }
            .onChange(of: onlineAlt) { alt in if let alt { Task { await addDrive(alt) } } }
            .onChange(of: query) { _ in limit = 60 }
            .onAppear(perform: takePending)
            .onChange(of: app.pendingQuery) { _ in takePending() }
            .abDestinations()
        }
    }

    @ViewBuilder private var idle: some View {
        if !saved.searches.isEmpty {
            Section14 {
                ForEach(saved.searches, id: \.self) { s in
                    Button { query = s } label: {
                        Label(s, systemImage: "clock.arrow.circlepath").fg14(.primary)
                    }
                }
            } header: {
                HStack { Text("Recent searches"); Spacer(); Button("Clear") { saved.clearSearches() }.font(.footnote).textCase(nil) }
            }
        }
        Section14 {
            ForEach(guideTopics, id: \.0) { t in
                Button { query = t.2; saved.searched(t.2) } label: {
                    Label(t.0, systemImage: t.1).fg14(.primary)
                }
            }
            RouteLink(Route.page("/banks/subjects", "All subjects")) { Label("Not sure of the name? Browse by college", systemImage: "square.grid.2x2") }
        } header: { Text("What are you looking for?") } footer: {
            Text("Type the course name in Arabic or English, with mistakes too. We translate it, suggest the right word and search both languages.")
        }
        Section14("Try") {
            ForEach(["Calculus final", "Pharmacology", "Physics lab", "Anatomy midterm", "ملخص", "2025"], id: \.self) { s in
                Button { query = s; saved.searched(s) } label: {
                    Label(s, systemImage: "magnifyingglass").fg14(.primary)
                }
            }
        }
        Section14 {
            EmptyState(symbol: "magnifyingglass.circle", title: String(format: L("Search %d materials"), library.items.count),
                       text: "Results from every subject page appear instantly, then files from Google Drive. Arabic and English both work.")
                .listRowBackground(Color.clear)
        }
    }

    private var guideTopics: [(String, String, String)] {
        [("Past exams and finals", "doc.text.magnifyingglass", "final"), ("Summaries", "text.alignleft", "summary"),
         ("Slides", "rectangle.on.rectangle", "slides"), ("Test banks", "questionmark.folder", "test bank")]
    }

    /// Suggestions for the text typed so far
    @ViewBuilder private var helpSection: some View {
        Section14 {
            if let fix = hints.fixes.first { HelpChips(title: "Did you mean?", options: [fix]) { query = $0 } }
            if !hints.completions.isEmpty { HelpChips(title: "Suggestions", options: hints.completions) { query = $0 } }
            if let alt = otherLanguage { HelpChips(title: "Also searching for", options: [alt]) { query = $0 } }
            ForEach(hints.subjects) { s in
                RouteLink(Route.subject(s)) {
                    HStack(spacing: 10) { Text(s.e ?? "📁"); Text(s.t); Spacer(); Text("Subject").font(.caption).fg14(.secondary) }
                }
            }
            HelpChips(title: "Narrow it down", options: narrow.filter { !AB.norm(query).contains(AB.norm($0)) }) { query = query.trimmingCharacters(in: .whitespaces) + " " + $0.lowercased() }
        }
    }

    private func refreshHints() async {
        onlineAlt = nil
        try? await Task.sleep(nanoseconds: 150_000_000)
        if Task.isCancelled { return }
        let h = help.hints(for: query, library: library)
        hints = h
        // words the list does not know go to the translator once typing pauses
        let q = query.trimmingCharacters(in: .whitespaces)
        guard h.translated == nil, q.count >= 3, q.contains(where: { $0.isLetter }) else { return }
        try? await Task.sleep(nanoseconds: 800_000_000)
        if Task.isCancelled { return }
        let t = await help.translate(q)
        if !Task.isCancelled { onlineAlt = t }
    }

    /// Drive results for the translated search join the ones already shown
    private func addDrive(_ alt: String) async {
        guard let r = try? await DriveSearch.search(alt) else { return }
        var seen = Set(drive.map { $0.m.u })
        drive += r.filter { seen.insert($0.m.u).inserted }
    }

    @ViewBuilder private var driveSection: some View {
        Section14 {
            switch driveState {
            case .loading:
                HStack { ABDots(); Text("Searching Google Drive…").fg14(.secondary) }
            case .failed:
                VStack(alignment: .leading, spacing: 8) {
                    Text("Drive search is not answering.").font(.headline)
                    Text("Check your connection and try again.").fg14(.secondary)
                    Button("Try again") { Task { await runDrive(force: true) } }.borderedButton14()
                }
            default:
                ForEach(driveShown) { r in
                    MaterialRow(m: r.m, kind: r.kind, meta: [size(r.size), date(r.date)].filter { !$0.isEmpty }.joined(separator: " · "))
                }
                if driveState == .done && driveShown.isEmpty {
                    Text(drive.isEmpty ? "No Drive files match." : "No Drive files match these filters.").fg14(.secondary)
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
            if let alt = hints.translated { await addDrive(alt) }
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
        return d.fmt14(.medium, .none)
    }
}
