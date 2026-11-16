import SwiftUI

// Message reactions (rooms and private chats) and the story reaction / reply lines in private chats.

enum Reactions {
    /// The same list the server accepts
    static let all = ["❤️", "😂", "😮", "😢", "👍", "🙏", "🔥", "👏"]

    /// The server's recent reaction changes: [{ id, reactions }] -> [message id: reactions]
    static func changes(_ v: Any?) -> [String: [String: String]] {
        var out: [String: [String: String]] = [:]
        for e in (v as? [[String: Any]]) ?? [] {
            if let id = e["id"] as? String { out[id] = (e["reactions"] as? [String: String]) ?? [:] }
        }
        return out
    }

    /// My reaction toggled: the same emoji again takes it back
    static func toggled(_ map: [String: String]?, me: String, emoji: String) -> [String: String] {
        var m = map ?? [:]
        if m[me] == emoji { m.removeValue(forKey: me) } else { m[me] = emoji }
        return m
    }
}

/// Little emoji counts under a bubble; tapping one reacts with it too (or takes mine back)
struct ReactionChips: View {
    let reactions: [String: String]?
    let me: String?
    let mine: Bool
    let tap: (String) -> Void

    var body: some View {
        let map = reactions ?? [:]
        if !map.isEmpty {
            let counts = Dictionary(grouping: map.values, by: { $0 }).mapValues(\.count)
            let order = Reactions.all.filter { counts[$0] != nil } + counts.keys.filter { !Reactions.all.contains($0) }.sorted()
            HStack(spacing: 4) {
                ForEach(order, id: \.self) { e in
                    let isMine = me.map { map[$0] == e } ?? false
                    Button { Haptics.tap(); tap(e) } label: {
                        HStack(spacing: 2) {
                            Text(e).font(.caption)
                            if (counts[e] ?? 0) > 1 { Text("\(counts[e] ?? 0)").font(.caption2.weight(.semibold)) }
                        }
                        .padding(.horizontal, 7).padding(.vertical, 3)
                        .background(isMine ? Color.abAccent.opacity(0.22) : Color(.secondarySystemBackground), in: Capsule())
                        .overlay(Capsule().stroke(isMine ? Color.abAccent.opacity(0.6) : Color.primary.opacity(0.08), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity, alignment: mine ? .trailing : .leading)
            .padding(mine ? .trailing : .leading, 6)
            .transition(.scale.combined(with: .opacity))
        }
    }
}

/// The row of emojis at the top of a message's long-press menu
struct ReactionMenu: View {
    let current: String?
    let pick: (String) -> Void
    var body: some View {
        ControlGroup {
            ForEach(Reactions.all, id: \.self) { e in
                Button { pick(e) } label: { Text(current == e ? "\(e)✓" : e) }
            }
        }
    }
}

/// Story reactions and replies arrive as private messages. Older apps send them as plain text
/// ("😂 Reacted to your story", "↩️ Replied to your story: hi"), so the text is read and each person sees it from their own side.
enum StoryNote {
    enum Kind { case reaction(String), reply(String) }

    static func parse(_ text: String) -> Kind? {
        if text.hasPrefix("↩️ Replied to your story: ") {
            return .reply(String(text.dropFirst("↩️ Replied to your story: ".count)))
        }
        if text.hasSuffix(" Reacted to your story") {
            let e = String(text.dropLast(" Reacted to your story".count)).trimmingCharacters(in: .whitespaces)
            if !e.isEmpty && e.count <= 4 { return .reaction(e) }
        }
        return nil
    }

    /// "her story", "his story" or "their story", from what the person shows on their profile
    static func their(_ gender: String?) -> String {
        switch gender?.lowercased() {
        case "female": return L("her story")
        case "male": return L("his story")
        default: return L("their story")
        }
    }

    /// The line above the bubble: "You reacted 😂 to her story" / "Sara replied to your story"
    static func line(_ k: Kind, sentByMe: Bool, otherName: String, otherGender: String?) -> String {
        switch (k, sentByMe) {
        case (.reaction(let e), true): return String(format: L("You reacted %@ to %@"), e, their(otherGender))
        case (.reaction(let e), false): return String(format: L("%@ reacted %@ to your story"), otherName, e)
        case (.reply, true): return String(format: L("You replied to %@"), their(otherGender))
        case (.reply, false): return String(format: L("%@ replied to your story"), otherName)
        }
    }

    /// For chat lists and notifications, seen by the person who got it
    static func preview(_ text: String, sentByMe: Bool, otherName: String, otherGender: String?) -> String {
        guard let k = parse(text) else { return text }
        switch k {
        case .reaction: return line(k, sentByMe: sentByMe, otherName: otherName, otherGender: otherGender)
        case .reply(let t): return line(k, sentByMe: sentByMe, otherName: otherName, otherGender: otherGender) + ": " + t
        }
    }
}
