import SwiftUI
import PDFKit
import QuickLook

// MARK: - Link facts checked on the server (tools/check_links.py)

/// Drive shortcuts resolved to their real file/folder, and links that no longer work.
/// Read from disk at launch so row icons are right straight away, then refreshed from asubank.org.
final class DriveLinks {
    static let shared = DriveLinks()
    private(set) var map: [String: [String]] = [:]     // shortcut id -> [target id, "folder"|"file"]
    private(set) var broken: [String: String] = [:]    // url -> reason
    private var deleted = Set<String>()                // Drive ids admins removed
    private static let deletedKey = "ab.deletedFiles.v1"
    private let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
    private let lock = NSLock()

    private init() {
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let d = try? Data(contentsOf: dir.appendingPathComponent("drive-map.json")) { map = (try? JSONDecoder().decode([String: [String]].self, from: d)) ?? [:] }
        if let d = try? Data(contentsOf: dir.appendingPathComponent("broken.json")) { broken = (try? JSONDecoder().decode([String: String].self, from: d)) ?? [:] }
        deleted = Set(UserDefaults.standard.stringArray(forKey: Self.deletedKey) ?? [])
    }

    /// Returns true when the list changed
    @discardableResult
    func setDeleted(_ ids: Set<String>) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard ids != deleted else { return false }
        deleted = ids
        UserDefaults.standard.set(Array(ids), forKey: Self.deletedKey)
        return true
    }

    var deletedIds: Set<String> {
        lock.lock(); defer { lock.unlock() }
        return deleted
    }

    /// A file an admin removed, by its own id or through the shortcut that points to it
    func isDeleted(_ u: String) -> Bool {
        lock.lock()
        let gone = deleted, links = map
        lock.unlock()
        guard !gone.isEmpty, let id = AB.driveId(u) else { return false }
        if gone.contains(id) { return true }
        if let t = links[id]?.first, gone.contains(t) { return true }
        return false
    }

    /// Returns true when something changed
    func refresh() async -> Bool {
        var changed = false
        for (name, isMap) in [("drive-map.json", true), ("broken.json", false)] {
            guard let url = URL(string: "https://asubank.org/assets/app/\(name)") else { continue }
            var req = URLRequest(url: url)
            req.cachePolicy = .reloadIgnoringLocalCacheData
            guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200 else { continue }
            if store(data, isMap: isMap) { changed = true }
            try? data.write(to: dir.appendingPathComponent(name), options: .atomic)
        }
        return changed
    }

    private func store(_ data: Data, isMap: Bool) -> Bool {
        lock.lock(); defer { lock.unlock() }
        if isMap, let m = try? JSONDecoder().decode([String: [String]].self, from: data), m != map { map = m; return true }
        if !isMap, let b = try? JSONDecoder().decode([String: String].self, from: data), b != broken { broken = b; return true }
        return false
    }

    func target(_ id: String) -> (id: String, folder: Bool)? {
        lock.lock(); defer { lock.unlock() }
        guard let t = map[id], let first = t.first else { return nil }
        return (first, t.count > 1 && t[1] == "folder")
    }

    func isBroken(_ u: String) -> String? {
        if isDeleted(u) { return "This file was removed from ASU Bank." }
        lock.lock(); defer { lock.unlock() }
        return broken[u]
    }
}

/// Tells every file row when an admin removed something
@MainActor
final class RemovedFiles: ObservableObject {
    static let shared = RemovedFiles()
    @Published private(set) var revision = 0

    /// Returns true when the list changed
    func refresh() async -> Bool {
        guard let r = try? await AccountsAPI.call("deletedFiles"), let ids = r["ids"] as? [String],
              DriveLinks.shared.setDeleted(Set(ids)) else { return false }
        revision += 1
        return true
    }

    func add(_ ids: [String]) {
        DriveLinks.shared.setDeleted(DriveLinks.shared.deletedIds.union(ids))
        revision += 1
    }
}

// MARK: - Material screen

/// Opens any material natively: Drive folders as lists, PDFs in PDFKit, Office files and images in Quick Look.
/// Only things iOS can't show itself (YouTube, other websites, Dropbox folders) use a web view.
struct MaterialScreen: View {
    @State private var reporting: ReportTarget?
    let material: Material
    @EnvironmentObject private var saved: SavedStore
    @Environment(\.openURL) private var openURL
    @StateObject private var loader = MaterialLoader()

