import SwiftUI
import AVFoundation
import AudioToolbox
import UserNotifications

// MARK: - groups people ask to add

struct ApprovedGroup: Decodable, Identifiable {
    let id: String
    let title: String
    var category: String?
    let url: String
}

/// Asks for a group to be listed. The owner accepts or rejects it in the Admin tab.
struct GroupRequestSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var account = AccountStore.shared
    @State private var title = ""
    @State private var category = ""
    @State private var url = ""
    @State private var notes = ""
    @State private var busy = false
    @State private var sent = false
    @State private var error: String?
    @State private var mine: [[String: Any]] = []

    private let extra = ["Course", "Club", "Dorm or housing", "Other"]
    private var valid: Bool { title.trimmingCharacters(in: .whitespaces).count >= 3 && url.lowercased().hasPrefix("https://") }

    var body: some View {
        ABStack {
            Form {
                if sent {
                    Section {
                        VStack(spacing: 10) {
                            Image(systemName: "checkmark.circle.fill").font(.system(size: 56)).foregroundStyle(.green)
                            Text("Request sent").font(.title2.weight(.bold))
                            Text("The owner will review your group. We'll notify you when it's accepted or rejected.")
                                .multilineTextAlignment(.center).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 20)
                    }
                } else if !account.signedIn {
                    Section {
                        Text("Sign in so we can tell you when your group is accepted.")
                        Button("Sign in") { account.signInShown = true }
                    }
                } else {
                    Section {
                        TextField("Group name", text: $title)
                        Picker("Category", selection: $category) {
                            Text("Not set").tag("")
                            ForEach(ABColleges.all, id: \.self) { Text($0).tag($0) }
                            ForEach(extra, id: \.self) { Text(L($0)).tag($0) }
                        }
                    } header: { Text("About the group") }
                    Section {
                        TextField("https://chat.whatsapp.com/…", text: $url)
                            .keyboardType(.URL).autocapitalization(.none).disableAutocorrection(true)
                    } header: { Text("Invite link") } footer: { Text("Paste the group's invite link, starting with https://") }
                    Section("Notes (optional)") { TextField("Anything the owner should know", text: $notes) }
                    if let error { Section { Text(error).foregroundStyle(.red) } }
                    if !mine.isEmpty {
                        Section("Your requests") {
                            ForEach(Array(mine.enumerated()), id: \.offset) { _, r in
                                HStack {
                                    Text(r["title"] as? String ?? "").lineLimit(1)
                                    Spacer()
                                    statusBadge(r["status"] as? String ?? "pending")
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Add your group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(sent ? "Done" : "Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if !sent && account.signedIn { Button(busy ? "Sending…" : "Send") { Task { await send() } }.disabled(!valid || busy) }
                }
            }
            .task {
                guard account.signedIn else { return }
                mine = ((try? await account.call("myGroupRequests"))?["requests"] as? [[String: Any]]) ?? []
            }
        }
    }

    private func send() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await account.call("groupRequest", ["title": title, "category": category, "url": url.trimmingCharacters(in: .whitespaces), "notes": notes])
            withAnimation { sent = true }
            Haptics.success()
        } catch { self.error = error.localizedDescription; Haptics.error() }
    }
}

@ViewBuilder func statusBadge(_ status: String) -> some View {
    switch status {
    case "approved": Badge(text: L("Accepted"), color: .green)
    case "rejected": Badge(text: L("Rejected"), color: .red)
    default: Badge(text: L("Waiting"), color: .orange)
    }
}

/// Admins: groups people asked to add
struct AdminGroupRequestsScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @Environment(\.openURL) private var openURL
    @State private var requests: [[String: Any]] = []
    @State private var showAll = false
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            Picker("", selection: $showAll) {
                Text("Waiting").tag(false)
                Text("All").tag(true)
            }
            .pickerStyle(.segmented)
            if let error { Text(error).foregroundStyle(.red) }
            if loading { ABDots() }
            else if requests.isEmpty && error == nil { Text("Nothing to review.").foregroundStyle(.secondary) }
            ForEach(Array(requests.enumerated()), id: \.offset) { _, r in row(r) }
        }
        .navigationTitle("Group requests")
        .task(id: showAll) { await load() }
        .refreshable { await load() }
    }

    private func row(_ r: [String: Any]) -> some View {
        let status = r["status"] as? String ?? "pending"
        let url = r["url"] as? String ?? ""
        return VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text(r["title"] as? String ?? "").font(.body.weight(.semibold))
                Spacer()
                statusBadge(status)
            }
            if let c = r["category"] as? String, !c.isEmpty { Text(L(c)).font(.caption).foregroundStyle(.secondary) }
            Button(url) { if let u = URL(string: url) { openURL(u) } }.font(.caption).lineLimit(1)
            if let n = r["notes"] as? String, !n.isEmpty { Text(n).font(.caption) }
            Text("@\(r["username"] as? String ?? "") · \(Social.ago(r["created"] as? String))").font(.caption2).foregroundStyle(.tertiary)
            HStack(spacing: 14) {
                if status != "approved" { Button("Accept") { Task { await set(r, "approved") } }.tint(.green) }
                if status != "rejected" { Button("Reject", role: .destructive) { Task { await set(r, "rejected") } } }
            }
            .font(.subheadline)
        }
        .buttonStyle(.borderless)
        .padding(.vertical, 2)
    }

    private func set(_ r: [String: Any], _ status: String) async {
        do {
            _ = try await account.call("adminGroupSet", ["id": r["id"] as? String ?? "", "status": status])
            Haptics.success()
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func load() async {
        let tag = showAll ? "all" : "open"
        if requests.isEmpty, let r = account.saved("adminGroupRequests", tag: tag) {
            requests = (r["requests"] as? [[String: Any]]) ?? []
            loading = false
        }
        do {
            requests = (try await account.callSaving("adminGroupRequests", ["all": showAll], tag: tag)["requests"] as? [[String: Any]]) ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }
}

// MARK: - photoshoot

/// Books a session with ASU Lens & Light Studio. Posts to the same script as the website's form.
struct PhotoshootScreen: View {
    private static let endpoint = URL(string: "https://script.google.com/macros/s/AKfycbxMdRZImJfRkBjjG_l-DhOrJLZsVghrpuRybmGZwQAv8jSiBEYRb639e0Sl4wUV7S2H2A/exec")!
    private static let promos: [String: (percent: Double, expires: String)] = ["FRIENDSONLY70": (70, "2028-12-12")]

    @ObservedObject private var account = AccountStore.shared
    @State private var first = ""
    @State private var middle = ""
    @State private var last = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var date = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
    @State private var session = "portrait"
    @State private var from = PhotoshootScreen.at(10)
    @State private var to = PhotoshootScreen.at(11)
    @State private var outside = false
    @State private var address = ""
    @State private var edits = 0
    @State private var premium = true
    @State private var notes = ""
    @State private var promo = ""
    @State private var busy = false
    @State private var sent = false
    @State private var error: String?

    private let sessions = [("portrait", "Portrait"), ("graduation", "Graduation"), ("group", "Group"), ("event", "Event"),
                            ("product", "Product / Commercial"), ("other", "Other")]

    private static func at(_ hour: Int) -> Date { Calendar.current.date(bySettingHour: hour, minute: 0, second: 0, of: Date()) ?? Date() }

    private func minutes(_ d: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
    private var hours: Double { max(0, Double(minutes(to) - minutes(from)) / 60) }
    private var hourFee: Double { hours * 5 }
    private var editFee: Double { Double(edits) * 3 }
    private var travelFee: Double { outside ? 5 : 0 }
    private var subtotal: Double { hourFee + editFee + travelFee }

    private var activePromo: (code: String, percent: Double)? {
        let code = promo.trimmingCharacters(in: .whitespaces).uppercased()
        guard let p = Self.promos[code], Self.day(Date()) <= p.expires else { return nil }
        return (code, p.percent)
    }
    private var promoAmount: Double { subtotal * (activePromo?.percent ?? 0) / 100 }
    private var total: Double { subtotal - promoAmount }
    private var valid: Bool {
        !first.trimmingCharacters(in: .whitespaces).isEmpty && !last.trimmingCharacters(in: .whitespaces).isEmpty
            && !phone.trimmingCharacters(in: .whitespaces).isEmpty && hours > 0
    }

    private static func day(_ d: Date) -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: d)
    }

    private static func clock(_ d: Date) -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "HH:mm"
        return f.string(from: d)
    }

    private func jd(_ v: Double) -> String { String(format: "%.2f", v) + " " + L("JD") }

    var body: some View {
        Form {
            if sent {
                Section {
                    VStack(spacing: 10) {
                        Image(systemName: "checkmark.circle.fill").font(.system(size: 56)).foregroundStyle(.green)
                        Text("Booking submitted!").font(.title2.weight(.bold))
                        Text("The photographer will review your request and contact you on \(phone) with the final invoice and payment details.")
                            .multilineTextAlignment(.center).foregroundStyle(.secondary)
                        Button("Book another session") { reset() }.padding(.top, 6)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 18)
                }
            } else {
                Section {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("📸").font(.largeTitle)
                        Text("ASU Lens & Light Studio").font(.title3.weight(.bold))
                        Text("Every frame, a story. Fill in your details and see the price as you go. The photographer reviews your request and sends the final invoice.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
                Section("Your details") {
                    TextField("First name", text: $first).textContentType(.givenName)
                    TextField("Middle name (optional)", text: $middle).textContentType(.middleName)
                    TextField("Last name", text: $last).textContentType(.familyName)
                    TextField("WhatsApp or phone", text: $phone).keyboardType(.phonePad).textContentType(.telephoneNumber)
                    TextField("Email (optional)", text: $email).keyboardType(.emailAddress).autocapitalization(.none)
                }
                Section("Session") {
                    DatePicker("Date", selection: $date, in: Date()..., displayedComponents: .date)
                    Picker("Session type", selection: $session) { ForEach(sessions, id: \.0) { Text(L($0.1)).tag($0.0) } }
                    DatePicker("From", selection: $from, displayedComponents: .hourAndMinute)
                    DatePicker("To", selection: $to, displayedComponents: .hourAndMinute)
                    if hours <= 0 { Text("The end time must be after the start time.").font(.caption).foregroundStyle(.red) }
                }
                Section {
                    Picker("Location", selection: $outside) {
                        Text("ASU campus").tag(false)
                        Text("Outside ASU").tag(true)
                    }
                    .pickerStyle(.segmented)
                    TextField(outside ? "Address" : "Where on campus? (optional)", text: $address)
                } header: { Text("Location") } footer: {
                    Text(outside ? "Outside ASU adds a 5 JD travel fee." : "No travel fee on campus.")
                }
                Section {
                    Stepper(value: $edits, in: 0...500) {
                        HStack { Text("Photos to edit"); Spacer(); Text("\(edits)").foregroundStyle(.secondary).monospacedDigit() }
                    }
                    Picker("Edit quality", selection: $premium) {
                        Text("Standard").tag(false)
                        Text("Premium").tag(true)
                    }
                    .pickerStyle(.segmented)
                } header: { Text("Photos and editing") } footer: {
                    Text(premium ? "Photoshop and paid tools: skin retouching, compositing and advanced grading. 3 JD a photo as a base price; complex edits may cost more."
                                 : "Basic retouching, color grading, crop and export. 3 JD a photo.")
                }
                Section("Notes (optional)") { TextEditor(text: $notes).frame(minHeight: 70) }
                Section {
                    TextField("Promo code", text: $promo).autocapitalization(.allCharacters).disableAutocorrection(true)
                    if let p = activePromo {
                        Text(String(format: L("%d%% off applied"), Int(p.percent))).font(.caption).foregroundStyle(.green)
                    } else if !promo.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text("This promo code isn't valid.").font(.caption).foregroundStyle(.red)
                    }
                }
                Section {
                    line(String(format: L("Session (%@ h × 5 JD)"), String(format: "%.1f", hours)), jd(hourFee))
                    line(String(format: L("Editing (%d × 3 JD)"), edits), jd(editFee))
                    line(L("Travel"), outside ? jd(travelFee) : L("Free"))
                    if activePromo != nil { line(L("Promo discount"), "−" + jd(promoAmount)) }
                    HStack {
                        Text("Estimated total").font(.headline)
                        Spacer()
                        Text(jd(total)).font(.title3.weight(.bold)).foregroundStyle(Color.abAccent)
                    }
                } header: { Text("Your estimate") } footer: {
                    Text("This is an estimate; the photographer confirms the final amount. You can pay by PayPal, CliQ or cash.")
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
                Section {
                    Button { Task { await submit() } } label: {
                        HStack { Spacer(); Text(busy ? "Sending…" : "Send booking request").fontWeight(.semibold); Spacer() }
                    }
                    .disabled(!valid || busy)
                }
            }
        }
        .navigationTitle("Book a photoshoot")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if first.isEmpty, let u = account.user {
                let parts = u.displayName.split(separator: " ").map(String.init)
                first = parts.first ?? ""
                if parts.count > 1 { last = parts.last ?? "" }
                email = u.email ?? ""
                phone = u.phone ?? ""
            }
        }
    }

    private func line(_ a: String, _ b: String) -> some View {
        HStack { Text(a); Spacer(); Text(b).foregroundStyle(.secondary).monospacedDigit() }
    }

    private func reset() {
        withAnimation { sent = false }
        notes = ""; promo = ""; edits = 0; address = ""
    }

    private func submit() async {
        busy = true
        defer { busy = false }
        let name = [first, middle, last].map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.joined(separator: " ")
        let place = address.trimmingCharacters(in: .whitespaces)
        let pricing: [String: Any] = ["hourlyFee": hourFee, "editFee": editFee, "travelFee": travelFee, "subtotal": subtotal,
                                      "promoCode": (activePromo?.code as Any?) ?? NSNull(), "promoPercentOff": activePromo?.percent ?? 0,
                                      "promoAmount": promoAmount, "estimatedTotal": total]
        let body: [String: Any] = [
            "fullName": name, "firstName": first.trimmingCharacters(in: .whitespaces), "middleName": middle.trimmingCharacters(in: .whitespaces),
            "lastName": last.trimmingCharacters(in: .whitespaces), "phone": phone.trimmingCharacters(in: .whitespaces),
            "email": email.trimmingCharacters(in: .whitespaces), "shootDate": Self.day(date), "timeFrom": Self.clock(from), "timeTo": Self.clock(to),
            "durationHours": String(format: "%.2f", hours), "sessionType": session,
            "location": place.isEmpty ? (outside ? "Not specified" : "ASU Campus") : place,
            "locationZone": outside ? "outside" : "asu", "editPhotos": edits,
            "editType": premium ? "Premium (Photoshop + paid tools)" : "Standard", "notes": notes, "pricing": pricing,
            "submittedAt": ISO8601DateFormatter().string(from: Date())
        ]
        do {
            var req = URLRequest(url: Self.endpoint, timeoutInterval: 40)
            req.httpMethod = "POST"
            req.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (_, response) = try await URLSession.shared.data(for: req)
            guard ((response as? HTTPURLResponse)?.statusCode ?? 500) < 400 else { throw URLError(.badServerResponse) }
            withAnimation { sent = true }
            error = nil
            Haptics.success()
        } catch {
            self.error = L("Couldn't send your booking. Check your connection and try again.")
            Haptics.error()
        }
    }
}

// MARK: - weather

struct WeatherHour: Identifiable {
    var id: Int { hour }
    let hour: Int
    let temp: Int
    let rain: Int
    let code: Int

    var symbol: String { WeatherText.symbol(code) }
}

enum WeatherText {
    /// Multicolor symbols draw clouds white, which vanish on a light card
    static func colors(_ c: Int) -> (Color, Color) {
        switch c {
        case 0: return (.orange, .orange)
        case 1, 2: return (.gray, .orange)
        case 51...57, 61...67, 80...82: return (.gray, .blue)
        case 71...77, 85, 86: return (.gray, .cyan)
        case 95...99: return (.gray, .yellow)
        default: return (.gray, .gray)
        }
    }

    static func symbol(_ c: Int) -> String {
        switch c {
        case 0: return "sun.max.fill"
        case 1, 2: return "cloud.sun.fill"
        case 3: return "cloud.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51...57: return "cloud.drizzle.fill"
        case 61...67, 80...82: return "cloud.rain.fill"
        case 71...77, 85, 86: return "cloud.snow.fill"
        case 95...99: return "cloud.bolt.rain.fill"
        default: return "cloud.fill"
        }
    }

    static func label(_ c: Int) -> String {
        switch c {
        case 0: return "Clear"
        case 1, 2: return "Partly cloudy"
        case 3: return "Cloudy"
        case 45, 48: return "Foggy"
        case 51...57: return "Drizzle"
        case 61...67, 80...82: return "Rain"
        case 71...77, 85, 86: return "Snow"
        case 95...99: return "Thunderstorms"
        default: return "Cloudy"
        }
    }

    static func hourText(_ h: Int) -> String {
        guard let d = Calendar.current.date(bySettingHour: h, minute: 0, second: 0, of: Date()) else { return "\(h):00" }
        let f = DateFormatter()
        f.dateFormat = "h a"
        f.locale = Locale(identifier: AppLanguage.code)
        return f.string(from: d)
    }

    static func advice(hours: [WeatherHour]) -> String? {
        let rain = hours.map(\.rain).max() ?? 0
        let hot = hours.map(\.temp).max() ?? 0
        let cold = hours.map(\.temp).min() ?? 20
        if rain >= 50 { return "Take an umbrella, rain is likely." }
        if hot >= 32 { return "It's hot. Drink water and stay in the shade." }
        if cold <= 8 { return "It's cold. Take a jacket." }
        return nil
    }
}

@MainActor
final class WeatherStore: ObservableObject {
    @Published var hours: [WeatherHour] = []
    @Published var tomorrow = false
    /// The day shown, like "Wednesday, 4 November 2026"
    @Published var dateText = ""
    @Published var failed = false
    private var loaded: Date?

    /// Campus location only; nothing about the student is sent
    private let url = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=32.04&longitude=35.90&hourly=temperature_2m,precipitation_probability,weather_code&timezone=Asia%2FAmman&forecast_days=2")!

    private struct Reply: Decodable {
        struct Hourly: Decodable {
            let time: [String]
            let temperature_2m: [Double?]
            let precipitation_probability: [Int?]
            let weather_code: [Int?]
        }
        let hourly: Hourly
    }

    func load() async {
        if let loaded, Date().timeIntervalSince(loaded) < 1800, !hours.isEmpty { return }
        let key = "ab.weather.cache"
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard apply(data) else { throw URLError(.cannotParseResponse) }
            UserDefaults.standard.set(data, forKey: key)
            loaded = Date()
            failed = false
        } catch {
            if hours.isEmpty, let d = UserDefaults.standard.data(forKey: key), apply(d) { return }
            failed = hours.isEmpty
        }
    }

    private func apply(_ data: Data) -> Bool {
        guard let r = try? JSONDecoder().decode(Reply.self, from: data) else { return false }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Amman") ?? .current
        let now = Date()
        let late = cal.component(.hour, from: now) >= 16
        let day = late ? (cal.date(byAdding: .day, value: 1, to: now) ?? now) : now
        let f = DateFormatter()
        f.calendar = cal
        f.timeZone = cal.timeZone
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        let prefix = f.string(from: day)
        var out: [WeatherHour] = []
        for (i, t) in r.hourly.time.enumerated() where t.hasPrefix(prefix) {
            guard let h = Int(t.dropFirst(11).prefix(2)), (7...16).contains(h), i < r.hourly.temperature_2m.count else { continue }
            out.append(WeatherHour(hour: h, temp: Int((r.hourly.temperature_2m[i] ?? 0).rounded()),
                                   rain: i < r.hourly.precipitation_probability.count ? r.hourly.precipitation_probability[i] ?? 0 : 0,
                                   code: i < r.hourly.weather_code.count ? r.hourly.weather_code[i] ?? 3 : 3))
        }
        guard !out.isEmpty else { return false }
        hours = out
        tomorrow = late
        let shown = DateFormatter()
        shown.calendar = cal
        shown.timeZone = cal.timeZone
        shown.locale = Locale(identifier: AppLanguage.code)
        shown.setLocalizedDateFormatFromTemplate("EEEEdMMMMyyyy")
        dateText = shown.string(from: day)
        return true
    }
}

