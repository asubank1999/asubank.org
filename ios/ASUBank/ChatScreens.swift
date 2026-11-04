import SwiftUI

/// The Chat tab: rooms, or a sign-in prompt
struct ChatTab: View {
    @EnvironmentObject private var app: AppState
    @ObservedObject private var account = AccountStore.shared
    @State private var openRoom: ChatRoom?
    @State private var editingRoom: ChatRoom?
    @State private var addingRoom = false
    @State private var online: [OnlineUser] = []
    @State private var onlineCount = 0

    var body: some View {
        ABStack {
            Group {
                if account.signedIn { rooms.modifier(CommunityRulesGate()) } else { SignInPrompt() }
            }
            .navigationTitle("Chat")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    if account.isAdmin {
                        Button { addingRoom = true } label: { Image(systemName: "plus.bubble") }.accessibilityLabel(Text("Add a room"))
                    }
                }
            }
            .push(isPresented: Binding(get: { openRoom != nil }, set: { if !$0 { openRoom = nil } })) {
                if let r = openRoom { ChatRoomView(room: r) }
            }
            .sheet(isPresented: $addingRoom) { RoomEditor(room: nil) }
            .sheet(item: $editingRoom) { RoomEditor(room: $0) }
        }
        .onAppear(perform: takeRoom)
        .onChange(of: app.pendingRoom) { _ in takeRoom() }
        .task { await loadOnline() }
    }

    private func takeRoom() {
        guard let id = app.pendingRoom, account.signedIn else { return }
        app.pendingRoom = nil
        openRoom = account.rooms.first { $0.id == id }
    }

    private func loadOnline() async {
        guard account.signedIn, let r = try? await account.call("online") else { return }
        online = decode([OnlineUser].self, r["users"]) ?? []
        onlineCount = (r["onlineCount"] as? NSNumber)?.intValue ?? 0
    }

    private var rooms: some View {
        List {
            Section {
                HStack(spacing: 12) {
                    if let u = account.user { Avatar(name: u.displayName, color: u.color, size: 44, admin: u.isAdmin, photo: u.avatar, online: true) }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(String(format: L("Hi, %@"), account.user?.displayName ?? "")).font(.headline)
                        Text("Be kind and help each other. Don't share personal info.").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    RouteLinkAccount()
                }
                .padding(.vertical, 2)
            }
            if !online.isEmpty {
                Section {
                    NavigationLink { OnlineScreen(users: online) } label: {
                        HStack(spacing: -8) {
                            ForEach(online.prefix(6)) { u in
                                Avatar(name: u.displayName, color: u.color ?? "#8C1D40", size: 32, photo: u.avatar, online: u.online)
                            }
                            Text(String(format: L("%d online now"), onlineCount)).font(.subheadline.weight(.semibold)).padding(.leading, 16)
                        }
                    }
                }
            }
            Section("Rooms") {
                ForEach(account.rooms) { r in
                    Button { openRoom = r } label: { roomLabel(r) }
                    .contextMenu {
                        if account.isAdmin {
                            Button { editingRoom = r } label: { Label("Edit room", systemImage: "slider.horizontal.3") }
                            Button { Task { await RoomEditor.quick(r, locked: !(r.locked == true)) } } label: {
                                Label(r.locked == true ? "Unlock" : "Lock", systemImage: r.locked == true ? "lock.open" : "lock")
                            }
                            Button { Task { await RoomEditor.quick(r, archived: !(r.archived == true)) } } label: {
                                Label(r.archived == true ? "Unarchive" : "Archive", systemImage: "archivebox")
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .refreshable { await account.refresh(); await loadOnline() }
    }

    private func roomLabel(_ r: ChatRoom) -> some View {
        HStack(spacing: 12) {
            Text(r.emoji).font(.title2).frame(width: 44, height: 44)
                .background(Color.abAccentSoft, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .opacity(r.archived == true ? 0.5 : 1)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(r.name).font(.body.weight(.semibold)).foregroundStyle(.primary)
                    if r.adminOnly == true { Badge(text: L("TEAM"), color: .orange) }
                    if r.locked == true { Image(systemName: "lock.fill").font(.caption).foregroundStyle(.secondary) }
                    if r.archived == true { Badge(text: L("ARCHIVED"), color: .gray) }
                }
                Text(r.about).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
        }
    }
}

private struct RouteLinkAccount: View {
    @State private var shown = false
    var body: some View {
        Button { shown = true } label: { Image(systemName: "person.crop.circle").font(.title2) }
            .sheet(isPresented: $shown) { ABStack { AccountScreen().toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { shown = false } } } } }
    }
}

struct OnlineUser: Codable, Identifiable, Hashable {
    var id: String
    var displayName: String
    var avatar: String?
    var color: String?
    var role: String?
    var badges: [String]?
    var lastSeen: String?
    var online: Bool
}

/// Everyone who used the chat lately: online first, then last seen
struct OnlineScreen: View {
    let users: [OnlineUser]
    var body: some View {
        List {
            let on = users.filter(\.online), off = users.filter { !$0.online }
            if !on.isEmpty { Section(String(format: L("Online · %d"), on.count)) { ForEach(on) { row($0) } } }
            if !off.isEmpty { Section(String(format: L("Offline · %d"), off.count)) { ForEach(off) { row($0) } } }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Who's online")
    }

    private func row(_ u: OnlineUser) -> some View {
        NavigationLink { ProfileScreen(userId: u.id, fallbackName: u.displayName, fallbackColor: u.color ?? "#8C1D40") } label: {
            HStack(spacing: 12) {
                Avatar(name: u.displayName, color: u.color ?? "#8C1D40", size: 40, admin: u.role == "admin", photo: u.avatar, online: u.online)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) { Text(u.displayName).font(.body.weight(.semibold)); BadgeRow(badges: u.badges ?? [], compact: true) }
                    Text(u.online ? L("Online now") : lastSeenText(u.lastSeen)).font(.caption).foregroundStyle(u.online ? Color.green : .secondary)
                }
            }
        }
    }
}

// MARK: - room editor (admins)

struct RoomEditor: View {
    let room: ChatRoom?
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    @State private var name = ""
    @State private var emoji = "💬"
    @State private var about = ""
    @State private var adminOnly = false
    @State private var locked = false
    @State private var archived = false
    @State private var order = 0
    @State private var busy = false
    @State private var error: String?
    @State private var confirmDelete = false
    @State private var confirmClear = false

    private let emojis = ["💬", "🆘", "📚", "💊", "💻", "📈", "📣", "🎓", "🧪", "⚖️", "🎨", "🏀", "🎮", "🕌", "☕️", "🔬", "🧮", "🌍"]

    var body: some View {
        ABStack {
            Form {
                Section("Room") {
                    TextField("Name", text: $name)
                    TextField("What it's for", text: $about)
                }
                Section("Icon") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(emojis, id: \.self) { e in
                                Button { emoji = e } label: {
                                    Text(e).font(.title2).frame(width: 42, height: 42)
                                        .background(emoji == e ? Color.abAccentSoft : .clear, in: RoundedRectangle(cornerRadius: 10))
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                }
                Section {
                    Toggle("Only admins can post", isOn: $adminOnly)
                    Toggle("Locked (read-only for now)", isOn: $locked)
                    Toggle("Archived (hidden from students)", isOn: $archived)
                    Stepper(String(format: L("Position %d"), order + 1), value: $order, in: 0...50)
                } header: { Text("Settings") }
                if let room {
                    Section {
                        Button(role: .destructive) { confirmClear = true } label: { Label("Clear all messages", systemImage: "eraser") }
                        if room.id != "announcements" {
                            Button(role: .destructive) { confirmDelete = true } label: { Label("Delete room", systemImage: "trash") }
                        }
                    }
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle(room == nil ? "New room" : "Edit room").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Saving…" : "Save") { Task { await save() } }.disabled(busy || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .confirmationDialog("Delete this room?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete room", role: .destructive) { Task { await run("adminRoomDelete", ["id": room?.id ?? ""]) } }
            } message: { Text("Its messages disappear from the apps. This can't be undone.") }
            .confirmationDialog("Clear every message in this room?", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Clear messages", role: .destructive) { Task { await run("adminClearRoom", ["room": room?.id ?? ""]) } }
            }
            .onAppear {
                guard let r = room else { order = account.rooms.count; return }
                name = r.name; emoji = r.emoji; about = r.about; adminOnly = r.adminOnly == true
                locked = r.locked == true; archived = r.archived == true; order = r.order ?? 0
            }
        }
    }

    private func save() async {
        var f: [String: Any] = ["name": name, "emoji": emoji, "about": about, "adminOnly": adminOnly, "locked": locked, "archived": archived, "order": order]
        if let id = room?.id { f["id"] = id }
        await run("adminRoomSave", f)
    }

    private func run(_ action: String, _ fields: [String: Any]) async {
        busy = true
        do {
            let r = try await account.call(action, fields)
            if r["rooms"] != nil { account.setRooms(r["rooms"]) } else { await account.reloadRooms() }
            Haptics.success()
            dismiss()
        } catch { self.error = error.localizedDescription; Haptics.error() }
        busy = false
    }

    /// Lock / archive from the room list's long-press menu
    @MainActor static func quick(_ r: ChatRoom, locked: Bool? = nil, archived: Bool? = nil) async {
        let f: [String: Any] = ["id": r.id, "name": r.name, "emoji": r.emoji, "about": r.about, "adminOnly": r.adminOnly == true,
                                "locked": locked ?? (r.locked == true), "archived": archived ?? (r.archived == true), "order": r.order ?? 0]
        if let res = try? await AccountStore.shared.call("adminRoomSave", f) { AccountStore.shared.setRooms(res["rooms"]); Haptics.success() }
    }
}

// MARK: - room

@MainActor
final class RoomModel: ObservableObject {
    @Published var room: ChatRoom
    @Published var messages: [ChatMessage] = []
    @Published var people: [String: ChatPerson] = [:]
    @Published var pinned: ChatMessage?
    @Published var loading = true
    @Published var error: String?
    private var poll: Task<Void, Never>?

    init(room: ChatRoom) { self.room = room }

    func start() {
        ChatNotifier.shared.openRoom = room.id
        poll?.cancel()
        poll = Task { [weak self] in
            while !Task.isCancelled {
                await self?.fetch()
                try? await Task.sleep(nanoseconds: 3_000_000_000)
            }
        }
    }

    func stop() {
        poll?.cancel()
        if ChatNotifier.shared.openRoom == room.id { ChatNotifier.shared.openRoom = nil }
    }

    func hideMessages(from userId: String) { messages.removeAll { $0.userId == userId } }

    func fetch() async {
        var fields: [String: Any] = ["room": room.id]
        if let last = messages.last?.id { fields["after"] = last }
        do {
            let r = try await AccountStore.shared.call("chat", fields)
            let new = decode([ChatMessage].self, r["messages"]) ?? []
            let gone = Set((r["deleted"] as? [String]) ?? [])
            let edits = (r["edited"] as? [[String: Any]]) ?? []
            let blocked = Set(AccountStore.shared.user?.blocked ?? [])
            var list = messages.filter { !gone.contains($0.id) && !blocked.contains($0.userId) }
            for e in edits {
                if let id = e["id"] as? String, let i = list.firstIndex(where: { $0.id == id }) {
                    list[i].text = e["text"] as? String ?? list[i].text
                    list[i].edited = e["edited"] as? String
                }
            }
            let have = Set(list.map(\.id))
            list += new.filter { !have.contains($0.id) && !blocked.contains($0.userId) }
            if list != messages { withAnimation(.easeOut(duration: 0.2)) { messages = list } }
            if let p = decode([String: ChatPerson].self, r["people"]) { people.merge(p) { _, n in n } }
            if let rm = decode(ChatRoom.self, r["room"]), rm != room { room = rm }
            pinned = decode(ChatMessage.self, r["pinned"])
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    func send(_ text: String, replyTo: String?) async -> Bool {
        do {
            let r = try await AccountStore.shared.call("send", ["room": room.id, "text": text, "replyTo": replyTo ?? ""])
            if let m = decode(ChatMessage.self, r["message"]), !messages.contains(where: { $0.id == m.id }) {
                withAnimation { messages.append(m) }
            }
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    func edit(_ m: ChatMessage, to text: String) async -> Bool {
        do {
            _ = try await AccountStore.shared.call("editMessage", ["id": m.id, "text": text])
            if let i = messages.firstIndex(where: { $0.id == m.id }) {
                messages[i].text = text
                messages[i].edited = ISO8601DateFormatter.ab.string(from: Date())
            }
            return true
        } catch { self.error = error.localizedDescription; return false }
    }

    func delete(_ m: ChatMessage) async {
        do {
            _ = try await AccountStore.shared.call("deleteMessage", ["id": m.id])
            withAnimation { messages.removeAll { $0.id == m.id } }
        } catch { self.error = error.localizedDescription }
    }

    func pin(_ m: ChatMessage?) async {
        do {
            _ = try await AccountStore.shared.call("adminPin", ["room": room.id, "id": m?.id ?? ""])
            pinned = m
            room.pinned = m?.id ?? ""
        } catch { self.error = error.localizedDescription }
    }

    func ban(_ m: ChatMessage) async {
        do { _ = try await AccountStore.shared.call("adminBan", ["userId": m.userId, "banned": true, "reason": "Removed from the chat by an admin"]) }
        catch { self.error = error.localizedDescription }
    }
}

struct ChatRoomView: View {
    @StateObject private var model: RoomModel
    @ObservedObject private var account = AccountStore.shared
    @State private var text = ""
    @State private var replyTo: ChatMessage?
    @State private var editing: ChatMessage?
    @State private var sending = false
    @State private var banTarget: ChatMessage?
    @State private var reporting: ReportTarget?
    @State private var blocking: BlockTarget?
    @State private var profile: ChatMessage?
    @State private var roomSettings = false
    @FocusState private var focused: Bool

    init(room: ChatRoom) { _model = StateObject(wrappedValue: RoomModel(room: room)) }

    private var canPost: Bool {
        account.isAdmin || (model.room.adminOnly != true && model.room.locked != true && model.room.archived != true)
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 2) {
                    if model.loading && model.messages.isEmpty {
                        ProgressView().padding(40)
                    } else if model.messages.isEmpty {
                        VStack(spacing: 8) {
                            Text(model.room.emoji).font(.system(size: 44))
                            Text("No messages yet").font(.headline)
                            Text(canPost ? "Start the conversation!" : "News from the team will show up here.").foregroundStyle(.secondary)
                        }
                        .padding(.top, 60)
                    }
                    ForEach(Array(model.messages.enumerated()), id: \.element.id) { i, m in
                        let prev = i > 0 ? model.messages[i - 1] : nil
                        if prev == nil || !Calendar.current.isDate(prev!.date, inSameDayAs: m.date) {
                            Text(dayLabel(m.date)).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .background(Color(UIColor.secondarySystemBackground), in: Capsule())
                                .padding(.vertical, 8)
                        }
                        bubble(m, grouped: prev?.userId == m.userId && Calendar.current.isDate(prev!.date, inSameDayAs: m.date))
                            .id(m.id)
                    }
                    Color.clear.frame(height: 4).id("bottom")
                }
                .padding(.horizontal, 10)
            }
            .background(Color.abBackground)
            .dismissKeyboardOnScroll()
            .safeAreaInset(edge: .top) {
                if let p = model.pinned {
                    Button { withAnimation { proxy.scrollTo(p.id, anchor: .center) } } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "pin.fill").foregroundStyle(Color.abAccent)
                            VStack(alignment: .leading, spacing: 1) {
                                Text("Pinned message").font(.caption2.weight(.bold)).foregroundStyle(Color.abAccent)
                                Text(p.text).font(.caption).lineLimit(1).foregroundStyle(.primary)
                            }
                            Spacer()
                            if account.isAdmin {
                                Button { Task { await model.pin(nil) } } label: { Image(systemName: "xmark").foregroundStyle(.secondary) }
                            }
                        }
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.bar)
                    }
                    .buttonStyle(.plain)
                }
            }
            .onChange(of: model.messages.count) { _ in withAnimation { proxy.scrollTo("bottom", anchor: .bottom) } }
            .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { proxy.scrollTo("bottom", anchor: .bottom) } }
        }
        .safeAreaInset(edge: .bottom) { composer }
        .navigationTitle("\(model.room.emoji) \(model.room.name)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if account.isAdmin {
                    Button { roomSettings = true } label: { Image(systemName: "slider.horizontal.3") }.accessibilityLabel(Text("Edit room"))
                }
            }
        }
        .sheet(isPresented: $roomSettings) { RoomEditor(room: model.room) }
        .push(isPresented: Binding(get: { profile != nil }, set: { if !$0 { profile = nil } })) {
            if let m = profile { ProfileScreen(userId: m.userId, fallbackName: m.displayName, fallbackColor: m.color) }
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
        .onChange(of: account.rooms) { rs in if let r = rs.first(where: { $0.id == model.room.id }) { model.room = r } }
        .modifier(ModerationDialogs(report: $reporting, block: $blocking, blocked: { model.hideMessages(from: $0) }))
        .confirmationDialog("Ban @\(banTarget?.username ?? "")?", isPresented: Binding(get: { banTarget != nil }, set: { if !$0 { banTarget = nil } }), titleVisibility: .visible) {
            Button("Ban from ASU Bank", role: .destructive) { if let m = banTarget { Task { await model.ban(m) } } }
        } message: { Text("They're signed out and can't sign in again until an admin unbans them.") }
    }

    private func dayLabel(_ d: Date) -> String {
        if Calendar.current.isDateInToday(d) { return L("Today") }
        if Calendar.current.isDateInYesterday(d) { return L("Yesterday") }
        let f = DateFormatter(); f.setLocalizedDateFormatFromTemplate("EEEEdMMM")
        return f.string(from: d)
    }

    @ViewBuilder private func bubble(_ m: ChatMessage, grouped: Bool) -> some View {
        let mine = m.userId == account.user?.id
        let person = model.people[m.userId]
        let reply = m.replyTo.flatMap { id in model.messages.first { $0.id == id } }
        HStack(alignment: .bottom, spacing: 8) {
            if mine { Spacer(minLength: 50) } else {
                if grouped { Color.clear.frame(width: 30, height: 1) } else {
                    Button { profile = m } label: {
                        Avatar(name: m.displayName, color: m.color, size: 30, admin: m.role == "admin", photo: person?.avatar, online: person?.online)
                    }.buttonStyle(.plain)
                }
            }
            VStack(alignment: mine ? .trailing : .leading, spacing: 3) {
                if !mine && !grouped {
                    HStack(spacing: 4) {
                        Text(m.displayName).font(.caption.weight(.semibold)).foregroundStyle(Color(hexString: m.color))
                        if m.role == "admin" { Image(systemName: "checkmark.seal.fill").font(.caption2).foregroundStyle(Color.abGold) }
                        BadgeRow(badges: person?.badges ?? [], compact: true)
                    }
                }
                VStack(alignment: .leading, spacing: 4) {
                    if let reply {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(reply.displayName).font(.caption2.weight(.bold))
                            Text(reply.text).font(.caption).lineLimit(2)
                        }
                        .padding(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background((mine ? Color.white : Color.abAccent).opacity(0.15), in: RoundedRectangle(cornerRadius: 8))
                    }
                    Text(m.text).font(.body).foregroundStyle(mine ? .white : .primary).textSelection(.enabled)
                    HStack(spacing: 4) {
                        if model.pinned?.id == m.id { Image(systemName: "pin.fill") }
                        if m.edited?.isEmpty == false { Text("edited") }
                        Text(m.date, style: .time)
                    }
                    .font(.caption2).foregroundStyle(mine ? .white.opacity(0.7) : .secondary)
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(mine ? AnyShapeStyle(Color.abAccent) : AnyShapeStyle(Color(UIColor.secondarySystemGroupedBackground)),
                            in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .contextMenu {
                    if canPost { Button { editing = nil; replyTo = m; focused = true } label: { Label("Reply", systemImage: "arrowshape.turn.up.left") } }
                    Button { UIPasteboard.general.string = m.text } label: { Label("Copy", systemImage: "doc.on.doc") }
                    if mine {
                        Button { replyTo = nil; editing = m; text = m.text; focused = true } label: { Label("Edit", systemImage: "pencil") }
                    }
                    if !mine { Button { profile = m } label: { Label("View profile", systemImage: "person.crop.circle") } }
                    if !mine {
                        Button { reporting = ReportTarget(kind: "message", targetId: m.id, name: String(m.text.prefix(120))) } label: { Label("Report message", systemImage: "flag") }
                        if m.role != "admin" { Button(role: .destructive) { blocking = BlockTarget(userId: m.userId, name: m.displayName) } label: { Label("Block", systemImage: "nosign") } }
                    }
                    if account.isAdmin {
                        Button { Task { await model.pin(model.pinned?.id == m.id ? nil : m) } } label: {
                            Label(model.pinned?.id == m.id ? "Unpin" : "Pin", systemImage: model.pinned?.id == m.id ? "pin.slash" : "pin")
                        }
                    }
                    if mine || account.isAdmin {
                        Button(role: .destructive) { Task { await model.delete(m) } } label: { Label("Delete", systemImage: "trash") }
                    }
                    if account.isAdmin && !mine && m.role != "admin" {
                        Button(role: .destructive) { banTarget = m } label: { Label("Ban @\(m.username)", systemImage: "hand.raised.fill") }
                    }
                }
            }
            if !mine { Spacer(minLength: 50) }
        }
        .padding(.top, grouped ? 1 : 6)
    }

    @ViewBuilder private var composer: some View {
        VStack(spacing: 0) {
            if let e = model.error {
                Text(e).font(.caption).foregroundStyle(.white).padding(6).frame(maxWidth: .infinity).background(Color.red.opacity(0.85))
            }
            if canPost {
                if let r = replyTo ?? editing {
                    HStack {
                        Image(systemName: editing != nil ? "pencil" : "arrowshape.turn.up.left.fill").foregroundStyle(Color.abAccent)
                        VStack(alignment: .leading) {
                            Text(editing != nil ? L("Editing your message") : String(format: L("Replying to %@"), r.displayName)).font(.caption.weight(.semibold))
                            Text(r.text).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer()
                        Button { if editing != nil { text = "" }; replyTo = nil; editing = nil } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
                    }
                    .padding(.horizontal, 14).padding(.top, 8)
                }
                HStack(spacing: 8) {
                    TextField("Message", text: $text)
                        .focused($focused)
                        .padding(.horizontal, 14).padding(.vertical, 9)
                        .background(Color(UIColor.secondarySystemBackground), in: Capsule())
                        .submitLabel(.send)
                        .onSubmit(send)
                    Button(action: send) {
                        Image(systemName: sending ? "hourglass" : (editing != nil ? "checkmark.circle.fill" : "arrow.up.circle.fill"))
                            .font(.system(size: 32)).foregroundStyle(Color.abAccent)
                    }
                    .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty || sending)
                }
                .padding(.horizontal, 10).padding(.vertical, 8)
            } else {
                Label(model.room.archived == true ? L("This room is archived.") : model.room.locked == true ? L("This room is locked. Only admins can post.") : L("Only the ASU Bank team posts here."),
                      systemImage: model.room.locked == true || model.room.archived == true ? "lock.fill" : "megaphone")
                    .font(.footnote).foregroundStyle(.secondary).padding(12)
            }
        }
        .background(.bar)
    }

    private func send() {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, !sending else { return }
        sending = true
        let r = replyTo?.id
        let e = editing
        text = ""; replyTo = nil; editing = nil
        Task {
            let ok = e != nil ? await model.edit(e!, to: t) : await model.send(t, replyTo: r)
            if !ok { text = t; editing = e }
            sending = false
            Haptics.tap()
        }
    }
}
