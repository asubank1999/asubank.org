import SwiftUI

/// A link on the home page or the More tab
struct SiteLink: Identifiable {
    enum Target { case web(String), tab(AppTab), external(URL) }
    let emoji: String
    let title: String
    var subtitle: String? = nil
    var badge: String? = nil
    /// yyyy-MM-dd the page was added; shows a NEW badge for 30 days
    var added: String? = nil
    let target: Target
    var id: String { title }
    var shownBadge: String? { AB.isNew(added) ? "NEW" : badge }
}

enum Links {
    /// The three tools students use most, first on Home
    static let top: [SiteLink] = [
        .init(emoji: "📝", title: "Rate your teachers", subtitle: "See averages and rate anonymously.", target: .web("/requests/portal_to_rating")),
        .init(emoji: "👥", title: "Course matcher", subtitle: "Find classmates with similar schedules", target: .web("/requests/matcher_confirm")),
        .init(emoji: "🔄", title: "Subject swap", subtitle: "Trade a registered subject", target: .web("/swap"))
    ]

    static let quick: [SiteLink] = [
        .init(emoji: "📅", title: "Calendar 2026–2027", subtitle: "This year's important dates.", added: "2026-08-31", target: .web("/schedules/calendar27")),
        .init(emoji: "⏰", title: "My lectures", subtitle: "Your timetable, with reminders.", target: .tab(.lectures)),
        .init(emoji: "✅", title: "Exams & tasks", subtitle: "Everything you have coming up.", target: .web("/app/planner")),
        .init(emoji: "🆕", title: "Latest uploads", subtitle: "Newest PDFs, midterms, finals and quizzes.", target: .web("/guides/latest")),
        .init(emoji: "⚡", title: "Google Drive materials", subtitle: "Search every uploaded file.", badge: "FAST", target: .tab(.search)),
        .init(emoji: "🎯", title: "Focus timer", subtitle: "Study in short sessions with breaks.", target: .web("/app/focus")),
        .init(emoji: "🙋", title: "Absence tracker", subtitle: "Count missed lectures, stay under the limit.", added: "2026-11-08", target: .web("/app/absences")),
        .init(emoji: "🧮", title: "Final mark calculator", subtitle: "What you need on the final to pass.", added: "2026-11-08", target: .web("/app/finalmark")),
        .init(emoji: "🃏", title: "Flashcards", subtitle: "Make cards and test yourself.", added: "2026-11-08", target: .web("/app/flashcards")),
        .init(emoji: "🔥", title: "Study stats", subtitle: "Your streak and minutes studied.", added: "2026-11-08", target: .web("/app/stats")),
        .init(emoji: "📥", title: "Recent uploads", subtitle: "Latest study materials and resources.", target: .web("/portal/confirm_device")),
        .init(emoji: "📬", title: "Request a material", subtitle: "Can't find it? Ask for it.", target: .web("/requests/student_request_portal")),
        .init(emoji: "📤", title: "Upload files", subtitle: "Share your study materials.", target: .tab(.upload)),
        .init(emoji: "➕", title: "Useful gate", subtitle: "University resources in one place.", target: .web("/guides/links"))
    ]

    static let newHere: [SiteLink] = [
        .init(emoji: "🎓", title: "New Student Guide", subtitle: "Admissions, fees, majors, scholarships", added: "2026-09-13", target: .web("/guides/aspu_qa")),
        .init(emoji: "🔗", title: "ASU shortcuts", subtitle: "My ASU, EduGate, Teams, absences", target: .web("/guides/asu")),
        .init(emoji: "🎬", title: "How to search for files", subtitle: "Step by step, in the app and on the web", target: .web("/guides/tutorial")),
        .init(emoji: "👥", title: "Course WhatsApp groups", subtitle: "Groups for courses, clubs and interests", target: .web("/guides/groups")),
        .init(emoji: "📊", title: "Grading scale", target: .web("/guides/grading-scale-asu")),
        .init(emoji: "📝", title: "Placement tests", target: .web("/guides/placement")),
        .init(emoji: "🏫", title: "University halls", subtitle: "Type a hall number, find the building and floor", target: .web("/guides/halls")),
        .init(emoji: "🗺️", title: "Study plans", target: .external(URL(string: "https://drive.google.com/drive/folders/1QNvzQm4TjBg4x-dRpFp9roUBxdu2dQRY?usp=sharing")!))
    ]

