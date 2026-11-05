import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import VisionKit

/// A file waiting to be uploaded. It's kept as a copy on disk, so big files never sit in memory.
struct PendingFile: Identifiable {
    enum State: Equatable { case ready, uploading(Double), done(String), failed(String), rejected(String) }
    let id = UUID()
    var name: String
    var url: URL
    var size: Int64
    var mime: String
    var state: State = .ready
}

/// Big files go straight to Google Drive in chunks. The upload script only opens the session (action "start"),
/// so there's no size limit in the app or the script, and a dropped connection resumes where it stopped.
enum Uploader {
    static let chunkSize = 8 * 1024 * 1024 // Drive wants multiples of 256 KB
    static let allowed = ["pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "jpg", "jpeg", "png", "heic", "webp", "zip", "rar", "7z", "csv",
                          "mp4", "mov", "m4v", "webm", "mp3", "m4a"]

    private struct Start: Decodable { var success: Bool?; var uploadUrl: String?; var error: String? }
    private struct Done: Decodable { var id: String? }

    private final class Progress: NSObject, URLSessionTaskDelegate {
        let onProgress: (Int64) -> Void
        init(_ p: @escaping (Int64) -> Void) { onProgress = p }
        func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
            onProgress(totalBytesSent)
        }
    }

    private static func fail(_ message: String) -> NSError {
        NSError(domain: "upload", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }

    private static func startSession(_ f: PendingFile, subject: String, kind: String, year: String, note: String) async throws -> URL {
        let body: [String: Any] = ["action": "start", "name": f.name, "mimeType": f.mime, "size": f.size,
                                   "subject": subject, "kind": kind, "year": year, "note": note]
        var req = URLRequest(url: AB.uploadAPI, timeoutInterval: 60)
        req.httpMethod = "POST"
        req.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, _) = try await URLSession.shared.data(for: req)
        let r = try? JSONDecoder().decode(Start.self, from: data)
        guard r?.success == true, let s = r?.uploadUrl, let url = URL(string: s) else {
            throw fail(r?.error ?? "Upload failed. Please try again.")
        }
        return url
    }

    /// Asks Drive how much of the file arrived. Returns the next byte to send, or the file's link if it's complete.
    private static func status(_ session: URL, size: Int64) async -> (offset: Int64, link: String?)? {
        var req = URLRequest(url: session, timeoutInterval: 60)
        req.httpMethod = "PUT"
        req.setValue("bytes */\(size)", forHTTPHeaderField: "Content-Range")
        guard let (data, resp) = try? await URLSession.shared.upload(for: req, from: Data()),
              let http = resp as? HTTPURLResponse else { return nil }
        if http.statusCode == 200 || http.statusCode == 201 { return (size, link(data)) }
        if http.statusCode == 308 { return (nextOffset(http), nil) }
        return nil
    }

    private static func nextOffset(_ r: HTTPURLResponse) -> Int64 {
        guard let range = r.value(forHTTPHeaderField: "Range"), let last = range.split(separator: "-").last, let n = Int64(last) else { return 0 }
        return n + 1
    }

    private static func link(_ data: Data) -> String {
        guard let id = (try? JSONDecoder().decode(Done.self, from: data))?.id else { return "" }
        return "https://drive.google.com/file/d/\(id)/view"
    }

    static func send(_ f: PendingFile, subject: String, kind: String, year: String, note: String, progress: @escaping (Double) -> Void) async throws -> String {
        let session = try await startSession(f, subject: subject, kind: kind, year: year, note: note)
        let handle = try FileHandle(forReadingFrom: f.url)
        defer { try? handle.close() }
        var offset: Int64 = 0
        var tries = 0
        while true {
            try Task.checkCancellation()
            try handle.seek(toOffset: UInt64(offset))
            let chunk = handle.readData(ofLength: chunkSize)
            guard !chunk.isEmpty else { throw fail("The file changed while uploading. Please add it again.") }
            var req = URLRequest(url: session, timeoutInterval: 300)
            req.httpMethod = "PUT"
            req.setValue("bytes \(offset)-\(offset + Int64(chunk.count) - 1)/\(f.size)", forHTTPHeaderField: "Content-Range")
            let base = offset, total = Double(max(f.size, 1))
            let watcher = Progress { sent in progress(Double(base + sent) / total) }
            var retry = false
            do {
                let (data, resp) = try await URLSession.shared.upload(for: req, from: chunk, delegate: watcher)
                let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
                if code == 200 || code == 201 { return link(data) }
                if code == 308, let http = resp as? HTTPURLResponse {
                    offset = nextOffset(http)
                    tries = 0
                    continue
                }
                if code == 404 || code == 410 { throw fail("The upload expired. Please try again.") }
                if code >= 400 && code < 500 && code != 429 && code != 408 { throw fail("Upload failed. Please try again.") }
                retry = true
            } catch let e as URLError where e.code != .cancelled {
                retry = true
            }
            if retry {
                tries += 1
                if tries > 6 { throw URLError(.networkConnectionLost) }
                try await Task.sleep(nanoseconds: UInt64(1 << tries) * 1_000_000_000)
                if let s = await status(session, size: f.size) {
                    if let l = s.link { return l }
                    offset = s.offset
                }
            }
        }
    }
}