    var body: some View {
        Group {
            switch loader.state {
            case .resolving:
                ProgressView("Opening…").frame(maxWidth: .infinity, maxHeight: .infinity)
            case .downloading(let p):
                VStack(spacing: 14) {
                    KindTile(kind: AB.kind(material.u, material.t)).scaleEffect(1.4).padding(.bottom, 6)
                    Text(material.t).font(.headline).multilineTextAlignment(.center).lineLimit(3)
                    if p > 0 { ProgressView(value: p).frame(width: 200) } else { ProgressView() }
                    Text(p > 0 ? "Downloading \(Int(p * 100))%" : "Downloading…").font(.footnote).foregroundStyle(.secondary)
                }
                .padding(30).frame(maxWidth: .infinity, maxHeight: .infinity)
            case .folder(let id):
                DriveFolderList(id: id, title: material.t)
            case .pdf(let file):
                PDFKitView(url: file).ignoresSafeArea(edges: .bottom)
            case .quickLook(let file):
                QuickLookView(url: file).ignoresSafeArea(edges: .bottom)
            case .web(let url):
                WebView(url: url, allowZoom: true).ignoresSafeArea(edges: .bottom)
            case .card(let file, let why):
                FileCard(material: material, file: file, why: why)
            case .broken(let why):
                BrokenCard(material: material, why: why)
            }
        }
        .navigationTitle(material.t)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $reporting) { ReportSheet(target: $0) }
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button { saved.toggle(material); Haptics.tap() } label: {
                    Image(systemName: saved.isSaved(material.u) ? "star.fill" : "star")
                }
                .accessibilityLabel(saved.isSaved(material.u) ? "Remove from Saved" : "Save")
                Menu {
                    if let f = loader.file {
                        ShareButton(item: f) { Label("Save to Files / share file", systemImage: "square.and.arrow.down") }
                    }
                    if let url = AB.absolute(material.u) {
                        ShareButton(item: url, subject: Text(material.t)) { Label("Share link", systemImage: "square.and.arrow.up") }
                        Button { openURL(url) } label: { Label("Open in Drive / Safari", systemImage: "safari") }
                        Button { UIPasteboard.general.url = url; Haptics.success() } label: { Label("Copy link", systemImage: "link") }
                    }
                    Button(role: .destructive) { reporting = ReportTarget(kind: "file", targetId: material.u, name: material.t) } label: { Label("Report this file", systemImage: "flag") }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .task { await loader.open(material) }
        .onAppear { saved.opened(material) }
    }
}

@MainActor
final class MaterialLoader: ObservableObject {
    enum State: Equatable {
        case resolving, downloading(Double), folder(String), pdf(URL), quickLook(URL), web(URL), card(URL?, String), broken(String)
    }
    @Published var state: State = .resolving
    @Published var file: URL?
    private var started = false
    private var progressObs: NSKeyValueObservation?

