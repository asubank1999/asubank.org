import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import VisionKit

/// A file waiting to be uploaded
struct PendingFile: Identifiable {
    enum State: Equatable { case ready, uploading(Double), done(String), failed(String) }
    let id = UUID()
    var name: String
    var data: Data
    var mime: String
    var state: State = .ready
}

/// Sends files to the same Apps Script as asubank.org/upload/uploadmethod, with real progress
enum Uploader {
    static let maxBytes = 25 * 1024 * 1024
    static let allowed = ["pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "jpg", "jpeg", "png", "heic", "webp", "zip", "rar"]

    struct Reply: Decodable { var success: Bool?; var url: String?; var error: String? }

    private final class Progress: NSObject, URLSessionTaskDelegate {
        let onProgress: (Double) -> Void
        init(_ p: @escaping (Double) -> Void) { onProgress = p }
        func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
            guard totalBytesExpectedToSend > 0 else { return }
            onProgress(Double(totalBytesSent) / Double(totalBytesExpectedToSend))
        }
    }

    static func send(_ f: PendingFile, subject: String, kind: String, year: String, note: String, progress: @escaping (Double) -> Void) async throws -> String {
        let body: [String: String] = [
            "name": f.name, "mimeType": f.mime, "data": f.data.base64EncodedString(),
            "subject": subject, "kind": kind, "year": year, "note": note
        ]
        var req = URLRequest(url: AB.uploadAPI)
        req.httpMethod = "POST"
        req.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 300
        let payload = try JSONSerialization.data(withJSONObject: body)
        let (data, _) = try await URLSession.shared.upload(for: req, from: payload, delegate: Progress(progress))
        let r = try? JSONDecoder().decode(Reply.self, from: data)
        if r?.success == true { return r?.url ?? "" }
        throw NSError(domain: "upload", code: 1, userInfo: [NSLocalizedDescriptionKey: r?.error ?? "Upload failed. Please try again."])
    }
}

struct UploadView: View {
    @EnvironmentObject private var library: Library
    @EnvironmentObject private var app: AppState
    @AppStorage("ab.upload.agreed") private var agreed = false
    @State private var files: [PendingFile] = []
    @State private var subject = ""
    @State private var kind = ""
    @State private var year = ""
    @State private var note = ""
    @State private var busy = false
    @State private var showImporter = false
    @State private var showScanner = false
    @State private var showPhotos = false
    @State private var message: String?

    private let kinds = ["Final", "Midterm", "Quiz", "Summary", "Notes", "Slides", "Test bank", "Assignment"]
    private var years: [String] {
        let y = Calendar.current.component(.year, from: Date())
        return (0..<8).map { String(y - $0) }
    }
    private var readyCount: Int { files.filter { $0.state == .ready }.count }

