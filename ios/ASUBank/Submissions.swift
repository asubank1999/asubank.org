import SwiftUI

/// Student submissions (presentations for the campus display screen), natively.
/// Same Apps Script as /upload/submit_file and /upload/student_submissions_board (JSONP GET API).
enum Submissions {
    static let api = "https://script.google.com/macros/s/AKfycbxT65oWO1Pve_9i4xThLiKBDZ1KTHlOoetrcOMo1A9QjoK8cm7djR077INB-yVSVaWN/exec"

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
        let (data, _) = try await URLSession.shared.data(from: c.url!)
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
            Section {
                HStack(spacing: 8) {
                    stat("\(items.count)", "Total", .abAccent)
                    stat("\(items.filter { $0.status == "approved" }.count)", "Approved", .green)
                    stat("\(items.filter { $0.status == "pending" }.count)", "Pending", .orange)
                }
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                Picker("Status", selection: $filter) {
                    Text("All").tag("all"); Text("Approved").tag("approved"); Text("Pending").tag("pending"); Text("Review").tag("review")
                }
                .pickerStyle(.segmented)
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            }
            if loading && items.isEmpty { HStack { Spacer(); ProgressView("Loading submissions…"); Spacer() }.listRowBackground(Color.clear) }
            if let error { Section { Text(error).foregroundStyle(.red); Button("Try again") { Task { await load() } } } }
            ForEach(shown) { i in
                Section {
                    NavigationLink { SubmissionDetail(item: i, admin: admin) { await load() } } label: { row(i) }
                }
            }
            if !loading && shown.isEmpty && error == nil {
                Text("No submissions here yet.").foregroundStyle(.secondary).listRowBackground(Color.clear)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Submissions")
        .searchable(text: $query, prompt: "Title or student")
        .refreshable { await load() }
        .task { await load() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { submitting = true } label: { Label("Submit", systemImage: "plus.circle.fill") }
            }
        }
        .sheet(isPresented: $submitting) { SubmitPresentationSheet { await load() } }
    }

    private func stat(_ v: String, _ t: String, _ c: Color) -> some View {
        VStack(spacing: 2) {
            Text(v).font(.title2.weight(.bold)).foregroundStyle(c).monospacedDigit()
            Text(t).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10)
        .background(Color(UIColor.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func row(_ i: Submissions.Item) -> some View {
        HStack(spacing: 12) {
            KindTile(kind: i.kind, size: 40)
            VStack(alignment: .leading, spacing: 3) {
                BiText(text: i.title, font: .body.weight(.semibold))
                Text(i.name + (i.date.map { " · " + $0.formatted(date: .abbreviated, time: .omitted) } ?? "")).font(.caption).foregroundStyle(.secondary)
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
    @Environment(\.dismiss) private var dismiss
    @State private var secret = Keychain.get("ab.subs.secret") ?? ""
    @State private var busy = false
    @State private var error: String?
    @State private var confirm = false

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    HStack { KindTile(kind: item.kind, size: 46); Spacer(); statusBadge(item.status) }
                    BiText(text: item.title, font: .title3.weight(.bold))
                    Text("by \(item.name)").foregroundStyle(.secondary)
                    if let d = item.date { Text(d.formatted(date: .complete, time: .omitted)).font(.caption).foregroundStyle(.secondary) }
                }
                .padding(.vertical, 4)
            }
            if let n = item.notes, !n.isEmpty { Section("Notes") { BiText(text: n) } }
            Section("Files") {
                if let u = item.primaryUrl { linkRow(item.title, u, item.platform) }
                ForEach(item.backupLinks ?? [], id: \.self) { l in linkRow(l.label ?? "Backup", l.url, l.platform) }
            }
            if admin {
                Section {
                    SecureField("Admin secret", text: $secret)
                    Button(role: .destructive) { confirm = true } label: { Label(busy ? "Deleting…" : "Delete submission", systemImage: "trash") }
                        .disabled(secret.isEmpty || busy)
                } header: { Text("Admin") } footer: { Text("Uses the ADMIN_SECRET of the submissions script. It's kept in this phone's keychain.") }
                if let error { Text(error).foregroundStyle(.red) }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Submission")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Delete this submission?", isPresented: $confirm, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { Task { await delete() } }
        }
    }

    private func linkRow(_ title: String, _ url: String, _ platform: String?) -> some View {
        SiteLinkRowButton(url: url, title: title) {
            HStack(spacing: 12) {
                KindTile(kind: AB.kind(url, title), size: 30)
                VStack(alignment: .leading) {
                    BiText(text: title)
                    Text(platform ?? URL(string: url)?.host ?? "").font(.caption).foregroundStyle(.secondary)
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
    @Environment(\.dismiss) private var dismiss
    @AppStorage("ab.subs.name") private var name = ""
    @State private var title = ""
    @State private var type = "pdf"
    @State private var platform = "Google Drive"
    @State private var url = ""
    @State private var notes = ""
    @State private var backups: [Submissions.Link] = []
    @State private var busy = false
    @State private var error: String?
    @State private var sent = false

    private let types = [("pdf", "📄 PDF"), ("pptx", "📊 PowerPoint"), ("docx", "📝 Word"), ("image", "🖼️ Image"), ("video", "🎬 Video")]
    private let platforms = ["Google Drive", "OneDrive", "Dropbox", "Other"]

    private var valid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && !title.trimmingCharacters(in: .whitespaces).isEmpty && (url.hasPrefix("https://") || url.hasPrefix("http://"))
    }

    var body: some View {
        ABStack {
            Form {
                if sent {
                    Section {
                        VStack(spacing: 10) {
                            Image(systemName: "checkmark.circle.fill").font(.system(size: 56)).foregroundStyle(.green)
                            Text("Submitted!").font(.title2.weight(.bold))
                            Text("The team reviews it before it goes on the campus display screen.").multilineTextAlignment(.center).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 20)
                    }
                } else {
                    Section {
                        TextField("Your name", text: $name).textContentType(.name)
                        TextField("Presentation title", text: $title)
                    } header: { Text("About it") }
                    Section {
                        Picker("Type", selection: $type) { ForEach(types, id: \.0) { Text($0.1).tag($0.0) } }
                        Picker("Stored on", selection: $platform) { ForEach(platforms, id: \.self) { Text($0).tag($0) } }
                        TextField("Link to the file (https://…)", text: $url).keyboardType(.URL).autocapitalization(.none).disableAutocorrection(true)
                    } header: { Text("The file") } footer: { Text("Share the file so anyone with the link can view it.") }
                    Section {
                        ForEach(backups.indices, id: \.self) { i in
                            TextField("Backup link", text: $backups[i].url).keyboardType(.URL).autocapitalization(.none)
                        }
                        .onDelete { backups.remove(atOffsets: $0) }
                        Button { backups.append(.init(url: "", platform: "Other", type: type, label: title)) } label: { Label("Add a backup link", systemImage: "plus") }
                    } header: { Text("Backup links (optional)") }
                    Section("Notes (optional)") { TextField("Anything the team should know", text: $notes) }
                    if let error { Section { Text(error).foregroundStyle(.red) } }
                }
            }
            .navigationTitle("Submit a file").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(sent ? "Done" : "Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if !sent { Button(busy ? "Sending…" : "Submit") { Task { await submit() } }.disabled(!valid || busy) }
                }
            }
        }
    }

    private func submit() async {
        busy = true
        let links = backups.filter { !$0.url.isEmpty }.map { ["url": $0.url, "platform": $0.platform ?? "Other", "type": type, "label": title] }
        let payload: [String: Any] = ["studentName": name, "presentationTitle": title, "fileType": type, "platform": platform, "primaryUrl": url,
                                      "notes": notes, "isBackup": !links.isEmpty, "backupLinks": links]
        do {
            let data = try JSONSerialization.data(withJSONObject: payload)
            _ = try await Submissions.get([URLQueryItem(name: "action", value: "submit"), URLQueryItem(name: "data", value: String(data: data, encoding: .utf8))])
            withAnimation { sent = true }
            Haptics.success()
            await done()
        } catch { self.error = error.localizedDescription; Haptics.error() }
        busy = false
    }
}
