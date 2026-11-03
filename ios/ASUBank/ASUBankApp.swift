import SwiftUI

@main
struct ASUBankApp: App {
    @StateObject private var app = AppState()
    @StateObject private var library = Library()
    @StateObject private var saved = SavedStore()
    @StateObject private var pages = SitePages()
    @StateObject private var config = AppConfig()
    @AppStorage("ab.appearance") private var appearance = 0

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .environmentObject(library)
                .environmentObject(saved)
                .environmentObject(pages)
                .environmentObject(config)
                .tint(.abAccent)
                .preferredColorScheme(appearance == 1 ? .light : appearance == 2 ? .dark : nil)
                .task {
                    await library.load()
                    if await DriveLinks.shared.refresh() { library.reindex() }
                }
                .task { await pages.load() }
                .onOpenURL { url in app.open(url) }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var app: AppState
    @EnvironmentObject private var config: AppConfig
    @Environment(\.scenePhase) private var phase
    @Environment(\.openURL) private var openURL
    @AppStorage(WelcomeTermsView.key) private var accepted = false

    var body: some View {
        TabView(selection: $app.tab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(AppTab.home)
            SearchView()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(AppTab.search)
            UploadView()
                .tabItem { Label("Upload", systemImage: "arrow.up.circle.fill") }
                .tag(AppTab.upload)
            SavedView()
                .tabItem { Label("Saved", systemImage: "star.fill") }
                .tag(AppTab.saved)
            MoreView()
                .tabItem { Label("More", systemImage: "ellipsis.circle.fill") }
                .tag(AppTab.more)
        }
        .onChange(of: app.tab) { _ in Haptics.tap() }
        .fullScreenCover(isPresented: Binding(get: { !accepted && !config.mustUpdate }, set: { _ in })) {
            WelcomeTermsView()
        }
        .fullScreenCover(isPresented: Binding(get: { config.mustUpdate }, set: { _ in })) {
            UpdateRequiredView().environmentObject(config)
        }
        .alert("Update available", isPresented: $config.offerUpdate) {
            Button("Update") { if let u = config.storeURL { openURL(u) } }
            Button("Later", role: .cancel) {}
        } message: {
            Text("ASU Bank \(config.latest ?? "") is out on the App Store. You have \(AppConfig.current).")
        }
        .task { await config.check() }
        .onChange(of: phase) { p in
            if p == .active { Task { await config.check() } }
        }
    }
}

extension AppState {
    /// asubank://search?q=..., and shared asubank.org links to the search/upload/saved pages
    func open(_ url: URL) {
        let path = url.host == "asubank.org" || url.host == "www.asubank.org" ? url.path : "/" + (url.host ?? "") + url.path
        let q = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "q" }?.value
        switch path {
        case "/search", "/guides/search": if let q { search(q) } else { tab = .search }
        case "/upload", "/upload/uploadmethod": tab = .upload
        case "/saved", "/guides/saved": tab = .saved
        default: tab = .home
        }
    }
}
