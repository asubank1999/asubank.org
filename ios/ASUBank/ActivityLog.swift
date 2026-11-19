import SwiftUI

// 1.22.0: the admins' activity log. Who signs up, signs in and opens UniHub, from which phone or the website,
// and what they did (posts, stories, profile changes, reports). Never the text of messages.

/// Tells the server once per app start (it keeps at most one per 30 minutes) that UniHub was opened
@MainActor enum Hello {
    static func send() {
        var info = utsname()
        uname(&info)
        let model = withUnsafePointer(to: &info.machine) { $0.withMemoryRebound(to: CChar.self, capacity: 1) { String(cString: $0) } }
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let fields: [String: Any] = ["source": "ios", "appVersion": version, "device": model + " · iOS " + UIDevice.current.systemVersion,
                                     "lang": Locale.preferredLanguages.first ?? ""]
        Task { _ = try? await AccountStore.shared.call("hello", fields) }
    }
}

struct ActivityRecord: Decodable, Identifiable {
    var time: String
    var userId: String?
    var username: String?
    var name: String?
    var kind: String
    var source: String?
    var appVersion: String?
    var device: String?
    var detail: String?
    var id: String { time + (userId ?? "") + kind }

    var date: Date? { Disappear.date(time) }
    var platform: String {
        let s = (source ?? "").lowercased()
        return s == "ios" || s == "android" ? s : s.isEmpty ? "" : "web"
    }
    var group: String {
        switch kind {
        case "signed up": return "signup"
        case "signed in", "signed out": return "signin"
        case "sign-in failed": return "failed"
        case "opened UniHub": return "open"
        case "posted", "added a story", "commented", "shared a video": return "content"
        case "edited profile", "changed photo", "changed cover", "changed password", "deleted account": return "profile"
        case "sent a report", "sent a suggestion": return "report"
        default: return "other"
        }
    }
}

