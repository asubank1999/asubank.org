import SwiftUI

// MARK: - pages.json (made from asubank.org by tools/build_app_pages.py)

struct PageLink: Codable, Hashable {
    var t: String
    var u: String?
    var s: String?
    var b: String?
    var e: String?
}

struct PageFAQ: Codable, Hashable {
    var q: String
    var a: String
    var qa: String?
    var aa: String?
}

struct PageTable: Codable, Hashable {
    var h: [String]?
    var r: [[String]]
}

struct PageHead: Codable, Hashable {
    var eyebrow: String?
    var sub: String?
    var ar: String?
}

/// One block of a page; exactly one of the content fields is set
struct PageBlock: Codable, Hashable {
    var head: PageHead?
    var h: String?
    var ar: String?
    var e: String?
    var p: String?
    var links: [PageLink]?
    var list: [String]?
    var table: PageTable?
    var img: String?
    var alt: String?
    var faq: [PageFAQ]?
}

struct CalEvent: Codable, Hashable, Identifiable {
    var d: String
    var day: String
    var n: String
    var w: Int?
    var id: String { d + n }
}

struct SitePage: Codable {
    var t: String
    var b: [PageBlock]
    var cal: [CalEvent]?
    var web: Int?
}

/// All asubank.org pages as native content; cached on disk so they open offline
@MainActor
final class SitePages: ObservableObject {
    @Published private(set) var pages: [String: SitePage] = [:]
    @Published private(set) var loaded = false

    private let url = URL(string: "https://asubank.org/assets/app/pages.json")!
    private let cacheFile = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("pages.json")

    func page(_ path: String) -> SitePage? { pages[path] }

    func load() async {
        if pages.isEmpty, let data = try? Data(contentsOf: cacheFile) { apply(data) }
        var req = URLRequest(url: url)
        req.cachePolicy = .reloadIgnoringLocalCacheData
        if let (data, resp) = try? await URLSession.shared.data(for: req),
           (resp as? HTTPURLResponse)?.statusCode == 200, apply(data) {
            try? data.write(to: cacheFile, options: .atomic)
        }
        loaded = true
    }

    @discardableResult
    private func apply(_ data: Data) -> Bool {
        guard let p = try? JSONDecoder().decode([String: SitePage].self, from: data) else { return false }
        pages = p
        return true
    }
}

// MARK: - Routing every asubank.org link to a native screen

enum SiteTarget {
    case tab(AppTab)
    case push(Route)
    case external(URL)
}

enum SiteRouter {
    static let latestPaths: Set<String> = ["/guides/latest", "/guides/latest_v2", "/portal/confirm_device", "/portal/indexuserportal",
                                           "/portal/agreement-page", "/portal/agreement-page_mobile", "/portal/agreement-page_desktop", "/portal/agreement-page_android"]

    /// Where a link from a page or the home screen should go
    static func target(_ u: String, title: String) -> SiteTarget? {
        if let path = AB.sitePath(u) {
            switch path {
            case "/": return .tab(.home)
            case "/guides/search": return .tab(.search)
            case "/guides/saved": return .tab(.saved)
            case "/upload/uploadmethod", "/upload", "/upload/dupload", "/upload/gupload", "/upload/googledrive_fileupload", "/upload/discord_fileupload": return .tab(.upload)
            default: return .push(.page(path, title))
            }
        }
        guard let url = URL(string: u), let scheme = url.scheme?.lowercased() else { return nil }
        let host = url.host?.lowercased() ?? ""
        if scheme == "http" || scheme == "https" {
            // chats and payments open in their own apps; files open in the in-app viewer
            if host.contains("whatsapp.com") || host == "wa.me" || host.contains("paypal.") || host.contains("t.me") || host.contains("instagram.com") {
                return .external(url)
            }
            return .push(.material(Material(u: u, t: title, s: host.replacingOccurrences(of: "www.", with: ""))))
        }
        return .external(url) // mailto:, tel:, whatsapp:
    }
}

/// Shows any asubank.org path as the best native screen
struct SitePageView: View {
    let path: String
    let title: String
    @EnvironmentObject private var pages: SitePages
    @EnvironmentObject private var library: Library

