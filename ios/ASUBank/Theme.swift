import SwiftUI
import UIKit

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xff) / 255, green: CGFloat((hex >> 8) & 0xff) / 255, blue: CGFloat(hex & 0xff) / 255, alpha: alpha)
    }
}

/// Colors from the website's design system (assets/app/app.css)
extension Color {
    static var abAccent: Color { AppTheme.accent.color }
    static var abAccentSoft: Color { AppTheme.accent.soft }
    static let abGold = Color(UIColor(hex: 0xFFC627))
    static var abBackground: Color { AppTheme.appearance == 3 ? .black : Color(UIColor.systemGroupedBackground) }
    static let abSurface = Color(UIColor.secondarySystemGroupedBackground)
}

extension Kind {
    var color: Color {
        switch self {
        case .folder: return Color(UIColor(hex: 0x0a84ff))
        case .pdf: return Color(UIColor(hex: 0xe5372d))
        case .doc: return Color(UIColor(hex: 0x2b6fd6))
        case .ppt: return Color(UIColor(hex: 0xe8590c))
        case .sheet: return Color(UIColor(hex: 0x1f8a3e))
        case .img: return Color(UIColor(hex: 0xaf52de))
        case .video: return Color(UIColor(hex: 0xff2d55))
        case .zip: return Color(UIColor(hex: 0x8e8e93))
        case .file, .link: return Color(UIColor(hex: 0x5e5ce6))
        case .page: return .abAccent
        }
    }
}

/// Rounded colored icon like the website's file tiles
struct KindTile: View {
    let kind: Kind
    var size: CGFloat = 34
    var body: some View {
        Image(systemName: kind.symbol)
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(kind.color.abGradient, in: RoundedRectangle(cornerRadius: size * 0.26, style: .continuous))
    }
}

struct Badge: View {
    let text: String
    var color: Color = .abAccent
    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .bold))
            .padding(.horizontal, 6).padding(.vertical, 2)
            .foregroundStyle(color)
            .background(color.opacity(0.14), in: Capsule())
    }
}

struct Chip: View {
    let title: String
    let on: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14).padding(.vertical, 7)
                .foregroundStyle(on ? Color.white : Color.primary)
                .background(on ? AnyShapeStyle(Color.abAccent) : AnyShapeStyle(Color.abSurface), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct ChipBar<T: Hashable>: View {
    let options: [(T, String)]
    @Binding var selection: T
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(options.indices, id: \.self) { i in
                    let o = options[i]
                    Chip(title: o.1, on: selection == o.0) {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { selection = o.0 }
                        UISelectionFeedbackGenerator().selectionChanged()
                    }
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 4)
        }
    }
}

enum Haptics {
    static func tap() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func success() { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func error() { UINotificationFeedbackGenerator().notificationOccurred(.error) }
}

/// App theme: appearance (system, light, dark, pitch black) and the accent color, from More > Theme
enum AppTheme {
    struct Accent: Identifiable {
        let id: Int
        let name: String
        let light: UInt32
        let dark: UInt32
        var color: Color { Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) }) }
        var soft: Color { Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark, alpha: 0.16) : UIColor(hex: light, alpha: 0.1) }) }
    }

    static let accents: [Accent] = [
        .init(id: 0, name: "ASU maroon", light: 0x8C1D40, dark: 0xff6f9a),
        .init(id: 1, name: "Ocean", light: 0x0a63c9, dark: 0x4da3ff),
        .init(id: 2, name: "Forest", light: 0x1f7a3a, dark: 0x4cd07a),
        .init(id: 3, name: "Violet", light: 0x6e3fd1, dark: 0xb18cff),
        .init(id: 4, name: "Sunset", light: 0xd2510a, dark: 0xff9a52),
        .init(id: 5, name: "Teal", light: 0x00847f, dark: 0x3fd6cf),
        .init(id: 6, name: "Graphite", light: 0x3a3a3c, dark: 0xd1d1d6),
    ]

    /// 0 system, 1 light, 2 dark, 3 pitch black (OLED)
    static var appearance: Int { UserDefaults.standard.integer(forKey: "ab.appearance") }
    static var accent: Accent { accents.first { $0.id == UserDefaults.standard.integer(forKey: "ab.accent") } ?? accents[0] }

    static var scheme: ColorScheme? { appearance == 1 ? .light : appearance >= 2 ? .dark : nil }

    /// Bar colors (pitch black makes the tab and navigation bars pure black too)
    static func applyBars() {
        let nav = UINavigationBarAppearance()
        let tab = UITabBarAppearance()
        if appearance == 3 {
            nav.configureWithOpaqueBackground(); nav.backgroundColor = .black; nav.shadowColor = .clear
            tab.configureWithOpaqueBackground(); tab.backgroundColor = .black
            UITableView.appearance().backgroundColor = .black
        } else {
            nav.configureWithDefaultBackground()
            tab.configureWithDefaultBackground()
            UITableView.appearance().backgroundColor = nil
        }
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav
        if appearance == 3 { UINavigationBar.appearance().scrollEdgeAppearance = nav } else { UINavigationBar.appearance().scrollEdgeAppearance = nil }
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = appearance == 3 ? tab : nil
    }
}
