import SwiftUI

// More > Safety & legal: the report form, your reports, the rules, and the record of when you accepted the terms.

/// The dated record that someone accepted the Terms of Service. The server's clock is what counts.
enum TermsProof {
    /// Same as TERMS_VERSION in accounts-api.gs. A new version needs a new `WelcomeTermsView.key` too.
    static let version = "2026-11-06"
    private static let dateKey = "ab.terms.date.v2"
    private static let deviceKey = "ab.device.id"
    private static var d: UserDefaults { .standard }

    static var device: String {
        if let s = d.string(forKey: deviceKey) { return s }
        let s = UUID().uuidString
        d.set(s, forKey: deviceKey)
        return s
    }

    static var acceptedAt: Date? { d.object(forKey: dateKey) as? Date }

    @MainActor static func accepted() {
        d.set(Date(), forKey: dateKey)
        Task { await sync() }
    }

    /// Sends the record once for this device, and once for each account that signs in on it
    @MainActor static func sync() async {
        guard let date = acceptedAt else { return }
        let who = AccountStore.shared.user?.id ?? "device"
        let sentKey = "ab.terms.sent.\(version).\(who)"
        guard !d.bool(forKey: sentKey) else { return }
        let fields: [String: Any] = [
            "device": device, "version": version, "source": "ios", "appVersion": AppConfig.current,
            "acceptedAt": ISO8601DateFormatter().string(from: date)
        ]
        if (try? await AccountsAPI.call("termsAccept", fields, token: AccountStore.shared.token)) != nil {
            d.set(true, forKey: sentKey)
        }
    }

    static func text(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = AppLanguage.locale
        f.dateStyle = .long
        f.timeStyle = .short
        return String(format: L("You accepted the Terms of Service on %@."), f.string(from: date))
    }
}

struct SafetyScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var showTerms = false
    @State private var serverDate: String?

    var body: some View {
        List {
            Section14 {
                NavigationLink { ReportCenterScreen() } label: { Label("Report something", systemImage: "exclamationmark.bubble.fill") }
                if account.signedIn {
                    NavigationLink { MyReportsScreen() } label: { Label("My reports", systemImage: "tray.full") }
                    NavigationLink { BlockedScreen() } label: { Label("Blocked people", systemImage: "hand.raised") }
                }
            } header: { Text("Feel safe here") } footer: {
                Text("Report anything, inside or outside the app. You can report without an account, and only the UniHub team reads your report.")
            }

            Section14 {
                Label("If someone is in danger right now, call 911. UniHub can't reach emergency services.", systemImage: "phone.fill")
                    .font(.subheadline)
                Button { if let u = URL(string: "tel:911") { UIApplication.shared.open(u) } } label: { Label("Call 911", systemImage: "phone.arrow.up.right") }
            } header: { Text("Emergency") }

            Section14 {
                if let date = TermsProof.acceptedAt {
                    Label(TermsProof.text(date), systemImage: "checkmark.seal.fill").font(.subheadline)
                }
                if let serverDate { Text(serverDate).font(.caption).fg14(.secondary) }
                Button { showTerms = true } label: { Label("About & terms", systemImage: "heart.text.square") }
                RouteLink(Route.page("/legal/uni_tos", "Terms of Service")) { Label("Terms of Service", systemImage: "doc.text") }
                RouteLink(Route.page("/legal/privacy", "Privacy policy")) { Label("Privacy policy", systemImage: "hand.raised") }
                RouteLink(Route.page("/legal/community", "Community rules")) { Label("Community rules", systemImage: "checkmark.shield") }
                RouteLink(Route.page("/legal/copyright", "Copyright and removal requests")) { Label("Copyright and removal requests", systemImage: "c.circle") }
                if account.signedIn {
                    RouteLink(Route.page("/legal/delete-account", "Delete your account")) { Label("Delete your account", systemImage: "person.crop.circle.badge.xmark") }
                }
            } header: { Text("Rules and your rights") }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Safety & legal")
        .sheet(isPresented: $showTerms) { WelcomeTermsView { showTerms = false } }
        .task14 {
            await TermsProof.sync()
            guard account.signedIn, let r = try? await account.call("termsStatus"),
                  let list = r["accepted"] as? [[String: Any]], let at = list.first?["at"] as? String,
                  let date = ISO8601DateFormatter.ab.date(from: at) ?? ISO8601DateFormatter().date(from: at) else { return }
            let f = DateFormatter()
            f.locale = AppLanguage.locale
            f.dateStyle = .long
            f.timeStyle = .short
            serverDate = String(format: L("Saved on your account: %@"), f.string(from: date))
        }
    }
}

