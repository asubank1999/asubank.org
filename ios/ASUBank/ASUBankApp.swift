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
                    .environment(\.locale, AppLanguage.locale)
                    .environment(\.layoutDirection, AppLanguage.isRTL ? .rightToLeft : .leftToRight)
                if splash {
                    SplashView().transition(.opacity).zIndex(1)
                }
            }
                .environmentObject(app)
                .environmentObject(library)
                .environmentObject(saved)
                .environmentObject(pages)
                .overlay(alignment: .top) { JobsBanner().padding(.top, 4) }
                .environmentObject(config)
                .tint(.abAccent)
                .preferredColorScheme(appearance == 1 ? .light : appearance >= 2 ? .dark : nil)
                .task {
                    try? await Task.sleep(nanoseconds: 600_000_000)
                    withAnimation(.easeOut(duration: 0.35)) { splash = false }
                }
                .task {
                    await AccountStore.shared.refresh()
                    if AccountStore.shared.signedIn {
                        ChatNotifier.shared.start()
                        await NoticeStore.shared.load()
                    }
                }
                .task {
                    await library.load()
                    let links = await DriveLinks.shared.refresh()
                    let removed = await RemovedFiles.shared.refresh()
                    if links || removed { await library.reindex() }
                }
                .task { await pages.load() }
                .task { await SearchHelp.shared.load() }
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
    @ObservedObject private var notifier = ChatNotifier.shared
    @ObservedObject private var notices = NoticeStore.shared
    @AppStorage(TabBarPrefs.key) private var chosenTabs = TabBarPrefs.standard
    @State private var lastTab: AppTab = .home
    @State private var whatsNew: WhatsNewEntry?
    /// Tapping the tab you are on rebuilds its stack, which goes back to its first screen
    @State private var resets: [AppTab: Int] = [:]

    private var tabSelection: Binding<AppTab> {
        Binding(get: { app.tab }, set: { t in
            if t == app.tab { resets[t, default: 0] += 1; Haptics.tap() }
            app.tab = t
        })
    }

    private var middleTabs: [AppTab] { TabBarPrefs.chosen(chosenTabs, admin: account.isAdmin) }

    @ViewBuilder private func screen(_ t: AppTab) -> some View {
        switch t {
        case .feed: FeedTab()
        case .search: SearchView()
        case .chat: ChatTab()
        case .friends: FriendsTab()
        case .notifications: NotificationsTab()
        case .saved: SavedView()
        case .lectures: LecturesTab()
        case .hacks: LifeHacksTab()
        case .admin: AdminTab()
        case .more: MoreView()
        default: HomeView()
        }
    }

    var body: some View {
        TabView(selection: tabSelection) {
            HomeView().id(resets[.home, default: 0])
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(AppTab.home)
            ForEach(middleTabs) { t in
                let o = TabBarPrefs.options.first { $0.tab == t }
                screen(t).id(resets[t, default: 0])
                    .tabItem { Label(LocalizedStringKey(o?.name ?? ""), systemImage: o?.icon ?? "circle") }
                    .tag(t)
                    .badge(t == .friends ? notifier.socialUnread : t == .notifications ? notices.unread : 0)
            }
            if account.isAdmin {
                AdminTab().id(resets[.admin, default: 0])
                    .tabItem { Label("Admin", systemImage: "shield.lefthalf.filled") }
                    .tag(AppTab.admin)
            }
            MoreView().id(resets[.more, default: 0])
                .tabItem { Label("More", systemImage: "ellipsis.circle.fill") }
                .tag(AppTab.more)
        }
        .onChange(of: app.tab) { t in
            // Upload opens as a sheet over the tab you were on; admins find Saved under More
            if t == .upload { app.tab = lastTab; app.uploadShown = true; return }
            if t == .admin && !account.isAdmin { app.tab = lastTab; return }
            // a tab that isn't in the bar opens over the one you're on
            if t != .home && t != .more && t != .admin && !middleTabs.contains(t) {
                app.tab = lastTab
                app.overlayTab = t
                return
            }
            lastTab = t
            Haptics.tap()
        }
        .onAppear {
            if app.tab == .upload { app.tab = .home; app.uploadShown = true }
            if app.tab != .home && app.tab != .more && app.tab != .admin && !middleTabs.contains(app.tab) {
                let t = app.tab
                app.tab = .home
                app.overlayTab = t
            }
        }
        .sheet(isPresented: $app.uploadShown) { UploadView() }
        .sheet(item: $app.overlayTab) { screen($0) }
        .sheet(item: $whatsNew) { WhatsNewSheet(entry: $0) }
        .task { whatsNew = WhatsNew.pending(accepted: accepted) }
        .task { LectureStore.shared.reschedule() }
        .sheet(isPresented: $account.signInShown) { SignInScreen() }
        .alert("Thank you for joining early! 🌱", isPresented: $account.earlyThanksShown) {
            Button("You're welcome", role: .cancel) {}
        } message: {
            Text("You joined UniHub in its early days, so your profile now has the Early supporter badge. Thank you for helping students at ASU.")
        }
        .fullScreenCover(isPresented: Binding(get: { !accepted && !config.mustUpdate }, set: { _ in })) {
            WelcomeTermsView()
        }
        .fullScreenCover(isPresented: Binding(get: { config.mustUpdate }, set: { _ in })) {
            UpdateRequiredView().environmentObject(config)
        }
        .alert("Update available", isPresented: Binding(get: { config.offerUpdate && whatsNew == nil }, set: { config.offerUpdate = $0 })) {
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
        var m = "UniHub " + (config.latest ?? "") + " is out. You have " + AppConfig.current + "."
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
