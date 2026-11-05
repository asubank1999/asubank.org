import SwiftUI
import AVFoundation

// 1.14.0: disappearing items, voice messages, photos and videos in chats, work that carries on in the background, and the loading effects.

// MARK: - disappearing

enum Disappear {
    static let choices: [(seconds: Int, label: String)] = [
        (0, "Don't disappear"), (60, "1 minute"), (3600, "60 minutes"), (86400, "1 day"), (604800, "1 week"),
    ]

    static func date(_ iso: String?) -> Date? {
        guard let iso, !iso.isEmpty else { return nil }
        return ISO8601DateFormatter.ab.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
    }

    static func expired(_ iso: String?) -> Bool {
        guard let d = date(iso) else { return false }
        return d <= Date()
    }

    /// "5m", "3h", "2d"
    static func left(_ iso: String?) -> String? {
        guard let d = date(iso) else { return nil }
        let s = max(0, Int(d.timeIntervalSinceNow))
        if s < 60 { return "\(max(1, s))s" }
        if s < 3600 { return "\(s / 60)m" }
        if s < 86400 { return "\(s / 3600)h" }
        return "\(s / 86400)d"
    }
}

/// The small timer button next to a message box or in a post
struct DisappearMenu: View {
    @Binding var seconds: Int
    var body: some View {
        Menu {
            Section("Disappears after") {
                ForEach(Disappear.choices, id: \.seconds) { c in
                    Button {
                        seconds = c.seconds
                        Haptics.tap()
                    } label: {
                        if seconds == c.seconds { Label(LocalizedStringKey(c.label), systemImage: "checkmark") } else { Text(LocalizedStringKey(c.label)) }
                    }
                }
            }
        } label: {
            Image(systemName: seconds > 0 ? "timer.circle.fill" : "timer")
                .font(.headline)
                .foregroundStyle(Color.abAccent)
                .frame(width: 38, height: 38)
                .background(seconds > 0 ? Color.abAccentSoft : Color.clear, in: Circle())
        }
        .accessibilityLabel(Text("Disappears after"))
    }
}

struct ExpiryTag: View {
    let expires: String?
    var tint: Color = .secondary
    var body: some View {
        if let left = Disappear.left(expires) {
            HStack(spacing: 2) {
                Image(systemName: "timer")
                Text(left)
            }
            .font(.caption2)
            .foregroundStyle(tint)
        }
    }
}

// MARK: - files

enum MediaFiles {
    /// A voice message, photo or video from a room or post, kept on the phone after the first time
    @MainActor static func local(scope: String, id: String, name: String) async throws -> URL {
        let safe = name.components(separatedBy: CharacterSet(charactersIn: "/\\:")).joined(separator: "_")
        let dir = DMFiles.folder.appendingPathComponent(id, isDirectory: true)
        let file = dir.appendingPathComponent(safe.isEmpty ? "file" : safe)
        if FileManager.default.fileExists(atPath: file.path) { return file }
        let r = try await AccountsAPI.call("mediaFile", ["scope": scope, "id": id], token: AccountStore.shared.token)
        guard let s = r["data"] as? String, let data = Data(base64Encoded: s) else {
            throw AccountsAPI.Failure(message: L("File not found."))
        }
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try data.write(to: file)
        return file
    }

    static func temp(_ ext: String) -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "." + ext)
    }
}

/// Something on its way to a chat. `load` does the slow part, so the message shows at once.
struct ChatOutgoing {
    var type: String
    var fileName: String
    var duration: Int = 0
    var size: Int = 0
    var localFile: URL?
    var load: () async -> String?
}

// MARK: - recording

@MainActor
final class VoiceRecorder: NSObject, ObservableObject {
    static let maxSeconds = 180
    @Published var recording = false
    @Published var seconds = 0
    @Published var level: CGFloat = 0
    private var rec: AVAudioRecorder?
    private var timer: Timer?
    private var started = Date()

