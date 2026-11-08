import SwiftUI

// Offline study tools: absence tracker, final mark calculator, flashcards, and study stats from the focus timer.
// Everything is saved on the phone only. The Android app has the same tools in StudyTools.kt.

@MainActor
func lectureSubjects() -> [String] {
    var seen = Set<String>()
    return LectureStore.shared.lectures.map { $0.subject.trimmingCharacters(in: .whitespaces) }
        .filter { !$0.isEmpty && seen.insert($0.lowercased()).inserted }
}

private func saveJSON<T: Encodable>(_ v: T, _ key: String) {
    if let d = try? JSONEncoder().encode(v) { UserDefaults.standard.set(d, forKey: key) }
}

private func loadJSON<T: Decodable>(_ t: T.Type, _ key: String) -> T? {
    guard let d = UserDefaults.standard.data(forKey: key) else { return nil }
    return try? JSONDecoder().decode(t, from: d)
}

// MARK: - Absence tracker

struct AbsenceSubject: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var name: String
    var limit: Int
    var missed = 0
}

@MainActor
final class AbsenceStore: ObservableObject {
    static let shared = AbsenceStore()
    @Published private(set) var list: [AbsenceSubject] = []
    private let key = "ab.absences"

    init() { list = loadJSON([AbsenceSubject].self, key) ?? [] }

    func save(_ s: AbsenceSubject) {
        if let i = list.firstIndex(where: { $0.id == s.id }) { list[i] = s } else { list.append(s) }
        saveJSON(list, key)
    }

    func remove(_ id: String) { list.removeAll { $0.id == id }; saveJSON(list, key) }

    func bump(_ id: String, _ by: Int) {
        guard let i = list.firstIndex(where: { $0.id == id }) else { return }
        list[i].missed = max(0, list[i].missed + by)
        saveJSON(list, key)
    }

    /// 15% of a 16-week term, from how many days a week the subject meets in My lectures
    func suggestedLimit(_ name: String) -> Int {
        let days = Set(LectureStore.shared.lectures.filter { $0.subject.trimmingCharacters(in: .whitespaces).lowercased() == name.lowercased() }.flatMap(\.days)).count
        return max(1, 16 * max(days, 2) * 15 / 100)
    }
}

struct AbsenceScreen: View {
    @ObservedObject private var store = AbsenceStore.shared
    @State private var editing: AbsenceSubject?

    @MainActor private var missing: [String] { lectureSubjects().filter { n in !store.list.contains { $0.name.lowercased() == n.lowercased() } } }

    var body: some View {
        List {
            if store.list.isEmpty {
                Section {
                    EmptyState(symbol: "calendar.badge.minus", title: L("Track your absences"),
                               text: L("Add a subject, then tap + each time you miss a lecture. We warn you before you reach the limit."))
                        .listRowBackground(Color.clear)
                }
            } else {
                Section {
                    ForEach(store.list) { row($0) }
                } footer: {
                    Text(L("Most universities in Jordan allow about 15% of a subject's lectures. Check your own university's rule and set the limit to match."))
                }
            }
            if !missing.isEmpty {
                Section("From My lectures") {
                    ForEach(missing, id: \.self) { n in
                        Button {
                            store.save(AbsenceSubject(name: n, limit: store.suggestedLimit(n)))
                        } label: {
                            HStack {
                                Image(systemName: "plus.circle.fill").foregroundStyle(Color.abAccent)
                                VStack(alignment: .leading) {
                                    Text(n).foregroundStyle(.primary)
                                    Text(String(format: L("Limit %d absences"), store.suggestedLimit(n))).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Absence tracker")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editing = AbsenceSubject(name: "", limit: 4) } label: { Image(systemName: "plus") }.accessibilityLabel(Text("Add"))
            }
        }
        .sheet(item: $editing) { AbsenceEditor(item: $0) }
    }

    private func row(_ s: AbsenceSubject) -> some View {
        let left = s.limit - s.missed
        let color: Color = left <= 0 ? .red : left == 1 ? .orange : .green
        let status = left < 0 ? String(format: L("%d over the limit"), -left)
            : left == 0 ? L("At the limit: don't miss another lecture")
            : left == 1 ? L("1 absence left") : String(format: L("%d absences left"), left)
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Button { editing = s } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.name).font(.body.weight(.semibold)).foregroundStyle(.primary).lineLimit(1)
                        Text(status).font(.caption).foregroundStyle(color)
                    }
                }
                .buttonStyle(.plain)
                Spacer()
                Button { store.bump(s.id, -1); Haptics.tap() } label: { Image(systemName: "minus.circle").font(.title2) }
                    .buttonStyle(.borderless).disabled(s.missed == 0).accessibilityLabel(Text("Remove one"))
                Text("\(s.missed)/\(s.limit)").font(.body.weight(.bold)).monospacedDigit().frame(minWidth: 48)
                    .environment(\.layoutDirection, .leftToRight)
                Button { store.bump(s.id, 1); Haptics.tap() } label: { Image(systemName: "plus.circle.fill").font(.title2) }
                    .buttonStyle(.borderless).tint(Color.abAccent).accessibilityLabel(Text("Add one"))
            }
            ProgressView(value: min(1, Double(s.missed) / Double(max(1, s.limit)))).tint(color)
        }
        .padding(.vertical, 4)
        .swipeActions { Button(role: .destructive) { store.remove(s.id) } label: { Label("Delete", systemImage: "trash") } }
    }
}

