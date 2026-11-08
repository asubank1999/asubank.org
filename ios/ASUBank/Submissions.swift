import SwiftUI

/// Student submissions (presentations for the campus display screen), natively.
/// Same Apps Script as /upload/submit_file and /upload/student_submissions_board (JSONP GET API).
enum Submissions {
    static let api = "https://script.google.com/macros/s/AKfycbxT65oWO1Pve_9i4xThLiKBDZ1KTHlOoetrcOMo1A9QjoK8cm7djR077INB-yVSVaWN/exec"

    private final class UploadProgress: NSObject, URLSessionTaskDelegate {
        let onProgress: (Double) -> Void
        init(_ p: @escaping (Double) -> Void) { onProgress = p }
        func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
            guard totalBytesExpectedToSend > 0 else { return }
            onProgress(Double(totalBytesSent) / Double(totalBytesExpectedToSend))
        }
    }

    /// Stores the file with UniHub and returns its link. It doesn't appear in the public uploads list.
    static func upload(name: String, data: Data, token: String?, progress: @escaping (Double) -> Void) async throws -> String {
        var body: [String: Any] = ["action": "submissionFile", "name": name, "data": data.base64EncodedString()]
        if let token { body["token"] = token }
        var req = URLRequest(url: AccountsAPI.url)
        req.httpMethod = "POST"
        req.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 300
        let payload = try JSONSerialization.data(withJSONObject: body)
        let (reply, _) = try await URLSession.shared.upload14(for: req, from: payload, delegate: UploadProgress(progress))
        let o = (try? JSONSerialization.jsonObject(with: reply)) as? [String: Any]
        if o?["ok"] as? Bool == true, let url = o?["url"] as? String { return url }
        throw NSError(domain: "upload", code: 1, userInfo: [NSLocalizedDescriptionKey: o?["error"] as? String ?? L("Upload failed. Please try again.")])
    }

    struct Link: Codable, Hashable { var url: String; var platform: String?; var type: String?; var label: String? }
    struct Item: Codable, Identifiable, Hashable {
        var id: String
        var studentName: String?
        var presentationTitle: String?
        var fileType: String?
        var primaryUrl: String?
        var platform: String?
        var status: String?
        var submittedDate: String?
        var notes: String?
        var backupLinks: [Link]?

        var title: String { presentationTitle?.isEmpty == false ? presentationTitle! : "Untitled" }
        var name: String { studentName?.isEmpty == false ? studentName! : "Unknown student" }
        var kind: Kind {
            switch fileType ?? "" {
            case "pdf": return .pdf
            case "pptx": return .ppt
            case "docx": return .doc
            case "image": return .img
            case "video": return .video
            default: return AB.kind(primaryUrl ?? "", title)
            }
        }
        var date: Date? {
            guard let s = submittedDate else { return nil }
            let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "EEE MMM dd yyyy"
            return f.date(from: String(s.prefix(15)))
        }
    }

    /// Calls the JSONP API and unwraps `cb(...)`
    static func get(_ query: [URLQueryItem]) async throws -> [String: Any] {
        var c = URLComponents(string: api)!
        c.queryItems = query + [URLQueryItem(name: "callback", value: "cb")]
        let (data, _) = try await URLSession.shared.data14(from: c.url!)
        var s = String(data: data, encoding: .utf8) ?? ""
        if let a = s.firstIndex(of: "("), let b = s.lastIndex(of: ")") { s = String(s[s.index(after: a)..<b]) }
        guard let obj = (try? JSONSerialization.jsonObject(with: Data(s.utf8))) as? [String: Any] else {
            throw AccountsAPI.Failure(message: "The submissions server sent something unexpected.")
        }
        if obj["success"] as? Bool == true { return obj }
        throw AccountsAPI.Failure(message: obj["error"] as? String ?? "Something went wrong.")
    }

    static func list() async throws -> [Item] {
        decode([Item].self, try await get([URLQueryItem(name: "action", value: "list")])["data"]) ?? []
    }
}

struct SubmissionsScreen: View {
    var admin = false
    @State private var items: [Submissions.Item] = []
    @State private var loading = true
    @State private var error: String?
    @State private var filter = "all"
    @State private var query = ""
    @State private var submitting = false

    private var shown: [Submissions.Item] {
        let q = AB.norm(query)
        return items.filter { i in
            (filter == "all" || i.status == filter) && (q.isEmpty || AB.norm(i.title + " " + i.name).contains(q))
        }
    }

