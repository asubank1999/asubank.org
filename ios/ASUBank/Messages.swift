import SwiftUI
import QuickLook
import UniformTypeIdentifiers

// Private messages between friends. Admins can write to anyone. Files stay private: the server hands them only to the two people in the chat.

struct DMMessage: Decodable, Identifiable, Equatable {
    let id: String
    let from: String
    let to: String
    var text: String
    let created: String
    var fileName: String?
    var fileType: String?
    var fileSize: Int?
    var hasFile: Bool?
    var duration: Int?
    var expires: String?
    var pending: Bool?
    var localFile: URL?

    var date: Date { Social.date(created) }
    var preview: String {
        if !text.isEmpty { return text }
        switch fileType {
        case "photo": return "📷 " + L("Photo")
        case "voice": return "🎤 " + L("Voice message")
        case "video": return "🎬 " + L("Video")
        default: return "📄 " + (fileName ?? "")
        }
    }
}

struct DMConversation: Decodable, Identifiable {
    let user: ABUser
    let last: DMMessage
    let unread: Int
    var id: String { user.id }
}

@MainActor
final class DMListModel: ObservableObject {
    @Published var conversations: [DMConversation] = []
    @Published var unread = 0
    @Published var loaded = false
    @Published var error: String?

    private func read(_ r: [String: Any]) {
        conversations = decode([DMConversation].self, r["conversations"]) ?? []
        unread = (r["unread"] as? NSNumber)?.intValue ?? 0
    }

    func load() async {
        let account = AccountStore.shared
        guard account.signedIn else { return }
        if !loaded, let r = account.saved("dmList") { read(r) }
        do {
            read(try await account.callSaving("dmList"))
            error = nil
        } catch { self.error = error.localizedDescription }
        loaded = true
    }
}

/// The conversations, as sections of the Chat tab's list
struct DMConversationRows: View {
    @ObservedObject var model: DMListModel

