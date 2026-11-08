import SwiftUI

// Student tools that work offline: exams and tasks with due dates, a focus timer, and the calendar card on Home.

struct PlannerItem: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var title: String
    var course: String
    var kind: String // exam, assignment, task
    var due: Date
    var done = false
}

@MainActor
final class PlannerStore: ObservableObject {
    static let shared = PlannerStore()
    @Published private(set) var items: [PlannerItem] = []
    private let key = "ab.planner"

    init() {
        if let d = UserDefaults.standard.data(forKey: key), let l = try? JSONDecoder().decode([PlannerItem].self, from: d) { items = l }
    }

    var upcoming: [PlannerItem] { items.filter { !$0.done }.sorted { $0.due < $1.due } }
    var finished: [PlannerItem] { items.filter(\.done).sorted { $0.due > $1.due } }

    func save(_ i: PlannerItem) {
        if let n = items.firstIndex(where: { $0.id == i.id }) { items[n] = i } else { items.append(i) }
        commit()
    }

    func remove(_ id: String) {
        items.removeAll { $0.id == id }
        commit()
    }

    func toggle(_ id: String) {
        guard let n = items.firstIndex(where: { $0.id == id }) else { return }
        items[n].done.toggle()
        commit()
    }

    private func commit() {
        if let d = try? JSONEncoder().encode(items) { UserDefaults.standard.set(d, forKey: key) }
    }

    static func symbol(_ kind: String) -> String {
        kind == "exam" ? "graduationcap.fill" : kind == "assignment" ? "doc.text.fill" : "checkmark.circle.fill"
    }

    static func dueText(_ date: Date) -> String {
        let days = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: Date()), to: Calendar.current.startOfDay(for: date)).day ?? 0
        if days < 0 { return L("Overdue") }
        if days == 0 { return L("Today") }
        if days == 1 { return L("Tomorrow") }
        return String(format: L("In %d days"), days)
    }
}

struct PlannerScreen: View {
    @ObservedObject private var store = PlannerStore.shared
    @State private var editing: PlannerItem?

    var body: some View {
        List {
            if store.items.isEmpty {
                Section {
                    EmptyState(symbol: "checklist", title: "Nothing planned yet", text: "Add your exams and assignments, and the next ones show on Home.")
                        .listRowBackground(Color.clear)
                }
            }
            if !store.upcoming.isEmpty {
                Section("Coming up") { ForEach(store.upcoming) { row($0) } }
            }
            if !store.finished.isEmpty {
                Section("Done") { ForEach(store.finished) { row($0) } }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Exams & tasks")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editing = PlannerItem(title: "", course: "", kind: "exam", due: Date().addingTimeInterval(7 * 86400)) } label: { Image(systemName: "plus") }
                    .accessibilityLabel(Text("Add"))
            }
        }
        .sheet(item: $editing) { PlannerEditor(item: $0) }
    }

    private func row(_ i: PlannerItem) -> some View {
        HStack(spacing: 12) {
            Button { store.toggle(i.id); Haptics.tap() } label: {
                Image(systemName: i.done ? "checkmark.circle.fill" : "circle").font(.title3)
                    .foregroundStyle(i.done ? Color.green : Color.secondary)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(Text(i.done ? "Mark as not done" : "Mark as done"))
            Button { editing = i } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(i.title).strikethrough(i.done).foregroundStyle(.primary)
                    Text([i.course, i.due.formatted(date: .abbreviated, time: .omitted)].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.plain)
            Spacer()
            if !i.done {
                Text(PlannerStore.dueText(i.due)).font(.caption.weight(.semibold))
                    .foregroundStyle(i.due < Calendar.current.startOfDay(for: Date()) ? Color.red : Color.abAccent)
            }
        }
        .swipeActions {
            Button(role: .destructive) { store.remove(i.id) } label: { Label("Delete", systemImage: "trash") }
        }
    }
}

private struct PlannerEditor: View {
    @State var item: PlannerItem
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = PlannerStore.shared

