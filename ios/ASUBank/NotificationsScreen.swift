import SwiftUI

/// One line in the Notifications tab, from the accounts API (action "notifications")
struct Notice: Codable, Identifiable, Equatable {
    var id: String
    var type: String
    var created: String
    var text: String?
    var name: String?
    var userId: String?
    var avatar: String?
    var color: String?
    var kind: String?
    var url: String?
}

/// Phone notifications the app showed by itself (lecture reminders, chat and private messages), kept on this
/// phone so the Notifications tab lists everything that rang, not only what the server keeps.
@MainActor
final class LocalNotices {
    static let shared = LocalNotices()
    private static let key = "ab.notices.local"
    private static let lectureCheckKey = "ab.notices.lectureCheck"
    private(set) var items: [Notice] = {
        guard let d = UserDefaults.standard.data(forKey: LocalNotices.key) else { return [] }
        return (try? JSONDecoder().decode([Notice].self, from: d)) ?? []
    }()

    func add(id: String, type: String, title: String, body: String, room: String? = nil, at: Date = Date()) {
        guard !items.contains(where: { $0.id == id }) else { return }
        items.append(Notice(id: id, type: type, created: ISO8601DateFormatter.ab.string(from: at), text: body, name: title, kind: room))
        items.sort { $0.created > $1.created }
        if items.count > 150 { items.removeLast(items.count - 150) }
        if let d = try? JSONEncoder().encode(items) { UserDefaults.standard.set(d, forKey: Self.key) }
        NoticeStore.shared.refreshLocal()
    }

    /// Lecture reminders are set ahead and ring while the app is closed, so add the ones that rang since the last look
    func catchUpLectures() {
        let now = Date()
        let d = UserDefaults.standard
        guard let last = d.object(forKey: Self.lectureCheckKey) as? Date else { d.set(now, forKey: Self.lectureCheckKey); return }
        d.set(now, forKey: Self.lectureCheckKey)
        let cal = Calendar.current
        let from = max(last, now.addingTimeInterval(-8 * 86400))
        var day = cal.startOfDay(for: from)
        while day <= now {
            let weekday = cal.component(.weekday, from: day)
            for l in LectureStore.shared.lectures where l.enabled && l.days.contains(weekday) {
                guard let start = cal.date(bySettingHour: l.hour, minute: l.minute, second: 0, of: day) else { continue }
                let rang = start.addingTimeInterval(TimeInterval(-LectureStore.before * 60))
                guard rang > last, rang <= now else { continue }
                add(id: "lecture-\(l.id)-\(Int(rang.timeIntervalSince1970))", type: "lecture",
                    title: String(format: L("%@ in 10 minutes"), l.subject),
                    body: l.hall.isEmpty ? String(format: L("Starts at %@"), l.timeText) : String(format: L("Hall %@ · starts at %@"), l.hall, l.timeText),
                    at: rang)
            }
            guard let next = cal.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
    }
}

/// The list and what's unread. Kept in one place so the tab's badge and the screen agree.
@MainActor
final class NoticeStore: ObservableObject {
    static let shared = NoticeStore()
    private static let readKey = "ab.notices.lastRead"

    @Published private(set) var items: [Notice] = []
    @Published private(set) var loaded = false
    @Published private(set) var failed: String?
    @Published private(set) var lastRead = UserDefaults.standard.string(forKey: NoticeStore.readKey) ?? ""
    /// what the server keeps; `items` adds the phone's own notifications to it
    private var server: [Notice] = []

    func refreshLocal() {
        let all = (server + LocalNotices.shared.items).sorted { $0.created > $1.created }
        if all != items { items = all }
        if !AccountStore.shared.signedIn { loaded = true }
    }

    var unread: Int { items.filter { $0.created > lastRead }.count }

    func load() async {
        let account = AccountStore.shared
        LocalNotices.shared.catchUpLectures()
        guard account.signedIn else { server = []; refreshLocal(); return }
        if server.isEmpty, let r = account.saved("notifications") { server = decode([Notice].self, r["items"]) ?? []; refreshLocal(); loaded = true }
        do {
            let r = try await account.callSaving("notifications", ["social2": true])
            server = decode([Notice].self, r["items"]) ?? []
            refreshLocal()
            failed = nil
            // the first time, what's already there isn't "new"
            if lastRead.isEmpty { markRead() }
        } catch { failed = error.localizedDescription }
        loaded = true
    }

    func reset() {
        server = []
        items = LocalNotices.shared.items
        loaded = false
        lastRead = ""
        UserDefaults.standard.removeObject(forKey: Self.readKey)
    }

    func markRead() {
        guard let newest = items.map(\.created).max(), newest > lastRead else { return }
        lastRead = newest
        UserDefaults.standard.set(newest, forKey: Self.readKey)
    }
}

struct NotificationsTab: View {
    var body: some View { ABStack { NotificationsScreen() } }
}

struct NotificationsScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var store = NoticeStore.shared
    @EnvironmentObject private var app: AppState
    /// What was unread when the screen opened, so the dots stay while you're looking
    @State private var readBefore: String?