    var body: some View {
        Section14 {
            NavigationLink { NewMessageScreen() } label: {
                Label("New message", systemImage: "square.and.pencil").fg14(Color.abAccent)
            }
        }
        if model.conversations.isEmpty {
            Section14 {
                if model.loaded {
                    EmptyState(symbol: "paperplane", title: "No private messages yet", text: "You can message your friends. Only the two of you see the chat.")
                        .listRowBackground(Color.clear)
                } else {
                    ForEach(0..<5, id: \.self) { _ in SkeletonRow() }
                }
            }
        } else {
            Section14 {
                ForEach(model.conversations) { c in
                    NavigationLink { DMThreadScreen(userId: c.user.id, name: c.user.displayName, color: c.user.color, photo: c.user.avatar) } label: {
                        HStack(spacing: 12) {
                            Avatar(name: c.user.displayName, color: c.user.color, size: 50, admin: c.user.isAdmin, photo: c.user.avatar, online: c.user.online, peek: false)
                            VStack(alignment: .leading, spacing: 3) {
                                HStack {
                                    Text(c.user.displayName).font(.body.weight(c.unread > 0 ? .bold : .semibold)).lineLimit(1)
                                    Spacer()
                                    Text(Social.ago(c.last.created)).font(.caption).fg14(c.unread > 0 ? Color.abAccent : .secondary)
                                }
                                HStack {
                                    Text(c.last.preview).font(.subheadline).fg14(c.unread > 0 ? .primary : .secondary).lineLimit(1)
                                    Spacer()
                                    if c.unread > 0 {
                                        Text("\(c.unread)").font(.caption2.weight(.bold)).fg14(.white)
                                            .padding(.horizontal, 7).padding(.vertical, 3).bgIn14(Color.abFill, in: Capsule())
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 3)
                    }
                }
            }
        }
    }
}

/// Choosing who to write to: friends, and for admins anyone
struct NewMessageScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var friends: [ABUser] = []
    @State private var found: [ABUser] = []
    @State private var query = ""
    @State private var loaded = false

    private var shown: [ABUser] {
        let q = AB.norm(query)
        return q.isEmpty ? friends : friends.filter { AB.norm($0.displayName).contains(q) }
    }

    var body: some View {
        List {
            if shown.isEmpty && found.isEmpty && loaded {
                EmptyState(symbol: "person.2", title: "No friends yet", text: "Add friends first. Then you can message them here.").listRowBackground(Color.clear)
            }
            if !shown.isEmpty { Section14("Friends") { ForEach(shown) { row($0) } } }
            if !found.isEmpty { Section14("Everyone") { ForEach(found) { row($0) } } }
        }
        .listStyle(InsetGroupedListStyle())
        .searchable14(text: $query, prompt: "Search people")
        .navigationTitle("New message")
        .navigationBarTitleDisplayMode(.inline)
        .task14 {
            if let r = try? await account.call("friends") { friends = decode([ABUser].self, r["friends"]) ?? [] }
            loaded = true
        }
        .task14(id: query) {
            guard account.isAdmin, query.count >= 2 else { found = []; return }
            try? await Task.sleep(nanoseconds: 400_000_000)
            if Task.isCancelled { return }
            if let r = try? await account.call("searchUsers", ["q": query]) { found = decode([ABUser].self, r["people"]) ?? [] }
        }
    }

    private func row(_ u: ABUser) -> some View {
        NavigationLink { DMThreadScreen(userId: u.id, name: u.displayName, color: u.color, photo: u.avatar) } label: {
            HStack(spacing: 12) {
                Avatar(name: u.displayName, color: u.color, size: 40, admin: u.isAdmin, photo: u.avatar, online: u.online, peek: false)
                Text(u.displayName).font(.body.weight(.semibold))
            }
        }
    }
}

// MARK: - files

enum DMFiles {
    static var folder: URL {
        let d = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("dm", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    @MainActor static func local(_ m: DMMessage) async throws -> URL {
        let safe = (m.fileName ?? "file").components(separatedBy: CharacterSet(charactersIn: "/\\:")).joined(separator: "_")
        let dir = folder.appendingPathComponent(m.id, isDirectory: true)
        let file = dir.appendingPathComponent(safe)
        if FileManager.default.fileExists(atPath: file.path) { return file }
        let r = try await AccountStore.shared.call("dmFile", ["id": m.id])
        guard let s = r["data"] as? String, let data = Data(base64Encoded: s) else {
            throw AccountsAPI.Failure(message: L("File not found."))
        }
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try data.write(to: file)
        return file
    }

    static func sizeText(_ n: Int) -> String {
        n >= 1_000_000 ? String(format: "%.1f MB", Double(n) / 1_000_000) : "\(max(1, n / 1000)) KB"
    }
}

struct FilePreview: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UINavigationController {
        let q = QLPreviewController()
        q.dataSource = context.coordinator
        q.navigationItem.leftBarButtonItem = UIBarButtonItem(systemItem: .done, primaryAction: UIAction { _ in q.dismiss(animated: true) })
        return UINavigationController(rootViewController: q)
    }
    func updateUIViewController(_ uiViewController: UINavigationController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(url) }

    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(_ u: URL) { url = u }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem { url as NSURL }
    }
}

struct DocumentPicker: UIViewControllerRepresentable {
    var picked: (URL) -> Void
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let docx = UTType(filenameExtension: "docx") ?? .data
        let p = UIDocumentPickerViewController(forOpeningContentTypes: [.pdf, docx], asCopy: true)
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(picked) }

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let picked: (URL) -> Void
        init(_ p: @escaping (URL) -> Void) { picked = p }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            if let u = urls.first { picked(u) }
        }
    }
}

struct PreviewFile: Identifiable {
    let url: URL
    var id: String { url.path }
}

// MARK: - conversation

