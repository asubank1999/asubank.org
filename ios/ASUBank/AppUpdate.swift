import SwiftUI
import WebKit

/// Remote switches in https://asubank.org/assets/app/app-config.json, plus the App Store version check
@MainActor
final class AppConfig: ObservableObject {
    struct Remote: Decodable {
        struct IOS: Decodable { var latest: String?; var minimum: String?; var store: String?; var notes: String? }
        /// Every field is optional so a badly filled entry can't break the update check
        struct Sponsor: Decodable { var title: String?; var text: String?; var url: String?; var until: String? }
        var ios: IOS?
        var share: String?
        var donate: Bool?
        var sponsor: Sponsor?
    }
    /// A paid card on Home. No "sponsor" entry in app-config.json means no card.
    struct SponsorInfo: Equatable {
        let title: String
        let text: String?
        let url: URL
        /// Last day to show, as yyyy-MM-dd
        let until: String?

        var isLive: Bool {
            guard let until = until else { return true }
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy-MM-dd"
            return f.string(from: Date()) <= until
        }
    }
    struct Lookup: Decodable {
        struct R: Decodable { var version: String; var trackViewUrl: String?; var releaseNotes: String? }
        var results: [R]
    }

    @Published private(set) var donate = true
    @Published private(set) var sponsor: SponsorInfo?
    @Published private(set) var latest: String?
    @Published private(set) var notes: String?
    @Published private(set) var mustUpdate = false
    @Published var offerUpdate = false
    @Published private(set) var storeURL: URL?
    /// What "Share UniHub" sends. Set "share" in app-config.json when the store pages exist.
    @Published private(set) var shareURL = URL(string: "https://asubank.org/")!
    @Published private(set) var checking = false
    private var checkedAt: Date?

    /// App Store build (AB_STORE_BUILD=YES): no outside updates and no outside payments
    static let isStore = (Bundle.main.object(forInfoDictionaryKey: "ABStoreBuild") as? String) == "YES"
    static let current = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    static let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
    private static let bundleID = Bundle.main.bundleIdentifier ?? "org.unihub.app"
    private static let configURL = URL(string: "https://asubank.org/assets/app/app-config.json")!

    /// -1, 0, 1 for dotted versions like 1.10.2
    static func compare(_ a: String, _ b: String) -> Int {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }, y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let l = i < x.count ? x[i] : 0, r = i < y.count ? y[i] : 0
            if l != r { return l < r ? -1 : 1 }
        }
        return 0
    }

    /// On launch and when the app comes back; at most every 30 minutes unless `force`
    func check(force: Bool = false) async {
        if !force, let t = checkedAt, Date().timeIntervalSince(t) < 1800 { return }
        checkedAt = Date()
        checking = true
        defer { checking = false }

        var remote: Remote?
        var req = URLRequest(url: Self.configURL)
        req.cachePolicy = .reloadIgnoringLocalCacheData
        if let (data, _) = try? await URLSession.shared.data(for: req) { remote = try? JSONDecoder().decode(Remote.self, from: data) }
        if let s = remote?.share, let u = URL(string: s) { shareURL = u }
        donate = !Self.isStore && (remote?.donate ?? true)
        // Offline keeps the last sponsor; only a config that really has none removes it
        if let remote = remote { sponsor = Self.isStore ? nil : Self.sponsorInfo(remote.sponsor) }

        // The App Store is the truth for the newest version; app-config.json covers the time before review ends
        var store: Lookup.R?
        for q in ["&country=jo", ""] {
            guard let u = URL(string: "https://itunes.apple.com/lookup?bundleId=\(Self.bundleID)\(q)&t=\(Int(Date().timeIntervalSince1970))"),
                  let (data, _) = try? await URLSession.shared.data(from: u),
                  let r = try? JSONDecoder().decode(Lookup.self, from: data).results.first else { continue }
            store = r
            break
        }
        if let s = store?.trackViewUrl { storeURL = URL(string: s) }
        else if let s = remote?.ios?.store, !s.isEmpty { storeURL = URL(string: s) }

        let newest = [store?.version, remote?.ios?.latest].compactMap { $0 }.max { Self.compare($0, $1) < 0 }
        latest = newest
        // Sideloaded builds have no App Store page: send them to the GitHub release instead
        if !Self.isStore, storeURL == nil, let n = newest { storeURL = URL(string: "https://github.com/asubank1999/asubank.org/releases/tag/android-v\(n)") }
        notes = store?.releaseNotes ?? remote?.ios?.notes
        // Only nag when there is somewhere to update from
        guard storeURL != nil else { return }
        if let min = remote?.ios?.minimum, Self.compare(Self.current, min) < 0 { mustUpdate = true }
        if let n = newest, Self.compare(Self.current, n) < 0 {
            let key = "ab.update.offered.\(n)"
            if force || !UserDefaults.standard.bool(forKey: key) {
                UserDefaults.standard.set(true, forKey: key)
                offerUpdate = !mustUpdate
            }
        }
    }

    var updateAvailable: Bool { latest.map { Self.compare(Self.current, $0) < 0 } ?? false }

    /// Needs a title and an https link, or there's no card
    private static func sponsorInfo(_ s: Remote.Sponsor?) -> SponsorInfo? {
        guard let title = s?.title?.trimmingCharacters(in: .whitespacesAndNewlines), !title.isEmpty,
              let link = s?.url, let url = URL(string: link), url.scheme?.lowercased() == "https" else { return nil }
        return SponsorInfo(title: title, text: s?.text, url: url, until: s?.until)
    }
}

