import SwiftUI
import UIKit
import WebKit
import AVFoundation

// iOS 14 build only (branch ios14, made by MyApps/tools/ios14/make_ios14.py).
// The normal app needs iOS 15. This build swaps iOS 15-only SwiftUI calls for these helpers:
// on iOS 15+ they call the real thing, on iOS 14 they use an older equivalent or do nothing.

// MARK: - Colors and styles

extension Color {
    static var tertiary14: Color { Color(UIColor.tertiaryLabel) }
    static var quaternary14: Color { Color(UIColor.quaternaryLabel) }
    /// stands in for the blurred materials (.bar, .thinMaterial...) that iOS 14 SwiftUI doesn't have
    static var mat14: Color { Color(UIColor.secondarySystemBackground).opacity(0.94) }
}

extension Font {
    func monoDigit14() -> Font { monospacedDigit() }
    func monoFont14() -> Font {
        if #available(iOS 15, *) { return monospaced() }
        return self
    }
}

/// Replacement for AnyShapeStyle (iOS 15): a colour or a gradient
struct AnyStyle14 {
    var color: Color?
    var gradient: LinearGradient?
    init(_ c: Color) { color = c }
    init(_ g: LinearGradient) { gradient = g }
}

extension Shape {
    @ViewBuilder func fill14(_ s: AnyStyle14) -> some View {
        if let c = s.color { self.fill(c) } else if let g = s.gradient { self.fill(g) }
    }
    @ViewBuilder func stroke(_ s: AnyStyle14, lineWidth: CGFloat = 1) -> some View {
        if let c = s.color { self.stroke(c, lineWidth: lineWidth) } else if let g = s.gradient { self.stroke(g, lineWidth: lineWidth) }
    }
}

// MARK: - View helpers

enum Vis14 { case automatic, visible, hidden }
enum HEdge14 { case leading, trailing }
enum VEdge14 { case top, bottom }

extension View {
    // foregroundStyle
    @ViewBuilder func fg14(_ c: Color) -> some View {
        if #available(iOS 15, *) { self.foregroundStyle(c) } else { self.foregroundColor(c) }
    }
    @ViewBuilder func fg14(_ a: Color, _ b: Color) -> some View {
        if #available(iOS 15, *) { self.foregroundStyle(a, b) } else { self.foregroundColor(a) }
    }
    @ViewBuilder func fg14<S: ShapeStyle>(_ s: S) -> some View {
        if #available(iOS 15, *) { self.foregroundStyle(s) } else { self.overlay(Rectangle().fill(s)).mask(self) }
    }

    // background(_:in:)
    func bgIn14<S: Shape>(_ c: Color, in shape: S) -> some View { background(shape.fill(c)) }
    func bgIn14<G: ShapeStyle, S: Shape>(_ g: G, in shape: S) -> some View { background(shape.fill(g)) }
    func bgIn14<S: Shape>(_ s: AnyStyle14, in shape: S) -> some View { background(shape.fill14(s)) }

    // overlay(alignment:content:)
    func overlay14<V: View>(alignment: Alignment = .center, @ViewBuilder _ content: () -> V) -> some View {
        overlay(content(), alignment: alignment)
    }

    // tint
    @ViewBuilder func tint14(_ c: Color?) -> some View {
        if #available(iOS 15, *) { self.tint(c) } else { self.accentColor(c) }
    }

    // task
    func task14(priority: TaskPriority = .userInitiated, _ action: @escaping () async -> Void) -> some View {
        modifier(Task14(id: 0, action: action))
    }
    func task14<T: Equatable>(id: T, priority: TaskPriority = .userInitiated, _ action: @escaping () async -> Void) -> some View {
        modifier(Task14(id: id, action: action))
    }

    // refreshable
    @ViewBuilder func refreshable14(action: @escaping () async -> Void) -> some View {
        if #available(iOS 15, *) { self.refreshable(action: action) } else { self }
    }

