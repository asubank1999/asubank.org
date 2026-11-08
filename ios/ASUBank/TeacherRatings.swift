import SwiftUI
import WebKit

/// Rate your teachers, natively. The ratings live in the "Rate Your Teacher" Apps Script, which only
/// talks to its own page (google.script.run). A hidden web view loads that page and we call the
/// script's functions inside it: bootstrap(query), getTeacherStats(name), addRating(name, rating, clientId).
enum Ratings {
    static let script = "https://script.google.com/macros/s/AKfycbwdw7QBPLeBz1X-xBP6JeZEF5t7i4Ru1VfWshVqanRKd6xN0J2gQqvvovnTrYI_bpKt/exec"
    static let banned = ["fuck", "shit", "bitch", "asshole", "idiot", "dumb", "retard", "gay", "whore", "slut", "stupid", "kill", "suicide", "sex", "porn", "dick", "pussy", "cock", "boob"]

    static func rude(_ s: String) -> Bool {
        let t = s.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil).filter { $0.isASCII && ($0.isLetter || $0.isNumber) }
        return banned.contains { t.contains($0) }
    }

    static func color(_ v: Double) -> Color {
        if v <= 3 { return Color(UIColor(hex: 0xe5372d)) }
        if v <= 6 { return Color(UIColor(hex: 0xf59f00)) }
        return Color(UIColor(hex: 0x1f8a3e))
    }

    static var clientId: String {
        let k = "ab.rate.clientId"
        if let id = UserDefaults.standard.string(forKey: k) { return id }
        let id = "cid_" + UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        UserDefaults.standard.set(id, forKey: k)
        return id
    }
}

struct Teacher: Identifiable, Hashable {
    let name: String
    let average: Double
    let count: Int
    var id: String { name.lowercased() }

    init?(_ any: Any?) {
        guard let d = any as? [String: Any], let n = (d["display"] as? String) ?? (d["name"] as? String), !n.isEmpty else { return nil }
        name = n.trimmingCharacters(in: .whitespacesAndNewlines)
        average = (d["average"] as? Double) ?? (d["average"] as? NSNumber)?.doubleValue ?? Double("\(d["average"] ?? "")") ?? 0
        count = (d["count"] as? Int) ?? (d["count"] as? NSNumber)?.intValue ?? Int("\(d["count"] ?? "")") ?? 0
    }
}

@MainActor
final class RatingBridge: ScriptBridge {
    /// One connection for the whole app: reopening the screen is instant once it is ready
    static let shared = RatingBridge()

    @Published var teachers: [Teacher] = []
    @Published var loading = false

    init() { super.init(url: Ratings.script, marker: "teacherInput") }

    override func didConnect(first: Bool) async {
        if first || teachers.isEmpty { await refresh() }
    }

    func refresh() async {
        loading = true
        defer { loading = false }
        if let r = try? await callOnce("bootstrap", [""]) as? [String: Any],
           let items = (r["data"] as? [String: Any])?["items"] as? [Any] {
            var seen = Set<String>()
            teachers = items.compactMap { Teacher($0) }.filter { seen.insert($0.id).inserted }
        }
    }

    /// Returns an error message, or nil when saved
    func rate(_ name: String, _ rating: Int) async -> String? {
        do {
            _ = try await call("addRating", [name, rating, Ratings.clientId])
            await refresh()
            return nil
        } catch {
            return ScriptBridge.message(error)
        }
    }
}

enum RatingSort: String, CaseIterable { case top = "Top rated", votes = "Most votes", name = "A–Z" }

struct TeacherRatingsScreen: View {
    @ObservedObject private var bridge = RatingBridge.shared
    @AppStorage("ab.rate.intro.v1") private var introSeen = false
    @State private var query = ""
    @State private var sort: RatingSort = .votes
    @State private var rating: RateTarget?

    var body: some View {
        Group {
            if !introSeen {
                RatingIntro { withAnimation { introSeen = true } }
            } else {
                list
            }
        }
        .bridgeHost(bridge)
        .navigationTitle("Rate your teachers")
        .navigationBarTitleDisplayMode(introSeen ? .large : .inline)
        .sheet(item: $rating) { t in
            RateSheet(target: t, bridge: bridge, known: bridge.teachers)
        }
    }

