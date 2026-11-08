import SwiftUI
import WebKit

/// Talks natively to an UniHub Google Apps Script web app (ratings, course matcher, material requests).
/// Those scripts only answer their own page through google.script.run, so a hidden web view loads the
/// page, a script injected into every frame reports the frame that holds the form (found by `marker`,
/// an element id on that page), and `call` runs script functions inside that frame.
@MainActor
class ScriptBridge: NSObject, ObservableObject, WKScriptMessageHandler, WKNavigationDelegate {
    enum Status: Equatable { case connecting, ready, failed }

    @Published var status: Status = .connecting

    let url: URL
    let web: WKWebView
    private let handler: String
    private var frame: WKFrameInfo?
    private var timeout: Task<Void, Never>?

    init(url: String, marker: String) {
        self.url = URL(string: url)!
        handler = "ab_" + marker
        let config = WKWebViewConfiguration()
        let probe = """
        (function(){var n=0;function go(){try{if(window.google&&google.script&&google.script.run&&document.getElementById('\(marker)')){window.webkit.messageHandlers.\(handler).postMessage('ready');return;}}catch(e){}if(++n<160)setTimeout(go,250);}go();})();
        """
        config.userContentController.addUserScript(WKUserScript(source: probe, injectionTime: .atDocumentEnd, forMainFrameOnly: false))
        // full size (kept behind the screen): iOS throttles tiny or off-screen pages
        web = WKWebView(frame: CGRect(x: 0, y: 0, width: 320, height: 480), configuration: config)
        super.init()
        config.userContentController.add(WeakHandler(self), name: handler)
        web.navigationDelegate = self
    }

    func connect(force: Bool = false) {
        guard force || status == .failed || (status == .connecting && frame == nil && !web.isLoading) else { return }
        status = .connecting
        frame = nil
        web.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
        timeout?.cancel()
        timeout = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 45_000_000_000)
            if let self, self.status == .connecting { self.status = .failed }
        }
    }

    /// Called when the page (re)connects
    func didConnect(first: Bool) async {}

    private func waitReady(_ seconds: Double) async -> Bool {
        let end = Date().addingTimeInterval(seconds)
        while status != .ready, Date() < end { try? await Task.sleep(nanoseconds: 200_000_000) }
        return status == .ready
    }

    nonisolated func userContentController(_ c: WKUserContentController, didReceive message: WKScriptMessage) {
        let info = message.frameInfo
        Task { @MainActor in
            // always use the newest frame: Google sometimes reloads it, and the old one stops answering
            let first = self.status != .ready
            self.frame = info
            self.timeout?.cancel()
            self.status = .ready
            await self.didConnect(first: first)
        }
    }

    nonisolated func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        Task { @MainActor in if self.status == .connecting { self.status = .failed } }
    }

    nonisolated func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        Task { @MainActor in if self.status == .connecting { self.status = .failed } }
    }

    /// Calls a function of the Apps Script and returns its JSON result. Reconnects and retries once
    /// when the page stopped answering.
    func call(_ fn: String, _ args: [Any], timeout seconds: Double = 25) async throws -> Any? {
        do {
            return try await callOnce(fn, args, timeout: seconds)
        } catch let e as NSError where e.domain == "bridge" {
            connect(force: true)
            guard await waitReady(30) else { throw e }
            return try await callOnce(fn, args, timeout: seconds)
        }
    }

    func callOnce(_ fn: String, _ args: [Any], timeout seconds: Double = 25) async throws -> Any? {
        if status != .ready { _ = await waitReady(30) }
        guard let frame else { throw NSError(domain: "bridge", code: 1, userInfo: [NSLocalizedDescriptionKey: "Couldn't connect. Check your connection and try again."]) }
        return try await withThrowingTaskGroup(of: Any?.self) { group in
            group.addTask { @MainActor in try await self.run(fn, args, in: frame) }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                throw NSError(domain: "bridge", code: 2, userInfo: [NSLocalizedDescriptionKey: "The server didn't answer. Try again."])
            }
            let first = try await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    private func run(_ fn: String, _ args: [Any], in frame: WKFrameInfo) async throws -> Any? {
        let js = """
        return await new Promise(function(res, rej){
          var r = google.script.run.withSuccessHandler(function(v){ res(JSON.stringify(v === undefined ? null : v)); })
            .withFailureHandler(function(e){ rej(new Error(e && e.message ? e.message : String(e))); });
          r[fn].apply(r, args);
        });
        """
        let out: Any?
        do {
            out = try await web.callJS14(js, arguments: ["fn": fn, "args": args], in: frame)
        } catch let e as NSError where e.domain == WKErrorDomain && e.code != WKError.Code.javaScriptExceptionOccurred.rawValue {
            // the frame went away (e.g. Google reloaded it): reconnect. A script error (the server
            // refusing something) is a real answer and is passed on as it is.
            throw NSError(domain: "bridge", code: 3, userInfo: [NSLocalizedDescriptionKey: e.localizedDescription])
        }
        guard let s = out as? String, let data = s.data(using: .utf8) else { return nil }
        return try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
    }

    /// A readable message for a failed call
    static func message(_ error: Error) -> String {
        let msg = (error as NSError).userInfo["WKJavaScriptExceptionMessage"] as? String ?? error.localizedDescription
        return msg.replacingOccurrences(of: "Error: ", with: "")
    }
}

/// Avoids a retain cycle between the web view's content controller and the bridge
final class WeakHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ t: WKScriptMessageHandler) { target = t }
    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) { target?.userContentController(c, didReceive: m) }
}

/// Puts a bridge's page behind a screen (invisible, not touchable) so iOS keeps it running
struct BridgeHost: ViewModifier {
    let bridge: ScriptBridge
    func body(content: Content) -> some View {
        content
            .background(HiddenWeb(web: bridge.web).opacity(0.001).allowsHitTesting(false).accessibilityHidden(true))
            .onAppear { bridge.connect() }
    }
}

extension View {
    func bridgeHost(_ bridge: ScriptBridge) -> some View { modifier(BridgeHost(bridge: bridge)) }
}

private struct HiddenWeb: UIViewRepresentable {
    let web: WKWebView
    func makeUIView(context: Context) -> WKWebView { web }
    func updateUIView(_ v: WKWebView, context: Context) {}
}
