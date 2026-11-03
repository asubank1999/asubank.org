import SwiftUI

/// A round colored avatar with the first letter of the name
struct Avatar: View {
    let name: String
    let color: String
    var size: CGFloat = 36
    var admin = false
    var body: some View {
        let c = Color(hexString: color)
        Text(String(name.prefix(1)).uppercased())
            .font(.system(size: size * 0.44, weight: .bold)).foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(c.abGradient, in: Circle())
            .overlay(alignment: .bottomTrailing) {
                if admin {
                    Image(systemName: "checkmark.seal.fill").font(.system(size: size * 0.34)).foregroundStyle(Color.abGold)
                        .background(Circle().fill(Color(UIColor.systemBackground)).padding(1))
                        .offset(x: 2, y: 2)
                }
            }
            .accessibilityHidden(true)
    }
}

// MARK: - sign in / sign up

struct SignInScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var creating = false
    @State private var login = ""
    @State private var username = ""
    @State private var displayName = ""
    @State private var password = ""
    @State private var password2 = ""
    @State private var email = ""
    @State private var uniId = ""
    @State private var code = ""
    @State private var needCode = false
    @State private var codeSentTo: String?
    @State private var busy = false
    @State private var error: String?
    @State private var showPassword = false

    private var canSubmit: Bool {
        if creating {
            return username.count >= 3 && password.count >= 6 && password == password2 && (!needCode || code.count == 6)
        }
        return !login.isEmpty && !password.isEmpty
    }

    var body: some View {
        ABStack {
            Form {
                Section {
                    VStack(spacing: 10) {
                        Image("Logo").resizable().scaledToFit().frame(width: 74, height: 74)
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        Text(creating ? "Create your account" : "Welcome back").font(.title2.weight(.bold))
                        Text(creating ? "Join the student chat and keep your stuff in sync." : "Sign in to chat with other students.")
                            .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 6)
                    .listRowBackground(Color.clear)
                    Picker("", selection: $creating.animation()) {
                        Text("Sign in").tag(false)
                        Text("Create account").tag(true)
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }

                if creating {
                    Section {
                        TextField("Username", text: $username).textContentType(.username).autocapitalization(.none).disableAutocorrection(true)
                        TextField("Display name (optional)", text: $displayName).textContentType(.name)
                        passwordField("Password (6+ characters)", $password, new: true)
                        passwordField("Repeat password", $password2, new: true)
                    } header: { Text("Your account") } footer: {
                        if !password2.isEmpty && password != password2 { Text("The passwords don't match.").foregroundStyle(.red) }
                        else { Text("Usernames are 3 to 20 letters, numbers, dots or underscores.") }
                    }
                    Section {
                        TextField("University email (optional)", text: $email).textContentType(.emailAddress).keyboardType(.emailAddress).autocapitalization(.none)
                        TextField("University ID (optional)", text: $uniId).keyboardType(.numberPad)
                    } header: { Text("Optional") } footer: { Text("Lets you sign in with your email or ID too. Nobody else can see them.") }
                    if needCode {
                        Section {
                            TextField("6-digit code", text: $code).keyboardType(.numberPad).textContentType(.oneTimeCode)
                            Button(codeSentTo == nil ? "Send code" : "Send again") { Task { await sendCode() } }.disabled(busy)
                        } header: { Text("Admin account") } footer: {
                            Text(codeSentTo.map { "We sent the code to \($0)." } ?? "This username, email or ID belongs to the admin. We'll email a code to confirm it's you.")
                        }
                    }
                } else {
                    Section {
                        TextField("Username, email or university ID", text: $login).textContentType(.username).autocapitalization(.none).disableAutocorrection(true)
                        passwordField("Password", $password, new: false)
                    } footer: { Text("Forgot your password? Ask an admin in the WhatsApp community to reset it.") }
                }

                if let error {
                    Section { Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red) }
                }

                Section {
                    Button(action: { Task { await submit() } }) {
                        HStack {
                            Spacer()
                            if busy { ProgressView().tint(.white) }
                            Text(creating ? "Create account" : "Sign in").font(.headline)
                            Spacer()
                        }
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent).tint(.abAccent)
                    .disabled(!canSubmit || busy)
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                } footer: {
                    Text("By continuing you agree to the Terms of Service and the WhatsApp group rules. Be kind; admins can remove messages and accounts.")
                }
            }
            .navigationTitle(creating ? "Create account" : "Sign in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
            .dismissKeyboardOnScroll()
        }
    }

    private func passwordField(_ title: String, _ text: Binding<String>, new: Bool) -> some View {
        HStack {
            Group {
                if showPassword { TextField(title, text: text) } else { SecureField(title, text: text) }
            }
            .textContentType(new ? .newPassword : .password).autocapitalization(.none).disableAutocorrection(true)
            Button { showPassword.toggle() } label: { Image(systemName: showPassword ? "eye.slash" : "eye").foregroundStyle(.secondary) }
                .buttonStyle(.plain)
        }
    }

    private func sendCode() async {
        busy = true; error = nil
        do { codeSentTo = try await account.sendAdminCode(username: username, email: email, uniId: uniId) } catch { self.error = error.localizedDescription }
        busy = false
    }

    private func submit() async {
        busy = true; error = nil
        do {
            if creating {
                try await account.register(username: username, password: password, displayName: displayName, email: email, uniId: uniId, code: code)
            } else {
                try await account.signIn(login: login, password: password)
            }
            Haptics.success()
            dismiss()
        } catch let e as AccountsAPI.Failure where e.needCode {
            withAnimation { needCode = true }
            error = e.message
        } catch {
            self.error = error.localizedDescription
            Haptics.error()
        }
        busy = false
    }
}

