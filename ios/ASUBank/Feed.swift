import SwiftUI

// The feed: everyone's posts with photos and comments, plus stories that disappear after 24 hours.
// Everything lives in the accounts API, so both apps share it.

struct StoryItem: Decodable, Identifiable, Hashable {
    let id: String
    var image: String?
    var text: String?
    var bg: String?
    var created: String?
    var seen: Bool?
    var views: Int?
}

struct StoryGroup: Decodable, Identifiable {
    let user: ABUser
    var stories: [StoryItem]
    var seen: Bool?
    var id: String { user.id }
}

struct PostComment: Decodable, Identifiable {
    let id: String
    var text: String
    var created: String?
    let user: ABUser
}

enum PhotoTools {
    /// A photo shrunk so its long side fits, as base64 JPEG, for sending to the API
    static func jpegBase64(_ image: UIImage, maxSide: CGFloat, quality: CGFloat = 0.78) -> String? {
        let longest = max(image.size.width, image.size.height)
        guard longest > 0 else { return nil }
        let scale = min(1, maxSide / longest)
        let size = CGSize(width: (image.size.width * scale).rounded(), height: (image.size.height * scale).rounded())
        let fmt = UIGraphicsImageRendererFormat.default()
        fmt.scale = 1
        let small = UIGraphicsImageRenderer(size: size, format: fmt).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        return small.jpegData(compressionQuality: quality)?.base64EncodedString()
    }

    /// The same Drive photo at a size that looks sharp full screen
    static func large(_ url: String) -> URL? {
        URL(string: url.replacingOccurrences(of: #"sz=w\d+"#, with: "sz=w1600", options: .regularExpression))
    }

    /// Full screen over whatever is showing, sheets included
    @MainActor static func show(_ url: String) {
        guard let large = large(url) else { return }
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        guard var top = scene?.windows.first(where: \.isKeyWindow)?.rootViewController ?? scene?.windows.first?.rootViewController else { return }
        while let next = top.presentedViewController { top = next }
        let presenter = top
        let host = UIHostingController(rootView: PhotoViewer(url: large) { presenter.dismiss(animated: true) })
        host.modalPresentationStyle = .overFullScreen
        host.modalTransitionStyle = .crossDissolve
        host.view.backgroundColor = .clear
        top.present(host, animated: true)
    }
}

/// A photo on black: pinch to zoom, drag down or tap X to close
struct PhotoViewer: View {
    let url: URL
    let close: () -> Void
    @State private var image: UIImage?
    @State private var failed = false
    @State private var zoom: CGFloat = 1
    @State private var steady: CGFloat = 1
    @State private var drag: CGSize = .zero

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.opacity(1 - min(0.6, Double(abs(drag.height)) / 500.0)).ignoresSafeArea()
            Group {
                if let image {
                    Image(uiImage: image).resizable().scaledToFit()
                        .scaleEffect(zoom)
                        .offset(zoom > 1 ? drag : CGSize(width: 0, height: drag.height))
                        .gesture(MagnificationGesture()
                            .onChanged { zoom = max(1, min(5, steady * $0)) }
                            .onEnded { _ in steady = zoom })
                        .simultaneousGesture(DragGesture()
                            .onChanged { drag = $0.translation }
                            .onEnded { v in
                                if zoom <= 1 && abs(v.translation.height) > 120 { close() }
                                else if zoom <= 1 { withAnimation(.spring()) { drag = .zero } }
                            })
                        .onTapGesture(count: 2) {
                            withAnimation(.spring()) {
                                zoom = zoom > 1 ? 1 : 2.5
                                steady = zoom
                                if zoom == 1 { drag = .zero }
                            }
                        }
                } else if failed {
                    Text("Couldn't load this photo").foregroundStyle(.white)
                } else {
                    ProgressView().tint(.white)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            Button { close() } label: {
                Image(systemName: "xmark").font(.headline).foregroundStyle(.white)
                    .frame(width: 40, height: 40).background(.white.opacity(0.18), in: Circle())
            }
            .padding(16)
            .accessibilityLabel(Text("Close"))
        }
        .statusBar(hidden: true)
        .task {
            image = await PhotoCache.shared.load(url, pixelSize: 800)
            failed = image == nil
        }
    }
}

/// Hold a profile photo to see it bigger
struct AvatarPeek: ViewModifier {
    let photo: String?
    func body(content: Content) -> some View {
        if let photo, !photo.isEmpty, let url = PhotoTools.large(photo) {
            if #available(iOS 16.0, *) {
                content.contextMenu {
                    Button { PhotoTools.show(photo) } label: { Label("View full screen", systemImage: "arrow.up.left.and.arrow.down.right") }
                } preview: {
                    CachedPhoto(url: url, pixelSize: 340) { ProgressView().frame(width: 340, height: 340) }
                        .frame(width: 340, height: 340).clipped()
                }
            } else {
                content.contextMenu {
                    Button { PhotoTools.show(photo) } label: { Label("View full screen", systemImage: "arrow.up.left.and.arrow.down.right") }
                }
            }
        } else {
            content
        }
    }
}

struct FeedTab: View {
    var body: some View { ABStack { FeedScreen() } }
}

struct FeedScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var scope = "all"
    @State private var posts: [FeedPost] = []
    @State private var groups: [StoryGroup] = []
    @State private var loaded = false
    @State private var more = true
    @State private var loadingMore = false
    @State private var error: String?
    @State private var composing = false
    @State private var addingStory = false
    @State private var viewing: StoryStart?