/// Home: the weather on campus from 7:00 AM till 4:00 PM
struct WeatherCard: View {
    @StateObject private var store = WeatherStore()

    var body: some View {
        // Not a Group: .task on an empty Group never runs, so nothing would ever load.
        VStack(spacing: 0) {
            Color.clear.frame(height: 0).task { await store.load() }
            if !store.hours.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 3) {
                            Label(store.tomorrow ? L("Tomorrow on campus") : L("Today on campus"), systemImage: "thermometer.sun.fill")
                                .font(.subheadline.weight(.semibold))
                            Text(store.dateText).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 3) {
                            Text("7 AM – 4 PM").font(.caption).foregroundStyle(.secondary)
                            Text(String(format: L("High %d° · Low %d°"), store.hours.map(\.temp).max() ?? 0, store.hours.map(\.temp).min() ?? 0))
                                .font(.caption.weight(.semibold)).monospacedDigit()
                        }
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(store.hours) { h in
                                VStack(spacing: 5) {
                                    Text(WeatherText.hourText(h.hour)).font(.caption2).foregroundStyle(.secondary)
                                    Image(systemName: h.symbol).symbolRenderingMode(.palette)
                                        .foregroundStyle(WeatherText.colors(h.code).0, WeatherText.colors(h.code).1)
                                        .font(.title3).frame(height: 26)
                                        .accessibilityLabel(L(WeatherText.label(h.code)))
                                    Text("\(h.temp)°").font(.subheadline.weight(.semibold)).monospacedDigit()
                                    Text("\(h.rain)%").font(.caption2).foregroundStyle(h.rain >= 40 ? Color.blue : Color.secondary)
                                }
                                .frame(minWidth: 38)
                            }
                        }
                    }
                    if let a = WeatherText.advice(hours: store.hours) {
                        Text(L(a)).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(14)
                .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding(.horizontal, 16)
                .padding(.top, 14)
            }
        }
    }
}

