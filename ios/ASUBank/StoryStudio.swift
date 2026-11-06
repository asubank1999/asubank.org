import SwiftUI
import AVFoundation

// 1.14.0: the new screen for making a story, text styles and effects, and who saw a story.

struct StoryStyle: Equatable {
    var font = "classic"
    var effect = "shadow"
    var color = "#FFFFFF"
    var bg2 = ""
    var align = "center"
    var anim = "none"

    init() {}

    init(json: String?) {
        guard let d = json?.data(using: .utf8), let o = try? JSONSerialization.jsonObject(with: d) as? [String: String] else { return }
        font = o["font"] ?? font
        effect = o["effect"] ?? effect
        color = o["color"] ?? color
        bg2 = o["bg2"] ?? bg2
        align = o["align"] ?? align
        anim = o["anim"] ?? anim
    }

    var fields: [String: String] { ["font": font, "effect": effect, "color": color, "bg2": bg2, "align": align, "anim": anim] }

    static let fonts: [(id: String, name: String)] = [
        ("classic", "Classic"), ("modern", "Modern"), ("serif", "Serif"), ("mono", "Typewriter"),
        ("hand", "Handwriting"), ("script", "Script"), ("tall", "Tall"), ("round", "Round"),
    ]
    static let effects: [(id: String, name: String)] = [
        ("none", "Plain"), ("shadow", "Shadow"), ("outline", "Outline"), ("neon", "Neon"),
        ("box", "Label"), ("retro", "Retro"), ("rainbow", "Rainbow"), ("glow", "Glow"),
    ]
    static let anims: [(id: String, name: String)] = [("none", "Still"), ("pop", "Pop"), ("pulse", "Pulse"), ("float", "Float"), ("fade", "Fade")]
    static let colors = ["#FFFFFF", "#000000", "#FFD60A", "#FF9F0A", "#FF453A", "#FF375F", "#FF6F9A", "#BF5AF2", "#8B5CF6", "#5E5CE6", "#0A84FF", "#64D2FF", "#30D158", "#00C7BE", "#A2845E", "#C7C7CC"]
    static let backgrounds: [(String, String)] = [
        ("#0B1560", ""), ("#8C1D40", ""), ("#0A63C9", ""), ("#1F7A3A", ""), ("#6E3FD1", ""), ("#D2510A", ""), ("#00847F", ""), ("#C2185B", ""), ("#3A3A3C", ""), ("#000000", ""),
        ("#0B1560", "#8B5CF6"), ("#FF375F", "#FF9F0A"), ("#5E5CE6", "#64D2FF"), ("#30D158", "#00C7BE"), ("#BF5AF2", "#FF6F9A"), ("#FF9F0A", "#FFD60A"), ("#1C1C1E", "#5E5CE6"), ("#8C1D40", "#0B1560"),
    ]

    func uiFont(_ size: CGFloat) -> Font {
        switch font {
        case "modern": return .custom("AvenirNext-Heavy", size: size)
        case "serif": return .custom("Georgia-Bold", size: size)
        case "mono": return .custom("AmericanTypewriter-Bold", size: size)
        case "hand": return .custom("Noteworthy-Bold", size: size)
        case "script": return .custom("SnellRoundhand-Black", size: size * 1.2)
        case "tall": return .custom("Futura-CondensedExtraBold", size: size * 1.1)
        case "round": return .system(size: size, weight: .heavy, design: .rounded)
        default: return .system(size: size, weight: .bold)
        }
    }
}