/// A picked photo: the system's temporary copy has to be moved before its callback returns
func copyPickedPhoto(_ source: URL, name: String) -> URL? {
    let dir = FileManager.default.temporaryDirectory.appendingPathComponent("uploads/\(UUID().uuidString)", isDirectory: true)
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    let target = dir.appendingPathComponent(name.replacingOccurrences(of: "/", with: " "))
    return (try? FileManager.default.copyItem(at: source, to: target)) != nil ? target : nil
}

/// Keeps uploading when the upload screen is closed, as long as the app stays open
@MainActor
final class UploadQueue: ObservableObject {
    static let shared = UploadQueue()
    @Published var files: [PendingFile] = []
    @Published var subject = ""
    @Published var kind = ""
    @Published var year = ""
    @Published var note = ""
    @Published private(set) var busy = false
    @Published private(set) var adding = 0
    @Published var finished: Int?

    private let folder = FileManager.default.temporaryDirectory.appendingPathComponent("uploads", isDirectory: true)
    private var background: UIBackgroundTaskIdentifier = .invalid

    var readyCount: Int { files.filter { $0.state == .ready }.count }
    var waitingBytes: Int64 { files.filter { $0.state == .ready }.reduce(0) { $0 + $1.size } }
    /// How far the whole batch is, 0 to 1
    var overall: Double {
        let active = files.filter { if case .rejected = $0.state { return false }; return true }
        let total = active.reduce(Int64(0)) { $0 + $1.size }
        guard total > 0 else { return 0 }
        var sent = 0.0
        for f in active {
            switch f.state {
            case .uploading(let p): sent += p * Double(f.size)
            case .done: sent += Double(f.size)
            default: break
            }
        }
        return sent / Double(total)
    }

    private func newFile(_ name: String) -> URL {
        let dir = folder.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(name.replacingOccurrences(of: "/", with: " "))
    }

