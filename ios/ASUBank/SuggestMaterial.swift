import SwiftUI

/// Suggest materials, natively. It saves to the same place as the form on asubank.org/upload/suggest_to_whatsapp,
/// and admins read both in Admin > Suggestions.
struct SuggestMaterialScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var note = ""
    @State private var contact = ""
    @State private var busy = false
    @State private var error: String?
    @State private var sent = false

    private let share = ["Past exams or quizzes (with solutions if possible)", "Study guides or cheat sheets", "Practice problems with answers", "Professor-specific materials"]
    private let rules = ["Only share materials you're permitted to distribute", "Clearly label documents with course information",
                         "Don't share current semester exams that might still be in use", "No spam or unrelated content", "Respect copyright and academic integrity policies"]

    private var text: String { note.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        Form {
            if sent {
                Section14 {
                    VStack(spacing: 10) {
                        Image(systemName: "checkmark.circle.fill").font(.system(size: 44)).fg14(.green)
                        Text("Thank you!").font(.headline)
                        Text("We got your suggestion. The UniHub team will look at it soon.")
                            .font(.subheadline).fg14(.secondary).multilineTextAlignment(.center)
                        Button("Send another") { sent = false }.buttonStyle(BorderlessButtonStyle()).padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                }
            } else {
                Section14 {
                    ZStack(alignment: .topLeading) {
                        if note.isEmpty {
                            Text("Example: [PHY 202] Dr. Smith, Fall 2023 midterm with solutions.")
                                .fg14(.secondary).padding(.top, 8).padding(.leading, 4).allowsHitTesting(false)
                        }
                        TextEditor(text: $note).frame(minHeight: 120)
                    }
                } header: { Text("Describe what you're suggesting") } footer: {
                    Text("Include the course code, professor name and semester if you know them. At least 20 characters.")
                }
                Section14 {
                    TextField("Email, WhatsApp or ASU ID", text: $contact)
                        .autocapitalization(.none).disableAutocorrection(true)
                } header: { Text("How can we contact you? (optional)") } footer: {
                    if let u = account.user { Text("You're sending this as @\(u.username).") }
                }
                if let error { Section14 { Text(error).fg14(.red) } }
                Section14 {
                    Button { Task { await send() } } label: {
                        HStack {
                            Spacer()
                            if busy { ABDots() } else { Text("Send suggestion").fontWeight(.semibold) }
                            Spacer()
                        }
                    }
                    .disabled(busy || text.count < 20)
                }
                Section14("What to share") {
                    ForEach(share, id: \.self) { Label(LocalizedStringKey($0), systemImage: "checkmark") }
                }
                Section14("Guidelines") {
                    ForEach(rules, id: \.self) { Label(LocalizedStringKey($0), systemImage: "exclamationmark.circle") }
                }
                Section14 {
                    RouteLink(Route.page("/requests/request", "Request a material")) {
                        Label("Looking for something specific? Request it", systemImage: "doc.text.magnifyingglass")
                    }
                }
            }
        }
        .navigationTitle("Suggest materials")
    }

    private func send() async {
        busy = true
        do {
            _ = try await account.call("suggest", ["note": text, "contact": contact.trimmingCharacters(in: .whitespaces)])
            note = ""
            contact = ""
            error = nil
            sent = true
            Haptics.success()
        } catch {
            self.error = error.localizedDescription
            Haptics.error()
        }
        busy = false
    }
}

/// Admins: what students suggested, from the apps and the website
struct AdminSuggestionsScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @State private var list: [[String: Any]] = []
    @State private var showAll = false
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            Picker("", selection: $showAll) {
                Text("Open").tag(false)
                Text("All").tag(true)
            }
            .pickerStyle(SegmentedPickerStyle())
            if let error { Text(error).fg14(.red) }
            if loading { HStack { Spacer(); ABDots(); Spacer() } }
            else if list.isEmpty && error == nil { Text("Nothing to review.").fg14(.secondary) }
            ForEach(Array(list.enumerated()), id: \.offset) { _, r in row(r) }
        }
        .navigationTitle("Suggestions")
        .task14(id: showAll) { await load() }
        .refreshable14 { await load() }
    }

    private func row(_ r: [String: Any]) -> some View {
        let status = r["status"] as? String ?? "open"
        let who = [r["username"] as? String, r["contact"] as? String, r["created"] as? String].compactMap { $0 }.filter { !$0.isEmpty }
        return VStack(alignment: .leading, spacing: 5) {
            Text(r["note"] as? String ?? "").font(.body)
            Text(who.joined(separator: " · ")).font(.caption2).fg14(Color.tertiary14)
            HStack(spacing: 14) {
                if status == "open" {
                    Button("Done") { Task { await set(r, "done") } }
                    Button("Dismiss") { Task { await set(r, "dismissed") } }
                } else {
                    Text(L(status == "done" ? "Handled" : "Dismissed")).font(.caption).fg14(.secondary)
                }
            }
            .buttonStyle(BorderlessButtonStyle()).font(.subheadline)
        }
        .padding(.vertical, 2)
    }

    private func set(_ r: [String: Any], _ status: String) async {
        do {
            _ = try await account.call("adminSuggestionSet", ["id": r["id"] as? String ?? "", "status": status])
            await load()
        } catch { self.error = error.localizedDescription }
    }

    private func load() async {
        let tag = showAll ? "all" : "open"
        if list.isEmpty, let r = account.saved("adminSuggestions", tag: tag) {
            list = (r["suggestions"] as? [[String: Any]]) ?? []
            loading = false
        }
        do {
            let r = try await account.callSaving("adminSuggestions", ["all": showAll], tag: tag)
            list = (r["suggestions"] as? [[String: Any]]) ?? []
            error = nil
        } catch { self.error = error.localizedDescription }
        loading = false
    }
}
