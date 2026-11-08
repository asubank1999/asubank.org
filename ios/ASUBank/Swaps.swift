import SwiftUI

// MARK: - subject swap

/// One listing. Contact details never come with it: they arrive only through swapContact.
struct SwapListing: Identifiable, Equatable {
    let id: String
    let have: String
    let want: String
    let section: String
    let note: String
    let name: String
    let major: String
    var status: String
    let created: String
    let mine: Bool

    init(_ d: [String: Any]) {
        id = d["id"] as? String ?? ""
        have = d["have"] as? String ?? ""
        want = d["want"] as? String ?? ""
        section = d["section"] as? String ?? ""
        note = d["note"] as? String ?? ""
        name = d["name"] as? String ?? ""
        major = d["major"] as? String ?? ""
        status = d["status"] as? String ?? "open"
        created = d["created"] as? String ?? ""
        mine = d["mine"] as? Bool ?? false
    }

    /// True when this student has what I want and wants what I have
    func matches(_ other: SwapListing) -> Bool {
        have.caseInsensitiveCompare(other.want) == .orderedSame && want.caseInsensitiveCompare(other.have) == .orderedSame
    }
}

struct SwapContact {
    let fullName: String
    let studentId: String
    let major: String
    let phone: String
    let email: String
}

struct SwapsScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var swaps: [SwapListing] = []
    @State private var query = ""
    @State private var loading = true
    @State private var error: String?
    @State private var adding = false
    @State private var opened: SwapListing?

    private var mine: [SwapListing] { swaps.filter(\.mine) }
    private var others: [SwapListing] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        let list = swaps.filter { !$0.mine && (q.isEmpty || "\($0.have) \($0.want) \($0.major)".lowercased().contains(q)) }
        let open = mine.filter { $0.status == "open" }
        return list.filter { s in open.contains { $0.matches(s) } } + list.filter { s in !open.contains { $0.matches(s) } }
    }
    private func isMatch(_ s: SwapListing) -> Bool { mine.contains { $0.status == "open" && $0.matches(s) } }

    var body: some View {
        List {
            Section14 {
                Text("Trade a registered subject with another student. Say what you have and what you want.")
                    .font(.subheadline).fg14(.secondary)
                Button {
                    if account.signedIn { adding = true } else { account.signInShown = true }
                    Haptics.tap()
                } label: { Label("Post a swap", systemImage: "plus.circle.fill") }
            }
            if let error { Text(error).fg14(.red) }
            if loading && swaps.isEmpty { ForEach(0..<4, id: \.self) { _ in SkeletonRow() } }
            if !mine.isEmpty {
                Section14("Your swaps") { ForEach(mine) { row($0) } }
            }
            Section14("Students offering") {
                if !loading && others.isEmpty {
                    Text("No swaps here yet.").fg14(.secondary)
                }
                ForEach(others) { row($0) }
            }
        }
        .listStyle(InsetGroupedListStyle())
        .searchable14(text: $query, prompt: Text("Search subjects"))
        .navigationTitle(Text("Subject swap"))
        .refreshable14 { await load() }
        .task14 { await load() }
        .sheet(isPresented: $adding) {
            AddSwapSheet { new in swaps.insert(new, at: 0) }
        }
        .sheet(item: $opened) { s in
            SwapDetailSheet(swap: s) { status in
                if status == "deleted" { swaps.removeAll { $0.id == s.id } }
                else if let i = swaps.firstIndex(where: { $0.id == s.id }) { swaps[i].status = status }
            }
        }
    }

    private func row(_ s: SwapListing) -> some View {
        Button { opened = s; Haptics.tap() } label: {
            VStack(alignment: .leading, spacing: 5) {
                if isMatch(s) {
                    Label("Matches your swap", systemImage: "checkmark.seal.fill").font(.caption.weight(.semibold)).fg14(.green)
                }
                if s.status == "done" { Text("Done").font(.caption.weight(.semibold)).fg14(.secondary) }
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(s.have).font(.body.weight(.semibold))
                    Image(systemName: "arrow.left.arrow.right").font(.caption).fg14(.secondary)
                    Text(s.want).font(.body.weight(.semibold))
                }
                .fg14(.primary)
                Text([s.name, s.major, s.section].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.caption).fg14(.secondary)
            }
        }
    }

    private func load() async {
        do {
            let r = try await account.call("swapList")
            swaps = (r["swaps"] as? [[String: Any]] ?? []).map(SwapListing.init)
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }
}

private struct AddSwapSheet: View {
    @Environment(\.presentationMode) private var presentationMode14
    private func dismiss() { presentationMode14.wrappedValue.dismiss() }
    @ObservedObject private var account = AccountStore.shared
    var added: (SwapListing) -> Void
    @State private var have = ""
    @State private var want = ""
    @State private var section = ""
    @State private var note = ""
    @State private var fullName = ""
    @State private var studentId = ""
    @State private var major = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var busy = false
    @State private var error: String?

