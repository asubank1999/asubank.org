import SwiftUI

// The feed: everyone's posts with photos and comments, plus stories that disappear after 24 hours.
// Everything lives in the accounts API, so both apps share it.

struct StoryItem: Decodable, Identifiable, Hashable {
    let id: String
    var image: String?
    var video: String?
    var audience: String?
    var text: String?
    var bg: String?
    var created: String?
    var seen: Bool?
    var views: Int?
    var style: String?
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
                    ABDots().tint(.white)
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
                    CachedPhoto(url: url, pixelSize: 340) { ABDots().frame(width: 340, height: 340) }
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
    var body: some View { ABStack { FeedScreen(swipeCamera: true) } }
}

/// Who a post or story is for
struct AudiencePicker: View {
    @Binding var audience: String
    var body: some View {
        Picker(selection: $audience) {
            Label("Everyone, also guests", systemImage: "globe").tag("public")
            Label("ASU students", systemImage: "graduationcap").tag("students")
            Label("Friends only", systemImage: "person.2").tag("friends")
        } label: {
            Label("Who can see this", systemImage: Social.audienceSymbol(audience))
        }
    }
}

/// Notices a sideways swipe on the list behind it, without getting in the way of scrolling
private struct SwipeCatcher: UIViewRepresentable {
    var swiped: () -> Void

    func makeUIView(context: Context) -> UIView {
        let v = UIView()
        v.isUserInteractionEnabled = false
        return v
    }
    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.swiped = swiped
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { context.coordinator.attach(from: uiView) }
    }
    func makeCoordinator() -> Coordinator { Coordinator(swiped) }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var swiped: () -> Void
        private weak var list: UIScrollView?
        init(_ s: @escaping () -> Void) { swiped = s }

        private func search(_ v: UIView) -> UIScrollView? {
            if v is UITableView || v is UICollectionView { return v as? UIScrollView }
            for s in v.subviews { if let f = search(s) { return f } }
            return nil
        }

        func attach(from view: UIView) {
            guard list == nil else { return }
            var up = view.superview
            while let s = up {
                if let f = search(s) {
                    list = f
                    for d in [UISwipeGestureRecognizer.Direction.left, .right] {
                        let g = UISwipeGestureRecognizer(target: self, action: #selector(fire))
                        g.direction = d
                        g.delegate = self
                        f.addGestureRecognizer(g)
                    }
                    return
                }
                up = s.superview
            }
        }

        @objc private func fire() { swiped() }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        /// The stories row scrolls sideways by itself
        func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            var v = touch.view
            while let s = v, s !== list {
                if let sc = s as? UIScrollView, sc.contentSize.width > sc.bounds.width + 1 { return false }
                v = s.superview
            }
            return true
        }
    }
}

struct FeedScreen: View {
    var swipeCamera = false
    @ObservedObject private var account = AccountStore.shared
    @AppStorage("ab.feed.scope") private var savedScope = "friends"
    @State private var posts: [FeedPost] = []
    @State private var groups: [StoryGroup] = []
    @State private var loaded = false
    @State private var more = true
    @State private var loadingMore = false
    @State private var error: String?
    @State private var composing = false
    @State private var camera = false
    @State private var pendingDraft: StoryDraft?
    @State private var draft: StoryDraft?
    @State private var viewing: StoryStart?

    struct StoryStart: Identifiable { let index: Int; var id: Int { index } }

    /// People without an account only have the guest feed
    private var scope: String { account.signedIn ? savedScope : "guest" }

