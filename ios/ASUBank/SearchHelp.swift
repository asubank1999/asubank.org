import SwiftUI

/// One subject or study word in both languages, from assets/app/search-help.json
struct HelpTerm: Codable {
    var en: String
    var ar: String
    var alt: [String]?
    var p: String?
    var forms: [String] { [en, ar] + (alt ?? []) }
}

/// What the search screen can offer for the text typed so far
struct SearchHints: Equatable {
    /// Other spellings and languages for each word; the search finds any of them
    var alts: [String: [String]] = [:]
    /// Filler words ("مادة", "looking for") that titles don't have to contain
    var skip: Set<String> = []
    /// The search in the other language
    var translated: String?
    var fixes: [String] = []
    var completions: [String] = []
    var subjects: [Subject] = []
}

/// Helps students find a material: translates between Arabic and English, fixes typos and points to subjects.
/// The word list lives on the website, so it can grow without an app update.
@MainActor
final class SearchHelp: ObservableObject {
    static let shared = SearchHelp()
    private struct File: Codable { var stop: [String]?; var terms: [HelpTerm] }

    @Published private(set) var terms: [HelpTerm] = []
    private var stop: Set<String> = []
    private var lookup: [String: Int] = [:]
    private var vocabulary: Set<String> = []
    private var vocabularySize = -1
    private var online: [String: String] = [:]

    private let url = URL(string: "https://asubank.org/assets/app/search-help.json")!
    private let cacheFile = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("search-help.json")

    func load() async {
        if terms.isEmpty, let data = try? Data(contentsOf: cacheFile) { apply(data) }
        var req = URLRequest(url: url)
        req.cachePolicy = .reloadIgnoringLocalCacheData
        if let (data, resp) = try? await URLSession.shared.data14(for: req),
           (resp as? HTTPURLResponse)?.statusCode == 200, apply(data) {
            try? data.write(to: cacheFile, options: .atomic)
        }
    }

    @discardableResult
    private func apply(_ data: Data) -> Bool {
        guard let f = try? JSONDecoder().decode(File.self, from: data) else { return false }
        terms = f.terms
        stop = Set((f.stop ?? []).map(AB.norm))
        lookup = [:]
        for (i, t) in f.terms.enumerated() {
            for form in t.forms { lookup[AB.norm(form)] = lookup[AB.norm(form)] ?? i }
        }
        vocabularySize = -1
        return true
    }

