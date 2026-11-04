import SwiftUI

// Friends, pokes and posts. Everything lives in the accounts API, so both apps share it.

struct Poke: Decodable, Identifiable {
    let id: String
    let kind: String
    var place: String?
    var created: String?
    var read: Bool?
    let user: ABUser
}

struct FeedPost: Decodable, Identifiable {
    let id: String
    var text: String
    var created: String?
    var likes: Int
    var liked: Bool
    let user: ABUser
}

enum Social {
    static let pokeKinds: [(id: String, label: String, emoji: String)] = [
        ("poke", "Poke", "👉"),
        ("meet", "Ask to meet", "📍"),
        ("study", "Study together", "📚"),
        ("question", "I have a question", "❓"),
        ("notes", "I need your notes", "📝"),
        ("coffee", "Coffee?", "☕")
    ]

    static func pokeText(kind: String, name: String, place: String?) -> String {
        let where_ = (place ?? "").trimmingCharacters(in: .whitespaces)
        switch kind {
        case "meet":
            return where_.isEmpty ? String(format: L("%@ wants to meet you"), name) : String(format: L("%@ wants to meet you at %@"), name, where_)
        case "study": return String(format: L("%@ wants to study with you"), name)
        case "question": return String(format: L("%@ has a question for you"), name)
        case "notes": return String(format: L("%@ needs your notes"), name)
        case "coffee": return String(format: L("%@ wants to get coffee with you"), name)
        default: return String(format: L("%@ poked you"), name)
        }
    }

    static func ago(_ iso: String?) -> String {
        guard let iso, let d = ISO8601DateFormatter.ab.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else { return "" }
        let f = RelativeDateTimeFormatter()
        f.unitsStyle = .short
        return f.localizedString(for: d, relativeTo: Date())
    }
}

/// Add friend / Requested / Friends, for a person's row or profile
struct FriendButton: View {
    let userId: String
    @Binding var state: String
    var changed: () async -> Void = {}
    @ObservedObject private var account = AccountStore.shared
    @State private var busy = false
    @State private var confirmRemove = false
    @State private var error: String?

    var body: some View {
        Group {
            switch state {
            case "friends":
                button("Friends", "person.fill.checkmark", filled: false) { confirmRemove = true }
            case "outgoing":
                button("Requested", "clock", filled: false) { run("friendRemove", [:]) }
            case "incoming":
                button("Accept", "person.fill.checkmark", filled: true) { run("friendRespond", ["accept": true]) }
            default:
                button("Add friend", "person.badge.plus", filled: true) { run("friendRequest", [:]) }
            }
        }
        .disabled(busy)
        .confirmationDialog("Remove this friend?", isPresented: $confirmRemove, titleVisibility: .visible) {
            Button("Remove friend", role: .destructive) { run("friendRemove", [:]) }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Couldn't do that", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(error ?? "") }
    }

    private func button(_ title: String, _ icon: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(L(title), systemImage: icon)
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 12).padding(.vertical, 8)
                .foregroundStyle(filled ? Color.white : Color.primary)
                .background(filled ? AnyShapeStyle(Color.abAccent) : AnyShapeStyle(Color.primary.opacity(0.09)), in: Capsule())
        }
        .buttonStyle(.borderless)
    }

    private func run(_ action: String, _ extra: [String: Any]) {
        Task {
            busy = true
            defer { busy = false }
            var fields = extra
            fields["userId"] = userId
            do {
                let r = try await account.call(action, fields)
                state = r["friendship"] as? String ?? "none"
                Haptics.success()
                await changed()
            } catch { self.error = error.localizedDescription; Haptics.error() }
        }
    }
}

/// "Poke" and the other short messages friends can send each other
struct PokeSheet: View {
    let user: ABUser
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    @State private var kind = "poke"
    @State private var place = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ABStack {
            Form {
                Section {
                    ForEach(Social.pokeKinds, id: \.id) { k in
                        Button { kind = k.id } label: {
                            HStack {
                                Text(k.emoji)
                                Text(L(k.label)).foregroundColor(.primary)
                                Spacer()
                                if kind == k.id { Image(systemName: "checkmark").foregroundStyle(Color.abAccent) }
                            }
                        }
                    }
                } header: { Text("What do you want to say?") }
                if kind == "meet" {
                    Section {
                        TextField("Where and when? Like: the library at 2:00", text: $place)
                    } header: { Text("Where") }
                }
                Section {
                    Text(Social.pokeText(kind: kind, name: account.user?.displayName ?? "", place: place)).foregroundStyle(.secondary)
                } header: { Text("They will see") }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle(user.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Sending…" : "Send") { Task { await send() } }.disabled(busy)
                }
            }
        }
    }

    private func send() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await account.call("poke", ["userId": user.id, "kind": kind, "place": kind == "meet" ? place : ""])
            Haptics.success()
            dismiss()
        } catch { self.error = error.localizedDescription; Haptics.error() }
    }
}