    // searchable
    @ViewBuilder func searchable14(text: Binding<String>, prompt: LocalizedStringKey) -> some View {
        if #available(iOS 15, *) { self.searchable(text: text, prompt: prompt) } else { Search14(content: self, text: text, prompt: prompt) }
    }
    @ViewBuilder func searchable14(text: Binding<String>, prompt: Text) -> some View {
        if #available(iOS 15, *) { self.searchable(text: text, prompt: prompt) } else { Search14(content: self, text: text, prompt: "Search") }
    }
    @ViewBuilder func searchable14(text: Binding<String>) -> some View {
        if #available(iOS 15, *) { self.searchable(text: text) } else { Search14(content: self, text: text, prompt: "Search") }
    }
    @ViewBuilder func onSubmitSearch14(_ action: @escaping () -> Void) -> some View {
        if #available(iOS 15, *) { self.onSubmit(of: .search, action) } else { self }
    }
    @ViewBuilder func onSubmit14(_ action: @escaping () -> Void) -> some View {
        if #available(iOS 15, *) { self.onSubmit(action) } else { self }
    }

    // swipeActions: a long-press menu on iOS 14
    @ViewBuilder func swipeActions14<V: View>(edge: HEdge14 = .trailing, allowsFullSwipe: Bool = true, @ViewBuilder content: () -> V) -> some View {
        if #available(iOS 15, *) { self.swipeActions(edge: edge == .leading ? .leading : .trailing, allowsFullSwipe: allowsFullSwipe, content: content) } else { self.contextMenu { content() } }
    }

