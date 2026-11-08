import SwiftUI
import UniformTypeIdentifiers

/// Request a material (custom summaries, Q&A, test banks... from your PDF), natively. Sends the
/// request through the request Apps Script (handleFormSubmission).
@MainActor
enum Requests {
    static let script = "https://script.google.com/macros/s/AKfycbz_bpogYUiNV8qW9KKw1MK0DUZHd0e3LoxfFndzMeOVirchHBYJ-Jy1bMn2XsWq9sVtxQ/exec"
    static let bridge = ScriptBridge(url: script, marker: "requestForm")
    static let community = "https://chat.whatsapp.com/LS92JgjO26Y5SZK9SVJ9FJ"
    static let whatsapp = "mailto:necro144@yahoo.com?body=" + "Hi UniHub, about my material request".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!
    static let maxBytes = 15 * 1024 * 1024

    struct Service: Identifiable { let name: String; let emoji: String; let text: String; var id: String { name } }
    static let services: [Service] = [
        .init(name: "PDF Summary", emoji: "📄", text: "The key points of your PDF"),
        .init(name: "Questions & Answers", emoji: "💬", text: "Full Q&A from your PDF"),
        .init(name: "Questions Without Answers", emoji: "📝", text: "Practice questions"),
        .init(name: "Test Bank", emoji: "📁", text: "Exam-style question bank"),
        .init(name: "Multiple Choice Questions", emoji: "✅", text: "MCQs with or without answers"),
        .init(name: "Teaching Method", emoji: "👨‍🏫", text: "A plan to study it easily"),
        .init(name: "Explanation", emoji: "🧠", text: "The hard parts, made simple"),
        .init(name: "Tutoring", emoji: "🎓", text: "One-on-one help"),
    ]
}

struct RequestMaterialScreen: View {
    @ObservedObject private var bridge = Requests.bridge
    @EnvironmentObject private var app: AppState
    @Environment(\.openURL) private var openURL
    @AppStorage("ab.request.whatsapp") private var whatsapp = ""
    @State private var service = ""
    @State private var file: (name: String, data: Data)?
    @State private var course = ""
    @State private var notes = ""
    @State private var payment = ""
    @State private var picking = false
    @State private var sending = false
    @State private var result: (title: String, text: String, ok: Bool)?
    @State private var copied = false

    private let grid = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    private var missing: String? {
        if service.isEmpty { return "Choose a service." }
        if file == nil { return "Attach the PDF you want it made from." }
        if course.trimmingCharacters(in: .whitespaces).isEmpty { return "Enter the subject or course." }
        let d = whatsapp.filter(\.isNumber).count
        if d < 9 || d > 15 { return "Enter your WhatsApp number so we can send it to you." }
        if payment.isEmpty { return "Choose how you'll pay." }
        return nil
    }