// MARK: - sponsor

/// A paid card from app-config.json, always labelled as sponsored. Opens outside the app.
struct SponsorCard: View {
    @EnvironmentObject private var config: AppConfig
    @Environment(\.openURL) private var openURL

    var body: some View {
        if let s = config.sponsor, s.isLive {
            Button { openURL(s.url) } label: {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Sponsored").font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                        Text(verbatim: s.title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                        if let t = s.text, !t.isEmpty {
                            Text(verbatim: t).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    Image(systemName: "arrow.up.right").font(.footnote).foregroundStyle(.tertiary)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(PressStyle())
            .padding(.horizontal, 16)
            .padding(.top, 14)
        }
    }
}

// MARK: - life hacks

struct HackSection: Decodable, Identifiable {
    struct Item: Decodable, Identifiable {
        var id: String { t }
        let t: String
        let d: String
    }
    var id: String { title }
    let emoji: String
    let title: String
    let items: [Item]
}

struct LifeHacksTab: View {
    var body: some View { ABStack { LifeHacksScreen() } }
}

/// Emergency numbers and study, money and health tips. The tips come from assets/app/life-hacks.json.
struct LifeHacksScreen: View {
    @State private var sections: [HackSection] = []
    @State private var loading = true
    private let cacheKey = "ab.hacks.cache"

