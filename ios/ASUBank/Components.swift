import SwiftUI
import WebKit

/// Where a row or card leads
enum Route: Hashable {
    case subject(Subject)
    case material(Material)
    case web(String, String) // title, URL or site path
}

extension View {
    /// Every tab's NavigationStack understands the same routes
    func abDestinations() -> some View {
        navigationDestination(for: Route.self) { r in
            switch r {
            case .subject(let s): SubjectView(subject: s)
            case .material(let m): MaterialScreen(material: m)
            case .web(let t, let u): WebScreen(title: t, url: AB.absolute(u) ?? AB.site)
            }
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
        NavigationLink(value: Route.material(m)) {
            HStack(spacing: 12) {
                KindTile(kind: k)
                VStack(alignment: .leading, spacing: 3) {
                    Text(m.t.trimmingCharacters(in: CharacterSet(charactersIn: "_ :")))
                        .font(.body)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    HStack(spacing: 6) {
                        if m.tag == "new" { Badge(text: "NEW", color: .green) }
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
                ShareLink(item: url, subject: Text(m.t)) { Label("Share", systemImage: "square.and.arrow.up") }
                Button { UIPasteboard.general.url = url } label: { Label("Copy link", systemImage: "link") }
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

/// Opens a material inside the app (Drive / Docs preview), with save, share and "open in" buttons
struct MaterialScreen: View {
    let material: Material
    @EnvironmentObject private var saved: SavedStore
    @Environment(\.openURL) private var openURL

    var body: some View {
        let kind = AB.kind(material.u, material.t)
        Group {
            if let url = AB.previewURL(material.u) {
                WebView(url: url)
            } else {
                Text("This link can't be opened.").foregroundStyle(.secondary)
            }
        }
        .ignoresSafeArea(edges: .bottom)
        .navigationTitle(material.t)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button { saved.toggle(material); Haptics.tap() } label: {
                    Image(systemName: saved.isSaved(material.u) ? "star.fill" : "star")
                }
                .accessibilityLabel(saved.isSaved(material.u) ? "Remove from Saved" : "Save")
                Menu {
                    if let url = AB.absolute(material.u) {
                        ShareLink(item: url, subject: Text(material.t)) { Label("Share", systemImage: "square.and.arrow.up") }
                        Button { openURL(url) } label: {
                            Label(kind == .page ? "Open in Safari" : "Open in Drive / Safari", systemImage: "safari")
                        }
                        Button { UIPasteboard.general.url = url; Haptics.success() } label: { Label("Copy link", systemImage: "link") }
                    }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .onAppear { saved.opened(material) }
    }
}

/// A page of asubank.org shown inside the app
struct WebScreen: View {
    let title: String
    let url: URL
    @Environment(\.openURL) private var openURL

    var body: some View {
        WebView(url: url)
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        ShareLink(item: url) { Label("Share", systemImage: "square.and.arrow.up") }
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
    @StateObject private var model = WebModel()

    var body: some View {
        ZStack(alignment: .top) {
            WebViewRepresentable(url: url, model: model)
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
final class WebModel: NSObject, ObservableObject, WKNavigationDelegate {
    @Published var progress: Double = 0
    @Published var error: String?
    weak var web: WKWebView?
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
        // target=_blank links: load them in this view
        if action.targetFrame == nil { webView.load(action.request); return .cancel }
        return .allow
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
    let model: WebModel

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        cfg.websiteDataStore = .default()
        let hideTabbar = WKUserScript(
            source: "document.documentElement.setAttribute('data-ab-notab','');document.documentElement.setAttribute('data-ab-app','ios');",
            injectionTime: .atDocumentStart, forMainFrameOnly: true)
        cfg.userContentController.addUserScript(hideTabbar)
        let w = WKWebView(frame: .zero, configuration: cfg)
        w.navigationDelegate = model
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