    // safeAreaInset
    @ViewBuilder func safeAreaInset14<V: View>(edge: VEdge14, spacing: CGFloat? = nil, @ViewBuilder content: () -> V) -> some View {
        if #available(iOS 15, *) {
            self.safeAreaInset(edge: edge == .top ? .top : .bottom, spacing: spacing, content: content)
        } else if edge == .top {
            VStack(spacing: 0) { content(); self }
        } else {
            VStack(spacing: 0) { self; content() }
        }
    }

    // alert and confirmationDialog: a small sheet with the same buttons on iOS 14
    @ViewBuilder func alert14<A: View, M: View>(_ title: LocalizedStringKey, isPresented: Binding<Bool>, @ViewBuilder actions: () -> A, @ViewBuilder message: () -> M) -> some View {
        if #available(iOS 15, *) { self.alert(title, isPresented: isPresented, actions: actions, message: message) }
        else { self.sheet(isPresented: isPresented) { Dialog14(title: Text(title), actions: actions(), message: message(), isPresented: isPresented) } }
    }
    @ViewBuilder func alert14<A: View>(_ title: LocalizedStringKey, isPresented: Binding<Bool>, @ViewBuilder actions: () -> A) -> some View {
        if #available(iOS 15, *) { self.alert(title, isPresented: isPresented, actions: actions) }
        else { self.sheet(isPresented: isPresented) { Dialog14(title: Text(title), actions: actions(), message: EmptyView(), isPresented: isPresented) } }
    }
    @ViewBuilder func alert14<S: StringProtocol, A: View, M: View>(_ title: S, isPresented: Binding<Bool>, @ViewBuilder actions: () -> A, @ViewBuilder message: () -> M) -> some View {
        if #available(iOS 15, *) { self.alert(title, isPresented: isPresented, actions: actions, message: message) }
        else { self.sheet(isPresented: isPresented) { Dialog14(title: Text(title), actions: actions(), message: message(), isPresented: isPresented) } }
    }
    @ViewBuilder func alert14<S: StringProtocol, A: View>(_ title: S, isPresented: Binding<Bool>, @ViewBuilder actions: () -> A) -> some View {
        if #available(iOS 15, *) { self.alert(title, isPresented: isPresented, actions: actions) }
        else { self.sheet(isPresented: isPresented) { Dialog14(title: Text(title), actions: actions(), message: EmptyView(), isPresented: isPresented) } }
    }
    @ViewBuilder func confirmationDialog14<A: View>(_ title: LocalizedStringKey, isPresented: Binding<Bool>, titleVisibility: Vis14 = .automatic, @ViewBuilder actions: () -> A) -> some View {
        if #available(iOS 15, *) {
            self.confirmationDialog(title, isPresented: isPresented, titleVisibility: titleVisibility == .visible ? .visible : titleVisibility == .hidden ? .hidden : .automatic, actions: actions)
        } else {
            self.sheet(isPresented: isPresented) { Dialog14(title: Text(title), actions: actions(), message: EmptyView(), isPresented: isPresented) }
        }
    }
    @ViewBuilder func confirmationDialog14<A: View, M: View>(_ title: LocalizedStringKey, isPresented: Binding<Bool>, titleVisibility: Vis14 = .automatic, @ViewBuilder actions: () -> A, @ViewBuilder message: () -> M) -> some View {
        if #available(iOS 15, *) {
            self.confirmationDialog(title, isPresented: isPresented, titleVisibility: titleVisibility == .visible ? .visible : titleVisibility == .hidden ? .hidden : .automatic, actions: actions, message: message)
        } else {
            self.sheet(isPresented: isPresented) { Dialog14(title: Text(title), actions: actions(), message: message(), isPresented: isPresented) }
        }
    }

    @ViewBuilder func confirmationDialog14<S: StringProtocol, A: View>(_ title: S, isPresented: Binding<Bool>, titleVisibility: Vis14 = .automatic, @ViewBuilder actions: () -> A) -> some View {
        if #available(iOS 15, *) {
            self.confirmationDialog(title, isPresented: isPresented, titleVisibility: titleVisibility == .visible ? .visible : titleVisibility == .hidden ? .hidden : .automatic, actions: actions)
        } else {
            self.sheet(isPresented: isPresented) { Dialog14(title: Text(title), actions: actions(), message: EmptyView(), isPresented: isPresented) }
        }
    }
    @ViewBuilder func confirmationDialog14<S: StringProtocol, A: View, M: View>(_ title: S, isPresented: Binding<Bool>, titleVisibility: Vis14 = .automatic, @ViewBuilder actions: () -> A, @ViewBuilder message: () -> M) -> some View {
        if #available(iOS 15, *) {
            self.confirmationDialog(title, isPresented: isPresented, titleVisibility: titleVisibility == .visible ? .visible : titleVisibility == .hidden ? .hidden : .automatic, actions: actions, message: message)
        } else {
            self.sheet(isPresented: isPresented) { Dialog14(title: Text(title), actions: actions(), message: message(), isPresented: isPresented) }
        }
    }
    @ViewBuilder func confirmationDialog14<S: StringProtocol, T, A: View, M: View>(_ title: S, isPresented: Binding<Bool>, titleVisibility: Vis14 = .automatic, presenting data: T?, @ViewBuilder actions: @escaping (T) -> A, @ViewBuilder message: @escaping (T) -> M) -> some View {
        if #available(iOS 15, *) {
            self.confirmationDialog(title, isPresented: isPresented, titleVisibility: titleVisibility == .visible ? .visible : titleVisibility == .hidden ? .hidden : .automatic, presenting: data, actions: actions, message: message)
        } else {
            self.sheet(isPresented: isPresented) {
                if let d = data { Dialog14(title: Text(title), actions: actions(d), message: message(d), isPresented: isPresented) }
            }
        }
    }
    @ViewBuilder func confirmationDialog14<S: StringProtocol, T, A: View>(_ title: S, isPresented: Binding<Bool>, titleVisibility: Vis14 = .automatic, presenting data: T?, @ViewBuilder actions: @escaping (T) -> A) -> some View {
        if #available(iOS 15, *) {
            self.confirmationDialog(title, isPresented: isPresented, titleVisibility: titleVisibility == .visible ? .visible : titleVisibility == .hidden ? .hidden : .automatic, presenting: data, actions: actions)
        } else {
            self.sheet(isPresented: isPresented) {
                if let d = data { Dialog14(title: Text(title), actions: actions(d), message: EmptyView(), isPresented: isPresented) }
            }
        }
    }

    // button styles
    @ViewBuilder func borderedButton14() -> some View {
        if #available(iOS 15, *) { self.buttonStyle(.bordered) } else { self.buttonStyle(Bordered14(prominent: false)) }
    }
    @ViewBuilder func prominentButton14() -> some View {
        if #available(iOS 15, *) { self.buttonStyle(.borderedProminent) } else { self.buttonStyle(Bordered14(prominent: true)) }
    }

    // small iOS 15 extras that iOS 14 simply skips
    @ViewBuilder func monoDigit14() -> some View {
        if #available(iOS 15, *) { self.monospacedDigit() } else { self }
    }
    @ViewBuilder func badge14(_ n: Int) -> some View {
        if #available(iOS 15, *) { self.badge(n) } else { self }
    }
    @ViewBuilder func listRowSepHidden14() -> some View {
        if #available(iOS 15, *) { self.listRowSeparator(.hidden) } else { self }
    }
    @ViewBuilder func noDismiss14(_ on: Bool = true) -> some View {
        if #available(iOS 15, *) { self.interactiveDismissDisabled(on) } else { self }
    }
    @ViewBuilder func palette14() -> some View {
        if #available(iOS 15, *) { self.symbolRenderingMode(.palette) } else { self }
    }
    func focused14(_ b: Binding<Bool>) -> some View { self }
}