    private var shown: [Teacher] {
        let q = AB.norm(query)
        let f = bridge.teachers.filter { q.isEmpty || AB.norm($0.name).contains(q) }
        switch sort {
        case .top: return f.sorted { ($0.average, $0.count) > ($1.average, $1.count) }
        case .votes: return f.sorted { ($0.count, $0.average) > ($1.count, $1.average) }
        case .name: return f.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }

    @ViewBuilder private var list: some View {
        List {
            Section14 {
                Button { rating = RateTarget(name: "") } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "star.bubble.fill")
                            .font(.system(size: 18, weight: .semibold))
                            .fg14(.white)
                            .frame(width: 38, height: 38)
                            .bgIn14(Color.abFill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Rate a teacher").font(.headline).fg14(.primary)
                            Text("Anonymous · takes 5 seconds").font(.footnote).fg14(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).fg14(Color.tertiary14)
                    }
                }
                .disabled(bridge.status != .ready)
            }

            switch bridge.status {
            case .connecting:
                Section14 { HStack { Spacer(); ABDots("Loading ratings…"); Spacer() }.padding(.vertical, 24) }
            case .failed:
                Section14 {
                    VStack(spacing: 10) {
                        Image(systemName: "wifi.exclamationmark").font(.largeTitle).fg14(.secondary)
                        Text("Couldn't load the ratings").font(.headline)
                        Text("Check your connection and try again.").font(.footnote).fg14(.secondary)
                        Button("Try again") { bridge.connect(force: true) }.prominentButton14().tint14(.abFill)
                        RouteLink(Route.web("Rate your teachers", Ratings.script)) { Text("Open the web version").font(.footnote) }
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 16)
                }
            case .ready:
                Section14 {
                    Picker("Sort", selection: $sort) {
                        ForEach(RatingSort.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 4, trailing: 0))
                }
                Section14 {
                    if shown.isEmpty {
                        VStack(spacing: 8) {
                            Text(query.isEmpty ? "No ratings yet" : "No teacher named “\(query)” yet").font(.headline)
                            Text("Be the first to rate them.").font(.footnote).fg14(.secondary)
                            Button("Rate \(query.isEmpty ? "a teacher" : query)") { rating = RateTarget(name: query) }
                                .prominentButton14().tint14(.abFill)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                    }
                    ForEach(shown) { t in
                        Button { rating = RateTarget(name: t.name) } label: { TeacherRow(t: t) }
                            .buttonStyle(PlainButtonStyle())
                    }
                } header: {
                    Text("\(bridge.teachers.count) teachers rated")
                } footer: {
                    Text("Tap a teacher to add your rating. Averages are out of 10.")
                }
            }
        }
        .searchable14(text: $query, prompt: "Search teachers")
        .refreshable14 { await bridge.refresh() }
        .animation(.easeInOut(duration: 0.25), value: bridge.status)
    }
}

struct RateTarget: Identifiable { let name: String; var id: String { name } }

private struct TeacherRow: View {
    let t: Teacher
    var body: some View {
        HStack(spacing: 12) {
            Text(String(format: "%.1f", t.average))
                .font(.system(size: 16, weight: .bold, design: .rounded).monoDigit14())
                .fg14(.white)
                .frame(width: 46, height: 38)
                .bgIn14(Ratings.color(t.average), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 5) {
                Text(t.name).font(.body.weight(.medium)).lineLimit(2)
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.secondary.opacity(0.15))
                        Capsule().fill(Ratings.color(t.average)).frame(width: max(4, g.size.width * min(1, t.average / 10)))
                    }
                }
                .frame(height: 5)
                Text(String(format: L("%d votes"), t.count)).font(.caption).fg14(.secondary)
            }
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).fg14(Color.tertiary14)
        }
        .contentShape(Rectangle())
        .padding(.vertical, 3)
    }
}

private struct RatingIntro: View {
    let onContinue: () -> Void
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Image(systemName: "star.bubble.fill")
                    .font(.system(size: 40, weight: .semibold))
                    .fg14(.white)
                    .frame(width: 84, height: 84)
                    .bgIn14(Color.abFill, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .padding(.top, 20)
                Text("Rate your teachers").font(.largeTitle.weight(.bold))
                Text("Share your experience and help other students choose their sections.")
                    .multilineTextAlignment(.center).fg14(.secondary)
                VStack(alignment: .leading, spacing: 16) {
                    IntroPoint(symbol: "eye.slash.fill", title: "100% anonymous", text: "Nobody, not even the admins, can see who rated.")
                    IntroPoint(symbol: "chart.bar.fill", title: "Real averages", text: "Every teacher shows an average out of 10 and how many students voted.")
                    IntroPoint(symbol: "person.badge.plus", title: "Teacher not listed?", text: "Be the first to rate them, and others will join in.")
                    IntroPoint(symbol: "heart.text.square.fill", title: "Be fair", text: "Rate the teaching, not the person. Rude names are blocked.")
                }
                .padding(18)
                .bgIn14(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                Button(action: onContinue) {
                    Text("Continue").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .prominentButton14().tint14(.abFill)
                NavigationLink("Terms of Service") { SitePageView(path: "/legal/uni_tos", title: "Terms of Service") }
                    .font(.footnote)
                Text("By continuing you agree to the Terms of Service.").font(.caption).fg14(.secondary)
            }
            .padding(20)
        }
        .background(Color.abBackground.ignoresSafeArea())
    }
}