struct StoryBackground: View {
    let bg: String
    let bg2: String
    var body: some View {
        if bg2.isEmpty { Color(hexString: bg) } else {
            LinearGradient(colors: [Color(hexString: bg), Color(hexString: bg2)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }
}

/// Story text with its font, colour, effect and motion
struct StoryTextView: View {
    let text: String
    let style: StoryStyle
    var size: CGFloat = 28
    @State private var moving = false
    @State private var hue = 0.0

    private var color: Color { Color(hexString: style.color) }
    private var alignment: TextAlignment { style.align == "left" ? .leading : style.align == "right" ? .trailing : .center }

    var body: some View {
        styled
            .multilineTextAlignment(alignment)
            .frame(maxWidth: .infinity, alignment: style.align == "left" ? .leading : style.align == "right" ? .trailing : .center)
            .padding(.horizontal, 22)
            .scaleEffect(scale)
            .offset(y: style.anim == "float" ? (moving ? -10 : 10) : 0)
            .opacity(style.anim == "fade" ? (moving ? 1 : 0.25) : 1)
            // tied to these values, so it can't catch layout changes around the text
            .animation(motion, value: moving)
            .animation(style.effect == "rainbow" ? .linear(duration: 3).repeatForever(autoreverses: false) : nil, value: hue)
            .onAppear {
                if motion != nil { moving = true }
                if style.effect == "rainbow" { hue = 360 }
            }
            .id(style.anim + style.effect)
    }

    private var motion: Animation? {
        switch style.anim {
        case "pop": return .spring(response: 0.45, dampingFraction: 0.5)
        case "pulse": return .easeInOut(duration: 0.7).repeatForever(autoreverses: true)
        case "float": return .easeInOut(duration: 1.6).repeatForever(autoreverses: true)
        case "fade": return .easeInOut(duration: 1.2).repeatForever(autoreverses: true)
        default: return nil
        }
    }

    private var scale: CGFloat {
        switch style.anim {
        case "pop": return moving ? 1 : 0.3
        case "pulse": return moving ? 1.07 : 0.96
        default: return 1
        }
    }

    @ViewBuilder private var styled: some View {
        let t = Text(text).font(style.uiFont(size))
        switch style.effect {
        case "none":
            t.foregroundStyle(color)
        case "outline":
            let edge: Color = style.color == "#000000" ? .white : .black
            t.foregroundStyle(color)
                .shadow(color: edge, radius: 0, x: 1.5, y: 1.5).shadow(color: edge, radius: 0, x: -1.5, y: -1.5)
                .shadow(color: edge, radius: 0, x: 1.5, y: -1.5).shadow(color: edge, radius: 0, x: -1.5, y: 1.5)
        case "neon":
            t.foregroundStyle(.white).shadow(color: color, radius: 3).shadow(color: color, radius: 9).shadow(color: color, radius: 18)
        case "box":
            t.foregroundStyle(style.color == "#000000" ? Color.white : .black)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(color, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        case "retro":
            t.foregroundStyle(color).shadow(color: Color(hexString: "#FF375F"), radius: 0, x: 3, y: 3).shadow(color: Color(hexString: "#64D2FF"), radius: 0, x: 6, y: 6)
        case "rainbow":
            t.foregroundStyle(Color(hexString: "#FF375F")).hueRotation(.degrees(hue))
        case "glow":
            t.foregroundStyle(color).shadow(color: .white.opacity(0.9), radius: 12)
        default:
            t.foregroundStyle(color).shadow(color: .black.opacity(0.7), radius: 4)
        }
    }
}

private final class LoopPlayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    var looper: AVPlayerLooper?
}

/// The video playing over and over behind the tools
struct LoopingVideo: UIViewRepresentable {
    let url: URL
    var muted: Bool
    func makeUIView(context: Context) -> UIView {
        let v = LoopPlayerView()
        let q = AVQueuePlayer()
        v.looper = AVPlayerLooper(player: q, templateItem: AVPlayerItem(url: url))
        v.playerLayer.player = q
        v.playerLayer.videoGravity = .resizeAspect
        q.isMuted = muted
        q.play()
        return v
    }
    func updateUIView(_ uiView: UIView, context: Context) {
        (uiView as? LoopPlayerView)?.playerLayer.player?.isMuted = muted
    }
    static func dismantleUIView(_ uiView: UIView, coordinator: ()) {
        (uiView as? LoopPlayerView)?.playerLayer.player?.pause()
    }
}

struct ComposeStorySheet: View {
    var media: StoryMedia?
    var posted: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @AppStorage("ab.audience") private var audience = "public"
    @State private var text = ""
    @State private var photo: UIImage?
    @State private var video: URL?
    @State private var fromCamera = false
    @State private var poster: UIImage?
    @State private var picking = false
    @State private var bg = "#0B1560"
    @State private var style = StoryStyle()
    @State private var tool = 0
    @State private var muted = false
    @FocusState private var typing: Bool

    init(media: StoryMedia? = nil, posted: @escaping () async -> Void) {
        self.media = media
        self.posted = posted
        switch media {
        case .photo(let image):
            _photo = State(initialValue: image)
        case .video(let url, let still, let camera):
            _video = State(initialValue: url)
            _poster = State(initialValue: still)
            _fromCamera = State(initialValue: camera)
        case nil:
            break
        }
    }

    private var hasMedia: Bool { photo != nil || video != nil }
    private var canPost: Bool { hasMedia || !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var tools: [LocalizedStringKey] { hasMedia ? ["Font", "Effect", "Color", "Motion"] : ["Background", "Font", "Effect", "Color", "Motion"] }
    private var toolOffset: Int { hasMedia ? 1 : 0 }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            preview
            VStack(spacing: 0) {
                topBar
                Spacer()
                StoryTextView(text: text.isEmpty && !hasMedia ? L("Write something") : text, style: style, size: 30)
                    .opacity(text.isEmpty && !hasMedia ? 0.55 : 1)
                    .allowsHitTesting(false)
                Spacer()
                panel
            }
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $picking) {
            StoryMediaPicker { m in
                switch m {
                case .photo(let image): photo = image; video = nil; poster = nil
                case .video(let url, let still, let camera): video = url; poster = still; fromCamera = camera; photo = nil
                }
                tool = 0
            }
            .ignoresSafeArea()
        }
    }

    @ViewBuilder private var preview: some View {
        if let video {
            LoopingVideo(url: video, muted: muted).ignoresSafeArea()
        } else if let photo {
            Image(uiImage: photo).resizable().scaledToFit().ignoresSafeArea()
        } else {
            StoryBackground(bg: bg, bg2: style.bg2).ignoresSafeArea()
        }
    }

    private func round(_ symbol: String, _ label: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.headline).foregroundStyle(.white).frame(width: 40, height: 40)
                .background(.black.opacity(0.45), in: Circle())
        }
        .accessibilityLabel(Text(label))
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            round("xmark", "Cancel") { dismiss() }
            Spacer()
            if video != nil { round(muted ? "speaker.slash.fill" : "speaker.wave.2.fill", "Sound") { muted.toggle() } }
            round(hasMedia ? "photo.badge.arrow.down" : "photo", hasMedia ? "Change photo" : "Add a photo") { picking = true }
            if hasMedia { round("trash", video != nil ? "Remove video" : "Remove photo") { photo = nil; video = nil; poster = nil; tool = 0 } }
        }
        .padding(.horizontal, 14).padding(.top, 8)
    }

    private var panel: some View {
        VStack(spacing: 10) {
            TextField("Write something", text: $text)
                .focused($typing)
                .padding(.horizontal, 16).padding(.vertical, 11)
                .background(.white.opacity(0.14), in: Capsule())
                .foregroundStyle(.white)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(tools.indices, id: \.self) { i in
                        Button { withAnimation(.easeOut(duration: 0.15)) { tool = i } } label: {
                            Text(tools[i]).font(.footnote.weight(.semibold))
                                .foregroundStyle(tool == i ? Color.black : Color.white)
                                .padding(.horizontal, 13).padding(.vertical, 7)
                                .background(tool == i ? Color.white : Color.white.opacity(0.14), in: Capsule())
                        }
                    }
                    Divider().frame(height: 18).overlay(Color.white.opacity(0.3))
                    alignButton
                }
            }
            ScrollView(.horizontal, showsIndicators: false) { toolRow.padding(.vertical, 3) }.frame(height: 50)
            HStack(spacing: 10) {
                Menu {
                    Picker("Who can see this", selection: $audience) {
                        Label("Everyone, also guests", systemImage: "globe").tag("public")
                        Label("ASU students", systemImage: "graduationcap").tag("students")
                        Label("Friends only", systemImage: "person.2").tag("friends")
                    }
                } label: {
                    Image(systemName: Social.audienceSymbol(audience)).font(.headline).foregroundStyle(.white)
                        .frame(width: 46, height: 46).background(.white.opacity(0.14), in: Circle())
                }
                .accessibilityLabel(Text("Who can see this"))
                Button { post() } label: {
                    Label("Share story", systemImage: "paperplane.fill").font(.headline).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).frame(height: 46)
                        .background(Color.abFill.abGradient, in: Capsule())
                        .opacity(canPost ? 1 : 0.45)
                }
                .disabled(!canPost)
            }
            Text("Your story disappears after 24 hours and is deleted from our database after 2 days.")
                .font(.caption2).foregroundStyle(.white.opacity(0.65)).multilineTextAlignment(.center)
        }
        .padding(14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(.horizontal, 8).padding(.bottom, 6)
    }

    private var alignButton: some View {
        Button {
            style.align = style.align == "center" ? "left" : style.align == "left" ? "right" : "center"
            Haptics.tap()
        } label: {
            Image(systemName: style.align == "left" ? "text.alignleft" : style.align == "right" ? "text.alignright" : "text.aligncenter")
                .font(.subheadline).foregroundStyle(.white).frame(width: 34, height: 32)
                .background(Color.white.opacity(0.14), in: Capsule())
        }
        .accessibilityLabel(Text("Text alignment"))
    }

    private func chip(_ name: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button { action(); Haptics.tap() } label: {
            Text(LocalizedStringKey(name)).font(.footnote.weight(.semibold))
                .foregroundStyle(on ? Color.black : Color.white)
                .padding(.horizontal, 14).frame(height: 38)
                .background(on ? Color.white : Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    @ViewBuilder private var toolRow: some View {
        HStack(spacing: 8) {
            switch tool + toolOffset {
            case 0:
                ForEach(StoryStyle.backgrounds.indices, id: \.self) { i in
                    let p = StoryStyle.backgrounds[i]
                    Button { bg = p.0; style.bg2 = p.1; Haptics.tap() } label: {
                        StoryBackground(bg: p.0, bg2: p.1).frame(width: 40, height: 40).clipShape(Circle())
                            .overlay(Circle().stroke(Color.white, lineWidth: bg == p.0 && style.bg2 == p.1 ? 3 : 0.5))
                    }
                }
            case 1:
                ForEach(StoryStyle.fonts, id: \.id) { f in
                    Button { style.font = f.id; Haptics.tap() } label: {
                        Text(LocalizedStringKey(f.name)).font(styleFont(f.id))
                            .foregroundStyle(style.font == f.id ? Color.black : Color.white)
                            .padding(.horizontal, 14).frame(height: 38)
                            .background(style.font == f.id ? Color.white : Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
            case 2:
                ForEach(StoryStyle.effects, id: \.id) { e in chip(e.name, on: style.effect == e.id) { style.effect = e.id } }
            case 3:
                ForEach(StoryStyle.colors, id: \.self) { c in
                    Button { style.color = c; Haptics.tap() } label: {
                        Circle().fill(Color(hexString: c)).frame(width: 36, height: 36)
                            .overlay(Circle().stroke(Color.white, lineWidth: style.color == c ? 3 : 0.5))
                    }
                }
            default:
                ForEach(StoryStyle.anims, id: \.id) { a in chip(a.name, on: style.anim == a.id) { style.anim = a.id } }
            }
        }
    }

    private func styleFont(_ id: String) -> Font {
        var s = StoryStyle()
        s.font = id
        return s.uiFont(15)
    }

    /// The sheet closes straight away; the upload carries on in the background
    private func post() {
        let words = String(text.prefix(300))
        let background = bg
        let look = style.fields
        let who = audience
        let shot = photo
        let clip = video
        let still = poster
        let camera = fromCamera
        BackgroundJobs.shared.add(title: L("Posting your story…"), done: posted) {
            var fields: [String: Any] = ["text": words, "bg": background, "audience": who, "style": look]
            if let clip {
                guard let ready = await StoryVideo.prepare(clip, fromCamera: camera), let bytes = try? Data(contentsOf: ready) else {
                    throw AccountsAPI.Failure(message: L("That video is too big. Keep it short."))
                }
                fields["video"] = bytes.base64EncodedString()
                if let still {
                    fields["image"] = await Task.detached(priority: .userInitiated) { PhotoTools.jpegBase64(still, maxSide: 900) }.value
                }
            } else if let shot {
                let data = await Task.detached(priority: .userInitiated) { PhotoTools.jpegBase64(shot, maxSide: 1280) }.value
                guard let data else { throw AccountsAPI.Failure(message: L("Couldn't load this photo")) }
                fields["image"] = data
            }
            _ = try await AccountStore.shared.call("storyCreate", fields)
        }
        dismiss()
    }
}

/// Who saw a story, with the exact date and time
struct StoryViewersSheet: View {
    let storyId: String
    @Environment(\.dismiss) private var dismiss
    @State private var viewers: [Viewer] = []
    @State private var loaded = false
    @State private var error: String?

    struct Viewer: Decodable, Identifiable {
        let user: ABUser
        let at: String?
        var id: String { user.id }
    }

    var body: some View {
        ABStack {
            List {
                if !loaded {
                    ForEach(0..<5, id: \.self) { _ in SkeletonRow().listRowSeparator(.hidden) }
                } else if let error {
                    Text(error).foregroundStyle(.red)
                } else if viewers.isEmpty {
                    EmptyState(symbol: "eye", title: "No views yet", text: "People who see your story show up here.").listRowBackground(Color.clear)
                }
                ForEach(viewers) { v in
                    NavigationLink { ProfileScreen(userId: v.user.id, fallbackName: v.user.displayName, fallbackColor: v.user.color) } label: {
                        HStack(spacing: 12) {
                            Avatar(name: v.user.displayName, color: v.user.color, size: 44, admin: v.user.isAdmin, photo: v.user.avatar, peek: false)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(v.user.displayName).font(.body.weight(.semibold)).lineLimit(1)
                                if let d = Disappear.date(v.at) {
                                    Text(d.formatted(date: .long, time: .standard)).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("Viewers")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .task {
                do {
                    let r = try await AccountStore.shared.call("storyViewers", ["id": storyId])
                    withAnimation { viewers = decode([Viewer].self, r["viewers"]) ?? [] }
                } catch { self.error = error.localizedDescription }
                loaded = true
            }
        }
    }
}
