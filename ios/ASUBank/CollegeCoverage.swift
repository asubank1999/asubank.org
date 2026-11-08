import SwiftUI

/// How the library's files are spread over the colleges, counted live from materials.json.
/// General files aren't a college, so they're left out.
enum CollegeShares {
    struct Share: Identifiable {
        let name: String
        let files: Int
        let percent: Int
        var id: String { name }
    }

    static func from(_ subjects: [Subject]) -> [Share] {
        var totals: [String: Int] = [:]
        for s in subjects where s.c != "Archive" && s.c != "Popular & general" { totals[s.c, default: 0] += s.n ?? 0 }
        let all = totals.values.reduce(0, +)
        guard all > 0 else { return [] }
        return totals.filter { $0.value > 0 }
            .map { Share(name: $0.key, files: $0.value, percent: max(1, Int((Double($0.value) * 100 / Double(all)).rounded()))) }
            .sorted { $0.files > $1.files }
    }
}

struct CollegeCoverageScreen: View {
    @EnvironmentObject private var library: Library

    var body: some View {
        let shares = CollegeShares.from(library.subjects)
        List {
            Section14 {
                if shares.isEmpty { HStack { Spacer(); ABDots(); Spacer() } }
                ForEach(shares) { s in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(L(s.name)).font(.body.weight(.semibold))
                            Spacer()
                            Text("\(s.percent)%").font(.body.weight(.bold)).monoDigit14().fg14(Color.abAccent)
                        }
                        ProgressView(value: Double(s.percent), total: 100).tint14(Color.abAccent)
                        Text(String(format: L("%d files"), s.files)).font(.caption).fg14(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            } footer: {
                Text("Each college's share of the files sorted by college. It shows where we have the most, not how much of a college's courses is covered. General files and the archive aren't counted, and search covers them too.")
            }
            Section14 {
                RouteLink(Route.page("/banks/subjects", "All subjects")) { Label("Subjects directory", systemImage: "square.grid.2x2") }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Materials by college")
    }
}