struct ActivityLogScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var all: [ActivityRecord] = []
    @State private var loaded = false
    @State private var failed = false
    @State private var platform = "all"
    @State private var kind = "all"
    @State private var query = ""
    @State private var limit = 150

    struct Kind: Identifiable { let id: String; let label: String; let symbol: String }
    private static let kinds: [Kind] = [
        Kind(id: "all", label: "Everything", symbol: "list.bullet"), Kind(id: "open", label: "App opens", symbol: "app.badge"),
        Kind(id: "signup", label: "New accounts", symbol: "person.badge.plus"), Kind(id: "signin", label: "Sign-ins", symbol: "person.crop.circle.badge.checkmark"),
        Kind(id: "failed", label: "Failed sign-ins", symbol: "exclamationmark.shield"), Kind(id: "content", label: "Posts and stories", symbol: "square.and.pencil"),
        Kind(id: "profile", label: "Profile changes", symbol: "person.text.rectangle"), Kind(id: "report", label: "Reports", symbol: "flag"),
    ]

    private var shown: [ActivityRecord] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return all.filter { r in
            (platform == "all" || r.platform == platform) && (kind == "all" || r.group == kind) &&
            (q.isEmpty || [r.username ?? "", r.name ?? "", r.device ?? "", r.detail ?? ""].contains { $0.lowercased().contains(q) })
        }
    }

    struct Day: Identifiable { let id: String; var rows: [ActivityRecord] }

    /// shown records, a section per day
    private var days: [Day] {
        var out: [Day] = []
        let f = DateFormatter()
        f.dateStyle = .full
        f.timeStyle = .none
        for r in shown.prefix(limit) {
            let d = r.date ?? Date.distantPast
            let title = Calendar.current.isDateInToday(d) ? L("Today") : Calendar.current.isDateInYesterday(d) ? L("Yesterday") : f.string(from: d)
            if out.last?.id == title { out[out.count - 1].rows.append(r) } else { out.append(Day(id: title, rows: [r])) }
        }
        return out
    }

    private func people(since: Date, platform p: String? = nil) -> Int {
        Set(all.filter { ($0.date ?? .distantPast) >= since && !($0.userId ?? "").isEmpty && (p == nil || $0.platform == p) }.compactMap(\.userId)).count
    }
    private func count(_ group: String, since: Date) -> Int { all.filter { $0.group == group && ($0.date ?? .distantPast) >= since }.count }

    var body: some View {
        List {
            if !all.isEmpty { summary }
            Section {
                Picker("Platform", selection: $platform) {
                    Text("All").tag("all")
                    Text(verbatim: "iPhone").tag("ios")
                    Text(verbatim: "Android").tag("android")
                    Text(verbatim: "Web").tag("web")
                }
                .pickerStyle(.segmented)
                Picker("Show", selection: $kind) {
                    ForEach(Self.kinds) { k in Label(LocalizedStringKey(k.label), systemImage: k.symbol).tag(k.id) }
                }
                TextField("Search a name, username or phone model", text: $query)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
            } footer: {
                Text(verbatim: String(format: L("%d records"), shown.count))
            }
            if !loaded && all.isEmpty {
                Section { ForEach(0..<6, id: \.self) { _ in SkeletonRow() } }
            } else if failed && all.isEmpty {
                Section { Text("The activity log needs the newest server. Try again later.").foregroundStyle(.secondary) }
            } else if shown.isEmpty {
                Section { Text("Nothing matches.").foregroundStyle(.secondary) }
            }
            ForEach(days) { day in
                Section(day.id) {
                    ForEach(day.rows) { r in row(r) }
                }
            }
            if shown.count > limit {
                Button { limit += 300 } label: { Text("Show more") }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Activity log")
        .task { await load() }
        .refreshable { await load() }
    }

    private var summary: some View {
        let today = Calendar.current.startOfDay(for: Date())
        let week = Calendar.current.date(byAdding: .day, value: -6, to: today) ?? today
        return Section {
            HStack(spacing: 8) {
                tile(people(since: today), L("people today"), "person.2.fill")
                tile(count("open", since: today), L("app opens today"), "app.badge.fill")
                tile(count("signup", since: today), L("new today"), "person.badge.plus")
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
            HStack(spacing: 8) {
                tile(people(since: week), L("people this week"), "calendar")
                tile(people(since: week, platform: "ios"), "iPhone", "iphone")
                tile(people(since: week, platform: "android"), "Android", "a.circle")
                tile(people(since: week, platform: "web"), "Web", "globe")
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
        } header: { Text("At a glance") } footer: { Text("People are counted once, however often they open UniHub. Guests are not counted as people.") }
    }

    private func tile(_ n: Int, _ label: String, _ symbol: String) -> some View {
        VStack(spacing: 3) {
            Image(systemName: symbol).font(.caption).foregroundStyle(Color.abAccent)
            Text("\(n)").font(.title3.weight(.bold).monospacedDigit())
            Text(verbatim: label).font(.caption2).foregroundStyle(.secondary).lineLimit(2).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 64)
        .padding(.vertical, 4)
        .background(Color.abAccentSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func row(_ r: ActivityRecord) -> some View {
        let symbol = Self.kinds.first { $0.id == r.group }?.symbol ?? "circle"
        let who: String = {
            let u = r.username ?? "", n = r.name ?? ""
            if u.isEmpty || u == "guest" { return L("Guest (not signed in)") }
            return n.isEmpty || n == u ? "@" + u : n + "  @" + u
        }()
        let time = r.date.map { $0.formatted(date: .omitted, time: .shortened) } ?? ""
        let app = r.platform == "ios" ? "iPhone" : r.platform == "android" ? "Android" : r.platform == "web" ? "Web" : ""
        let line2 = [time, [app, r.appVersion ?? ""].filter { !$0.isEmpty }.joined(separator: " "), r.device ?? "", r.detail ?? ""]
            .filter { !$0.isEmpty }.joined(separator: " · ")
        return Button {
            if let u = r.username, !u.isEmpty, u != "guest" { query = u }
        } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: symbol).foregroundStyle(r.group == "failed" ? Color.red : Color.abAccent).frame(width: 24).padding(.top, 2)
                VStack(alignment: .leading, spacing: 2) {
                    (Text(verbatim: who).fontWeight(.semibold) + Text(verbatim: "  " + L(r.kind)))
                        .foregroundStyle(.primary).lineLimit(2)
                    Text(verbatim: line2).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
            }
        }
    }

    private func load() async {
        if all.isEmpty, let r = account.saved("adminActivity") { all = decode([ActivityRecord].self, r["records"]) ?? [] }
        do {
            let r = try await account.callSaving("adminActivity")
            all = decode([ActivityRecord].self, r["records"]) ?? all
            failed = false
        } catch { failed = true }
        loaded = true
    }
}