    static let schedules: [SiteLink] = [
        .init(emoji: "📅", title: "Calendar 2026–2027", added: "2026-08-31", target: .web("/schedules/calendar27")),
        .init(emoji: "🗓️", title: "Academic calendar 2026", subtitle: "Important dates and deadlines", target: .web("/schedules/cal2026")),
        .init(emoji: "🌙", title: "Ramadan lecture timetable", target: .web("/schedules/ramadan_timetable")),
        .init(emoji: "📅", title: "Calendar 2025–2026", badge: "OLD", target: .web("/schedules/asu_date_asu2025_2026")),
        .init(emoji: "🚌", title: "Bus times", badge: "OUTDATED", target: .web("/schedules/bus20251"))
    ]

    static let community: [SiteLink] = [
        .init(emoji: "💬", title: "WhatsApp community", subtitle: "Updates, shared files and help", target: .external(AB.whatsapp)),
        .init(emoji: "🖥️", title: "Upload your presentations", subtitle: "For the campus display screen", target: .web("/upload/submit_file")),
        .init(emoji: "📋", title: "Submitted files (students)", subtitle: "Files uploaded by students", target: .web("/upload/student_submissions_board")),
        .init(emoji: "📢", title: "Student ads & services", subtitle: "Tutoring, design help, items for sale", target: .web("/requests/student_ads")),
        .init(emoji: "📸", title: "Campus event photos", target: .web("/guides/gallery")),
        .init(emoji: "📷", title: "Book a photoshoot", target: .web("/apps/photoshoot_booking")),
        .init(emoji: "⭐", title: "Rate our service", subtitle: "10 quick questions", target: .web("/requests/service_rating")),
        .init(emoji: "🗳️", title: "Vote", subtitle: "Community votes and polls", target: .web("/apps/voting"))
    ]

    static let help: [SiteLink] = [
        .init(emoji: "❓", title: "FAQs", target: .web("/guides/faqs")),
        .init(emoji: "✉️", title: "Contact us", target: .web("/guides/contact")),
        .init(emoji: "🤍", title: "Support UniHub", subtitle: "Donate with PayPal or CliQ", target: .web("/apps/donate")),
        .init(emoji: "🙏", title: "Thanks list", target: .web("/guides/thankslist")),
        .init(emoji: "📜", title: "WhatsApp group rules", target: .web("/legal/wa_tos")),
        .init(emoji: "📋", title: "Terms of Service", target: .web("/legal/uni_tos"))
    ]
}

/// Wraps a SiteLink's destination around any label
/// Small square shortcut on Home (Top picks): an emoji in a tinted circle and a short name
struct TopPickTile: View {
    let link: SiteLink
    var body: some View {
        VStack(spacing: 6) {
            Text(link.emoji).font(.system(size: 22))
                .frame(width: 42, height: 42)
                .background(Circle().fill(Color.accentColor.opacity(0.14)))
            Text(link.title).font(.caption.weight(.semibold)).fg14(.primary)
                .lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.85)
        }
        .frame(maxWidth: .infinity, minHeight: 86)
        .padding(.vertical, 8).padding(.horizontal, 4)
        .bgIn14(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay14(alignment: .topTrailing) {
            if link.shownBadge != nil { Circle().fill(Color.orange).frame(width: 8, height: 8).padding(8) }
        }
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

struct SiteLinkButton<Label: View>: View {
    let link: SiteLink
    @ViewBuilder let label: () -> Label
    @EnvironmentObject private var app: AppState
    @Environment(\.openURL) private var openURL

    var body: some View {
        switch link.target {
        case .web(let path):
            RouteLink(Route.web(link.title, path)) { label() }
        case .tab(let t):
            Button { app.tab = t; Haptics.tap() } label: { label() }.tint14(.primary)
        case .external(let url):
            Button { openURL(url) } label: { label() }.tint14(.primary)
        }
    }
}

struct SiteLinkRow: View {
    let link: SiteLink
    private var hidden: Bool {
        if AppConfig.isStore, case .web(let p) = link.target { return Moderation.paidPaths.contains(p) }
        return false
    }
    var body: some View {
        if !hidden { SiteLinkButton(link: link) {
            HStack(spacing: 12) {
                Text(link.emoji).font(.title3).frame(width: 30)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(link.title).fg14(.primary)
                        if let b = link.shownBadge { Badge(text: b, color: b == "NEW" ? .green : .orange) }
                    }
                    if let s = link.subtitle { Text(s).font(.footnote).fg14(.secondary) }
                }
                if case .external = link.target {
                    Spacer()
                    Image(systemName: "arrow.up.right").font(.footnote).fg14(Color.tertiary14)
                }
            }
        } }
    }
}

