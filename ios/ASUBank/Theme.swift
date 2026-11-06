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
    /// For filled buttons and bubbles with white text. The dark themes' accents are pale, and white on those can't be read.
    static var abFill: Color { Color(UIColor(hex: AppTheme.accent.light)) }
    static let abGold = Color(UIColor(hex: 0xFFC627))
    static var abBackground: Color { AppTheme.tint.map { Color($0) } ?? Color(UIColor.systemGroupedBackground) }
    static var abSurface: Color {
        switch AppTheme.appearance {
        case 4: return Color(UIColor(hex: 0x16213A))
        case 5: return Color(UIColor(hex: 0x16291E))
        case 6: return Color(UIColor(hex: 0x281A2F))
        case 7: return Color(UIColor(hex: 0xFFFBF2))
        case 8: return Color(UIColor(hex: 0xF6FAFF))
        case 9: return Color(UIColor(hex: 0xFFF8FA))
        default: return Color(UIColor.secondarySystemGroupedBackground)
        }
    }
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
                .background(on ? AnyShapeStyle(Color.abFill) : AnyShapeStyle(Color.abSurface), in: Capsule())
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
    static func tap() { if HomePrefs.haptics { UIImpactFeedbackGenerator(style: .light).impactOccurred() } }
    static func success() { if HomePrefs.haptics { UINotificationFeedbackGenerator().notificationOccurred(.success) } }
    static func error() { if HomePrefs.haptics { UINotificationFeedbackGenerator().notificationOccurred(.error) } }
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
        .init(id: 7, name: "Rose", light: 0xc2185b, dark: 0xff7eb0),
        .init(id: 8, name: "Royal blue", light: 0x2f3fc4, dark: 0x8c9bff),
        .init(id: 9, name: "Mint", light: 0x0f8a6d, dark: 0x5be3bf),
        .init(id: 10, name: "Gold", light: 0xa8780a, dark: 0xffc627),
        .init(id: 11, name: "Crimson", light: 0xb3132b, dark: 0xff6b7d),
        .init(id: 12, name: "Lavender", light: 0x7a5aa6, dark: 0xc9aefc),
        .init(id: 13, name: "Coffee", light: 0x6f4e37, dark: 0xd2a77f),
        .init(id: 14, name: "Emerald", light: 0x047857, dark: 0x34d399),
        .init(id: 15, name: "Indigo", light: 0x4338ca, dark: 0xa5b4fc),
        .init(id: 16, name: "Sky", light: 0x0369a1, dark: 0x7dd3fc),
        .init(id: 17, name: "Berry", light: 0x9d174d, dark: 0xf9a8d4),
        .init(id: 18, name: "Olive", light: 0x5f6b1a, dark: 0xc5d36a),
        .init(id: 19, name: "Copper", light: 0x9a4a1f, dark: 0xe8a67c),
    ]

    /// 0 system, 1 light, 2 dark, 3 pitch black (OLED), 4 midnight, 5 forest night, 6 plum, 7 sand, 8 sky, 9 blush
    static var appearance: Int { UserDefaults.standard.integer(forKey: "ab.appearance") }
    static var accent: Accent { accents.first { $0.id == UserDefaults.standard.integer(forKey: "ab.accent") } ?? accents[0] }

    static var scheme: ColorScheme? {
        switch appearance {
        case 1, 7, 8, 9: return .light
        case 2...6: return .dark
        default: return nil
        }
    }

    /// Windows follow the theme too, so sheets, alerts, menus and the keyboard match it from the first frame
    static func applyWindow() {
        let style: UIUserInterfaceStyle = scheme == .light ? .light : scheme == .dark ? .dark : .unspecified
        for scene in UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }) {
            for w in scene.windows { w.overrideUserInterfaceStyle = style }
        }
    }

    /// The background of the themes that have their own color
    static var tint: UIColor? {
        switch appearance {
        case 3: return .black
        case 4: return UIColor(hex: 0x0B1426)
        case 5: return UIColor(hex: 0x0C1A12)
        case 6: return UIColor(hex: 0x1A0F1F)
        case 7: return UIColor(hex: 0xF4ECDD)
        case 8: return UIColor(hex: 0xE3EEF9)
        case 9: return UIColor(hex: 0xFBEAF0)
        default: return nil
        }
    }

    /// The Home banner follows the accent color
    static var heroColors: [Color] {
        let h = accent.light
        let r: UInt32 = ((h >> 16) & 0xff) * 62 / 100
        let g: UInt32 = ((h >> 8) & 0xff) * 62 / 100
        let b: UInt32 = (h & 0xff) * 62 / 100
        let dark: UInt32 = (r << 16) | (g << 8) | b
        return [Color(UIColor(hex: h)), Color(UIColor(hex: dark))]
    }

    /// Bar colors (pitch black makes the tab and navigation bars pure black too)
    static func applyBars() {
        let nav = UINavigationBarAppearance()
        let tab = UITabBarAppearance()
        if let tint {
            nav.configureWithOpaqueBackground(); nav.backgroundColor = tint; nav.shadowColor = .clear
            tab.configureWithOpaqueBackground(); tab.backgroundColor = tint
            UITableView.appearance().backgroundColor = tint
            UICollectionView.appearance().backgroundColor = tint
        } else {
            nav.configureWithDefaultBackground()
            tab.configureWithDefaultBackground()
            UITableView.appearance().backgroundColor = nil
            UICollectionView.appearance().backgroundColor = nil
        }
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav
        if tint != nil { UINavigationBar.appearance().scrollEdgeAppearance = nav } else { UINavigationBar.appearance().scrollEdgeAppearance = nil }
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tint != nil ? tab : nil
    }
}
