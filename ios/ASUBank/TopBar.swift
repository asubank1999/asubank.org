import SwiftUI

// The Home top bar (your photo on the left, who's online and the bell on the right) and the who's online box.
// Apps only.

/// Who's online, shared by Home, Feed, Friends and Chat. The list needs an account; guests only see how many.
@MainActor final class OnlineStore: ObservableObject {
    static let shared = OnlineStore()
    @Published var users: [OnlineUser] = []
    @Published var count = 0
    @Published var loaded = false
    private var last = Date.distantPast

    func load(force: Bool = false) async {
        guard force || Date().timeIntervalSince(last) > 45 else { return }
        last = Date()
        if AccountStore.shared.signedIn {
            guard let r = try? await AccountStore.shared.call("online") else { return }
            users = decode([OnlineUser].self, r["users"]) ?? []
            count = (r["onlineCount"] as? NSNumber)?.intValue ?? users.filter(\.online).count
        } else {
            guard let r = try? await AccountsAPI.call("onlineCount") else { return }
            users = []
            count = (r["onlineCount"] as? NSNumber)?.intValue ?? 0
        }
        loaded = true
    }

    /// Keeps the number fresh while a screen shows it
    func keepFresh() async {
        while !Task.isCancelled {
            await load()
            try? await Task.sleep(nanoseconds: 60_000_000_000)
        }
    }
}

/// The online list in its own sheet, reachable from any top bar
struct OnlineSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = OnlineStore.shared
    var body: some View {
        ABStack {
            OnlineScreen(users: store.users)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
                .abDestinations()
        }
        .task { await store.load(force: true) }
    }
}

/// A green dot and how many are online, for top bars
struct OnlineButton: View {
    @ObservedObject private var store = OnlineStore.shared
    @ObservedObject private var account = AccountStore.shared
    @State private var shown = false
    var body: some View {
        Button {
            Haptics.tap()
            if account.signedIn { shown = true } else { account.signInShown = true }
        } label: {
            HStack(spacing: 4) {
                Circle().fill(Color.green).frame(width: 8, height: 8)
                Text("\(store.count)").font(.footnote.weight(.semibold)).monospacedDigit().foregroundStyle(.primary)
            }
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(Color.green.opacity(0.16), in: Capsule())
        }
        .accessibilityLabel(Text(String(format: L("%d online now"), store.count)))
        .task(id: account.signedIn) { await store.load(force: true); await store.keepFresh() }
        .sheet(isPresented: $shown) { OnlineSheet() }
    }
}

/// The box that says who is online: faces, how many, and a way to the full list
struct OnlineBox: View {
    @ObservedObject private var store = OnlineStore.shared
    @ObservedObject private var account = AccountStore.shared
    @State private var shown = false

    private var faces: [OnlineUser] { Array(store.users.filter(\.online).prefix(5)) }

    var body: some View {
        Button {
            Haptics.tap()
            if account.signedIn { shown = true } else { account.signInShown = true }
        } label: {
            HStack(spacing: 10) {
                if faces.isEmpty {
                    Image(systemName: "person.2.fill").font(.subheadline).foregroundStyle(Color.green)
                        .frame(width: 32, height: 32).background(Color.green.opacity(0.16), in: Circle())
                } else {
                    HStack(spacing: -9) {
                        ForEach(faces) { u in
                            Avatar(name: u.displayName, color: u.color ?? "#8C1D40", size: 32, photo: u.avatar)
                                .background(Circle().fill(Color.abSurface).padding(-2))
                        }
                    }
                    .environment(\.layoutDirection, .leftToRight)
                }
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 5) {
                        Circle().fill(Color.green).frame(width: 8, height: 8)
                        Text(String(format: L("%d online now"), store.count)).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                    }
                    Text(account.signedIn ? L("See everyone") : L("Sign in to see who")).font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.forward").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(10)
            .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .task(id: account.signedIn) { await store.load(force: true); await store.keepFresh() }
        .sheet(isPresented: $shown) { OnlineSheet() }
    }
}

/// Your photo, top left. Opens your profile, or sign in.
struct MeButton: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var shown = false
    var body: some View {
        Button {
            Haptics.tap()
            if account.signedIn { shown = true } else { account.signInShown = true }
        } label: {
            if let u = account.user {
                Avatar(name: u.displayName, color: u.color, size: 32, admin: u.isAdmin, photo: u.avatar)
            } else {
                Image(systemName: "person.crop.circle.fill").font(.title2).foregroundStyle(Color.abAccent)
            }
        }
        .accessibilityLabel(Text("My profile"))
        .sheet(isPresented: $shown) { MeSheet() }
    }
}