    private func isArabicWord(_ s: String) -> Bool { s.unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) } }

    /// "الفيزياء" and "فيزياء" are the same word
    private func term(_ word: String) -> HelpTerm? {
        if let i = lookup[word] { return terms[i] }
        if word.hasPrefix("ال"), let i = lookup[String(word.dropFirst(2))] { return terms[i] }
        return nil
    }

    func hints(for query: String, library: Library) -> SearchHints {
        var h = SearchHints()
        let nq = AB.norm(query)
        guard nq.count >= 2 else { return h }
        let words = nq.split(separator: " ").map(String.init)
        h.skip = Set(words.filter { stop.contains($0) })
        if h.skip.count == words.count { h.skip = [] }
        let wanted = words.filter { !h.skip.contains($0) }

        var other: [String] = []
        var i = 0
        while i < wanted.count {
            // two-word names first ("test bank", "جبر خطي")
            if i + 1 < wanted.count, let t = term(wanted[i] + " " + wanted[i + 1]) {
                other.append(isArabicWord(wanted[i]) ? t.en : t.ar)
                i += 2
                continue
            }
            let w = wanted[i]
            if let t = term(w) {
                h.alts[w] = t.forms.map(AB.norm).filter { $0 != w }
                other.append(isArabicWord(w) ? t.en : t.ar)
            } else {
                other.append(w)
            }
            i += 1
        }
        let joined = other.joined(separator: " ")
        if AB.norm(joined) != wanted.joined(separator: " ") { h.translated = joined }

        // subjects by name, or the subject a word belongs to
        var subjects: [Subject] = []
        for s in library.subjects where s.c != "Archive" {
            let name = AB.norm(s.t + " " + (s.ar ?? ""))
            if wanted.allSatisfy({ w in name.contains(w) || (h.alts[w] ?? []).contains { name.contains($0) } }) { subjects.append(s) }
        }
        for w in wanted {
            if let p = term(w)?.p, let s = library.subject(forPath: p), !subjects.contains(s) { subjects.append(s) }
        }
        h.subjects = Array(subjects.prefix(3))

        // finish the word being typed
        if let last = wanted.last, last.count >= 2, term(last) == nil {
            let lead = wanted.dropLast().joined(separator: " ")
            var seen = Set<String>()
            for t in terms {
                if let f = t.forms.first(where: { AB.norm($0).hasPrefix(last) }), seen.insert(t.en).inserted {
                    let word = isArabicWord(last) ? f : t.en
                    h.completions.append(lead.isEmpty ? word : lead + " " + word)
                }
                if h.completions.count >= 5 { break }
            }
        }

        // typos: only for words no title contains
        buildVocabulary(library)
        var fixed = wanted, changed = false
        for (j, w) in wanted.enumerated() where w.count >= 4 && term(w) == nil && !library.items.contains(where: { $0.q.contains(w) }) {
            if let c = closest(w) { fixed[j] = c; changed = true }
        }
        if changed { h.fixes = [fixed.joined(separator: " ")] }
        return h
    }

    private func buildVocabulary(_ library: Library) {
        guard vocabularySize != library.items.count else { return }
        vocabularySize = library.items.count
        var counts: [String: Int] = [:]
        for it in library.items {
            for w in Set(AB.norm(it.m.t).split(separator: " ")) where w.count >= 4 { counts[String(w), default: 0] += 1 }
        }
        vocabulary = Set(counts.filter { $0.value >= 3 }.keys)
        for t in terms {
            for f in t.forms { for w in AB.norm(f).split(separator: " ") where w.count >= 4 { vocabulary.insert(String(w)) } }
        }
    }

    private func closest(_ w: String) -> String? {
        let allowed = w.count >= 7 ? 2 : 1
        var best: (String, Int)?
        for v in vocabulary where abs(v.count - w.count) <= allowed {
            let d = Self.distance(Array(w), Array(v), limit: allowed)
            if d <= allowed, best == nil || d < best!.1 || (d == best!.1 && v < best!.0) { best = (v, d) }
        }
        return best?.0
    }

    private static func distance(_ a: [Character], _ b: [Character], limit: Int) -> Int {
        var prev = Array(0...b.count)
        for i in 1...max(a.count, 1) where !a.isEmpty {
            var row = [i] + Array(repeating: 0, count: b.count)
            var low = i
            for j in 1...max(b.count, 1) where !b.isEmpty {
                row[j] = min(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1))
                low = min(low, row[j])
            }
            if low > limit { return limit + 1 }
            prev = row
        }
        return prev[b.count]
    }

    /// Translates text the word list doesn't know, through the accounts Apps Script (Google Translate)
    func translate(_ text: String) async -> String? {
        let key = AB.norm(text)
        if let hit = online[key] { return hit.isEmpty ? nil : hit }
        var req = URLRequest(url: AccountsAPI.url)
        req.httpMethod = "POST"
        req.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["action": "translate", "text": text])
        guard let (data, _) = try? await URLSession.shared.data14(for: req),
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any], o["ok"] as? Bool == true else { return nil }
        let t = (o["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        online[key] = t
        return t.isEmpty ? nil : t
    }
}

/// A titled row of tappable suggestions
struct HelpChips: View {
    let title: String
    let options: [String]
    let pick: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption.weight(.semibold)).fg14(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(options, id: \.self) { o in
                        Button { pick(o) } label: {
                            Text(o).font(.subheadline.weight(.medium))
                                .padding(.horizontal, 12).padding(.vertical, 7)
                                .bgIn14(Color.abAccentSoft, in: Capsule())
                                .fg14(Color.abAccent)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
        .padding(.vertical, 2)
    }
}
