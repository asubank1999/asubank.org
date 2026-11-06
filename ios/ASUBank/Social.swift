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
    var audience: String?
    let id: String
    var text: String
    var image: String?
    var comments: Int?
    var created: String?
    var likes: Int
    var liked: Bool
    let user: ABUser
    var video: String?
    var hasVoice: Bool?
    var duration: Int?
    var expires: String?
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

    static func date(_ iso: String) -> Date { ISO8601DateFormatter.ab.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) ?? Date() }

    static func audienceSymbol(_ a: String?) -> String {
        switch a {
        case "public": return "globe"
        case "friends": return "person.2.fill"
        default: return "graduationcap.fill"
        }
    }

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
    @State private var pop = false
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
        .animation(.spring(response: 0.35, dampingFraction: 0.6), value: state)
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
                .background(filled ? AnyShapeStyle(Color.abFill) : AnyShapeStyle(Color.primary.opacity(0.09)), in: Capsule())
                .scaleEffect(pop ? 1.12 : 1)
        }
        .buttonStyle(.borderless)
        .transition(.scale(scale: 0.7).combined(with: .opacity))
    }

    /// The button changes straight away with a little bounce; only a failure puts it back
    private func run(_ action: String, _ extra: [String: Any]) {
        let before = state
        switch action {
        case "friendRequest": state = "outgoing"
        case "friendRespond": state = "friends"
        default: state = "none"
        }
        Haptics.success()
        withAnimation(.spring(response: 0.25, dampingFraction: 0.4)) { pop = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) { withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { pop = false } }
        Task {
            var fields = extra
            fields["userId"] = userId
            do {
                let r = try await account.call(action, fields)
                state = r["friendship"] as? String ?? state
                await changed()
            } catch {
                state = before
                self.error = error.localizedDescription
                Haptics.error()
            }
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
    @State private var text = ""
    @State private var photo: UIImage?
    @State private var video: URL?
    @State private var videoCamera = false
    @State private var poster: UIImage?
    @State private var voice: (url: URL, seconds: Int)?
    @State private var picking = false
    @State private var disappear = 0
    @State private var error: String?
    @StateObject private var rec = VoiceRecorder()
    @AppStorage("ab.audience") private var audience = "public"

    private var empty: Bool { photo == nil && video == nil && voice == nil && text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        ABStack {
            Form {
                Section {
                    TextEditor(text: $text).frame(minHeight: 120)
                } header: { Text("What's on your mind?") } footer: { Text("\(text.count)/1000") }
                Section {
                    if let shown = photo ?? poster {
                        Color.clear.frame(height: 220)
                            .overlay(Image(uiImage: shown).resizable().scaledToFill())
                            .overlay { if video != nil { Image(systemName: "play.circle.fill").font(.system(size: 48)).foregroundStyle(.white) } }
                            .clipped()
                            .listRowInsets(EdgeInsets())
                        Button(role: .destructive) { photo = nil; video = nil; poster = nil } label: { Label(video != nil ? "Remove video" : "Remove photo", systemImage: "trash") }
                    }
                    Button { picking = true } label: { Label(photo == nil && video == nil ? "Add a photo or video" : "Change", systemImage: "photo.on.rectangle") }
                    if rec.recording {
                        HStack {
                            Circle().fill(.red).frame(width: 10, height: 10)
                            Text(VoiceBubble.time(rec.seconds)).monospacedDigit()
                            Spacer()
                            Button("Cancel", role: .destructive) { rec.cancel() }.buttonStyle(.borderless)
                            Button("Done") { if let f = rec.stop() { voice = (f.0, f.1) } }.buttonStyle(.borderless)
                        }
                    } else if let voice {
                        VoiceBubble(id: voice.url.lastPathComponent, duration: voice.seconds, localFile: voice.url) { voice.url }
                        Button(role: .destructive) { self.voice = nil } label: { Label("Remove voice message", systemImage: "trash") }
                    } else {
                        Button {
                            Task { if !(await rec.start()) { error = L("UniHub can't use the microphone. You can allow it in Settings.") } }
                        } label: { Label("Record a voice message", systemImage: "mic") }
                    }
                }
                Section {
                    AudiencePicker(audience: $audience)
                    Picker(selection: $disappear) {
                        ForEach(Disappear.choices, id: \.seconds) { Text(LocalizedStringKey($0.label)).tag($0.seconds) }
                    } label: { Label("Disappears after", systemImage: "timer") }
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle("New post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { rec.cancel(); dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Post") { post() }.disabled(empty || rec.recording)
                }
            }
            .sheet(isPresented: $picking) {
                StoryMediaPicker { m in
                    switch m {
                    case .photo(let image): photo = image; video = nil; poster = nil
                    case .video(let url, let still, let camera): video = url; poster = still; videoCamera = camera; photo = nil
                    }
                }
                .ignoresSafeArea()
            }
        }
    }

    /// The sheet closes straight away; the upload carries on in the background
    private func post() {
        let words = String(text.prefix(1000))
        let who = audience
        let time = disappear
        let shot = photo
        let clip = video
        let camera = videoCamera
        let sound = voice
        BackgroundJobs.shared.add(title: L("Publishing your post…"), done: posted) {
            var fields: [String: Any] = ["text": words, "audience": who, "expiresIn": time]
            if let shot {
                let data = await Task.detached(priority: .userInitiated) { PhotoTools.jpegBase64(shot, maxSide: 1280) }.value
                guard let data else { throw AccountsAPI.Failure(message: L("Couldn't load this photo")) }
                fields["image"] = data
            }
            if let clip {
                guard let ready = await StoryVideo.prepare(clip, fromCamera: camera), let bytes = try? Data(contentsOf: ready) else {
                    throw AccountsAPI.Failure(message: L("That video is too big. Keep it short."))
                }
                fields["video"] = bytes.base64EncodedString()
            }
            if let sound {
                guard let bytes = try? Data(contentsOf: sound.url) else { throw AccountsAPI.Failure(message: L("Couldn't send this file.")) }
                fields["file"] = bytes.base64EncodedString()
                fields["fileName"] = "voice.m4a"
                fields["duration"] = sound.seconds
            }
            _ = try await AccountStore.shared.call("postCreate", fields)
        }
        dismiss()
    }
}

struct PostRow: View {
    let post: FeedPost
    var changed: () async -> Void
    @ObservedObject private var account = AccountStore.shared
    @State private var liked: Bool?
    @State private var likes: Int?
    @State private var commentCount: Int?
    @State private var showComments = false
    @State private var reporting: ReportTarget?
    @State private var blocking: BlockTarget?
    @State private var preview: PreviewFile?
    @State private var gone = false
    @State private var confirmDelete = false

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 10) {
                ProfileEntry(post: post) {
                    HStack(spacing: 10) {
                        Avatar(name: post.user.displayName, color: post.user.color, size: 38, admin: post.user.isAdmin, photo: post.user.avatar, online: post.user.online)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(post.user.displayName).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                            HStack(spacing: 4) {
                                Text(Social.ago(post.created))
                                Image(systemName: Social.audienceSymbol(post.audience))
                                ExpiryTag(expires: post.expires)
                            }
                            .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                Spacer()
                Menu {
                    actions
                } label: {
                    Image(systemName: "ellipsis").foregroundStyle(.secondary).frame(width: 32, height: 32)
                }
                .buttonStyle(.borderless)
            }
            if !post.text.isEmpty { Text(post.text).fixedSize(horizontal: false, vertical: true) }
            if let image = post.image, !image.isEmpty, let url = URL(string: image) {
                Button { PhotoTools.show(image) } label: {
                    Color.abSurface.frame(height: 280)
                        .overlay(CachedPhoto(url: url, pixelSize: 420) { ABDots() })
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text("View photo"))
            }
            if let video = post.video, !video.isEmpty, let url = URL(string: video) {
                ChatMediaTile(type: "video", fetch: {
                    guard let f = await StoryVideo.local(url) else { throw AccountsAPI.Failure(message: L("File not found.")) }
                    return f
                }, open: { preview = PreviewFile(url: $0) })
            }
            if post.hasVoice == true {
                VoiceBubble(id: post.id, duration: post.duration ?? 0) {
                    try await MediaFiles.local(scope: "post", id: post.id, name: "voice.m4a")
                }
            }
            HStack(spacing: 18) {
                Button { Task { await like() } } label: {
                    Label("\(likes ?? post.likes)", systemImage: (liked ?? post.liked) ? "heart.fill" : "heart")
                        .font(.subheadline)
                        .foregroundStyle((liked ?? post.liked) ? Color.red : Color.secondary)
                }
                .buttonStyle(.borderless)
                Button { if account.signedIn { showComments = true } else { account.signInShown = true } } label: {
                    Label("\(commentCount ?? post.comments ?? 0)", systemImage: "bubble.left")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text("Comments"))
            }
        }
        .padding(.vertical, 4)
        .opacity(gone ? 0.25 : 1)
        // hold a post for the same options as the ... button
        .contextMenu { actions }
        .confirmationDialog("Delete this post?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { remove() }
            Button("Cancel", role: .cancel) {}
        } message: { Text("This can't be undone.") }
        .sheet(item: $preview) { FilePreview(url: $0.url).ignoresSafeArea() }
        .onChange(of: post.likes) { _ in likes = nil; liked = nil }
        .onChange(of: post.comments) { _ in commentCount = nil }
        .sheet(isPresented: $showComments) { CommentsSheet(post: post) { commentCount = $0 } }
        .modifier(ModerationDialogs(report: $reporting, block: $blocking, blocked: { _ in Task { await changed() } }))
    }

    @ViewBuilder private var actions: some View {
        if !post.text.isEmpty {
            Button { UIPasteboard.general.string = post.text; Haptics.tap() } label: { Label("Copy", systemImage: "doc.on.doc") }
        }
        if post.user.id == account.user?.id || account.isAdmin {
            Button(role: .destructive) { later { confirmDelete = true } } label: { Label("Delete", systemImage: "trash") }
        }
        if post.user.id != account.user?.id {
            Button { later { reporting = ReportTarget(kind: "post", targetId: post.id, name: post.text) } } label: { Label("Report", systemImage: "flag") }
            if account.signedIn, !post.user.isAdmin {
                Button(role: .destructive) { later { blocking = BlockTarget(userId: post.user.id, name: post.user.displayName) } } label: { Label("Block", systemImage: "hand.raised") }
            }
        }
    }

    /// A sheet asked for while a hold menu is closing doesn't open
    private func later(_ action: @escaping () -> Void) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: action)
    }

    private func remove() {
        withAnimation { gone = true }
        Task {
            _ = try? await account.call("postDelete", ["id": post.id])
            await changed()
        }
    }

    private func like() async {
        guard account.signedIn else { account.signInShown = true; return }
        let on = !(liked ?? post.liked)
        Haptics.tap()
        liked = on
        likes = (likes ?? post.likes) + (on ? 1 : -1)
        if let r = try? await account.call("postLike", ["id": post.id, "on": on]) {
            likes = (r["likes"] as? NSNumber)?.intValue ?? likes
        }
    }
}