    var body: some View {
        Form {
            Section14 {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 12) {
                        Text("✨").font(.system(size: 30))
                            .frame(width: 52, height: 52)
                            .bgIn14(Color.abAccentSoft, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Made for you from your PDF").font(.headline)
                            Text("3 JD · reviewed by our team").font(.subheadline).fg14(.secondary)
                        }
                    }
                    Text("Send us a PDF and we'll turn it into a summary, questions, a test bank and more. We use AI to draft it, then review and edit it ourselves.")
                        .font(.subheadline).fg14(.secondary)
                    Text("اطلب ملخصاً أو بنك أسئلة مخصصاً لك من ملفك")
                        .font(.footnote).fg14(.secondary)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                .padding(.vertical, 4)
            }

            Section14 {
                Button { app.tab = .search } label: { Label("Search UniHub first, it may already be there", systemImage: "magnifyingglass") }
                Button { openURL(URL(string: Requests.community)!) } label: { Label("Ask the WhatsApp community for free", systemImage: "person.3") }
            } header: { Text("Before you pay") }

            Section14 {
                LazyVGrid(columns: grid, spacing: 10) {
                    ForEach(Requests.services) { s in
                        Button {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { service = s.name }
                            UISelectionFeedbackGenerator().selectionChanged()
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(s.emoji).font(.title2)
                                Text(s.name).font(.subheadline.weight(.semibold)).fg14(.primary).lineLimit(2).multilineTextAlignment(.leading)
                                Text(s.text).font(.caption).fg14(.secondary).lineLimit(2).multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, minHeight: 92, alignment: .topLeading)
                            .padding(10)
                            .bgIn14(service == s.name ? Color.abAccentSoft : Color.abBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(service == s.name ? Color.abAccent : .clear, lineWidth: 2))
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.vertical, 6)
            } header: { Text("1. What do you need?") }

            Section14 {
                Button { picking = true } label: {
                    HStack(spacing: 12) {
                        KindTile(kind: .pdf)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(file?.name ?? "Choose a PDF").fg14(.primary).lineLimit(1)
                            Text(file.map { ByteCountFormatter.string(fromByteCount: Int64($0.data.count), countStyle: .file) } ?? "Up to 15 MB")
                                .font(.caption).fg14(.secondary)
                        }
                        Spacer()
                        Text(file == nil ? "Choose" : "Change").font(.subheadline.weight(.semibold)).fg14(Color.abAccent)
                    }
                }
                TextField("Subject / course, e.g. Calculus 1", text: $course)
                TextField("Your WhatsApp, e.g. 079 123 4567", text: $whatsapp).keyboardType(.phonePad).textContentType(.telephoneNumber)
                TextField("Anything special? (optional)", text: $notes)
            } header: { Text("2. Your file and details") } footer: { Text("We send the finished file to your WhatsApp.") }

            Section14 {
                PaymentRow(title: "CliQ", detail: "Alias ALANQ99 · Bank of Jordan", symbol: "banknote.fill", on: payment == "CliQ") { payment = "CliQ" }
                PaymentRow(title: "In person", detail: "Pay on campus", symbol: "person.2.fill", on: payment == "In-Person") { payment = "In-Person" }
                if payment == "CliQ" {
                    Button {
                        UIPasteboard.general.string = "ALANQ99"
                        Haptics.success()
                        withAnimation { copied = true }
                    } label: { Label(copied ? "Copied ALANQ99" : "Copy CliQ alias ALANQ99", systemImage: copied ? "checkmark.circle.fill" : "doc.on.doc") }
                }
            } header: { Text("3. Payment · 3 JD") } footer: { Text("After you submit, email us to confirm your payment.") }

            Section14 {
                Button(action: submit) {
                    HStack {
                        Spacer()
                        if sending { ABDots().tint14(.white) }
                        Text(sending ? "Sending…" : "Send request").font(.headline)
                        Spacer()
                    }
                    .padding(.vertical, 6)
                }
                .prominentButton14().tint14(.abFill)
                .disabled(sending)
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            } footer: {
                if let m = missing { Text(m) }
                else if bridge.status == .connecting { Text("Connecting…") }
            }
        }
        .navigationTitle("Request a material")
        .navigationBarTitleDisplayMode(.inline)
        .bridgeHost(bridge)
        .dismissKeyboardOnScroll()
        .fileImporter(isPresented: $picking, allowedContentTypes: [.pdf]) { r in
            guard case .success(let u) = r else { return }
            let ok = u.startAccessingSecurityScopedResource()
            defer { if ok { u.stopAccessingSecurityScopedResource() } }
            guard let d = try? Data(contentsOf: u) else { return }
            if d.count > Requests.maxBytes { result = ("File too big", "Please choose a PDF up to 15 MB.", false); return }
            file = (u.lastPathComponent, d)
        }
        .alert14(result?.title ?? "", isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) {
            if result?.ok == true {
                Button("Email us") { openURL(URL(string: Requests.whatsapp)!) }
                Button("Done") {}
            } else {
                Button("OK") {}
            }
        } message: { Text(result?.text ?? "") }
    }

    private func submit() {
        if let m = missing { result = ("Almost there", m, false); Haptics.error(); return }
        guard let file else { return }
        sending = true
        let data: [String: Any] = [
            "service": service, "course": course.trimmingCharacters(in: .whitespaces), "whatsapp": whatsapp.trimmingCharacters(in: .whitespaces),
            "instructions": notes.trimmingCharacters(in: .whitespaces), "payment": payment, "filename": file.name,
            "mimeType": "application/pdf", "base64": file.data.base64EncodedString(),
        ]
        Task {
            if bridge.status == .failed { bridge.connect(force: true) }
            do {
                let r = try await bridge.call("handleFormSubmission", [data], timeout: 120)
                result = ("Request sent!", (r as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "We got your request. Email us to confirm the payment.", true)
                self.file = nil; service = ""; course = ""; notes = ""
                Haptics.success()
            } catch {
                result = ("Couldn't send", ScriptBridge.message(error), false)
                Haptics.error()
            }
            sending = false
        }
    }
}

private struct PaymentRow: View {
    let title: String
    let detail: String
    let symbol: String
    let on: Bool
    let action: () -> Void
    var body: some View {
        Button(action: { action(); UISelectionFeedbackGenerator().selectionChanged() }) {
            HStack(spacing: 12) {
                Image(systemName: symbol).fg14(.white)
                    .frame(width: 32, height: 32)
                    .bgIn14(Color(UIColor(hex: 0x1f8a3e)), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).fg14(.primary)
                    Text(detail).font(.caption).fg14(.secondary)
                }
                Spacer()
                Image(systemName: on ? "checkmark.circle.fill" : "circle").font(.title3).fg14(on ? Color.abAccent : Color.secondary)
            }
        }
    }
}
