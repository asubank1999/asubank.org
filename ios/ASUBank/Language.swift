import SwiftUI

/// App language: System, English, Arabic or Spanish (More > Language).
/// The choice goes into AppleLanguages so iOS loads that .lproj on the next launch; the root view also gets the
/// matching locale and layout direction right away. Translations live in i18n/strings.json (tools/gen_i18n.py
/// writes en/ar/es.lproj/Localizable.strings from it).
enum AppLanguage {
    static let key = "ab.language"
    static let choices: [(code: String, name: String, native: String)] = [
        ("system", "System language", ""), ("en", "English", "English"), ("ar", "Arabic", "العربية"), ("es", "Spanish", "Español"),
    ]

    static var choice: String { UserDefaults.standard.string(forKey: key) ?? "system" }

    /// The language actually in use: the choice, or the phone's first supported language
    static var code: String {
        if choice != "system" { return choice }
        let pref = Locale.preferredLanguages.first.map { String($0.prefix(2)) } ?? "en"
        return ["ar", "es"].contains(pref) ? pref : "en"
    }

    static var isRTL: Bool { code == "ar" }
    static var locale: Locale { Locale(identifier: code) }

    static func set(_ c: String) {
        UserDefaults.standard.set(c, forKey: key)
        if c == "system" { UserDefaults.standard.removeObject(forKey: "AppleLanguages") }
        else { UserDefaults.standard.set([c], forKey: "AppleLanguages") }
        bundleCache = nil
    }

    private static var bundleCache: Bundle?
    static var bundle: Bundle {
        if let b = bundleCache { return b }
        let b = Bundle.main.path(forResource: code, ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
        bundleCache = b
        return b
    }
}

/// A translated string, for text that is built in code (SwiftUI Text("literal") is translated by itself)
func L(_ s: String) -> String { AppLanguage.bundle.localizedString(forKey: s, value: s, table: nil) }

// SwiftUI only translates string literals. These overloads win over its generic String versions, so text that comes
// from a variable (page content, link titles, subject names) is looked up too; text without a translation stays as it is.
extension Text {
    @_disfavoredOverload init(_ s: String) { self.init(verbatim: L(s)) }
}

extension Label where Title == Text, Icon == Image {
    @_disfavoredOverload init(_ s: String, systemImage: String) {
        self.init { Text(verbatim: L(s)) } icon: { Image(systemName: systemImage) }
    }
}

extension Section where Parent == Text, Content: View, Footer == EmptyView {
    @_disfavoredOverload init(_ s: String, @ViewBuilder content: () -> Content) {
        self.init(content: content) { Text(verbatim: L(s)) }
    }
}

extension View {
    @_disfavoredOverload func navigationTitle(_ s: String) -> some View { navigationTitle(Text(verbatim: L(s))) }
}

struct LanguageScreen: View {
    @AppStorage(AppLanguage.key) private var choice = "system"
    @AppStorage("ab.theme.rev") private var themeRev = 0
    @State private var changed = false

    var body: some View {
        List {
            Section {
                ForEach(AppLanguage.choices, id: \.code) { c in
                    Button {
                        guard c.code != choice else { return }
                        AppLanguage.set(c.code)
                        choice = c.code
                        changed = true
                        Haptics.tap()
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(LocalizedStringKey(c.name)).foregroundStyle(.primary)
                                if !c.native.isEmpty { Text(c.native).font(.caption).foregroundStyle(.secondary) }
                            }
                            Spacer()
                            if choice == c.code { Image(systemName: "checkmark").foregroundStyle(Color.abAccent).font(.body.weight(.semibold)) }
                        }
                    }
                }
            } footer: { Text("System language follows your iPhone's settings. Material names and files stay as they were uploaded.") }
            if changed {
                Section {
                    Label("Close and reopen ASU Bank to finish switching the language.", systemImage: "arrow.clockwise")
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Language")
        .onDisappear { if changed { themeRev += 1 } }
    }
}
