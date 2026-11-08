import SwiftUI
import Security
import UserNotifications
import BackgroundTasks

// Accounts, chat and admin, through the "UniHub Accounts" Apps Script (tools/apps-script/accounts-api.gs).
// The website uses the same API, so accounts, chat and admin actions are shared.

enum AccountsAPI {
    static let url = URL(string: "https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec")!

    struct Failure: LocalizedError {
        let message: String
        var needCode = false
        var errorDescription: String? { message }
    }

    /// POSTs {action, ...fields} as text/plain JSON (no CORS preflight) and returns the reply when ok
    static func call(_ action: String, _ fields: [String: Any] = [:], token: String? = nil) async throws -> [String: Any] {
        var body = fields
        body["action"] = action
        if let token { body["token"] = token }
        var req = URLRequest(url: url, timeoutInterval: fields["video"] != nil || fields["file"] != nil || action == "dmFile" ? 180 : 40)
        req.httpMethod = "POST"
        req.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let data: Data
        do { (data, _) = try await URLSession.shared.data14(for: req) } catch {
            throw Failure(message: "Couldn't reach UniHub. Check your connection and try again.")
        }
        guard let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            throw Failure(message: "The server sent something unexpected. Try again.")
        }
        if obj["ok"] as? Bool == true { return obj }
        throw Failure(message: obj["error"] as? String ?? "Something went wrong.", needCode: obj["needCode"] as? Bool == true)
    }
}

// MARK: - models

struct ABUser: Codable, Identifiable, Hashable {
    var id: String
    var username: String
    var displayName: String
    var role: String
    var color: String
    var email: String?
    var uniId: String?
    var banned: Bool?
    var banReason: String?
    var created: String?
    var lastSeen: String?
    var mustReset: Bool?
    var avatar: String?
    var cover: String?
    var phone: String?
    var showPhone: Bool?
    /// "male" or "female"; others only get it when the person chose to show it
    var gender: String?
    var showGender: Bool?
    var bio: String?
    var major: String?
    var college: String?
    var year: String?
    var interests: String?
    var friendship: String?
    var blocked: [String]?
    var badges: [String]?
    var online: Bool?

    var isAdmin: Bool { role == "admin" }
    var initial: String { String((displayName.isEmpty ? username : displayName).prefix(1)).uppercased() }
}

/// Badges an admin can give (same list as BADGES in accounts-api.gs)
enum ABBadges {
    static let all: [(id: String, name: String, emoji: String)] = [
        ("founder", "Founder", "👑"), ("moderator", "Moderator", "🛡️"), ("support", "Support", "🛟"), ("helper", "Helper", "🤝"),
        ("donor", "Donor", "💛"), ("contributor", "Contributor", "📚"), ("tutor", "Tutor", "🎓"), ("verified", "Verified", "✅"),
        ("vip", "VIP", "⭐"), ("developer", "Developer", "💻"), ("team", "UniHub team", "🏦"), ("mentor", "Mentor", "🧭"),
        ("ambassador", "Ambassador", "📣"), ("top_student", "Top student", "🏅"), ("class_rep", "Class rep", "🙋"),
        ("translator", "Translator", "🌐"), ("designer", "Designer", "🎨"), ("researcher", "Researcher", "🔬"),
        ("organizer", "Organizer", "🎉"), ("early_supporter", "Early supporter", "🌱"), ("sponsor", "Sponsor", "💎"), ("legend", "Legend", "🔥"),
    ]
    static func emoji(_ id: String) -> String { all.first { $0.id == id }?.emoji ?? "" }
    static func name(_ id: String) -> String { all.first { $0.id == id }.map { L($0.name) } ?? id }
}

/// The colleges a student can pick on their profile
enum ABColleges {
    static let all = ["Information Technology", "Engineering", "Pharmacy", "Dentistry", "Nursing", "Allied Medical Sciences", "Business", "Law",
                      "Arts & Sciences", "Arts & Design", "Other"]
    static let years = ["First year", "Second year", "Third year", "Fourth year", "Fifth year", "Graduate", "Alumni"]
}