/// Your own profile in a sheet
private struct MeSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    var body: some View {
        ABStack {
            Group {
                if let u = account.user {
                    ProfileScreen(userId: u.id, fallbackName: u.displayName, fallbackColor: u.color)
                } else {
                    Color.clear
                }
            }
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .abDestinations()
        }
    }
}

// MARK: - cover photo

/// A picked photo waiting to be framed
struct PickedPhoto: Identifiable {
    let id = UUID()
    let image: UIImage
}

/// Move and zoom a picked photo inside the shape a cover photo has
struct CoverCropSheet: View {
    let image: UIImage
    var done: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var box = CGSize(width: 1, height: 1)

    /// Wide and short, like the top of a profile
    private let ratio: CGFloat = 2.4

    /// The photo's size on screen at the current zoom
    private func shown(_ b: CGSize, _ s: CGFloat) -> CGSize {
        let w = max(image.size.width, 1), h = max(image.size.height, 1)
        let k = max(b.width / w, b.height / h) * s
        return CGSize(width: w * k, height: h * k)
    }

    /// The photo always fills the frame
    private func clamp(_ o: CGSize, _ s: CGFloat) -> CGSize {
        let p = shown(box, s)
        let x = max(0, (p.width - box.width) / 2), y = max(0, (p.height - box.height) / 2)
        return CGSize(width: min(x, max(-x, o.width)), height: min(y, max(-y, o.height)))
    }

    var body: some View {
        ABStack {
            VStack(spacing: 18) {
                Spacer(minLength: 0)
                GeometryReader { geo in
                    let w = geo.size.width
                    let b = CGSize(width: w, height: w / ratio)
                    let p = shown(b, scale)
                    Image(uiImage: image)
                        .resizable()
                        .frame(width: p.width, height: p.height)
                        .offset(offset)
                        .frame(width: b.width, height: b.height)
                        .clipped()
                        .contentShape(Rectangle())
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Color.white, lineWidth: 2))
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .gesture(
                            DragGesture()
                                .onChanged { v in
                                    offset = clamp(CGSize(width: lastOffset.width + v.translation.width, height: lastOffset.height + v.translation.height), scale)
                                }
                                .onEnded { _ in lastOffset = offset }
                                .simultaneously(with:
                                    MagnificationGesture()
                                        .onChanged { v in
                                            scale = min(5, max(1, lastScale * v))
                                            offset = clamp(offset, scale)
                                        }
                                        .onEnded { _ in lastScale = scale; lastOffset = offset }
                                )
                        )
                        .onAppear { box = b }
                        .onChange(of: w) { _ in box = CGSize(width: w, height: w / ratio) }
                }
                .aspectRatio(ratio, contentMode: .fit)
                .padding(.horizontal, 16)
                // a photo doesn't flip in Arabic, and neither should dragging it
                .environment(\.layoutDirection, .leftToRight)
                Text("Drag to move, pinch to zoom").font(.subheadline).foregroundStyle(.secondary)
                HStack(spacing: 12) {
                    Image(systemName: "minus.magnifyingglass").foregroundStyle(.secondary)
                    Slider(value: Binding(get: { scale }, set: { v in
                        scale = v
                        lastScale = v
                        offset = clamp(offset, v)
                        lastOffset = offset
                    }), in: 1...5).tint(.abAccent)
                    Image(systemName: "plus.magnifyingglass").foregroundStyle(.secondary)
                }
                .padding(.horizontal, 24)
                Spacer(minLength: 0)
            }
            .background(Color.abBackground.ignoresSafeArea())
            .navigationTitle("Adjust cover photo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Use photo") {
                        done(rendered())
                        dismiss()
                    }
                    .font(.body.weight(.semibold))
                }
            }
        }
    }

    /// Draws what the frame shows. UIImage.draw keeps the photo the right way up.
    private func rendered() -> UIImage {
        let out = CGSize(width: 1280, height: (1280 / ratio).rounded())
        let k = out.width / max(box.width, 1)
        let p = shown(box, scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: out, format: format).image { _ in
            let w = p.width * k, h = p.height * k
            image.draw(in: CGRect(x: (out.width - w) / 2 + offset.width * k, y: (out.height - h) / 2 + offset.height * k, width: w, height: h))
        }
    }
}
