import SwiftUI

/// One version's changes, from whats-new.json (built from RELEASE_NOTES.md by tools/gen_whats_new.py)
struct WhatsNewEntry: Decodable, Identifiable {
    let version: String
    let changes: [String]
    var id: String { version }
}

@MainActor
enum WhatsNew {
    private static let seenKey = "ab.whatsnew.seen"

    static let all: [WhatsNewEntry] = {
        guard let url = Bundle.main.url(forResource: "whats-new", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let list = try? JSONDecoder().decode([WhatsNewEntry].self, from: data) else { return [] }
        return list
    }()

    /// This version's notes, once, the first launch after an update. A new install has nothing to compare with.
    static func pending(accepted: Bool) -> WhatsNewEntry? {
        let d = UserDefaults.standard
        let current = AppConfig.current
        // CI screenshots: `-showWhatsNew YES` shows it; other launches with -startTab must not be covered by it
        if d.bool(forKey: "showWhatsNew") { return all.first { $0.version == current } }
        if d.string(forKey: "startTab") != nil { return nil }
        if d.string(forKey: seenKey) == current { return nil }
        d.set(current, forKey: seenKey)
        guard accepted else { return nil }
        return all.first { $0.version == current }
    }
}

private struct ChangeLine: View {
    let text: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Image(systemName: "checkmark.circle.fill").font(.footnote).fg14(Color.abAccent)
            Text(verbatim: L(text)).font(.subheadline)
        }
        .padding(.vertical, 2)
    }
}

/// Shown over the app the first time it opens after an update
struct WhatsNewSheet: View {
    let entry: WhatsNewEntry
    @Environment(\.presentationMode) private var presentationMode14
    private func dismiss() { presentationMode14.wrappedValue.dismiss() }
    var body: some View {
        ABStack {
            List {
                Section14 {
                    ForEach(entry.changes, id: \.self) { ChangeLine(text: $0) }
                } header: {
                    Text(verbatim: String(format: L("What's new in %@"), entry.version))
                }
                Section14 {
                    NavigationLink { WhatsNewScreen() } label: {
                        Label("See all versions", systemImage: "clock.arrow.circlepath")
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle("What's New")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset14(edge: .bottom) {
                Button { dismiss() } label: {
                    Text("Continue").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .prominentButton14().tint14(.abFill)
                .padding(.horizontal, 20).padding(.vertical, 10)
            }
        }
    }
}

/// More > What's New: every version, newest first
struct WhatsNewScreen: View {
    var body: some View {
        List {
            ForEach(WhatsNew.all) { entry in
                Section14 {
                    ForEach(entry.changes, id: \.self) { ChangeLine(text: $0) }
                } header: {
                    Text(verbatim: String(format: L("Version %@"), entry.version))
                }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("What's New")
        .navigationBarTitleDisplayMode(.inline)
    }
}
