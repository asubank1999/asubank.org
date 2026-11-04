import SwiftUI
import WebKit

/// Where a row or card leads
enum Route: Hashable {
    case subject(Subject)
    case material(Material)
    case web(String, String) // title, URL or site path
    case page(String, String) // asubank.org path, title
}

struct RouteView: View {
    let route: Route
    var body: some View {
        switch route {
        case .subject(let s): SubjectView(subject: s)
        case .material(let m): MaterialScreen(material: m)
        case .web(let t, let u):
            if let p = AB.sitePath(u) { SitePageView(path: p, title: t) } else { WebScreen(title: t, url: AB.absolute(u) ?? AB.site) }
        case .page(let p, let t): SitePageView(path: p, title: t)
        }
    }
}


struct MaterialRow: View {
    let m: Material
    var kind: Kind? = nil
    var meta: String? = nil
    var showSource = true
    @EnvironmentObject private var saved: SavedStore

    var body: some View {
        let k = kind ?? AB.kind(m.u, m.t)
        RouteLink(k == .page ? Route.page(AB.sitePath(m.u) ?? m.u, m.t) : Route.material(m)) {
            HStack(spacing: 12) {
                KindTile(kind: k)
                VStack(alignment: .leading, spacing: 3) {
                    Text(m.t.trimmingCharacters(in: CharacterSet(charactersIn: "_ :")))
                        .font(.body)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    HStack(spacing: 6) {
                        if AB.isNew(m.d) { Badge(text: "NEW", color: .green) }
                        if m.tag == "pin" { Badge(text: "ALL FILES") }
                        if m.tag == "warn" { Badge(text: "CLOSED", color: .orange) }
                        Text([k.name, meta ?? (showSource ? m.s : nil)].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                if saved.isSaved(m.u) {
                    Image(systemName: "star.fill").font(.caption).foregroundStyle(Color.abGold)
                }
            }
            .padding(.vertical, 2)
        }
        .contextMenu {
            Button { saved.toggle(m); Haptics.tap() } label: {
                Label(saved.isSaved(m.u) ? "Remove from Saved" : "Save", systemImage: saved.isSaved(m.u) ? "star.slash" : "star")
            }
            if let url = AB.absolute(m.u) {
                ShareButton(item: url, subject: Text(m.t)) { Label("Share", systemImage: "square.and.arrow.up") }
                Button { UIPasteboard.general.url = url } label: { Label("Copy link", systemImage: "link") }
            }
            if k != .page {
                Button(role: .destructive) {
                    Task { @MainActor in
                        // once the menu has closed
                        try? await Task.sleep(nanoseconds: 350_000_000)
                        Moderation.report(ReportTarget(kind: "file", targetId: m.u, name: m.t))
                    }
                } label: {
                    Label("Report this file", systemImage: "flag")
                }
            }
        }
        .swipeActions(edge: .leading) {
            Button { saved.toggle(m); Haptics.tap() } label: {
                Label("Save", systemImage: saved.isSaved(m.u) ? "star.slash.fill" : "star.fill")
            }
            .tint(.abGold)
        }
    }
}

/// A page of asubank.org that needs the web (Google forms, uploads...), shown inside the app.
/// Links on it to other asubank.org pages open their native screens.
struct WebScreen: View {
    let title: String
    let url: URL
    @EnvironmentObject private var pages: SitePages
    @EnvironmentObject private var app: AppState
    @Environment(\.openURL) private var openURL
    @State private var next: Route?

    var body: some View {
        WebView(url: url, allowZoom: false) { path in
            guard path != AB.sitePath(url.absoluteString) else { return false }
            switch SiteRouter.target(path, title: "") {
            case .tab(let t):
                app.tab = t
                return true
            case .push(let r):
                if case .page(let p, _) = r, pages.page(p)?.web == 1 { return false }
                next = r
                return true
            default:
                return false
            }
        }
        .ignoresSafeArea(edges: .bottom)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .push(isPresented: Binding(get: { next != nil }, set: { if !$0 { next = nil } })) {
            if let next { RouteView(route: next) }
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    ShareButton(item: url) { Label("Share", systemImage: "square.and.arrow.up") }
                    Button { openURL(url) } label: { Label("Open in Safari", systemImage: "safari") }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
    }
}

/// WKWebView with a progress bar. asubank.org pages hide their own web tab bar (the app has a native one),
/// and links to other apps (WhatsApp, mail, phone) open outside.
struct WebView: View {
    let url: URL
    var allowZoom = true
    /// Return true when the app handled a tapped asubank.org link natively
    var onSiteLink: ((String) -> Bool)? = nil
    @StateObject private var model = WebModel()

    var body: some View {
        ZStack(alignment: .top) {
            WebViewRepresentable(url: url, allowZoom: allowZoom, model: model)
                .onAppear { model.onSiteLink = onSiteLink }
            if model.progress < 1 {
                ProgressView(value: model.progress)
                    .progressViewStyle(.linear)
                    .tint(.abAccent)
                    .transition(.opacity)
            }
            if let err = model.error {
                VStack(spacing: 12) {
                    Image(systemName: "wifi.exclamationmark").font(.largeTitle).foregroundStyle(.secondary)
                    Text(err).multilineTextAlignment(.center).foregroundStyle(.secondary)
                    Button("Try again") { model.reload() }.buttonStyle(.borderedProminent).tint(.abAccent)
                }
                .padding(32)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.abBackground)
            }
        }
        .animation(.easeOut(duration: 0.2), value: model.progress < 1)
    }
}

@MainActor
final class WebModel: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate {
    @Published var progress: Double = 0
    @Published var error: String?
    weak var web: WKWebView?
    var onSiteLink: ((String) -> Bool)?
    var allowZoom = true
    private var obs: NSKeyValueObservation?

    func attach(_ w: WKWebView) {
        web = w
        obs = w.observe(\.estimatedProgress, options: [.new]) { [weak self] w, _ in
            let p = w.estimatedProgress
            Task { @MainActor in self?.progress = p }
        }
    }

    func reload() { error = nil; web?.reload() }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction) async -> WKNavigationActionPolicy {
        guard let u = action.request.url else { return .allow }
        let scheme = u.scheme?.lowercased() ?? ""
        let host = u.host?.lowercased() ?? ""
        let external = !["http", "https", "about", "blob", "data"].contains(scheme)
            || host.hasSuffix("whatsapp.com") || host == "wa.me"
        if external {
            _ = await UIApplication.shared.open(u)
            return .cancel
        }
        if action.navigationType == .linkActivated, action.targetFrame?.isMainFrame != false,
           let path = AB.sitePath(u.absoluteString), onSiteLink?(path) == true {
            return .cancel
        }
        // target=_blank links: load them in this view
        if action.targetFrame == nil { webView.load(action.request); return .cancel }
        return .allow
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if !allowZoom { webView.scrollView.pinchGestureRecognizer?.isEnabled = false }
    }

    // window.open() and target=_blank: open in this same view (the request page's "Proceed" button used this)
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let u = navigationAction.request.url, let path = AB.sitePath(u.absoluteString), onSiteLink?(path) == true { return nil }
        webView.load(navigationAction.request)
        return nil
    }

    // alert(), confirm() and prompt() as native alerts
    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo) async {
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "OK", style: .default) { _ in c.resume() })
            present(a) { c.resume() }
        }
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo) async -> Bool {
        await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
            let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            a.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in c.resume(returning: false) })
            a.addAction(UIAlertAction(title: "OK", style: .default) { _ in c.resume(returning: true) })
            present(a) { c.resume(returning: false) }
        }
    }

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo) async -> String? {
        await withCheckedContinuation { (c: CheckedContinuation<String?, Never>) in
            let a = UIAlertController(title: nil, message: prompt, preferredStyle: .alert)
            a.addTextField { $0.text = defaultText }
            a.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in c.resume(returning: nil) })
            a.addAction(UIAlertAction(title: "OK", style: .default) { _ in c.resume(returning: a.textFields?.first?.text) })
            present(a) { c.resume(returning: nil) }
        }
    }

    /// Shows an alert over whatever is on screen; calls `failed` if nothing can present it
    private func present(_ a: UIViewController, failed: () -> Void) {
        let root = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first { $0.isKeyWindow }?.rootViewController
        var top = root
        while let p = top?.presentedViewController { top = p }
        guard let top else { return failed() }
        top.present(a, animated: true)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { fail(error) }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { fail(error) }

    private func fail(_ e: Error) {
        let code = (e as NSError).code
        if code == NSURLErrorCancelled || code == 102 /* frame load interrupted */ { return }
        progress = 1
        error = "This page couldn't load. Check your connection and try again."
    }
}

