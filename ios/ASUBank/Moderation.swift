import SwiftUI

/// What a report is about: "message", "user", "file" or "other"
struct ReportTarget: Identifiable {
    let id = UUID()
    let kind: String
    let targetId: String
    let name: String
}

struct BlockTarget: Identifiable {
    let id = UUID()
    let userId: String
    let name: String
}

enum Moderation {
    static let reasons: [(id: String, label: String)] = [
        ("spam", "Spam or advertising"),
        ("harassment", "Harassment or bullying"),
        ("hate", "Hate speech"),
        ("sexual", "Sexual content"),
        ("violence", "Violence or threats"),
        ("privacy", "Shares someone's private information"),
        ("copyright", "Copyright: I own this or represent the owner"),
        ("wrong", "Wrong, broken or misleading file"),
        ("other", "Something else")
    ]

    static let rules = [
        "Be respectful. No harassment, bullying, threats or hate speech.",
        "No sexual or violent content, and nothing illegal.",
        "Don't share other people's private information.",
        "Only share files you have the right to share.",
        "No spam or advertising.",
        "We have zero tolerance for abuse. Admins can remove content and ban accounts that break these rules."
    ]

    /// Paid services stay out of store builds until they use the store's own payments
    static let paidPaths: Set<String> = ["/apps/donate", "/requests/student_ads", "/requests/student_request_portal", "/requests/request", "/requests/student_request_main"]

    /// Opens the report form over whatever is on screen. A row can't carry its own sheet: long lists would hold hundreds of them.
    @MainActor static func report(_ target: ReportTarget) {
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).flatMap(\.windows)
            .first(where: \.isKeyWindow)?.rootViewController else { return }
        var top = root
        while let p = top.presentedViewController { top = p }
        weak var shown: UIViewController?
        let host = UIHostingController(rootView: ReportSheet(target: target, onClose: { shown?.dismiss(animated: true) }).tint(Color.abAccent))
        shown = host
        top.present(host, animated: true)
    }

    @MainActor static func setBlocked(_ userId: String, _ on: Bool) async throws {
        _ = try await AccountStore.shared.call("block", ["userId": userId, "on": on])
        await AccountStore.shared.refresh()
    }
}

/// Lets anyone report a message, a person, a file or a problem. Signing in isn't needed.
struct ReportSheet: View {
    let target: ReportTarget
    var onClose: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    @State private var reason = ""
    @State private var details = ""
    @State private var contact = ""
    @State private var busy = false
    @State private var sent = false
    @State private var error: String?

    var body: some View {
        ABStack {
            Form {
                if sent {
                    Section {
                        Label("Thank you. Our team will review your report.", systemImage: "checkmark.seal.fill")
                    }
                } else {
                    if !target.name.isEmpty { Section("Reporting") { Text(target.name).lineLimit(3) } }
                    Section("What's wrong?") {
                        ForEach(Moderation.reasons, id: \.id) { r in
                            Button { reason = r.id } label: {
                                HStack {
                                    Text(L(r.label)).foregroundColor(.primary)
                                    Spacer()
                                    if reason == r.id { Image(systemName: "checkmark").foregroundStyle(Color.abAccent) }
                                }
                            }
                        }
                    }
                    Section("Details (optional)") {
                        TextEditor(text: $details).frame(minHeight: 80)
                    }
                    if !account.signedIn {
                        Section("Your email or phone (optional)") {
                            TextField("So we can reply to you", text: $contact)
                                .textInputAutocapitalization(.never)
                        }
                    }
                    if let error { Section { Text(error).foregroundStyle(.red) } }
                    Section {
                        Button { Task { await send() } } label: {
                            HStack { Text("Send report"); if busy { Spacer(); ProgressView() } }
                        }
                        .disabled(reason.isEmpty || busy)
                    }
                }
            }
            .navigationTitle("Report")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(sent ? "Done" : "Cancel") { if let onClose { onClose() } else { dismiss() } }
                }
            }
        }
    }

    private func send() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await AccountsAPI.call("report", ["kind": target.kind, "targetId": target.targetId, "targetName": target.name,
                                                      "reason": reason, "details": details, "contact": contact], token: account.token)
            sent = true
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// The report sheet and the "block this person?" question, shared by the chat and profile screens
struct ModerationDialogs: ViewModifier {
    @Binding var report: ReportTarget?
    @Binding var block: BlockTarget?
    var blocked: (String) -> Void = { _ in }
    @State private var error: String?