/// Shown over everything when this version is below app-config.json's "minimum"
struct UpdateRequiredView: View {
    @EnvironmentObject private var config: AppConfig
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "arrow.down.app.fill")
                .font(.system(size: 64)).foregroundStyle(Color.abAccent)
            Text("Update required").font(.largeTitle.weight(.bold))
            Text("This version of UniHub (\(AppConfig.current)) no longer works. Please update to \(config.latest ?? "the latest version") to keep using the app.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary)
            Text("يرجى تحديث التطبيق للاستمرار.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary)
                .environment(\.layoutDirection, .rightToLeft)
            Spacer()
            Button {
                if let u = config.storeURL { openURL(u) }
            } label: {
                Text("Update now").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent).tint(.abAccent)
        }
        .padding(28)
        .interactiveDismissDisabled()
    }
}

/// Terms everyone accepts once, after the app first loads
struct WelcomeTermsView: View {
    /// nil while it's the first-launch gate; set when re-opened from More
    var onClose: (() -> Void)? = nil
    @AppStorage(WelcomeTermsView.key) private var accepted = false
    @State private var agree = false
    @Environment(\.openURL) private var openURL
    static let key = "ab.terms.accepted.v1"

    var body: some View {
        ABStack {
            List {
                Section {
                    VStack(spacing: 10) {
                        Image(systemName: "books.vertical.fill")
                            .font(.system(size: 34)).foregroundStyle(.white)
                            .frame(width: 76, height: 76)
                            .background(Color.abAccent, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        Text("Welcome to UniHub").font(.title2.weight(.bold))
                        Text("Please read this before you start").font(.subheadline).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                    .listRowBackground(Color.clear)
                }
                Section("About this app") {
                    term("heart.fill", "A free, student-run project", "UniHub is free for students and run by a student. Ads on the website, sponsors and donations support it.")
                    term("building.columns", "Not from the university", "This app is not made by, owned by, or affiliated with Applied Science Private University (ASPU).")
                    term("person.fill", "Made by a fellow student", "It's built by a college friend who studies English Language and Literature and wants to help all ASPU students search and find what they need.")
                    term("checkmark.shield", "Use materials responsibly", "Files are shared by students. Check them against your official course resources.")
                }
                Section("بالعربية") {
                    Text("UniHub مشروع طلابي مجاني، وليس تابعاً لجامعة العلوم التطبيقية الخاصة ولا صادراً عنها. أنشأه زميل جامعي يدرس اللغة الإنجليزية وآدابها، بهدف مساعدة جميع طلاب الجامعة في البحث عن احتياجاتهم والوصول إليها بسهولة.")
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .environment(\.layoutDirection, .rightToLeft)
                        .padding(.vertical, 4)
                }
                Section {
                    Button { openURL(URL(string: "https://asubank.org/legal/uni_tos")!) } label: {
                        Label("Read the full Terms of Service", systemImage: "doc.text")
                    }
                    if onClose == nil {
                        Toggle("I understand and agree / أوافق", isOn: $agree.animation()).tint(.abAccent)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle(onClose == nil ? "" : "About UniHub")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // (an `if` around a ToolbarItem needs iOS 16)
                ToolbarItem(placement: .confirmationAction) {
                    if let onClose { Button("Done", action: onClose) }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if onClose == nil {
                    Button {
                        accepted = true
                        Haptics.success()
                    } label: {
                        Text("Accept and continue").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent).tint(.abAccent)
                    .disabled(!agree)
                    .padding(.horizontal, 20).padding(.vertical, 10)
                    .background(.bar)
                }
            }
        }
        .interactiveDismissDisabled(onClose == nil)
    }

    private func term(_ symbol: String, _ title: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol).foregroundStyle(Color.abAccent).frame(width: 26).padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.body.weight(.semibold))
                Text(text).font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 3)
    }
}

/// App size and the cache the More tab can clear (downloaded pages, materials index, web view and image caches).
/// Saved materials and settings live in UserDefaults and are never touched.
enum AppStorageInfo {
    private static let fm = FileManager.default
    private static var caches: URL { fm.urls(for: .cachesDirectory, in: .userDomainMask)[0] }
    private static var library: URL { fm.urls(for: .libraryDirectory, in: .userDomainMask)[0] }

    static func size(of dir: URL) -> Int64 {
        guard let e = fm.enumerator(at: dir, includingPropertiesForKeys: [.totalFileAllocatedSizeKey, .fileAllocatedSizeKey]) else { return 0 }
        var total: Int64 = 0
        for case let u as URL in e {
            let v = try? u.resourceValues(forKeys: [.totalFileAllocatedSizeKey, .fileAllocatedSizeKey])
            total += Int64(v?.totalFileAllocatedSize ?? v?.fileAllocatedSize ?? 0)
        }
        return total
    }

    /// (app, cache, everything else the app keeps)
    static func measure() async -> (app: Int64, cache: Int64, data: Int64) {
        await Task.detached(priority: .utility) {
            let app = size(of: Bundle.main.bundleURL)
            let cache = size(of: caches) + size(of: fm.temporaryDirectory)
            let all = size(of: library) + size(of: fm.urls(for: .documentDirectory, in: .userDomainMask)[0])
            return (app, cache, max(0, all - size(of: caches)))
        }.value
    }

    static func clear() async {
        URLCache.shared.removeAllCachedResponses()
        await WKWebsiteDataStore.default().removeData(
            ofTypes: [WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache, WKWebsiteDataTypeFetchCache, WKWebsiteDataTypeOfflineWebApplicationCache],
            modifiedSince: .distantPast)
        await Task.detached(priority: .utility) {
            for dir in [caches, fm.temporaryDirectory] {
                for u in (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? [] { try? fm.removeItem(at: u) }
            }
        }.value
    }
}
