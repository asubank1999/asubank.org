import SwiftUI

/// One line in the Notifications tab, from the accounts API (action "notifications")
struct Notice: Decodable, Identifiable, Equatable {
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

/// The list and what's unread. Kept in one place so the tab's badge and the screen agree.
@MainActor
final class NoticeStore: ObservableObject {
    static let shared = NoticeStore()
    private static let readKey = "ab.notices.lastRead"

    @Published private(set) var items: [Notice] = []
    @Published private(set) var loaded = false
    @Published private(set) var failed: String?
    @Published private(set) var lastRead = UserDefaults.standard.string(forKey: NoticeStore.readKey) ?? ""

    var unread: Int { items.filter { $0.created > lastRead }.count }

    func load() async {
        let account = AccountStore.shared
        guard account.signedIn else { items = []; loaded = false; return }
        if items.isEmpty, let r = account.saved("notifications") { items = decode([Notice].self, r["items"]) ?? []; loaded = true }
        do {
            let r = try await account.callSaving("notifications", ["social2": true])
            let list = decode([Notice].self, r["items"]) ?? []
            if list != items { items = list }
            failed = nil
            // the first time, what's already there isn't "new"
            if lastRead.isEmpty { markRead() }
        } catch { failed = error.localizedDescription }
        loaded = true
    }

    func reset() {
        items = []
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
            if account.signedIn {
                List {
                    if let e = store.failed, store.items.isEmpty { Text(e).foregroundStyle(.red) }
                    if !store.loaded { HStack { Spacer(); ABDots(); Spacer() } }
                    else if store.items.isEmpty && store.failed == nil {
                        EmptyState(symbol: "bell", title: "Nothing yet", text: "Friend requests, pokes, new uploads and announcements will show up here.").listRowBackground(Color.clear)
                    }
                    ForEach(store.items) { n in row(n) }
                }
                .listStyle(.insetGrouped)
                .refreshable { await store.load() }
            } else {
                SignInPrompt(forNotifications: true)
            }
        }
        .navigationTitle("Notifications")
        .task {
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
            Button { app.pendingRoom = "announcements"; app.tab = .chat } label: { content(n) }.buttonStyle(.plain)
        case "group_request":
            NavigationLink { AdminGroupRequestsScreen() } label: { content(n) }
        case "report":
            NavigationLink { AdminReportsScreen() } label: { content(n) }
        case "suggestion":
            NavigationLink { AdminSuggestionsScreen() } label: { content(n) }
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
                    .background(Color.primary.opacity(0.08), in: Circle())
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title(n)).font(.subheadline.weight(.semibold)).lineLimit(2)
                let b = detail(n)
                if !b.isEmpty { Text(b).font(.subheadline).foregroundStyle(.secondary).lineLimit(3) }
                if let d = date(n.created) { Text(d.formatted(.relative(presentation: .named))).font(.caption).foregroundStyle(.secondary) }
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