    func body(content: Content) -> some View {
        content
            .sheet(item: $report) { ReportSheet(target: $0) }
            .confirmationDialog(
                String(format: L("Block %@?"), block?.name ?? ""),
                isPresented: Binding(get: { block != nil }, set: { if !$0 { block = nil } }),
                titleVisibility: .visible
            ) {
                Button("Block", role: .destructive) {
                    guard let b = block else { return }
                    Task {
                        do { try await Moderation.setBlocked(b.userId, true); blocked(b.userId) }
                        catch { self.error = error.localizedDescription }
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("You won't see their messages or get notifications from them. You can undo this in your account.")
            }
            .alert("Couldn't block", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(error ?? "") }
    }
}

/// Everyone agrees to the community rules once, before using the chat
struct CommunityRulesGate: ViewModifier {
    @AppStorage("ab.chat.rulesAccepted") private var accepted = false
    @State private var onScreen = false

    func body(content: Content) -> some View {
        content
            .onAppear { onScreen = true }
            .onDisappear { onScreen = false }
            .sheet(isPresented: Binding(get: { onScreen && !accepted }, set: { _ in })) {
            ABStack {
                List {
                    Section {
                        ForEach(Moderation.rules, id: \.self) { Label(L($0), systemImage: "checkmark.shield") }
                    } footer: {
                        Text("To report something, press and hold a message or open a profile.")
                    }
                    Section {
                        Button { accepted = true } label: { Text("I agree").frame(maxWidth: .infinity).font(.headline) }
                    }
                }
                .listStyle(.insetGrouped)
                .navigationTitle("Community rules")
                .navigationBarTitleDisplayMode(.inline)
            }
            .interactiveDismissDisabled()
        }
    }
}

/// People you have blocked, with a way to undo it
struct BlockedScreen: View {
    @State private var people: [[String: Any]] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(.red) }
            if loading { ProgressView() }
            else if people.isEmpty && error == nil { Text("You haven't blocked anyone.").foregroundStyle(.secondary) }
            ForEach(Array(people.enumerated()), id: \.offset) { _, p in
                HStack {
                    VStack(alignment: .leading) {
                        Text(p["displayName"] as? String ?? "")
                        Text("@\(p["username"] as? String ?? "")").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("Unblock") {
                        Task {
                            do { try await Moderation.setBlocked(p["id"] as? String ?? "", false); await load() }
                            catch { self.error = error.localizedDescription }
                        }
                    }
                    .buttonStyle(.borderless)
                }
            }
        }
        .navigationTitle("Blocked people")
        .task { await load() }
    }

    private func load() async {
        do { people = (try await AccountStore.shared.call("blockedList")["people"] as? [[String: Any]]) ?? []; error = nil }
        catch { self.error = error.localizedDescription }
        loading = false
    }
}

/// Admins: what people reported, and what to do about it
struct AdminReportsScreen: View {
    @State private var reports: [[String: Any]] = []
    @State private var showAll = false
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            Picker("", selection: $showAll) {
                Text("Open").tag(false)
                Text("All").tag(true)
            }
            .pickerStyle(.segmented)
            if let error { Text(error).foregroundStyle(.red) }
            if loading { HStack { Spacer(); ProgressView(); Spacer() } }
            else if reports.isEmpty && error == nil { Text("Nothing to review.").foregroundStyle(.secondary) }
            ForEach(Array(reports.enumerated()), id: \.offset) { _, r in row(r) }
        }
        .navigationTitle("Reports")
        .task(id: showAll) { await load() }
        .refreshable { await load() }
    }

    private func row(_ r: [String: Any]) -> some View {
        let reason = r["reason"] as? String ?? ""
        let label = Moderation.reasons.first { $0.id == reason }?.label ?? reason
        let kind = r["kind"] as? String ?? ""
        let status = r["status"] as? String ?? "open"
        return VStack(alignment: .leading, spacing: 5) {
            Text(L(label)).font(.body.weight(.semibold))
            if let n = r["targetName"] as? String, !n.isEmpty { Text(n).font(.subheadline) }
            if let t = r["targetUser"] as? String, !t.isEmpty { Text("@\(t)").font(.caption).foregroundStyle(.secondary) }
            if let d = r["details"] as? String, !d.isEmpty { Text(d).font(.caption) }
            Text("\(r["reporterName"] as? String ?? "") · \(r["contact"] as? String ?? "") · \(r["created"] as? String ?? "")")
                .font(.caption2).foregroundStyle(.tertiary)
            HStack(spacing: 14) {
                if status == "open" {
                    Button("Done") { Task { await set(r, "done") } }
                    Button("Dismiss") { Task { await set(r, "dismissed") } }
                    if kind == "post" {
                        Button("Delete post", role: .destructive) {
                            Task {
                                do {
                                    _ = try await AccountStore.shared.call("postDelete", ["id": r["targetId"] as? String ?? ""])
                                    await set(r, "done")
                                } catch { self.error = error.localizedDescription }
                            }
                        }
                    }
                    if kind == "comment" || kind == "story" {
                        Button("Delete", role: .destructive) {
                            Task {
                                do {
                                    _ = try await AccountStore.shared.call(kind == "story" ? "storyDelete" : "commentDelete", ["id": r["targetId"] as? String ?? ""])
                                    await set(r, "done")
                                } catch { self.error = error.localizedDescription }
                            }
                        }
                    }
                    if kind == "message" {
                        Button("Delete message", role: .destructive) {
                            Task {
                                do {
                                    _ = try await AccountStore.shared.call("deleteMessage", ["id": r["targetId"] as? String ?? ""])
                                    await set(r, "done")
                                } catch { self.error = error.localizedDescription }
                            }
                        }
                    }
                } else {
                    Text(L(status == "done" ? "Handled" : "Dismissed")).font(.caption).foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.borderless).font(.subheadline)
        }
        .padding(.vertical, 2)
    }

    private func set(_ r: [String: Any], _ status: String) async {
        do {
            _ = try await AccountStore.shared.call("adminReportSet", ["id": r["id"] as? String ?? "", "status": status])
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func load() async {
        let tag = showAll ? "all" : "open"
        let account = AccountStore.shared
        if reports.isEmpty, let r = account.saved("adminReports", tag: tag) {
            reports = (r["reports"] as? [[String: Any]]) ?? []
            loading = false
        }
        do {
            let r = try await account.callSaving("adminReports", ["all": showAll], tag: tag)
            reports = (r["reports"] as? [[String: Any]]) ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }
}

/// Where a paid service would be, in a store build
struct StoreNoticeView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "bag").font(.system(size: 44)).foregroundStyle(.secondary)
            Text("This service isn't available in this version of the app.").multilineTextAlignment(.center).foregroundStyle(.secondary)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.abBackground)
    }
}