struct HomeView: View {
    @EnvironmentObject private var app: AppState
    @EnvironmentObject private var library: Library
    @EnvironmentObject private var saved: SavedStore
    @ObservedObject private var notices = NoticeStore.shared
    @ObservedObject private var account = AccountStore.shared
    @AppStorage(HomePrefs.key) private var hidden = ""
    @AppStorage(HomePrefs.compactKey) private var compact = false
    @AppStorage(HomePrefs.recentKey) private var remember = true

    @State private var linked: Route?
    @State private var customizing = false
    /// CI screenshots: -startScreen report | safety | customize | share
    @State private var startScreen = UserDefaults.standard.string(forKey: "startScreen")

    private var grid: [GridItem] { [GridItem(.adaptive(minimum: compact ? 104 : 158), spacing: 12)] }
    private func on(_ id: String) -> Bool { HomePrefs.on(id, hidden) }

    var body: some View {
        ABStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if on("online") { OnlineBox().padding(.horizontal, 16).padding(.top, 8) }
                    if on("top") {
                        HStack(spacing: 10) {
                            ForEach(Links.top) { l in
                                SiteLinkButton(link: l) { TopPickTile(link: l) }
                                    .buttonStyle(PressStyle())
                            }
                        }
                        .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 4)
                    }
                    if on("weather") { WeatherCard() }
                    if on("calendar") { HomeCalendarCard() }
                    if on("lecture") { NextLectureCard() }
                    if on("due") { HomeDueCard() }
                    if on("due") { HomeStudyCard() }
                    if on("hero") { hero.padding(.top, 4) }
                    SponsorCard()
                    if on("continue"), remember, !saved.recent.isEmpty {
                        SectionHeader(title: "Continue")
                            .overlay14(alignment: .bottomTrailing) {
                                Menu {
                                    Button() { saved.clearRecent(); Haptics.tap() } label: { Label("Clear the Continue list", systemImage: "trash") }
                                    Button { hidden = HomePrefs.setting("continue", false, in: hidden); Haptics.tap() } label: { Label("Hide Continue", systemImage: "eye.slash") }
                                    Button { customizing = true } label: { Label("Customize", systemImage: "slider.horizontal.3") }
                                } label: {
                                    Image(systemName: "ellipsis.circle").font(.body).frame(width: 36, height: 30)
                                }
                                .padding(.trailing, 12).padding(.bottom, 6)
                                .accessibilityLabel(Text("Continue"))
                            }
                        list(saved.recent.prefix(4).map(\.material))
                    }
                    if on("saved"), !saved.saved.isEmpty {
                        SectionHeader(title: "Saved")
                        list(saved.saved.prefix(4).map(\.material))
                    }
                    if on("quick") {
                        SectionHeader(title: "Quick links", arabic: "روابط سريعة")
                        LazyVGrid(columns: grid, spacing: 12) {
                            ForEach(Links.quick) { l in
                                SiteLinkButton(link: l) { Card(emoji: l.emoji, title: l.title, subtitle: compact ? nil : l.subtitle, badge: l.shownBadge) }
                                    .buttonStyle(PressStyle())
                            }
                        }
                        .padding(.horizontal, 16)
                    }

                    if on("schedules") {
                        SectionHeader(title: "Calendars & schedules", arabic: "التقويم والجداول")
                        group(Links.schedules)
                    }

                    if on("subjects") { subjects }

                    if on("newhere") {
                        SectionHeader(title: "New here? Start with these", arabic: "طالب جديد؟")
                        group(Links.newHere)
                    }
                    if on("community") {
                        SectionHeader(title: "Community & services")
                        group(Links.community)
                    }

