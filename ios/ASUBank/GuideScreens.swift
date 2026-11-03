import SwiftUI

// Native versions of the asubank.org guide pages: how to search, grading scale, WhatsApp groups,
// thanks list and the student ads service.

private let adminWhatsApp = "mailto:necro144@yahoo.com"

private func waMessage(_ text: String) -> URL {
    URL(string: adminWhatsApp + "?text=" + (text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""))!
}

/// A big rounded header card used by the guide screens
private struct GuideHero: View {
    let emoji: String
    let title: String
    let text: String
    var ar: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(emoji).font(.system(size: 34))
                .frame(width: 60, height: 60)
                .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            Text(title).font(.title2.weight(.bold)).foregroundStyle(.white)
            Text(text).font(.subheadline).foregroundStyle(.white.opacity(0.88))
            if let ar { BiText(text: ar, font: .footnote, color: .white.opacity(0.8)) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(Color.abAccent.abGradient, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
    }
}

private struct StepRow: View {
    let n: Int
    let title: String
    let text: String
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(n)").font(.subheadline.weight(.bold)).foregroundStyle(.white)
                .frame(width: 28, height: 28).background(Color.abAccent, in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.body.weight(.semibold))
                Text(text).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - How to search

struct SearchGuideScreen: View {
    @EnvironmentObject private var app: AppState
    @State private var tryQuery = ""
    private let examples = ["Calculus", "MATH101", "Anatomy", "Pharmacology", "Statistics", "English"]

    var body: some View {
        List {
            GuideHero(emoji: "🔍", title: "How to find any material",
                      text: "Everything on ASU Bank is searchable from one box: subject pages and the shared Google Drive.",
                      ar: "اكتب اسم المادة أو رمزها، وستجد الملفات فوراً")

            Section {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("Try it: type a subject", text: $tryQuery)
                        .submitLabel(.search)
                        .onSubmit(go)
                    if !tryQuery.isEmpty { Button("Search", action: go).font(.subheadline.weight(.semibold)) }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(examples, id: \.self) { e in
                            Button { app.search(e); Haptics.tap() } label: {
                                Text(e).font(.subheadline).padding(.horizontal, 12).padding(.vertical, 6)
                                    .background(Color.abAccentSoft, in: Capsule()).foregroundStyle(Color.abAccent)
                            }.buttonStyle(.plain)
                        }
                    }.padding(.vertical, 2)
                }
            } header: { Text("Try it now") }

            Section {
                StepRow(n: 1, title: "Open Search", text: "Tap the Search tab at the bottom. The box is always at the top.")
                StepRow(n: 2, title: "Type the subject or its code", text: "A few letters are enough: \"calc\", \"MATH101\" or a doctor's name. Arabic names work too.")
                StepRow(n: 3, title: "Pick a result", text: "Subject pages come first, then files found in the Google Drive. Folders open right in the app.")
                StepRow(n: 4, title: "Narrow it down", text: "Use the filter button to show only subject pages, only Drive files, or one year.")
                StepRow(n: 5, title: "Save it for later", text: "Tap the bookmark on any file or subject. It appears in the Saved tab, even offline.")
            } header: { Text("In the app") }

            Section {
                Label("Search the course name, not the file name. Files are named by the students who uploaded them.", systemImage: "lightbulb")
                Label("Try the English and the Arabic name if the first gives nothing.", systemImage: "character.book.closed")
                Label("Open the subject page to see every year and doctor in one place.", systemImage: "books.vertical")
                Label("New files show up in Latest uploads on the Home tab.", systemImage: "clock.arrow.circlepath")
            } header: { Text("Tips") }
            .font(.subheadline)