/// What chat screens know about the people in a room
struct ChatPerson: Codable, Hashable {
    var avatar: String?
    var badges: [String]?
    var role: String?
    var displayName: String?
    var color: String?
    var online: Bool?
    var lastSeen: String?
}

struct ChatRoom: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var emoji: String
    var about: String
    var adminOnly: Bool?
    var locked: Bool?
    var archived: Bool?
    var pinned: String?
    var order: Int?
}

/// Something the app may notify about (the `events` action)
struct ABEvent: Codable, Hashable {
    var kind: String?
    var id: String
    var type: String
    var room: String?
    var roomName: String?
    var userId: String?
    var displayName: String?
    var text: String
    var created: String
    var url: String?
    var replyToUserId: String?
}

struct ChatMessage: Codable, Identifiable, Hashable {
    var id: String
    var room: String
    var userId: String
    var username: String
    var displayName: String
    var role: String
    var text: String
    var created: String
    var replyTo: String?
    var color: String
    var edited: String?
    var fileName: String?
    var fileType: String?
    var fileSize: Int?
    var hasFile: Bool?
    var duration: Int?
    var expires: String?
    var pending: Bool?
    var localFile: URL?

    var date: Date { ISO8601DateFormatter.ab.date(from: created) ?? Date() }
    var preview: String {
        if !text.isEmpty { return text }
        switch fileType {
        case "voice": return "🎤 " + L("Voice message")
        case "video": return "🎬 " + L("Video")
        case "photo": return "📷 " + L("Photo")
        default: return text
        }
    }
}

struct ChatLatest: Codable, Hashable {
    var id: String
    var room: String
    var roomName: String?
    var displayName: String
    var username: String
    var userId: String
    var text: String
    var created: String
    var announcement: Bool?
}

extension ISO8601DateFormatter {
    static let ab: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
}

func decode<T: Decodable>(_ type: T.Type, _ any: Any?) -> T? {
    guard let any, JSONSerialization.isValidJSONObject(any) || any is [Any] || any is [String: Any],
          let data = try? JSONSerialization.data(withJSONObject: any) else { return nil }
    return try? JSONDecoder().decode(T.self, from: data)
}

extension Color {
    init(hexString: String) {
        let s = hexString.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        self = Color(UIColor(hex: UInt32(s, radix: 16) ?? 0x8C1D40))
    }
}

// MARK: - keychain

enum Keychain {
    static func set(_ value: String?, for key: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key, kSecAttrService as String: "org.unihub.app"]
        SecItemDelete(q as CFDictionary)
        guard let value, let data = value.data(using: .utf8) else { return }
        var add = q
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ key: String) -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key, kSecAttrService as String: "org.unihub.app",
                                kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }
}

// MARK: - session

/// The signed-in account (token in the keychain, profile cached so the app opens signed in offline)
@MainActor
final class AccountStore: ObservableObject {
    static let shared = AccountStore()

    @Published private(set) var user: ABUser?
    @Published private(set) var rooms: [ChatRoom] = AccountStore.defaultRooms
    @Published var signInShown = false
    /// The thank-you after signing up during the early supporter period
    @Published var earlyThanksShown = false

    private(set) var token: String?
    private let userKey = "ab.account.user"

    static let defaultRooms: [ChatRoom] = [
        .init(id: "general", name: "General", emoji: "💬", about: "Say hi, ask anything about ASU"),
        .init(id: "help", name: "Study help", emoji: "🆘", about: "Stuck on something? Ask other students"),
        .init(id: "materials", name: "Materials", emoji: "📚", about: "Ask for or share study files"),
        .init(id: "announcements", name: "Announcements", emoji: "📣", about: "News from the UniHub team", adminOnly: true),
    ]

    var signedIn: Bool { user != nil && token != nil }
    var isAdmin: Bool { user?.isAdmin == true }

