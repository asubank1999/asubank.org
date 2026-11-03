import Foundation
import SwiftUI

enum AppTab: Hashable { case home, search, upload, saved, more }

/// Shared app state: selected tab and a query handed from Home to Search
@MainActor
final class AppState: ObservableObject {
    @Published var tab: AppTab = .home
    @Published var pendingQuery: String?

    init() {
        // Launch arguments used by the CI screenshots, e.g. `-startTab search -startQuery calculus`
        let d = UserDefaults.standard
        let tabs: [String: AppTab] = ["home": .home, "search": .search, "upload": .upload, "saved": .saved, "more": .more]
        if let t = d.string(forKey: "startTab"), let tab = tabs[t] { self.tab = tab }
        pendingQuery = d.string(forKey: "startQuery")
    }

    func search(_ q: String) {
        pendingQuery = q
        tab = .search
    }
}

/// The material catalog (materials.json). Cached on disk so the app works offline.
@MainActor
final class Library: ObservableObject {
    @Published private(set) var subjects: [Subject] = []
    @Published private(set) var items: [IndexedMaterial] = []
    @Published private(set) var loading = false
    @Published private(set) var failed = false

    private let cacheFile: URL = {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("materials.json")
    }()

    var categories: [(name: String, subjects: [Subject])] {
        var order: [String] = []
        var groups: [String: [Subject]] = [:]
        for s in subjects where s.c != "Archive" {
            if groups[s.c] == nil { order.append(s.c) }
            groups[s.c, default: []].append(s)
        }
        return order.map { ($0, groups[$0] ?? []) }
    }

    var years: [String] {
        Array(Set(items.map(\.year).filter { !$0.isEmpty })).sorted(by: >)
    }

    func items(in subject: Subject) -> [IndexedMaterial] {
        items.filter { $0.m.p == subject.p }
    }

    func subject(forPath p: String) -> Subject? {
        subjects.first { $0.p == p }
    }

    /// Re-applies the catalog after shortcut / broken-link data changed
    func reindex() {
        if let data = try? Data(contentsOf: cacheFile) { apply(data) }
    }

    func load() async {
        if items.isEmpty, let data = try? Data(contentsOf: cacheFile) { apply(data) }
        loading = true
        defer { loading = false }
        do {
            var req = URLRequest(url: AB.catalogURL)
            req.cachePolicy = .reloadIgnoringLocalCacheData
            let (data, resp) = try await URLSession.shared.data(for: req)
            guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            if apply(data) { try? data.write(to: cacheFile, options: .atomic) }
            failed = false
        } catch {
            failed = items.isEmpty
        }
    }

    @discardableResult
    private func apply(_ data: Data) -> Bool {
        guard let c = try? JSONDecoder().decode(Catalog.self, from: data) else { return false }
        subjects = c.subjects
        // Links the server found dead are hidden (tools/check_links.py -> broken.json)
        items = c.items.filter { DriveLinks.shared.isBroken($0.u) == nil }.map(AB.index)
        return true
    }
}

/// Saved files, recently opened files and recent searches (kept on this device)
@MainActor
final class SavedStore: ObservableObject {
    @Published private(set) var saved: [SavedItem] = []
    @Published private(set) var recent: [SavedItem] = []
    @Published private(set) var searches: [String] = []

    private let d = UserDefaults.standard

    init() {
        saved = read("ab.saved")
        recent = read("ab.recent")
        searches = d.stringArray(forKey: "ab.searches") ?? []
    }

    func isSaved(_ u: String) -> Bool { saved.contains { $0.u == u } }

    func toggle(_ m: Material) {
        if isSaved(m.u) { saved.removeAll { $0.u == m.u } }
        else { saved.insert(SavedItem(u: m.u, t: m.t, s: m.s ?? "", date: Date()), at: 0) }
        write(saved, "ab.saved")
    }