    var body: some View {
        List {
            Section {
                Button { if let u = URL(string: "tel:911") { UIApplication.shared.open(u) } } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "phone.fill").foregroundStyle(.white).frame(width: 36, height: 36).background(Color.red, in: Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Call 911").font(.body.weight(.semibold)).foregroundStyle(.primary)
                            Text("Jordan's emergency number: ambulance, fire and police").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            } header: { Text("In an emergency") }
            ForEach(sections) { s in
                Section {
                    ForEach(s.items) { i in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(i.t).font(.subheadline.weight(.semibold))
                            Text(i.d).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.vertical, 2)
                    }
                } header: { Text("\(s.emoji) \(s.title)") }
            }
            if loading { ABDots() }
            else if sections.isEmpty { Text("Couldn't load the tips. Check your connection and try again.").foregroundStyle(.secondary) }
            Section {
                Text("These are general tips, not medical or professional advice. In an emergency, call for help first.").font(.caption).foregroundStyle(.secondary)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Life hacks")
        .task { await load() }
        .refreshable { await load() }
    }

    private func apply(_ data: Data) -> Bool {
        guard let all = try? JSONDecoder().decode([String: [HackSection]].self, from: data) else { return false }
        sections = all[AppLanguage.code] ?? all["en"] ?? []
        return !sections.isEmpty
    }

    private func load() async {
        if sections.isEmpty, let d = UserDefaults.standard.data(forKey: cacheKey) { _ = apply(d) }
        if let u = URL(string: "https://asubank.org/assets/app/life-hacks.json"),
           let (data, _) = try? await URLSession.shared.data(from: u), apply(data) {
            UserDefaults.standard.set(data, forKey: cacheKey)
        }
        loading = false
    }
}

// MARK: - notification sounds

enum NotificationSounds {
    static let all: [(id: String, name: String)] = [
        ("default", "Phone default"), ("chime", "Chime"), ("bell", "Bell"), ("ping", "Ping"),
        ("marimba", "Marimba"), ("soft", "Soft"), ("alert", "Alert"), ("drops", "Drops")
    ]
    static let messageKey = "ab.sound.messages"
    static let lectureKey = "ab.sound.lectures"