    struct StoryStart: Identifiable { let index: Int; var id: Int { index } }

    var body: some View {
        Group {
            if !account.signedIn {
                SignInPrompt(forFeed: true)
            } else {
                list
            }
        }
        .navigationTitle("Feed")
    }

    private var list: some View {
        List {
            Section {
                StoriesRow(groups: groups, add: { addingStory = true }, open: { viewing = StoryStart(index: $0) })
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)

            Section {
                Button { composing = true } label: {
                    HStack(spacing: 10) {
                        if let u = account.user {
                            Avatar(name: u.displayName, color: u.color, size: 38, admin: u.isAdmin, photo: u.avatar, peek: false)
                        }
                        Text("What's on your mind?").foregroundStyle(.secondary)
                        Spacer()
                        Image(systemName: "photo.fill").foregroundStyle(Color.abAccent)
                    }
                    .padding(.vertical, 2)
                }
                .buttonStyle(.plain)
            }

            Picker("Show", selection: $scope) {
                Text("Everyone").tag("all")
                Text("Friends").tag("friends")
            }
            .pickerStyle(.segmented)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)

            if let error, posts.isEmpty {
                Section {
                    Text(error).foregroundStyle(.red)
                    Button("Try again") { Task { await load() } }
                }
            } else if posts.isEmpty {
                Section {
                    if loaded {
                        EmptyState(symbol: "text.bubble", title: scope == "friends" ? "No posts from friends yet" : "No posts yet",
                                   text: "Share something with other ASU students.")
                            .listRowBackground(Color.clear)
                    } else {
                        HStack { Spacer(); ProgressView(); Spacer() }.padding(20).listRowBackground(Color.clear)
                    }
                }
            }
            ForEach(posts) { p in
                Section { PostRow(post: p) { await load() } }
            }
            if more && !posts.isEmpty {
                Section {
                    Button { Task { await loadMore() } } label: {
                        HStack { Spacer(); if loadingMore { ProgressView() } else { Text("Load more") }; Spacer() }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .refreshable { await load() }
        .task { if !loaded { await load() } }
        .onChange(of: scope) { _ in posts = []; loaded = false; Task { await load() } }
        .sheet(isPresented: $composing) { ComposePostSheet { await load() } }
        .sheet(isPresented: $addingStory) { ComposeStorySheet { await loadStories() } }
        .fullScreenCover(item: $viewing, onDismiss: { Task { await loadStories() } }) { start in
            StoryViewer(groups: groups, startGroup: start.index)
        }
    }

    private func load() async {
        Task { await loadStories() }
        if posts.isEmpty, let r = account.saved("feedAll", tag: scope) { posts = decode([FeedPost].self, r["posts"]) ?? [] }
        do {
            let r = try await account.callSaving("feedAll", ["scope": scope], tag: scope)
            let list = decode([FeedPost].self, r["posts"]) ?? []
            posts = list
            more = list.count >= 25
            error = nil
        } catch { self.error = error.localizedDescription }
        loaded = true
    }

    private func loadMore() async {
        guard !loadingMore else { return }
        loadingMore = true
        defer { loadingMore = false }
        guard let r = try? await account.call("feedAll", ["scope": scope, "offset": posts.count]) else { return }
        let list = decode([FeedPost].self, r["posts"]) ?? []
        let have = Set(posts.map(\.id))
        posts += list.filter { !have.contains($0.id) }
        more = list.count >= 25
    }

    private func loadStories() async {
        if let r = try? await account.call("stories") { groups = decode([StoryGroup].self, r["groups"]) ?? [] }
    }
}

// MARK: - stories

struct StoriesRow: View {
    let groups: [StoryGroup]
    let add: () -> Void
    let open: (Int) -> Void
    @ObservedObject private var account = AccountStore.shared

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 12) {
                Button(action: add) {
                    VStack(spacing: 5) {
                        ZStack(alignment: .bottomTrailing) {
                            Avatar(name: account.user?.displayName ?? "", color: account.user?.color ?? "#8C1D40", size: 60, photo: account.user?.avatar, peek: false)
                            Image(systemName: "plus.circle.fill").font(.system(size: 22))
                                .foregroundStyle(.white, Color.abAccent)
                                .background(Circle().fill(Color.abBackground))
                        }
                        Text("Add story").font(.caption2).foregroundStyle(.primary).lineLimit(1)
                    }
                    .frame(width: 72)
                }
                .buttonStyle(.plain)
                ForEach(Array(groups.enumerated()), id: \.element.id) { i, g in
                    Button { open(i) } label: {
                        VStack(spacing: 5) {
                            Avatar(name: g.user.displayName, color: g.user.color, size: 60, photo: g.user.avatar, peek: false)
                                .padding(3)
                                .overlay(Circle().stroke(g.seen == true ? AnyShapeStyle(Color.secondary.opacity(0.4)) : AnyShapeStyle(Color.abAccent.abGradient), lineWidth: 2.5))
                            Text(g.user.id == account.user?.id ? L("Your story") : g.user.displayName)
                                .font(.caption2).foregroundStyle(.primary).lineLimit(1)
                        }
                        .frame(width: 72)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 6)
        }
    }
}

struct ComposeStorySheet: View {
    var posted: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    @State private var text = ""
    @State private var photo: UIImage?
    @State private var picking = false
    @State private var bg = "#8C1D40"
    @State private var busy = false
    @State private var error: String?

    private let colors = ["#8C1D40", "#0A63C9", "#1F7A3A", "#6E3FD1", "#D2510A", "#00847F", "#C2185B", "#3A3A3C"]

    var body: some View {
        ABStack {
            Form {
                Section {
                    ZStack {
                        if let photo {
                            Color.clear.frame(height: 300).overlay(Image(uiImage: photo).resizable().scaledToFill()).clipped()
                        } else {
                            Color(hexString: bg).frame(height: 300)
                        }
                        if !text.isEmpty {
                            Text(text).font(.title3.weight(.bold)).foregroundStyle(.white).multilineTextAlignment(.center)
                                .shadow(radius: 3).padding(16)
                        }
                    }
                    .listRowInsets(EdgeInsets())
                }
                Section {
                    Button { picking = true } label: { Label(photo == nil ? "Add a photo" : "Change photo", systemImage: "photo") }
                    if photo != nil {
                        Button(role: .destructive) { photo = nil } label: { Label("Remove photo", systemImage: "trash") }
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(colors, id: \.self) { c in
                                    Button { bg = c } label: {
                                        Circle().fill(Color(hexString: c)).frame(width: 34, height: 34)
                                            .overlay(Circle().stroke(Color.primary, lineWidth: bg == c ? 2.5 : 0).padding(-3))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(5)
                        }
                    }
                }
                Section {
                    TextField("Write something", text: $text)
                } footer: {
                    Text("Your story is seen by ASU Bank students for 24 hours, then it disappears. It's deleted from our database after 2 days.")
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle("New story")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Posting…" : "Post") { Task { await post() } }
                        .disabled(busy || (photo == nil && text.trimmingCharacters(in: .whitespaces).isEmpty))
                }
            }
            .sheet(isPresented: $picking) { AvatarPicker { photo = $0 } }
        }
    }

    private func post() async {
        busy = true
        defer { busy = false }
        var fields: [String: Any] = ["text": String(text.prefix(300)), "bg": bg]
        if let photo {
            let data = await Task.detached(priority: .userInitiated) { PhotoTools.jpegBase64(photo, maxSide: 1280) }.value
            guard let data else { error = L("Couldn't load this photo"); return }
            fields["image"] = data
        }
        do {
            _ = try await account.call("storyCreate", fields)
            Haptics.success()
            await posted()
            dismiss()
        } catch { self.error = error.localizedDescription; Haptics.error() }
    }
}

struct StoryViewer: View {
    let groups: [StoryGroup]
    let startGroup: Int
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    @State private var g = 0
    @State private var s = 0
    @State private var progress: Double = 0
    @State private var paused = false
    @State private var removed = Set<String>()
    @State private var reporting: ReportTarget?

    init(groups: [StoryGroup], startGroup: Int) {
        self.groups = groups
        self.startGroup = startGroup
        _g = State(initialValue: startGroup)
    }

    private var group: StoryGroup? { groups.indices.contains(g) ? groups[g] : nil }
    private var stories: [StoryItem] { group?.stories.filter { !removed.contains($0.id) } ?? [] }
    private var story: StoryItem? { stories.indices.contains(s) ? stories[s] : nil }
    private static let seconds = 6.0

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let story {
                page(story)
            }
            HStack(spacing: 0) {
                Color.clear.contentShape(Rectangle()).onTapGesture { back() }.frame(maxWidth: .infinity)
                Color.clear.contentShape(Rectangle()).onTapGesture { forward() }.frame(maxWidth: .infinity)
            }
            .simultaneousGesture(LongPressGesture(minimumDuration: 0.2).onChanged { _ in paused = true })
            .simultaneousGesture(DragGesture(minimumDistance: 0).onEnded { v in
                paused = false
                if v.translation.height > 120 { dismiss() }
            })
            VStack(spacing: 10) {
                HStack(spacing: 4) {
                    ForEach(stories.indices, id: \.self) { i in
                        GeometryReader { geo in
                            Capsule().fill(.white.opacity(0.3))
                                .overlay(alignment: .leading) {
                                    Capsule().fill(.white).frame(width: geo.size.width * (i < s ? 1 : i == s ? progress : 0))
                                }
                        }
                        .frame(height: 3)
                    }
                }
                if let u = group?.user {
                    HStack(spacing: 10) {
                        Avatar(name: u.displayName, color: u.color, size: 36, photo: u.avatar, peek: false)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(u.displayName).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                            Text(Social.ago(story?.created)).font(.caption).foregroundStyle(.white.opacity(0.8))
                        }
                        Spacer()
                        menu(u)
                        Button { dismiss() } label: {
                            Image(systemName: "xmark").font(.headline).foregroundStyle(.white).frame(width: 36, height: 36)
                        }
                    }
                }
                Spacer()
                if let v = story?.views, group?.user.id == account.user?.id {
                    Label(String(format: L("%d views"), v), systemImage: "eye.fill")
                        .font(.footnote.weight(.semibold)).foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 6).background(.black.opacity(0.45), in: Capsule())
                }
            }
            .padding(12)
        }
        .statusBar(hidden: true)
        .sheet(item: $reporting, onDismiss: { paused = false }) { ReportSheet(target: $0) }
        .task(id: "\(g)-\(s)-\(story?.id ?? "")") { await run() }
    }

    @ViewBuilder private func page(_ item: StoryItem) -> some View {
        ZStack {
            if let image = item.image, !image.isEmpty, let url = PhotoTools.large(image) {
                StoryPhoto(url: url).id(item.id)
            } else {
                Color(hexString: item.bg ?? "#8C1D40").ignoresSafeArea()
            }
            if let t = item.text, !t.isEmpty {
                Text(t).font(.title2.weight(.bold)).foregroundStyle(.white).multilineTextAlignment(.center)
                    .shadow(color: .black.opacity(0.7), radius: 4).padding(24)
            }
        }
    }

    private func menu(_ u: ABUser) -> some View {
        Menu {
            if u.id == account.user?.id || account.isAdmin {
                Button(role: .destructive) { Task { await delete() } } label: { Label("Delete", systemImage: "trash") }
            }
            if u.id != account.user?.id, let story {
                Button {
                    paused = true
                    reporting = ReportTarget(kind: "story", targetId: story.id, name: story.text ?? "")
                } label: { Label("Report", systemImage: "flag") }
            }
        } label: {
            Image(systemName: "ellipsis").font(.headline).foregroundStyle(.white).frame(width: 36, height: 36)
        }
    }

    private func run() async {
        progress = 0
        guard let story else { return }
        if story.seen != true { _ = try? await account.call("storySeen", ["id": story.id]) }
        let step = 0.05 / Self.seconds
        while progress < 1 {
            try? await Task.sleep(nanoseconds: 50_000_000)
            if Task.isCancelled { return }
            if !paused { progress += step }
        }
        forward()
    }

    private func forward() {
        if s + 1 < stories.count { s += 1; return }
        if g + 1 < groups.count { g += 1; s = 0; return }
        dismiss()
    }

    private func back() {
        if s > 0 { s -= 1; return }
        if g > 0 {
            g -= 1
            s = max(0, stories.count - 1)
        } else { progress = 0 }
    }

    private func delete() async {
        guard let story else { return }
        _ = try? await account.call("storyDelete", ["id": story.id])
        removed.insert(story.id)
        if stories.isEmpty { forward() } else if s >= stories.count { s = stories.count - 1 }
    }
}