/// The full report form. Works without an account.
struct ReportCenterScreen: View {
    private static let reasons: [(id: String, label: String)] = [
        ("safety", "Someone's safety is at risk"),
        ("harassment", "Harassment or bullying"),
        ("hate", "Hate speech"),
        ("sexual", "Sexual content"),
        ("violence", "Violence or threats"),
        ("privacy", "Shares someone's private information"),
        ("impersonation", "Pretending to be someone else"),
        ("scam", "Scam or fraud"),
        ("copyright", "Copyright: I own this or represent the owner"),
        ("wrong", "Wrong, broken or misleading file"),
        ("cheating", "Cheating or a rule of the university"),
        ("spam", "Spam or advertising"),
        ("bug", "A bug in the app or website"),
        ("account", "A problem with my account"),
        ("other", "Something else")
    ]
    private static let places: [(id: String, label: String)] = [
        ("app", "In the app"), ("website", "On the website"), ("whatsapp", "In a WhatsApp group"),
        ("campus", "On campus"), ("elsewhere", "Somewhere else")
    ]

    @ObservedObject private var account = AccountStore.shared
    @State private var reason = ""
    @State private var place = "app"
    @State private var what = ""
    @State private var when = ""
    @State private var details = ""
    @State private var contact = ""
    @State private var anonymous = false
    @State private var truthful = false
    @State private var photo: UIImage?
    @State private var picking = false
    @State private var busy = false
    @State private var error: String?
    @State private var reference: String?

    private var ready: Bool {
        !reason.isEmpty && truthful && !busy && details.trimmingCharacters(in: .whitespacesAndNewlines).count >= 10
    }

    var body: some View {
        Form {
            if let reference {
                Section14 {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("We got your report", systemImage: "checkmark.seal.fill").font(.headline).fg14(.green)
                        Text("Only the UniHub team reads it. Urgent reports are looked at first.").font(.subheadline)
                        Text(String(format: L("Reference: %@"), reference)).font(.body.monospaced())
                    }
                    .padding(.vertical, 4)
                    Button { UIPasteboard.general.string = reference; Haptics.success() } label: { Label("Copy link", systemImage: "doc.on.doc") }
                    if account.signedIn {
                        NavigationLink { MyReportsScreen() } label: { Label("My reports", systemImage: "tray.full") }
                    }
                }
            } else {
                Section14 {
                    Label("If someone is in danger right now, call 911. UniHub can't reach emergency services.", systemImage: "exclamationmark.triangle.fill")
                        .font(.subheadline)
                }
                Section14 {
                    Picker("What is it about?", selection: $reason) {
                        Text("Choose").tag("")
                        ForEach(Self.reasons, id: \.id) { Text(L($0.label)).tag($0.id) }
                    }
                    Picker("Where did it happen?", selection: $place) {
                        ForEach(Self.places, id: \.id) { Text(L($0.label)).tag($0.id) }
                    }
                }
                Section14("What happened?") {
                    TextEditor(text: $details).frame(minHeight: 120)
                }
                Section14 {
                    TextField("Who or what: a name, username, file or link", text: $what)
                    TextField("When did it happen?", text: $when)
                } header: { Text("Details (optional)") }
                Section14 {
                    if let photo {
                        Image(uiImage: photo).resizable().scaledToFit().frame(maxHeight: 180)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        Button("Remove") { self.photo = nil }
                    } else {
                        Button { picking = true } label: { Label("Add a screenshot", systemImage: "photo") }
                    }
                } footer: { Text("Screenshots stay private. Only the UniHub team can open them.") }
                Section14 {
                    if account.signedIn {
                        Toggle("Hide my name from the team", isOn: $anonymous).tint14(.abAccent)
                    }
                    if !account.signedIn || anonymous {
                        TextField("Your email or phone (optional)", text: $contact)
                            .autocapitalization(.none).disableAutocorrection(true)
                    }
                } footer: {
                    Text(L(account.signedIn
                           ? "With your name hidden, you can still follow the report under My reports."
                           : "Leave a way to reach you if you want a reply. Without one, we can't answer you."))
                }
                Section14 {
                    Toggle("What I wrote is true, to the best of my knowledge", isOn: $truthful).tint14(.abAccent)
                    if let error { Text(error).fg14(.red) }
                    Button { Task { await send() } } label: {
                        HStack { Text("Send report").fontWeight(.semibold); if busy { Spacer(); ABDots() } }
                    }
                    .disabled(!ready)
                } footer: { Text("False reports made to harm someone break the community rules.") }
            }
        }
        .navigationTitle("Report something")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $picking) { AvatarPicker { photo = $0 } }
    }

    private func send() async {
        busy = true
        defer { busy = false }
        var fields: [String: Any] = [
            "kind": place == "app" ? "app" : "outside",
            "targetId": "",
            "targetName": what.trimmingCharacters(in: .whitespacesAndNewlines),
            "reason": reason,
            "place": place,
            "details": details.trimmingCharacters(in: .whitespacesAndNewlines) + (when.isEmpty ? "" : "\n\nWhen: " + when),
            "contact": contact.trimmingCharacters(in: .whitespaces),
            "anonymous": anonymous
        ]
        if let photo, let data = PhotoTools.jpegBase64(photo, maxSide: 1600) { fields["photo"] = data }
        do {
            let r = try await AccountsAPI.call("report", fields, token: account.token)
            reference = r["ref"] as? String ?? "-"
            error = nil
            Haptics.success()
        } catch {
            self.error = error.localizedDescription
            Haptics.error()
        }
    }
}

