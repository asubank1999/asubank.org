import SwiftUI
import PhotosUI
import ImageIO

/// A round avatar: the profile photo, or the first letter of the name on the user's color.
/// `online` adds a green (online) or grey (offline) dot.
struct Avatar: View {
    let name: String
    let color: String
    var size: CGFloat = 36
    var admin = false
    var photo: String? = nil
    var online: Bool? = nil
    /// Holding the photo shows it bigger. Off where holding already means something else.
    var peek = true
    var body: some View {
        let c = Color(hexString: color)
        let letter = Text(String(name.prefix(1)).uppercased())
            .font(.system(size: size * 0.44, weight: .bold)).foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(c.abGradient, in: Circle())
        Group {
            if let photo, let url = URL(string: photo), !photo.isEmpty {
                CachedPhoto(url: url, pixelSize: size) { letter }
                    .frame(width: size, height: size).clipShape(Circle())
            } else { letter }
        }
        .overlay(alignment: .bottomTrailing) {
            // Sits exactly on the circle's edge at any avatar size: at 30% wide, a badge in the corner of the frame is centered on the rim
            if admin { OwnerStar(size: max(13, size * 0.30)) }
        }
        .overlay(alignment: .topTrailing) {
            if let online {
                Circle().fill(online ? Color.green : Color.gray)
                    .frame(width: max(9, size * 0.26), height: max(9, size * 0.26))
                    .overlay(Circle().stroke(Color(UIColor.systemBackground), lineWidth: 2))
                    .offset(x: 1, y: -1)
            }
        }
        .modifier(AvatarPeek(photo: peek ? photo : nil))
        .accessibilityHidden(true)
    }
}

/// The gold star on the owner's and admins' photos
struct OwnerStar: View {
    let size: CGFloat
    var body: some View {
        Image(systemName: "star.fill")
            .font(.system(size: size * 0.58, weight: .bold))
            .foregroundStyle(.black.opacity(0.82))
            .frame(width: size, height: size)
            .background(Color.abGold, in: Circle())
            .overlay(Circle().stroke(.white, lineWidth: max(1, size * 0.07)))
    }
}

/// Remembers people's photos in memory, shrunk to the size they're drawn at. AsyncImage downloaded them again on every
/// redraw and decoded them full size, which was a big part of the lag in the chat and friends lists.
@MainActor
final class PhotoCache {
    static let shared = PhotoCache()
    private let images = NSCache<NSString, UIImage>()
    private var running: [String: Task<UIImage?, Never>] = [:]
    init() { images.countLimit = 150 }

    func cached(_ url: URL) -> UIImage? { images.object(forKey: url.absoluteString as NSString) }

    func load(_ url: URL, pixelSize: CGFloat) async -> UIImage? {
        let key = url.absoluteString
        if let i = images.object(forKey: key as NSString) { return i }
        if let t = running[key] { return await t.value }
        let side = pixelSize * UIScreen.main.scale
        let t = Task.detached(priority: .utility) { () -> UIImage? in
            guard let (data, _) = try? await URLSession.shared.data(from: url) else { return nil }
            let opts: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true,
                                         kCGImageSourceShouldCacheImmediately: true, kCGImageSourceThumbnailMaxPixelSize: max(side, 180)]
            guard let src = CGImageSourceCreateWithData(data as CFData, nil),
                  let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return UIImage(data: data) }
            return UIImage(cgImage: cg)
        }
        running[key] = t
        let img = await t.value
        running[key] = nil
        if let img { images.setObject(img, forKey: key as NSString) }
        return img
    }
}

struct CachedPhoto<Placeholder: View>: View {
    let url: URL
    let pixelSize: CGFloat
    @ViewBuilder let placeholder: () -> Placeholder
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let shown = image ?? PhotoCache.shared.cached(url) {
                Image(uiImage: shown).resizable().scaledToFill()
            } else {
                placeholder()
            }
        }
        .task(id: url) {
            if PhotoCache.shared.cached(url) == nil { image = await PhotoCache.shared.load(url, pixelSize: pixelSize) }
        }
    }
}

/// A user's badges as small pills ("🤝 Helper")
struct BadgeRow: View {
    let badges: [String]
    var compact = false
    var body: some View {
        if !badges.isEmpty {
            HStack(spacing: 4) {
                ForEach(badges, id: \.self) { b in
                    if compact { Text(ABBadges.emoji(b)).font(.caption2) }
                    else {
                        Text("\(ABBadges.emoji(b)) \(ABBadges.name(b))").font(.caption2.weight(.semibold))
                            .padding(.horizontal, 7).padding(.vertical, 3)
                            .background(Color.abAccentSoft, in: Capsule())
                    }
                }
            }
        }
    }
}