@MainActor
final class DMThreadModel: ObservableObject {
    let userId: String
    @Published var messages: [DMMessage] = []
    @Published var user: ABUser?
    @Published var canSend = true
    @Published var seenUpTo = ""
    @Published var loading = true
    @Published var error: String?
    private var poll: Task<Void, Never>?
    /// false until the first full answer from the server; until then the saved copy is on screen
    private var synced = false

    init(userId: String) { self.userId = userId }

    func start() {
        ChatNotifier.shared.openDM = userId
        // the chat as it was last time shows at once; the server's answer replaces it a moment later
        if messages.isEmpty, let r = AccountStore.shared.saved("dmThread", tag: userId) {
            messages = (decode([DMMessage].self, r["messages"]) ?? []).filter { !Disappear.expired($0.expires) }
            if let u = decode(ABUser.self, r["user"]) { user = u }
            if let c = r["canSend"] as? Bool { canSend = c }
            if !messages.isEmpty { loading = false }
        }
        poll?.cancel()
        poll = Task { [weak self] in
            while !Task.isCancelled {
                await self?.fetch()
                try? await Task.sleep(nanoseconds: 3_000_000_000)
            }
        }
    }

    func stop() {
        poll?.cancel()
        if ChatNotifier.shared.openDM == userId { ChatNotifier.shared.openDM = nil }
    }