/// The reports this account sent, with what happened to them
struct MyReportsScreen: View {
    private struct Item: Decodable, Identifiable {
        var ref: String
        var created: String?
        var reason: String?
        var targetName: String?
        var status: String?
        var reply: String?
        var id: String { ref }
    }

    @ObservedObject private var account = AccountStore.shared
    @State private var items: [Item] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            if let error { Text(error).fg14(.red) }
            if loading && items.isEmpty { HStack { Spacer(); ABDots(); Spacer() } }
            else if items.isEmpty && error == nil {
                EmptyState(symbol: "tray", title: L("No reports yet"), text: L("Reports you send show here."))
                    .listRowBackground(Color.clear)
            }
            ForEach(items) { r in
                VStack(alignment: .leading, spacing: 5) {
                    HStack {
                        Text(L(Moderation.reasons.first { $0.id == r.reason }?.label ?? (r.reason ?? ""))).font(.body.weight(.semibold))
                        Spacer()
                        Badge(text: L(statusName(r.status)), color: r.status == "open" ? .orange : .green)
                    }
                    if let n = r.targetName, !n.isEmpty { Text(n).font(.subheadline).lineLimit(2) }
                    if let t = r.reply, !t.isEmpty {
                        Label(t, systemImage: "bubble.left.fill").font(.subheadline)
                    }
                    Text(String(format: L("Reference: %@"), r.ref) + " · " + String((r.created ?? "").prefix(10)))
                        .font(.caption).fg14(.secondary)
                }
                .padding(.vertical, 2)
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("My reports")
        .task14 { await load() }
        .refreshable14 { await load() }
    }

    private func statusName(_ s: String?) -> String {
        switch s {
        case "done": return "Handled"
        case "dismissed": return "Closed"
        default: return "Open"
        }
    }

    private func load() async {
        do {
            let r = try await account.call("myReports")
            items = decode([Item].self, r["reports"]) ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }
}

/// Admins: when an account accepted the terms
struct AdminTermsScreen: View {
    private struct Row: Decodable, Identifiable {
        var at: String
        var version: String?
        var source: String?
        var appVersion: String?
        var device: String?
        var id: String { at + (device ?? "") }
    }

    @ObservedObject private var account = AccountStore.shared
    @State private var query = ""
    @State private var rows: [Row] = []
    @State private var who: String?
    @State private var busy = false
    @State private var error: String?
    @State private var people: [ABUser] = []
    @State private var peopleLoaded = false

    /// everyone, or the people the search box matches
    private var shown: [ABUser] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return people }
        return people.filter { u in
            [u.username, u.displayName, u.email ?? "", u.uniId ?? ""].contains { $0.lowercased().contains(q) }
        }
    }

    var body: some View {
        List {
            Section14 {
                TextField("Username, email or university ID", text: $query)
                    .autocapitalization(.none).disableAutocorrection(true)
                    .onSubmit14 { if shown.isEmpty { Task { await find(query) } } }
                if busy { ABDots() }
                if let error { Text(error).fg14(.red) }
            }
            if let who {
                Section14("@\(who)") {
                    if rows.isEmpty { Text("No record for this account yet.").fg14(.secondary) }
                    ForEach(rows) { r in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(r.at).font(.subheadline.monospaced())
                            Text("\(r.version ?? "") · \(r.source ?? "") \(r.appVersion ?? "")").font(.caption).fg14(.secondary)
                        }
                    }
                }
            }
            Section14(people.isEmpty ? "" : String(format: L("Everyone (%d)"), shown.count)) {
                if !peopleLoaded {
                    ForEach(0..<5, id: \.self) { _ in SkeletonRow() }
                } else if shown.isEmpty {
                    Button { Task { await find(query) } } label: { Text("Look up this account") }
                        .disabled(query.trimmingCharacters(in: .whitespaces).isEmpty || busy)
                }
                ForEach(shown) { u in
                    Button { Task { await find(u.username) } } label: {
                        HStack(spacing: 10) {
                            Avatar(name: u.displayName, color: u.color, size: 34, admin: u.isAdmin, photo: u.avatar)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(u.displayName).fg14(.primary)
                                Text("@" + u.username).font(.caption).fg14(.secondary)
                            }
                            Spacer()
                            if who == u.username { Image(systemName: "checkmark").fg14(Color.abAccent) }
                        }
                    }
                }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Terms records")
        .task14 { await loadPeople() }
        .refreshable14 { await loadPeople() }
    }

    private func loadPeople() async {
        if people.isEmpty, let r = account.saved("adminUsers") { people = decode([ABUser].self, r["users"]) ?? [] }
        if let r = try? await account.callSaving("adminUsers", ["q": ""]) { people = decode([ABUser].self, r["users"]) ?? people }
        peopleLoaded = true
    }

    private func find(_ q: String) async {
        let q = q.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        busy = true
        defer { busy = false }
        do {
            let r = try await account.call("adminTerms", ["query": q])
            who = r["username"] as? String
            rows = decode([Row].self, r["accepted"]) ?? []
            error = nil
        } catch {
            who = nil
            rows = []
            self.error = error.localizedDescription
        }
    }
}
