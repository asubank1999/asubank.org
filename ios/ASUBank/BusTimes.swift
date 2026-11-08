import SwiftUI

/// One bus line from asubank.org/schedules/bus20251
struct BusRoute: Identifiable, Hashable {
    var id: String { name }
    let name: String
    let pickup: String
    let morning: [String]
    let evening: [String]
}

/// The timetable lives in the website page's script, so changing the page changes the app too
enum BusTimes {
    static let path = "/schedules/bus20251"
    private static let cacheKey = "ab.bus.page"

    static func parse(_ html: String) -> [BusRoute] {
        guard let start = html.range(of: "const data") else { return [] }
        let body = String(html[start.lowerBound...])
        let pattern = #"\{\s*name:\s*"([^"]*)"\s*,\s*pickup:\s*"([^"]*)"\s*,\s*morning:\s*\[([^\]]*)\]\s*,\s*evening:\s*\[([^\]]*)\]\s*\}"#
        guard let re = try? NSRegularExpression(pattern: pattern), let timeRe = try? NSRegularExpression(pattern: #""([^"]+)""#) else { return [] }
        let ns = body as NSString
        func times(_ s: String) -> [String] {
            let n = s as NSString
            return timeRe.matches(in: s, range: NSRange(location: 0, length: n.length)).map { n.substring(with: $0.range(at: 1)) }
        }
        return re.matches(in: body, range: NSRange(location: 0, length: ns.length)).map { m in
            BusRoute(
                name: ns.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespaces),
                pickup: ns.substring(with: m.range(at: 2)).trimmingCharacters(in: .whitespaces),
                morning: times(ns.substring(with: m.range(at: 3))),
                evening: times(ns.substring(with: m.range(at: 4)))
            )
        }
    }

    static func cached() -> [BusRoute] { parse(UserDefaults.standard.string(forKey: cacheKey) ?? "") }

    static func load() async -> [BusRoute]? {
        guard let url = URL(string: AB.site.absoluteString + path + ".html"),
              let (data, _) = try? await URLSession.shared.data14(from: url),
              let html = String(data: data, encoding: .utf8) else { return nil }
        let routes = parse(html)
        guard !routes.isEmpty else { return nil }
        UserDefaults.standard.set(html, forKey: cacheKey)
        return routes
    }
}

struct BusTimesScreen: View {
    @State private var routes = BusTimes.cached()
    @State private var loading = true
    @State private var failed = false
    @State private var query = ""
    @State private var evening = Calendar.current.component(.hour, from: Date()) >= 12

    private var shown: [BusRoute] {
        let q = query.trimmingCharacters(in: .whitespaces)
        if q.isEmpty { return routes }
        return routes.filter { $0.name.localizedCaseInsensitiveContains(q) || $0.pickup.localizedCaseInsensitiveContains(q) }
    }

    var body: some View {
        List {
            Section14 {
                Picker("", selection: $evening) {
                    Text("To the university (AM)").tag(false)
                    Text("Going home (PM)").tag(true)
                }
                .pickerStyle(SegmentedPickerStyle())
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }
            if routes.isEmpty && loading {
                Section14 { ForEach(0..<4, id: \.self) { _ in SkeletonRow() } }
            } else if routes.isEmpty && failed {
                Section14 { Text("Couldn't load the bus times. Check your connection.").fg14(.red) }
            } else if shown.isEmpty {
                Section14 { Text("No bus line matches.").fg14(.secondary) }
            } else {
                ForEach(shown) { r in Section14 { row(r) } }
            }
            Section14 {} footer: { Text("Times can change. The transport office has the final word.") }
        }
        .listStyle(InsetGroupedListStyle())
        .searchable14(text: $query, prompt: Text("Area or pickup point"))
        .navigationTitle("Bus times")
        .task14 { await refresh() }
        .refreshable14 { await refresh() }
    }

    private func refresh() async {
        if let fresh = await BusTimes.load() { routes = fresh; failed = false } else if routes.isEmpty { failed = true }
        loading = false
    }

    private func row(_ r: BusRoute) -> some View {
        let times = evening ? r.evening : r.morning
        return VStack(alignment: .leading, spacing: 8) {
            Label(r.name, systemImage: "bus.fill").font(.headline).fg14(.primary)
            if !r.pickup.isEmpty { Text(r.pickup).font(.footnote).fg14(.secondary) }
            if times.isEmpty {
                Text("No buses at this time").font(.footnote)
            } else {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 64), spacing: 6)], alignment: .leading, spacing: 6) {
                    ForEach(times, id: \.self) { t in
                        Text(t).font(.subheadline.weight(.semibold)).monoDigit14()
                            .fg14(Color.abAccent)
                            .padding(.horizontal, 12).padding(.vertical, 5)
                            .bgIn14(Color.abAccent.opacity(0.12), in: Capsule())
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}