    /// false when the microphone isn't allowed
    func start() async -> Bool {
        let session = AVAudioSession.sharedInstance()
        let allowed: Bool = await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
            session.requestRecordPermission { c.resume(returning: $0) }
        }
        guard allowed else { return false }
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true)
            let url = MediaFiles.temp("m4a")
            let r = try AVAudioRecorder(url: url, settings: [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 22050,
                AVNumberOfChannelsKey: 1,
                AVEncoderBitRateKey: 32000,
            ])
            r.isMeteringEnabled = true
            guard r.record() else { return false }
            rec = r
            started = Date()
            seconds = 0
            recording = true
            timer?.invalidate()
            timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.tick() }
            }
            return true
        } catch { return false }
    }

    private func tick() {
        guard let rec else { return }
        rec.updateMeters()
        level = CGFloat(max(0, (rec.averagePower(forChannel: 0) + 50) / 50))
        seconds = Int(Date().timeIntervalSince(started))
        if seconds >= Self.maxSeconds { finished = stopKeeping() }
    }

    /// Set when the limit stopped the recording by itself
    @Published var finished: (URL, Int)?

    private func stopKeeping() -> (URL, Int)? {
        guard let rec else { return nil }
        let length = Int(max(1, rec.currentTime.rounded()))
        let url = rec.url
        rec.stop()
        clean()
        return (url, length)
    }

    func stop() -> (URL, Int)? { stopKeeping() }

    func cancel() {
        rec?.stop()
        rec?.deleteRecording()
        clean()
    }

    private func clean() {
        rec = nil
        timer?.invalidate()
        timer = nil
        recording = false
        try? AVAudioSession.sharedInstance().setCategory(.playback)
    }
}

// MARK: - playing

@MainActor
final class VoicePlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    private static weak var current: VoicePlayer?
    @Published var playing = false
    @Published var progress: Double = 0
    private var player: AVAudioPlayer?
    private var timer: Timer?

    func toggle(_ url: URL) -> Bool {
        if playing { pause(); return true }
        if player == nil || player?.url != url {
            guard let p = try? AVAudioPlayer(contentsOf: url) else { return false }
            p.delegate = self
            player = p
        }
        Self.current?.pause()
        Self.current = self
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        player?.play()
        playing = true
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, let p = self.player, p.duration > 0 else { return }
                self.progress = p.currentTime / p.duration
            }
        }
        return true
    }

    func pause() {
        player?.pause()
        playing = false
        timer?.invalidate()
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            self.playing = false
            self.progress = 0
            self.timer?.invalidate()
        }
    }
}

/// A voice message: play button, bars that fill while it plays, and how long it is
struct VoiceBubble: View {
    let id: String
    let duration: Int
    var mine = false
    var localFile: URL?
    var fetch: () async throws -> URL
    @StateObject private var player = VoicePlayer()
    @State private var loading = false
    @State private var failed = false

    private var bars: [CGFloat] {
        var seed = id.utf8.reduce(UInt32(7)) { $0 &* 31 &+ UInt32($1) }
        return (0..<26).map { _ in
            seed = seed &* 1_664_525 &+ 1_013_904_223
            return 6 + CGFloat(seed >> 28)
        }
    }

    private var fg: Color { mine ? .white : .abAccent }

    var body: some View {
        HStack(spacing: 10) {
            Button { Task { await tap() } } label: {
                ZStack {
                    Circle().fill(mine ? Color.white.opacity(0.22) : Color.abAccentSoft)
                    if loading { ABDots().tint(fg).scaleEffect(0.6) } else {
                        Image(systemName: player.playing ? "pause.fill" : "play.fill").font(.subheadline.weight(.bold)).foregroundStyle(fg)
                    }
                }
                .frame(width: 38, height: 38)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(player.playing ? "Pause" : "Play voice message"))
            HStack(spacing: 2) {
                ForEach(bars.indices, id: \.self) { i in
                    Capsule()
                        .fill(fg.opacity(Double(i) / Double(bars.count) <= player.progress && player.progress > 0 ? 1 : 0.4))
                        .frame(width: 2.5, height: bars[i])
                }
            }
            .frame(height: 24)
            Text(failed ? "!" : VoiceBubble.time(duration)).font(.caption2.monospacedDigit()).foregroundStyle(mine ? .white.opacity(0.85) : .secondary)
        }
        .frame(minWidth: 190, alignment: .leading)
    }