    var body: some View {
        List {
            Section14 {
                HStack(spacing: 8) {
                    stat("\(items.count)", "Total", .abAccent)
                    stat("\(items.filter { $0.status == "approved" }.count)", "Approved", .green)
                    stat("\(items.filter { $0.status == "pending" }.count)", "Pending", .orange)
                }
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                Picker("Status", selection: $filter) {
                    Text("All").tag("all"); Text("Approved").tag("approved"); Text("Pending").tag("pending"); Text("Review").tag("review")
                }
                .pickerStyle(SegmentedPickerStyle())
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            }
            if loading && items.isEmpty { HStack { Spacer(); ABDots("Loading submissions…"); Spacer() }.listRowBackground(Color.clear) }
            if let error { Section14 { Text(error).fg14(.red); Button("Try again") { Task { await load() } } } }
            ForEach(shown) { i in
                Section14 {
                    NavigationLink { SubmissionDetail(item: i, admin: admin) { await load() } } label: { row(i) }
                }
            }
            if !loading && shown.isEmpty && error == nil {
                Text("No submissions here yet.").fg14(.secondary).listRowBackground(Color.clear)
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Submissions")
        .searchable14(text: $query, prompt: "Title or student")
        .refreshable14 { await load() }
        .task14 { await load() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { submitting = true } label: { Label("Submit", systemImage: "plus.circle.fill") }
            }
        }
        .sheet(isPresented: $submitting) { SubmitPresentationSheet { await load() } }
    }

    private func stat(_ v: String, _ t: String, _ c: Color) -> some View {
        VStack(spacing: 2) {
            Text(v).font(.title2.weight(.bold)).fg14(c).monoDigit14()
            Text(t).font(.caption).fg14(.secondary)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10)
        .bgIn14(Color(UIColor.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func row(_ i: Submissions.Item) -> some View {
        HStack(spacing: 12) {
            KindTile(kind: i.kind, size: 40)
            VStack(alignment: .leading, spacing: 3) {
                BiText(text: i.title, font: .body.weight(.semibold))
                Text(i.name + (i.date.map { " · " + $0.fmt14(.medium, .none) } ?? "")).font(.caption).fg14(.secondary)
            }
            Spacer()
            statusBadge(i.status)
        }
        .padding(.vertical, 2)
    }

    private func load() async {
        loading = true
        do { items = try await Submissions.list(); error = nil } catch { self.error = error.localizedDescription }
        loading = false
    }
}

@ViewBuilder func statusBadge(_ s: String?) -> some View {
    switch s ?? "" {
    case "approved": Badge(text: "APPROVED", color: .green)
    case "pending": Badge(text: "PENDING", color: .orange)
    case "review": Badge(text: "IN REVIEW", color: .blue)
    default: EmptyView()
    }
}

private struct SubmissionDetail: View {
    let item: Submissions.Item
    let admin: Bool
    var changed: () async -> Void
    @Environment(\.presentationMode) private var presentationMode14
    private func dismiss() { presentationMode14.wrappedValue.dismiss() }
    @State private var secret = Keychain.get("ab.subs.secret") ?? ""
    @State private var busy = false
    @State private var error: String?
    @State private var confirm = false

    var body: some View {
        List {
            Section14 {
                VStack(alignment: .leading, spacing: 8) {
                    HStack { KindTile(kind: item.kind, size: 46); Spacer(); statusBadge(item.status) }
                    BiText(text: item.title, font: .title3.weight(.bold))
                    Text("by \(item.name)").fg14(.secondary)
                    if let d = item.date { Text(d.fmt14(.full, .none)).font(.caption).fg14(.secondary) }
                }
                .padding(.vertical, 4)
            }
            if let n = item.notes, !n.isEmpty { Section14("Notes") { BiText(text: n) } }
            Section14("Files") {
                if let u = item.primaryUrl { linkRow(item.title, u, item.platform) }
                ForEach(item.backupLinks ?? [], id: \.self) { l in linkRow(l.label ?? "Backup", l.url, l.platform) }
            }
            Section14 {
                Button() { Moderation.report(ReportTarget(kind: "file", targetId: item.primaryUrl ?? item.id, name: item.title)) } label: {
                    Label("Report this file", systemImage: "flag")
                }
            }
            if admin {
                Section14 {
                    SecureField("Admin secret", text: $secret)
                    Button() { confirm = true } label: { Label(busy ? "Deleting…" : "Delete submission", systemImage: "trash") }
                        .disabled(secret.isEmpty || busy)
                } header: { Text("Admin") } footer: { Text("Uses the ADMIN_SECRET of the submissions script. It's kept in this phone's keychain.") }
                if let error { Text(error).fg14(.red) }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Submission")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog14("Delete this submission?", isPresented: $confirm, titleVisibility: .visible) {
            Button("Delete") { Task { await delete() } }
        }
    }

    private func linkRow(_ title: String, _ url: String, _ platform: String?) -> some View {
        SiteLinkRowButton(url: url, title: title) {
            HStack(spacing: 12) {
                KindTile(kind: AB.kind(url, title), size: 30)
                VStack(alignment: .leading) {
                    BiText(text: title)
                    Text(platform ?? URL(string: url)?.host ?? "").font(.caption).fg14(.secondary)
                }
            }
        }
    }

    private func delete() async {
        busy = true
        Keychain.set(secret, for: "ab.subs.secret")
        do {
            _ = try await Submissions.get([URLQueryItem(name: "action", value: "delete"), URLQueryItem(name: "id", value: item.id), URLQueryItem(name: "secret", value: secret)])
            Haptics.success()
            await changed()
            dismiss()
        } catch { self.error = error.localizedDescription }
        busy = false
    }
}

private struct SubmitPresentationSheet: View {
    var done: () async -> Void
    @Environment(\.presentationMode) private var presentationMode14
    private func dismiss() { presentationMode14.wrappedValue.dismiss() }
    @ObservedObject private var account = AccountStore.shared
    @AppStorage("ab.subs.name") private var name = ""
    @State private var title = ""
    @State private var notes = ""
    @State private var fileName = ""
    @State private var fileData: Data?
    @State private var showImporter = false
    @State private var showPhotos = false
    @State private var progress: Double?
    @State private var busy = false
    @State private var error: String?
    @State private var sent = false

    private let allowed = ["pdf", "pptx", "ppt", "docx", "doc", "png", "jpg", "jpeg", "webp", "heic", "mp4", "mov", "m4v", "webm"]
    private let maxBytes = 25 * 1024 * 1024

    private var valid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && !title.trimmingCharacters(in: .whitespaces).isEmpty && fileData != nil
    }

    private var fileType: String {
        switch (fileName as NSString).pathExtension.lowercased() {
        case "pdf": return "pdf"
        case "ppt", "pptx": return "pptx"
        case "doc", "docx": return "docx"
        case "mp4", "mov", "m4v", "webm": return "video"
        default: return "image"
        }
    }

    var body: some View {
        ABStack {
            Form {
                if sent {
                    Section14 {
                        VStack(spacing: 10) {
                            Image(systemName: "checkmark.circle.fill").font(.system(size: 56)).fg14(.green)
                            Text("Submitted!").font(.title2.weight(.bold))
                            Text("The team reviews it before it goes on the campus display screen.").multilineTextAlignment(.center).fg14(.secondary)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 20)
                    }
                } else {
                    Section14 {
                        TextField("Your name", text: $name).textContentType(.name)
                        TextField("Presentation title", text: $title)
                    } header: { Text("About it") }
                    Section14 {
                        if let d = fileData {
                            HStack(spacing: 12) {
                                KindTile(kind: AB.kind("", fileName))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(fileName).lineLimit(2)
                                    Text(ByteCountFormatter.string(fromByteCount: Int64(d.count), countStyle: .file)).font(.caption).fg14(.secondary)
                                }
                                Spacer()
                                if !busy {
                                    Button { fileData = nil; fileName = "" } label: { Image(systemName: "xmark.circle.fill").fg14(.secondary) }
                                        .buttonStyle(BorderlessButtonStyle())
                                }
                            }
                            if let progress {
                                ProgressView(value: progress).tint14(.abAccent)
                                Text(String(format: L("Uploading… %d%%"), Int(progress * 100))).font(.caption).fg14(.secondary)
                            }
                        } else {
                            Button { showImporter = true } label: { Label("Choose a file", systemImage: "doc.badge.plus") }
                            Button { showPhotos = true } label: { Label("Choose a photo", systemImage: "photo") }
                        }
                    } header: { Text("The file") } footer: { Text("PDF, PowerPoint, Word, images or video, up to 25 MB. It uploads right from the app.") }
                    Section14("Notes (optional)") { TextField("Anything the team should know", text: $notes) }
                    if let error { Section14 { Text(error).fg14(.red) } }
                }
            }
            .navigationTitle("Submit a file").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(sent ? "Done" : "Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if !sent { Button(busy ? "Sending…" : "Submit") { Task { await submit() } }.disabled(!valid || busy) }
                }
            }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.item]) { result in
                if case .success(let url) = result { pick(url) }
            }
            .sheet(isPresented: $showPhotos) {
                AvatarPicker { image in
                    if let d = image.jpegData(compressionQuality: 0.88) { take(name: "photo.jpg", data: d) }
                }
            }
        }
    }