    func fetch() async {
        var fields: [String: Any] = ["userId": userId]
        if synced, let last = messages.last(where: { $0.pending != true })?.id { fields["after"] = last }
        do {
            let full = !synced
            let r = full ? try await AccountStore.shared.callSaving("dmThread", fields, tag: userId) : try await AccountStore.shared.call("dmThread", fields)
            synced = true
            let new = decode([DMMessage].self, r["messages"]) ?? []
            let gone = Set((r["deleted"] as? [String]) ?? [])
            // a full answer replaces the saved copy (only messages still sending stay)
            let base = full ? messages.filter { $0.pending == true } : messages
            let kept = base.filter { !gone.contains($0.id) && !Disappear.expired($0.expires) }
            var list = kept.filter { $0.pending != true }
            let have = Set(list.map(\.id))
            list += new.filter { !have.contains($0.id) && !gone.contains($0.id) && !Disappear.expired($0.expires) }
            list += kept.filter { $0.pending == true }
            if list != messages { withAnimation(.easeOut(duration: 0.2)) { messages = list } }
            if let u = decode(ABUser.self, r["user"]) { user = u }
            if let c = r["canSend"] as? Bool { canSend = c }
            seenUpTo = r["seenUpTo"] as? String ?? seenUpTo
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    /// The message shows at once and is replaced by the real one when the server has it
    func send(text: String, outgoing: ChatOutgoing? = nil, expiresIn: Int = 0) async -> Bool {
        let me = AccountStore.shared.user?.id ?? ""
        let temp = DMMessage(id: "local-" + UUID().uuidString, from: me, to: userId, text: text, created: ISO8601DateFormatter.ab.string(from: Date()),
                             fileName: outgoing?.fileName, fileType: outgoing?.type, fileSize: outgoing?.size, hasFile: outgoing != nil,
                             duration: outgoing?.duration, pending: true, localFile: outgoing?.localFile)
        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { messages.append(temp) }
        var fields: [String: Any] = ["userId": userId, "text": text, "expiresIn": expiresIn]
        do {
            if let outgoing {
                guard let data = await outgoing.load() else { throw AccountsAPI.Failure(message: L("Couldn't send this file.")) }
                fields["file"] = data
                fields["fileName"] = outgoing.fileName
                fields["duration"] = outgoing.duration
            }
            let r = try await AccountStore.shared.call("dmSend", fields)
            messages.removeAll { $0.id == temp.id }
            if var m = decode(DMMessage.self, r["message"]), !messages.contains(where: { $0.id == m.id }) {
                m.localFile = outgoing?.localFile
                let at = messages.firstIndex { $0.pending == true } ?? messages.endIndex
                messages.insert(m, at: at)
            }
            error = nil
            return true
        } catch {
            withAnimation { messages.removeAll { $0.id == temp.id } }
            self.error = error.localizedDescription
            return false
        }
    }

    /// Gone at once for me; the other person's phone removes it on its next check
    func delete(_ m: DMMessage) async {
        let before = messages
        withAnimation { messages.removeAll { $0.id == m.id } }
        do { _ = try await AccountStore.shared.call("dmDelete", ["id": m.id]) }
        catch {
            messages = before
            self.error = error.localizedDescription
        }
    }
}

struct DMThreadScreen: View {
    let userId: String
    let name: String
    let color: String
    let photo: String?
    @StateObject private var model: DMThreadModel
    @ObservedObject private var account = AccountStore.shared
    @State private var text = ""
    @State private var preview: PreviewFile?
    @State private var disappear = 0
    @State private var reporting: ReportTarget?
    @State private var blocking: BlockTarget?
    @State private var opening: String?
    @State private var focused: Bool = false

    init(userId: String, name: String, color: String, photo: String? = nil) {
        self.userId = userId
        self.name = name
        self.color = color
        self.photo = photo
        _model = StateObject(wrappedValue: DMThreadModel(userId: userId))
    }

    private var lastSeenMine: String? {
        guard !model.seenUpTo.isEmpty else { return nil }
        return model.messages.last { $0.from == account.user?.id && $0.created <= model.seenUpTo }?.id
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 2) {
                    if model.loading && model.messages.isEmpty {
                        SkeletonBubbles()
                    } else if model.messages.isEmpty {
                        VStack(spacing: 8) {
                            Avatar(name: name, color: color, size: 72, photo: photo, peek: false)
                            Text(name).font(.headline)
                            Text("Say hello. Only the two of you see this chat.").font(.subheadline).fg14(.secondary).multilineTextAlignment(.center)
                        }
                        .padding(.top, 60)
                    }
                    ForEach(Array(model.messages.enumerated()), id: \.element.id) { i, m in
                        let prev = i > 0 ? model.messages[i - 1] : nil
                        let next = i + 1 < model.messages.count ? model.messages[i + 1] : nil
                        if prev == nil || !Calendar.current.isDate(prev!.date, inSameDayAs: m.date) {
                            ChatDayLabel(date: m.date)
                        }
                        bubble(m, joinedAbove: prev?.from == m.from, joinedBelow: next?.from == m.from).id(m.id)
                        if m.id == lastSeenMine {
                            Text("Seen").font(.caption2).fg14(.secondary).frame(maxWidth: .infinity, alignment: .trailing).padding(.trailing, 6)
                        }
                    }
                    Color.clear.frame(height: 4).id("bottom")
                }
                .padding(.horizontal, 10)
            }
            .background(Color.abBackground)
            .dismissKeyboardOnScroll()
            .onChange(of: model.messages.count) { _ in withAnimation { proxy.scrollTo("bottom", anchor: .bottom) } }
            .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { proxy.scrollTo("bottom", anchor: .bottom) } }
        }
        .safeAreaInset14(edge: .bottom) { composer }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                NavigationLink { ProfileScreen(userId: userId, fallbackName: name, fallbackColor: color) } label: {
                    HStack(spacing: 8) {
                        Avatar(name: name, color: color, size: 32, photo: model.user?.avatar ?? photo, online: model.user?.online, peek: false)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(name).font(.subheadline.weight(.semibold)).fg14(.primary).lineLimit(1)
                            if let u = model.user {
                                Text(u.online == true ? L("Online now") : lastSeenText(u.lastSeen))
                                    .font(.caption2).fg14(u.online == true ? Color.green : .secondary).lineLimit(1)
                            }
                        }
                    }
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
        .sheet(item: $preview) { FilePreview(url: $0.url).ignoresSafeArea() }
        .modifier(ModerationDialogs(report: $reporting, block: $blocking, blocked: { _ in }))
    }

    @ViewBuilder private func bubble(_ m: DMMessage, joinedAbove: Bool, joinedBelow: Bool) -> some View {
        let mine = m.from == account.user?.id
        HStack(alignment: .bottom, spacing: 8) {
            if mine { Spacer(minLength: 56) }
            VStack(alignment: .trailing, spacing: 4) {
                if m.hasFile == true { attachment(m, mine: mine) }
                if !m.text.isEmpty {
                    Text(m.text).font(.body).fg14(mine ? .white : .primary)
                        .multilineTextAlignment(.leading)
                }
                HStack(spacing: 4) {
                    ExpiryTag(expires: m.expires, tint: mine ? .white.opacity(0.8) : .secondary)
                    if m.pending == true { Image(systemName: "clock").font(.caption2).fg14(mine ? .white.opacity(0.8) : .secondary) }
                    Text(m.date, style: .time).font(.caption2).fg14(mine ? .white.opacity(0.75) : .secondary)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .chatBubble(mine: mine, joinedAbove: joinedAbove, joinedBelow: joinedBelow)
            .opacity(m.pending == true ? 0.72 : 1)
            .contextMenu {
                if !m.text.isEmpty { Button { UIPasteboard.general.string = m.text } label: { Label("Copy", systemImage: "doc.on.doc") } }
                if mine {
                    if m.pending != true {
                        Button() { Task { await model.delete(m) } } label: { Label("Delete for everyone", systemImage: "trash") }
                    }
                } else {
                    Button { reporting = ReportTarget(kind: "dm", targetId: m.id, name: String(m.preview.prefix(120))) } label: { Label("Report message", systemImage: "flag") }
                    if model.user?.isAdmin != true {
                        Button() { blocking = BlockTarget(userId: userId, name: name) } label: { Label("Block", systemImage: "hand.raised") }
                    }
                }
            }
            if !mine { Spacer(minLength: 56) }
        }
        .padding(.top, joinedAbove ? 1 : 6)
    }

    @ViewBuilder private func attachment(_ m: DMMessage, mine: Bool) -> some View {
        if m.fileType == "photo" || m.fileType == "voice" || m.fileType == "video" {
            ChatAttachment(id: m.id, type: m.fileType ?? "", duration: m.duration ?? 0, mine: mine, localFile: m.localFile,
                           fetch: { try await DMFiles.local(m) }, open: { preview = PreviewFile(url: $0) })
        } else {
            Button { Task { await open(m) } } label: {
                HStack(spacing: 10) {
                    Image(systemName: m.fileType == "pdf" ? "doc.richtext.fill" : "doc.text.fill").font(.title2)
                        .fg14(mine ? Color.white : (m.fileType == "pdf" ? Color.red : Color.blue))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(m.fileName ?? "").font(.subheadline.weight(.semibold)).lineLimit(2).multilineTextAlignment(.leading)
                        Text(DMFiles.sizeText(m.fileSize ?? 0)).font(.caption2).opacity(0.75)
                    }
                    if opening == m.id { ABDots().tint14(mine ? .white : nil) }
                }
                .fg14(mine ? .white : .primary)
                .frame(width: 220, alignment: .leading)
            }
            .buttonStyle(PlainButtonStyle())
        }
    }

    private func open(_ m: DMMessage) async {
        opening = m.id
        defer { opening = nil }
        do { preview = PreviewFile(url: try await DMFiles.local(m)) }
        catch { model.error = error.localizedDescription }
    }

    private var composer: some View {
        VStack(spacing: 0) {
            if let e = model.error {
                Text(e).font(.caption).fg14(.white).padding(6).frame(maxWidth: .infinity).background(Color.red.opacity(0.85))
            }
            if model.canSend {
                ChatInputBar(text: $text, focused: $focused, allowDocs: true, disappear: $disappear, send: sendText,
                             sendMedia: { o in let e = disappear; Task { _ = await model.send(text: "", outgoing: o, expiresIn: e) } },
                             fail: { model.error = $0 })
            } else {
                Label("You can only send private messages to friends.", systemImage: "lock.fill").font(.footnote).fg14(.secondary).padding(12)
            }
        }
        .background(Color.mat14)
    }

    private func sendText() {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        text = ""
        Haptics.tap()
        let e = disappear
        Task {
            if !(await model.send(text: t, expiresIn: e)), text.isEmpty { text = t }
        }
    }
}