    init() {
        token = Keychain.get("ab.token")
        if let d = UserDefaults.standard.data(forKey: userKey) { user = try? JSONDecoder().decode(ABUser.self, from: d) }
        if token == nil { user = nil }
    }

    func call(_ action: String, _ fields: [String: Any] = [:]) async throws -> [String: Any] {
        do {
            return try await AccountsAPI.call(action, fields, token: token)
        } catch let e as AccountsAPI.Failure where e.message.contains("sign in again") || e.message.contains("banned") || e.message.contains("no longer exists") {
            signOutLocally()
            throw e
        }
    }

    private func adopt(_ r: [String: Any]) {
        if let t = r["token"] as? String { token = t; Keychain.set(t, for: "ab.token") }
        if let u = decode(ABUser.self, r["user"]), u != user {
            user = u
            UserDefaults.standard.set(try? JSONEncoder().encode(u), forKey: userKey)
        }
        if let rs = decode([ChatRoom].self, r["rooms"]), !rs.isEmpty, rs != rooms { rooms = rs }
    }

    func signIn(login: String, password: String) async throws {
        adopt(try await AccountsAPI.call("login", ["login": login.trimmingCharacters(in: .whitespaces), "password": password, "platform": "ios"]))
        await refresh()
        ChatNotifier.shared.signedIn()
    }

    func register(username: String, password: String, displayName: String, email: String, uniId: String, code: String, gender: String, showGender: Bool) async throws {
        let reply = try await AccountsAPI.call("register", [
            "username": username.trimmingCharacters(in: .whitespaces), "password": password, "displayName": displayName,
            "email": email.trimmingCharacters(in: .whitespaces), "uniId": uniId.trimmingCharacters(in: .whitespaces),
            "code": code.trimmingCharacters(in: .whitespaces), "platform": "ios", "device": UIDevice.current.identifierForVendor?.uuidString ?? "",
            "gender": gender, "showGender": showGender,
        ])
        adopt(reply)
        await refresh()
        ChatNotifier.shared.signedIn()
        if reply["earlySupporter"] as? Bool == true {
            // after the sign-in sheet has closed; a screen can't show an alert while its sheet is leaving
            try? await Task.sleep(nanoseconds: 900_000_000)
            earlyThanksShown = true
        }
    }

    /// Emails the admin code (only for the owner's username, email or university ID)
    func sendAdminCode(username: String, email: String, uniId: String) async throws -> String {
        let r = try await AccountsAPI.call("adminCode", ["username": username, "email": email, "uniId": uniId])
        return r["sentTo"] as? String ?? "the owner's email"
    }

    func refresh() async {
        guard token != nil, let r = try? await call("me") else { return }
        adopt(r)
    }

    func updateProfile(displayName: String, email: String, uniId: String, phone: String, showPhone: Bool, bio: String, major: String, college: String, year: String, interests: String, gender: String, showGender: Bool) async throws {
        adopt(try await call("updateProfile", ["displayName": displayName, "email": email, "uniId": uniId,
                                               "phone": phone, "showPhone": showPhone, "bio": bio, "major": major, "college": college, "year": year, "interests": interests,
                                               "gender": gender, "showGender": showGender]))
    }

    /// For accounts made before sex was asked at sign-up
    func setGender(_ gender: String, show: Bool) async throws {
        adopt(try await call("updateProfile", ["gender": gender, "showGender": show]))
    }

    /// Uploads a cover photo, or removes it with nil
    func setCover(_ image: UIImage?) async throws {
        guard let image else { adopt(try await call("setCover", ["remove": true])); return }
        let data = await Task.detached(priority: .userInitiated) { PhotoTools.jpegBase64(image, maxSide: 1280) }.value
        guard let data else { return }
        adopt(try await call("setCover", ["data": data]))
    }

