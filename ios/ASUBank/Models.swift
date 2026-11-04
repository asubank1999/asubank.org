import Foundation

/// One subject page (e.g. /banks/pharmacy), from materials.json
struct Subject: Codable, Identifiable, Hashable {
    var p: String
    var t: String
    var ar: String?
    var c: String
    var e: String?
    var n: Int?
    var id: String { p }
}

/// One study material link, from materials.json or a Drive search result
struct Material: Codable, Identifiable, Hashable {
    var u: String
    var t: String
    var s: String?
    var p: String?
    var c: String?
    var tag: String?
    var d: String? // date the link was added, "YYYY-MM-DD"
    var id: String { u }
}

/// A material with its search text and kind worked out once
struct IndexedMaterial: Identifiable, Hashable {
    let m: Material
    let q: String
    let kind: Kind
    let year: String
    let isNew: Bool
    var id: String { m.u }
}

struct Catalog: Codable {
    var subjects: [Subject]
    var items: [Material]
}

/// Saved / recent entry
struct SavedItem: Codable, Identifiable, Hashable {
    var u: String
    var t: String
    var s: String
    var date: Date
    var id: String { u }
    var material: Material { Material(u: u, t: t, s: s) }
}

enum Kind: String, CaseIterable {
    case folder, pdf, doc, ppt, sheet, img, video, zip, file, link, page

    var name: String {
        switch self {
        case .folder: return "Folder"
        case .pdf: return "PDF"
        case .doc: return "Document"
        case .ppt: return "Slides"
        case .sheet: return "Sheet"
        case .img: return "Image"
        case .video: return "Video"
        case .zip: return "Archive"
        case .file: return "File"
        case .link: return "Link"
        case .page: return "Page"
        }
    }

    var symbol: String {
        switch self {
        case .folder: return "folder.fill"
        case .pdf: return "doc.richtext.fill"
        case .doc: return "doc.text.fill"
        case .ppt: return "play.rectangle.fill"
        case .sheet: return "tablecells.fill"
        case .img: return "photo.fill"
        case .video: return "video.fill"
        case .zip: return "archivebox.fill"
        case .file: return "doc.fill"
        case .link: return "link"
        case .page: return "safari.fill"
        }
    }
}

// MARK: - Helpers shared with the website (assets/app/app.js)

private func has(_ pattern: String, _ s: String) -> Bool {
    s.range(of: pattern, options: .regularExpression) != nil
}

private func firstGroup(_ pattern: String, _ s: String) -> String? {
    guard let re = try? NSRegularExpression(pattern: pattern),
          let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)),
          m.numberOfRanges > 1, let r = Range(m.range(at: 1), in: s) else { return nil }
    return String(s[r])
}

enum AB {
    static let site = URL(string: "https://asubank.org")!
    static let catalogURL = URL(string: "https://asubank.org/assets/app/materials.json")!
    static let driveSearchAPI = "https://script.google.com/macros/s/AKfycbz9_c0Hr4aOusP5Wskn9fIw6UO6m8k4Esps3wJqKvZv2LQf6qcNFqP99cgU5UFOskVHsQ/exec"
    static let uploadAPI = URL(string: "https://script.google.com/macros/s/AKfycbygD_bFuQN7k9zLKogIip_zEaXmsq9Vw-z6QPfMte1AAuLdq4Vf4-kGIgvy6hgVcOo/exec")!
    static let whatsapp = URL(string: "https://chat.whatsapp.com/LS92JgjO26Y5SZK9SVJ9FJ")!

