import SwiftUI

struct MoreView: View {
    @EnvironmentObject private var library: Library
    @AppStorage("ab.appearance") private var appearance = 0 // 0 system, 1 light, 2 dark
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Appearance", selection: $appearance) {
                        Text("Automatic").tag(0)
                        Text("Light").tag(1)
                        Text("Dark").tag(2)
                    }
                    .pickerStyle(.segmented)
                } header: { Text("Appearance") }

                Section("All subjects") {
                    NavigationLink(value: Route.web("All subjects", "/banks/subjects")) { Label("Subjects directory", systemImage: "square.grid.2x2") }
                    NavigationLink(value: Route.web("Latest uploads", "/guides/latest")) { Label("Latest uploads", systemImage: "sparkles") }
                    NavigationLink(value: Route.web("Request a material", "/requests/student_request_portal")) { Label("Request a material", systemImage: "tray.and.arrow.down") }
                }
                Section("New students") { ForEach(Links.newHere) { SiteLinkRow(link: $0) } }
                Section("Calendars & schedules") { ForEach(Links.schedules) { SiteLinkRow(link: $0) } }
                Section("Community & services") { ForEach(Links.community) { SiteLinkRow(link: $0) } }
                Section("Help") { ForEach(Links.help) { SiteLinkRow(link: $0) } }

                Section {
                    Button { openURL(AB.site) } label: { Label("Open asubank.org in Safari", systemImage: "safari") }
                    ShareLink(item: AB.site, message: Text("ASU Bank — summaries, past papers and test banks for ASU students")) {
                        Label("Share ASU Bank", systemImage: "square.and.arrow.up")
                    }
                    Button {
                        Task { await library.load() }
                        Haptics.success()
                    } label: { Label("Refresh materials", systemImage: "arrow.clockwise") }
                } footer: {
                    Text("ASU Bank \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "") · \(library.items.count) materials in \(library.subjects.count) subjects.\nPeer-verified study materials by Applied Science University students, for ASU students.")
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("More")
            .abDestinations()
        }
    }
}