    static func time(_ s: Int) -> String { String(format: "%d:%02d", s / 60, s % 60) }

    private func tap() async {
        loading = localFile == nil
        defer { loading = false }
        do {
            let url = try await resolved()
            failed = !player.toggle(url)
            Haptics.tap()
        } catch { failed = true }
    }

    private func resolved() async throws -> URL {
        if let localFile { return localFile }
        return try await fetch()
    }
}

/// A photo or video in a chat. Opens in the system preview.
struct ChatMediaTile: View {
    let type: String
    var localFile: URL?
    var fetch: () async throws -> URL
    var open: (URL) -> Void
    @State private var file: URL?
    @State private var image: UIImage?
    @State private var failed = false
    @State private var loading = false

    /// A video comes down only when it's tapped, so a chat doesn't use up mobile data
    private func tapped() {
        if let file { open(file); return }
        guard type == "video", !loading else { return }
        loading = true
        Task {
            if let got = try? await fetch() { file = got; open(got) } else { failed = true }
            loading = false
        }
    }

    var body: some View {
        Button { tapped() } label: {
            ZStack {
                Color.black.opacity(type == "video" ? 0.75 : 0.08)
                if let image { Image(uiImage: image).resizable().scaledToFill() }
                else if failed { Image(systemName: "exclamationmark.triangle").foregroundStyle(.secondary) }
                else if type != "video" && file == nil { ABDots().tint(Color.abAccent) }
                if type == "video" && loading { ABDots().tint(.white) }
                else if type == "video" {
                    Image(systemName: "play.circle.fill").font(.system(size: 44)).foregroundStyle(.white.opacity(0.92)).shadow(radius: 4)
                }
            }
            .frame(width: 220, height: type == "video" ? 280 : 220)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(type == "video" ? "Play video" : "View photo"))
        .task {
            if let localFile {
                file = localFile
                if type == "video" {
                    image = await Task.detached(priority: .utility) { StoryVideo.poster(localFile) }.value
                } else {
                    image = UIImage(contentsOfFile: localFile.path)
                }
            } else if type != "video" {
                guard let url = try? await fetch() else { failed = true; return }
                file = url
                image = UIImage(contentsOfFile: url.path)
            }
        }
    }
}

// MARK: - the message box with attachments and voice

struct ChatInputBar: View {
    @Binding var text: String
    var focused: FocusState<Bool>.Binding
    var placeholder: LocalizedStringKey = "Message"
    var sendSymbol = "arrow.up"
    var allowDocs = false
    var editing = false
    @Binding var disappear: Int
    var send: () -> Void
    var sendMedia: (ChatOutgoing) -> Void
    var fail: (String) -> Void

    @StateObject private var rec = VoiceRecorder()
    @State private var pickingPhoto = false
    @State private var pickingVideo = false
    @State private var pickingDoc = false

    private var empty: Bool { text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        Group {
            if rec.recording { recordingBar.transition(.move(edge: .bottom).combined(with: .opacity)) } else { normalBar.transition(.opacity) }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: rec.recording)
        .onChange(of: rec.finished?.0) { _ in
            if let f = rec.finished { deliver(f.0, f.1); rec.finished = nil }
        }
        .sheet(isPresented: $pickingPhoto) { AvatarPicker { sendPhoto($0) } }
        .sheet(isPresented: $pickingVideo) { StoryMediaPicker { m in pickedMedia(m) }.ignoresSafeArea() }
        .sheet(isPresented: $pickingDoc) { DocumentPicker { sendDoc($0) } }
    }