    /// Uploads a profile photo (a 640 px square JPEG, so it still looks good when someone holds it to expand it), or removes it with nil
    func setAvatar(_ image: UIImage?) async throws {
        guard let image else { adopt(try await call("setAvatar", ["remove": true])); return }
        let side: CGFloat = 640
        let scale = side / min(image.size.width, image.size.height)
        let size = CGSize(width: side, height: side)
        let fmt = UIGraphicsImageRendererFormat.default()
        fmt.scale = 1
        let square = UIGraphicsImageRenderer(size: size, format: fmt).image { _ in
            let w = image.size.width * scale, h = image.size.height * scale
            image.draw(in: CGRect(x: (side - w) / 2, y: (side - h) / 2, width: w, height: h))
        }
        guard let jpeg = square.jpegData(compressionQuality: 0.82) else { return }
        adopt(try await call("setAvatar", ["data": jpeg.base64EncodedString(), "type": "image/jpeg"]))
    }

    /// Reloads the room list (after an admin changed it)
    func reloadRooms() async {
        if let r = try? await call("rooms"), let rs = decode([ChatRoom].self, r["rooms"]), !rs.isEmpty { rooms = rs }
    }

    func setRooms(_ any: Any?) {
        if let rs = decode([ChatRoom].self, any), !rs.isEmpty { rooms = rs }
    }

    func changePassword(old: String, new: String) async throws {
        _ = try await call("changePassword", ["old": old, "password": new])
        await refresh()
    }

    func deleteAccount(password: String) async throws {
        _ = try await call("deleteAccount", ["password": password])
        signOutLocally()
    }

    func signOut() async {
        _ = try? await AccountsAPI.call("logout", [:], token: token)
        signOutLocally()
    }

    func signOutLocally() {
        try? FileManager.default.removeItem(at: DMFiles.folder)
        token = nil
        user = nil
        Keychain.set(nil, for: "ab.token")
        UserDefaults.standard.removeObject(forKey: userKey)
        ReplyCache.clear()
        NoticeStore.shared.reset()
    }
}

// MARK: - saved answers

/// The last answer to the slower calls, kept on this phone so Friends and the admin screens open at once and update
/// when the new answer arrives. Removed on sign out.
enum ReplyCache {
    private static var dir: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("ab-replies", isDirectory: true)
    }

    static func read(_ name: String) -> [String: Any]? {
        guard let d = try? Data(contentsOf: dir.appendingPathComponent(name)) else { return nil }
        return (try? JSONSerialization.jsonObject(with: d)) as? [String: Any]
    }

    static func write(_ name: String, _ reply: [String: Any]) {
        guard JSONSerialization.isValidJSONObject(reply), let d = try? JSONSerialization.data(withJSONObject: reply) else { return }
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try? d.write(to: dir.appendingPathComponent(name), options: [.atomic, .completeFileProtection])
    }

    static func clear() { try? FileManager.default.removeItem(at: dir) }
}

extension AccountStore {
    private func replyName(_ action: String, _ tag: String) -> String {
        let raw = "\(user?.id ?? "guest")-\(action)-\(tag)"
        return String(raw.map { $0.isLetter || $0.isNumber || $0 == "-" ? $0 : "_" }) + ".json"
    }

    /// The answer saved the last time, if any
    func saved(_ action: String, tag: String = "") -> [String: Any]? { ReplyCache.read(replyName(action, tag)) }

    /// A normal call that also saves its answer
    func callSaving(_ action: String, _ fields: [String: Any] = [:], tag: String = "") async throws -> [String: Any] {
        let name = replyName(action, tag)
        let r = try await call(action, fields)
        ReplyCache.write(name, r)
        return r
    }
}

// MARK: - notifications

/// Local notifications for new messages, replies, announcements and uploads. The app checks the server's
/// event feed while it is open and, when iOS allows it, in the background; a sideloaded app cannot get server push.
@MainActor
final class ChatNotifier: ObservableObject {
    static let shared = ChatNotifier()
    static let task = "org.unihub.app.chat"

