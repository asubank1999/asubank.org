import SwiftUI

/// The Admin tab (admins only): stats, users, announcements, uploads and the action log
struct AdminTab: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var stats: [String: Int] = [:]
    @State private var error: String?
    @State private var announcing = false

    private let tiles: [(String, String, String, UInt32)] = [
        ("users", "Users", "person.2.fill", 0x0a84ff), ("activeToday", "Active today", "bolt.fill", 0x1f8a3e),
        ("newWeek", "New this week", "person.badge.plus", 0xaf52de), ("messagesToday", "Messages today", "bubble.left.fill", 0xe8590c),
        ("banned", "Banned", "hand.raised.fill", 0xff2d55), ("admins", "Admins", "checkmark.seal.fill", 0x8C1D40),
    ]

    var body: some View {
        ABStack {
            List {
                if !account.isAdmin {
                    Text("Only admins can see this.").foregroundStyle(.secondary)
                } else {
                    Section {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10), GridItem(.flexible())], spacing: 10) {
                            ForEach(tiles, id: \.0) { t in
                                VStack(alignment: .leading, spacing: 6) {
                                    Image(systemName: t.2).foregroundStyle(.white).frame(width: 28, height: 28)
                                        .background(Color(UIColor(hex: t.3)).abGradient, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                                    Text(stats[t.0].map(String.init) ?? "–").font(.title2.weight(.bold)).monospacedDigit()
                                    Text(t.1).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(10)
                                .background(Color(UIColor.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            }
                        }
                        .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                    } header: { Text("Overview") }

                    Section("Manage") {
                        NavigationLink { AdminUsersScreen() } label: { Label("Users", systemImage: "person.2") }
                        Button { announcing = true } label: { Label("Send an announcement", systemImage: "megaphone") }
                        NavigationLink { LatestScreen() } label: { Label("Uploads (long-press a file to delete)", systemImage: "tray.full") }
                        NavigationLink { SubmissionsScreen(admin: true) } label: { Label("Student submissions", systemImage: "rectangle.stack") }
                        NavigationLink { AdminLogScreen() } label: { Label("Admin activity", systemImage: "clock.arrow.circlepath") }
                    }
                    Section {
                        Label("Long-press a chat message to delete it or ban its sender.", systemImage: "hand.tap")
                        Label("Users' accounts live in the \"ASU Bank Accounts\" sheet in necro1442's Drive.", systemImage: "tablecells")
                    } header: { Text("Tips") }.font(.subheadline)
                    if let error { Section { Text(error).foregroundStyle(.red) } }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Admin")
            .refreshable { await load() }
            .task { await load() }
            .sheet(isPresented: $announcing) { AnnounceSheet() }
        }
    }

    private func load() async {
        do {
            let r = try await account.call("adminStats")
            stats = (r["stats"] as? [String: Any] ?? [:]).compactMapValues { ($0 as? NSNumber)?.intValue }
            error = nil
        } catch { self.error = error.localizedDescription }
    }
}

private struct AnnounceSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ABStack {
            Form {
                Section {
                    TextEditor(text: $text).frame(minHeight: 140)
                } header: { Text("Message") } footer: { Text("Posted in 📣 Announcements. Everyone with notifications on gets it, even if they only want announcements.") }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle("Announcement").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Sending…" : "Send") {
                        Task {
                            busy = true
                            do { _ = try await AccountStore.shared.call("send", ["room": "announcements", "text": text]); Haptics.success(); dismiss() }
                            catch { self.error = error.localizedDescription }
                            busy = false
                        }
                    }.disabled(busy || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

// MARK: - users

struct AdminUsersScreen: View {
    @State private var users: [ABUser] = []
    @State private var query = ""
    @State private var loading = true
    @State private var error: String?
    @State private var filter = 0

    private var shown: [ABUser] {
        users.filter { u in
            (filter == 0 || (filter == 1 && u.isAdmin) || (filter == 2 && u.banned == true))
        }
    }

    var body: some View {
        List {
            Picker("Show", selection: $filter) {
                Text("All").tag(0); Text("Admins").tag(1); Text("Banned").tag(2)
            }
            .pickerStyle(.segmented)
            .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            if loading && users.isEmpty { HStack { Spacer(); ProgressView(); Spacer() } }
            if let error { Text(error).foregroundStyle(.red) }
            Section("\(shown.count) users") {
                ForEach(shown) { u in
                    NavigationLink { AdminUserScreen(user: u) { await load() } } label: {
                        HStack(spacing: 12) {
                            Avatar(name: u.displayName, color: u.color, size: 36, admin: u.isAdmin)
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(u.displayName).font(.body.weight(.semibold))
                                    if u.banned == true { Badge(text: "BANNED", color: .red) }
                                }
                                Text("@\(u.username)" + (u.uniId?.isEmpty == false ? " · \(u.uniId!)" : "")).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Users")
        .searchable(text: $query, prompt: "Username, email or ID")
        .onSubmit(of: .search) { Task { await load() } }
        .onChange(of: query) { q in if q.isEmpty { Task { await load() } } }
        .refreshable { await load() }
        .task { await load() }
    }

    private func load() async {
        loading = true
        do {
            let r = try await AccountStore.shared.call("adminUsers", ["q": query])
            users = decode([ABUser].self, r["users"]) ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }
}

struct AdminUserScreen: View {
    let user: ABUser
    var changed: () async -> Void
    @ObservedObject private var account = AccountStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var busy = false
    @State private var error: String?
    @State private var tempPassword: String?
    @State private var reason = ""
    @State private var confirmDelete = false
    @State private var banned: Bool
    @State private var isAdmin: Bool

    init(user: ABUser, changed: @escaping () async -> Void) {
        self.user = user
        self.changed = changed
        _banned = State(initialValue: user.banned == true)
        _isAdmin = State(initialValue: user.isAdmin)
    }

    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    Avatar(name: user.displayName, color: user.color, size: 56, admin: isAdmin)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(user.displayName).font(.title3.weight(.semibold))
                        Text("@\(user.username)").foregroundStyle(.secondary)
                    }
                }
            }
            Section("Details") {
                info("Email", user.email)
                info("University ID", user.uniId)
                info("Joined", user.created.map(short))
                info("Last seen", user.lastSeen.map(short))
                if banned, let r = user.banReason, !r.isEmpty { info("Ban reason", r) }
            }
            if user.id != account.user?.id {
                Section {
                    if banned {
                        Button { Task { await act("adminBan", ["banned": false]) { banned = false } } } label: { Label("Unban", systemImage: "checkmark.circle") }
                    } else {
                        TextField("Reason (optional)", text: $reason)
                        Button(role: .destructive) { Task { await act("adminBan", ["banned": true, "reason": reason]) { banned = true } } } label: {
                            Label("Ban this user", systemImage: "hand.raised.fill")
                        }
                    }
                } header: { Text("Moderation") }
                Section {
                    Button { Task { await reset() } } label: { Label("Reset password", systemImage: "key.fill") }
                    if let p = tempPassword {
                        HStack {
                            Text(p).font(.body.monospaced().weight(.semibold)).textSelection(.enabled)
                            Spacer()
                            Button("Copy") { UIPasteboard.general.string = p; Haptics.success() }
                        }
                    }
                } footer: { Text(tempPassword == nil ? "Makes a temporary password for you to send them. They're asked to change it." : "Send this to the student. It's shown only once.") }
                Section {
                    Button { Task { await act("adminSetRole", ["role": isAdmin ? "user" : "admin"]) { isAdmin.toggle() } } } label: {
                        Label(isAdmin ? "Remove admin" : "Make admin", systemImage: "checkmark.seal")
                    }
                    Button(role: .destructive) { confirmDelete = true } label: { Label("Delete account", systemImage: "trash") }
                }
            }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(user.username)
        .navigationBarTitleDisplayMode(.inline)
        .disabled(busy)
        .overlay { if busy { ProgressView() } }
        .confirmationDialog("Delete @\(user.username)?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete account", role: .destructive) { Task { await act("adminDeleteUser", [:]) { dismiss() } } }
        } message: { Text("This can't be undone.") }
    }

    private func info(_ k: String, _ v: String?) -> some View {
        HStack { Text(k); Spacer(); Text(v?.isEmpty == false ? v! : "–").foregroundStyle(.secondary).textSelection(.enabled) }
    }

    private func short(_ iso: String) -> String {
        guard let d = ISO8601DateFormatter.ab.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else { return iso }
        return d.formatted(date: .abbreviated, time: .shortened)
    }

    private func act(_ action: String, _ fields: [String: Any], done: () -> Void) async {
        busy = true
        var f = fields
        f["userId"] = user.id
        do { _ = try await account.call(action, f); done(); error = nil; Haptics.success(); await changed() }
        catch { self.error = error.localizedDescription; Haptics.error() }
        busy = false
    }

    private func reset() async {
        busy = true
        do {
            let r = try await account.call("adminResetPassword", ["userId": user.id])
            tempPassword = r["password"] as? String
            error = nil
        } catch { self.error = error.localizedDescription }
        busy = false
    }
}

struct AdminLogScreen: View {
    @State private var log: [[String: Any]] = []
    @State private var error: String?

    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(.red) }
            if log.isEmpty && error == nil { Text("Nothing yet.").foregroundStyle(.secondary) }
            ForEach(Array(log.enumerated()), id: \.offset) { _, r in
                VStack(alignment: .leading, spacing: 3) {
                    Text("\(r["action"] as? String ?? "") · \(r["target"] as? String ?? "")").font(.body.weight(.semibold))
                    if let d = r["detail"] as? String, !d.isEmpty { Text(d).font(.caption).foregroundStyle(.secondary) }
                    Text(r["time"] as? String ?? "").font(.caption2).foregroundStyle(.tertiary)
                }
            }
        }
        .navigationTitle("Admin activity")
        .task {
            do { log = (try await AccountStore.shared.call("adminLog")["log"] as? [[String: Any]]) ?? [] } catch { self.error = error.localizedDescription }
        }
    }
}

/// Moves an upload to the Drive trash (admins; only files in the upload folder)
enum AdminUploads {
    @MainActor static func delete(_ url: String) async throws {
        guard let id = AB.driveId(url) else { throw AccountsAPI.Failure(message: "This isn't a Google Drive file.") }
        _ = try await AccountStore.shared.call("adminDeleteUpload", ["fileId": id])
    }
}