// MARK: - parts shared with the rooms

/// A rounded rectangle with its own radius for each corner
struct BubbleCorners: Shape {
    var tl: CGFloat, tr: CGFloat, bl: CGFloat, br: CGFloat

    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX + tl, y: r.minY))
        p.addLine(to: CGPoint(x: r.maxX - tr, y: r.minY))
        p.addArc(center: CGPoint(x: r.maxX - tr, y: r.minY + tr), radius: tr, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY - br))
        p.addArc(center: CGPoint(x: r.maxX - br, y: r.maxY - br), radius: br, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        p.addLine(to: CGPoint(x: r.minX + bl, y: r.maxY))
        p.addArc(center: CGPoint(x: r.minX + bl, y: r.maxY - bl), radius: bl, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        p.addLine(to: CGPoint(x: r.minX, y: r.minY + tl))
        p.addArc(center: CGPoint(x: r.minX + tl, y: r.minY + tl), radius: tl, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        p.closeSubpath()
        return p
    }
}

extension View {
    /// The look of every chat message: accent gradient for mine, a soft card for others, joined corners inside a run of messages
    func chatBubble(mine: Bool, joinedAbove: Bool, joinedBelow: Bool) -> some View {
        let big: CGFloat = 20, small: CGFloat = 6
        let shape = BubbleCorners(
            tl: !mine && joinedAbove ? small : big,
            tr: mine && joinedAbove ? small : big,
            bl: !mine && joinedBelow ? small : big,
            br: mine && joinedBelow ? small : big
        )
        return self
            .bgIn14(mine ? AnyStyle14(Color.abFill.abGradient) : AnyStyle14(Color.abSurface), in: shape)
            .shadow(color: .black.opacity(mine ? 0 : 0.06), radius: 3, y: 1)
    }
}

struct ChatDayLabel: View {
    let date: Date
    private var text: String {
        if Calendar.current.isDateInToday(date) { return L("Today") }
        if Calendar.current.isDateInYesterday(date) { return L("Yesterday") }
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("EEEEdMMM")
        return f.string(from: date)
    }
    var body: some View {
        Text(text).font(.caption2.weight(.semibold)).fg14(.secondary)
            .padding(.horizontal, 12).padding(.vertical, 5)
            .bgIn14(Color.mat14, in: Capsule())
            .padding(.vertical, 10)
    }
}

/// The message box at the bottom of a chat
struct ChatComposerBar: View {
    @Binding var text: String
    var focused: Binding<Bool>
    var busy: Bool
    var placeholder: LocalizedStringKey
    var sendSymbol = "arrow.up"
    var leading: AnyView?
    var send: () -> Void

    private var empty: Bool { text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        HStack(spacing: 8) {
            if let leading { leading }
            TextField(placeholder, text: $text)
                .focused14(focused)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .bgIn14(Color.abSurface, in: Capsule())
                .overlay(Capsule().stroke(Color.secondary.opacity(0.2), lineWidth: 1))
                
                .onSubmit14(send)
            Button(action: send) {
                Group {
                    if busy { ABDots().tint14(.white) } else { Image(systemName: sendSymbol).font(.headline.weight(.bold)).fg14(.white) }
                }
                .frame(width: 38, height: 38)
                .bgIn14(Color.abFill.abGradient, in: Circle())
                .opacity(empty && !busy ? 0.4 : 1)
            }
            .disabled(empty || busy)
            .accessibilityLabel(Text("Send"))
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
    }
}