    private static func pref(_ key: String, _ fallback: Bool) -> Bool {
        UserDefaults.standard.object(forKey: "ab.notify." + key) as? Bool ?? fallback
    }
    /// 1.5 had one setting: 0 every message, 1 announcements only, 2 off
    private static let oldMode = UserDefaults.standard.integer(forKey: "ab.notify.mode")

    @Published var messages = pref("messages", oldMode == 0) { didSet { UserDefaults.standard.set(messages, forKey: "ab.notify.messages") } }
    @Published var replies = pref("replies", oldMode != 2) { didSet { UserDefaults.standard.set(replies, forKey: "ab.notify.replies") } }
    @Published var announcements = pref("announcements", oldMode != 2) { didSet { UserDefaults.standard.set(announcements, forKey: "ab.notify.announcements") } }
    @Published var uploads = pref("uploads", oldMode != 2) { didSet { UserDefaults.standard.set(uploads, forKey: "ab.notify.uploads") } }
    @Published var social = pref("social", true) { didSet { UserDefaults.standard.set(social, forKey: "ab.notify.social") } }
    /// New friend requests and pokes since the Friends tab was last open
    @Published var socialUnread = 0
    /// Rooms whose messages never notify
    @Published var muted = Set(UserDefaults.standard.stringArray(forKey: "ab.notify.muted") ?? []) {
        didSet { UserDefaults.standard.set(Array(muted), forKey: "ab.notify.muted") }
    }
    /// The room on screen, which never notifies
    var openRoom: String?
    var openDM: String?
    @Published var unread = 0
    private var timer: Task<Void, Never>?