private struct WebViewRepresentable: UIViewRepresentable {
    let url: URL
    let allowZoom: Bool
    let model: WebModel

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        cfg.websiteDataStore = .default()
        let hideTabbar = WKUserScript(
            source: "document.documentElement.setAttribute('data-ab-notab','');document.documentElement.setAttribute('data-ab-app','ios');",
            injectionTime: .atDocumentStart, forMainFrameOnly: true)
        cfg.userContentController.addUserScript(hideTabbar)
        if !allowZoom {
            let noZoom = WKUserScript(source: """
                var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
                m.content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover';
                """, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
            cfg.userContentController.addUserScript(noZoom)
        }
        let w = WKWebView(frame: .zero, configuration: cfg)
        w.navigationDelegate = model
        w.uiDelegate = model
        model.allowZoom = allowZoom
        w.allowsBackForwardNavigationGestures = true
        w.isOpaque = false
        w.backgroundColor = .systemGroupedBackground
        w.customUserAgent = nil
        model.attach(w)
        w.load(URLRequest(url: url))
        return w
    }

    func updateUIView(_ w: WKWebView, context: Context) {}
}

/// Card with emoji, title and subtitle (home quick links and subject grid)
struct Card: View {
    let emoji: String
    let title: String
    var subtitle: String? = nil
    var badge: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(emoji).font(.system(size: 26))
                Spacer()
                if let badge { Badge(text: badge) }
            }
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(2).multilineTextAlignment(.leading)
            if let subtitle {
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2).multilineTextAlignment(.leading)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 92, alignment: .topLeading)
        .padding(14)
        .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Press animation used by cards
struct PressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

struct SectionHeader: View {
    let title: String
    var arabic: String? = nil
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).font(.title3.weight(.bold))
            Spacer()
            if let arabic { Text(arabic).font(.footnote).foregroundStyle(.secondary) }
        }
        .padding(.horizontal, 20)
        .padding(.top, 26)
        .padding(.bottom, 8)
    }
}

struct EmptyState: View {
    let symbol: String
    let title: String
    let text: String
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: symbol).font(.system(size: 44)).foregroundStyle(Color.abAccent.opacity(0.8))
            Text(title).font(.headline)
            Text(text).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .padding(40)
        .frame(maxWidth: .infinity)
    }
}