    private var normalBar: some View {
        HStack(spacing: 6) {
            if !editing {
                Menu {
                    Button { pickingPhoto = true } label: { Label("Photo", systemImage: "photo") }
                    Button { pickingVideo = true } label: { Label("Video", systemImage: "video") }
                    if allowDocs { Button { pickingDoc = true } label: { Label("PDF or Word file", systemImage: "doc") } }
                } label: {
                    Image(systemName: "plus").font(.headline).foregroundStyle(Color.abAccent).frame(width: 38, height: 38)
                        .background(Color.abAccentSoft, in: Circle())
                }
                .accessibilityLabel(Text("Attach a file"))
                DisappearMenu(seconds: $disappear)
            }
            TextField(placeholder, text: $text)
                .focused(focused)
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(Color.abSurface, in: Capsule())
                .overlay(Capsule().stroke(Color.secondary.opacity(0.2), lineWidth: 1))
                .submitLabel(.send)
                .onSubmit(send)
            if empty && !editing {
                Button { Task { await startRecording() } } label: {
                    Image(systemName: "mic.fill").font(.headline).foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(Color.abAccent.abGradient, in: Circle())
                }
                .accessibilityLabel(Text("Record a voice message"))
                .transition(.scale.combined(with: .opacity))
            } else {
                Button(action: send) {
                    Image(systemName: sendSymbol).font(.headline.weight(.bold)).foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(Color.abAccent.abGradient, in: Circle())
                        .opacity(empty ? 0.4 : 1)
                }
                .disabled(empty)
                .accessibilityLabel(Text("Send"))
                .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.25, dampingFraction: 0.8), value: empty)
        .padding(.horizontal, 10).padding(.vertical, 8)
    }

    private var recordingBar: some View {
        HStack(spacing: 12) {
            Button { rec.cancel(); Haptics.tap() } label: {
                Image(systemName: "trash").font(.headline).foregroundStyle(.red).frame(width: 38, height: 38)
            }
            .accessibilityLabel(Text("Cancel"))
            Circle().fill(.red).frame(width: 10, height: 10).scaleEffect(0.8 + rec.level * 0.8)
            Text(VoiceBubble.time(rec.seconds)).font(.body.monospacedDigit().weight(.semibold))
            HStack(spacing: 2) {
                ForEach(0..<18, id: \.self) { i in
                    Capsule().fill(Color.abAccent.opacity(0.75))
                        .frame(width: 3, height: 4 + rec.level * CGFloat(6 + (i * 7) % 18))
                }
            }
            .frame(maxWidth: .infinity)
            .animation(.linear(duration: 0.1), value: rec.level)
            Button {
                if let f = rec.stop() { deliver(f.0, f.1) }
                Haptics.success()
            } label: {
                Image(systemName: "arrow.up").font(.headline.weight(.bold)).foregroundStyle(.white)
                    .frame(width: 38, height: 38).background(Color.abAccent.abGradient, in: Circle())
            }
            .accessibilityLabel(Text("Send"))
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
    }

    private func startRecording() async {
        focused.wrappedValue = false
        Haptics.tap()
        if !(await rec.start()) { fail(L("UniHub can't use the microphone. You can allow it in Settings.")) }
    }

    private func deliver(_ url: URL, _ length: Int) {
        let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        sendMedia(ChatOutgoing(type: "voice", fileName: "voice.m4a", duration: length, size: size, localFile: url) {
            guard let d = try? Data(contentsOf: url) else { return nil }
            return d.base64EncodedString()
        })
    }

    private func sendPhoto(_ image: UIImage) {
        let local = MediaFiles.temp("jpg")
        try? image.jpegData(compressionQuality: 0.7)?.write(to: local)
        sendMedia(ChatOutgoing(type: "photo", fileName: "photo.jpg", localFile: local) {
            await Task.detached(priority: .userInitiated) { PhotoTools.jpegBase64(image, maxSide: 1800) }.value
        })
    }

    private func pickedMedia(_ m: StoryMedia) {
        switch m {
        case .photo(let image):
            sendPhoto(image)
        case .video(let url, _, let camera):
            sendMedia(ChatOutgoing(type: "video", fileName: "video.mp4", localFile: url) {
                guard let ready = await StoryVideo.prepare(url, fromCamera: camera), let d = try? Data(contentsOf: ready) else { return nil }
                return d.base64EncodedString()
            })
        }
    }

    private func sendDoc(_ url: URL) {
        guard let data = try? Data(contentsOf: url) else { fail(L("Couldn't open this file.")); return }
        guard data.count <= 8_000_000 else { fail(L("That file is too big. The limit is 8 MB.")); return }
        sendMedia(ChatOutgoing(type: url.pathExtension.lowercased() == "pdf" ? "pdf" : "docx", fileName: url.lastPathComponent, size: data.count) {
            data.base64EncodedString()
        })
    }
}

// MARK: - background work

/// Posting keeps going while the student does something else. A strip at the top shows it, and offers a retry if it failed.
@MainActor
final class BackgroundJobs: ObservableObject {
    static let shared = BackgroundJobs()