/// Picks a photo from the library (PHPicker works on iOS 15, PhotosPicker needs 16)
struct AvatarPicker: UIViewControllerRepresentable {
    var picked: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var c = PHPickerConfiguration()
        c.filter = .images
        c.selectionLimit = 1
        let p = PHPickerViewController(configuration: c)
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ vc: PHPickerViewController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let parent: AvatarPicker
        init(_ p: AvatarPicker) { parent = p }
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            parent.dismiss()
            guard let item = results.first?.itemProvider, item.canLoadObject(ofClass: UIImage.self) else { return }
            item.loadObject(ofClass: UIImage.self) { obj, _ in
                if let img = obj as? UIImage { DispatchQueue.main.async { self.parent.picked(img) } }
            }
        }
    }
}

/// Someone's profile: photo, badges, bio, online or last seen, phone if they share it
/// "Last seen 5 minutes ago"
func lastSeenText(_ iso: String?) -> String {
    guard let iso, let d = ISO8601DateFormatter.ab.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else { return L("Offline") }
    let f = RelativeDateTimeFormatter()
    f.unitsStyle = .full
    return String(format: L("Last seen %@"), f.localizedString(for: d, relativeTo: Date()))
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
                            if busy { ABDots().tint(.white) }
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
    @State private var picking = false
    @State private var photoBusy = false
    @State private var photoError: String?

    var body: some View {
        List {
            if let u = account.user {
                Section {
                    HStack(spacing: 14) {
                        Menu {
                            Button { picking = true } label: { Label("Choose a photo", systemImage: "photo") }
                            if u.avatar?.isEmpty == false {
                                Button(role: .destructive) { setPhoto(nil) } label: { Label("Remove photo", systemImage: "trash") }
                            }
                        } label: {
                            Avatar(name: u.displayName, color: u.color, size: 64, admin: u.isAdmin, photo: u.avatar, peek: false)
                                .overlay(alignment: .bottomLeading) {
                                    Image(systemName: photoBusy ? "hourglass.circle.fill" : "camera.circle.fill").font(.system(size: 22))
                                        .foregroundStyle(.white, Color.abAccent).offset(x: -2, y: 2)
                                }
                        }
                        VStack(alignment: .leading, spacing: 3) {
                            Text(u.displayName).font(.title3.weight(.semibold))
                            Text("@\(u.username)").foregroundStyle(.secondary)
                            HStack(spacing: 4) {
                                if u.isAdmin { Badge(text: L("ADMIN"), color: .orange) }
                                BadgeRow(badges: u.badges ?? [], compact: true)
                            }
                        }
                    }
                    .padding(.vertical, 6)
                    if let b = u.bio, !b.isEmpty { Text(b).font(.subheadline).foregroundStyle(.secondary) }
                    if let c = u.college, !c.isEmpty { Label(c, systemImage: "building.columns").font(.subheadline).foregroundStyle(.secondary) }
                    if let m = u.major, !m.isEmpty { Label(m, systemImage: "graduationcap").font(.subheadline).foregroundStyle(.secondary) }
                    if let photoError { Text(photoError).font(.caption).foregroundStyle(.red) }
                }
                if u.mustReset == true {
                    Section {
                        Button { changing = true } label: { Label("An admin reset your password. Choose a new one now.", systemImage: "key.fill") }
                    }
                }
                Section("Profile") {
                    row("Email", u.email?.isEmpty == false ? u.email! : "Not set")
                    row("University ID", u.uniId?.isEmpty == false ? u.uniId! : "Not set")
                    row("Phone", u.phone?.isEmpty == false ? u.phone! + (u.showPhone == true ? "" : " 🔒") : "Not set")
                    Button { editing = true } label: { Label("Edit profile", systemImage: "pencil") }
                    Button { changing = true } label: { Label("Change password", systemImage: "key") }
                    NavigationLink { BlockedScreen() } label: { Label("Blocked people", systemImage: "nosign") }
                }
                Section {
                    Toggle(isOn: $notifier.messages) { Label("New chat messages", systemImage: "bubble.left.and.bubble.right") }
                    Toggle(isOn: $notifier.replies) { Label("Replies to me", systemImage: "arrowshape.turn.up.left") }
                    Toggle(isOn: $notifier.announcements) { Label("Announcements", systemImage: "megaphone") }
                    Toggle(isOn: $notifier.uploads) { Label("New uploaded files", systemImage: "doc.badge.plus") }
                    Toggle(isOn: $notifier.social) { Label("Friends and pokes", systemImage: "person.2") }
                    NavigationLink { SoundsScreen() } label: { Label("Notification sounds", systemImage: "speaker.wave.2") }
                    if notifier.messages {
                        NavigationLink { MutedRoomsScreen() } label: {
                            HStack {
                                Label("Rooms", systemImage: "number")
                                Spacer()
                                Text(notifier.muted.isEmpty ? L("All") : String(format: L("%d muted"), notifier.muted.count)).foregroundStyle(.secondary)
                            }
                        }
                    }
                } header: { Text("Notifications") } footer: { Text("UniHub checks for new activity while it's open, and in the background when iOS allows it.") }
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
        .sheet(isPresented: $picking) { AvatarPicker { setPhoto($0) }.ignoresSafeArea() }
        .confirmationDialog("Sign out of UniHub?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { Task { await account.signOut(); ChatNotifier.shared.stop() } }
        }
        .task { await account.refresh() }
    }

    private func row(_ k: LocalizedStringKey, _ v: String) -> some View {
        HStack { Text(k); Spacer(); Text(v == "Not set" ? L(v) : v).foregroundStyle(.secondary).lineLimit(1) }
    }

    private func setPhoto(_ img: UIImage?) {
        photoBusy = true; photoError = nil
        Task {
            do { try await account.setAvatar(img); Haptics.success() } catch { photoError = error.localizedDescription }
            photoBusy = false
        }
    }
}

/// Which rooms may notify about new messages
private struct MutedRoomsScreen: View {
    @ObservedObject private var account = AccountStore.shared
    @ObservedObject private var notifier = ChatNotifier.shared
    var body: some View {
        List {
            Section {
                ForEach(account.rooms.filter { $0.archived != true && $0.adminOnly != true }) { r in
                    Toggle(isOn: Binding(get: { !notifier.muted.contains(r.id) },
                                         set: { on in if on { notifier.muted.remove(r.id) } else { notifier.muted.insert(r.id) } })) {
                        Text("\(r.emoji) \(r.name)")
                    }
                }
            } footer: { Text("Turn a room off to stop its message notifications. Replies to you still notify if that's on.") }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Rooms")
    }
}

struct EditProfileSheet: View {
    @ObservedObject private var account = AccountStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""
    @State private var uniId = ""
    @State private var phone = ""
    @State private var showPhone = false
    @State private var bio = ""
    @State private var major = ""
    @State private var college = ""
    @State private var year = ""
    @State private var interests = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ABStack {
            Form {
                Section("Display name") { TextField("Your name", text: $name) }
                Section {
                    TextField("A line about you (optional)", text: $bio)
                } header: { Text("About") } footer: { Text("\(bio.count)/160") }
                Section("Studies") {
                    Picker("College", selection: $college) {
                        Text("Not set").tag("")
                        ForEach(ABColleges.all, id: \.self) { Text($0).tag($0) }
                    }
                    TextField("Your major", text: $major)
                    Picker("Year", selection: $year) {
                        Text("Not set").tag("")
                        ForEach(ABColleges.years, id: \.self) { Text(L($0)).tag($0) }
                    }
                }
                Section("Interests") { TextField("Football, coding, photography…", text: $interests) }
                Section {
                    TextField("Phone number", text: $phone).keyboardType(.phonePad).textContentType(.telephoneNumber)
                    Toggle("Show my number on my profile", isOn: $showPhone)
                } header: { Text("Phone") } footer: { Text("Only admins see it unless you choose to show it.") }
                Section {
                    TextField("University email", text: $email).keyboardType(.emailAddress).autocapitalization(.none)
                    TextField("University ID", text: $uniId).keyboardType(.numberPad)
                } header: { Text("Optional") } footer: { Text("You can sign in with these too. Only you and admins see them.") }
                if let error { Section { Text(error).foregroundStyle(.red) } }
            }
            .navigationTitle("Edit profile").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Saving…" : "Save") {
                        Task {
                            busy = true
                            do {
                                try await account.updateProfile(displayName: name, email: email, uniId: uniId, phone: phone, showPhone: showPhone,
                                                                bio: String(bio.prefix(160)), major: String(major.prefix(60)), college: college,
                                                                year: year, interests: String(interests.prefix(160)))
                                dismiss()
                            } catch { self.error = error.localizedDescription }
                            busy = false
                        }
                    }.disabled(busy)
                }
            }
            .onAppear {
                let u = account.user
                name = u?.displayName ?? ""; email = u?.email ?? ""; uniId = u?.uniId ?? ""
                phone = u?.phone ?? ""; showPhone = u?.showPhone == true; bio = u?.bio ?? ""
                major = u?.major ?? ""; college = u?.college ?? ""; year = u?.year ?? ""; interests = u?.interests ?? ""
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

/// Shown in Chat and Notifications when signed out
struct SignInPrompt: View {
    var forNotifications = false
    var forFeed = false
    @ObservedObject private var account = AccountStore.shared
    var body: some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: forFeed ? "newspaper.fill" : forNotifications ? "bell.fill" : "bubble.left.and.bubble.right.fill").font(.system(size: 54)).foregroundStyle(Color.abAccent)
            if forFeed {
                Text("Sign in to see the feed").font(.title3.weight(.bold)).multilineTextAlignment(.center).padding(.horizontal, 30)
                Text("Post, comment and share stories with other ASU students.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.horizontal, 30)
            } else if forNotifications {
                Text("Sign in to see your notifications").font(.title3.weight(.bold)).multilineTextAlignment(.center).padding(.horizontal, 30)
                Text("Friend requests, pokes, new uploads and announcements will show up here.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.horizontal, 30)
            } else {
                Text("Student chat").font(.title.weight(.bold))
                Text("Ask other ASU students for help, share materials and get news from the team. Sign in or create a free account to join.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.horizontal, 30)
                Text("اسأل زملاءك وشارك المواد — سجّل الدخول للانضمام").font(.footnote).foregroundStyle(.secondary)
            }
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
