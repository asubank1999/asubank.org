import SwiftUI
import UserNotifications

@main
struct ASUBankApp: App {
    @StateObject private var app = AppState()
    @StateObject private var library = Library()
    @StateObject private var saved = SavedStore()
    @StateObject private var pages = SitePages()
    @StateObject private var config = AppConfig()
    @AppStorage("ab.appearance") private var appearance = 0
    @AppStorage("ab.theme.rev") private var themeRev = 0
    @State private var splash = !UserDefaults.standard.bool(forKey: "noSplash")
        && UserDefaults.standard.string(forKey: "startTab") == nil
        && UserDefaults.standard.string(forKey: "startPath") == nil

    init() {
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
        ChatNotifier.register()
        AppTheme.applyBars()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                RootView()
                    .id(themeRev)
                if splash {
                    SplashView().transition(.opacity).zIndex(1)
                }
            }
                .environmentObject(app)
                .environmentObject(library)
                .environmentObject(saved)
                .environmentObject(pages)
                .environmentObject(config)
                .tint(.abAccent)
                .preferredColorScheme(appearance == 1 ? .light : appearance >= 2 ? .dark : nil)
                .task {
                    try? await Task.sleep(nanoseconds: 1_500_000_000)
                    withAnimation(.easeOut(duration: 0.35)) { splash = false }
                }
                .task {
                    await AccountStore.shared.refresh()
                    if AccountStore.shared.signedIn { ChatNotifier.shared.start() }
                }
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
    @ObservedObject private var account = AccountStore.shared
    @State private var lastTab: AppTab = .home

    var body: some View {
        TabView(selection: $app.tab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(AppTab.home)
            SearchView()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(AppTab.search)
            ChatTab()
                .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right.fill") }
                .tag(AppTab.chat)
            if account.isAdmin {
                AdminTab()
                    .tabItem { Label("Admin", systemImage: "shield.lefthalf.filled") }
                    .tag(AppTab.admin)
            } else {
                SavedView()
                    .tabItem { Label("Saved", systemImage: "star.fill") }
                    .tag(AppTab.saved)
            }
            MoreView()
                .tabItem { Label("More", systemImage: "ellipsis.circle.fill") }
                .tag(AppTab.more)
        }
        .onChange(of: app.tab) { t in
            // Upload opens as a sheet over the tab you were on; admins find Saved under More
            if t == .upload { app.tab = lastTab; app.uploadShown = true; return }
            if t == .saved && account.isAdmin { app.tab = .more; return }
            if t == .admin && !account.isAdmin { app.tab = lastTab; return }
            lastTab = t
            Haptics.tap()
        }
        .onAppear {
            if app.tab == .upload { app.tab = .home; app.uploadShown = true }
        }
        .sheet(isPresented: $app.uploadShown) { UploadView() }
        .sheet(isPresented: $account.signInShown) { SignInScreen() }
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
            Text(updateMessage)
        }
        .task { await config.check() }
        .onChange(of: phase) { p in
            if p == .active { Task { await config.check() } }
        }
    }
}

extension RootView {
    var updateMessage: String {
        var m = "ASU Bank " + (config.latest ?? "") + " is out. You have " + AppConfig.current + "."
        if let n = config.notes, !n.isEmpty { m += "\n\n" + n }
        return m
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
        default:
            tab = .home
            if url.host?.hasSuffix("asubank.org") == true, let p = AB.sitePath(url.absoluteString), p != "/" {
                pendingRoute = .page(p, "")
            }
        }
    }
}