private struct AbsenceEditor: View {
    @State var item: AbsenceSubject
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = AbsenceStore.shared

    var body: some View {
        ABStack {
            Form {
                Section {
                    TextField("Subject", text: $item.name)
                    Stepper(String(format: L("Allowed absences: %d"), item.limit), value: $item.limit, in: 1...60)
                    Stepper(String(format: L("Missed so far: %d"), item.missed), value: $item.missed, in: 0...99)
                }
                if store.list.contains(where: { $0.id == item.id }) {
                    Section { Button("Delete", role: .destructive) { store.remove(item.id); dismiss() } }
                }
            }
            .navigationTitle(item.name.isEmpty ? L("Add a subject") : item.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        item.name = item.name.trimmingCharacters(in: .whitespaces)
                        store.save(item); Haptics.success(); dismiss()
                    }
                    .disabled(item.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

// MARK: - Final mark calculator

struct FinalMarkScreen: View {
    @AppStorage("ab.final.have") private var have = ""
    @AppStorage("ab.final.outOf") private var outOf = "50"
    @AppStorage("ab.final.weight") private var finalW = "50"
    @AppStorage("ab.final.target") private var target = "50"

    private func num(_ s: String) -> Double? { Double(s.replacingOccurrences(of: ",", with: ".")) }

    var body: some View {
        let h = num(have), o = num(outOf), f = num(finalW)
        let ok = h != nil && o != nil && f != nil && o! > 0 && f! > 0 && h! <= o!
        return List {
            Section {
                field("Marks so far", $have)
                field("Out of", $outOf)
                field("The final is out of", $finalW)
            } header: { Text("Your marks so far") } footer: {
                Text("Add up everything before the final: midterm, quizzes, assignments, labs. Example: 22 out of 50.")
            }
            if let h, let o, let f, ok {
                let total = o + f
                let goals = Array(Set([50.0, 60, 70, 80, 90].map { $0 * total / 100 } + [num(target)].compactMap { $0 })).sorted()
                Section {
                    ForEach(goals, id: \.self) { g in
                        let n = g - h
                        HStack {
                            Text(String(format: L("To get %@"), fmt(g)) + (g == num(target) ? "  ⭐" : ""))
                            Spacer()
                            if n <= 0 {
                                Text("Already reached").bold().foregroundStyle(.green)
                            } else if n > f {
                                Text("Not possible").bold().foregroundStyle(.red)
                            } else {
                                Text("\(fmt((n * 2).rounded(.up) / 2)) / \(fmt(f))").bold()
                                    .foregroundStyle(n / f > 0.8 ? Color.orange : Color.primary)
                                    .environment(\.layoutDirection, .leftToRight)
                            }
                        }
                    }
                } header: { Text("What you need on the final") } footer: {
                    Text(String(format: L("The total is out of %@."), fmt(total)))
                }
            } else if let h, let o, h > o {
                Section { Text("Your marks so far can't be more than the total.").foregroundStyle(.red) }
            }
            Section {
                field("Goal total", $target)
            } header: { Text("Your own goal") } footer: {
                Text("Usually 50 is the pass mark out of 100. Check your grading scale.")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Final mark calculator")
    }

    private func field(_ title: LocalizedStringKey, _ v: Binding<String>) -> some View {
        HStack {
            Text(title)
            Spacer()
            TextField("0", text: v).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(maxWidth: 110)
        }
    }

    private func fmt(_ d: Double) -> String { d.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(d)) : String(format: "%.1f", d) }
}

// MARK: - Flashcards

struct Flashcard: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var front: String
    var back: String
}

struct FlashDeck: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var name: String
    var cards: [Flashcard] = []
}

@MainActor
final class FlashcardStore: ObservableObject {
    static let shared = FlashcardStore()
    @Published private(set) var decks: [FlashDeck] = []
    private let key = "ab.flashcards"

    init() { decks = loadJSON([FlashDeck].self, key) ?? [] }

    func save(_ d: FlashDeck) {
        if let i = decks.firstIndex(where: { $0.id == d.id }) { decks[i] = d } else { decks.append(d) }
        saveJSON(decks, key)
    }

    func remove(_ id: String) { decks.removeAll { $0.id == id }; saveJSON(decks, key) }
    func deck(_ id: String) -> FlashDeck? { decks.first { $0.id == id } }
}

struct FlashcardsScreen: View {
    @ObservedObject private var store = FlashcardStore.shared
    @State private var naming: FlashDeck?

    var body: some View {
        List {
            if store.decks.isEmpty {
                Section {
                    EmptyState(symbol: "rectangle.on.rectangle.angled", title: L("Make your own flashcards"),
                               text: L("Make a deck for a subject, write a question on the front and the answer on the back, then test yourself."))
                        .listRowBackground(Color.clear)
                }
            } else {
                Section {
                    ForEach(store.decks) { d in
                        NavigationLink { DeckScreen(deckID: d.id) } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(d.name)
                                Text(String(format: L("%d cards"), d.cards.count)).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .swipeActions { Button(role: .destructive) { store.remove(d.id) } label: { Label("Delete", systemImage: "trash") } }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Flashcards")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { naming = FlashDeck(name: "") } label: { Image(systemName: "plus") }.accessibilityLabel(Text("New deck"))
            }
        }
        .sheet(item: $naming) { DeckNameSheet(deck: $0) }
    }
}

private struct DeckNameSheet: View {
    @State var deck: FlashDeck
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ABStack {
            Form { TextField("Deck name, e.g. Biochemistry", text: $deck.name) }
                .navigationTitle(FlashcardStore.shared.deck(deck.id) == nil ? L("New deck") : L("Rename"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") {
                            deck.name = deck.name.trimmingCharacters(in: .whitespaces)
                            FlashcardStore.shared.save(deck); dismiss()
                        }
                        .disabled(deck.name.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
        }
    }
}

private struct DeckScreen: View {
    let deckID: String
    @ObservedObject private var store = FlashcardStore.shared
    @State private var editing: Flashcard?
    @State private var renaming: FlashDeck?
    @State private var studying = false

    var body: some View {
        let d = store.deck(deckID) ?? FlashDeck(name: "")
        List {
            Section {
                HStack(spacing: 10) {
                    Button { studying = true } label: { Label("Study", systemImage: "play.fill").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).tint(.abFill).disabled(d.cards.isEmpty)
                    Button { editing = Flashcard(front: "", back: "") } label: { Label("Add a card", systemImage: "plus").frame(maxWidth: .infinity) }
                        .buttonStyle(.bordered)
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }
            if d.cards.isEmpty {
                Section { EmptyState(symbol: "rectangle.on.rectangle.angled", title: L("No cards yet"), text: L("Add your first card.")).listRowBackground(Color.clear) }
            } else {
                Section {
                    ForEach(d.cards) { c in
                        Button { editing = c } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(c.front).foregroundStyle(.primary)
                                Text(c.back).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            }
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                var n = d; n.cards.removeAll { $0.id == c.id }; store.save(n)
                            } label: { Label("Delete", systemImage: "trash") }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(d.name)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { renaming = d } label: { Image(systemName: "pencil") }.accessibilityLabel(Text("Rename"))
            }
        }
        .sheet(item: $editing) { CardEditor(deckID: deckID, card: $0) }
        .sheet(item: $renaming) { DeckNameSheet(deck: $0) }
        .fullScreenCover(isPresented: $studying) { StudyCardsView(deck: d) }
    }
}

private struct CardEditor: View {
    let deckID: String
    @State var card: Flashcard
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = FlashcardStore.shared
    @State private var added = 0

    private var exists: Bool { store.deck(deckID)?.cards.contains { $0.id == card.id } ?? false }

    var body: some View {
        ABStack {
            Form {
                Section("Front (question)") { TextEditor(text: $card.front).frame(minHeight: 70) }
                Section("Back (answer)") { TextEditor(text: $card.back).frame(minHeight: 70) }
                if added > 0 { Section { Text(String(format: L("%d cards added"), added)).foregroundStyle(.secondary) } }
            }
            .navigationTitle(exists ? L("Edit card") : L("Add a card"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(LocalizedStringKey(exists || added > 0 ? "Done" : "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(LocalizedStringKey(exists ? "Save" : "Add")) { save() }
                        .disabled(card.front.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || card.back.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func save() {
        guard var d = store.deck(deckID) else { dismiss(); return }
        let c = Flashcard(id: card.id, front: card.front.trimmingCharacters(in: .whitespacesAndNewlines), back: card.back.trimmingCharacters(in: .whitespacesAndNewlines))
        if let i = d.cards.firstIndex(where: { $0.id == c.id }) {
            d.cards[i] = c; store.save(d); dismiss()
        } else {
            d.cards.append(c); store.save(d); Haptics.success()
            added += 1
            card = Flashcard(front: "", back: "") // stay open for the next card
        }
    }
}

/// One round through the cards; the ones marked "Again" come back until all are known
private struct StudyCardsView: View {
    let deck: FlashDeck
    @Environment(\.dismiss) private var dismiss
    @State private var queue: [Flashcard] = []
    @State private var again: [Flashcard] = []
    @State private var flipped = false
    @State private var known = 0
    @State private var round = 1
    @State private var started = false

    var body: some View {
        ABStack {
            VStack(spacing: 16) {
                if queue.isEmpty && started {
                    Spacer()
                    Text("🎉").font(.system(size: 56))
                    Text("You know every card!").font(.title2.bold())
                    Text(String(format: L("%d cards in %d rounds"), deck.cards.count, round)).foregroundStyle(.secondary)
                    Button("Study again") { restart() }.buttonStyle(.borderedProminent).tint(.abFill)
                    Button("Done") { dismiss() }.buttonStyle(.bordered)
                    Spacer()
                } else if let c = queue.first {
                    Text(String(format: L("Round %d · %d left · %d known"), round, queue.count + again.count, known))
                        .font(.caption).foregroundStyle(.secondary)
                    ZStack {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(flipped ? Color.abAccentSoft : Color.abSurface)
                        VStack(spacing: 10) {
                            Text(LocalizedStringKey(flipped ? "Answer" : "Question")).font(.caption.weight(.semibold)).foregroundStyle(Color.abAccent)
                            Text(flipped ? c.back : c.front).font(.title3.weight(.medium)).multilineTextAlignment(.center)
                        }
                        .padding(24)
                        .rotation3DEffect(.degrees(flipped ? 180 : 0), axis: (x: 0, y: 1, z: 0))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .rotation3DEffect(.degrees(flipped ? 180 : 0), axis: (x: 0, y: 1, z: 0))
                    .onTapGesture { withAnimation(.easeInOut(duration: 0.35)) { flipped.toggle() } }
                    if flipped {
                        HStack(spacing: 12) {
                            Button { next(false) } label: { Text("Again").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                            Button { next(true) } label: { Text("Knew it").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(.green)
                        }
                        .controlSize(.large)
                    } else {
                        Text("Tap the card to see the answer").foregroundStyle(.secondary)
                    }
                }
            }
            .padding(20)
            .navigationTitle(deck.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
        }
        .onAppear { if !started { restart() } }
    }

    private func restart() {
        queue = deck.cards.shuffled(); again = []; known = 0; round = 1; flipped = false; started = true
    }

    private func next(_ knew: Bool) {
        guard let c = queue.first else { return }
        Haptics.tap()
        if knew { known += 1 }
        let a = knew ? again : again + [c]
        flipped = false
        let rest = Array(queue.dropFirst())
        if rest.isEmpty && !a.isEmpty { queue = a.shuffled(); again = []; round += 1 } else { queue = rest; again = a }
    }
}

// MARK: - Study stats (filled by the focus timer)

@MainActor
final class StudyLog: ObservableObject {
    static let shared = StudyLog()
    /// day (yyyy-MM-dd) -> subject -> minutes
    @Published private(set) var days: [String: [String: Int]] = [:]
    @Published var goal: Int { didSet { UserDefaults.standard.set(goal, forKey: "ab.study.goal") } }
    private let key = "ab.study.log"

    init() {
        let g = UserDefaults.standard.integer(forKey: "ab.study.goal")
        goal = g > 0 ? g : 60
        days = loadJSON([String: [String: Int]].self, key) ?? [:]
    }

    static func dayKey(_ d: Date) -> String {
        let c = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: d)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    func add(minutes: Int, subject: String) {
        let k = StudyLog.dayKey(Date())
        let s = subject.isEmpty ? "General" : subject
        days[k, default: [:]][s, default: 0] += minutes
        let cut = StudyLog.dayKey(Date().addingTimeInterval(-366 * 86400))
        days = days.filter { $0.key >= cut }
        saveJSON(days, key)
    }

    func minutes(_ d: Date) -> Int { days[StudyLog.dayKey(d)]?.values.reduce(0, +) ?? 0 }

    /// Days in a row with some study, ending today (or yesterday, so the streak isn't lost before you study today)
    var streak: Int {
        var d = Date()
        if minutes(d) == 0 { d = d.addingTimeInterval(-86400) }
        var n = 0
        while minutes(d) > 0 { n += 1; d = d.addingTimeInterval(-86400) }
        return n
    }

    var week: [Date] { (0..<7).reversed().map { Date().addingTimeInterval(-Double($0) * 86400) } }
}

struct StudyStatsScreen: View {
    @ObservedObject private var log = StudyLog.shared

    var body: some View {
        let week = log.week
        let todayMin = log.minutes(Date())
        let top = max(log.goal, week.map { log.minutes($0) }.max() ?? 0)
        var subj: [String: Int] = [:]
        for d in week { for (k, v) in log.days[StudyLog.dayKey(d)] ?? [:] { subj[k, default: 0] += v } }
        let bySubject = subj.sorted { $0.value > $1.value }
        let fmt = DateFormatter()
        fmt.setLocalizedDateFormatFromTemplate("EEEEE")
        fmt.locale = AppLanguage.locale

        return List {
            Section {
                HStack(spacing: 10) {
                    tile("🔥", String(format: L("%d days"), log.streak), "Streak")
                    tile("⏱️", "\(todayMin)/\(log.goal)", "Minutes today")
                    tile("📅", "\(week.map { log.minutes($0) }.reduce(0, +))", "This week")
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }
            Section("Last 7 days") {
                HStack(alignment: .bottom, spacing: 8) {
                    ForEach(week, id: \.self) { d in
                        let m = log.minutes(d)
                        VStack(spacing: 4) {
                            Spacer(minLength: 0)
                            if m > 0 { Text("\(m)").font(.caption2).foregroundStyle(.secondary) }
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .fill(m >= log.goal ? Color.green : Color.abAccent.opacity(m > 0 ? 0.8 : 0.15))
                                .frame(height: max(4, CGFloat(m) / CGFloat(max(1, top)) * 100))
                            Text(fmt.string(from: d)).font(.caption2).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                .frame(height: 150)
                .padding(.vertical, 6)
            }
            Section {
                Stepper(String(format: L("Daily goal: %d minutes"), log.goal), value: $log.goal, in: 15...600, step: 15)
                NavigationLink { FocusTimerScreen() } label: { Label("Focus timer", systemImage: "timer") }
            } footer: {
                Text("Minutes count when a focus timer session ends. Green days reached your goal.")
            }
            if !bySubject.isEmpty {
                Section("This week by subject") {
                    ForEach(bySubject, id: \.key) { e in
                        HStack {
                            Text(e.key == "General" ? L("General") : e.key).lineLimit(1)
                            Spacer()
                            Text(String(format: L("%d min"), e.value)).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Study stats")
    }

    private func tile(_ emoji: String, _ value: String, _ label: LocalizedStringKey) -> some View {
        VStack(spacing: 2) {
            Text(emoji).font(.title3)
            Text(value).font(.headline).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Home: streak and today's minutes, once the student has used the focus timer
struct HomeStudyCard: View {
    @ObservedObject private var log = StudyLog.shared

    var body: some View {
        if !log.days.isEmpty {
            let today = log.minutes(Date())
            let streak = log.streak
            NavigationLink { StudyStatsScreen() } label: {
                HStack(spacing: 12) {
                    Text(streak > 0 ? "🔥" : "📚").font(.system(size: 28))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(streak > 0 ? String(format: L("%d-day study streak"), streak) : L("Start a new streak today"))
                            .font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                        Text(String(format: L("%d of %d minutes today"), today, log.goal)).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 4)
                    ZStack {
                        Circle().stroke(Color.abAccent.opacity(0.15), lineWidth: 4)
                        Circle().trim(from: 0, to: CGFloat(min(1, Double(today) / Double(max(1, log.goal)))))
                            .stroke(today >= log.goal ? Color.green : Color.abAccent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                    }
                    .frame(width: 32, height: 32)
                }
                .padding(14)
                .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(PressStyle())
            .padding(.horizontal, 16)
            .padding(.top, 12)
        }
    }
}