    private func t(_ s: String) -> String { s.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var valid: Bool {
        !t(have).isEmpty && !t(want).isEmpty && !t(fullName).isEmpty && !t(studentId).isEmpty && !(t(phone).isEmpty && t(email).isEmpty)
    }

    var body: some View {
        NavigationView {
            Form {
                Section14("The swap") {
                    TextField("Subject you have", text: $have)
                    TextField("Subject you want", text: $want)
                    TextField("Section or time (optional)", text: $section)
                    TextField("Note (optional)", text: $note)
                }
                Section14 {
                    TextField("Full name", text: $fullName).textContentType(.name)
                    TextField("University ID", text: $studentId).keyboardType(.numberPad)
                    TextField("Major (optional)", text: $major)
                    TextField("Phone number", text: $phone).keyboardType(.phonePad).textContentType(.telephoneNumber)
                    TextField("University or personal email", text: $email)
                        .keyboardType(.emailAddress).textContentType(.emailAddress).autocapitalization(.none).disableAutocorrection(true)
                } header: { Text("Your details") } footer: {
                    Text("Signed-in students can see these when they open your swap. Add a phone number or an email.")
                }
                if let error { Text(error).fg14(.red) }
            }
            .navigationTitle(Text("Post a swap"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if busy { ABDots() } else { Button("Post") { Task { await send() } }.disabled(!valid) }
                }
            }
        }
    }

    private func send() async {
        busy = true
        do {
            let r = try await account.call("swapAdd", ["have": t(have), "want": t(want), "section": t(section), "note": t(note),
                                                       "fullName": t(fullName), "studentId": t(studentId), "major": t(major), "phone": t(phone), "email": t(email)])
            if let d = r["swap"] as? [String: Any] { added(SwapListing(d)) }
            Haptics.tap()
            dismiss()
        } catch { self.error = error.localizedDescription }
        busy = false
    }
}

private struct SwapDetailSheet: View {
    @Environment(\.presentationMode) private var presentationMode14
    private func dismiss() { presentationMode14.wrappedValue.dismiss() }
    @Environment(\.openURL) private var openURL
    @ObservedObject private var account = AccountStore.shared
    let swap: SwapListing
    var changed: (String) -> Void
    @State private var contact: SwapContact?
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        NavigationView {
            List {
                Section14 {
                    LabeledRow(title: "Has", value: swap.have)
                    LabeledRow(title: "Wants", value: swap.want)
                    if !swap.section.isEmpty { LabeledRow(title: "Section or time", value: swap.section) }
                    if !swap.note.isEmpty { Text(swap.note) }
                }
                if !account.signedIn {
                    Section14 {
                        Text("Sign in to see this student's contact details.").fg14(.secondary)
                        Button("Sign in or create account") { dismiss(); account.signInShown = true }
                    }
                } else if let c = contact {
                    Section14("Contact") {
                        LabeledRow(title: "Name", value: c.fullName)
                        if !c.studentId.isEmpty { LabeledRow(title: "University ID", value: c.studentId) }
                        if !c.major.isEmpty { LabeledRow(title: "Major", value: c.major) }
                        if !c.phone.isEmpty, let u = URL(string: "tel:" + c.phone.filter { $0.isNumber || $0 == "+" }) {
                            Button { openURL(u) } label: { Label(c.phone, systemImage: "phone.fill") }
                        }
                        if !c.email.isEmpty, let u = URL(string: "mailto:" + c.email) {
                            Button { openURL(u) } label: { Label(c.email, systemImage: "envelope.fill") }
                        }
                    }
                } else if loading {
                    SkeletonRow()
                }
                if let error { Text(error).fg14(.red) }
                if swap.mine || account.user?.role == "admin" {
                    Section14 {
                        if swap.status == "open" {
                            Button { set("done") } label: { Label("Mark as done", systemImage: "checkmark.circle") }
                        } else {
                            Button { set("open") } label: { Label("Open again", systemImage: "arrow.uturn.backward") }
                        }
                        Button() { set("deleted") } label: { Label("Delete", systemImage: "trash") }
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle(Text("Subject swap"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
            .task14 { await loadContact() }
        }
    }

    private func loadContact() async {
        guard account.signedIn else { return }
        loading = true
        do {
            let r = try await account.call("swapContact", ["id": swap.id])
            if let d = r["contact"] as? [String: Any] {
                contact = SwapContact(fullName: d["fullName"] as? String ?? "", studentId: d["studentId"] as? String ?? "",
                                      major: d["major"] as? String ?? "", phone: d["phone"] as? String ?? "", email: d["email"] as? String ?? "")
            }
        } catch { self.error = error.localizedDescription }
        loading = false
    }

    /// Shows at once and saves behind the scenes
    private func set(_ status: String) {
        changed(status)
        Haptics.tap()
        dismiss()
        Task { _ = try? await account.call("swapSet", ["id": swap.id, "status": status]) }
    }
}

private struct LabeledRow: View {
    let title: LocalizedStringKey
    let value: String
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).fg14(.secondary)
            Spacer(minLength: 12)
            Text(value).multilineTextAlignment(.trailing)
        }
    }
}
