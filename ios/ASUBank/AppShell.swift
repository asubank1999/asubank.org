import SwiftUI

/// The logo for a moment while the app starts
struct SplashView: View {
    @State private var shown = false
    var body: some View {
        ZStack {
            (AppTheme.tint.map { Color($0) } ?? Color(UIColor.systemBackground)).ignoresSafeArea()
            VStack(spacing: 16) {
                Image("Logo").resizable().scaledToFit().frame(width: 120, height: 120)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                    .shadow(color: Color.abAccent.opacity(0.35), radius: 18, y: 8)
                    .scaleEffect(shown ? 1 : 0.82)
                Text("UniHub").font(.title.weight(.bold))
                Text("Study materials for ASU students").font(.subheadline).foregroundStyle(.secondary)
            }
            .opacity(shown ? 1 : 0)
        }
        .onAppear { withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) { shown = true } }
    }
}

/// More > Theme
struct ThemeScreen: View {
    @AppStorage("ab.appearance") private var appearance = 0
    @AppStorage("ab.accent") private var accent = 0
    @AppStorage("ab.theme.rev") private var rev = 0
    @State private var pendingMode: Int?
    @State private var pendingAccent: Int?

    private let modes: [(Int, String, String, String)] = [
        (0, "Match my phone", "Follows your phone's light or dark mode", "iphone"),
        (1, "Light", "Always light", "sun.max.fill"),
        (2, "Dark", "Always dark", "moon.fill"),
        (3, "Pitch black (OLED)", "True black everywhere; saves battery on OLED screens", "moon.stars.fill"),
        (4, "Midnight", "Dark navy background", "moon.zzz.fill"),
        (5, "Forest night", "Dark green background", "leaf.fill"),
        (6, "Plum", "Dark purple background", "sparkles"),
        (7, "Sand", "Warm light background", "sun.haze.fill"),
        (8, "Sky", "Soft blue background", "cloud.sun.fill"),
        (9, "Blush", "Soft pink background", "heart.fill"),
    ]

    var body: some View {
        List {
            Section {
                ForEach(modes, id: \.0) { m in
                    Button {
                        if appearance != m.0 { pendingMode = m.0 }
                        Haptics.tap()
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: m.3).foregroundStyle(.white).frame(width: 30, height: 30)
                                .background(m.0 == 3 ? Color.black : Color.abAccent, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 7).stroke(Color.secondary.opacity(m.0 == 3 ? 0.4 : 0), lineWidth: 1))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(m.1).foregroundStyle(.primary)
                                Text(m.2).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if appearance == m.0 { Image(systemName: "checkmark").font(.body.weight(.semibold)).foregroundStyle(Color.abAccent) }
                        }
                    }
                }
            } header: { Text("Appearance") }

            Section {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 84), spacing: 12)], spacing: 14) {
                    ForEach(AppTheme.accents) { a in
                        Button {
                            if accent != a.id { pendingAccent = a.id }
                            Haptics.tap()
                        } label: {
                            VStack(spacing: 6) {
                                Circle().fill(a.color.abGradient).frame(width: 44, height: 44)
                                    .overlay(Circle().stroke(Color.primary, lineWidth: accent == a.id ? 3 : 0).padding(-4))
                                    .overlay { if accent == a.id { Image(systemName: "checkmark").font(.headline).foregroundStyle(.white) } }
                                Text(a.name).font(.caption).foregroundStyle(.primary).lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 8)
            } header: { Text("Accent color") } footer: { Text("Used for buttons, links and highlights across the app.") }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Theme")
        .alert("Restart UniHub to change the theme", isPresented: Binding(get: { pendingMode != nil || pendingAccent != nil }, set: { if !$0 { pendingMode = nil; pendingAccent = nil } })) {
            Button("Restart now") {
                if let m = pendingMode { appearance = m }
                if let a = pendingAccent { accent = a }
                rev += 1
                if AppConfig.isStore { AppTheme.applyBars() } else { AppRestart.now() }
            }
            Button("Cancel", role: .cancel) {}
        } message: { Text("The app closes so the new colors load cleanly. Tap its icon to open it again.") }
    }
}