/// Opens the person's profile. Profiles need an account, so guests are asked to sign in.
private struct ProfileEntry<Content: View>: View {
    let post: FeedPost
    @ViewBuilder var content: () -> Content
    @ObservedObject private var account = AccountStore.shared
    var body: some View {
        Group {
            if account.signedIn {
                NavigationLink { ProfileScreen(userId: post.user.id, fallbackName: post.user.displayName, fallbackColor: post.user.color) } label: { content() }
            } else {
                Button { account.signInShown = true } label: { content() }
            }
        }
        .buttonStyle(.plain)
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
                    Button("Sign in") { account.signInShown = true }.buttonStyle(.borderedProminent).tint(.abFill)
                }
                .padding(32)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.abBackground)
            }
        }
        .navigationTitle("Friends")
        .toolbar { ToolbarItem(placement: .primaryAction) { OnlineButton() } }
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
        if loading { ABDots() }
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
                                Image(systemName: "checkmark").foregroundStyle(.white).frame(width: 34, height: 34).background(Color.abFill, in: Circle())
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
        func read(_ r: [String: Any]) {
            friends = decode([ABUser].self, r["friends"]) ?? []
            incoming = decode([ABUser].self, r["incoming"]) ?? []
            outgoing = decode([ABUser].self, r["outgoing"]) ?? []
            pokes = decode([Poke].self, r["pokes"]) ?? []
        }
        // what we knew last time, straight away
        if loading {
            if let r = account.saved("friends") { read(r); loading = false }
            if let r = account.saved("feed") { feed = decode([FeedPost].self, r["posts"]) ?? [] }
        }
        // the feed doesn't hold up the friends list
        Task { await loadFeed() }
        do {
            read(try await account.callSaving("friends"))
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
        if segment == 2 { await markRead() }
    }

    private func loadFeed() async {
        if let r = try? await account.callSaving("feed") { feed = decode([FeedPost].self, r["posts"]) ?? [] }
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
/// wa.me needs the number with its country code and no leading 0: 0791234567 becomes 962791234567.
/// Arabic digits count too. Numbers that already have a country code stay as they are.
func whatsAppNumber(_ phone: String) -> String {
    let arabic = Array("٠١٢٣٤٥٦٧٨٩"), persian = Array("۰۱۲۳۴۵۶۷۸۹")
    var d = String(phone.compactMap { c -> Character? in
        if let i = arabic.firstIndex(of: c) ?? persian.firstIndex(of: c) { return Character(String(i)) }
        return c.isASCII && c.isNumber ? c : nil
    })
    if d.hasPrefix("00") { d.removeFirst(2) }
    if d.hasPrefix("0") { d = "962" + String(d.dropFirst()) } else if d.count == 9 && d.hasPrefix("7") { d = "962" + d }
    return d
}

struct ProfileScreen: View {
    let userId: String
    var fallbackName = ""
    var fallbackColor = "#8C1D40"
    @ObservedObject private var account = AccountStore.shared
    @State private var user: ABUser?
    @State private var friendship = "none"
    @State private var canMessage = false
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
    @State private var pickingCover = false
    @State private var coverBusy = false
    @State private var cropping: PickedPhoto?
    @State private var openMessage = false
    @State private var openFriends = false
    @State private var openCustomize = false

    private var isMe: Bool { userId == account.user?.id }

    var body: some View {
        List {
            Section { header }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            if let u = user {
                if let p = u.phone, !p.isEmpty {
                    Section("Phone") {
                        Button { if let url = URL(string: "tel:" + p.filter { $0.isNumber || $0 == "+" }) { UIApplication.shared.open(url) } } label: {
                            Label(p, systemImage: "phone.fill")
                        }
                        Button { if let url = URL(string: "https://wa.me/" + whatsAppNumber(p)) { UIApplication.shared.open(url) } } label: {
                            Label("WhatsApp", systemImage: "message.fill")
                        }
                    }
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
            if user == nil && error == nil {
                Section { ForEach(0..<2, id: \.self) { _ in SkeletonPost() } }
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }
        .listStyle(.insetGrouped)
        .animation(.easeOut(duration: 0.25), value: user == nil)
        .modifier(ModerationDialogs(report: $reporting, block: $blocking, blocked: { _ in Task { await load() } }))
        .navigationTitle(user?.displayName ?? fallbackName)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .sheet(item: $poking) { PokeSheet(user: $0) }
        .sheet(isPresented: $composing) { ComposePostSheet { await load() } }
        .sheet(isPresented: $editing, onDismiss: { Task { await load() } }) { EditProfileSheet() }
        .sheet(isPresented: $pickingCover) {
            // the picker has to finish closing before the next screen can open
            AvatarPicker { image in
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { cropping = PickedPhoto(image: image) }
            }
        }
        .fullScreenCover(item: $cropping) { p in
            CoverCropSheet(image: p.image) { image in Task { await setCover(image) } }
        }
        .push(isPresented: $openMessage) {
            if let u = user { DMThreadScreen(userId: u.id, name: u.displayName, color: u.color, photo: u.avatar) }
        }
        .push(isPresented: $openFriends) { FriendsScreen() }
        .push(isPresented: $openCustomize) { CustomizeScreen() }
    }

    private func setCover(_ image: UIImage?) async {
        coverBusy = true
        defer { coverBusy = false }
        do {
            try await account.setCover(image)
            Haptics.success()
            await load()
        } catch { self.error = error.localizedDescription; Haptics.error() }
    }

    /// Cover, photo, name, numbers and actions together, so one screen shows it all, even on a small phone
    private var header: some View {
        let color = Color(hexString: user?.color ?? fallbackColor)
        return VStack(alignment: .leading, spacing: 8) {
            LinearGradient(colors: [color, Color.abFill.opacity(0.75)], startPoint: .topLeading, endPoint: .bottomTrailing)
                .frame(height: 112)
                .overlay {
                    if let cover = user?.cover, !cover.isEmpty, let url = URL(string: cover) {
                        Button { PhotoTools.show(cover) } label: {
                            Color.clear.overlay(alignment: .top) { CachedPhoto(url: url, pixelSize: 1000) { Color.clear } }.clipped().contentShape(Rectangle())
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(Text("View cover photo"))
                    }
                }
                .overlay(alignment: .topTrailing) {
                    if isMe {
                        Menu {
                            Button { pickingCover = true } label: { Label("Choose a cover photo", systemImage: "photo") }
                            if user?.cover?.isEmpty == false {
                                Button(role: .destructive) { Task { await setCover(nil) } } label: { Label("Remove cover photo", systemImage: "trash") }
                            }
                        } label: {
                            Image(systemName: coverBusy ? "hourglass" : "camera.fill").font(.subheadline).foregroundStyle(.white)
                                .frame(width: 34, height: 34).background(.black.opacity(0.45), in: Circle())
                        }
                        .padding(8)
                        .accessibilityLabel(Text("Change cover photo"))
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            HStack(alignment: .top, spacing: 12) {
                Avatar(name: user?.displayName ?? fallbackName, color: user?.color ?? fallbackColor, size: 76,
                       admin: user?.isAdmin == true, photo: user?.avatar, online: user?.online)
                    .background(Circle().fill(Color.abBackground).padding(-3))
                    .padding(.top, -34)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(user?.displayName ?? fallbackName).font(.headline).lineLimit(1).minimumScaleFactor(0.8)
                        if user?.isAdmin == true { Badge(text: L("ADMIN"), color: .orange) }
                    }
                    if let u = user {
                        Text("@\(u.username)").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        Text(u.online == true ? L("Online now") : lastSeenText(u.lastSeen)).font(.caption)
                            .foregroundStyle(u.online == true ? Color.green : .secondary).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 4)

            if let u = user {
                if u.badges?.isEmpty == false { BadgeRow(badges: u.badges ?? []).padding(.horizontal, 4) }
                HStack(spacing: 0) {
                    stat(friendCount, "Friends") { if isMe { openFriends = true } }
                    stat(postCount, "Posts")
                    stat(messageCount, "Messages")
                    stat(u.badges?.count ?? 0, "Badges")
                }
                .padding(.vertical, 8)
                .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                actionRow(u)

                if let about = aboutLines(u) {
                    VStack(alignment: .leading, spacing: 4) { about }
                        .font(.footnote)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(10)
                        .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            } else if error == nil {
                ProfileSkeleton()
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, 4)
    }

    @ViewBuilder private func actionRow(_ u: ABUser) -> some View {
        if isMe {
            HStack(spacing: 8) {
                pill("Edit profile", "pencil", filled: true) { editing = true }
                pill("Write a post", "square.and.pencil") { composing = true }
                pill("Customize", "slider.horizontal.3") { openCustomize = true }
            }
        } else if account.signedIn, account.user?.blocked?.contains(u.id) != true {
            HStack(spacing: 8) {
                FriendButton(userId: u.id, state: $friendship) { await load() }
                if friendship == "friends" {
                    pill("Poke", "hand.point.right.fill") { poking = u }
                }
                if canMessage {
                    pill("Message", "paperplane.fill", filled: true) { openMessage = true }
                }
            }
        }
    }

    private func pill(_ title: String, _ icon: String, filled: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(L(title), systemImage: icon)
                .font(.footnote.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.75)
                .frame(maxWidth: .infinity).padding(.vertical, 8)
                .foregroundStyle(filled ? Color.white : Color.primary)
                .background(filled ? AnyShapeStyle(Color.abFill) : AnyShapeStyle(Color.primary.opacity(0.09)), in: Capsule())
        }
        .buttonStyle(.borderless)
    }

    /// Bio, studies and interests in one small card
    private func aboutLines(_ u: ABUser) -> AnyView? {
        let studies = [u.college, u.major, u.year.map { L($0) }].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
        let bio = u.bio ?? "", interests = u.interests ?? ""
        let sex = u.gender == "male" ? L("Male") : u.gender == "female" ? L("Female") : ""
        if bio.isEmpty && studies.isEmpty && interests.isEmpty && sex.isEmpty { return nil }
        return AnyView(Group {
            if !bio.isEmpty { Text(bio).lineLimit(4) }
            if !sex.isEmpty { Label(sex, systemImage: "person.fill").foregroundStyle(.secondary) }
            if !studies.isEmpty { Label(studies, systemImage: "graduationcap").foregroundStyle(.secondary) }
            if !interests.isEmpty { Label(interests, systemImage: "sparkles").foregroundStyle(.secondary) }
        })
    }

    private func stat(_ n: Int, _ title: String, tap: (() -> Void)? = nil) -> some View {
        Button { tap?() } label: {
            VStack(spacing: 1) {
                Text("\(n)").font(.subheadline.weight(.bold)).monospacedDigit().foregroundStyle(.primary)
                Text(L(title)).font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderless)
    }

    private func load() async {
        func read(_ r: [String: Any]) {
            user = decode(ABUser.self, r["user"])
            friendship = r["friendship"] as? String ?? "none"
            canMessage = r["canMessage"] as? Bool ?? false
            friendCount = (r["friendCount"] as? NSNumber)?.intValue ?? 0
            postCount = (r["postCount"] as? NSNumber)?.intValue ?? 0
            messageCount = (r["messageCount"] as? NSNumber)?.intValue ?? 0
            posts = decode([FeedPost].self, r["posts"]) ?? []
        }
        if user == nil, let r = account.saved("profile", tag: userId) { read(r) }
        do {
            read(try await account.callSaving("profile", ["userId": userId, "social": true], tag: userId))
            error = nil
        } catch { self.error = error.localizedDescription }
    }
}
