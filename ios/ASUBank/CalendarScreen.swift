import SwiftUI

/// University calendar (schedules/calendar27 etc.) as a native list with countdowns
struct CalendarScreen: View {
    let title: String
    let path: String
    let events: [CalEvent]
    @State private var showPast = false
    @State private var query = ""

    private struct Dated: Identifiable {
        let e: CalEvent
        let date: Date
        var id: String { e.id }
    }

    private var dated: [Dated] {
        events.compactMap { e in AB.date(e.d).map { Dated(e: e, date: $0) } }.sorted { $0.date < $1.date }
    }

    private var today: Date { Calendar.current.startOfDay(for: Date()) }

    private var shown: [Dated] {
        let q = AB.norm(query)
        return dated.filter { (showPast || $0.date >= today) && (q.isEmpty || AB.norm($0.e.n).contains(q)) }
    }

    private var months: [(key: String, items: [Dated])] {
        let f = DateFormatter()
        f.dateFormat = "LLLL yyyy"
        var order: [String] = []
        var groups: [String: [Dated]] = [:]
        for d in shown {
            let k = f.string(from: d.date)
            if groups[k] == nil { order.append(k) }
            groups[k, default: []].append(d)
        }
        return order.map { ($0, groups[$0] ?? []) }
    }

    private var next: Dated? { dated.first { $0.date >= today } }

    var body: some View {
        List {
            if let n = next, query.isEmpty {
                Section {
                    VStack(alignment: .trailing, spacing: 10) {
                        HStack {
                            Text("NEXT UP").font(.caption.weight(.bold)).foregroundStyle(.white.opacity(0.85))
                            Spacer()
                            Text(countdown(n.date)).font(.caption.weight(.bold))
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .background(.white.opacity(0.2), in: Capsule())
                                .foregroundStyle(.white)
                        }
                        .environment(\.layoutDirection, .leftToRight)
                        Text(n.e.n)
                            .font(.title3.weight(.bold)).foregroundStyle(.white)
                            .multilineTextAlignment(.trailing)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                        Text(n.date.formatted(date: .complete, time: .omitted))
                            .font(.subheadline).foregroundStyle(.white.opacity(0.85))
                    }
                    .padding(16)
                    .background(
                        LinearGradient(colors: [Color(UIColor(hex: 0x8C1D40)), Color(UIColor(hex: 0x5a0f28))], startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                    )
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }
            }
            Section {
                Toggle("Show past events", isOn: $showPast.animation()).tint(.abAccent)
            }
            ForEach(months, id: \.key) { m in
                Section(m.key) {
                    ForEach(m.items) { d in row(d) }
                }
            }
            if shown.isEmpty {
                EmptyState(symbol: "calendar", title: "No events", text: query.isEmpty ? "Turn on “Show past events” to see the whole year." : "Nothing matches “\(query)”.")
                    .listRowBackground(Color.clear)
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $query, prompt: "Search events")
        .navigationTitle(title.count > 24 ? "Academic calendar" : title)
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let url = AB.absolute(path) {
                    ShareButton(item: url) { Image(systemName: "square.and.arrow.up") }
                }
            }
        }
    }

    private func row(_ d: Dated) -> some View {
        let past = d.date < today
        let isToday = Calendar.current.isDate(d.date, inSameDayAs: today)
        return HStack(alignment: .top, spacing: 12) {
            VStack(spacing: 0) {
                Text(d.date.formatted(.dateTime.month(.abbreviated)).uppercased())
                    .font(.system(size: 11, weight: .bold)).foregroundStyle(isToday ? .white : Color.abAccent)
                Text(d.date.formatted(.dateTime.day()))
                    .font(.system(size: 22, weight: .bold)).foregroundStyle(isToday ? .white : .primary)
            }
            .frame(width: 50, height: 52)
            .background(isToday ? AnyShapeStyle(Color.abAccent) : AnyShapeStyle(Color.abAccentSoft), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .trailing, spacing: 4) {
                Text(d.e.n)
                    .font(.body.weight(.medium))
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                HStack(spacing: 6) {
                    Text(isToday ? "Today" : countdown(d.date))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(past ? Color.secondary : Color.abAccent)
                    Spacer()
                    Text([d.e.day, (d.e.w ?? 0) > 0 ? "Week \(d.e.w!)" : nil].compactMap { $0 }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(.secondary)
                }
                .environment(\.layoutDirection, .leftToRight)
            }
        }
        .padding(.vertical, 2)
        .opacity(past ? 0.55 : 1)
        .accessibilityElement(children: .combine)
    }

    private func countdown(_ date: Date) -> String {
        let days = Calendar.current.dateComponents([.day], from: today, to: date).day ?? 0
        switch days {
        case 0: return "Today"
        case 1: return "Tomorrow"
        case 2...: return "In \(days) days"
        case -1: return "Yesterday"
        default: return "\(-days) days ago"
        }
    }
}