private struct IntroPoint: View {
    let symbol: String
    let title: String
    let text: String
    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: symbol).font(.title3).fg14(Color.abAccent).frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(text).font(.subheadline).fg14(.secondary)
            }
        }
    }
}

private struct RateSheet: View {
    let target: RateTarget
    @ObservedObject var bridge: RatingBridge
    let known: [Teacher]
    @Environment(\.presentationMode) private var presentation
    @State private var name = ""
    @State private var score = 0
    @State private var current: Teacher?
    @State private var saving = false
    @State private var message: String?
    @State private var done = false

    private var trimmed: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var suggestions: [Teacher] {
        let q = AB.norm(trimmed)
        guard q.count >= 2 else { return [] }
        return known.filter { AB.norm($0.name).contains(q) && $0.name != trimmed }.prefix(5).map { $0 }
    }

    var body: some View {
        NavigationView {
            Form {
                Section14 {
                    TextField("Teacher's full name, e.g. Dr. Ahmad Malik", text: $name)
                        .autocapitalization(.words)
                        .disableAutocorrection(true)
                        .disabled(done)
                    ForEach(suggestions) { t in
                        Button { name = t.name } label: {
                            HStack {
                                Image(systemName: "arrow.turn.down.right").fg14(.secondary)
                                Text(t.name).fg14(.primary)
                                Spacer()
                                Text(String(format: "%.1f", t.average)).fg14(Ratings.color(t.average))
                            }
                        }
                    }
                } header: {
                    Text("Teacher")
                } footer: {
                    if !suggestions.isEmpty { Text("Pick the existing name so all ratings stay together.") }
                }

                if let c = current, c.count > 0 {
                    Section14("Current rating") {
                        HStack {
                            Text(String(format: "%.1f / 10", c.average)).font(.title3.weight(.bold)).fg14(Ratings.color(c.average))
                            Spacer()
                            Text(String(format: L("%d votes"), c.count)).fg14(.secondary)
                        }
                    }
                }

                Section14 {
                    VStack(spacing: 14) {
                        Text(score == 0 ? "Tap a score" : "\(score) / 10 · \(label(score))")
                            .font(.headline)
                            .fg14(score == 0 ? Color.secondary : Ratings.color(Double(score)))
                        ForEach([1, 6], id: \.self) { start in
                            HStack(spacing: 8) {
                                ForEach(start..<(start + 5), id: \.self) { n in
                                    Button {
                                        withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) { score = n }
                                        UISelectionFeedbackGenerator().selectionChanged()
                                    } label: {
                                        Text("\(n)")
                                            .font(.system(size: 18, weight: .bold, design: .rounded))
                                            .frame(maxWidth: .infinity, minHeight: 46)
                                            .fg14(n <= score ? Color.white : Color.primary)
                                            .bgIn14(n <= score ? Ratings.color(Double(score)) : Color.secondary.opacity(0.12),
                                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                            .scaleEffect(n == score ? 1.08 : 1)
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                    .disabled(done)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 6)
                } header: {
                    Text("Your rating")
                }

                if let message {
                    Section14 {
                        Label(message, systemImage: done ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                            .fg14(done ? Color.green : Color.orange)
                    }
                }

                Section14 {
                    Button {
                        if done { presentation.wrappedValue.dismiss() } else { save() }
                    } label: {
                        HStack {
                            Spacer()
                            if saving { ABDots() } else { Text(done ? "Done" : "Save rating").font(.headline) }
                            Spacer()
                        }
                    }
                    .disabled(saving || (!done && (trimmed.count < 3 || score == 0)))
                } footer: {
                    Text("Your rating is anonymous.")
                }
            }
            .navigationTitle(Text(LocalizedStringKey(target.name.isEmpty ? "Rate a teacher" : "Rate")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { presentation.wrappedValue.dismiss() } }
            }
        }
        .onAppear {
            if name.isEmpty { name = target.name }
            current = known.first { $0.name == target.name }
        }
    }

    private func label(_ n: Int) -> String {
        switch n {
        case 1...2: return "Very poor"
        case 3...4: return "Poor"
        case 5...6: return "OK"
        case 7...8: return "Good"
        default: return "Excellent"
        }
    }

    private func save() {
        guard !Ratings.rude(trimmed) else {
            message = "That name isn't allowed."; Haptics.error(); return
        }
        saving = true
        message = nil
        Task {
            let err = await bridge.rate(trimmed, score)
            saving = false
            if let err {
                message = err; Haptics.error()
            } else {
                done = true
                message = "Thanks! Your rating was saved."
                current = bridge.teachers.first { $0.name.lowercased() == trimmed.lowercased() }
                Haptics.success()
            }
        }
    }
}
