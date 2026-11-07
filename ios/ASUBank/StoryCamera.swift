import SwiftUI
import AVFoundation
import PhotosUI
import UniformTypeIdentifiers

// The story camera: photos and short videos, at 60 frames per second when the phone's camera can do it.

enum StoryMedia {
    case photo(UIImage)
    case video(URL, poster: UIImage?, fromCamera: Bool)
}

struct StoryDraft: Identifiable {
    let id = UUID()
    var media: StoryMedia?
}

enum StoryVideo {
    static let maxSeconds = 30.0
    static let maxBytes = 12_000_000

    static func poster(_ url: URL) -> UIImage? {
        let g = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        g.appliesPreferredTrackTransform = true
        g.maximumSize = CGSize(width: 1280, height: 1280)
        guard let cg = try? g.copyCGImage(at: .zero, actualTime: nil) else { return nil }
        return UIImage(cgImage: cg)
    }

    /// An MP4 of at most 30 seconds that fits the upload limit. Camera videos are already small, so they are only rewrapped and keep their 60 FPS.
    static func prepare(_ url: URL, fromCamera: Bool) async -> URL? {
        let presets = fromCamera
            ? [AVAssetExportPresetPassthrough, AVAssetExportPreset1280x720, AVAssetExportPreset960x540]
            : [AVAssetExportPreset1280x720, AVAssetExportPreset960x540, AVAssetExportPresetMediumQuality]
        let asset = AVURLAsset(url: url)
        for preset in presets {
            guard let e = AVAssetExportSession(asset: asset, presetName: preset) else { continue }
            let out = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".mp4")
            e.outputURL = out
            e.outputFileType = .mp4
            e.shouldOptimizeForNetworkUse = true
            e.timeRange = CMTimeRange(start: .zero, duration: CMTime(seconds: maxSeconds, preferredTimescale: 600))
            await withCheckedContinuation { (done: CheckedContinuation<Void, Never>) in
                e.exportAsynchronously { done.resume() }
            }
            let size = (try? out.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? nil
            if e.status == .completed, let size, size > 0, size <= maxBytes { return out }
            try? FileManager.default.removeItem(at: out)
        }
        return nil
    }

    /// A story video saved on the phone, so it plays smoothly
    static func local(_ remote: URL) async -> URL? {
        let name = "story-" + String(abs(remote.absoluteString.hashValue)) + ".mp4"
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        if FileManager.default.fileExists(atPath: file.path) { return file }
        guard let (tmp, response) = try? await URLSession.shared.download(from: remote),
              (response as? HTTPURLResponse)?.statusCode ?? 200 < 400 else { return nil }
        try? FileManager.default.moveItem(at: tmp, to: file)
        return FileManager.default.fileExists(atPath: file.path) ? file : nil
    }
}

// MARK: - camera

final class StoryCameraModel: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate, AVCaptureFileOutputRecordingDelegate {
    let session = AVCaptureSession()
    @Published var blocked = false
    @Published var recording = false
    @Published var seconds = 0
    @Published var sixty = false
    var finished: ((StoryMedia) -> Void)?

    private let queue = DispatchQueue(label: "org.unihub.camera")
    private let photos = AVCapturePhotoOutput()
    private let movies = AVCaptureMovieFileOutput()
    private var videoInput: AVCaptureDeviceInput?
    private var audioInput: AVCaptureDeviceInput?
    private var position = AVCaptureDevice.Position.back
    private var videoMode = false
    private var timer: Timer?

    func start() {
        Task {
            guard await AVCaptureDevice.requestAccess(for: .video) else {
                await MainActor.run { self.blocked = true }
                return
            }
            _ = await AVCaptureDevice.requestAccess(for: .audio)
            self.queue.async {
                self.configure()
                if !self.session.isRunning { self.session.startRunning() }
            }
        }
    }

    func stop() {
        queue.async { if self.session.isRunning { self.session.stopRunning() } }
    }

    func setVideoMode(_ on: Bool) {
        queue.async {
            self.videoMode = on
            self.configure()
        }
    }