    static func sound(_ key: String) -> UNNotificationSound {
        let id = UserDefaults.standard.string(forKey: key) ?? "default"
        if id == "default" { return .default }
        return UNNotificationSound(named: UNNotificationSoundName("ab_\(id).wav"))
    }

    static func name(_ id: String) -> String { all.first { $0.id == id }?.name ?? "Phone default" }

    private static var player: AVAudioPlayer?
    static func preview(_ id: String) {
        guard id != "default", let url = Bundle.main.url(forResource: "ab_\(id)", withExtension: "wav") else {
            AudioServicesPlayAlertSound(1007)
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.mixWithOthers])
        player = try? AVAudioPlayer(contentsOf: url)
        player?.play()
    }
}

struct SoundsScreen: View {
    @AppStorage(NotificationSounds.messageKey) private var messages = "default"
    @AppStorage(NotificationSounds.lectureKey) private var lectures = "default"

    var body: some View {
        List {
            picker("Notifications", $messages)
            picker("Lecture reminders", $lectures)
            Section {
                Text("iPhones don't let apps use your own ringtones, so these sounds come with UniHub.").font(.caption).foregroundStyle(.secondary)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Notification sounds")
        .onChange(of: lectures) { _ in LectureStore.shared.reschedule() }
    }

    private func picker(_ title: String, _ value: Binding<String>) -> some View {
        Section(L(title)) {
            ForEach(NotificationSounds.all, id: \.id) { s in
                Button {
                    value.wrappedValue = s.id
                    NotificationSounds.preview(s.id)
                } label: {
                    HStack {
                        Text(L(s.name)).foregroundColor(.primary)
                        Spacer()
                        if value.wrappedValue == s.id { Image(systemName: "checkmark").foregroundStyle(Color.abAccent) }
                    }
                }
            }
        }
    }
}

// MARK: - lectures

struct Lecture: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var subject: String
    var hall: String
    /// 1 Sunday ... 7 Saturday
    var days: [Int]
    var hour: Int
    var minute: Int
    var enabled = true

    var timeText: String {
        guard let d = Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) else { return "\(hour):\(minute)" }
        let f = DateFormatter()
        f.timeStyle = .short
        f.locale = Locale(identifier: AppLanguage.code)
        return f.string(from: d)
    }