    func removeSaved(at offsets: IndexSet) { saved.remove(atOffsets: offsets); write(saved, "ab.saved") }

    func opened(_ m: Material) {
        recent.removeAll { $0.u == m.u }
        recent.insert(SavedItem(u: m.u, t: m.t, s: m.s ?? "", date: Date()), at: 0)
        recent = Array(recent.prefix(40))
        write(recent, "ab.recent")
    }

    func removeRecent(at offsets: IndexSet) { recent.remove(atOffsets: offsets); write(recent, "ab.recent") }
    func clearRecent() { recent = []; write(recent, "ab.recent") }

    func searched(_ q: String) {
        let q = q.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { return }
        searches.removeAll { $0 == q }
        searches.insert(q, at: 0)
        searches = Array(searches.prefix(12))
        d.set(searches, forKey: "ab.searches")
    }

    func clearSearches() { searches = []; d.set(searches, forKey: "ab.searches") }

    private func read(_ key: String) -> [SavedItem] {
        guard let data = d.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([SavedItem].self, from: data)) ?? []
    }

    private func write(_ v: [SavedItem], _ key: String) {
        d.set(try? JSONEncoder().encode(v), forKey: key)
    }
}

/// Google Drive search (same Apps Script as asubank.org/guides/search)
enum DriveSearch {
    struct Reply: Decodable {
        var success: Bool?
        var files: [File]?
    }
    struct File: Decodable {
        var name: String
        var url: String
        var mimeType: String?
        var dateUploaded: String?
        var size: Double?

        enum CodingKeys: String, CodingKey { case name, url, mimeType, dateUploaded, size }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            name = try c.decode(String.self, forKey: .name)
            url = try c.decode(String.self, forKey: .url)
            mimeType = try? c.decode(String.self, forKey: .mimeType)
            dateUploaded = try? c.decode(String.self, forKey: .dateUploaded)
            size = try? c.decode(Double.self, forKey: .size) // size can be a string for shortcuts
        }
    }

    struct Result: Identifiable, Hashable {
        let m: Material
        let kind: Kind
        let date: String
        let size: Double
        var id: String { m.u }
        var year: String { date.isEmpty ? AB.yearOf(m.t) : String(date.prefix(4)) }
    }

    static func search(_ q: String) async throws -> [Result] {
        var c = URLComponents(string: driveSearchURL)!
        c.queryItems = [
            .init(name: "search", value: q), .init(name: "sort", value: "date"), .init(name: "order", value: "desc"),
            .init(name: "t", value: String(Int(Date().timeIntervalSince1970)))
        ]
        let (data, _) = try await URLSession.shared.data(from: c.url!)
        let r = try JSONDecoder().decode(Reply.self, from: data)
        guard r.success == true, let files = r.files else { throw URLError(.cannotParseResponse) }
        return files.filter { DriveLinks.shared.isBroken($0.url) == nil }.map { f in
            let k = kind(mime: f.mimeType, url: f.url, name: f.name)
            return Result(m: Material(u: f.url, t: f.name, s: "Google Drive"), kind: k, date: f.dateUploaded ?? "", size: f.size ?? 0)
        }
    }

    private static var driveSearchURL: String { AB.driveSearchAPI }

    /// Kind from Drive's mime type; shortcuts use the resolved target (drive-map.json)
    static func kind(mime: String?, url: String, name: String) -> Kind {
        let mime = mime ?? ""
        if mime.contains("folder") { return .folder }
        if mime.contains("pdf") { return .pdf }
        if mime.contains("presentation") { return .ppt }
        if mime.contains("wordprocessing") || mime.hasSuffix(".document") { return .doc }
        if mime.contains("spreadsheet") { return .sheet }
        if mime.hasPrefix("image/") { return .img }
        if mime.hasPrefix("video/") { return .video }
        if mime.contains("zip") || mime.contains("rar") { return .zip }
        return AB.kind(url, name)
    }
}