    struct Job: Identifiable {
        let id = UUID()
        let title: String
        var failed: String?
        var success = false
        let run: () async throws -> Void
        let done: () async -> Void
    }

    @Published var jobs: [Job] = []

    func add(title: String, done: @escaping () async -> Void = {}, run: @escaping () async throws -> Void) {
        let job = Job(title: title, run: run, done: done)
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { jobs.append(job) }
        start(job.id)
    }

    func retry(_ id: UUID) {
        guard let i = jobs.firstIndex(where: { $0.id == id }) else { return }
        jobs[i].failed = nil
        start(id)
    }

    func dismiss(_ id: UUID) {
        withAnimation { jobs.removeAll { $0.id == id } }
    }

    private func start(_ id: UUID) {
        guard let job = jobs.first(where: { $0.id == id }) else { return }
        Task {
            let holder = TaskHolder()
            holder.task = UIApplication.shared.beginBackgroundTask { UIApplication.shared.endBackgroundTask(holder.task) }
            defer { UIApplication.shared.endBackgroundTask(holder.task) }
            do {
                try await job.run()
                Haptics.success()
                if let i = jobs.firstIndex(where: { $0.id == id }) {
                    withAnimation { jobs[i].success = true }
                }
                await job.done()
                try? await Task.sleep(nanoseconds: 1_200_000_000)
                dismiss(id)
            } catch {
                Haptics.error()
                if let i = jobs.firstIndex(where: { $0.id == id }) {
                    withAnimation { jobs[i].failed = error.localizedDescription }
                }
            }
        }
    }
}

private final class TaskHolder { var task: UIBackgroundTaskIdentifier = .invalid }

struct JobsBanner: View {
    @ObservedObject private var jobs = BackgroundJobs.shared
    var body: some View {
        VStack(spacing: 6) {
            ForEach(jobs.jobs) { j in
                HStack(spacing: 10) {
                    if j.success { Image(systemName: "checkmark.circle.fill").foregroundStyle(.green) }
                    else if j.failed != nil { Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange) }
                    else { ABDots().tint(Color.abAccent).scaleEffect(0.7).frame(width: 34) }
                    VStack(alignment: .leading, spacing: 1) {
                        Text(j.success ? L("Done") : j.title).font(.footnote.weight(.semibold)).lineLimit(1)
                        if let f = j.failed { Text(f).font(.caption2).foregroundStyle(.secondary).lineLimit(2) }
                    }
                    Spacer(minLength: 0)
                    if j.failed != nil {
                        Button("Try again") { jobs.retry(j.id) }.font(.footnote.weight(.semibold))
                        Button { jobs.dismiss(j.id) } label: { Image(systemName: "xmark").font(.caption) }.accessibilityLabel(Text("Close"))
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(.regularMaterial, in: Capsule())
                .shadow(color: .black.opacity(0.15), radius: 8, y: 3)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .padding(.horizontal, 16)
    }
}

// MARK: - loading effects

/// Three dots in the accent color, used wherever something is on its way
struct ABDots: View {
    var title: LocalizedStringKey?
    @State private var on = false

    init(_ title: LocalizedStringKey? = nil) { self.title = title }

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 5) {
                ForEach(0..<3, id: \.self) { i in
                    Circle().fill(.tint).frame(width: 8, height: 8)
                        .scaleEffect(on ? 1 : 0.45)
                        .opacity(on ? 1 : 0.35)
                        .animation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true).delay(Double(i) * 0.16), value: on)
                }
            }
            if let title { Text(title).font(.footnote).foregroundStyle(.secondary) }
        }
        .onAppear { on = true }
        .accessibilityLabel(Text("Loading"))
    }
}

