import SwiftUI

/// More > App icon. The alternates are app icon sets in Assets.xcassets, listed in project.yml.
struct AppIconScreen: View {
    private struct Choice: Identifiable {
        let icon: String?
        let name: String
        let preview: String
        var id: String { preview }
    }

    private let choices = [
        Choice(icon: nil, name: "Classic", preview: "IconPreviewDefault"),
        Choice(icon: "IconNavy", name: "Midnight book", preview: "IconPreviewNavy"),
        Choice(icon: "IconCap", name: "Graduation cap", preview: "IconPreviewCap"),
        Choice(icon: "IconCards", name: "Study cards", preview: "IconPreviewCards"),
        Choice(icon: "IconBook", name: "Book and cap", preview: "IconPreviewBook"),
    ]

    @State private var current = UIApplication.shared.alternateIconName
    @State private var failed = false

    var body: some View {
        List {
            Section {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 14)], spacing: 18) {
                    ForEach(choices) { c in
                        Button { pick(c.icon) } label: {
                            VStack(spacing: 8) {
                                Image(c.preview)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 76, height: 76)
                                    .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
                                    .overlay(RoundedRectangle(cornerRadius: 17, style: .continuous).stroke(Color.secondary.opacity(0.25), lineWidth: 0.5))
                                    .overlay(RoundedRectangle(cornerRadius: 21, style: .continuous)
                                        .stroke(Color.abAccent, lineWidth: current == c.icon ? 3 : 0).padding(-4))
                                Text(LocalizedStringKey(c.name)).font(.caption).foregroundStyle(.primary).lineLimit(2).multilineTextAlignment(.center)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 8)
            } footer: { Text("Pick the icon you want on your Home Screen. Your iPhone shows a short message when it changes.") }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("App icon")
        .alert("Couldn't change the icon", isPresented: $failed) { Button("OK", role: .cancel) {} }
    }

    private func pick(_ icon: String?) {
        guard icon != current else { return }
        UIApplication.shared.setAlternateIconName(icon) { error in
            DispatchQueue.main.async {
                if error == nil { current = icon; Haptics.success() } else { failed = true }
            }
        }
    }
}