// MARK: - profile

struct AccountScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var notifier = ChatNotifier.shared
    @State private var editing = false
    @State private var changing = false
    @State private var deleting = false
    @State private var confirmSignOut = false

    var body: some View {
        List {
            if let u = account.user {
                Section {
                    HStack(spacing: 14) {
                        Avatar(name: u.displayName, color: u.color, size: 60, admin: u.isAdmin)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(u.displayName).font(.title3.weight(.semibold))
                            Text("@\(u.username)").foregroundStyle(.secondary)
                            if u.isAdmin { Badge(text: "ADMIN", color: .orange) }
                        }
                    }
                    .padding(.vertical, 6)
                }
                if u.mustReset == true {
                    Section {
                        Button { changing = true } label: { Label("An admin reset your password. Choose a new one now.", systemImage: "key.fill") }
                    }
                }
                Section("Profile") {
                    row("Email", u.email?.isEmpty == false ? u.email! : "Not set")
                    row("University ID", u.uniId?.isEmpty == false ? u.uniId! : "Not set")
                    Button { editing = true } label: { Label("Edit profile", systemImage: "pencil") }
                    Button { changing = true } label: { Label("Change password", systemImage: "key") }
                }
                Section {
                    Picker(selection: $notifier.mode) {
                        Text("Every new message").tag(0)
                        Text("Announcements only").tag(1)
                        Text("Off").tag(2)
                    } label: { Label("Chat notifications", systemImage: "bell.badge") }
                } footer: { Text("ASU Bank checks for new messages while it's open, and in the background when iOS allows it.") }
                Section {
                    Button { confirmSignOut = true } label: { Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right") }
                    Button(role: .destructive) { deleting = true } label: { Label("Delete my account", systemImage: "trash") }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Account")
        .refreshable { await account.refresh() }
        .sheet(isPresented: $editing) { EditProfileSheet() }
        .sheet(isPresented: $changing) { ChangePasswordSheet() }
        .sheet(isPresented: $deleting) { DeleteAccountSheet() }
        .confirmationDialog("Sign out of ASU Bank?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { Task { await account.signOut(); ChatNotifier.shared.stop() } }
        }
        .task { await account.refresh() }
    }

    private func row(_ k: String, _ v: String) -> some View {
        HStack { Text(k); Spacer(); Text(v).foregroundStyle(.secondary).lineLimit(1) }
    }
}

private struct EditProfileSheet: View {
    @ObservedObject private var account = AccountStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""
    @State private var uniId = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ABStack {
            Form {
                Section("Display name") { TextField("Your name", text: $name) }
                Section {
                    TextField("University email", text: $email).keyboardType(.emailAddress).autocapitalization(.none)
                    TextField("University ID", text: $uniId).keyboardType(.numberPad)
                } header: { Text("Optional") } footer: { Text("You can sign in with these too.") }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle("Edit profile").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Saving…" : "Save") {
                        Task {
                            busy = true
                            do { try await account.updateProfile(displayName: name, email: email, uniId: uniId); dismiss() } catch { self.error = error.localizedDescription }
                            busy = false
                        }
                    }.disabled(busy)
                }
            }
            .onAppear {
                name = account.user?.displayName ?? ""; email = account.user?.email ?? ""; uniId = account.user?.uniId ?? ""
            }
        }
    }
}

private struct ChangePasswordSheet: View {
    @ObservedObject private var account = AccountStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var old = ""
    @State private var new = ""
    @State private var again = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ABStack {
            Form {
                Section { SecureField("Current password", text: $old).textContentType(.password) }
                Section {
                    SecureField("New password (6+ characters)", text: $new).textContentType(.newPassword)
                    SecureField("Repeat new password", text: $again).textContentType(.newPassword)
                } footer: { Text("You'll stay signed in here; other devices are signed out.") }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle("Change password").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task {
                            busy = true
                            do { try await account.changePassword(old: old, new: new); Haptics.success(); dismiss() } catch { self.error = error.localizedDescription }
                            busy = false
                        }
                    }.disabled(busy || new.count < 6 || new != again || old.isEmpty)
                }
            }
        }
    }
}

private struct DeleteAccountSheet: View {
    @ObservedObject private var account = AccountStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ABStack {
            Form {
                Section {
                    Text("This deletes your account for good. Your messages stay in the chat without your account.").foregroundStyle(.secondary)
                    SecureField("Your password", text: $password).textContentType(.password)
                }
                if let error { Section { Text(error).foregroundStyle(.red) } }
                Section {
                    Button(role: .destructive) {
                        Task {
                            busy = true
                            do { try await account.deleteAccount(password: password); dismiss() } catch { self.error = error.localizedDescription }
                            busy = false
                        }
                    } label: { Text(busy ? "Deleting…" : "Delete my account") }.disabled(password.isEmpty || busy)
                }
            }
            .navigationTitle("Delete account").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
    }
}

/// Shown in Chat when signed out
struct SignInPrompt: View {
    @ObservedObject private var account = AccountStore.shared
    var body: some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: "bubble.left.and.bubble.right.fill").font(.system(size: 54)).foregroundStyle(Color.abAccent)
            Text("Student chat").font(.title.weight(.bold))
            Text("Ask other ASU students for help, share materials and get news from the team. Sign in or create a free account to join.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.horizontal, 30)
            Text("اسأل زملاءك وشارك المواد — سجّل الدخول للانضمام").font(.footnote).foregroundStyle(.secondary)
            Button { account.signInShown = true } label: {
                Text("Sign in or create account").font(.headline).frame(maxWidth: 280).padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent).tint(.abAccent)
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .background(Color.abBackground)
    }
}
