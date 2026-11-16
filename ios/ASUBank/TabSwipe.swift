import SwiftUI
import UIKit

/// Swipe left or right on a tab's first screen to move to the next or previous tab.
/// One pan recognizer on the window, so it covers every tab without wrapping each screen. It stays out of the way of:
/// pushed screens (the back swipe), sheets, sideways-scrolling rows, sliders, text fields and rows swiped open for their actions.
struct TabSwipe: UIViewRepresentable {
    /// +1 for the next tab, -1 for the previous one
    var move: (Int) -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> Probe {
        let v = Probe()
        v.isUserInteractionEnabled = false
        v.coordinator = context.coordinator
        return v
    }

    func updateUIView(_ uiView: Probe, context: Context) { context.coordinator.move = move }

    static func dismantleUIView(_ uiView: Probe, coordinator: Coordinator) { coordinator.detach() }

    /// Only there to learn which window the tabs are in
    final class Probe: UIView {
        weak var coordinator: Coordinator?
        override func didMoveToWindow() {
            super.didMoveToWindow()
            if let w = window { coordinator?.attach(to: w) }
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var move: (Int) -> Void = { _ in }
        private weak var window: UIWindow?
        private weak var pan: UIPanGestureRecognizer?
        /// The list the swipe started on, to see afterwards whether one of its rows was swiped open
        private weak var list: UIScrollView?
        /// A row showed its swipe actions at some point during this swipe
        private var rowSwiped = false

        func attach(to w: UIWindow) {
            guard window !== w else { return }
            detach()
            let g = UIPanGestureRecognizer(target: self, action: #selector(panned(_:)))
            g.delegate = self
            g.cancelsTouchesInView = false
            g.delaysTouchesBegan = false
            g.delaysTouchesEnded = false
            g.maximumNumberOfTouches = 1
            w.addGestureRecognizer(g)
            window = w
            pan = g
        }

        func detach() {
            if let g = pan { g.view?.removeGestureRecognizer(g) }
            pan = nil
            window = nil
        }

        @objc private func panned(_ g: UIPanGestureRecognizer) {
            if g.state == .began { rowSwiped = false }
            if g.state == .changed, !rowSwiped, let l = list, Self.rowSwipedOpen(in: l, depth: 0) { rowSwiped = true }
            guard g.state == .ended, let w = window else { return }
            let t = g.translation(in: w), v = g.velocity(in: w)
            // clearly sideways, and either far enough or a flick
            guard abs(t.x) > 70, abs(t.x) > abs(t.y) * 2 else { return }
            guard abs(t.x) > w.bounds.width * 0.3 || abs(v.x) > 250 else { return }
            if rowSwiped { return }
            if let l = list, Self.rowSwipedOpen(in: l, depth: 0) { return }
            let rtl = UIApplication.shared.userInterfaceLayoutDirection == .rightToLeft
            move((t.x < 0) != rtl ? 1 : -1)
        }

        /// UIKit adds a "swipe action pull view" to a list while one of its rows shows its actions
        private static func rowSwipedOpen(in view: UIView, depth: Int) -> Bool {
            if String(describing: type(of: view)).contains("SwipeAction") { return true }
            guard depth < 2 else { return false }
            return view.subviews.contains { rowSwipedOpen(in: $0, depth: depth + 1) }
        }

        func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
            guard let p = g as? UIPanGestureRecognizer, let w = window else { return false }
            let v = p.velocity(in: w)
            guard abs(v.x) > abs(v.y) * 1.5 else { return false }
            // nothing presented over the tabs (sheets, covers, alerts, menus)
            guard let root = w.rootViewController, root.presentedViewController == nil else { return false }
            guard let hit = w.hitTest(p.location(in: w), with: nil) else { return false }
            list = nil
            var view: UIView? = hit
            while let x = view {
                if x is UISlider || x is UITextField || x is UITextView || x is UITabBar || x is UINavigationBar || x is UIPickerView { return false }
                if String(describing: type(of: x)).contains("MKMapView") { return false }
                if let s = x as? UIScrollView {
                    if s.contentSize.width > s.bounds.width + 1 { return false }   // a row that scrolls sideways keeps its swipe
                    if list == nil { list = s }
                }
                view = x.superview
            }
            // only on a tab's first screen: deeper screens keep the swipe for going back
            var r: UIResponder? = hit
            while let x = r {
                if let n = x as? UINavigationController, n.viewControllers.count > 1 { return false }
                r = x.next
            }
            return true
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
    }
}