    private func pick(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let d = try? Data(contentsOf: url) else { error = L("Couldn't read this file."); return }
        take(name: url.lastPathComponent, data: d)
    }

    private func take(name: String, data: Data) {
        error = nil
        guard allowed.contains((name as NSString).pathExtension.lowercased()) else { error = L("This file type isn't allowed."); return }
        guard data.count <= maxBytes else { error = L("Bigger than 25 MB."); return }
        guard !data.isEmpty else { error = L("This file is empty."); return }
        fileName = name
        fileData = data
    }

    private func submit() async {
        guard let data = fileData else { return }
        busy = true
        defer { busy = false; progress = nil }
        do {
            progress = 0
            let url = try await Submissions.upload(name: fileName, data: data, token: account.token) { p in
                Task { @MainActor in progress = p }
            }
            let payload: [String: Any] = ["studentName": name, "presentationTitle": title, "fileType": fileType, "platform": "UniHub", "primaryUrl": url,
                                          "notes": notes, "isBackup": false, "backupLinks": [Any]()]
            let json = try JSONSerialization.data(withJSONObject: payload)
            _ = try await Submissions.get([URLQueryItem(name: "action", value: "submit"), URLQueryItem(name: "data", value: String(data: json, encoding: .utf8))])
            withAnimation { sent = true }
            error = nil
            Haptics.success()
            await done()
        } catch { self.error = error.localizedDescription; Haptics.error() }
    }
}