    private func register(name: String, at url: URL) {
        let size = ((try? FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? NSNumber)?.int64Value ?? 0
        let ext = (name as NSString).pathExtension
        var f = PendingFile(name: name, url: url, size: size, mime: UTType(filenameExtension: ext)?.preferredMIMEType ?? "application/octet-stream")
        if !Uploader.allowed.contains(ext.lowercased()) { f.state = .rejected("This file type is not accepted.") }
        else if size == 0 { f.state = .rejected("The file is empty.") }
        withAnimation { files.append(f) }
    }

    func add(urls: [URL]) {
        adding += urls.count
        for u in urls {
            let target = newFile(u.lastPathComponent)
            Task {
                let ok = await Task.detached(priority: .userInitiated) { () -> Bool in
                    let scoped = u.startAccessingSecurityScopedResource()
                    defer { if scoped { u.stopAccessingSecurityScopedResource() } }
                    return (try? FileManager.default.copyItem(at: u, to: target)) != nil
                }.value
                if ok { register(name: u.lastPathComponent, at: target) }
                adding -= 1
            }
        }
    }

    func add(data: Data, name: String) {
        let target = newFile(name)
        guard (try? data.write(to: target)) != nil else { return }
        register(name: name, at: target)
    }

    func addCopied(_ url: URL) { register(name: url.lastPathComponent, at: url) }

    func remove(at offsets: IndexSet) {
        for i in offsets {
            if case .uploading = files[i].state { return }
        }
        for i in offsets { try? FileManager.default.removeItem(at: files[i].url.deletingLastPathComponent()) }
        files.remove(atOffsets: offsets)
    }

    func retry(_ id: UUID) {
        if let i = files.firstIndex(where: { $0.id == id }) { files[i].state = .ready }
    }

    /// Clears the finished files, ready for the next batch
    func reset() {
        let done = files.filter { if case .done = $0.state { return true }; return false }
        for f in done { try? FileManager.default.removeItem(at: f.url.deletingLastPathComponent()) }
        files.removeAll { f in done.contains { $0.id == f.id } }
        finished = nil
    }

    private func set(_ id: UUID, _ s: PendingFile.State) {
        if let i = files.firstIndex(where: { $0.id == id }) { files[i].state = s }
    }

    func start() {
        guard !busy else { return }
        busy = true
        finished = nil
        UIApplication.shared.isIdleTimerDisabled = true
        background = UIApplication.shared.beginBackgroundTask(withName: "upload") { [weak self] in self?.endBackground() }
        let subject = self.subject, kind = self.kind, year = self.year, note = self.note.trimmingCharacters(in: .whitespaces)
        Task {
            var ok = 0
            for f in files where f.state == .ready {
                set(f.id, .uploading(0))
                var shown = 0.0
                do {
                    let url = try await Uploader.send(f, subject: subject, kind: kind, year: year, note: note) { p in
                        // at most one redraw per percent
                        guard p - shown >= 0.01 else { return }
                        shown = p
                        Task { @MainActor in UploadQueue.shared.set(f.id, .uploading(min(p, 0.99))) }
                    }
                    set(f.id, .done(url))
                    MyUploads.add(name: f.name, url: url)
                    ok += 1
                } catch {
                    set(f.id, .failed((error as? URLError) != nil ? L("Network error. Check your connection and try again.") : L(error.localizedDescription)))
                }
            }
            busy = false
            UIApplication.shared.isIdleTimerDisabled = false
            endBackground()
            if ok > 0 { Haptics.success(); finished = ok } else { Haptics.error() }
        }
    }

    private func endBackground() {
        if background != .invalid { UIApplication.shared.endBackgroundTask(background); background = .invalid }
    }
}

struct UploadView: View {
    @EnvironmentObject private var library: Library
    @EnvironmentObject private var app: AppState
    @AppStorage("ab.upload.agreed") private var agreed = false
    @ObservedObject private var q = UploadQueue.shared
    @State private var showImporter = false
    @State private var showScanner = false
    @State private var showPhotos = false
    @State private var rulesOpen = false

    private let kinds = ["Final", "Midterm", "Quiz", "Summary", "Notes", "Slides", "Test bank", "Assignment"]
    private var years: [String] {
        let y = Calendar.current.component(.year, from: Date())
        return (0..<8).map { String(y - $0) }
    }

