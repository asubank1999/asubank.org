import SwiftUI

/// The Chat tab: rooms, or a sign-in prompt
struct ChatTab: View {
    @EnvironmentObject private var app: AppState
    @ObservedObject private var account = AccountStore.shared
    @State private var openRoom: ChatRoom?

    var body: some View {
        ABStack {
            Group {
                if account.signedIn { rooms } else { SignInPrompt() }
            }
            .navigationTitle("Chat")
            .push(isPresented: Binding(get: { openRoom != nil }, set: { if !$0 { openRoom = nil } })) {
                if let r = openRoom { ChatRoomView(room: r) }
            }
        }
        .onAppear(perform: takeRoom)
        .onChange(of: app.pendingRoom) { _ in takeRoom() }
    }

    private func takeRoom() {
        guard let id = app.pendingRoom, account.signedIn else { return }
        app.pendingRoom = nil
        openRoom = account.rooms.first { $0.id == id }
    }

    private var rooms: some View {
        List {
            Section {
                HStack(spacing: 12) {
                    if let u = account.user { Avatar(name: u.displayName, color: u.color, size: 44, admin: u.isAdmin) }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Hi, \(account.user?.displayName ?? "")").font(.headline)
                        Text("Be kind and help each other. Don't share personal info.").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    RouteLinkAccount()
                }
                .padding(.vertical, 2)
            }
            Section("Rooms") {
                ForEach(account.rooms) { r in
                    Button { openRoom = r } label: {
                        HStack(spacing: 12) {
                            Text(r.emoji).font(.title2).frame(width: 44, height: 44)
                                .background(Color.abAccentSoft, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(r.name).font(.body.weight(.semibold)).foregroundStyle(.primary)
                                    if r.adminOnly == true { Badge(text: "TEAM", color: .orange) }
                                }
                                Text(r.about).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .refreshable { await account.refresh() }
    }
}

private struct RouteLinkAccount: View {
    @State private var shown = false
    var body: some View {
        Button { shown = true } label: { Image(systemName: "person.crop.circle").font(.title2) }
            .sheet(isPresented: $shown) { ABStack { AccountScreen().toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { shown = false } } } } }
    }
}

// MARK: - room

@MainActor
final class RoomModel: ObservableObject {
    let room: ChatRoom
    @Published var messages: [ChatMessage] = []
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

    func fetch() async {
        var fields: [String: Any] = ["room": room.id]
        if let last = messages.last?.id { fields["after"] = last }
        do {
            let r = try await AccountStore.shared.call("chat", fields)
            let new = decode([ChatMessage].self, r["messages"]) ?? []
            let gone = Set((r["deleted"] as? [String]) ?? [])
            var list = messages.filter { !gone.contains($0.id) }
            let have = Set(list.map(\.id))
            list += new.filter { !have.contains($0.id) }
            if list != messages { withAnimation(.easeOut(duration: 0.2)) { messages = list } }
            if let l = decode(ChatLatest.self, r["latest"]) {
                UserDefaults.standard.set(max(UserDefaults.standard.string(forKey: "ab.notify.lastSeenDate") ?? "", l.created), forKey: "ab.notify.lastSeenDate")
            }
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

    func delete(_ m: ChatMessage) async {
        do {
            _ = try await AccountStore.shared.call("deleteMessage", ["id": m.id])
            withAnimation { messages.removeAll { $0.id == m.id } }
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
    @State private var sending = false
    @State private var banTarget: ChatMessage?
    @FocusState private var focused: Bool

    init(room: ChatRoom) { _model = StateObject(wrappedValue: RoomModel(room: room)) }

    private var canPost: Bool { model.room.adminOnly != true || account.isAdmin }

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
            .onChange(of: model.messages.count) { _ in withAnimation { proxy.scrollTo("bottom", anchor: .bottom) } }
            .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { proxy.scrollTo("bottom", anchor: .bottom) } }
        }
        .safeAreaInset(edge: .bottom) { composer }
        .navigationTitle("\(model.room.emoji) \(model.room.name)")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.start() }
        .onDisappear { model.stop() }
        .confirmationDialog("Ban @\(banTarget?.username ?? "")?", isPresented: Binding(get: { banTarget != nil }, set: { if !$0 { banTarget = nil } }), titleVisibility: .visible) {
            Button("Ban from ASU Bank", role: .destructive) { if let m = banTarget { Task { await model.ban(m) } } }
        } message: { Text("They're signed out and can't sign in again until an admin unbans them.") }
    }

    private func dayLabel(_ d: Date) -> String {
        if Calendar.current.isDateInToday(d) { return "Today" }
        if Calendar.current.isDateInYesterday(d) { return "Yesterday" }
        let f = DateFormatter(); f.dateFormat = "EEEE, d MMM"
        return f.string(from: d)
    }

    @ViewBuilder private func bubble(_ m: ChatMessage, grouped: Bool) -> some View {
        let mine = m.userId == account.user?.id
        let reply = m.replyTo.flatMap { id in model.messages.first { $0.id == id } }
        HStack(alignment: .bottom, spacing: 8) {
            if mine { Spacer(minLength: 50) } else {
                if grouped { Color.clear.frame(width: 30, height: 1) } else { Avatar(name: m.displayName, color: m.color, size: 30, admin: m.role == "admin") }
            }
            VStack(alignment: mine ? .trailing : .leading, spacing: 3) {
                if !mine && !grouped {
                    HStack(spacing: 4) {
                        Text(m.displayName).font(.caption.weight(.semibold)).foregroundStyle(Color(hexString: m.color))
                        if m.role == "admin" { Image(systemName: "checkmark.seal.fill").font(.caption2).foregroundStyle(Color.abGold) }
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
                    Text(m.date, style: .time).font(.caption2).foregroundStyle(mine ? .white.opacity(0.7) : .secondary)
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(mine ? AnyShapeStyle(Color.abAccent) : AnyShapeStyle(Color(UIColor.secondarySystemGroupedBackground)),
                            in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .contextMenu {
                    if canPost { Button { replyTo = m; focused = true } label: { Label("Reply", systemImage: "arrowshape.turn.up.left") } }
                    Button { UIPasteboard.general.string = m.text } label: { Label("Copy", systemImage: "doc.on.doc") }
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
                if let r = replyTo {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left.fill").foregroundStyle(Color.abAccent)
                        VStack(alignment: .leading) {
                            Text("Replying to \(r.displayName)").font(.caption.weight(.semibold))
                            Text(r.text).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer()
                        Button { replyTo = nil } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
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
                        Image(systemName: sending ? "hourglass" : "arrow.up.circle.fill").font(.system(size: 32)).foregroundStyle(Color.abAccent)
                    }
                    .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty || sending)
                }
                .padding(.horizontal, 10).padding(.vertical, 8)
            } else {
                Text("Only the ASU Bank team posts here.").font(.footnote).foregroundStyle(.secondary).padding(12)
            }
        }
        .background(.bar)
    }

    private func send() {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, !sending else { return }
        sending = true
        let r = replyTo?.id
        text = ""; replyTo = nil
        Task {
            if !(await model.send(t, replyTo: r)) { text = t }
            sending = false
            Haptics.tap()
        }
    }
}