                    Text("Peer-verified study materials curated by Applied Science University students, for ASU students.")
                        .font(.footnote).fg14(.secondary)
                        .padding(20)
                }
                .padding(.bottom, 20)
            }
            .background(Color.abBackground)
            .navigationTitle("UniHub")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) { MeButton() }
                ToolbarItem(placement: .primaryAction) {
                    HStack(spacing: 12) {
                        OnlineButton()
                        if account.isAdmin {
                            Button { app.overlayTab = .admin; Haptics.tap() } label: { Image(systemName: "shield.lefthalf.filled") }
                                .accessibilityLabel(Text("Admin"))
                        }
                        Button { app.tab = .notifications; Haptics.tap() } label: {
                            Image(systemName: notices.unread > 0 ? "bell.badge.fill" : "bell")
                        }
                        .accessibilityLabel(Text("Notifications"))
                    }
                }
            }
            .sheet(isPresented: $customizing) {
                ABStack {
                    CustomizeScreen()
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { customizing = false } } }
                }
                .environmentObject(saved)
            }
            .refreshable14 { await library.load() }
            .abDestinations()
            // asubank.org links opened from outside the app show their native screen here
            .push(isPresented: Binding(get: { linked != nil }, set: { if !$0 { linked = nil } })) {
                if let linked { RouteView(route: linked) }
            }
            .push(isPresented: Binding(get: { startScreen != nil }, set: { if !$0 { startScreen = nil } })) {
                switch startScreen {
                case "report": ReportCenterScreen()
                case "safety": SafetyScreen()
                case "customize": CustomizeScreen()
                case "share": ShareVideoScreen()
                default: EmptyView()
                }
            }
            .onAppear(perform: takeLink)
            .onChange(of: app.pendingRoute) { _ in takeLink() }
        }
    }

    private func takeLink() {
        guard let r = app.pendingRoute else { return }
        app.pendingRoute = nil
        linked = r
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Summaries, past papers and test banks for ASU students")
                .font(.subheadline.weight(.semibold))
                .fg14(.white)
            Button { app.tab = .search; Haptics.tap() } label: {
                HStack {
                    Image(systemName: "magnifyingglass")
                    Text("Search \(library.items.isEmpty ? "" : "\(library.items.count) ")materials, subjects, years…").lineLimit(1).minimumScaleFactor(0.85)
                    Spacer()
                }
                .font(.subheadline)
                .fg14(.secondary)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .bgIn14(Color.abSurface, in: Capsule())
            }
            .buttonStyle(PressStyle())
            HStack(spacing: 8) {
                heroButton("📤 Upload") { app.tab = .upload }
                heroButton("💬 WhatsApp") { UIApplication.shared.open(AB.whatsapp) }
                heroButton("★ Saved") { app.tab = .saved }
            }
        }
        .padding(14)
        .background(
            LinearGradient(gradient: Gradient(colors: AppTheme.heroColors), startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous)
        )
        .overlay14(alignment: .topTrailing) {
            Circle().fill(Color.abGold.opacity(0.25)).frame(width: 120).blur(radius: 30).offset(x: 20, y: -30).allowsHitTesting(false)
        }
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private func heroButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button { action(); Haptics.tap() } label: {
            Text(title).font(.caption.weight(.semibold)).fg14(.white)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .bgIn14(.white.opacity(0.16), in: Capsule())
        }
        .buttonStyle(PressStyle())
    }

    @ViewBuilder private var subjects: some View {
        SectionHeader(title: "Browse by subject", arabic: "تصفح حسب المادة")
        if library.subjects.isEmpty {
            HStack { Spacer(); if library.failed { Text("Connect to the internet to load subjects.").fg14(.secondary) } else { ABDots() }; Spacer() }
                .padding(30)
        }
        ForEach(library.categories, id: \.name) { cat in
            Text(cat.name.uppercased())
                .font(.footnote.weight(.semibold)).fg14(.secondary)
                .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 6)
            LazyVGrid(columns: grid, spacing: 12) {
                ForEach(cat.subjects) { s in
                    RouteLink(Route.subject(s)) {
                        Card(emoji: s.e ?? "📁", title: s.t, subtitle: [s.ar, s.n.map { "\($0) items" }].compactMap { $0 }.joined(separator: " · "))
                    }
                    .buttonStyle(PressStyle())
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func list(_ items: [Material]) -> some View {
        VStack(spacing: 0) {
            ForEach(items) { m in
                MaterialRow(m: m)
                    .padding(.horizontal, 16).padding(.vertical, 8)
                    .fg14(.primary)
                if m.id != items.last?.id { Divider().padding(.leading, 62) }
            }
        }
        .buttonStyle(PlainButtonStyle())
        .bgIn14(Color.abSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .padding(.horizontal, 16)
    }

    private func group(_ links: [SiteLink]) -> some View {
        VStack(spacing: 0) {
            ForEach(links) { l in
                SiteLinkRow(link: l)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.vertical, 11)
                    .contentShape(Rectangle())
                if l.id != links.last?.id { Divider().padding(.leading, 58) }
            }
        }
        .buttonStyle(PlainButtonStyle())
        .bgIn14(Color.abSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .padding(.horizontal, 16)
    }
}