    var body: some View {
        ABStack {
            List {
                Section { hero }
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)

                Section {
                    HStack(spacing: 10) {
                        sourceButton("Files", "folder") { showImporter = true }
                        sourceButton("Photos", "photo.on.rectangle") { showPhotos = true }
                        if VNDocumentCameraViewController.isSupported {
                            sourceButton("Scan", "doc.viewfinder") { showScanner = true }
                        }
                    }
                    .disabled(q.busy)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }

                if !q.files.isEmpty || q.adding > 0 {
                    Section {
                        ForEach(q.files) { f in fileRow(f) }
                            .onDelete { q.remove(at: $0) }
                        if q.adding > 0 {
                            HStack(spacing: 12) { ABDots(); Text("Preparing files…").foregroundStyle(.secondary) }
                        }
                    } header: {
                        HStack {
                            Text("Your files")
                            Spacer()
                            if q.readyCount > 0 {
                                Text(ByteCountFormatter.string(fromByteCount: q.waitingBytes, countStyle: .file)).textCase(nil)
                            }
                        }
                    }
                }

                Section {
                    Picker(selection: $q.subject) {
                        Text("Choose (optional)").tag("")
                        ForEach(library.subjects.filter { $0.c != "Archive" }) { Text($0.t).tag($0.t) }
                    } label: { Label("Subject", systemImage: "books.vertical") }
                    Picker(selection: $q.kind) {
                        Text("Any").tag("")
                        ForEach(kinds, id: \.self) { Text($0).tag($0) }
                    } label: { Label("Type", systemImage: "tag") }
                    Picker(selection: $q.year) {
                        Text("Any").tag("")
                        ForEach(years, id: \.self) { Text(verbatim: $0).tag($0) }
                    } label: { Label("Year", systemImage: "calendar") }
                    HStack(spacing: 12) {
                        Image(systemName: "text.bubble").foregroundStyle(Color.abAccent).frame(width: 28)
                        TextField("Doctor / note (optional)", text: $q.note)
                    }
                } header: { Text("Details") } footer: {
                    Text("Files are named like “[Subject] [Final 2025] name.pdf” so other students can find them.")
                }

                Section {
                    Toggle(isOn: $agreed) { Text("I agree to the upload rules") }
                        .tint(.abAccent)
                    DisclosureGroup("Upload rules", isExpanded: $rulesOpen) {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("Hide your university number and name on scans.", systemImage: "eye.slash")
                            Label("No illegal, private or copyrighted material.", systemImage: "hand.raised")
                            Label("Use a clear name, e.g. “Calculus 1 Midterm 2025 Dr Ahmad.pdf”.", systemImage: "textformat")
                        }
                        .font(.subheadline)
                        .labelStyle(TightLabel())
                        .padding(.vertical, 4)
                        RouteLink(Route.page("/legal/uni_tos", "Terms of Service")) { Text("Read the terms") }
                    }
                } footer: {
                    if q.busy { Text("Keep the app open until your files finish. You can close this screen.") }
                }

                Section {
                    Button { q.start() } label: {
                        HStack(spacing: 8) {
                            Spacer()
                            if q.busy { ABDots().tint(.white) } else { Image(systemName: "arrow.up.circle.fill") }
                            Text(uploadTitle).font(.headline)
                            Spacer()
                        }
                        .padding(.vertical, 8)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.abAccent)
                    .disabled(!agreed || q.readyCount == 0 || q.busy)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }

                Section("Other ways to share") {
                    RouteLink(Route.page("/upload/submit_file", "Campus display")) { Label("Campus display presentations", systemImage: "display") }
                    RouteLink(Route.page("/upload/student_submissions_board", "Submitted files")) { Label("Submitted files board", systemImage: "list.clipboard") }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Upload")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { app.uploadShown = false } }
            }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
                if case .success(let urls) = result { q.add(urls: urls) }
            }
            .sheet(isPresented: $showScanner) {
                ScannerView { pdf in
                    if let pdf { q.add(data: pdf, name: "Scan \(Date().formatted(.dateTime.year().month().day().hour().minute())).pdf") }
                    showScanner = false
                }
                .ignoresSafeArea()
            }
            .sheet(isPresented: $showPhotos) {
                PhotoPicker { showPhotos = false }.ignoresSafeArea()
            }
            .abDestinations()
        }
    }

    private var uploadTitle: String {
        if q.busy { return L("Uploading…") }
        if q.readyCount > 1 { return String(format: L("Upload %d files"), q.readyCount) }
        return L("Upload")
    }

    @ViewBuilder private var hero: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: q.finished != nil ? "checkmark.circle.fill" : "icloud.and.arrow.up.fill")
                    .font(.system(size: 30))
                VStack(alignment: .leading, spacing: 2) {
                    if let n = q.finished {
                        Text(verbatim: String(format: L("Thank you! %d files uploaded. They're in Latest uploads now."), n)).font(.headline)
                    } else {
                        Text("Share your study files").font(.headline)
                        Text("Any file size, as many files as you like.").font(.subheadline).opacity(0.9)
                    }
                }
                Spacer(minLength: 0)
            }
            if q.busy {
                ProgressView(value: q.overall).tint(.white)
                Text(verbatim: "\(Int(q.overall * 100))%").font(.caption.weight(.semibold)).monospacedDigit()
            }
            if q.finished != nil {
                Button { withAnimation { q.reset() } } label: {
                    Text("Upload more").font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 14).padding(.vertical, 7)
                        .background(.white.opacity(0.22), in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .foregroundStyle(.white)
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.abAccent.abGradient, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func sourceButton(_ title: String, _ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: symbol).font(.title2)
                Text(LocalizedStringKey(title)).font(.footnote.weight(.semibold))
            }
            .foregroundStyle(Color.abAccent)
            .frame(maxWidth: .infinity, minHeight: 76)
            .background(Color.abAccentSoft, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(PressStyle())
    }

    private func fileRow(_ f: PendingFile) -> some View {
        HStack(spacing: 12) {
            KindTile(kind: AB.kind("", f.name), size: 34)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: f.name).lineLimit(1)
                switch f.state {
                case .ready:
                    Text(verbatim: ByteCountFormatter.string(fromByteCount: f.size, countStyle: .file)).font(.caption).foregroundStyle(.secondary)
                case .uploading(let p):
                    ProgressView(value: p).tint(.abAccent)
                    Text(verbatim: "\(Int(p * 100))% · " + ByteCountFormatter.string(fromByteCount: f.size, countStyle: .file)).font(.caption).foregroundStyle(.secondary).monospacedDigit()
                case .done:
                    Label("Uploaded", systemImage: "checkmark.circle.fill").font(.caption).foregroundStyle(.green)
                case .failed(let e):
                    Text(e).font(.caption).foregroundStyle(.red)
                case .rejected(let e):
                    Text(e).font(.caption).foregroundStyle(.red)
                }
            }
            Spacer(minLength: 0)
            if case .failed = f.state {
                Button { q.retry(f.id) } label: {
                    Image(systemName: "arrow.clockwise.circle.fill").font(.title2).foregroundStyle(Color.abAccent)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Try again")
            }
        }
    }
}

