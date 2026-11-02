import SwiftUI

struct MoreView: View {
    @EnvironmentObject private var library: Library
    @EnvironmentObject private var config: AppConfig
    @AppStorage("ab.appearance") private var appearance = 0 // 0 system, 1 light, 2 dark
    @Environment(\.openURL) private var openURL
    @State private var showTerms = false
    @State private var copied = false
    @State private var sizes: (app: Int64, cache: Int64, data: Int64)?
    @State private var clearing = false
    @State private var confirmClear = false

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
                    NavigationLink(value: Route.page("/banks/subjects", "All subjects")) { Label("Subjects directory", systemImage: "square.grid.2x2") }
                    NavigationLink(value: Route.page("/guides/latest", "Latest uploads")) { Label("Latest uploads", systemImage: "sparkles") }
                    NavigationLink(value: Route.page("/requests/student_request_portal", "Request a material")) { Label("Request a material", systemImage: "tray.and.arrow.down") }
                    NavigationLink(value: Route.page("/guides/links", "ASU resources")) { Label("ASU resources (Useful gate)", systemImage: "square.grid.3x3.fill") }
                }
                Section("New students") { ForEach(Links.newHere) { SiteLinkRow(link: $0) } }
                Section("Calendars & schedules") { ForEach(Links.schedules) { SiteLinkRow(link: $0) } }
                Section("Community & services") { ForEach(Links.community) { SiteLinkRow(link: $0) } }
                Section("Help") { ForEach(Links.help.filter { $0.title != "Support ASU Bank" }) { SiteLinkRow(link: $0) } }

                if config.donate {
                    Section {
                        Button { openURL(URL(string: "https://www.paypal.com/paypalme/necro144")!) } label: {
                            row("creditcard.fill", .blue, "Donate with PayPal", "paypal.me/necro144", external: true)
                        }
                        Button { openURL(URL(string: "mailto:necro144@yahoo.com?body=" + "Hi! I want to donate to ASU Bank with CliQ.".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)!) } label: {
                            row("banknote.fill", .green, "Donate with CliQ", "Message the number ending in 71 on WhatsApp", external: true)
                        }
                        Button {
                            UIPasteboard.general.string = "necro144@yahoo.com"
                            Haptics.success()
                            withAnimation { copied = true }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { withAnimation { copied = false } }
                        } label: {
                            row(copied ? "checkmark.circle.fill" : "doc.on.doc", .gray, copied ? "Copied" : "Copy CliQ number", "necro144@yahoo.com")
                        }
                    } header: { Text("Support ASU Bank") } footer: {
                        Text("ASU Bank is a free, volunteer, non-profit project. Donations only help keep it running.")
                    }
                }

                Section {
                    sizeRow("App", "app.badge", sizes?.app)
                    sizeRow("Documents & data", "internaldrive", sizes?.data)
                    sizeRow("Cache", "archivebox", sizes?.cache)
                    Button(role: .destructive) { confirmClear = true } label: {
                        HStack {
                            Label("Clear cache", systemImage: "trash")
                            Spacer()
                            if clearing { ProgressView() }
                        }
                    }
                    .disabled(clearing)
                } header: { Text("Storage") } footer: {
                    Text("Clearing the cache frees space and reloads pages and the materials list from asubank.org. Your saved materials and settings stay.")
                }
                .confirmationDialog("Clear the cache?", isPresented: $confirmClear, titleVisibility: .visible) {
                    Button("Clear cache", role: .destructive) {
                        clearing = true
                        Task {
                            await AppStorageInfo.clear()
                            await library.load()
                            sizes = await AppStorageInfo.measure()
                            clearing = false
                            Haptics.success()
                        }
                    }
                } message: {
                    Text("Downloaded pages, images and web data will be fetched again when you need them.")
                }

                Section("About") {
                    HStack {
                        Label("Version", systemImage: "info.circle")
                        Spacer()
                        Text("\(AppConfig.current) (\(AppConfig.build))").foregroundStyle(.secondary)
                    }
                    Button { Task { await config.check(force: true); if !config.updateAvailable { Haptics.success() } } } label: {
                        HStack {
                            Label(config.updateAvailable ? "Update to \(config.latest ?? "")" : "Check for updates", systemImage: "arrow.down.app")
                            Spacer()
                            if config.checking { ProgressView() }
                            else if config.latest != nil && !config.updateAvailable { Text("Up to date").foregroundStyle(.secondary) }
                        }
                    }
                    Button { showTerms = true } label: { Label("About & terms", systemImage: "heart.text.square") }
                    NavigationLink(value: Route.page("/guides/contact", "Contact us")) { Label("Contact us", systemImage: "envelope") }
                }

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
            .task { sizes = await AppStorageInfo.measure() }
            .sheet(isPresented: $showTerms) { WelcomeTermsView { showTerms = false } }
            .abDestinations()
        }
    }

    private func sizeRow(_ title: String, _ symbol: String, _ bytes: Int64?) -> some View {
        HStack {
            Label(title, systemImage: symbol)
            Spacer()
            if let bytes { Text(ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)).foregroundStyle(.secondary) }
            else { ProgressView() }
        }
    }

    private func row(_ symbol: String, _ color: Color, _ title: String, _ sub: String, external: Bool = false) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).foregroundStyle(.white).frame(width: 30, height: 30)
                .background(color, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).foregroundStyle(.primary)
                Text(sub).font(.footnote).foregroundStyle(.secondary)
            }
            if external { Spacer(); Image(systemName: "arrow.up.right").font(.footnote).foregroundStyle(.tertiary) }
        }
    }
}