    var body: some View {
        if path == "/banks/subjects" {
            SubjectsDirectory()
        } else if path.hasPrefix("/banks/"), let s = library.subject(forPath: path) {
            SubjectView(subject: s)
        } else if SiteRouter.latestPaths.contains(path) {
            LatestScreen()
        } else if path == "/guides/halls" || path == "/guides/colleges_hall" {
            HallsScreen()
        } else if path == "/requests/portal_to_rating" || path == "/requests/rating" {
            TeacherRatingsScreen()
        } else if path == "/requests/matcher" || path == "/requests/matcher_confirm" {
            CourseMatcherScreen()
        } else if ["/requests/student_request_portal", "/requests/request", "/requests/student_request_main"].contains(path) {
            RequestMaterialScreen()
        } else if path == "/guides/tutorial" {
            SearchGuideScreen()
        } else if path == "/guides/grading-scale-asu" {
            GradingScaleScreen()
        } else if path == "/requests/student_ads" {
            StudentAdsScreen()
        } else if path == "/guides/thankslist" {
            ThanksScreen(page: pages.page(path))
        } else if ["/upload/submit_file", "/upload/student_submissions_board", "/upload/submissions"].contains(path) {
            SubmissionsScreen()
        } else if path == "/apps/adminpanel_subs" {
            SubmissionsScreen(admin: AccountStore.shared.isAdmin)
        } else if ["/account", "/account/", "/account/index"].contains(path) {
            if AccountStore.shared.signedIn { AccountScreen() } else { SignInPrompt() }
        } else if path == "/apps/donate" {
            DonateScreen()
        } else if path == "/guides/importantlinks", let page = pages.page("/guides/links") {
            NativePageScreen(path: "/guides/links", page: page)
        } else if path == "/guides/groups", let page = pages.page(path) {
            GroupsScreen(page: page)
        } else if let page = pages.page(path) {
            if let cal = page.cal, !cal.isEmpty {
                CalendarScreen(title: page.t, path: path, events: cal)
            } else if page.web == 1 {
                WebScreen(title: title.isEmpty ? page.t : title, url: AB.absolute(path) ?? AB.site)
            } else {
                NativePageScreen(path: path, page: page)
            }
        } else if !pages.loaded {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.abBackground)
        } else {
            WebScreen(title: title, url: AB.absolute(path) ?? AB.site)
        }
    }
}

/// A row that follows an asubank.org (or other) link the native way
struct SiteLinkRowButton<Label: View>: View {
    let url: String
    let title: String
    @ViewBuilder let label: () -> Label
    @EnvironmentObject private var app: AppState
    @Environment(\.openURL) private var openURL

    var body: some View {
        switch SiteRouter.target(url, title: title) {
        case .push(let r):
            RouteLink(r) { label() }
        case .tab(let t):
            Button { app.tab = t; Haptics.tap() } label: { label() }.tint(.primary)
        case .external(let u):
            Button { openURL(u) } label: { label() }.tint(.primary)
        case nil:
            label()
        }
    }
}

// MARK: - Native page

private func isArabic(_ s: String) -> Bool {
    let letters = s.unicodeScalars.filter { CharacterSet.letters.contains($0) }
    guard !letters.isEmpty else { return false }
    let ar = letters.filter { (0x0600...0x06FF).contains($0.value) || (0x0750...0x077F).contains($0.value) }.count
    return Double(ar) / Double(letters.count) > 0.5
}

/// Text that lines up right-to-left when it is Arabic
struct BiText: View {
    let text: String
    var font: Font = .body
    var color: Color = .primary
    var selectable = false
    var body: some View {
        let ar = isArabic(text)
        let t = Text(text)
            .font(font)
            .foregroundStyle(color)
            .multilineTextAlignment(ar ? .trailing : .leading)
            .frame(maxWidth: .infinity, alignment: ar ? .trailing : .leading)
            .environment(\.layoutDirection, ar ? .rightToLeft : .leftToRight)
        if selectable { t.textSelection(.enabled) } else { t }
    }
}

private struct PageSection: Identifiable {
    let id: Int
    var title: String?
    var ar: String?
    var emoji: String?
    var blocks: [PageBlock]
}

struct NativePageScreen: View {
    let path: String
    let page: SitePage
    @EnvironmentObject private var pages: SitePages
    @Environment(\.openURL) private var openURL
    @State private var arabic = false

    private var sections: [PageSection] {
        var out: [PageSection] = [PageSection(id: 0, blocks: [])]
        for b in page.b {
            if let h = b.h {
                out.append(PageSection(id: out.count, title: h, ar: b.ar, emoji: b.e, blocks: []))
            } else {
                out[out.count - 1].blocks.append(b)
            }
        }
        return out.filter { !$0.blocks.isEmpty || $0.title != nil }
    }

    private var hasArabicFAQ: Bool { page.b.contains { $0.faq?.contains { $0.qa != nil } ?? false } }

