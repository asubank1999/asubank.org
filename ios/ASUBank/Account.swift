import SwiftUI
import Security
import UserNotifications
import BackgroundTasks

// Accounts, chat and admin, through the "ASU Bank Accounts" Apps Script (tools/apps-script/accounts-api.gs).
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
        var req = URLRequest(url: url, timeoutInterval: 40)
        req.httpMethod = "POST"
        req.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let data: Data
        do { (data, _) = try await URLSession.shared.data(for: req) } catch {
            throw Failure(message: "Couldn't reach ASU Bank. Check your connection and try again.")
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

    var isAdmin: Bool { role == "admin" }
    var initial: String { String((displayName.isEmpty ? username : displayName).prefix(1)).uppercased() }
}

struct ChatRoom: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var emoji: String
    var about: String
    var adminOnly: Bool?
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

    var date: Date { ISO8601DateFormatter.ab.date(from: created) ?? Date() }
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
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key, kSecAttrService as String: "org.asubank.app"]
        SecItemDelete(q as CFDictionary)
        guard let value, let data = value.data(using: .utf8) else { return }
        var add = q
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ key: String) -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key, kSecAttrService as String: "org.asubank.app",
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

    private(set) var token: String?
    private let userKey = "ab.account.user"

    static let defaultRooms: [ChatRoom] = [
        .init(id: "general", name: "General", emoji: "💬", about: "Say hi, ask anything about ASU"),
        .init(id: "help", name: "Study help", emoji: "🆘", about: "Stuck on something? Ask other students"),
        .init(id: "materials", name: "Materials", emoji: "📚", about: "Ask for or share study files"),
        .init(id: "announcements", name: "Announcements", emoji: "📣", about: "News from the ASU Bank team", adminOnly: true),
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
        if let u = decode(ABUser.self, r["user"]) {
            user = u
            UserDefaults.standard.set(try? JSONEncoder().encode(u), forKey: userKey)
        }
        if let rs = decode([ChatRoom].self, r["rooms"]), !rs.isEmpty { rooms = rs }
    }

    func signIn(login: String, password: String) async throws {
        adopt(try await AccountsAPI.call("login", ["login": login.trimmingCharacters(in: .whitespaces), "password": password, "platform": "ios"]))
        await refresh()
        ChatNotifier.shared.signedIn()
    }

    func register(username: String, password: String, displayName: String, email: String, uniId: String, code: String) async throws {
        adopt(try await AccountsAPI.call("register", [
            "username": username.trimmingCharacters(in: .whitespaces), "password": password, "displayName": displayName,
            "email": email.trimmingCharacters(in: .whitespaces), "uniId": uniId.trimmingCharacters(in: .whitespaces),
            "code": code.trimmingCharacters(in: .whitespaces), "platform": "ios", "device": UIDevice.current.identifierForVendor?.uuidString ?? "",
        ]))
        await refresh()
        ChatNotifier.shared.signedIn()
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

    func updateProfile(displayName: String, email: String, uniId: String) async throws {
        adopt(try await call("updateProfile", ["displayName": displayName, "email": email, "uniId": uniId]))
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
        token = nil
        user = nil
        Keychain.set(nil, for: "ab.token")
        UserDefaults.standard.removeObject(forKey: userKey)
    }
}

// MARK: - notifications

/// Local notifications for new chat messages. The app checks the newest message while it is open and,
/// when iOS allows it, in the background; a sideloaded app cannot get server push.
@MainActor
final class ChatNotifier: ObservableObject {
    static let shared = ChatNotifier()
    static let task = "org.asubank.app.chat"

    /// 0 every new message, 1 announcements only, 2 off
    @Published var mode = UserDefaults.standard.integer(forKey: "ab.notify.mode") {
        didSet { UserDefaults.standard.set(mode, forKey: "ab.notify.mode") }
    }
    /// The room on screen, which never notifies
    var openRoom: String?
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
        guard let token = AccountStore.shared.token,
              let r = try? await AccountsAPI.call("latest", [:], token: token),
              let l = decode(ChatLatest.self, r["latest"]) else { return }
        await deliver(l)
    }

    func deliver(_ l: ChatLatest) async {
        // first check after install: remember, don't notify
        let seen = UserDefaults.standard.string(forKey: "ab.notify.lastSeenDate")
        UserDefaults.standard.set(max(seen ?? "", l.created), forKey: "ab.notify.lastSeenDate")
        guard let seen, l.created > seen else { return }
        if l.userId == AccountStore.shared.user?.id || l.room == openRoom { return }
        unread += 1
        if mode == 2 || (mode == 1 && l.announcement != true) { return }
        let c = UNMutableNotificationContent()
        c.title = l.announcement == true ? "📣 \(l.displayName)" : "New message from \(l.displayName)"
        c.subtitle = l.roomName ?? ""
        c.body = l.text
        c.sound = .default
        c.threadIdentifier = l.room
        c.userInfo = ["room": l.room]
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: l.id, content: c, trigger: nil))
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
    func userNotificationCenter(_ c: UNUserNotificationCenter, willPresent n: UNNotification) async -> UNNotificationPresentationOptions { [.banner, .sound, .list] }
    func userNotificationCenter(_ c: UNUserNotificationCenter, didReceive r: UNNotificationResponse) async {
        let room = r.notification.request.content.userInfo["room"] as? String
        await MainActor.run {
            AppState.shared?.tab = .chat
            AppState.shared?.pendingRoom = room
        }
    }
}