private struct TightLabel: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            configuration.icon.foregroundStyle(Color.abAccent).frame(width: 20)
            configuration.title
        }
    }
}

/// VisionKit document scanner; returns the pages as one PDF
struct ScannerView: UIViewControllerRepresentable {
    let done: (Data?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(done) }

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let vc = VNDocumentCameraViewController()
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: VNDocumentCameraViewController, context: Context) {}

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let done: (Data?) -> Void
        init(_ d: @escaping (Data?) -> Void) { done = d }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            let page = CGRect(x: 0, y: 0, width: 612, height: 792) // US Letter in points
            let pdf = UIGraphicsPDFRenderer(bounds: page).pdfData { ctx in
                for i in 0..<scan.pageCount {
                    let img = scan.imageOfPage(at: i)
                    ctx.beginPage()
                    let s = min(page.width / img.size.width, page.height / img.size.height)
                    let w = img.size.width * s, h = img.size.height * s
                    img.draw(in: CGRect(x: (page.width - w) / 2, y: (page.height - h) / 2, width: w, height: h))
                }
            }
            done(pdf)
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) { done(nil) }
        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) { done(nil) }
    }
}

/// The system photo picker (PHPicker), with no limit on how many. Photos are copied to disk one by one instead of being held in memory.
struct PhotoPicker: UIViewControllerRepresentable {
    let done: () -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = .any(of: [.images, .videos])
        config.selectionLimit = 0
        config.preferredAssetRepresentationMode = .current
        let vc = PHPickerViewController(configuration: config)
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: PHPickerViewController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(done) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let done: () -> Void
        init(_ done: @escaping () -> Void) { self.done = done }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            done()
            for r in results {
                let provider = r.itemProvider
                let type = provider.registeredTypeIdentifiers.first { UTType($0)?.conforms(to: .image) == true || UTType($0)?.conforms(to: .movie) == true } ?? UTType.image.identifier
                provider.loadFileRepresentation(forTypeIdentifier: type) { url, _ in
                    guard let url else { return }
                    let base = provider.suggestedName ?? "Photo"
                    let name = (url.pathExtension.isEmpty ? base : base + "." + url.pathExtension.lowercased())
                    guard let copy = copyPickedPhoto(url, name: name) else { return }
                    Task { @MainActor in UploadQueue.shared.addCopied(copy) }
                }
            }
        }
    }
}