    static let cacheDir: URL = {
        let d = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("materials", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()

    func open(_ m: Material) async {
        guard !started else { return }
        started = true
        let u = m.u
        if let why = DriveLinks.shared.isBroken(u) { state = .broken(why); return }
        let kind = AB.kind(u, m.t)

        // Google Drive folders
        if kind == .folder, u.contains("google.com"), let id = AB.driveId(u) { state = .folder(id); return }

        // Google Docs / Slides / Sheets: export to PDF and show natively
        if let g = AB.group(#"docs\.google\.com/(document|presentation|spreadsheets)/d/"#, u), let id = AB.group(#"/d/([\w-]{10,})"#, u) {
            let export = g == "presentation" ? "https://docs.google.com/presentation/d/\(id)/export/pdf" : "https://docs.google.com/\(g)/d/\(id)/export?format=pdf"
            if await download(URL(string: export)!, key: id, name: m.t + ".pdf") { return }
            state = .web(URL(string: "https://docs.google.com/\(g)/d/\(id)/preview")!)
            return
        }

        // Google Drive files (including shortcuts to files or folders)
        if u.contains("drive.google.com"), var id = AB.driveId(u) {
            if let t = DriveLinks.shared.target(id) {
                if t.folder { state = .folder(t.id); return }
                id = t.id
            }
            if await download(Self.driveDownload(id), key: id, name: m.t) { return }
            // Not a downloadable file: maybe a shortcut the server hasn't seen yet
            if let t = await Self.resolveShortcut(id) {
                if t.folder { state = .folder(t.id); return }
                if t.id != id, await download(Self.driveDownload(t.id), key: t.id, name: m.t) { return }
                id = t.id
            }
            state = .web(URL(string: "https://drive.google.com/file/d/\(id)/preview")!)
            return
        }

        // Dropbox files: direct download
        if u.contains("dropbox.com/scl/fi") || u.contains("dropbox.com/s/") {
            var c = URLComponents(string: u)
            let items = (c?.queryItems ?? []).filter { $0.name != "dl" } + [URLQueryItem(name: "dl", value: "1")]
            c?.queryItems = items
            if let d = c?.url, await download(d, key: "dbx" + Self.stableHash(u), name: m.t) { return }
        }

        state = .web(AB.previewURL(u) ?? AB.site)
    }

    static func driveDownload(_ id: String) -> URL {
        URL(string: "https://drive.usercontent.google.com/download?id=\(id)&export=download&confirm=t")!
    }

    /// Opening a shortcut's /view page redirects to the real file
    static func resolveShortcut(_ id: String) async -> (id: String, folder: Bool)? {
        guard let (data, resp) = try? await URLSession.shared.data(from: URL(string: "https://drive.google.com/file/d/\(id)/view")!),
              let final = resp.url?.absoluteString, (resp as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        let body = String(decoding: data.prefix(600_000), as: UTF8.self)
        let target = AB.driveId(final) ?? id
        return (target, body.contains("application/vnd.google-apps.folder"))
    }

    /// Downloads into the cache (or reuses it) and picks the native viewer. False when the server sent a web page instead of a file.
    private func download(_ url: URL, key: String, name: String) async -> Bool {
        let safeKey = String(key.prefix(80)).replacingOccurrences(of: "/", with: "_")
        if let cached = try? FileManager.default.contentsOfDirectory(at: Self.cacheDir, includingPropertiesForKeys: nil)
            .first(where: { $0.deletingPathExtension().lastPathComponent == safeKey }) {
            show(cached)
            return true
        }
        state = .downloading(0)
        let result: (URL, URLResponse)? = await withCheckedContinuation { c in
            let task = URLSession.shared.downloadTask(with: url) { tmp, resp, _ in
                guard let tmp, let resp else { return c.resume(returning: nil) }
                // Move out of the temporary location before this callback returns
                let keep = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
                do { try FileManager.default.moveItem(at: tmp, to: keep); c.resume(returning: (keep, resp)) } catch { c.resume(returning: nil) }
            }
            progressObs = task.progress.observe(\.fractionCompleted) { [weak self] p, _ in
                let f = p.fractionCompleted
                Task { @MainActor in if case .downloading = self?.state { self?.state = .downloading(f) } }
            }
            task.resume()
        }
        progressObs = nil
        guard let (tmp, resp) = result else { return false }
        let http = resp as? HTTPURLResponse
        let mime = resp.mimeType ?? ""
        if http?.statusCode != 200 || mime.hasPrefix("text/html") {
            try? FileManager.default.removeItem(at: tmp)
            return false
        }
        var ext = (resp.suggestedFilename as NSString?)?.pathExtension.lowercased() ?? ""
        if ext.isEmpty || ext == "bin" {
            ext = mime.contains("pdf") ? "pdf" : (name as NSString).pathExtension.lowercased()
        }
        if Self.isPDF(tmp) { ext = "pdf" }
        let dest = Self.cacheDir.appendingPathComponent(safeKey).appendingPathExtension(ext.isEmpty ? "bin" : ext)
        try? FileManager.default.removeItem(at: dest)
        try? FileManager.default.moveItem(at: tmp, to: dest)
        show(dest)
        return true
    }

    private func show(_ f: URL) {
        file = f
        let ext = f.pathExtension.lowercased()
        if ext == "pdf" || Self.isPDF(f) { state = .pdf(f); return }
        if ["zip", "rar", "7z", "bin", "exe", "apk"].contains(ext) {
            state = .card(f, ext == "bin" ? "iOS can't preview this file type." : "This is a compressed \(ext.uppercased()) file. Save it to Files to open it.")
            return
        }
        if QLPreviewController.canPreview(f as NSURL) { state = .quickLook(f) } else { state = .card(f, "iOS can't preview this file type.") }
    }

    /// Same value on every launch (String.hashValue isn't)
    static func stableHash(_ s: String) -> String {
        var h: UInt64 = 5381
        for b in s.utf8 { h = (h &* 33) &+ UInt64(b) }
        return String(h, radix: 36)
    }

    static func isPDF(_ f: URL) -> Bool {
        guard let h = try? FileHandle(forReadingFrom: f) else { return false }
        defer { try? h.close() }
        return (try? h.read(upToCount: 5)) == Data("%PDF-".utf8)
    }
}

// MARK: - Viewers

struct PDFKitView: UIViewRepresentable {
    let url: URL
    func makeUIView(context: Context) -> PDFView {
        let v = PDFView()
        v.autoScales = true
        v.displayMode = .singlePageContinuous
        v.displayDirection = .vertical
        v.pageShadowsEnabled = true
        v.backgroundColor = .secondarySystemBackground
        v.document = PDFDocument(url: url)
        return v
    }
    func updateUIView(_ v: PDFView, context: Context) {
        if v.document?.documentURL != url { v.document = PDFDocument(url: url) }
    }
}

struct QuickLookView: UIViewControllerRepresentable {
    let url: URL
    func makeCoordinator() -> Coordinator { Coordinator(url: url) }
    func makeUIViewController(context: Context) -> QLPreviewController {
        let c = QLPreviewController()
        c.dataSource = context.coordinator
        return c
    }
    func updateUIViewController(_ c: QLPreviewController, context: Context) {}
    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(url: URL) { self.url = url }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem { url as NSURL }
    }
}

private struct FileCard: View {
    let material: Material
    let file: URL?
    let why: String
    @Environment(\.openURL) private var openURL
    var body: some View {
        VStack(spacing: 14) {
            KindTile(kind: AB.kind(material.u, material.t)).scaleEffect(1.6).padding(.bottom, 10)
            Text(material.t).font(.title3.weight(.semibold)).multilineTextAlignment(.center)
            if let file, let size = try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize {
                Text(ByteCountFormatter.string(fromByteCount: Int64(size), countStyle: .file)).foregroundStyle(.secondary)
            }
            Text(why).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
            if let file {
                ShareButton(item: file) {
                    Label("Save to Files", systemImage: "square.and.arrow.down").frame(maxWidth: 260).padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent).tint(.abAccent)
            }
            if let u = AB.absolute(material.u) {
                Button { openURL(u) } label: { Label("Open in Drive", systemImage: "arrow.up.right.square").frame(maxWidth: 260).padding(.vertical, 6) }
                    .buttonStyle(.bordered)
            }
        }
        .padding(30).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct BrokenCard: View {
    let material: Material
    let why: String
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 48)).foregroundStyle(.orange)
            Text("This material isn't available").font(.title3.weight(.semibold))
            Text("The owner removed it or made it private (\(why)). Ask for it and we'll upload it again.")
                .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
            RouteLink(Route.page("/requests/student_request_portal", "Request a material")) {
                Label("Request this material", systemImage: "tray.and.arrow.down").frame(maxWidth: 260).padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent).tint(.abAccent)
        }
        .padding(30).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Native Google Drive folder

struct DriveFolderList: View {
    let id: String
    let title: String
    @State private var entries: [Entry] = []
    @State private var state = 0 // 0 loading, 1 done, 2 failed
    @State private var query = ""

    struct Entry: Identifiable {
        let id: String
        let url: String
        let name: String
        let type: String
        let modified: String
        var isFolder: Bool { type.lowercased() == "folder" || url.contains("/folders/") }
        var kind: Kind {
            if isFolder { return .folder }
            if let t = DriveLinks.shared.target(id), t.folder { return .folder }
            let k = AB.kind(url, name)
            if k != .file { return k }
            switch type.lowercased() {
            case let t where t.contains("pdf"): return .pdf
            case let t where t.contains("powerpoint") || t.contains("presentation") || t.contains("slides"): return .ppt
            case let t where t.contains("word") || t.contains("wordprocessing") || t.contains("docs"): return .doc
            case let t where t.contains("excel") || t.contains("spreadsheet") || t.contains("sheets"): return .sheet
            case let t where t.contains("image"): return .img
            case let t where t.contains("video"): return .video
            case let t where t.contains("zip") || t.contains("archive") || t.contains("compressed"): return .zip
            default: return .file
            }
        }
    }

    private var shown: [Entry] {
        let q = AB.norm(query)
        return entries.filter { q.isEmpty || AB.norm($0.name).contains(q) }
    }

    var body: some View {
        List {
            switch state {
            case 0:
                HStack { Spacer(); ProgressView("Loading folder…"); Spacer() }.padding(30).listRowBackground(Color.clear)
            case 2:
                VStack(spacing: 10) {
                    Image(systemName: "folder.badge.questionmark").font(.system(size: 40)).foregroundStyle(.secondary)
                    Text("Couldn't open this folder").font(.headline)
                    Text("Check your connection, or the folder may be private.").font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    Button("Try again") { Task { await load() } }.buttonStyle(.borderedProminent).tint(.abAccent)
                }
                .frame(maxWidth: .infinity).padding(20).listRowBackground(Color.clear)
            default:
                if entries.isEmpty {
                    EmptyState(symbol: "folder", title: "Empty folder", text: "Nothing has been uploaded here yet.").listRowBackground(Color.clear)
                } else {
                    Section("\(entries.count) item\(entries.count == 1 ? "" : "s")") {
                        ForEach(shown) { e in
                            MaterialRow(m: Material(u: e.url, t: e.name, s: title, d: nil), kind: e.kind, meta: e.modified.isEmpty ? nil : e.modified, showSource: false)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $query, prompt: "Search in this folder")
        .refreshable { await load() }
        .task { if entries.isEmpty { await load() } }
    }

    private func load() async {
        if entries.isEmpty { state = 0 }
        guard let url = URL(string: "https://drive.google.com/embeddedfolderview?id=\(id)"),
              let (data, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200, resp.url?.host?.contains("accounts") != true else { state = 2; return }
        let html = String(decoding: data, as: UTF8.self)
        entries = Self.parse(html).sorted { a, b in
            a.isFolder != b.isFolder ? a.isFolder : a.name.localizedStandardCompare(b.name) == .orderedAscending
        }
        state = 1
    }

    /// Reads each entry on its own. Files with a thumbnail have no aria-label (only folders and
    /// icon-only files do), so the type comes from the label, the file-type icon URL or the image alt.
    static func parse(_ html: String) -> [Entry] {
        html.components(separatedBy: #"class="flip-entry" id="entry-"#).dropFirst().compactMap { chunk in
            func first(_ pattern: String) -> String? {
                guard let re = try? NSRegularExpression(pattern: pattern),
                      let m = re.firstMatch(in: chunk, range: NSRange(chunk.startIndex..., in: chunk)),
                      let r = Range(m.range(at: 1), in: chunk) else { return nil }
                return decode(String(chunk[r]))
            }
            guard let name = first(#"flip-entry-title">([\s\S]*?)</div>"#) else { return nil }
            let id = String(chunk.prefix { $0 != "\"" })
            let type = first(#"aria-label="([^"]*)""#) ?? first(#"/type/([^"]+)""#) ?? first(#"alt="([^"]+)""#) ?? ""
            return Entry(id: id, url: first(#"href="([^"]+)""#) ?? "https://drive.google.com/file/d/\(id)/view",
                         name: name.trimmingCharacters(in: .whitespacesAndNewlines), type: type,
                         modified: first(#"flip-entry-last-modified"><div>([^<]*)"#) ?? "")
        }
    }

    static func decode(_ s: String) -> String {
        var r = s
        for (a, b) in [("&amp;", "&"), ("&quot;", "\""), ("&#39;", "'"), ("&lt;", "<"), ("&gt;", ">"), ("&nbsp;", " ")] { r = r.replacingOccurrences(of: a, with: b) }
        if let re = try? NSRegularExpression(pattern: "&#(\\d+);") {
            let ns = r as NSString
            for m in re.matches(in: r, range: NSRange(location: 0, length: ns.length)).reversed() {
                if let code = Int(ns.substring(with: m.range(at: 1))), let sc = UnicodeScalar(code) {
                    r = (r as NSString).replacingCharacters(in: m.range, with: String(Character(sc)))
                }
            }
        }
        return r
    }
}