    var body: some View {
        List {
            if hasArabicFAQ {
                Picker("Language", selection: $arabic) {
                    Text("English").tag(false)
                    Text("العربية").tag(true)
                }
                .pickerStyle(.segmented)
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            }
            ForEach(sections) { sec in
                Section {
                    ForEach(Array(sec.blocks.enumerated()), id: \.offset) { _, b in block(b) }
                } header: {
                    if let t = sec.title {
                        HStack {
                            Text([sec.emoji, t].compactMap { $0 }.joined(separator: " "))
                            Spacer()
                            if let ar = sec.ar { Text(ar).textCase(nil) }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(page.t)
        .navigationBarTitleDisplayMode(page.t.count > 24 ? .inline : .large)
        .refreshable { await pages.load() }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let url = AB.absolute(path) {
                    Menu {
                        ShareButton(item: url) { Label("Share", systemImage: "square.and.arrow.up") }
                        Button { openURL(url) } label: { Label("Open in Safari", systemImage: "safari") }
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
        }
    }

    @ViewBuilder private func block(_ b: PageBlock) -> some View {
        if let h = b.head {
            VStack(alignment: .leading, spacing: 6) {
                if let e = h.eyebrow { Text(e.uppercased()).font(.caption.weight(.bold)).foregroundStyle(Color.abAccent) }
                if let s = h.sub { BiText(text: s, font: .body, color: .secondary) }
                if let a = h.ar { BiText(text: a, font: .subheadline, color: .secondary) }
            }
            .padding(.vertical, 4)
        } else if let p = b.p {
            BiText(text: p, font: .body, selectable: true)
                .padding(.vertical, 2)
        } else if let links = b.links {
            ForEach(Array(links.enumerated()), id: \.offset) { _, l in linkRow(l) }
        } else if let list = b.list {
            ForEach(Array(list.enumerated()), id: \.offset) { _, item in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Circle().fill(Color.abAccent).frame(width: 6, height: 6).alignmentGuide(.firstTextBaseline) { $0[.bottom] - 2 }
                    BiText(text: item)
                }
            }
        } else if let t = b.table {
            ForEach(Array(t.r.enumerated()), id: \.offset) { _, row in tableRow(row, head: t.h) }
        } else if let img = b.img, let url = AB.absolute(img) {
            AsyncImage(url: url) { phase in
                if let i = phase.image {
                    i.resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                } else {
                    RoundedRectangle(cornerRadius: 12).fill(Color.secondary.opacity(0.1)).frame(height: 180)
                        .overlay(ProgressView())
                }
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
            .accessibilityLabel(b.alt ?? "")
        } else if let faq = b.faq {
            ForEach(Array(faq.enumerated()), id: \.offset) { _, f in
                let q = arabic ? (f.qa ?? f.q) : f.q
                let a = arabic ? (f.aa ?? f.a) : f.a
                DisclosureGroup {
                    BiText(text: a, font: .subheadline, color: .secondary).padding(.vertical, 4)
                } label: {
                    BiText(text: q, font: .body.weight(.semibold))
                }
                .tint(.abAccent)
            }
        }
    }

    private func linkRow(_ l: PageLink) -> some View {
        let content = HStack(spacing: 12) {
            if let e = l.e, !e.isEmpty {
                Text(e).font(.title3).frame(width: 30)
            } else if let u = l.u {
                KindTile(kind: AB.sitePath(u) != nil ? .page : AB.kind(u, l.t), size: 30)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    BiText(text: l.t)
                    if let b = l.b { Badge(text: b.uppercased(), color: b.uppercased() == "NEW" ? .green : .abAccent).fixedSize() }
                }
                if let s = l.s { BiText(text: s, font: .footnote, color: .secondary) }
            }
            if let u = l.u, case .external = SiteRouter.target(u, title: l.t) {
                Image(systemName: "arrow.up.right").font(.footnote).foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 2)
        return Group {
            if let u = l.u {
                SiteLinkRowButton(url: u, title: l.t) { content }
            } else {
                content
            }
        }
    }

    private func tableRow(_ row: [String], head: [String]?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if let head, head.count == row.count {
                ForEach(Array(row.enumerated()), id: \.offset) { i, cell in
                    if !cell.isEmpty {
                        HStack(alignment: .firstTextBaseline) {
                            Text(head[i]).font(.caption).foregroundStyle(.secondary)
                            Spacer(minLength: 12)
                            Text(cell).font(i == 0 ? .body.weight(.semibold) : .body).multilineTextAlignment(.trailing)
                        }
                    }
                }
            } else {
                BiText(text: row.filter { !$0.isEmpty }.joined(separator: " · "))
            }
        }
        .padding(.vertical, 2)
    }
}

/// /banks/subjects: every subject grouped by college
struct SubjectsDirectory: View {
    @EnvironmentObject private var library: Library
    var body: some View {
        List {
            ForEach(library.categories, id: \.name) { cat in
                Section(cat.name) {
                    ForEach(cat.subjects) { s in
                        RouteLink(Route.subject(s)) {
                            HStack(spacing: 12) {
                                Text(s.e ?? "📁").font(.title3).frame(width: 30)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(s.t)
                                    Text([s.ar, s.n.map { "\($0) items" }].compactMap { $0 }.joined(separator: " · "))
                                        .font(.footnote).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("All subjects")
    }
}