    var body: some View {
        Group {
            if account.signedIn || !store.items.isEmpty {
                List {
                    if !account.signedIn {
                        Button("Sign in to see friend requests, pokes and uploads too") { account.signInShown = true }
                    }
                    if let e = store.failed, store.items.isEmpty { Text(e).fg14(.red) }
                    if !store.loaded { HStack { Spacer(); ABDots(); Spacer() } }
                    else if store.items.isEmpty && store.failed == nil {
                        EmptyState(symbol: "bell", title: "Nothing yet", text: "Friend requests, pokes, new uploads and announcements will show up here.").listRowBackground(Color.clear)
                    }
                    ForEach(store.items) { n in row(n) }
                }
                .listStyle(InsetGroupedListStyle())
                .refreshable14 { await store.load() }
            } else {
                SignInPrompt(forNotifications: true)
            }
        }
        .navigationTitle("Notifications")
        .task14 {
            await store.load()
            if readBefore == nil { readBefore = store.lastRead }
            store.markRead()
        }
    }

    @ViewBuilder private func row(_ n: Notice) -> some View {
        switch n.type {
        case "friend_request":
            NavigationLink { FriendsScreen() } label: { content(n) }
        case "post_comment":
            NavigationLink { FeedScreen() } label: { content(n) }
        case "friend_accept", "poke":
            if let id = n.userId, !id.isEmpty {
                NavigationLink { ProfileScreen(userId: id, fallbackName: n.name ?? "", fallbackColor: n.color ?? "#8C1D40") } label: { content(n) }
            } else { content(n) }
        case "upload":
            if let u = n.url, !u.isEmpty {
                NavigationLink { MaterialScreen(material: Material(u: u, t: n.text ?? "")) } label: { content(n) }
            } else { content(n) }
        case "announcement":
            Button { app.pendingRoom = "announcements"; app.tab = .chat } label: { content(n) }.buttonStyle(PlainButtonStyle())
        case "group_request":
            NavigationLink { AdminGroupRequestsScreen() } label: { content(n) }
        case "report":
            NavigationLink { AdminReportsScreen() } label: { content(n) }
        case "suggestion":
            NavigationLink { AdminSuggestionsScreen() } label: { content(n) }
        case "lecture":
            Button { app.tab = .lectures } label: { content(n) }.buttonStyle(PlainButtonStyle())
        case "chat", "dm":
            Button { app.pendingRoom = n.type == "dm" ? "__dm" : (n.kind ?? ""); app.tab = .chat } label: { content(n) }.buttonStyle(PlainButtonStyle())
        default:
            content(n)
        }
    }

    private func content(_ n: Notice) -> some View {
        HStack(spacing: 12) {
            if n.userId?.isEmpty == false {
                Avatar(name: n.name ?? "", color: n.color ?? "#8C1D40", size: 40, photo: n.avatar)
            } else {
                Text(emoji(n.type)).font(.title3)
                    .frame(width: 40, height: 40)
                    .bgIn14(Color.primary.opacity(0.08), in: Circle())
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title(n)).font(.subheadline.weight(.semibold)).lineLimit(2)
                let b = detail(n)
                if !b.isEmpty { Text(b).font(.subheadline).fg14(.secondary).lineLimit(3) }
                if let d = date(n.created) { Text(d.rel14()).font(.caption).fg14(.secondary) }
            }
            Spacer(minLength: 0)
            if let before = readBefore, n.created > before {
                Circle().fill(Color.abAccent).frame(width: 9, height: 9)
            }
        }
        .padding(.vertical, 2)
    }

    private func date(_ s: String) -> Date? { ISO8601DateFormatter.ab.date(from: s) ?? ISO8601DateFormatter().date(from: s) }

    private func emoji(_ type: String) -> String {
        switch type {
        case "upload": return "📄"
        case "announcement": return "📣"
        case "report": return "🚩"
        case "suggestion": return "💡"
        case "group_approved": return "✅"
        case "group_rejected": return "❌"
        case "group_request": return "💬"
        case "post_comment": return "💬"
        case "lecture": return "⏰"
        case "chat", "dm": return "💬"
        default: return "🔔"
        }
    }

    private func title(_ n: Notice) -> String {
        switch n.type {
        case "friend_request": return L("New friend request")
        case "friend_accept": return L("You have a new friend")
        case "poke": return n.name ?? ""
        case "post_comment": return String(format: L("%@ commented on your post"), n.name ?? "")
        case "upload": return L("New upload on UniHub")
        case "announcement": return L("Announcement")
        case "group_request": return L("New group request")
        case "group_approved": return L("Your group was accepted")
        case "group_rejected": return L("Your group wasn't accepted")
        case "report": return L("New report")
        case "suggestion": return L("New suggestion")
        default: return n.name ?? ""
        }
    }

    private func detail(_ n: Notice) -> String {
        let who = n.name ?? ""
        switch n.type {
        case "friend_request": return String(format: L("%@ wants to be your friend"), who)
        case "friend_accept": return String(format: L("%@ accepted your friend request"), who)
        case "poke": return Social.pokeText(kind: n.kind ?? "poke", name: who, place: n.text)
        case "group_request": return String(format: L("%@ asked to add: %@"), who, n.text ?? "")
        default: return n.text ?? ""
        }
    }
}