    var body: some View {
        ABStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Label("PDF, Word, PowerPoint, Excel, images or ZIP, up to 25 MB each.", systemImage: "doc.badge.plus")
                        Label("Hide your university number and name on scans.", systemImage: "eye.slash")
                        Label("No illegal, private or copyrighted material.", systemImage: "hand.raised")
                        Label("Use a clear name, e.g. “Calculus 1 Midterm 2025 Dr Ahmad.pdf”.", systemImage: "textformat")
                    }
                    .font(.subheadline)
                    .labelStyle(TightLabel())
                    Toggle(isOn: $agreed) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("I agree to the Terms of Service")
                            Text("أوافق على شروط الخدمة").font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    .tint(.abAccent)
                    RouteLink(Route.web("Terms of Service", "/legal/uni_tos")) { Text("Read the terms") }
                } header: { Text("Before you upload") }

                Section {
                    HStack(spacing: 10) {
                        sourceButton("Files", "folder") { showImporter = true }
                        sourceButton("Photos", "photo.on.rectangle") { showPhotos = true }
                        if VNDocumentCameraViewController.isSupported {
                            sourceButton("Scan", "doc.viewfinder") { showScanner = true }
                        }
                    }
                    .disabled(!agreed || busy)
                    .opacity(agreed ? 1 : 0.5)
                    .listRowInsets(EdgeInsets(top: 10, leading: 12, bottom: 10, trailing: 12))
                    ForEach(files) { f in fileRow(f) }
                        .onDelete { idx in if !busy { files.remove(atOffsets: idx) } }
                } header: { Text("Your files") } footer: {
                    if !agreed { Text("Turn on “I agree” first.") }
                    else if files.isEmpty { Text("Scanned pages become one PDF. You can add up to 20 photos at a time.") }
                }

                Section {
                    Picker("Subject", selection: $subject) {
                        Text("Choose (optional)").tag("")
                        ForEach(library.subjects.filter { $0.c != "Archive" }) { Text($0.t).tag($0.t) }
                    }
                    Picker("Type", selection: $kind) {
                        Text("Any").tag("")
                        ForEach(kinds, id: \.self) { Text($0).tag($0) }
                    }
                    Picker("Year", selection: $year) {
                        Text("Any").tag("")
                        ForEach(years, id: \.self) { Text($0).tag($0) }
                    }
                    TextField("Doctor / note (optional)", text: $note)
                } header: { Text("Details") } footer: {
                    Text("Files are named like “[Subject] [Final 2025] name.pdf” so other students can find them.")
                }

                Section {
                    Button(action: upload) {
                        HStack {
                            Spacer()
                            if busy { ProgressView().tint(.white) }
                            Text(busy ? "Uploading…" : readyCount > 0 ? "Upload \(readyCount) file\(readyCount == 1 ? "" : "s")" : "Upload")
                                .font(.headline)
                            Spacer()
                        }
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.abAccent)
                    .disabled(!agreed || readyCount == 0 || busy)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }

                Section("Other ways to share") {
                    RouteLink(Route.web("Instant upload", "/upload/gupload")) { Label("Instant upload (large files)", systemImage: "bolt.fill") }
                    RouteLink(Route.web("Structured submission", "/upload/submissions")) { Label("Structured submission", systemImage: "doc.text") }
                    RouteLink(Route.web("Campus display", "/upload/submit_file")) { Label("Campus display presentations", systemImage: "display") }
                    RouteLink(Route.web("Submitted files", "/upload/student_submissions_board")) { Label("Submitted files board", systemImage: "list.clipboard") }
                }
            }
            .navigationTitle("Upload")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { app.uploadShown = false } }
            }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
                if case .success(let urls) = result { addFiles(urls) }
            }
            .sheet(isPresented: $showScanner) {
                ScannerView { pdf in
                    if let pdf { add(name: "Scan \(Date().formatted(.dateTime.year().month().day().hour().minute())).pdf", data: pdf, mime: "application/pdf") }
                    showScanner = false
                }
                .ignoresSafeArea()
            }
            .sheet(isPresented: $showPhotos) {
                PhotoPicker { images in
                    showPhotos = false
                    addPhotos(images)
                }
                .ignoresSafeArea()
            }
            .alert("Upload", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(message ?? "") }
            .abDestinations()
        }
    }

    private func sourceLabel(_ title: String, _ symbol: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: symbol).font(.title2)
            Text(title).font(.footnote.weight(.semibold))
        }
        .foregroundStyle(Color.abAccent)
        .frame(maxWidth: .infinity, minHeight: 70)
        .background(Color.abAccentSoft, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func sourceButton(_ title: String, _ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { sourceLabel(title, symbol) }.buttonStyle(PressStyle())
    }

    private func fileRow(_ f: PendingFile) -> some View {
        HStack(spacing: 12) {
            KindTile(kind: AB.kind("", f.name), size: 32)
            VStack(alignment: .leading, spacing: 4) {
                Text(f.name).lineLimit(1)
                switch f.state {
                case .ready:
                    Text(ByteCountFormatter.string(fromByteCount: Int64(f.data.count), countStyle: .file)).font(.caption).foregroundStyle(.secondary)
                case .uploading(let p):
                    ProgressView(value: p).tint(.abAccent)
                    Text("Uploading… \(Int(p * 100))%").font(.caption).foregroundStyle(.secondary)
                case .done:
                    Text("✓ Uploaded — thank you!").font(.caption).foregroundStyle(.green)
                case .failed(let e):
                    Text(e).font(.caption).foregroundStyle(.red)
                }
            }
        }
    }

    // MARK: - Adding files

    private func add(name: String, data: Data, mime: String) {
        let ext = (name as NSString).pathExtension.lowercased()
        var f = PendingFile(name: name, data: data, mime: mime)
        if !Uploader.allowed.contains(ext) { f.state = .failed("This file type is not accepted.") }
        else if data.count > Uploader.maxBytes { f.state = .failed("Files must be 25 MB or smaller.") }
        else if data.isEmpty { f.state = .failed("The file is empty.") }
        withAnimation { files.append(f) }
    }

    private func addFiles(_ urls: [URL]) {
        for u in urls {
            let ok = u.startAccessingSecurityScopedResource()
            defer { if ok { u.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: u) else { continue }
            let mime = UTType(filenameExtension: u.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
            add(name: u.lastPathComponent, data: data, mime: mime)
        }
    }

    private func addPhotos(_ images: [UIImage]) {
        for (i, img) in images.enumerated() {
            guard let jpg = img.jpegData(compressionQuality: 0.85) else { continue }
            let stamp = Date().formatted(.dateTime.year().month().day().hour().minute().second())
            add(name: "Photo \(stamp)\(images.count > 1 ? " \(i + 1)" : "").jpg", data: jpg, mime: "image/jpeg")
        }
    }

    // MARK: - Uploading

    private func upload() {
        busy = true
        Task {
            var ok = 0
            for i in files.indices where files[i].state == .ready {
                let f = files[i]
                files[i].state = .uploading(0)
                do {
                    let url = try await Uploader.send(f, subject: subject, kind: kind, year: year, note: note.trimmingCharacters(in: .whitespaces)) { p in
                        Task { @MainActor in
                            if let j = files.firstIndex(where: { $0.id == f.id }) { files[j].state = .uploading(min(p, 0.99)) }
                        }
                    }
                    if let j = files.firstIndex(where: { $0.id == f.id }) { files[j].state = .done(url) }
                    MyUploads.add(name: f.name, url: url)
                    ok += 1
                } catch {
                    if let j = files.firstIndex(where: { $0.id == f.id }) {
                        files[j].state = .failed((error as? URLError) != nil ? "Network error. Check your connection and try again." : error.localizedDescription)
                    }
                }
            }
            busy = false
            if ok > 0 {
                Haptics.success()
                message = "\(ok) file\(ok == 1 ? "" : "s") uploaded — thank you! 🎉 New files appear in Latest uploads and in Search."
            } else {
                Haptics.error()
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

/// The system photo picker (PHPicker), up to 20 photos
struct PhotoPicker: UIViewControllerRepresentable {
    let done: ([UIImage]) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 20
        let vc = PHPickerViewController(configuration: config)
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: PHPickerViewController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(done) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let done: ([UIImage]) -> Void
        init(_ done: @escaping ([UIImage]) -> Void) { self.done = done }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard !results.isEmpty else { done([]); return }
            var images = [UIImage?](repeating: nil, count: results.count)
            let group = DispatchGroup()
            for (i, r) in results.enumerated() where r.itemProvider.canLoadObject(ofClass: UIImage.self) {
                group.enter()
                r.itemProvider.loadObject(ofClass: UIImage.self) { obj, _ in
                    DispatchQueue.main.async { images[i] = obj as? UIImage; group.leave() }
                }
            }
            group.notify(queue: .main) { self.done(images.compactMap { $0 }) }
        }
    }
}