            Section {
                RouteLink(.page("/requests/student_request_portal", "Request a material")) {
                    Label("Request it from us", systemImage: "tray.and.arrow.down")
                }
                Button { app.tab = .upload } label: { Label("Upload it for others", systemImage: "icloud.and.arrow.up") }
                RouteLink(.page("/guides/groups", "WhatsApp groups")) {
                    Label("Ask your college's WhatsApp group", systemImage: "person.3")
                }
            } header: { Text("Can't find it?") }

            Section {
                Text("On asubank.org the search box is at the top of every page. Type the subject, press Enter, and the results load from the subject pages and the Drive together. Add the site to your home screen from Safari's Share menu to open it like an app.")
                    .font(.subheadline).foregroundStyle(.secondary)
            } header: { Text("On the website") }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("How to search")
        .navigationBarTitleDisplayMode(.inline)
        .dismissKeyboardOnScroll()
    }

    private func go() {
        let q = tryQuery.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        app.search(q)
    }
}

// MARK: - Grading scale

enum Grading {
    struct Band: Identifiable {
        let min: Double
        let en: String
        let ar: String
        let note: String
        let color: Color
        var id: Double { min }
    }
    static let bands: [Band] = [
        .init(min: 92, en: "Excellent with honors", ar: "ممتاز مع مرتبة الشرف", note: "Top of the scale", color: Color(UIColor(hex: 0x7c3aed))),
        .init(min: 84, en: "Excellent", ar: "ممتاز", note: "Outstanding work", color: Color(UIColor(hex: 0x1f8a3e))),
        .init(min: 76, en: "Very good", ar: "جيد جداً", note: "Strong and steady", color: Color(UIColor(hex: 0x0a84ff))),
        .init(min: 68, en: "Good", ar: "جيد", note: "Clearly acceptable", color: Color(UIColor(hex: 0xe8a00c))),
        .init(min: 60, en: "Pass", ar: "مقبول", note: "The minimum to pass", color: Color(UIColor(hex: 0xe8590c))),
    ]
    static func band(_ v: Double) -> Band? { bands.first { v >= $0.min } }
    static func range(_ b: Band) -> String {
        guard let i = bands.firstIndex(where: { $0.min == b.min }) else { return "" }
        return i == 0 ? "92 – 100" : "\(Int(b.min)) – under \(Int(bands[i - 1].min))"
    }
}

private struct CourseRow: Identifiable, Codable {
    var id = UUID()
    var name = ""
    var hours = 3
    var mark = ""
}

struct GradingScaleScreen: View {
    @State private var value = ""
    @AppStorage("ab.gpa.courses") private var saved = Data()
    @AppStorage("ab.gpa.prevAvg") private var prevAvg = ""
    @AppStorage("ab.gpa.prevHours") private var prevHours = ""
    @State private var courses: [CourseRow] = []

    private var number: Double? { Double(value.replacingOccurrences(of: ",", with: ".")).flatMap { (0...100).contains($0) ? $0 : nil } }

    private var term: (avg: Double, hours: Int)? {
        let done = courses.compactMap { c -> (Double, Int)? in
            guard let m = Double(c.mark), (0...100).contains(m), c.hours > 0 else { return nil }
            return (m, c.hours)
        }
        let h = done.reduce(0) { $0 + $1.1 }
        guard h > 0 else { return nil }
        return (done.reduce(0) { $0 + $1.0 * Double($1.1) } / Double(h), h)
    }

    private var cumulative: Double? {
        guard let term, let pa = Double(prevAvg), let ph = Int(prevHours), ph > 0, (0...100).contains(pa) else { return nil }
        return (pa * Double(ph) + term.avg * Double(term.hours)) / Double(ph + term.hours)
    }

