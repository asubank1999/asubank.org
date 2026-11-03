import SwiftUI
import UIKit

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xff) / 255, green: CGFloat((hex >> 8) & 0xff) / 255, blue: CGFloat(hex & 0xff) / 255, alpha: alpha)
    }
}

/// Colors from the website's design system (assets/app/app.css)
extension Color {
    static let abAccent = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0xff6f9a) : UIColor(hex: 0x8C1D40) })
    static let abAccentSoft = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0xff6f9a, alpha: 0.16) : UIColor(hex: 0x8C1D40, alpha: 0.1) })
    static let abGold = Color(UIColor(hex: 0xFFC627))
    static let abBackground = Color(UIColor.systemGroupedBackground)
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
