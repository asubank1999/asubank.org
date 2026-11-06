import SwiftUI

/// Course matcher (classmate matching), natively. Saves the profile through the matcher Apps Script
/// (saveStudent); students with at least 50% in common get each other's contact by email.
@MainActor
enum Matcher {
    static let script = "https://script.google.com/macros/s/AKfycbwwb0HGoZc6O5IqVfAVZJx6rHJ3MGg98kbwGc4EiLwCCY1Tc3E9Zo8ZHX4B1YI7kuR-/exec"
    static let bridge = ScriptBridge(url: script, marker: "studyForm")

    static let years = ["Freshman", "Sophomore", "Junior", "Senior", "Graduate"]
    static let yearNames = ["Freshman": "1st year", "Sophomore": "2nd year", "Junior": "3rd year", "Senior": "4th year+", "Graduate": "Graduate"]
    static let locations = ["Library", "Campus Cafe", "Study Hall", "Online (Zoom/Teams)", "Dorm Common Area", "Off-Campus", "Flexible"]
    static let contacts = ["WhatsApp", "Email", "Phone", "Discord", "Instagram", "Any"]
    static let days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
    static let times = ["Morning", "Afternoon", "Evening"]
}

struct CourseMatcherScreen: View {
    @ObservedObject private var bridge = Matcher.bridge
    @AppStorage("ab.matcher.intro.v1") private var introSeen = false

