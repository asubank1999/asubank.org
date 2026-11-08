import SwiftUI

// More > Customize: what Home shows, the Continue list, text size, vibration. Kept on this device.

enum HomePrefs {
    /// Comma-separated ids of the Home sections someone turned off
    static let key = "ab.home.hidden"
    static let compactKey = "ab.home.compact"
    /// Whether opened files are remembered for Continue
    static let recentKey = "ab.recent.on"
    static let startKey = "ab.startTab"
    static let textKey = "ab.textSize"
    static let hapticsKey = "ab.haptics"

    static let sections: [(id: String, name: String, icon: String)] = [
        ("online", "Who's online", "circle.fill"),
        ("top", "Top picks", "star.circle"),
        ("weather", "Weather", "cloud.sun"),
        ("calendar", "Calendar", "calendar"),
        ("lecture", "Next lecture", "alarm"),
        ("due", "Exams & tasks", "checklist"),
        ("hero", "Search banner", "magnifyingglass"),
        ("continue", "Continue", "clock.arrow.circlepath"),
        ("saved", "Saved", "star"),
        ("quick", "Quick links", "square.grid.2x2"),
        ("schedules", "Calendars & schedules", "calendar.badge.clock"),
        ("subjects", "Subjects", "books.vertical"),
        ("newhere", "New students", "graduationcap"),
        ("community", "Community & services", "person.3")
    ]

    static func on(_ id: String, _ hidden: String) -> Bool {
        !hidden.split(separator: ",").contains { $0 == id }
    }

    static func setting(_ id: String, _ value: Bool, in hidden: String) -> String {
        var list = hidden.split(separator: ",").map(String.init).filter { $0 != id }
        if !value { list.append(id) }
        return list.joined(separator: ",")
    }

    static var remembersRecent: Bool { UserDefaults.standard.object(forKey: recentKey) as? Bool ?? true }
    static var haptics: Bool { UserDefaults.standard.object(forKey: hapticsKey) as? Bool ?? true }

    static let textSizes: [(id: Int, name: String)] = [(0, "Match my phone"), (1, "Small"), (2, "Normal"), (3, "Large"), (4, "Extra large")]

    /// 0 follows the phone
    @available(iOS 15, *)
    static func typeRange(_ choice: Int) -> ClosedRange<DynamicTypeSize> {
        switch choice {
        case 1: return DynamicTypeSize.small...DynamicTypeSize.small
        case 2: return DynamicTypeSize.large...DynamicTypeSize.large
        case 3: return DynamicTypeSize.xLarge...DynamicTypeSize.xLarge
        case 4: return DynamicTypeSize.xxxLarge...DynamicTypeSize.xxxLarge
        default: return DynamicTypeSize.xSmall...DynamicTypeSize.accessibility5
        }
    }
}

struct CustomizeScreen: View {
    @EnvironmentObject private var saved: SavedStore
    @AppStorage(HomePrefs.key) private var hidden = ""
    @AppStorage(HomePrefs.compactKey) private var compact = false
    @AppStorage(HomePrefs.recentKey) private var remember = true
    @AppStorage(HomePrefs.startKey) private var start = "home"
    @AppStorage(HomePrefs.textKey) private var textSize = 0
    @AppStorage(HomePrefs.hapticsKey) private var haptics = true
    @State private var confirmReset = false

    var body: some View {
        List {
            Section14 {
                NavigationLink { ThemeScreen() } label: { Label("Theme", systemImage: "paintpalette") }
                NavigationLink { TabBarScreen() } label: { Label("Customize tab bar", systemImage: "rectangle.3.group") }
                if UIApplication.shared.supportsAlternateIcons {
                    NavigationLink { AppIconScreen() } label: { Label("App icon", systemImage: "app.badge") }
                }
                NavigationLink { SoundsScreen() } label: { Label("Sounds", systemImage: "speaker.wave.2") }
                NavigationLink { LanguageScreen() } label: { Label("Language", systemImage: "globe") }
            } header: { Text("Look and feel") }

            Section14 {
                Toggle(isOn: $remember.animation()) { Label("Remember what I open", systemImage: "clock.arrow.circlepath") }
                    .tint14(.abAccent)
                    .onChange(of: remember) { on in if !on { saved.clearRecent() } }
                Toggle(isOn: homeBinding("continue")) { Label("Show Continue on Home", systemImage: "rectangle.stack") }
                    .tint14(.abAccent)
                    .disabled(!remember)
                Button() {
                    saved.clearRecent()
                    Haptics.success()
                } label: { Label("Clear the Continue list", systemImage: "trash") }
                .disabled(saved.recent.isEmpty)
            } header: { Text("Continue") } footer: {
                Text("Continue lists the files you opened lately. It stays on this phone. Turn off Remember and the app stops keeping this list.")
            }

            Section14 {
                ForEach(HomePrefs.sections.filter { $0.id != "continue" }, id: \.id) { s in
                    Toggle(isOn: homeBinding(s.id)) { Label(L(s.name), systemImage: s.icon) }.tint14(.abAccent)
                }
                Toggle(isOn: $compact.animation()) { Label("Smaller Quick links", systemImage: "square.grid.3x3") }.tint14(.abAccent)
            } header: { Text("What Home shows") } footer: {
                Text("Good for small screens: turn off what you don't use, so more fits on one screen.")
            }

            Section14 {
                Picker(selection: $start) {
                    Text("Home").tag("home")
                    Text("Feed").tag("feed")
                    Text("Chat").tag("chat")
                    Text("Search").tag("search")
                } label: { Label("Open the app on", systemImage: "arrow.forward.circle") }
                Picker(selection: $textSize) {
                    ForEach(HomePrefs.textSizes, id: \.id) { Text(L($0.name)).tag($0.id) }
                } label: { Label("Text size", systemImage: "textformat.size") }
                Toggle(isOn: $haptics) { Label("Vibration", systemImage: "iphone.radiowaves.left.and.right") }.tint14(.abAccent)
            } header: { Text("General") }

            Section14 {
                Button("Reset to the default") { confirmReset = true }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Customize")
        .confirmationDialog14("Reset to the default", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Reset") {
                hidden = ""
                compact = false
                remember = true
                start = "home"
                textSize = 0
                haptics = true
            }
            Button("Cancel") {}
        }
    }

    private func homeBinding(_ id: String) -> Binding<Bool> {
        Binding(get: { HomePrefs.on(id, hidden) }, set: { hidden = HomePrefs.setting(id, $0, in: hidden) })
    }
}

// MARK: - YouTube links

/// Share a YouTube video, the same list as asubank.org/upload/uploadmethod
struct ShareVideoScreen: View {
    private struct Link: Decodable, Identifiable {
        var id: String
        var u: String
        var t: String
        var s: String?
        var d: String?
        var canDelete: Bool?
    }