struct ComposePostSheet: View {
    var posted: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    @State private var text = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ABStack {
            Form {
                Section {
                    TextEditor(text: $text).frame(minHeight: 140)
                } header: { Text("What's on your mind?") } footer: { Text("\(text.count)/1000") }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle("New post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Posting…" : "Post") { Task { await post() } }
                        .disabled(busy || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func post() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await account.call("postCreate", ["text": String(text.prefix(1000))])
            Haptics.success()
            await posted()
            dismiss()
        } catch { self.error = error.localizedDescription; Haptics.error() }
    }
}

struct PostRow: View {
    let post: FeedPost
    var changed: () async -> Void
    @ObservedObject private var account = AccountStore.shared
    @State private var liked: Bool?
    @State private var likes: Int?
    @State private var reporting: ReportTarget?
    @State private var blocking: BlockTarget?

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 10) {
                NavigationLink { ProfileScreen(userId: post.user.id, fallbackName: post.user.displayName, fallbackColor: post.user.color) } label: {
                    HStack(spacing: 10) {
                        Avatar(name: post.user.displayName, color: post.user.color, size: 38, admin: post.user.isAdmin, photo: post.user.avatar, online: post.user.online)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(post.user.displayName).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                            Text(Social.ago(post.created)).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .buttonStyle(.plain)
                Spacer()
                Menu {
                    if post.user.id == account.user?.id || account.isAdmin {
                        Button("Delete", role: .destructive) {
                            Task {
                                _ = try? await account.call("postDelete", ["id": post.id])
                                await changed()
                            }
                        }
                    }
                    if post.user.id != account.user?.id {
                        Button { reporting = ReportTarget(kind: "post", targetId: post.id, name: post.text) } label: { Label("Report", systemImage: "flag") }
                        if !post.user.isAdmin {
                            Button(role: .destructive) { blocking = BlockTarget(userId: post.user.id, name: post.user.displayName) } label: { Label("Block", systemImage: "nosign") }
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis").foregroundStyle(.secondary).frame(width: 32, height: 32)
                }
                .buttonStyle(.borderless)
            }
            Text(post.text).fixedSize(horizontal: false, vertical: true)
            Button { Task { await like() } } label: {
                Label("\(likes ?? post.likes)", systemImage: (liked ?? post.liked) ? "heart.fill" : "heart")
                    .font(.subheadline)
                    .foregroundStyle((liked ?? post.liked) ? Color.red : Color.secondary)
            }
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 4)
        .modifier(ModerationDialogs(report: $reporting, block: $blocking, blocked: { _ in Task { await changed() } }))
    }

    private func like() async {
        let on = !(liked ?? post.liked)
        Haptics.tap()
        liked = on
        likes = (likes ?? post.likes) + (on ? 1 : -1)
        if let r = try? await account.call("postLike", ["id": post.id, "on": on]) {
            likes = (r["likes"] as? NSNumber)?.intValue ?? likes
        }
    }
}

struct FriendsTab: View {
    var body: some View { ABStack { FriendsScreen() } }
}

/// The Friends tab: posts from friends, friends, requests and pokes, and finding people
struct FriendsScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var notifier = ChatNotifier.shared
    @State private var segment = 0
    @State private var friends: [ABUser] = []
    @State private var incoming: [ABUser] = []
    @State private var outgoing: [ABUser] = []
    @State private var pokes: [Poke] = []
    @State private var feed: [FeedPost] = []
    @State private var people: [ABUser] = []
    @State private var query = ""
    @State private var loading = true
    @State private var error: String?
    @State private var poking: ABUser?
    @State private var composing = false

    private var alerts: Int { incoming.count + pokes.filter { $0.read != true }.count }

    var body: some View {
        Group {
            if account.signedIn {
                List {
                    Picker("", selection: $segment) {
                        Text("Feed").tag(0)
                        Text("Friends").tag(1)
                        Text(alerts > 0 ? "\(L("Alerts")) (\(alerts))" : L("Alerts")).tag(2)
                        Text("Find").tag(3)
                    }
                    .pickerStyle(.segmented)
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                    if let error { Text(error).foregroundStyle(.red) }
                    switch segment {
                    case 0: feedSection
                    case 1: friendsSection
                    case 2: alertsSection
                    default: findSection
                    }
                }
                .listStyle(.insetGrouped)
                .refreshable { await load() }
                .task { await load() }
                .onChange(of: segment) { s in
                    if s == 2 { Task { await markRead() } }
                    if s == 3 && people.isEmpty { Task { await search() } }
                }
                .modifier(CommunityRulesGate())
            } else {
                VStack(spacing: 14) {
                    Image(systemName: "person.2.fill").font(.system(size: 46)).foregroundStyle(Color.abAccent)
                    Text("Add your classmates as friends").font(.headline)
                    Text("Sign in to add friends, poke them and share posts.").multilineTextAlignment(.center).foregroundStyle(.secondary)
                    Button("Sign in") { account.signInShown = true }.buttonStyle(.borderedProminent).tint(.abAccent)
                }
                .padding(32)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.abBackground)
            }
        }
        .navigationTitle("Friends")
        .onAppear { notifier.socialUnread = 0 }
        .sheet(item: $poking) { PokeSheet(user: $0) }
        .sheet(isPresented: $composing) { ComposePostSheet { await loadFeed() } }
    }

