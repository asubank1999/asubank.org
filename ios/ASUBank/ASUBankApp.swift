import SwiftUI

@main
struct ASUBankApp: App {
    @StateObject private var app = AppState()
    @StateObject private var library = Library()
    @StateObject private var saved = SavedStore()
    @AppStorage("ab.appearance") private var appearance = 0

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .environmentObject(library)
                .environmentObject(saved)
                .tint(.abAccent)
                .preferredColorScheme(appearance == 1 ? .light : appearance == 2 ? .dark : nil)
                .task { await library.load() }
                .onOpenURL { url in app.open(url) }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var app: AppState

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