    var body: some View {
        ABStack {
            Form {
                Section {
                    Picker("Type", selection: $item.kind) {
                        Text("Exam").tag("exam")
                        Text("Assignment").tag("assignment")
                        Text("Task").tag("task")
                    }
                    .pickerStyle(.segmented)
                    TextField("Title", text: $item.title)
                    TextField("Course (optional)", text: $item.course)
                    DatePicker("Date", selection: $item.due, displayedComponents: .date)
                }
                if store.items.contains(where: { $0.id == item.id }) {
                    Section {
                        Button("Delete", role: .destructive) { store.remove(item.id); dismiss() }
                    }
                }
            }
            .navigationTitle(item.title.isEmpty ? L("New item") : item.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { store.save(item); Haptics.success(); dismiss() }
                        .disabled(item.title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

/// Focus timer: work, then a break. Counts from an end time, so it stays right if the screen locks.
struct FocusTimerScreen: View {
    @AppStorage("ab.focus.minutes") private var minutes = 25
    @AppStorage("ab.focus.count") private var doneCount = 0
    @AppStorage("ab.focus.day") private var doneDay = ""
    @AppStorage("ab.focus.subject") private var subject = ""
    @State private var end: Date?
    @State private var left: TimeInterval = 0
    @State private var onBreak = false
    @State private var now = Date()

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    private var total: TimeInterval { (onBreak ? 5 : Double(minutes)) * 60 }
    private var remaining: TimeInterval { end.map { max(0, $0.timeIntervalSince(now)) } ?? (left > 0 ? left : total) }
    private var today: String { String(ISO8601DateFormatter().string(from: Date()).prefix(10)) }

    var body: some View {
        List {
            Section {
                VStack(spacing: 16) {
                    Text(onBreak ? "Break" : "Focus").font(.headline).foregroundStyle(.secondary)
                    ZStack {
                        Circle().stroke(Color.abAccent.opacity(0.15), lineWidth: 12)
                        Circle().trim(from: 0, to: CGFloat(Double(remaining) / Double(total)))
                            .stroke(Color.abAccent, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                        Text(String(format: "%02d:%02d", Int(remaining) / 60, Int(remaining) % 60))
                            .font(.system(size: 46, weight: .bold, design: .rounded)).monospacedDigit()
                            .environment(\.layoutDirection, .leftToRight)
                    }
                    .frame(width: 220, height: 220)
                    HStack(spacing: 12) {
                        Button(end == nil ? "Start" : "Pause") { toggle() }
                            .buttonStyle(.borderedProminent).tint(.abFill)
                        Button("Reset") { reset() }.buttonStyle(.bordered)
                    }
                }
                .frame(maxWidth: .infinity).padding(.vertical, 10)
            }
            Section {
                Stepper(String(format: L("Focus for %d minutes"), minutes), value: $minutes, in: 5...90, step: 5)
                    .disabled(end != nil)
                LabeledRow(title: "Sessions today", value: "\(doneDay == today ? doneCount : 0)")
                if !lectureSubjects().isEmpty {
                    Picker("Studying", selection: $subject) {
                        Text("General").tag("")
                        ForEach(lectureSubjects(), id: \.self) { Text($0).tag($0) }
                    }
                    .disabled(end != nil)
                }
                NavigationLink { StudyStatsScreen() } label: { Label("Study stats", systemImage: "flame") }
            } footer: {
                Text("Keep this screen open. A 5 minute break starts after each session.")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Focus timer")
        .onReceive(tick) { d in
            now = d
            if let end, d >= end { finished() }
        }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    private func toggle() {
        now = Date()
        if let e = end {
            left = max(0, e.timeIntervalSince(now))
            end = nil
            UIApplication.shared.isIdleTimerDisabled = false
        } else {
            end = now.addingTimeInterval(left > 0 ? left : total)
            left = 0
            UIApplication.shared.isIdleTimerDisabled = true
        }
    }

    private func reset() {
        end = nil
        left = 0
        onBreak = false
        UIApplication.shared.isIdleTimerDisabled = false
    }

    private func finished() {
        Haptics.success()
        if !onBreak {
            if doneDay != today { doneDay = today; doneCount = 0 }
            doneCount += 1
            StudyLog.shared.add(minutes: minutes, subject: subject)
        }
        onBreak.toggle()
        left = 0
        end = Date().addingTimeInterval(total)
        now = Date()
    }
}

private struct LabeledRow: View {
    let title: LocalizedStringKey
    let value: String
    var body: some View {
        HStack { Text(title); Spacer(); Text(value).foregroundStyle(.secondary) }
    }
}

// MARK: - Home cards

/// First thing on Home: the next date on the university calendar
struct HomeCalendarCard: View {
    @EnvironmentObject private var pages: SitePages
    private let path = "/schedules/calendar27"

    private var next: (name: String, date: Date)? {
        let today = Calendar.current.startOfDay(for: Date())
        return pages.page(path)?.cal?
            .filter { !$0.n.hasPrefix("الأسبوع") }
            .compactMap { e in AB.date(e.d).map { (name: e.n, date: $0) } }
            .filter { $0.date >= today }
            .min { $0.date < $1.date }
    }

    var body: some View {
        RouteLink(Route.page(path, "")) {
            HStack(spacing: 12) {
                Image(systemName: "calendar").font(.title3.weight(.semibold)).foregroundStyle(.white)
                    .frame(width: 44, height: 44).background(Color.abFill.abGradient, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("University calendar").font(.caption).foregroundStyle(.secondary)
                    Text(next?.name ?? L("See this year's important dates")).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                        .lineLimit(2).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 4)
                if let n = next {
                    Text(PlannerStore.dueText(n.date)).font(.caption.weight(.bold)).foregroundStyle(Color.abAccent)
                        .padding(.horizontal, 9).padding(.vertical, 4).background(Color.abAccentSoft, in: Capsule())
                }
            }
            .padding(14)
            .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PressStyle())
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}

/// The next exams and tasks the student added
struct HomeDueCard: View {
    @ObservedObject private var store = PlannerStore.shared

    var body: some View {
        if !store.upcoming.isEmpty {
            NavigationLink { PlannerScreen() } label: {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(store.upcoming.prefix(3)) { i in
                        HStack(spacing: 10) {
                            Image(systemName: PlannerStore.symbol(i.kind)).foregroundStyle(Color.abAccent).frame(width: 22)
                            Text(i.title).font(.subheadline.weight(.medium)).foregroundStyle(.primary).lineLimit(1)
                            Spacer(minLength: 4)
                            Text(PlannerStore.dueText(i.due)).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        }
                    }
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