    // MARK: feed

    @ViewBuilder private var feedSection: some View {
        Section {
            Button { composing = true } label: { Label("Write a post", systemImage: "square.and.pencil") }
        }
        if feed.isEmpty && !loading {
            Section { Text("No posts yet. Add friends or write the first one.").foregroundStyle(.secondary) }
        }
        ForEach(feed) { p in
            Section { PostRow(post: p) { await loadFeed() } }
        }
    }

    // MARK: friends

    @ViewBuilder private var friendsSection: some View {
        if loading { ProgressView() }
        else if friends.isEmpty {
            Section {
                Text("You haven't added any friends yet.").foregroundStyle(.secondary)
                Button("Find people") { segment = 3 }
            }
        } else {
            Section {
                ForEach(friends) { u in
                    person(u) {
                        Button { poking = u } label: {
                            Text("👉").font(.title3).frame(width: 38, height: 38).background(Color.abAccentSoft, in: Circle())
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(L("Poke"))
                    }
                }
            } header: { Text(String(format: L("%d friends"), friends.count)) }
        }
    }

    // MARK: requests and pokes

    @ViewBuilder private var alertsSection: some View {
        if incoming.isEmpty && pokes.isEmpty && outgoing.isEmpty && !loading {
            Section { Text("Nothing new.").foregroundStyle(.secondary) }
        }
        if !incoming.isEmpty {
            Section("Friend requests") {
                ForEach(incoming) { u in
                    person(u) {
                        HStack(spacing: 8) {
                            Button { Task { await respond(u, true) } } label: {
                                Image(systemName: "checkmark").foregroundStyle(.white).frame(width: 34, height: 34).background(Color.abAccent, in: Circle())
                            }
                            .accessibilityLabel(L("Accept"))
                            Button { Task { await respond(u, false) } } label: {
                                Image(systemName: "xmark").foregroundStyle(.primary).frame(width: 34, height: 34).background(Color.primary.opacity(0.09), in: Circle())
                            }
                            .accessibilityLabel(L("Decline"))
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }
        }
        if !pokes.isEmpty {
            Section("Pokes and messages") {
                ForEach(pokes) { p in
                    HStack(spacing: 10) {
                        Avatar(name: p.user.displayName, color: p.user.color, size: 38, photo: p.user.avatar)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(Social.pokeText(kind: p.kind, name: p.user.displayName, place: p.place))
                                .font(.subheadline.weight(p.read == true ? .regular : .semibold))
                            Text(Social.ago(p.created)).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(L("Reply")) { poking = p.user }.buttonStyle(.borderless).font(.subheadline)
                    }
                }
            }
        }
        if !outgoing.isEmpty {
            Section("Requests you sent") {
                ForEach(outgoing) { u in
                    person(u) { FriendButton(userId: u.id, state: .constant("outgoing")) { await load() } }
                }
            }
        }
    }

    // MARK: finding people

    @ViewBuilder private var findSection: some View {
        Section {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Name, username, major or college", text: $query)
                    .autocapitalization(.none).disableAutocorrection(true)
                    .submitLabel(.search)
                    .onSubmit { Task { await search() } }
            }
        } footer: {
            if query.isEmpty { Text("People from your college and major show up here. Add them to your profile to see more.") }
        }
        if people.isEmpty && !loading {
            Section { Text("No people found.").foregroundStyle(.secondary) }
        }
        ForEach(people.indices, id: \.self) { i in
            person(people[i]) {
                FriendButton(userId: people[i].id, state: Binding(get: { people.indices.contains(i) ? people[i].friendship ?? "none" : "none" },
                                                                  set: { if people.indices.contains(i) { people[i].friendship = $0 } }))
            }
        }
    }

    private func person<T: View>(_ u: ABUser, @ViewBuilder trailing: () -> T) -> some View {
        HStack(spacing: 10) {
            NavigationLink { ProfileScreen(userId: u.id, fallbackName: u.displayName, fallbackColor: u.color) } label: {
                HStack(spacing: 10) {
                    Avatar(name: u.displayName, color: u.color, size: 42, admin: u.isAdmin, photo: u.avatar, online: u.online)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(u.displayName).font(.body.weight(.medium)).lineLimit(1)
                        Text(subtitle(u)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
            }
            Spacer(minLength: 4)
            trailing()
        }
    }

    private func subtitle(_ u: ABUser) -> String {
        if let m = u.major, !m.isEmpty { return m }
        if let c = u.college, !c.isEmpty { return c }
        return "@\(u.username)"
    }

    // MARK: data

    private func load() async {
        guard account.signedIn else { return }
        do {
            let r = try await account.call("friends")
            friends = decode([ABUser].self, r["friends"]) ?? []
            incoming = decode([ABUser].self, r["incoming"]) ?? []
            outgoing = decode([ABUser].self, r["outgoing"]) ?? []
            pokes = decode([Poke].self, r["pokes"]) ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
        await loadFeed()
        loading = false
        if segment == 2 { await markRead() }
    }

    private func loadFeed() async {
        if let r = try? await account.call("feed") { feed = decode([FeedPost].self, r["posts"]) ?? [] }
    }

    private func search() async {
        do {
            let r = try await account.call("searchUsers", ["q": query.trimmingCharacters(in: .whitespaces)])
            people = decode([ABUser].self, r["people"]) ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    private func respond(_ u: ABUser, _ accept: Bool) async {
        do {
            _ = try await account.call("friendRespond", ["userId": u.id, "accept": accept])
            Haptics.success()
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func markRead() async {
        guard pokes.contains(where: { $0.read != true }) else { return }
        _ = try? await account.call("pokesRead")
    }
}

/// A person's page: photo, counts, friend and poke buttons, studies and posts
struct ProfileScreen: View {
    let userId: String
    var fallbackName = ""
    var fallbackColor = "#8C1D40"
    @ObservedObject private var account = AccountStore.shared
    @State private var user: ABUser?
    @State private var friendship = "none"
    @State private var friendCount = 0
    @State private var postCount = 0
    @State private var messageCount = 0
    @State private var posts: [FeedPost] = []
    @State private var error: String?
    @State private var reporting: ReportTarget?
    @State private var blocking: BlockTarget?
    @State private var poking: ABUser?
    @State private var composing = false
    @State private var editing = false

    private var isMe: Bool { userId == account.user?.id }

    var body: some View {
        List {
            Section { header }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            if let u = user {
                if let b = u.bio, !b.isEmpty { Section("About") { Text(b) } }
                if u.major?.isEmpty == false || u.college?.isEmpty == false || u.year?.isEmpty == false {
                    Section("Studies") {
                        if let c = u.college, !c.isEmpty { Label(c, systemImage: "building.columns") }
                        if let m = u.major, !m.isEmpty { Label(m, systemImage: "graduationcap") }
                        if let y = u.year, !y.isEmpty { Label(L(y), systemImage: "calendar") }
                    }
                }
                if let i = u.interests, !i.isEmpty { Section("Interests") { Label(i, systemImage: "sparkles") } }
                if let p = u.phone, !p.isEmpty {
                    Section("Phone") {
                        Button { if let url = URL(string: "tel:" + p.filter { $0.isNumber || $0 == "+" }) { UIApplication.shared.open(url) } } label: {
                            Label(p, systemImage: "phone.fill")
                        }
                        Button { if let url = URL(string: "https://wa.me/" + p.filter(\.isNumber)) { UIApplication.shared.open(url) } } label: {
                            Label("WhatsApp", systemImage: "message.fill")
                        }
                    }
                }
                if isMe {
                    Section { Button { composing = true } label: { Label("Write a post", systemImage: "square.and.pencil") } }
                }
                ForEach(posts) { p in
                    Section { PostRow(post: p) { await load() } }
                }
                if account.signedIn, !isMe {
                    Section {
                        Button { reporting = ReportTarget(kind: "user", targetId: u.id, name: u.displayName) } label: { Label("Report this person", systemImage: "flag") }
                        if !u.isAdmin {
                            if account.user?.blocked?.contains(u.id) == true {
                                Button { Task { try? await Moderation.setBlocked(u.id, false) } } label: { Label("Unblock", systemImage: "checkmark.circle") }
                            } else {
                                Button(role: .destructive) { blocking = BlockTarget(userId: u.id, name: u.displayName) } label: { Label("Block", systemImage: "nosign") }
                            }
                        }
                    }
                }
                if account.isAdmin, !isMe {
                    Section { NavigationLink { AdminUserScreen(user: u) { await load() } } label: { Label("Manage this user", systemImage: "checkmark.shield") } }
                }
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }
        .listStyle(.insetGrouped)
        .modifier(ModerationDialogs(report: $reporting, block: $blocking, blocked: { _ in Task { await load() } }))
        .navigationTitle(user?.displayName ?? fallbackName)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .sheet(item: $poking) { PokeSheet(user: $0) }
        .sheet(isPresented: $composing) { ComposePostSheet { await load() } }
        .sheet(isPresented: $editing, onDismiss: { Task { await load() } }) { EditProfileSheet() }
    }

    private var header: some View {
        let color = Color(hexString: user?.color ?? fallbackColor)
        return VStack(spacing: 8) {
            ZStack(alignment: .bottom) {
                LinearGradient(colors: [color, Color.abAccent.opacity(0.75)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    .frame(height: 118)
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .padding(.bottom, 52)
                Avatar(name: user?.displayName ?? fallbackName, color: user?.color ?? fallbackColor, size: 100,
                       admin: user?.isAdmin == true, photo: user?.avatar, online: user?.online)
                    .overlay(Circle().stroke(Color.abBackground, lineWidth: 4))
            }
            Text(user?.displayName ?? fallbackName).font(.title2.weight(.bold))
            if let u = user {
                Text("@\(u.username)").foregroundStyle(.secondary)
                Text(u.online == true ? L("Online now") : lastSeenText(u.lastSeen)).font(.caption)
                    .foregroundStyle(u.online == true ? Color.green : .secondary)
                BadgeRow(badges: u.badges ?? [])
                if u.isAdmin { Badge(text: L("ADMIN"), color: .orange) }
                HStack(spacing: 0) {
                    stat(friendCount, "Friends")
                    stat(postCount, "Posts")
                    stat(messageCount, "Messages")
                    stat(u.badges?.count ?? 0, "Badges")
                }
                .padding(.vertical, 10)
                .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .padding(.top, 4)
                if isMe {
                    Button { editing = true } label: {
                        Label("Edit profile", systemImage: "pencil")
                            .font(.subheadline.weight(.semibold)).padding(.horizontal, 16).padding(.vertical, 8)
                            .foregroundStyle(Color.primary).background(Color.primary.opacity(0.09), in: Capsule())
                    }
                    .buttonStyle(.borderless)
                } else if account.signedIn, account.user?.blocked?.contains(u.id) != true {
                    HStack(spacing: 10) {
                        FriendButton(userId: u.id, state: $friendship) { await load() }
                        if friendship == "friends" {
                            Button { poking = u } label: {
                                Label("Poke", systemImage: "hand.point.right.fill")
                                    .font(.subheadline.weight(.semibold)).padding(.horizontal, 12).padding(.vertical, 8)
                                    .foregroundStyle(.white).background(Color.abAccent, in: Capsule())
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, 4)
    }

    private func stat(_ n: Int, _ title: String) -> some View {
        VStack(spacing: 2) {
            Text("\(n)").font(.headline).monospacedDigit()
            Text(L(title)).font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
    }

    private func load() async {
        do {
            let r = try await account.call("profile", ["userId": userId, "social": true])
            user = decode(ABUser.self, r["user"])
            friendship = r["friendship"] as? String ?? "none"
            friendCount = (r["friendCount"] as? NSNumber)?.intValue ?? 0
            postCount = (r["postCount"] as? NSNumber)?.intValue ?? 0
            messageCount = (r["messageCount"] as? NSNumber)?.intValue ?? 0
            posts = decode([FeedPost].self, r["posts"]) ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
    }
}