    var body: some View {
        List {
            if !groups.isEmpty || account.signedIn {
                Section {
                    StoriesRow(groups: groups, add: { camera = true }, open: { viewing = StoryStart(index: $0) })
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }

            if account.signedIn {
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

                Picker("Show", selection: $savedScope) {
                    Text("Friends").tag("friends")
                    Text("Strangers").tag("strangers")
                    Text("Guest").tag("guest")
                }
                .pickerStyle(.segmented)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            } else {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("You're browsing as a guest").font(.headline)
                        Text("Sign in to post, comment, like and add stories.").font(.subheadline).foregroundStyle(.secondary)
                        Button("Sign in or create account") { account.signInShown = true }
                            .buttonStyle(.borderedProminent).tint(.abFill)
                    }
                    .padding(.vertical, 4)
                }
            }

            if let error, posts.isEmpty {
                Section {
                    Text(error).foregroundStyle(.red)
                    Button("Try again") { Task { await load() } }
                }
            } else if posts.isEmpty {
                Section {
                    if loaded {
                        EmptyState(symbol: "text.bubble", title: emptyTitle, text: emptyText).listRowBackground(Color.clear)
                    } else {
                        ForEach(0..<3, id: \.self) { _ in SkeletonPost() }
                    }
                }
            }
            ForEach(posts) { p in
                Section { PostRow(post: p) { await load() } }
            }
            if more && !posts.isEmpty {
                Section {
                    Button { Task { await loadMore() } } label: {
                        HStack { Spacer(); if loadingMore { ABDots() } else { Text("Load more") }; Spacer() }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .background(swipeCamera && account.signedIn ? AnyView(SwipeCatcher { camera = true }) : AnyView(EmptyView()))
        .navigationTitle("Feed")
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) { OnlineButton() }
            ToolbarItem(placement: .primaryAction) {
                if account.signedIn {
                    Button { camera = true } label: { Image(systemName: "camera.fill") }.accessibilityLabel(Text("Add story"))
                }
            }
        }
        .refreshable { await load() }
        .task(id: scope) {
            posts = []
            loaded = false
            await load()
        }
        .sheet(isPresented: $composing) { ComposePostSheet { await load() } }
        .fullScreenCover(isPresented: $camera, onDismiss: {
            if let d = pendingDraft { pendingDraft = nil; draft = d }
        }) {
            StoryCameraScreen { media in pendingDraft = StoryDraft(media: media) }
        }
        .sheet(item: $draft) { d in ComposeStorySheet(media: d.media) { await loadStories() } }
        .fullScreenCover(item: $viewing, onDismiss: { Task { await loadStories() } }) { start in
            StoryViewer(groups: groups, startGroup: start.index)
        }
    }

    private var emptyTitle: String {
        switch scope {
        case "friends": return "No posts from friends yet"
        case "strangers": return "No posts from other students yet"
        default: return "No public posts yet"
        }
    }

    private var emptyText: String {
        switch scope {
        case "friends": return "Add friends, or look at what other students share under Strangers."
        case "guest": return "Posts shared with everyone show up here. Anyone can read them, also without an account."
        default: return "Share something with other ASU students."
        }
    }

    private func load() async {
        Task { await loadStories() }
        let action = account.signedIn ? "feedAll" : "publicFeed"
        if posts.isEmpty, let r = account.saved(action, tag: scope) { posts = decode([FeedPost].self, r["posts"]) ?? [] }
        do {
            let r = try await account.callSaving(action, ["scope": scope], tag: scope)
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
        let action = account.signedIn ? "feedAll" : "publicFeed"
        guard let r = try? await account.call(action, ["scope": scope, "offset": posts.count]) else { return }
        let list = decode([FeedPost].self, r["posts"]) ?? []
        let have = Set(posts.map(\.id))
        posts += list.filter { !have.contains($0.id) }
        more = list.count >= 25
    }

    private func loadStories() async {
        if let r = try? await account.call(account.signedIn ? "stories" : "publicStories") { groups = decode([StoryGroup].self, r["groups"]) ?? [] }
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
                if account.signedIn { Button(action: add) {
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
                .buttonStyle(.plain) }
                ForEach(Array(groups.enumerated()), id: \.element.id) { i, g in
                    Button { open(i) } label: {
                        VStack(spacing: 5) {
                            Avatar(name: g.user.displayName, color: g.user.color, size: 60, photo: g.user.avatar, peek: false)
                                .padding(3)
                                .overlay(Circle().stroke(g.seen == true ? AnyShapeStyle(Color.secondary.opacity(0.4)) : AnyShapeStyle(Color.abFill.abGradient), lineWidth: 2.5))
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
    @State private var videoLength: Double?
    @State private var viewersOf: ViewersTarget?

    struct ViewersTarget: Identifiable { let id: String }

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
                page(story).id(story.id).transition(.opacity)
            }
            HStack(spacing: 0) {
                Color.clear.contentShape(Rectangle()).onTapGesture { back() }.frame(maxWidth: .infinity)
                Color.clear.contentShape(Rectangle()).onTapGesture { forward() }.frame(maxWidth: .infinity)
            }
            .simultaneousGesture(LongPressGesture(minimumDuration: 0.2).onChanged { _ in paused = true })
            .simultaneousGesture(DragGesture(minimumDistance: 0).onEnded { v in
                paused = false
                if v.translation.height > 120 { dismiss() }
                else if abs(v.translation.width) > 70, abs(v.translation.width) > abs(v.translation.height) { switchPerson(v.translation.width < 0) }
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
                    Button {
                        paused = true
                        if let id = story?.id { viewersOf = ViewersTarget(id: id) }
                    } label: {
                        Label(String(format: L("%d views"), v), systemImage: "eye.fill")
                            .font(.footnote.weight(.semibold)).foregroundStyle(.white)
                            .padding(.horizontal, 14).padding(.vertical, 8).background(.black.opacity(0.45), in: Capsule())
                    }
                }
            }
            .padding(12)
        }
        .statusBar(hidden: true)
        .animation(.easeInOut(duration: 0.22), value: story?.id)
        .sheet(item: $reporting, onDismiss: { paused = false }) { ReportSheet(target: $0) }
        .sheet(item: $viewersOf, onDismiss: { paused = false }) { StoryViewersSheet(storyId: $0.id) }
        .task(id: "\(g)-\(s)-\(story?.id ?? "")") { await run() }
    }

    @ViewBuilder private func page(_ item: StoryItem) -> some View {
        ZStack {
            if let video = item.video, !video.isEmpty, let url = URL(string: video) {
                StoryVideoView(url: url, paused: paused, duration: $videoLength).id(item.id)
            } else if let image = item.image, !image.isEmpty, let url = PhotoTools.large(image) {
                StoryPhoto(url: url).id(item.id)
            } else {
                StoryBackground(bg: item.bg ?? "#8C1D40", bg2: StoryStyle(json: item.style).bg2).ignoresSafeArea()
            }
            if let t = item.text, !t.isEmpty {
                StoryTextView(text: t, style: StoryStyle(json: item.style)).id(item.id)
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
        videoLength = nil
        guard let story else { return }
        if story.seen != true, account.signedIn { Task { _ = try? await account.call("storySeen", ["id": story.id]) } }
        let isVideo = story.video?.isEmpty == false
        while progress < 1 {
            try? await Task.sleep(nanoseconds: 50_000_000)
            if Task.isCancelled { return }
            // a video's bar starts once the video has loaded, and follows its length
            if isVideo && videoLength == nil { continue }
            if !paused { progress += 0.05 / (videoLength ?? Self.seconds) }
        }
        forward()
    }

    /// Swiping sideways goes to the next or previous person's stories
    private func switchPerson(_ next: Bool) {
        Haptics.tap()
        if next {
            if g + 1 < groups.count { g += 1; s = 0 } else { dismiss() }
        } else if g > 0 {
            g -= 1
            s = 0
        }
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
            if let image { Image(uiImage: image).resizable().scaledToFit() } else { ABDots().tint(.white) }
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
    @State private var error: String?
    @State private var reporting: ReportTarget?

    var body: some View {
        ABStack {
            List {
                if comments.isEmpty {
                    if loaded {
                        EmptyState(symbol: "bubble.left", title: "No comments yet", text: "Be the first to comment.").listRowBackground(Color.clear)
                    } else {
                        ForEach(0..<4, id: \.self) { _ in SkeletonRow().listRowSeparator(.hidden) }
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
                    .opacity(c.id.hasPrefix("local-") ? 0.6 : 1)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .contextMenu {
                        Button { UIPasteboard.general.string = c.text } label: { Label("Copy", systemImage: "doc.on.doc") }
                        if canDelete(c) {
                            Button(role: .destructive) { Task { await remove(c) } } label: { Label("Delete", systemImage: "trash") }
                        }
                        if c.user.id != account.user?.id {
                            Button { reporting = ReportTarget(kind: "comment", targetId: c.id, name: c.text) } label: { Label("Report", systemImage: "flag") }
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
                        Image(systemName: "paperplane.fill").foregroundStyle(.white)
                            .frame(width: 38, height: 38).background(Color.abFill, in: Circle())
                    }
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
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

    /// The comment shows at once; only a failure brings the words back
    private func send() async {
        let words = String(draft.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
        guard !words.isEmpty, let me = account.user else { return }
        let temp = PostComment(id: "local-" + UUID().uuidString, text: words, created: ISO8601DateFormatter.ab.string(from: Date()), user: me)
        draft = ""
        Haptics.tap()
        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { comments.append(temp) }
        changed(comments.count)
        do {
            _ = try await account.call("commentAdd", ["postId": post.id, "text": words])
            Haptics.success()
            await load()
        } catch {
            withAnimation { comments.removeAll { $0.id == temp.id } }
            changed(comments.count)
            if draft.isEmpty { draft = words }
            self.error = error.localizedDescription
            Haptics.error()
        }
    }

    private func remove(_ c: PostComment) async {
        withAnimation { comments.removeAll { $0.id == c.id } }
        changed(comments.count)
        _ = try? await account.call("commentDelete", ["id": c.id])
        await load()
    }
}