    func signedIn() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        start()
    }

    func start() {
        timer?.cancel()
        guard AccountStore.shared.signedIn else { return }
        timer = Task { [weak self] in
            while !Task.isCancelled {
                await self?.check()
                try? await Task.sleep(nanoseconds: 25_000_000_000)
            }
        }
        Self.schedule()
    }

    func stop() { timer?.cancel(); timer = nil }

    func check() async {
        guard let token = AccountStore.shared.token else { return }
        let key = "ab.notify.lastSeenDate"
        let seen = UserDefaults.standard.string(forKey: key)
        guard let r = try? await AccountsAPI.call("events", ["since": seen ?? "", "social": true, "social2": true, "social3": true], token: token) else { return }
        let events = decode([ABEvent].self, r["events"]) ?? []
        let newest = events.map(\.created).max() ?? ""
        // first check after install: remember, don't notify
        guard let seen else { UserDefaults.standard.set(max(newest, r["now"] as? String ?? ""), forKey: key); return }
        UserDefaults.standard.set(max(seen, newest), forKey: key)
        for e in events where e.created > seen { await deliver(e) }
        if events.contains(where: { $0.created > seen && $0.type != "message" }) { await NoticeStore.shared.load() }
    }

    /// Whether the user wants a notification for this event
    func wants(_ e: ABEvent) -> Bool {
        let me = AccountStore.shared.user?.id
        if e.userId != nil && e.userId == me { return false }
        if let u = e.userId, AccountStore.shared.user?.blocked?.contains(u) == true { return false }
        switch e.type {
        case "upload": return uploads
        case "announcement": return announcements
        case "friend_request", "friend_accept", "poke", "post_comment": return social
        case "group_request", "group_approved", "group_rejected": return true
        case "dm": return e.userId != openDM
        default:
            if e.room == openRoom { return false }
            if replies && me != nil && e.replyToUserId == me { return true }
            return messages && !muted.contains(e.room ?? "")
        }
    }

    func deliver(_ e: ABEvent) async {
        guard wants(e) else { return }
        let isPrivate = e.type.hasPrefix("friend_") || e.type.hasPrefix("group_") || e.type == "poke" || e.type == "post_comment"
        if isPrivate { if !e.type.hasPrefix("group_") { socialUnread += 1 } }
        else if e.type != "upload" { unread += 1 }
        let me = AccountStore.shared.user?.id
        let who = e.displayName ?? ""
        let c = UNMutableNotificationContent()
        switch e.type {
        case "upload":
            c.title = L("📄 New upload on UniHub")
        case "announcement":
            c.title = "📣 \(who)"
        case "friend_request":
            c.title = L("New friend request")
        case "friend_accept":
            c.title = L("You have a new friend")
        case "poke":
            c.title = "👉 " + who
        case "post_comment":
            c.title = String(format: L("%@ commented on your post"), who)
        case "dm":
            c.title = who
        case "group_request":
            c.title = L("New group request")
        case "group_approved":
            c.title = L("Your group was accepted")
        case "group_rejected":
            c.title = L("Your group wasn't accepted")
        default:
            c.title = e.replyToUserId == me && me != nil ? String(format: L("%@ replied to you"), who) : String(format: L("New message from %@"), who)
        }
        c.subtitle = e.type == "upload" ? "" : (e.roomName ?? "")
        c.body = e.text
        switch e.type {
        case "friend_request": c.body = String(format: L("%@ wants to be your friend"), who)
        case "friend_accept": c.body = String(format: L("%@ accepted your friend request"), who)
        case "poke": c.body = Social.pokeText(kind: e.kind ?? "poke", name: who, place: e.text)
        case "group_request": c.body = String(format: L("%@ asked to add: %@"), who, e.text)
        default: break
        }
        c.sound = NotificationSounds.sound(NotificationSounds.messageKey)
        c.threadIdentifier = e.room ?? e.type
        c.userInfo = ["room": e.type == "dm" ? "__dm" : (e.room ?? ""), "url": e.url ?? "", "open": e.type == "group_request" ? "admin" : isPrivate && !e.type.hasPrefix("group_") ? "friends" : ""]
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: e.id, content: c, trigger: nil))
        // the server's list has the social ones; keep chat and private messages on the phone
        if e.type == "dm" || e.type == "message" || !["upload", "announcement", "friend_request", "friend_accept", "poke", "post_comment", "group_request", "group_approved", "group_rejected"].contains(e.type) {
            let line = c.subtitle.isEmpty ? c.body : c.subtitle + ": " + c.body
            LocalNotices.shared.add(id: e.id, type: e.type == "dm" ? "dm" : "chat", title: c.title, body: line, room: e.room)
        }
    }

    // background refresh (iOS decides when; usually a few times a day)
    nonisolated static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: task, using: nil) { t in
            let work = Task { @MainActor in
                await ChatNotifier.shared.check()
                t.setTaskCompleted(success: true)
            }
            t.expirationHandler = { work.cancel() }
            schedule()
        }
    }

    nonisolated static func schedule() {
        let r = BGAppRefreshTaskRequest(identifier: task)
        r.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(r)
    }
}

/// Shows notifications while the app is open too, and opens the chat when one is tapped
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationDelegate()
    func userNotificationCenter(_ c: UNUserNotificationCenter, willPresent n: UNNotification) async -> UNNotificationPresentationOptions {
        if n.request.identifier.hasPrefix("lecture-") { await MainActor.run { LocalNotices.shared.catchUpLectures() } }
        return [.banner, .sound, .list]
    }
    func userNotificationCenter(_ c: UNUserNotificationCenter, didReceive r: UNNotificationResponse) async {
        let info = r.notification.request.content.userInfo
        let room = info["room"] as? String ?? ""
        let url = info["url"] as? String ?? ""
        let open = info["open"] as? String ?? ""
        await MainActor.run {
            if open == "lectures" { LocalNotices.shared.catchUpLectures() }
            if open == "lectures" {
                AppState.shared?.tab = .lectures
            } else if open == "friends" {
                AppState.shared?.tab = .friends
            } else if open == "admin" {
                AppState.shared?.tab = .admin
            } else if room.isEmpty && !url.isEmpty {
                // a new upload: show the latest uploads list
                AppState.shared?.tab = .home
                AppState.shared?.pendingRoute = .page("/guides/latest", "")
            } else {
                AppState.shared?.tab = .chat
                AppState.shared?.pendingRoom = room
            }
        }
    }
}