private struct StoryPhoto: View {
    let url: URL
    @State private var image: UIImage?
    var body: some View {
        Group {
            if let image { Image(uiImage: image).resizable().scaledToFit() } else { ProgressView().tint(.white) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task { image = await PhotoCache.shared.load(url, pixelSize: 800) }
    }
}

// MARK: - comments

struct CommentsSheet: View {
    let post: FeedPost
    var changed: (Int) -> Void
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    @State private var comments: [PostComment] = []
    @State private var loaded = false
    @State private var draft = ""
    @State private var busy = false
    @State private var error: String?
    @State private var reporting: ReportTarget?

    var body: some View {
        ABStack {
            List {
                if comments.isEmpty {
                    if loaded {
                        EmptyState(symbol: "bubble.left", title: "No comments yet", text: "Be the first to comment.").listRowBackground(Color.clear)
                    } else {
                        HStack { Spacer(); ProgressView(); Spacer() }.padding(20).listRowBackground(Color.clear)
                    }
                }
                ForEach(comments) { c in
                    HStack(alignment: .top, spacing: 10) {
                        Avatar(name: c.user.displayName, color: c.user.color, size: 34, admin: c.user.isAdmin, photo: c.user.avatar)
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(c.user.displayName).font(.subheadline.weight(.semibold))
                                Text(Social.ago(c.created)).font(.caption).foregroundStyle(.secondary)
                            }
                            Text(c.text).font(.subheadline).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .swipeActions {
                        if canDelete(c) {
                            Button(role: .destructive) { Task { await remove(c) } } label: { Label("Delete", systemImage: "trash") }
                        }
                        if c.user.id != account.user?.id {
                            Button { reporting = ReportTarget(kind: "comment", targetId: c.id, name: c.text) } label: { Label("Report", systemImage: "flag") }
                                .tint(.orange)
                        }
                    }
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .listStyle(.plain)
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 8) {
                    TextField("Write a comment", text: $draft)
                        .padding(.horizontal, 12).padding(.vertical, 9)
                        .background(Color.abSurface, in: Capsule())
                    Button { Task { await send() } } label: {
                        Image(systemName: busy ? "hourglass" : "paperplane.fill").foregroundStyle(.white)
                            .frame(width: 38, height: 38).background(Color.abAccent, in: Circle())
                    }
                    .disabled(busy || draft.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityLabel(Text("Send"))
                }
                .padding(10)
                .background(.bar)
            }
            .navigationTitle("Comments")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .task { await load() }
            .sheet(item: $reporting) { ReportSheet(target: $0) }
        }
    }

    private func canDelete(_ c: PostComment) -> Bool {
        c.user.id == account.user?.id || post.user.id == account.user?.id || account.isAdmin
    }

    private func load() async {
        do {
            let r = try await account.call("comments", ["postId": post.id])
            comments = decode([PostComment].self, r["comments"]) ?? []
            error = nil
            changed(comments.count)
        } catch { self.error = error.localizedDescription }
        loaded = true
    }

    private func send() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await account.call("commentAdd", ["postId": post.id, "text": String(draft.prefix(500))])
            draft = ""
            Haptics.success()
            await load()
        } catch { self.error = error.localizedDescription; Haptics.error() }
    }

    private func remove(_ c: PostComment) async {
        _ = try? await account.call("commentDelete", ["id": c.id])
        await load()
    }
}