    func flip() {
        queue.async {
            self.position = self.position == .back ? .front : .back
            self.configure()
        }
    }

    private func configure() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
              let input = try? AVCaptureDeviceInput(device: device) else {
            DispatchQueue.main.async { self.blocked = true }
            return
        }
        session.beginConfiguration()
        if let old = videoInput { session.removeInput(old) }
        let preset: AVCaptureSession.Preset = videoMode ? .high : .photo
        if session.canSetSessionPreset(preset) { session.sessionPreset = preset }
        if session.canAddInput(input) {
            session.addInput(input)
            videoInput = input
        }
        if !session.outputs.contains(photos), session.canAddOutput(photos) { session.addOutput(photos) }
        if !session.outputs.contains(movies), session.canAddOutput(movies) { session.addOutput(movies) }
        if videoMode, audioInput == nil, AVCaptureDevice.authorizationStatus(for: .audio) == .authorized,
           let mic = AVCaptureDevice.default(for: .audio), let a = try? AVCaptureDeviceInput(device: mic), session.canAddInput(a) {
            session.addInput(a)
            audioInput = a
        }
        session.commitConfiguration()

        movies.maxRecordedDuration = CMTime(seconds: StoryVideo.maxSeconds, preferredTimescale: 600)
        if let c = movies.connection(with: .video) {
            if c.isVideoOrientationSupported { c.videoOrientation = .portrait }
            if movies.availableVideoCodecTypes.contains(.h264) {
                movies.setOutputSettings([
                    AVVideoCodecKey: AVVideoCodecType.h264,
                    AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 2_800_000],
                ], for: c)
            }
        }
        if let c = photos.connection(with: .video), c.isVideoOrientationSupported { c.videoOrientation = .portrait }
        let fast = videoMode ? useSixtyFPS(device) : false
        DispatchQueue.main.async { self.sixty = fast }
    }

    /// Picks a 720p (or else 1080p) mode that runs at 60 FPS, if this camera has one
    private func useSixtyFPS(_ device: AVCaptureDevice) -> Bool {
        func mode(_ height: Int32) -> AVCaptureDevice.Format? {
            device.formats.first { f in
                CMVideoFormatDescriptionGetDimensions(f.formatDescription).height == height
                    && f.videoSupportedFrameRateRanges.contains { $0.maxFrameRate >= 60 }
            }
        }
        guard let format = mode(720) ?? mode(1080), (try? device.lockForConfiguration()) != nil else { return false }
        device.activeFormat = format
        device.activeVideoMinFrameDuration = CMTime(value: 1, timescale: 60)
        device.activeVideoMaxFrameDuration = CMTime(value: 1, timescale: 60)
        device.unlockForConfiguration()
        return true
    }

    func takePhoto() {
        queue.async {
            guard self.session.isRunning, self.photos.connection(with: .video) != nil else { return }
            self.photos.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else { return }
        DispatchQueue.main.async { self.finished?(.photo(image)) }
    }

    func toggleRecording() {
        if recording {
            queue.async { self.movies.stopRecording() }
            return
        }
        recording = true
        seconds = 0
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.seconds += 1 }
        queue.async {
            let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".mov")
            self.movies.startRecording(to: file, recordingDelegate: self)
        }
    }

    func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo url: URL, from connections: [AVCaptureConnection], error: Error?) {
        let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? nil
        let poster = (size ?? 0) > 0 ? StoryVideo.poster(url) : nil
        DispatchQueue.main.async {
            self.timer?.invalidate()
            self.recording = false
            if (size ?? 0) > 0 { self.finished?(.video(url, poster: poster, fromCamera: true)) }
        }
    }
}

private final class CameraPreviewView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}

private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> CameraPreviewView {
        let v = CameraPreviewView()
        v.previewLayer.session = session
        v.previewLayer.videoGravity = .resizeAspectFill
        v.backgroundColor = .black
        return v
    }
    func updateUIView(_ uiView: CameraPreviewView, context: Context) {}
}

