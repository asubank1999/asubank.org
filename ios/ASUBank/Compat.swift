import SwiftUI
import UIKit

// iOS 15 support. NavigationStack, NavigationLink(value:), navigationDestination, ShareLink and
// Color.gradient are iOS 16 only; these wrappers use them on iOS 16+ and the iOS 15 way otherwise.
// (Every iPhone that runs iOS 14 can update to iOS 15, so 15 reaches the same phones.)

/// A tab's navigation container
struct ABStack<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        if #available(iOS 16.0, *) {
            NavigationStack { content() }
        } else {
            NavigationView { content() }.navigationViewStyle(.stack)
        }
    }
}

/// Builds the destination only when it is opened (iOS 15 builds NavigationLink destinations eagerly)
struct Lazy<V: View>: View {
    let build: () -> V
    init(_ build: @escaping () -> V) { self.build = build }
    var body: V { build() }
}

/// NavigationLink(value:) on iOS 16, a destination link on iOS 15
struct RouteLink<Label: View>: View {
    let route: Route
    let label: Label
    init(_ route: Route, @ViewBuilder label: () -> Label) {
        self.route = route
        self.label = label()
    }
    var body: some View {
        if #available(iOS 16.0, *) {
            NavigationLink(value: route) { label }
        } else {
            NavigationLink(destination: Lazy { RouteView(route: route) }) { label }
        }
    }
}

private struct Destinations: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.navigationDestination(for: Route.self) { RouteView(route: $0) }
        } else {
            content
        }
    }
}

private struct PushWhen<D: View>: ViewModifier {
    @Binding var isPresented: Bool
    let destination: () -> D
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.navigationDestination(isPresented: $isPresented, destination: destination)
        } else {
            content.background(NavigationLink(destination: Lazy(destination), isActive: $isPresented) { EmptyView() }.hidden())
        }
    }
}

private struct KeyboardDismiss: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) { content.scrollDismissesKeyboard(.interactively) } else { content }
    }
}

extension View {
    /// Every tab's stack understands the same routes
    func abDestinations() -> some View { modifier(Destinations()) }

    /// Pushes a screen when `isPresented` turns true
    func push<D: View>(isPresented: Binding<Bool>, @ViewBuilder destination: @escaping () -> D) -> some View {
        modifier(PushWhen(isPresented: isPresented, destination: destination))
    }

    func dismissKeyboardOnScroll() -> some View { modifier(KeyboardDismiss()) }
}

extension Color {
    /// Like iOS 16's Color.gradient: a soft top-to-bottom gradient of this color
    var abGradient: LinearGradient {
        LinearGradient(colors: [self.opacity(0.82), self], startPoint: .top, endPoint: .bottom)
    }
}

/// ShareLink for iOS 15 and later: shows the system share sheet
struct ShareButton<Label: View>: View {
    let items: [Any]
    let label: Label

    init(item: Any, subject: Text? = nil, message: String? = nil, @ViewBuilder label: () -> Label) {
        var items: [Any] = []
        if let message { items.append(message) }
        items.append(item)
        self.items = items
        self.label = label()
    }

    var body: some View {
        Button { Share.present(items) } label: { label }
    }
}

enum Share {
    static func present(_ items: [Any]) {
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?.rootViewController else { return }
        var top = root
        while let p = top.presentedViewController { top = p }
        let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if let pop = vc.popoverPresentationController {
            pop.sourceView = top.view
            pop.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
            pop.permittedArrowDirections = []
        }
        top.present(vc, animated: true)
    }
}