    var daysText: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: AppLanguage.code)
        let names = f.shortWeekdaySymbols ?? []
        return days.sorted().compactMap { names.indices.contains($0 - 1) ? names[$0 - 1] : nil }.joined(separator: " · ")
    }
}

/// Lectures stay on this phone. Each one repeats every week and reminds 10 minutes before it starts.
@MainActor
final class LectureStore: ObservableObject {
    static let shared = LectureStore()
    static let before = 10
    @Published var lectures: [Lecture] = []
    private let key = "ab.lectures"

    init() {
        if let d = UserDefaults.standard.data(forKey: key), let l = try? JSONDecoder().decode([Lecture].self, from: d) { lectures = l }
    }

    func save(_ l: Lecture) {
        if let i = lectures.firstIndex(where: { $0.id == l.id }) { lectures[i] = l } else { lectures.append(l) }
        commit()
    }

    func remove(_ id: String) {
        lectures.removeAll { $0.id == id }
        commit()
    }

    func setEnabled(_ id: String, _ on: Bool) {
        guard let i = lectures.firstIndex(where: { $0.id == id }) else { return }
        lectures[i].enabled = on
        commit()
    }

    private func commit() {
        lectures.sort { ($0.hour, $0.minute) < ($1.hour, $1.minute) }
        if let d = try? JSONEncoder().encode(lectures) { UserDefaults.standard.set(d, forKey: key) }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in
            Task { @MainActor in LectureStore.shared.reschedule() }
        }
    }

    /// Replaces all the lecture reminders with the current list (also after changing language or sound)
    func reschedule() {
        let c = UNUserNotificationCenter.current()
        let list = lectures
        c.getPendingNotificationRequests { pending in
            c.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix("lecture-") })
            Task { @MainActor in
                let sound = NotificationSounds.sound(NotificationSounds.lectureKey)
                for l in list where l.enabled {
                    for day in l.days {
                        // 10 minutes earlier, which can be the day before for a lecture just after midnight
                        var total = l.hour * 60 + l.minute - Self.before
                        var weekday = day
                        if total < 0 { total += 24 * 60; weekday = day == 1 ? 7 : day - 1 }
                        var when = DateComponents()
                        when.weekday = weekday
                        when.hour = total / 60
                        when.minute = total % 60
                        let content = UNMutableNotificationContent()
                        content.title = String(format: L("%@ in 10 minutes"), l.subject)
                        content.body = l.hall.isEmpty ? String(format: L("Starts at %@"), l.timeText)
                                                      : String(format: L("Hall %@ · starts at %@"), l.hall, l.timeText)
                        content.sound = sound
                        content.userInfo = ["open": "lectures"]
                        let trigger = UNCalendarNotificationTrigger(dateMatching: when, repeats: true)
                        try? await c.add(UNNotificationRequest(identifier: "lecture-\(l.id)-\(day)", content: content, trigger: trigger))
                    }
                }
            }
        }
    }

    /// The next lecture from now, for the Home screen
    var next: (lecture: Lecture, today: Bool)? {
        let now = Calendar.current.dateComponents([.weekday, .hour, .minute], from: Date())
        let nowMin = (now.hour ?? 0) * 60 + (now.minute ?? 0)
        let day = now.weekday ?? 1
        var best: (Lecture, Int)?
        for l in lectures where l.enabled {
            for d in l.days {
                var diff = ((d - day + 7) % 7) * 24 * 60 + l.hour * 60 + l.minute - nowMin
                if diff < 0 { diff += 7 * 24 * 60 }
                if best == nil || diff < best!.1 { best = (l, diff) }
            }
        }
        guard let best else { return nil }
        return (best.0, best.1 < 24 * 60 && best.1 + nowMin < 24 * 60)
    }
}

