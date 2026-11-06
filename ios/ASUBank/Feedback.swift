import SwiftUI

// Native "Rate our service" (requests/service_rating.html) and the Discord community vote (apps/voting.html).
// Both post to the same Apps Scripts as the website, so answers from the app and the site land in one sheet.

enum FeedbackAPI {
    static let rating = URL(string: "https://script.google.com/macros/s/AKfycbyA-A0WREgeLnCZYLpe3V1jtDDJ1LC2Pc-iYxG0_5soAwPcbPjLITla3_k8Q4t9Gk2E/exec")!
    static let voting = URL(string: "https://script.google.com/macros/s/AKfycbzmBIXkb4uqwWsZ_gYUwSRMTwZVshGucECvWSeCpO76zVAFq5SsWVXCnnTBUdOWcdYK8Q/exec")!

    static func post(_ url: URL, _ body: [String: Any]) async throws -> [String: Any] {
        var req = URLRequest(url: url, timeoutInterval: 40)
        req.httpMethod = "POST"
        req.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, _) = try await URLSession.shared.data(for: req)
        let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        if obj["ok"] as? Bool == false { throw AccountsAPI.Failure(message: obj["error"] as? String ?? L("Something went wrong.")) }
        return obj
    }

    static var stamp: String { ISO8601DateFormatter.ab.string(from: Date()) }
    static var agent: String { "UniHub iOS \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")" }
}

// MARK: - rate our service

struct ServiceRatingScreen: View {
    static let questions: [(String, String)] = [
        ("Easy to navigate", "سهل التنقل"), ("Found materials quickly", "مواد سريعة الوصول"), ("Loads fast", "تحميل سريع"),
        ("Mobile-friendly design", "مناسب للجوال"), ("Would recommend", "سأنصح به"), ("Accurate search results", "نتائج بحث دقيقة"),
        ("Smooth download", "تحميل سلس"), ("Well-organized content", "محتوى منظم"), ("Feels safe", "أشعر بالأمان"),
        ("Will use again", "سأستخدمه مجددًا"),
    ]

    @State private var rating = 7.0
    @State private var answers: [Int: Bool] = [:]
    @State private var suggestion = ""
    @State private var busy = false
    @State private var error: String?
    @State private var done = false

    private var face: String { ["😞", "😞", "😕", "😕", "😐", "😐", "🙂", "🙂", "😄", "🤩"][Int(rating) - 1] }