struct Shimmer: ViewModifier {
    @State private var x: CGFloat = -1
    func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { g in
                    LinearGradient(colors: [.clear, .white.opacity(0.28), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: g.size.width * 0.7)
                        .offset(x: x * g.size.width)
                }
                .allowsHitTesting(false)
            )
            .clipped()
            .onAppear { withAnimation(.linear(duration: 1.15).repeatForever(autoreverses: false)) { x = 1.3 } }
    }
}

extension View {
    func shimmer() -> some View { modifier(Shimmer()) }
}

/// Grey shapes in the form of what is coming, so the screen feels ready straight away
struct SkeletonPost: View {
    private let fill = Color.secondary.opacity(0.16)
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Circle().fill(fill).frame(width: 38, height: 38)
                VStack(alignment: .leading, spacing: 6) {
                    Capsule().fill(fill).frame(width: 120, height: 10)
                    Capsule().fill(fill).frame(width: 70, height: 8)
                }
            }
            Capsule().fill(fill).frame(height: 10)
            Capsule().fill(fill).frame(width: 200, height: 10)
            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(fill).frame(height: 170)
        }
        .padding(.vertical, 6)
        .shimmer()
    }
}

struct SkeletonRow: View {
    private let fill = Color.secondary.opacity(0.16)
    var body: some View {
        HStack(spacing: 12) {
            Circle().fill(fill).frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 7) {
                Capsule().fill(fill).frame(width: 140, height: 11)
                Capsule().fill(fill).frame(width: 200, height: 9)
            }
            Spacer()
        }
        .padding(.vertical, 4)
        .shimmer()
    }
}

struct SkeletonBubbles: View {
    private let fill = Color.secondary.opacity(0.16)
    private let widths: [(CGFloat, Bool)] = [(170, false), (120, false), (210, true), (140, false), (90, true), (190, false), (150, true)]
    var body: some View {
        VStack(spacing: 10) {
            ForEach(widths.indices, id: \.self) { i in
                HStack {
                    if widths[i].1 { Spacer() }
                    RoundedRectangle(cornerRadius: 18, style: .continuous).fill(fill).frame(width: widths[i].0, height: 38)
                    if !widths[i].1 { Spacer() }
                }
            }
        }
        .padding(.top, 14)
        .shimmer()
    }
}

// MARK: - share the app

struct UHShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

/// The row in More. The link comes from app-config.json, so the store pages can be set there later without an update.
struct ShareAppRow: View {
    @EnvironmentObject private var config: AppConfig
    @State private var showing = false
    var body: some View {
        Button { showing = true; Haptics.tap() } label: { Label("Share UniHub", systemImage: "square.and.arrow.up") }
            .sheet(isPresented: $showing) {
                UHShareSheet(items: [L("Summaries, past papers and study tools for university students. Get UniHub:"), config.shareURL]).ignoresSafeArea()
            }
    }
}

// MARK: - restart after a theme change

enum AppRestart {
    /// iOS doesn't let an app open itself again, so it closes and the student taps the icon
    static func now() {
        UserDefaults.standard.synchronize()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { exit(0) }
    }
}

/// What a message carries: a voice message, or a photo or video tile
struct ChatAttachment: View {
    let id: String
    let type: String
    var duration = 0
    var mine = false
    var localFile: URL?
    var fetch: () async throws -> URL
    var open: (URL) -> Void

    var body: some View {
        if type == "voice" {
            VoiceBubble(id: id, duration: duration, mine: mine, localFile: localFile, fetch: fetch)
        } else {
            ChatMediaTile(type: type, localFile: localFile, fetch: fetch, open: open)
        }
    }
}