struct LecturesTab: View {
    var body: some View { ABStack { LecturesScreen() } }
}

struct LecturesScreen: View {
    @ObservedObject private var store = LectureStore.shared
    @State private var editing: Lecture?
    @State private var denied = false

    var body: some View {
        List {
            if denied {
                Section {
                    Text("Notifications are off for UniHub, so reminders can't ring.").foregroundStyle(.red)
                    Button("Open Settings") { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } }
                }
            }
            if store.lectures.isEmpty {
                Section {
                    VStack(spacing: 8) {
                        Image(systemName: "alarm.fill").font(.system(size: 40)).foregroundStyle(Color.abAccent)
                        Text("Never miss a lecture").font(.headline)
                        Text("Add your subject, hall and time. We'll remind you 10 minutes before it starts.")
                            .multilineTextAlignment(.center).font(.subheadline).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                }
            }
            ForEach(1...7, id: \.self) { d in
                let list = store.lectures.filter { $0.days.contains(d) }
                if !list.isEmpty {
                    Section(Self.dayName(d)) {
                        ForEach(list) { l in row(l) }
                    }
                }
            }
            Section {
                NavigationLink { SoundsScreen() } label: { Label("Notification sounds", systemImage: "speaker.wave.2") }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("My lectures")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editing = Lecture(subject: "", hall: "", days: [], hour: 9, minute: 0) } label: { Image(systemName: "plus") }
                    .accessibilityLabel(L("Add a lecture"))
            }
        }
        .sheet(item: $editing) { LectureEditor(lecture: $0) }
        .task {
            let s = await UNUserNotificationCenter.current().notificationSettings()
            denied = s.authorizationStatus == .denied
        }
    }

    private static func dayName(_ d: Int) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: AppLanguage.code)
        let names = f.standaloneWeekdaySymbols ?? []
        return names.indices.contains(d - 1) ? names[d - 1] : ""
    }

    private func row(_ l: Lecture) -> some View {
        Button { editing = l } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(l.subject).font(.body.weight(.semibold)).foregroundStyle(.primary)
                    Text(l.hall.isEmpty ? l.timeText : "\(l.timeText) · \(L("Hall")) \(l.hall)").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Toggle("", isOn: Binding(get: { l.enabled }, set: { store.setEnabled(l.id, $0) })).labelsHidden()
            }
        }
    }
}

struct LectureEditor: View {
    @State var lecture: Lecture
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = LectureStore.shared
    @State private var time = Date()

    private var valid: Bool { !lecture.subject.trimmingCharacters(in: .whitespaces).isEmpty && !lecture.days.isEmpty }
    private var isNew: Bool { !store.lectures.contains { $0.id == lecture.id } }