    @ObservedObject private var account = AccountStore.shared
    @EnvironmentObject private var library: Library
    @Environment(\.openURL) private var openURL
    @State private var url = ""
    @State private var title = ""
    /// the chosen subject's page, e.g. /banks/it
    @State private var page = ""
    @State private var links: [Link] = []
    @State private var busy = false
    @State private var error: String?
    @State private var sent = false

    private var ready: Bool {
        url.lowercased().contains("youtu") && title.trimmingCharacters(in: .whitespaces).count >= 3 && chosen != nil && !busy
    }

    private var choices: [Subject] { library.subjects.filter { $0.c != "Archive" } }
    private var chosen: Subject? { choices.first { $0.p == page } }

    var body: some View {
        List {
            if account.signedIn {
                Section14 {
                    TextField("YouTube link", text: $url)
                        .keyboardType(.URL).autocapitalization(.none).disableAutocorrection(true)
                    TextField("Title", text: $title)
                    Button {
                        if let s = UIPasteboard.general.string { url = s.trimmingCharacters(in: .whitespacesAndNewlines) }
                    } label: { Label("Paste link", systemImage: "doc.on.clipboard") }
                    Picker("Subject", selection: $page) {
                        Text("Choose a subject").tag("")
                        ForEach(choices) { s in Text(s.t).tag(s.p) }
                    }
                    if let error { Text(error).fg14(.red) }
                    if sent { Label("Shared. Thank you!", systemImage: "checkmark.seal.fill").fg14(.green) }
                    Button { Task { await send() } } label: {
                        HStack { Text("Share video"); if busy { Spacer(); ABDots() } }
                    }
                    .disabled(!ready)
                } header: { Text("Share a YouTube video") } footer: {
                    Text("It's published at once, for every student. Share only videos that are public on YouTube.")
                }
            } else {
                Section14 {
                    Button("Sign in or create account") { account.signInShown = true }
                } footer: { Text("Sharing a video needs an account.") }
            }
            if !links.isEmpty {
                Section14("Videos students shared") {
                    ForEach(links) { l in
                        Button { if let u = URL(string: l.u) { openURL(u) } } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "play.rectangle.fill").font(.title3).fg14(.red)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(l.t).fg14(.primary).lineLimit(2)
                                    if let s = l.s, !s.isEmpty { Text(s).font(.caption).fg14(.secondary) }
                                }
                            }
                        }
                        .contextMenu {
                            if l.canDelete == true {
                                Button() { Task { await remove(l) } } label: { Label("Delete", systemImage: "trash") }
                            }
                            Button {
                                Task { @MainActor in
                                    // once the menu has closed
                                    try? await Task.sleep(nanoseconds: 350_000_000)
                                    Moderation.report(ReportTarget(kind: "file", targetId: l.u, name: l.t))
                                }
                            } label: { Label("Report", systemImage: "flag") }
                        }
                        .swipeActions14 {
                            if l.canDelete == true {
                                Button() { Task { await remove(l) } } label: { Label("Delete", systemImage: "trash") }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Share a video")
        .navigationBarTitleDisplayMode(.inline)
        .task14 { await load() }
        .refreshable14 { await load() }
    }

    private func load() async {
        guard let r = try? await AccountsAPI.call("linksList", [:], token: account.token) else { return }
        links = Array((decode([Link].self, r["links"]) ?? []).prefix(100))
    }

    private func send() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await account.call("linkAdd", [
                "url": url.trimmingCharacters(in: .whitespaces),
                "title": title.trimmingCharacters(in: .whitespaces),
                "subject": chosen?.t ?? "",
                "page": page,
                "category": chosen?.c ?? ""
            ])
            url = ""; title = ""; page = ""
            error = nil
            sent = true
            Haptics.success()
            await load()
        } catch {
            sent = false
            self.error = error.localizedDescription
            Haptics.error()
        }
    }

    private func remove(_ l: Link) async {
        links.removeAll { $0.id == l.id }
        _ = try? await account.call("linkDelete", ["id": l.id])
        await load()
    }
}