    var body: some View {
        Form {
            if done {
                Section {
                    VStack(spacing: 10) {
                        Text("💚").font(.system(size: 60))
                        Text("Thank you!").font(.title2.weight(.bold))
                        Text("Your rating helps us make UniHub better for everyone.").multilineTextAlignment(.center).foregroundStyle(.secondary)
                        Button("Rate again") { withAnimation { done = false; answers = [:]; suggestion = ""; rating = 7 } }.padding(.top, 6)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 24)
                }
            } else {
                Section {
                    VStack(spacing: 6) {
                        Text(face).font(.system(size: 54))
                        Text("\(Int(rating)) / 10").font(.title.weight(.bold)).monospacedDigit()
                        Slider(value: $rating, in: 1...10, step: 1).tint(.abAccent)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 6)
                } header: { Text("How would you rate UniHub?") }
                Section {
                    ForEach(Self.questions.indices, id: \.self) { i in
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(LocalizedStringKey(Self.questions[i].0)).font(.subheadline.weight(.semibold))
                                Text(Self.questions[i].1).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            choice(i, true, "hand.thumbsup.fill", .green)
                            choice(i, false, "hand.thumbsdown.fill", .red)
                        }
                    }
                } header: { Text("Quick questions") } footer: { Text(String(format: L("%d of %d answered"), answers.count, Self.questions.count)) }
                Section("Anything we should add or fix?") {
                    TextEditor(text: $suggestion).frame(minHeight: 90)
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
                Section {
                    Button { Task { await submit() } } label: {
                        HStack { Spacer(); if busy { ABDots() }; Text("Send rating").font(.headline); Spacer() }.padding(.vertical, 4)
                    }
                    .buttonStyle(.borderedProminent).tint(.abFill)
                    .disabled(busy || answers.count < Self.questions.count)
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                }
            }
        }
        .navigationTitle("Rate our service")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func choice(_ i: Int, _ v: Bool, _ icon: String, _ c: Color) -> some View {
        Button { withAnimation(.spring(response: 0.25)) { answers[i] = v }; Haptics.tap() } label: {
            Image(systemName: icon).font(.body)
                .frame(width: 38, height: 34)
                .foregroundStyle(answers[i] == v ? .white : c)
                .background(answers[i] == v ? c : c.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(v ? "Yes" : "No"))
    }

    private func submit() async {
        busy = true; error = nil
        var a: [String: String] = [:]
        for i in Self.questions.indices { a["q\(i + 1)"] = answers[i] == true ? "True" : "False" }
        do {
            _ = try await FeedbackAPI.post(FeedbackAPI.rating, ["timestamp": FeedbackAPI.stamp, "rating": Int(rating), "answers": a,
                                                                 "suggestion": suggestion, "page": "ios-app", "userAgent": FeedbackAPI.agent])
            Haptics.success()
            withAnimation { done = true }
        } catch { self.error = error.localizedDescription; Haptics.error() }
        busy = false
    }
}

// MARK: - Discord community vote

struct VotingScreen: View {
    static let games: [(String, String)] = [
        ("Valorant", "🎯"), ("League of Legends", "⚔️"), ("CS2", "🔫"), ("Fortnite", "🏗️"), ("Minecraft", "⛏️"), ("GTA V", "🚗"),
        ("Rocket League", "🚀"), ("Apex Legends", "🪂"), ("Overwatch 2", "🛡️"), ("PUBG", "🎒"), ("Call of Duty", "💥"), ("Rainbow Six", "🌈"),
        ("Dota 2", "🧙"), ("Roblox", "🧱"), ("Among Us", "🔪"), ("Fall Guys", "🍬"), ("FIFA / EA FC", "⚽"), ("NBA 2K", "🏀"),
        ("Clash Royale", "👑"), ("Clash of Clans", "🏰"), ("Free Fire", "🔥"), ("Mobile Legends", "📱"), ("Genshin Impact", "🌸"),
        ("Elden Ring", "🗡️"), ("Stardew Valley", "🌾"), ("Terraria", "🌳"), ("Chess.com", "♟️"), ("Uno / Cards", "🃏"), ("Skribbl.io", "✏️"),
        ("Jackbox", "📺"),
    ]

    @AppStorage("ab.voted") private var voted = false
    @State private var vote: String?
    @State private var interested: Bool?
    @State private var picked: Set<String> = []
    @State private var other = ""
    @State private var suggestion = ""
    @State private var stats: [String: Any] = [:]
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Form {
            resultsSection
            if voted {
                Section {
                    Label("Thanks, your vote is in!", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
                    Button("Vote again") { withAnimation { voted = false } }
                }
            } else {
                Section("Should we open the Discord gaming community?") {
                    HStack(spacing: 10) {
                        big("YES", L("Open it"), "✅", .green)
                        big("NO", L("Don't open"), "❌", .red)
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                }
                Section("Would you personally be interested in joining?") {
                    Picker("", selection: $interested) {
                        Text("Yes").tag(Optional(true))
                        Text("No").tag(Optional(false))
                    }
                    .pickerStyle(.segmented)
                }
                Section {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 8)], spacing: 8) {
                        ForEach(Self.games, id: \.0) { g in
                            let on = picked.contains(g.0)
                            Button {
                                withAnimation(.spring(response: 0.25)) { if on { picked.remove(g.0) } else { picked.insert(g.0) } }
                            } label: {
                                HStack(spacing: 6) { Text(g.1); Text(g.0).font(.caption.weight(.semibold)).lineLimit(1) ; Spacer(minLength: 0) }
                                    .padding(.horizontal, 10).padding(.vertical, 9)
                                    .foregroundStyle(on ? .white : .primary)
                                    .background(on ? Color.abAccent : Color(UIColor.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 4)
                    TextField("Another game?", text: $other)
                } header: { Text("Which games would you play?") } footer: { Text(String(format: L("%d picked"), picked.count)) }
                Section("Ideas for the community (optional)") { TextEditor(text: $suggestion).frame(minHeight: 80) }
                if let error { Section { Text(error).foregroundStyle(.red) } }
                Section {
                    Button { Task { await submit() } } label: {
                        HStack { Spacer(); if busy { ABDots() }; Text("Send my vote").font(.headline); Spacer() }.padding(.vertical, 4)
                    }
                    .buttonStyle(.borderedProminent).tint(.abFill)
                    .disabled(busy || vote == nil)
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                }
            }
        }
        .navigationTitle("Discord community vote")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    @ViewBuilder private var resultsSection: some View {
        let total = (stats["totalVotes"] as? NSNumber)?.intValue ?? 0
        let yes = (stats["yesPercent"] as? NSNumber)?.doubleValue ?? 0
        let top = (stats["topGames"] as? [[String: Any]]) ?? []
        Section {
            if total == 0 && stats.isEmpty {
                HStack { Spacer(); ABDots(); Spacer() }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(String(format: L("%d votes"), total)).font(.headline)
                        Spacer()
                        Text(String(format: L("%d interested"), (stats["interestedCount"] as? NSNumber)?.intValue ?? 0)).font(.caption).foregroundStyle(.secondary)
                    }
                    GeometryReader { g in
                        HStack(spacing: 0) {
                            Rectangle().fill(Color.green).frame(width: g.size.width * yes / 100)
                            Rectangle().fill(Color.red.opacity(0.75))
                        }
                        .clipShape(Capsule())
                    }
                    .frame(height: 12)
                    HStack {
                        Text("✅ \(Int(yes.rounded()))%").font(.caption.weight(.semibold))
                        Spacer()
                        Text("❌ \(Int((100 - yes).rounded()))%").font(.caption.weight(.semibold))
                    }
                    if !top.isEmpty {
                        Text("Most wanted games").font(.caption.weight(.bold)).foregroundStyle(.secondary).padding(.top, 4)
                        ForEach(Array(top.prefix(5).enumerated()), id: \.offset) { _, g in
                            HStack {
                                Text(g["name"] as? String ?? "")
                                Spacer()
                                Text("\((g["count"] as? NSNumber)?.intValue ?? 0)").foregroundStyle(.secondary).monospacedDigit()
                            }
                            .font(.subheadline)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        } header: { Text("Live results") }
    }

    private func big(_ v: String, _ label: String, _ emoji: String, _ c: Color) -> some View {
        Button { withAnimation(.spring(response: 0.25)) { vote = v }; Haptics.tap() } label: {
            VStack(spacing: 4) { Text(emoji).font(.title); Text(label).font(.subheadline.weight(.bold)) }
                .frame(maxWidth: .infinity).padding(.vertical, 14)
                .foregroundStyle(vote == v ? .white : c)
                .background(vote == v ? c : c.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        var req = URLRequest(url: FeedbackAPI.voting, timeoutInterval: 30)
        req.httpMethod = "GET"
        if let res = try? await URLSession.shared.data(for: req),
           let o = (try? JSONSerialization.jsonObject(with: res.0)) as? [String: Any], let s = o["stats"] as? [String: Any] { stats = s }
    }

    private func submit() async {
        guard let vote else { return }
        busy = true; error = nil
        var body: [String: Any] = ["timestamp": FeedbackAPI.stamp, "vote": vote, "games": Array(picked), "otherGame": other,
                                   "suggestion": suggestion, "page": "ios-app", "userAgent": FeedbackAPI.agent]
        body["interested"] = interested.map { $0 as Any } ?? NSNull()
        do {
            let r = try await FeedbackAPI.post(FeedbackAPI.voting, body)
            if let s = r["stats"] as? [String: Any] { stats = s }
            Haptics.success()
            withAnimation { voted = true }
        } catch { self.error = error.localizedDescription; Haptics.error() }
        busy = false
    }
}