    var body: some View {
        ABStack {
            Form {
                Section("Lecture") {
                    TextField("Subject", text: $lecture.subject)
                    TextField("Hall", text: $lecture.hall)
                    DatePicker("Starts at", selection: $time, displayedComponents: .hourAndMinute)
                }
                Section {
                    let f = DateFormatter()
                    HStack(spacing: 6) {
                        ForEach(1...7, id: \.self) { d in
                            let on = lecture.days.contains(d)
                            Button {
                                if on { lecture.days.removeAll { $0 == d } } else { lecture.days.append(d) }
                                Haptics.tap()
                            } label: {
                                Text(String((dayLetters(f)[d - 1])))
                                    .font(.caption.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.6)
                                    .frame(maxWidth: .infinity, minHeight: 38)
                                    .foregroundStyle(on ? Color.white : Color.primary)
                                    .background(on ? AnyShapeStyle(Color.abFill) : AnyShapeStyle(Color.primary.opacity(0.08)), in: RoundedRectangle(cornerRadius: 9))
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                } header: { Text("Days") } footer: { Text("We'll remind you 10 minutes before, every week on these days.") }
                if !isNew {
                    Section {
                        Button("Delete this lecture", role: .destructive) {
                            store.remove(lecture.id)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(isNew ? "Add a lecture" : "Edit lecture")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let c = Calendar.current.dateComponents([.hour, .minute], from: time)
                        lecture.hour = c.hour ?? 9
                        lecture.minute = c.minute ?? 0
                        lecture.subject = lecture.subject.trimmingCharacters(in: .whitespaces)
                        lecture.hall = lecture.hall.trimmingCharacters(in: .whitespaces)
                        store.save(lecture)
                        Haptics.success()
                        dismiss()
                    }
                    .disabled(!valid)
                }
            }
            .onAppear {
                time = Calendar.current.date(bySettingHour: lecture.hour, minute: lecture.minute, second: 0, of: Date()) ?? Date()
            }
        }
    }

    private func dayLetters(_ f: DateFormatter) -> [String] {
        f.locale = Locale(identifier: AppLanguage.code)
        return f.shortStandaloneWeekdaySymbols ?? ["S", "M", "T", "W", "T", "F", "S"]
    }
}

/// Home: what's next, when there's a lecture coming up today
struct NextLectureCard: View {
    @ObservedObject private var store = LectureStore.shared
    @EnvironmentObject private var app: AppState

    var body: some View {
        if let n = store.next, n.today {
            Button { app.tab = .lectures; Haptics.tap() } label: {
                HStack(spacing: 12) {
                    Image(systemName: "alarm.fill").foregroundStyle(.white).frame(width: 36, height: 36).background(Color.abFill, in: RoundedRectangle(cornerRadius: 10))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Next lecture").font(.caption).foregroundStyle(.secondary)
                        Text(n.lecture.subject).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(1)
                        Text(n.lecture.hall.isEmpty ? n.lecture.timeText : "\(n.lecture.timeText) · \(L("Hall")) \(n.lecture.hall)")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                }
                .padding(14)
                .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16)
            .padding(.top, 12)
        }
    }
}

// MARK: - tab bar

/// Which tabs sit between Home and More. Apps only.
enum TabBarPrefs {
    static let key = "ab.tabs"
    static let options: [(tab: AppTab, id: String, name: String, icon: String)] = [
        (.feed, "feed", "Feed", "newspaper.fill"),
        (.search, "search", "Search", "magnifyingglass"),
        (.chat, "chat", "Chat", "bubble.left.and.bubble.right.fill"),
        (.notifications, "notifications", "Notifications", "bell.fill"),
        (.friends, "friends", "Friends", "person.2.fill"),
        (.saved, "saved", "Saved", "star.fill"),
        (.lectures, "lectures", "My lectures", "alarm.fill"),
        (.hacks, "hacks", "Life hacks", "lightbulb.fill"),
        (.swap, "swap", "Subject swap", "arrow.left.arrow.right.circle.fill"),
        (.rating, "rating", "Rate your teachers", "star.bubble.fill"),
        (.matcher, "matcher", "Course matcher", "person.3.fill"),
        (.planner, "planner", "Exams & tasks", "checklist"),
        (.focus, "focus", "Focus timer", "timer")
    ]
    /// Home, Feed, Chat, Search, More. People who already picked their tabs keep them.
    static let standard = "feed,chat,search"

    static func chosen(_ stored: String, admin: Bool) -> [AppTab] {
        let ids = stored.split(separator: ",").map(String.init)
        let tabs = ids.compactMap { id in options.first { $0.id == id }?.tab }
        return Array((tabs.isEmpty ? [.search] : tabs).prefix(3))
    }
}

struct TabBarScreen: View {
    @AppStorage(TabBarPrefs.key) private var stored = TabBarPrefs.standard
    private let limit = 3
    private var ids: [String] { (stored).split(separator: ",").map(String.init).filter { id in TabBarPrefs.options.contains { $0.id == id } } }

    var body: some View {
        List {
            Section {
                row("house.fill", "Home", locked: true)
                ForEach(ids, id: \.self) { id in
                    if let o = TabBarPrefs.options.first(where: { $0.id == id }) {
                        HStack {
                            Button { set(ids.filter { $0 != id }) } label: {
                                Image(systemName: "minus.circle.fill").foregroundStyle(ids.count > 1 ? Color.red : Color.secondary)
                            }
                            .buttonStyle(.borderless)
                            .disabled(ids.count <= 1)
                            Image(systemName: o.icon).foregroundStyle(Color.abAccent).frame(width: 28)
                            Text(L(o.name))
                        }
                    }
                }
                .onMove { from, to in
                    var list = ids
                    list.move(fromOffsets: from, toOffset: to)
                    set(list)
                }
                row("ellipsis.circle.fill", "More", locked: true)
            } header: { Text("In your tab bar") } footer: {
                Text(String(format: L("Drag to reorder. Home and More always stay, and you can add up to %d more."), limit))
            }
            Section("Add a tab") {
                ForEach(TabBarPrefs.options.filter { !ids.contains($0.id) }, id: \.id) { o in
                    Button {
                        if ids.count < limit { set(ids + [o.id]); Haptics.tap() }
                    } label: {
                        HStack {
                            Image(systemName: "plus.circle.fill").foregroundStyle(ids.count < limit ? Color.green : Color.secondary)
                            Image(systemName: o.icon).foregroundStyle(Color.abAccent).frame(width: 28)
                            Text(L(o.name)).foregroundColor(.primary)
                        }
                    }
                    .disabled(ids.count >= limit)
                }
            }
            Section {
                Button("Reset to the default") { stored = TabBarPrefs.standard }
            }
        }
        .listStyle(.insetGrouped)
        .environment(\.editMode, .constant(.active))
        .navigationTitle("Customize tab bar")
    }

    private func set(_ list: [String]) { stored = list.joined(separator: ",") }

    private func row(_ icon: String, _ name: String, locked: Bool) -> some View {
        HStack {
            Image(systemName: "lock.fill").font(.caption).foregroundStyle(.tertiary).frame(width: 22)
            Image(systemName: icon).foregroundStyle(.secondary).frame(width: 28)
            Text(L(name)).foregroundStyle(.secondary)
        }
        .moveDisabled(true)
    }
}