    /// Lowercase, strip Arabic diacritics and unify letter forms so "أ" finds "ا" etc.
    static func norm(_ s: String) -> String {
        var x = s.lowercased()
        x = x.replacingOccurrences(of: #"[ً-ْـ]"#, with: "", options: .regularExpression)
        x = x.replacingOccurrences(of: "[أإآ]", with: "ا", options: .regularExpression)
        x = x.replacingOccurrences(of: "ة", with: "ه").replacingOccurrences(of: "ى", with: "ي")
        x = x.replacingOccurrences(of: #"[✅✔⭐🔥️]"#, with: "", options: .regularExpression)
        x = x.replacingOccurrences(of: #"[_\-–—.,:;()\[\]{}|/\\]+"#, with: " ", options: .regularExpression)
        x = x.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        return x.trimmingCharacters(in: .whitespaces)
    }

    /// "2025", "2024/25" and semester codes like "20231" all count as a year
    static func yearOf(_ t: String) -> String {
        firstGroup(#"(?:^|\D)(20[12]\d)[123]?(?!\d)"#, t) ?? ""
    }

    static func driveId(_ u: String) -> String? {
        firstGroup(#"/(?:file|document|presentation|spreadsheets)/d/([\w-]{10,})"#, u)
            ?? firstGroup(#"[?&]id=([\w-]{10,})"#, u)
            ?? firstGroup(#"/folders/([\w-]{10,})"#, u)
    }

    static func kind(_ u: String, _ t: String = "") -> Kind {
        let s = (u + " " + t).lowercased()
        if has(#"^/(?!/)"#, u) || has(#"^https?://(www\.)?asubank\.org"#, u) { return .page }
        if has(#"drive\.google\.com/(drive/(u/\d/)?folders|embeddedfolderview|folderview)"#, u) || has(#"dropbox\.com/(scl/fo|sh)/"#, u) || has(#"sharepoint\.com/:f:"#, u) { return .folder }
        if has(#"sharepoint\.com/:v:|youtube\.com/(playlist|@)"#, u) { return .video }
        if has(#"docs\.google\.com/document"#, u) { return .doc }
        if has(#"docs\.google\.com/presentation"#, u) { return .ppt }
        if has(#"docs\.google\.com/spreadsheets"#, u) { return .sheet }
        if has(#"\.pdf\b"#, s) { return .pdf }
        if has(#"\.(pptx?|key)\b"#, s) { return .ppt }
        if has(#"\.(docx?|rtf|odt)\b"#, s) { return .doc }
        if has(#"\.(xlsx?|csv)\b"#, s) { return .sheet }
        if has(#"\.(jpe?g|png|gif|webp|heic)\b"#, s) { return .img }
        if has(#"\.(mp4|mov|mkv)\b|youtube\.com|youtu\.be"#, s) { return .video }
        if has(#"\.(zip|rar|7z)\b"#, s) { return .zip }
        if has(#"drive\.google\.com/(file|open|uc)"#, u), let id = driveId(u), DriveLinks.shared.target(id)?.folder == true { return .folder }
        if has(#"drive\.google\.com/(file|open|uc)"#, u) || has(#"dropbox\.com"#, u) { return .file }
        return .link
    }

    /// Absolute URL for a material link ("/banks/x" becomes https://asubank.org/banks/x)
    static func absolute(_ u: String) -> URL? {
        if u.hasPrefix("/") { return URL(string: u, relativeTo: site)?.absoluteURL }
        return URL(string: u)
    }

    /// Inline viewer URL for Google Drive / Docs / YouTube links, otherwise the link itself
    static func previewURL(_ u: String) -> URL? {
        let id = driveId(u)
        if has(#"drive\.google\.com/(drive/(u/\d/)?folders|embeddedfolderview)"#, u), let id {
            return URL(string: "https://drive.google.com/embeddedfolderview?id=\(id)#list")
        }
        if let type = firstGroup(#"docs\.google\.com/(document|presentation|spreadsheets)/d/[\w-]+"#, u),
           let did = firstGroup(#"docs\.google\.com/(?:document|presentation|spreadsheets)/d/([\w-]+)"#, u) {
            return URL(string: "https://docs.google.com/\(type)/d/\(did)/preview")
        }
        if has(#"drive\.google\.com/(file|open|uc)"#, u), let id {
            return URL(string: "https://drive.google.com/file/d/\(id)/preview")
        }
        if let y = firstGroup(#"(?:youtube\.com/watch\?v=|youtu\.be/)([\w-]{6,})"#, u) {
            return URL(string: "https://www.youtube-nocookie.com/embed/\(y)")
        }
        return absolute(u)
    }

    static func index(_ m: Material) -> IndexedMaterial {
        IndexedMaterial(m: m, q: norm(m.t + " " + (m.s ?? "") + " " + (m.c ?? "")), kind: kind(m.u, m.t), year: yearOf(m.t), isNew: isNew(m.d))
    }

    /// NEW badges last 30 days after a link is added (same rule as the website)
    static let newDays = 30.0
    private static let dayFormat: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
    static func date(_ s: String?) -> Date? {
        guard let s, s.count >= 10 else { return nil }
        return dayFormat.date(from: String(s.prefix(10)))
    }
    static func isNew(_ d: String?) -> Bool {
        guard let date = date(d) else { return false }
        return Date().timeIntervalSince(date) < newDays * 86400
    }

    /// "https://asubank.org/guides/faqs.html#x" -> "/guides/faqs"; nil for other sites
    static func sitePath(_ u: String) -> String? {
        var p = u.trimmingCharacters(in: .whitespaces)
        if let r = p.range(of: #"^https?://(www\.)?asubank\.org"#, options: [.regularExpression, .caseInsensitive]) {
            p.removeSubrange(r)
            if p.isEmpty { p = "/" }
        }
        guard p.hasPrefix("/"), !p.hasPrefix("//") else { return nil }
        if let i = p.firstIndex(where: { $0 == "?" || $0 == "#" }) { p = String(p[..<i]) }
        p = p.replacingOccurrences(of: #"\.html?$"#, with: "", options: .regularExpression)
        while p.count > 1 && p.hasSuffix("/") { p.removeLast() }
        return p == "/index" ? "/" : p
    }

    /// Score items for a query; every word must match (same ranking as the website)
    /// alts: other spellings or languages of a word; skip: filler words titles need not contain
    static func match(_ items: [IndexedMaterial], _ q: String, limit: Int = 200, alts: [String: [String]] = [:], skip: Set<String> = []) -> [IndexedMaterial] {
        let nq = norm(q)
        guard !nq.isEmpty else { return [] }
        let toks = nq.split(separator: " ").map(String.init).filter { !skip.contains($0) }
        var out: [(Int, IndexedMaterial)] = []
        for it in items {
            var score = 0, ok = true
            for t in toks {
                var best = 0
                for form in [t] + (alts[t] ?? []) {
                    guard let r = it.q.range(of: form) else { continue }
                    if r.lowerBound == it.q.startIndex { best = max(best, 6) }
                    else if it.q[it.q.index(before: r.lowerBound)] == " " { best = max(best, 4) }
                    else { best = max(best, 1) }
                }
                if best == 0 { ok = false; break }
                score += best
            }
            if !ok { continue }
            let tn = norm(it.m.t)
            if tn.hasPrefix(nq) { score += 8 } else if tn.contains(nq) { score += 4 }
            if it.kind == .folder { score += 1 }
            if it.isNew { score += 1 }
            out.append((score, it))
        }
        return out.sorted { $0.0 > $1.0 }.prefix(limit).map { $0.1 }
    }
}

extension AB {
    /// First capture group of a regex, for other files (firstGroup is private to this one)
    static func group(_ pattern: String, _ s: String) -> String? { firstGroup(pattern, s) }
}
