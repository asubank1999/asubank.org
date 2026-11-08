import AVFoundation
import CoreImage
import Photos
import UIKit

/// Saves a story's photo or video to the phone with a UniHub watermark (logo, name and the owner's @username)
/// in the corner, like TikTok, so a saved story always shows where it came from.
enum StorySave {
    enum Failure: LocalizedError {
        case denied, download, export
        var errorDescription: String? {
            switch self {
            case .denied: return L("Allow UniHub to add photos in Settings to save stories.")
            case .download: return L("Couldn't download the story. Try again.")
            case .export: return L("Couldn't save the story. Try again.")
            }
        }
    }

    /// The watermark, `width` points wide: logo, "UniHub" and @username, white with a soft shadow
    static func watermark(width: CGFloat, username: String) -> UIImage {
        let logoSide = width * 0.26
        let height = logoSide * 1.1
        let size = CGSize(width: width, height: height)
        let r = UIGraphicsImageRenderer(size: size, format: { let f = UIGraphicsImageRendererFormat(); f.scale = 1; f.opaque = false; return f }())
        return r.image { ctx in
            let c = ctx.cgContext
            c.setShadow(offset: CGSize(width: 0, height: logoSide * 0.03), blur: logoSide * 0.12, color: UIColor.black.withAlphaComponent(0.55).cgColor)
            let logoRect = CGRect(x: 0, y: (height - logoSide) / 2, width: logoSide, height: logoSide)
            if let logo = UIImage(named: "Logo") {
                c.saveGState()
                UIBezierPath(roundedRect: logoRect, cornerRadius: logoSide * 0.22).addClip()
                logo.draw(in: logoRect)
                c.restoreGState()
            }
            let x = logoSide * 1.18
            let title = NSAttributedString(string: "UniHub", attributes: [
                .font: UIFont.systemFont(ofSize: logoSide * 0.46, weight: .heavy), .foregroundColor: UIColor.white.withAlphaComponent(0.95),
            ])
            let name = NSAttributedString(string: "@" + username, attributes: [
                .font: UIFont.systemFont(ofSize: logoSide * 0.3, weight: .semibold), .foregroundColor: UIColor.white.withAlphaComponent(0.9),
            ])
            title.draw(at: CGPoint(x: x, y: height / 2 - logoSide * 0.55))
            name.draw(at: CGPoint(x: x, y: height / 2 + logoSide * 0.02))
        }
    }

    private static func allowed() async -> Bool {
        let now = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        if now == .authorized || now == .limited { return true }
        if now != .notDetermined { return false }
        let s = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        return s == .authorized || s == .limited
    }

    /// A photo story, stamped in its bottom right corner
    static func savePhoto(_ url: URL, username: String) async throws {
        guard await allowed() else { throw Failure.denied }
        guard let (data, _) = try? await URLSession.shared.data14(from: url), let image = UIImage(data: data) else { throw Failure.download }
        let size = image.size
        let mark = watermark(width: max(160, size.width * 0.36), username: username)
        let pad = size.width * 0.04
        let out = UIGraphicsImageRenderer(size: size, format: { let f = UIGraphicsImageRendererFormat(); f.scale = image.scale; return f }()).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
            mark.draw(at: CGPoint(x: size.width - mark.size.width - pad, y: size.height - mark.size.height - pad))
        }
        try await PHPhotoLibrary.shared().performChanges { PHAssetChangeRequest.creationRequestForAsset(from: out) }
    }

    /// A video story, stamped on every frame in its bottom right corner
    static func saveVideo(_ remote: URL, username: String) async throws {
        guard await allowed() else { throw Failure.denied }
        guard let file = await StoryVideo.local(remote) else { throw Failure.download }
        let asset = AVURLAsset(url: file)
        guard let track = try? await asset.loadTracks(withMediaType: .video).first,
              let natural = Optional(track.naturalSize),
              let transform = Optional(track.preferredTransform) else { throw Failure.export }
        let shown = natural.applying(transform)
        let width = abs(shown.width)
        let mark = watermark(width: max(160, width * 0.36), username: username)
        guard let markCI = CIImage(image: mark) else { throw Failure.export }
        let pad = width * 0.04
        // CoreImage counts from the bottom left, so the corner is pad, pad from the right
        let placed = markCI.transformed(by: CGAffineTransform(translationX: width - mark.size.width - pad, y: pad))
        let composition = AVMutableVideoComposition(asset: asset) { request in
            let frame = request.sourceImage.clampedToExtent()
            request.finish(with: placed.composited(over: frame).cropped(to: request.sourceImage.extent), context: nil)
        }
        guard let export = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetHighestQuality) else { throw Failure.export }
        let out = FileManager.default.temporaryDirectory.appendingPathComponent("UniHub-story-" + UUID().uuidString + ".mp4")
        export.outputURL = out
        export.outputFileType = .mp4
        export.videoComposition = composition
        await withCheckedContinuation { (done: CheckedContinuation<Void, Never>) in export.exportAsynchronously { done.resume() } }
        guard export.status == .completed else { throw Failure.export }
        defer { try? FileManager.default.removeItem(at: out) }
        try await PHPhotoLibrary.shared().performChanges { PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: out) }
    }
}