    var body: some View {
        List {
            GuideHero(emoji: "🎓", title: "Grading scale", text: "How your term and cumulative average turn into a grade at ASU.",
                      ar: "سُلّم التقديرات للمعدل الفصلي والتراكمي")

            Section {
                ForEach(Grading.bands) { b in
                    let on = number.map { Grading.band($0)?.min == b.min } ?? false
                    HStack(spacing: 12) {
                        RoundedRectangle(cornerRadius: 3).fill(b.color).frame(width: 6, height: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(b.en).font(.body.weight(.semibold))
                            Text(b.note).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(b.ar).font(.subheadline.weight(.semibold))
                            Text(Grading.range(b)).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                    .listRowBackground(on ? b.color.opacity(0.15) : nil)
                }
            } header: { Text("The scale") } footer: { Text("Below 60 the average is under the passing limit.") }

            Section {
                HStack {
                    TextField("Your average, 0 to 100", text: $value).keyboardType(.decimalPad)
                    if let n = number { Text(String(format: "%.1f", n)).monospacedDigit().foregroundStyle(.secondary) }
                }
                if let n = number { resultRow(n) }
            } header: { Text("What's my grade?") }

            Section {
                ForEach($courses) { $c in
                    HStack(spacing: 8) {
                        TextField("Course", text: $c.name).frame(maxWidth: .infinity)
                        Picker("", selection: $c.hours) { ForEach(1...6, id: \.self) { Text("\($0) h").tag($0) } }
                            .labelsHidden().pickerStyle(.menu).fixedSize()
                        TextField("Mark", text: $c.mark).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 56)
                    }
                }
                .onDelete { courses.remove(atOffsets: $0) }
                Button { withAnimation { courses.append(CourseRow()) } } label: { Label("Add a course", systemImage: "plus.circle.fill") }
                if let t = term {
                    HStack { Text("Term average"); Spacer(); Text(String(format: "%.2f", t.avg)).font(.headline.monospacedDigit()) }
                    resultRow(t.avg)
                }
            } header: { Text("Term average calculator") } footer: { Text("Enter each course's credit hours and mark out of 100. Swipe a row to remove it.") }

            Section {
                TextField("Previous cumulative average", text: $prevAvg).keyboardType(.decimalPad)
                TextField("Hours passed before this term", text: $prevHours).keyboardType(.numberPad)
                if let c = cumulative {
                    HStack { Text("New cumulative"); Spacer(); Text(String(format: "%.2f", c)).font(.headline.monospacedDigit()) }
                    resultRow(c)
                }
            } header: { Text("Cumulative average") } footer: { Text("Uses the term above. The university's own calculation is final.") }

            Section {
                RouteLink(.web("Calculate your average", "https://web.asu.edu.jo/CalculateAvg/")) {
                    Label("Official ASU average calculator", systemImage: "function")
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Grading scale")
        .navigationBarTitleDisplayMode(.inline)
        .dismissKeyboardOnScroll()
        .onAppear {
            if courses.isEmpty {
                courses = (try? JSONDecoder().decode([CourseRow].self, from: saved)) ?? []
                if courses.isEmpty { courses = [CourseRow(), CourseRow(), CourseRow()] }
            }
        }
        .onChange(of: courses.map { "\($0.name)|\($0.hours)|\($0.mark)" }) { _ in
            saved = (try? JSONEncoder().encode(courses)) ?? Data()
        }
    }

    @ViewBuilder private func resultRow(_ v: Double) -> some View {
        if let b = Grading.band(v) {
            HStack {
                Image(systemName: "checkmark.seal.fill").foregroundStyle(b.color)
                Text(b.en).font(.body.weight(.semibold)).foregroundStyle(b.color)
                Spacer()
                Text(b.ar).font(.body.weight(.semibold)).foregroundStyle(b.color)
            }
        } else {
            Label("Under the passing limit (60)", systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red)
        }
    }
}

// MARK: - WhatsApp groups and resources

private struct GroupSection: Identifiable {
    let id: Int
    let title: String
    let resources: Bool
    var links: [PageLink]
}

struct GroupsScreen: View {
    let page: SitePage
    @Environment(\.openURL) private var openURL
    @State private var query = ""
    @State private var kind = 0

    private var sections: [GroupSection] {
        var out: [GroupSection] = []
        var parent = ""
        var title = ""
        var resources = false
        for b in page.b {
            if let h = b.h {
                if h == "Educational Resources" { resources = true; continue }
                if h.lowercased().hasSuffix(" year"), !parent.isEmpty {
                    title = "\(parent) · \(h)"
                } else {
                    parent = h
                    title = h
                }
            } else if let l = b.links, !title.isEmpty {
                out.append(GroupSection(id: out.count, title: title, resources: resources, links: l))
            }
        }
        return out
    }

    private var shown: [GroupSection] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return sections.filter { $0.resources == (kind == 1) }.compactMap { s in
            if q.isEmpty { return s }
            if s.title.localizedCaseInsensitiveContains(q) { return s }
            var s2 = s
            s2.links = s.links.filter { $0.t.localizedCaseInsensitiveContains(q) || ($0.s ?? "").localizedCaseInsensitiveContains(q) }
            return s2.links.isEmpty ? nil : s2
        }
    }

    var body: some View {
        List {
            GuideHero(emoji: "💬", title: "University groups", text: "Join your college's WhatsApp groups, buses and study resources.",
                      ar: "روابط قروبات الواتساب والمصادر الدراسية")
            Picker("Show", selection: $kind) {
                Text("WhatsApp groups").tag(0)
                Text("Study resources").tag(1)
            }
            .pickerStyle(.segmented)
            .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)

            ForEach(shown) { s in
                Section {
                    ForEach(Array(s.links.enumerated()), id: \.offset) { _, l in row(l) }
                } header: { Text(s.title) }
            }
            if shown.isEmpty {
                Text(query.isEmpty ? "Nothing here yet." : "No group matches \"\(query)\".").foregroundStyle(.secondary)
            }
            Section {
                Button { openURL(waMessage("Hi ASU Bank, I'd like to add a WhatsApp group: ")) } label: {
                    Label("Add your group's link", systemImage: "plus.bubble")
                }
            } footer: { Text("Groups are run by students, not by ASU Bank. Be kind and follow each group's rules.") }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Groups & resources")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $query, prompt: "Search groups")
    }

    @ViewBuilder private func row(_ l: PageLink) -> some View {
        let isWA = l.u?.contains("whatsapp.com") == true || l.u?.contains("wa.me") == true
        let label = HStack(spacing: 12) {
            if isWA {
                Image(systemName: "bubble.left.and.bubble.right.fill").font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 32, height: 32).background(Color(UIColor(hex: 0x25d366)), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            } else if let u = l.u {
                KindTile(kind: AB.sitePath(u) != nil ? .page : AB.kind(u, l.t), size: 32)
            }
            VStack(alignment: .leading, spacing: 2) {
                BiText(text: l.t)
                if let s = l.s { BiText(text: s, font: .caption, color: .secondary) }
            }
            if isWA {
                Text("Join").font(.caption.weight(.bold)).foregroundStyle(Color(UIColor(hex: 0x1f8a3e)))
                    .padding(.horizontal, 10).padding(.vertical, 4).background(Color(UIColor(hex: 0x25d366)).opacity(0.15), in: Capsule())
            }
        }
        if let u = l.u {
            SiteLinkRowButton(url: u, title: l.t) { label }
                .contextMenu {
                    Button { UIPasteboard.general.string = u; Haptics.success() } label: { Label("Copy link", systemImage: "doc.on.doc") }
                    if let url = URL(string: u) { ShareButton(item: url) { Label("Share", systemImage: "square.and.arrow.up") } }
                }
        } else { label }
    }
}

// MARK: - Thanks list

struct ThanksScreen: View {
    let page: SitePage?
    @Environment(\.openURL) private var openURL
    @EnvironmentObject private var library: Library
    @State private var shown = false
    private let palette: [UInt32] = [0x8C1D40, 0x0a84ff, 0x1f8a3e, 0xaf52de, 0xe8590c, 0xff2d55, 0x5e5ce6, 0x00a3a3]

    private var names: [String] {
        page?.b.compactMap(\.list).first ?? ["Qusai", "Lina", "Hxcker", "Osama", "Sama", "اعشق تمردي"]
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                VStack(spacing: 8) {
                    Text("💛").font(.system(size: 54))
                    Text("Thank you!").font(.largeTitle.weight(.bold))
                    Text("ASU Bank is built by students, for students. These people uploaded, sorted and shared the materials you use every day.")
                        .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    Text("شكراً لكل من ساهم في بناء المنصة").font(.footnote).foregroundStyle(.secondary)
                }
                .padding(.top, 8)

                HStack(spacing: 10) {
                    ForEach([("\(library.items.count)", "Materials"), ("\(library.subjects.count)", "Subjects"), ("\(names.count + 1)", "Helpers")], id: \.1) { s in
                        VStack(spacing: 2) {
                            Text(s.0).font(.title2.weight(.bold)).foregroundStyle(Color.abAccent).monospacedDigit()
                            Text(s.1).font(.caption).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Color(UIColor.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                }

                // the founder, above everyone else
                HStack(spacing: 14) {
                    Text("👑").font(.system(size: 28)).frame(width: 60, height: 60).background(Color(UIColor(hex: 0xFFC627)), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Alan").font(.title2.weight(.bold))
                        Text("Founder · builds and runs ASU Bank").font(.subheadline).opacity(0.85)
                    }
                    Spacer()
                }
                .foregroundStyle(.white)
                .padding(18)
                .background(LinearGradient(colors: [Color(UIColor(hex: 0x8C1D40)), Color(UIColor(hex: 0xB8336A))], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 20, style: .continuous))

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), spacing: 12)], spacing: 12) {
                    ForEach(Array(names.enumerated()), id: \.offset) { i, n in
                        let c = Color(UIColor(hex: palette[i % palette.count]))
                        VStack(spacing: 10) {
                            Text(String(n.prefix(1)).uppercased())
                                .font(.title.weight(.bold)).foregroundStyle(.white)
                                .frame(width: 64, height: 64)
                                .background(c.abGradient, in: Circle())
                                .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 2))
                                .shadow(color: c.opacity(0.35), radius: 8, y: 4)
                            Text(n).font(.headline).lineLimit(1).minimumScaleFactor(0.7)
                            Text(i == 0 ? "⭐ First contributor" : "Contributor").font(.caption).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 18)
                        .background(Color(UIColor.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .opacity(shown ? 1 : 0)
                        .scaleEffect(shown ? 1 : 0.85)
                        .animation(.spring(response: 0.45, dampingFraction: 0.75).delay(0.06 * Double(i % 12)), value: shown)
                    }
                }

                VStack(spacing: 10) {
                    Text("Want your name here?").font(.headline)
                    Text("Upload materials, help sort them, or report broken links. Message ASU-Requests and join the team.")
                        .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    Button { openURL(waMessage("Hi ASU Bank, I'd like to help the team")) } label: {
                        Label("Join the team on WhatsApp", systemImage: "hand.raised.fill").frame(maxWidth: .infinity).padding(.vertical, 4)
                    }
                    .buttonStyle(.borderedProminent).tint(.abAccent)
                }
                .padding(18)
                .background(Color.abAccentSoft, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .padding(16)
        }
        .background(Color.abBackground)
        .navigationTitle("Thanks list")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { shown = true }
    }
}

// MARK: - Student ads

struct StudentAdsScreen: View {
    @Environment(\.openURL) private var openURL
    @State private var copied = false

    var body: some View {
        List {
            GuideHero(emoji: "📣", title: "Student ads", text: "Advertise your services, products and talents to the whole university. Coming soon.",
                      ar: "أعلن عن خدماتك ومنتجاتك لطلاب الجامعة — قريباً")

            Section {
                feature("tag.fill", 0xe8590c, "Affordable", "Student-friendly price: 10 JD in total.")
                feature("scope", 0x0a84ff, "Targeted", "Shown to ASU students in the app and on asubank.org.")
                feature("lock.shield.fill", 0x1f8a3e, "Fair and safe", "You pay the second half only if your ad is accepted.")
            } header: { Text("Why advertise here") }

            Section {
                HStack(spacing: 0) {
                    price("5 JD", "When you send your ad")
                    Divider().frame(height: 44)
                    price("5 JD", "Only if it's accepted")
                }
                Button {
                    UIPasteboard.general.string = "ALANQ99"; Haptics.success(); withAnimation { copied = true }
                } label: {
                    HStack {
                        Label("CliQ alias: ALANQ99", systemImage: "banknote.fill")
                        Spacer()
                        Text(copied ? "Copied" : "Bank of Jordan · copy").font(.caption).foregroundStyle(.secondary)
                    }
                }
            } header: { Text("Pricing · 10 JD total") }

            Section {
                Button { openURL(waMessage("Hi ASU Bank, tell me when student ads open. I'd like to advertise: ")) } label: {
                    Label("Tell me when it opens", systemImage: "bell.badge.fill")
                }
                Button { openURL(waMessage("Hi ASU Bank, I have a question about student ads")) } label: {
                    Label("Ask a question", systemImage: "questionmark.bubble")
                }
            } footer: { Text("Ads are reviewed by the ASU Bank team before they go live.") }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Student ads")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func feature(_ symbol: String, _ hex: UInt32, _ title: String, _ text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).foregroundStyle(.white).frame(width: 32, height: 32)
                .background(Color(UIColor(hex: hex)).abGradient, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body.weight(.semibold))
                Text(text).font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func price(_ v: String, _ t: String) -> some View {
        VStack(spacing: 2) {
            Text(v).font(.title2.weight(.bold)).foregroundStyle(Color.abAccent)
            Text(t).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Donate

struct DonateScreen: View {
    @Environment(\.openURL) private var openURL
    @State private var copied = false

    var body: some View {
        List {
            GuideHero(emoji: "🤍", title: "Support ASU Bank", text: "ASU Bank is free and run by a volunteer student. Donations keep it online and growing.",
                      ar: "المنصة مجانية وتطوعية — تبرعك يساعدنا على الاستمرار")
            Section {
                Button {
                    UIPasteboard.general.string = "ALANQ99"; Haptics.success(); withAnimation { copied = true }
                } label: {
                    HStack(spacing: 12) {
                        Text("🏦").font(.title3).frame(width: 30)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("CliQ alias: ALANQ99").foregroundStyle(.primary)
                            Text("Bank of Jordan · tap to copy").font(.footnote).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: copied ? "checkmark.circle.fill" : "doc.on.doc").foregroundStyle(copied ? .green : .secondary)
                    }
                }
                Button { openURL(URL(string: "https://www.paypal.com/paypalme/necro144")!) } label: {
                    HStack(spacing: 12) {
                        Text("💙").font(.title3).frame(width: 30)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("PayPal").foregroundStyle(.primary)
                            Text("paypal.me/necro144 · cards and PayPal balance").font(.footnote).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "arrow.up.right").font(.footnote).foregroundStyle(.tertiary)
                    }
                }
            } header: { Text("Donate") } footer: {
                Text("Donations are optional and never unlock content. Everything on ASU Bank stays free. ASU Bank never asks for money from anyone else.")
            }
            Section {
                RouteLink(.page("/guides/thankslist", "Thanks list")) { Label("People who helped ASU Bank", systemImage: "heart") }
                RouteLink(.page("/guides/contact", "Contact us")) { Label("Questions about donating", systemImage: "envelope") }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Support ASU Bank")
        .navigationBarTitleDisplayMode(.inline)
    }
}