    var body: some View {
        Group {
            if introSeen { MatcherForm(bridge: bridge) } else { MatcherIntro { withAnimation { introSeen = true } } }
        }
        .bridgeHost(bridge)
        .navigationTitle("Course matcher")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct MatcherIntro: View {
    let onContinue: () -> Void
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Image(systemName: "person.2.fill")
                    .font(.system(size: 38, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 84, height: 84)
                    .background(Color(UIColor(hex: 0x0a84ff)), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .padding(.top, 20)
                Text("Find study buddies").font(.largeTitle.weight(.bold)).multilineTextAlignment(.center)
                Text("Get matched with classmates who take the same courses and are free at the same times.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 16) {
                    Point(symbol: "list.bullet.rectangle.fill", title: "1. Fill your profile", text: "Your courses, topics and when you're free. Takes about a minute.")
                    Point(symbol: "arrow.triangle.2.circlepath", title: "2. We match you", text: "When a classmate is at least 50% similar to you, you're matched.")
                    Point(symbol: "envelope.fill", title: "3. Check your email", text: "You both get each other's contact by email. Matching can take a while, so be patient.")
                    Point(symbol: "lock.fill", title: "Private until matched", text: "Your details are only shared with students you match with.")
                }
                .padding(18)
                .background(Color.abSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                Button(action: onContinue) {
                    Text("Continue").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent).tint(.abFill)
                Text("ابحث عن زملاء دراسة يدرسون نفس المواد ومتفرغين بنفس الأوقات")
                    .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
            .padding(20)
        }
        .background(Color.abBackground.ignoresSafeArea())
    }
}

private struct Point: View {
    let symbol: String
    let title: String
    let text: String
    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: symbol).font(.title3).foregroundStyle(Color.abAccent).frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(text).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
}

private struct MatcherForm: View {
    @ObservedObject var bridge: ScriptBridge
    @EnvironmentObject private var library: Library
    @AppStorage("ab.matcher.name") private var name = ""
    @AppStorage("ab.matcher.email") private var email = ""
    @AppStorage("ab.matcher.phone") private var phone = ""
    @AppStorage("ab.matcher.major") private var major = ""
    @State private var gender = ""
    @State private var year = ""
    @State private var courses: [String] = []
    @State private var topics: [String] = []
    @State private var courseText = ""
    @State private var topicText = ""
    @State private var free: Set<String> = []
    @State private var location = "Flexible"
    @State private var contact = "WhatsApp"
    @State private var sending = false
    @State private var result: (title: String, text: String, ok: Bool)?

    private var missing: String? {
        if name.trimmingCharacters(in: .whitespaces).count < 3 { return "Enter your full name." }
        if !email.contains("@") || !email.contains(".") { return "Enter a valid email: your matches are sent there." }
        if phone.filter(\.isNumber).count < 9 { return "Enter your phone number." }
        if gender.isEmpty { return "Choose your gender." }
        if major.trimmingCharacters(in: .whitespaces).isEmpty { return "Enter your major." }
        if year.isEmpty { return "Choose your year." }
        if courses.isEmpty { return "Add at least one course." }
        if topics.isEmpty { return "Add at least one study topic." }
        if free.isEmpty { return "Pick at least one time you're free." }
        return nil
    }

    /// Course suggestions from the subjects on UniHub
    private var suggestions: [String] {
        let q = AB.norm(courseText)
        guard q.count >= 2 else { return [] }
        return Array(library.subjects.map(\.t).filter { AB.norm($0).contains(q) && !courses.contains($0) }.prefix(4))
    }

    var body: some View {
        Form {
            Section {
                TextField("Full name", text: $name).textContentType(.name)
                TextField("Email (your matches are sent here)", text: $email)
                    .keyboardType(.emailAddress).textContentType(.emailAddress).textInputAutocapitalization(.never).disableAutocorrection(true)
                TextField("Phone, e.g. 079 123 4567", text: $phone).keyboardType(.phonePad).textContentType(.telephoneNumber)
                Picker("Gender", selection: $gender) {
                    Text("Choose").tag("")
                    Text("Male").tag("Male")
                    Text("Female").tag("Female")
                }
            } header: { Text("About you") } footer: { Text("Shared only with students you match with.") }

            Section("Your studies") {
                TextField("Major, e.g. Computer Science", text: $major)
                Picker("Year", selection: $year) {
                    Text("Choose").tag("")
                    ForEach(Matcher.years, id: \.self) { Text(Matcher.yearNames[$0] ?? $0).tag($0) }
                }
            }

            Section {
                TagField(placeholder: "Add a course, e.g. Calculus 1", text: $courseText, tags: $courses)
                ForEach(suggestions, id: \.self) { s in
                    Button { courses.append(s); courseText = ""; Haptics.tap() } label: {
                        Label(s, systemImage: "plus.circle").foregroundStyle(Color.abAccent)
                    }
                }
            } header: { Text("Courses") } footer: { Text("Type a course and tap Add. Add all the courses you want study buddies for.") }

            Section {
                TagField(placeholder: "Add a topic, e.g. Integrals, Midterm review", text: $topicText, tags: $topics)
            } header: { Text("Study topics") }

            Section {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(Matcher.days, id: \.self) { d in
                        HStack(spacing: 6) {
                            Text(String(d.prefix(3))).font(.subheadline.weight(.semibold)).frame(width: 38, alignment: .leading)
                            ForEach(Matcher.times, id: \.self) { t in
                                TimeChip(title: t, on: free.contains("\(d) \(t)")) { toggle("\(d) \(t)") }
                            }
                        }
                    }
                    HStack(spacing: 6) {
                        Text("").frame(width: 38)
                        TimeChip(title: "Weekends", on: free.contains("Weekends")) { toggle("Weekends") }
                    }
                }
                .padding(.vertical, 4)
            } header: { Text("When are you free?") } footer: { Text("\(free.count) selected") }

            Section("Preferences") {
                Picker("Study place", selection: $location) { ForEach(Matcher.locations, id: \.self) { Text($0).tag($0) } }
                Picker("Contact me by", selection: $contact) { ForEach(Matcher.contacts, id: \.self) { Text($0).tag($0) } }
            }

            Section {
                Button(action: submit) {
                    HStack {
                        Spacer()
                        if sending { ABDots().tint(.white) }
                        Text(sending ? "Finding matches…" : "Find my study buddies").font(.headline)
                        Spacer()
                    }
                    .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent).tint(.abFill)
                .disabled(sending)
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            } footer: {
                if let m = missing { Text(m) }
                else if bridge.status == .connecting { Text("Connecting to the matcher…") }
                else if bridge.status == .failed { Text("Couldn't reach the matcher. Check your connection; it will retry when you submit.") }
            }
        }
        .dismissKeyboardOnScroll()
        .alert(result?.title ?? "", isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(result?.text ?? "") }
    }

    private func toggle(_ slot: String) {
        if free.contains(slot) { free.remove(slot) } else { free.insert(slot) }
        UISelectionFeedbackGenerator().selectionChanged()
    }

    private func submit() {
        if let m = missing { result = ("Almost there", m, false); Haptics.error(); return }
        sending = true
        let order = Matcher.days.flatMap { d in Matcher.times.map { "\(d) \($0)" } } + ["Weekends"]
        let data: [String: Any] = [
            "name": name.trimmingCharacters(in: .whitespaces), "email": email.trimmingCharacters(in: .whitespaces),
            "phone": phone.trimmingCharacters(in: .whitespaces), "gender": gender, "major": major.trimmingCharacters(in: .whitespaces),
            "year": year, "courses": courses, "topics": topics, "availability": order.filter { free.contains($0) },
            "location": location, "contactMethod": contact,
        ]
        Task {
            if bridge.status == .failed { bridge.connect(force: true) }
            do {
                let r = try await bridge.call("saveStudent", [data], timeout: 40) as? [String: Any]
                let n = (r?["matchesFound"] as? Int) ?? (r?["matchesFound"] as? NSNumber)?.intValue ?? 0
                result = n > 0
                    ? ("You have \(n) match\(n == 1 ? "" : "es")!", "Check your email (\(email)) for their contact details.", true)
                    : ("Profile saved", "We'll email you as soon as a classmate with similar courses and times signs up.", true)
                courses = []; topics = []; free = []
                Haptics.success()
            } catch {
                result = ("Couldn't save", ScriptBridge.message(error), false)
                Haptics.error()
            }
            sending = false
        }
    }
}

/// A text field with an Add button; added items show as removable chips
private struct TagField: View {
    let placeholder: String
    @Binding var text: String
    @Binding var tags: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                TextField(placeholder, text: $text).onSubmit(add).submitLabel(.done)
                Button("Add", action: add).disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if !tags.isEmpty {
                FlowChips(items: tags) { t in withAnimation { tags.removeAll { $0 == t } } }
            }
        }
    }

    private func add() {
        let t = text.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty, !tags.contains(t) else { text = ""; return }
        withAnimation { tags.append(t) }
        text = ""
        Haptics.tap()
    }
}

/// Removable chips that wrap onto several lines
struct FlowChips: View {
    let items: [String]
    let remove: (String) -> Void
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(items, id: \.self) { t in
                    Button { remove(t) } label: {
                        HStack(spacing: 4) {
                            Text(t).font(.subheadline)
                            Image(systemName: "xmark").font(.caption2.weight(.bold))
                        }
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .foregroundStyle(Color.abAccent)
                        .background(Color.abAccentSoft, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct TimeChip: View {
    let title: String
    let on: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, minHeight: 32)
                .foregroundStyle(on ? Color.white : Color.primary)
                .background(on ? AnyShapeStyle(Color.abFill) : AnyShapeStyle(Color.secondary.opacity(0.12)), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
