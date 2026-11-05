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
    static let quick: [SiteLink] = [
        .init(emoji: "📅", title: "Calendar 2026–2027", subtitle: "This year's important dates.", added: "2026-08-31", target: .web("/schedules/calendar27")),
        .init(emoji: "⏰", title: "My lectures", subtitle: "Your timetable, with reminders.", target: .tab(.lectures)),
        .init(emoji: "✅", title: "Exams & tasks", subtitle: "Everything you have coming up.", target: .web("/app/planner")),
        .init(emoji: "🆕", title: "Latest uploads", subtitle: "Newest PDFs, midterms, finals and quizzes.", target: .web("/guides/latest")),
        .init(emoji: "⚡", title: "Google Drive materials", subtitle: "Search every uploaded file.", badge: "FAST", target: .tab(.search)),
        .init(emoji: "🎯", title: "Focus timer", subtitle: "Study in short sessions with breaks.", target: .web("/app/focus")),
        .init(emoji: "📥", title: "Recent uploads", subtitle: "Latest study materials and resources.", target: .web("/portal/confirm_device")),
        .init(emoji: "📬", title: "Request a material", subtitle: "Can't find it? Ask for it.", target: .web("/requests/student_request_portal")),
        .init(emoji: "📤", title: "Upload files", subtitle: "Share your study materials.", target: .tab(.upload)),
        .init(emoji: "📝", title: "Rate your teachers", subtitle: "See averages and rate anonymously.", target: .web("/requests/portal_to_rating")),
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
        .init(emoji: "👥", title: "Course matcher", subtitle: "Find classmates with similar schedules", target: .web("/requests/matcher_confirm")),
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
        .init(emoji: "🤍", title: "Support ASU Bank", subtitle: "Donate with PayPal or CliQ", target: .web("/apps/donate")),
        .init(emoji: "🙏", title: "Thanks list", target: .web("/guides/thankslist")),
        .init(emoji: "📜", title: "WhatsApp group rules", target: .web("/legal/wa_tos")),
        .init(emoji: "📋", title: "Terms of Service", target: .web("/legal/uni_tos"))
    ]
}

/// Wraps a SiteLink's destination around any label
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
            Button { app.tab = t; Haptics.tap() } label: { label() }.tint(.primary)
        case .external(let url):
            Button { openURL(url) } label: { label() }.tint(.primary)
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
                        Text(link.title).foregroundStyle(.primary)
                        if let b = link.shownBadge { Badge(text: b, color: b == "NEW" ? .green : .orange) }
                    }
                    if let s = link.subtitle { Text(s).font(.footnote).foregroundStyle(.secondary) }
                }
                if case .external = link.target {
                    Spacer()
                    Image(systemName: "arrow.up.right").font(.footnote).foregroundStyle(.tertiary)
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

    @State private var linked: Route?

    private let grid = [GridItem(.adaptive(minimum: 158), spacing: 12)]

    var body: some View {
        ABStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HomeCalendarCard()
                    NextLectureCard()
                    HomeDueCard()
                    hero.padding(.top, 4)
                    SponsorCard()
                    if !saved.recent.isEmpty {
                        SectionHeader(title: "Continue")
                        list(saved.recent.prefix(4).map(\.material))
                    }
                    if !saved.saved.isEmpty {
                        SectionHeader(title: "Saved")
                        list(saved.saved.prefix(4).map(\.material))
                    }
                    SectionHeader(title: "Quick links", arabic: "روابط سريعة")
                    LazyVGrid(columns: grid, spacing: 12) {
                        ForEach(Links.quick) { l in
                            SiteLinkButton(link: l) { Card(emoji: l.emoji, title: l.title, subtitle: l.subtitle, badge: l.shownBadge) }
                                .buttonStyle(PressStyle())
                        }
                    }
                    .padding(.horizontal, 16)

                    SectionHeader(title: "Calendars & schedules", arabic: "التقويم والجداول")
                    group(Links.schedules)

                    subjects

                    WeatherCard()

                    SectionHeader(title: "New here? Start with these", arabic: "طالب جديد؟")
                    group(Links.newHere)
                    SectionHeader(title: "Community & services")
                    group(Links.community)

                    Text("Peer-verified study materials curated by Applied Science University students, for ASU students.")
                        .font(.footnote).foregroundStyle(.secondary)
                        .padding(20)
                }
                .padding(.bottom, 20)
            }
            .background(Color.abBackground)
            .navigationTitle("ASU Bank")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { app.tab = .notifications; Haptics.tap() } label: {
                        Image(systemName: notices.unread > 0 ? "bell.badge.fill" : "bell")
                    }
                    .accessibilityLabel(Text("Notifications"))
                }
            }
            .refreshable { await library.load() }
            .abDestinations()
            // asubank.org links opened from outside the app show their native screen here
            .push(isPresented: Binding(get: { linked != nil }, set: { if !$0 { linked = nil } })) {
                if let linked { RouteView(route: linked) }
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
        VStack(alignment: .leading, spacing: 12) {
            Text("Summaries, past papers and test banks for ASU students")
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)
            Text("ملخصات وامتحانات سابقة وبنوك أسئلة لطلاب جامعة العلوم التطبيقية")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.85))
                .frame(maxWidth: .infinity, alignment: .trailing)
                .multilineTextAlignment(.trailing)
            Button { app.tab = .search; Haptics.tap() } label: {
                HStack {
                    Image(systemName: "magnifyingglass")
                    Text("Search \(library.items.isEmpty ? "" : "\(library.items.count) ")materials, subjects, years…")
                    Spacer()
                }
                .font(.body)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(PressStyle())
            HStack(spacing: 8) {
                heroButton("📤 Upload") { app.tab = .upload }
                heroButton("💬 WhatsApp") { UIApplication.shared.open(AB.whatsapp) }
                heroButton("★ Saved") { app.tab = .saved }
            }
        }
        .padding(20)
        .background(
            LinearGradient(colors: AppTheme.heroColors, startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous)
        )
        .overlay(alignment: .topTrailing) {
            Circle().fill(Color.abGold.opacity(0.25)).frame(width: 120).blur(radius: 30).offset(x: 20, y: -30).allowsHitTesting(false)
        }
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private func heroButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button { action(); Haptics.tap() } label: {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(.white.opacity(0.16), in: Capsule())
        }
        .buttonStyle(PressStyle())
    }

    @ViewBuilder private var subjects: some View {
        SectionHeader(title: "Browse by subject", arabic: "تصفح حسب المادة")
        if library.subjects.isEmpty {
            HStack { Spacer(); if library.failed { Text("Connect to the internet to load subjects.").foregroundStyle(.secondary) } else { ProgressView() }; Spacer() }
                .padding(30)
        }
        ForEach(library.categories, id: \.name) { cat in
            Text(cat.name.uppercased())
                .font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
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
                    .foregroundStyle(.primary)
                if m.id != items.last?.id { Divider().padding(.leading, 62) }
            }
        }
        .buttonStyle(.plain)
        .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
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
        .buttonStyle(.plain)
        .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .padding(.horizontal, 16)
    }
}