struct Task14<ID: Equatable>: ViewModifier {
    let id: ID
    let action: () async -> Void
    @State private var task: Task<Void, Never>?

    func body(content: Content) -> some View {
        content
            .onAppear { task?.cancel(); task = Task { await action() } }
            .onDisappear { task?.cancel(); task = nil }
            .onChange(of: id) { _ in task?.cancel(); task = Task { await action() } }
    }
}

private struct Search14<C: View>: View {
    let content: C
    @Binding var text: String
    let prompt: LocalizedStringKey
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass").foregroundColor(.secondary)
                TextField(prompt, text: $text).disableAutocorrection(true)
                if !text.isEmpty {
                    Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(.secondary) }.buttonStyle(BorderlessButtonStyle())
                }
            }
            .padding(9)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color(UIColor.tertiarySystemFill)))
            .padding(.horizontal, 16).padding(.vertical, 8)
            content
        }
    }
}

struct Dialog14<A: View, M: View>: View {
    let title: Text
    let actions: A
    let message: M
    @Binding var isPresented: Bool
    var body: some View {
        VStack(spacing: 16) {
            title.font(.headline).multilineTextAlignment(.center)
            message.font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center)
            VStack(spacing: 10) { actions }
                .buttonStyle(Bordered14(prominent: true))
                .simultaneousGesture(TapGesture().onEnded { isPresented = false })
            Button("Close") { isPresented = false }.buttonStyle(Bordered14(prominent: false))
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct Bordered14: ButtonStyle {
    var prominent: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .padding(.horizontal, 16).padding(.vertical, 10)
            .frame(minWidth: 44)
            .foregroundColor(prominent ? .white : .accentColor)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(prominent ? Color.accentColor : Color.accentColor.opacity(0.15)))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Section with the iOS 15 initializers (trailing header/footer closures, a title string)
struct Section14<P: View, C: View, F: View>: View {
    let header: P
    let content: C
    let footer: F
    var body: some View { Section(header: header, footer: footer) { content } }
}

extension Section14 where P == EmptyView, F == EmptyView {
    init(@ViewBuilder content: () -> C) { header = EmptyView(); self.content = content(); footer = EmptyView() }
}
extension Section14 where F == EmptyView {
    init(@ViewBuilder content: () -> C, @ViewBuilder header: () -> P) { self.header = header(); self.content = content(); footer = EmptyView() }
    init(header: P, @ViewBuilder content: () -> C) { self.header = header; self.content = content(); footer = EmptyView() }
}
extension Section14 where P == EmptyView {
    init(@ViewBuilder content: () -> C, @ViewBuilder footer: () -> F) { header = EmptyView(); self.content = content(); self.footer = footer() }
    init(footer: F, @ViewBuilder content: () -> C) { header = EmptyView(); self.content = content(); self.footer = footer }
}
extension Section14 {
    init(@ViewBuilder content: () -> C, @ViewBuilder header: () -> P, @ViewBuilder footer: () -> F) { self.header = header(); self.content = content(); self.footer = footer() }
    init(header: P, footer: F, @ViewBuilder content: () -> C) { self.header = header; self.content = content(); self.footer = footer }
}
extension Section14 where P == Text, F == EmptyView {
    init(_ title: LocalizedStringKey, @ViewBuilder content: () -> C) { header = Text(title); self.content = content(); footer = EmptyView() }
    init<S: StringProtocol>(_ title: S, @ViewBuilder content: () -> C) { header = Text(title); self.content = content(); footer = EmptyView() }
}

/// AsyncImage (iOS 15)
struct Phase14 { var image: Image? }

struct AsyncImage14<V: View>: View {
    let url: URL?
    let content: (Phase14) -> V
    @State private var image: UIImage?
    init(url: URL?, @ViewBuilder content: @escaping (Phase14) -> V) { self.url = url; self.content = content }
    var body: some View {
        content(Phase14(image: image.map { Image(uiImage: $0) }))
            .task14(id: url) {
                guard let url, let (d, _) = try? await URLSession.shared.data14(from: url) else { return }
                image = UIImage(data: d)
            }
    }
}

/// TimelineView(.animation) (iOS 15): a 30 fps clock
struct Tick14 { var date: Date }

struct TimelineView14<V: View>: View {
    let content: (Tick14) -> V
    @State private var now = Date()
    private let timer = Timer.publish(every: 1.0 / 30, on: .main, in: .common).autoconnect()
    init(@ViewBuilder content: @escaping (Tick14) -> V) { self.content = content }
    var body: some View { content(Tick14(date: now)).onReceive(timer) { now = $0 } }
}

// MARK: - Foundation

extension URLSession {
    func data14(for req: URLRequest) async throws -> (Data, URLResponse) {
        if #available(iOS 15, *) { return try await data(for: req) }
        return try await withCheckedThrowingContinuation { c in
            dataTask(with: req) { d, r, e in
                if let d, let r { c.resume(returning: (d, r)) } else { c.resume(throwing: e ?? URLError(.badServerResponse)) }
            }.resume()
        }
    }
    func data14(from url: URL) async throws -> (Data, URLResponse) { try await data14(for: URLRequest(url: url)) }
    func upload14(for req: URLRequest, from body: Data, delegate: URLSessionTaskDelegate? = nil) async throws -> (Data, URLResponse) {
        if #available(iOS 15, *) { return try await upload(for: req, from: body, delegate: delegate) }
        return try await withCheckedThrowingContinuation { c in
            uploadTask(with: req, from: body) { d, r, e in
                if let r { c.resume(returning: (d ?? Data(), r)) } else { c.resume(throwing: e ?? URLError(.badServerResponse)) }
            }.resume()
        }
    }
}

extension URLSession {
    func download14(from url: URL) async throws -> (URL, URLResponse) {
        if #available(iOS 15, *) { return try await download(from: url) }
        return try await withCheckedThrowingContinuation { c in
            downloadTask(with: url) { tmp, r, e in
                guard let tmp, let r else { c.resume(throwing: e ?? URLError(.badServerResponse)); return }
                // the system deletes tmp when this handler returns, so keep a copy
                let keep = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "-" + tmp.lastPathComponent)
                do { try FileManager.default.moveItem(at: tmp, to: keep); c.resume(returning: (keep, r)) } catch { c.resume(throwing: error) }
            }.resume()
        }
    }
}

extension WKWebView {
    @MainActor func callJS14(_ js: String, arguments: [String: Any], in frame: WKFrameInfo?) async throws -> Any? {
        if #available(iOS 15, *) { return try await callAsyncJavaScript(js, arguments: arguments, in: frame, contentWorld: .page) }
        return try await withCheckedThrowingContinuation { c in
            callAsyncJavaScript(js, arguments: arguments, in: frame, in: .page) { r in
                switch r { case .success(let v): c.resume(returning: v); case .failure(let e): c.resume(throwing: e) }
            }
        }
    }
}

extension Date {
    func fmt14(_ date: DateFormatter.Style, _ time: DateFormatter.Style) -> String {
        let f = DateFormatter(); f.locale = AppLanguage.locale; f.dateStyle = date; f.timeStyle = time
        return f.string(from: self)
    }
    func fmtT14(_ template: String) -> String {
        let f = DateFormatter(); f.locale = AppLanguage.locale; f.setLocalizedDateFormatFromTemplate(template)
        return f.string(from: self)
    }
    func rel14() -> String {
        let f = RelativeDateTimeFormatter(); f.locale = AppLanguage.locale; f.dateTimeStyle = .named
        return f.localizedString(for: self, relativeTo: Date())
    }
}