struct StoryCameraScreen: View {
    /// nil means a text story
    var done: (StoryMedia?) -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = StoryCameraModel()
    @State private var videoMode = false
    @State private var picking = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if model.blocked {
                VStack(spacing: 12) {
                    Image(systemName: "camera.fill").font(.system(size: 44)).foregroundStyle(.white.opacity(0.7))
                    Text("UniHub can't use the camera.").font(.headline).foregroundStyle(.white)
                    Text("You can allow it in Settings, or pick from your photos.").font(.subheadline).foregroundStyle(.white.opacity(0.75))
                        .multilineTextAlignment(.center).padding(.horizontal, 30)
                    Button("Open Settings") {
                        if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
                    }
                    .buttonStyle(.borderedProminent).tint(.abFill)
                }
            } else {
                CameraPreview(session: model.session).ignoresSafeArea()
            }
            VStack {
                HStack {
                    Button { dismiss() } label: { circle("xmark") }.accessibilityLabel(Text("Close"))
                    Spacer()
                    if model.recording {
                        Text(String(format: "%02d / %02d", model.seconds, Int(StoryVideo.maxSeconds)))
                            .font(.subheadline.weight(.bold)).foregroundStyle(.white)
                            .padding(.horizontal, 12).padding(.vertical, 6).background(Color.red, in: Capsule())
                    } else if videoMode && model.sixty {
                        Text("60 FPS").font(.caption.weight(.bold)).foregroundStyle(.white)
                            .padding(.horizontal, 10).padding(.vertical, 6).background(.black.opacity(0.45), in: Capsule())
                    }
                    Spacer()
                    Button { model.flip(); Haptics.tap() } label: { circle("arrow.triangle.2.circlepath.camera") }
                        .accessibilityLabel(Text("Switch camera"))
                }
                .padding(.horizontal, 14).padding(.top, 8)
                Spacer()
                HStack(spacing: 22) {
                    modeLabel("Photo", on: !videoMode) { setMode(false) }
                    modeLabel("Video", on: videoMode) { setMode(true) }
                }
                .padding(.bottom, 12)
                HStack {
                    Button { picking = true } label: { circle("photo.on.rectangle") }.accessibilityLabel(Text("Choose from photos"))
                    Spacer()
                    Button {
                        Haptics.tap()
                        if videoMode { model.toggleRecording() } else { model.takePhoto() }
                    } label: {
                        ZStack {
                            Circle().stroke(.white, lineWidth: 5).frame(width: 78, height: 78)
                            if model.recording {
                                RoundedRectangle(cornerRadius: 8).fill(Color.red).frame(width: 32, height: 32)
                            } else {
                                Circle().fill(videoMode ? Color.red : Color.white).frame(width: 62, height: 62)
                            }
                        }
                    }
                    .accessibilityLabel(Text(videoMode ? "Record" : "Take photo"))
                    Spacer()
                    Button { done(nil); dismiss() } label: {
                        Text("Aa").font(.headline).foregroundStyle(.white).frame(width: 46, height: 46)
                            .background(.black.opacity(0.45), in: Circle())
                    }
                    .accessibilityLabel(Text("Write a text story"))
                }
                .padding(.horizontal, 26).padding(.bottom, 16)
            }
        }
        .statusBar(hidden: true)
        .gesture(DragGesture(minimumDistance: 40).onEnded { v in
            guard abs(v.translation.width) > abs(v.translation.height), !model.recording else { return }
            setMode(v.translation.width < 0)
        })
        .onAppear {
            model.finished = { media in done(media); dismiss() }
            model.start()
        }
        .onDisappear { model.stop() }
        .sheet(isPresented: $picking) {
            StoryMediaPicker { media in done(media); dismiss() }
        }
    }

    private func setMode(_ on: Bool) {
        guard on != videoMode, !model.recording else { return }
        videoMode = on
        model.setVideoMode(on)
        Haptics.tap()
    }

    private func circle(_ symbol: String) -> some View {
        Image(systemName: symbol).font(.headline).foregroundStyle(.white).frame(width: 46, height: 46)
            .background(.black.opacity(0.45), in: Circle())
    }

    private func modeLabel(_ title: LocalizedStringKey, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.subheadline.weight(.bold)).foregroundStyle(on ? Color.yellow : Color.white)
                .padding(.horizontal, 12).padding(.vertical, 6).background(.black.opacity(0.35), in: Capsule())
        }
    }
}

/// Picks one photo or video from the library
struct StoryMediaPicker: UIViewControllerRepresentable {
    var picked: (StoryMedia) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var c = PHPickerConfiguration()
        c.filter = .any(of: [.images, .videos])
        c.selectionLimit = 1
        let p = PHPickerViewController(configuration: c)
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let parent: StoryMediaPicker
        init(_ p: StoryMediaPicker) { parent = p }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard let item = results.first?.itemProvider else { parent.dismiss(); return }
            let done = parent.picked
            if item.hasItemConformingToTypeIdentifier(UTType.movie.identifier) {
                item.loadFileRepresentation(forTypeIdentifier: UTType.movie.identifier) { url, _ in
                    guard let url else { return }
                    // the system removes its copy when this returns
                    let copy = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "." + url.pathExtension)
                    guard (try? FileManager.default.copyItem(at: url, to: copy)) != nil else { return }
                    let poster = StoryVideo.poster(copy)
                    DispatchQueue.main.async { done(.video(copy, poster: poster, fromCamera: false)) }
                }
            } else if item.canLoadObject(ofClass: UIImage.self) {
                item.loadObject(ofClass: UIImage.self) { object, _ in
                    guard let image = object as? UIImage else { return }
                    DispatchQueue.main.async { done(.photo(image)) }
                }
            }
            parent.dismiss()
        }
    }
}

// MARK: - video playback

private final class PlayerLayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

private struct PlayerLayer: UIViewRepresentable {
    let player: AVPlayer
    func makeUIView(context: Context) -> PlayerLayerView {
        let v = PlayerLayerView()
        v.playerLayer.player = player
        v.playerLayer.videoGravity = .resizeAspect
        return v
    }
    func updateUIView(_ uiView: PlayerLayerView, context: Context) {}
}

/// A story's video. Reports its length and how far it has played, so the progress bar follows the real video
/// and the next story starts only when this one has ended.
struct StoryVideoView: View {
    let url: URL
    let paused: Bool
    @Binding var duration: Double?
    @Binding var played: Double?
    var ended: () -> Void = {}
    @State private var player: AVPlayer?
    @State private var failed = false
    @State private var watch: Any?
    @State private var endWatch: NSObjectProtocol?

    var body: some View {
        Group {
            if let player {
                PlayerLayer(player: player)
            } else if failed {
                Image(systemName: "exclamationmark.triangle").font(.largeTitle).foregroundStyle(.white.opacity(0.7))
            } else {
                ABDots().tint(.white)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task(id: url) {
            player?.pause()
            player = nil
            failed = false
            guard let file = await StoryVideo.local(url), !Task.isCancelled else {
                failed = true
                duration = 6
                return
            }
            try? AVAudioSession.sharedInstance().setCategory(.playback)
            let asset = AVURLAsset(url: file)
            let length = (try? await asset.load(.duration))?.seconds ?? 6
            let item = AVPlayerItem(asset: asset)
            let p = AVPlayer(playerItem: item)
            p.actionAtItemEnd = .pause
            watch = p.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 20), queue: .main) { t in
                if t.seconds.isFinite { played = t.seconds }
            }
            endWatch = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { _ in ended() }
            player = p
            duration = length.isFinite && length > 0.5 ? length : 6
            if !paused { p.play() }
        }
        .onChange(of: paused) { on in if on { player?.pause() } else { player?.play() } }
        .onDisappear {
            player?.pause()
            if let watch { player?.removeTimeObserver(watch) }
            if let endWatch { NotificationCenter.default.removeObserver(endWatch) }
            watch = nil
            endWatch = nil
        }
    }
}
