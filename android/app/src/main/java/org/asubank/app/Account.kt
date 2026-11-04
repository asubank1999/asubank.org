package org.asubank.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit

// Accounts, chat and admin, through the "ASU Bank Accounts" Apps Script (tools/apps-script/accounts-api.gs).
// The iOS app and the website use the same API, so accounts, chat and admin actions are shared.

class ApiError(message: String, val needCode: Boolean = false) : Exception(message)

object AccountsApi {
    const val URL = "https://script.google.com/macros/s/AKfycbzXDE0Km2mxL_gAJFqspZeaADtugupeLN0YQlkEbXnrCkSTKBfImW_lLCKF8_SMI0i_/exec"

    /** POSTs {action, ...fields} as text/plain JSON and returns the reply when ok */
    suspend fun call(action: String, fields: Map<String, Any?> = emptyMap(), token: String? = null): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("action", action)
            if (token != null) put("token", token)
            for ((k, v) in fields) when (v) {
                null -> Unit
                is Boolean -> put(k, v)
                is Number -> put(k, v)
                is Collection<*> -> putJsonArray(k) { v.forEach { add(it.toString()) } }
                else -> put(k, v.toString())
            }
        }
        val req = Request.Builder().url(URL)
            .post(body.toString().toRequestBody("text/plain;charset=utf-8".toMediaType()))
            .build()
        val text = try {
            Net.http.newCall(req).execute().use { it.body?.string() }
        } catch (e: Exception) { null } ?: throw ApiError("Couldn't reach ASU Bank. Check your connection and try again.")
        val o = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull()
            ?: throw ApiError("The server sent something unexpected. Try again.")
        if (o.bool("ok")) return@withContext o
        throw ApiError(o.str("error") ?: "Something went wrong.", o.bool("needCode"))
    }
}

fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true
inline fun <reified T> JsonElement?.decodeAs(): T? = this?.let { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() }

// MARK: models

@Serializable
data class AbUser(
    val id: String,
    val username: String,
    val displayName: String = "",
    val role: String = "user",
    val color: String = "#8C1D40",
    val email: String? = null,
    val uniId: String? = null,
    val banned: Boolean? = null,
    val banReason: String? = null,
    val created: String? = null,
    val lastSeen: String? = null,
    val mustReset: Boolean? = null,
    val avatar: String? = null,
    val phone: String? = null,
    val showPhone: Boolean? = null,
    val bio: String? = null,
    val major: String? = null,
    val college: String? = null,
    val badges: List<String> = emptyList(),
    val online: Boolean? = null,
    val blocked: List<String> = emptyList(),
) {
    val isAdmin get() = role == "admin"
    val name get() = displayName.ifEmpty { username }
    val initial get() = name.take(1).uppercase()
}

/** Badges an admin can give (same list as BADGES in accounts-api.gs) */
object Badges {
    val all = listOf(
        Triple("founder", "Founder", "👑"), Triple("moderator", "Moderator", "🛡️"), Triple("support", "Support", "🛟"),
        Triple("helper", "Helper", "🤝"), Triple("donor", "Donor", "💛"), Triple("contributor", "Contributor", "📚"),
        Triple("tutor", "Tutor", "🎓"), Triple("verified", "Verified", "✅"), Triple("vip", "VIP", "⭐"), Triple("developer", "Developer", "💻"),
        Triple("team", "ASU Bank team", "🏦"), Triple("mentor", "Mentor", "🧭"), Triple("ambassador", "Ambassador", "📣"),
        Triple("top_student", "Top student", "🏅"), Triple("class_rep", "Class rep", "🙋"), Triple("translator", "Translator", "🌐"),
        Triple("designer", "Designer", "🎨"), Triple("researcher", "Researcher", "🔬"), Triple("organizer", "Organizer", "🎉"),
        Triple("early_supporter", "Early supporter", "🌱"), Triple("sponsor", "Sponsor", "💎"), Triple("legend", "Legend", "🔥"),
    )
    fun emoji(id: String) = all.firstOrNull { it.first == id }?.third ?: ""
    fun name(id: String) = all.firstOrNull { it.first == id }?.second?.let { tr(it) } ?: id
}

/** The colleges a student can pick on their profile */
val Colleges = listOf("Information Technology", "Engineering", "Pharmacy", "Dentistry", "Nursing", "Allied Medical Sciences", "Business", "Law",
    "Arts & Sciences", "Arts & Design", "Other")

/** What chat screens know about the people in a room */
@Serializable
data class ChatPerson(
    val avatar: String? = null,
    val badges: List<String> = emptyList(),
    val role: String? = null,
    val displayName: String? = null,
    val color: String? = null,
    val online: Boolean? = null,
    val lastSeen: String? = null,
)

@Serializable
data class OnlineUser(
    val id: String,
    val displayName: String = "",
    val avatar: String? = null,
    val color: String? = null,
    val role: String? = null,
    val badges: List<String> = emptyList(),
    val lastSeen: String? = null,
    val online: Boolean = false,
)

@Serializable
data class ChatRoom(
    val id: String,
    val name: String,
    val emoji: String = "💬",
    val about: String = "",
    val adminOnly: Boolean? = null,
    val locked: Boolean? = null,
    val archived: Boolean? = null,
    val pinned: String? = null,
    val order: Int? = null,
)

/** Something the app may notify about (the `events` action) */
@Serializable
data class AbEvent(
    val id: String,
    val type: String = "message",
    val room: String? = null,
    val roomName: String? = null,
    val userId: String? = null,
    val displayName: String? = null,
    val text: String = "",
    val created: String = "",
    val url: String? = null,
    val replyToUserId: String? = null,
)

@Serializable
data class ChatMessage(
    val id: String,
    val room: String,
    val userId: String,
    val username: String,
    val displayName: String = "",
    val role: String = "user",
    val text: String,
    val created: String,
    val replyTo: String? = null,
    val color: String = "#8C1D40",
    val edited: String? = null,
) {
    val instant: Instant get() = runCatching { Instant.parse(created) }.getOrDefault(Instant.now())
}

@Serializable
data class ChatLatest(
    val id: String,
    val room: String,
    val roomName: String? = null,
    val displayName: String = "",
    val username: String = "",
    val userId: String = "",
    val text: String = "",
    val created: String = "",
    val announcement: Boolean? = null,
)

fun hexColor(s: String?): androidx.compose.ui.graphics.Color =
    runCatching { androidx.compose.ui.graphics.Color(0xFF000000 or s!!.removePrefix("#").toLong(16)) }.getOrDefault(Brand.accent)

// MARK: session

/** The signed-in account (token and profile kept in private app storage, so the app opens signed in offline) */
object Account {
    val user = MutableStateFlow<AbUser?>(null)
    val rooms = MutableStateFlow(defaultRooms)
    val signInShown = MutableStateFlow(false)
    var token: String? = null
        private set

    val signedIn get() = user.value != null && token != null
    val isAdmin get() = user.value?.isAdmin == true

    private val defaultRooms get() = listOf(
        ChatRoom("general", "General", "💬", "Say hi, ask anything about ASU"),
        ChatRoom("help", "Study help", "🆘", "Stuck on something? Ask other students"),
        ChatRoom("materials", "Materials", "📚", "Ask for or share study files"),
        ChatRoom("announcements", "Announcements", "📣", "News from the ASU Bank team", adminOnly = true),
    )

    fun init() {
        token = Saved.prefs.getString("account.token", null)
        user.value = if (token == null) null else Saved.prefs.getString("account.user", null)?.let { runCatching { json.decodeFromString<AbUser>(it) }.getOrNull() }
    }

    suspend fun call(action: String, fields: Map<String, Any?> = emptyMap()): JsonObject = try {
        AccountsApi.call(action, fields, token)
    } catch (e: ApiError) {
        val m = e.message ?: ""
        if ("sign in again" in m || "banned" in m || "no longer exists" in m) signOutLocally()
        throw e
    }

    private fun adopt(r: JsonObject) {
        r.str("token")?.let { token = it; Saved.prefs.edit().putString("account.token", it).apply() }
        r["user"].decodeAs<AbUser>()?.let { u ->
            user.value = u
            Saved.prefs.edit().putString("account.user", json.encodeToString(u)).apply()
        }
        r["rooms"].decodeAs<List<ChatRoom>>()?.takeIf { it.isNotEmpty() }?.let { rooms.value = it }
    }

    private fun device() = runCatching { Settings.Secure.getString(Disk.ctx.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull() ?: ""

    suspend fun signIn(login: String, password: String) {
        adopt(AccountsApi.call("login", mapOf("login" to login.trim(), "password" to password, "platform" to "android")))
        refresh()
        ChatNotifier.start(Disk.ctx)
    }

    suspend fun register(username: String, password: String, displayName: String, email: String, uniId: String, code: String) {
        adopt(AccountsApi.call("register", mapOf(
            "username" to username.trim(), "password" to password, "displayName" to displayName.trim(),
            "email" to email.trim(), "uniId" to uniId.trim(), "code" to code.trim(), "platform" to "android", "device" to device(),
        )))
        refresh()
        ChatNotifier.start(Disk.ctx)
    }

    /** Emails the admin code (only for the owner's username, email or university ID) */
    suspend fun sendAdminCode(username: String, email: String, uniId: String): String =
        AccountsApi.call("adminCode", mapOf("username" to username.trim(), "email" to email.trim(), "uniId" to uniId.trim())).str("sentTo") ?: "the owner's email"

    suspend fun refresh() {
        if (token == null) return
        runCatching { adopt(call("me")) }
    }

    suspend fun updateProfile(displayName: String, email: String, uniId: String, phone: String, showPhone: Boolean, bio: String, major: String, college: String) =
        adopt(call("updateProfile", mapOf("displayName" to displayName, "email" to email, "uniId" to uniId,
            "phone" to phone, "showPhone" to showPhone, "bio" to bio.take(160), "major" to major.take(60), "college" to college)))

    /** Uploads a profile photo (a 256 px JPEG, base64), or removes it with null */
    suspend fun setAvatar(jpegBase64: String?) = adopt(
        if (jpegBase64 == null) call("setAvatar", mapOf("remove" to true))
        else call("setAvatar", mapOf("data" to jpegBase64, "type" to "image/jpeg"))
    )

    /** Reloads the room list (after an admin changed it) */
    suspend fun reloadRooms() { runCatching { setRooms(call("rooms")["rooms"]) } }

    fun setRooms(e: JsonElement?) { e.decodeAs<List<ChatRoom>>()?.takeIf { it.isNotEmpty() }?.let { rooms.value = it } }

    suspend fun changePassword(old: String, new: String) { call("changePassword", mapOf("old" to old, "password" to new)); refresh() }

    suspend fun deleteAccount(password: String) { call("deleteAccount", mapOf("password" to password)); signOutLocally() }

    suspend fun signOut() { runCatching { AccountsApi.call("logout", token = token) }; signOutLocally() }

    fun signOutLocally() {
        token = null
        user.value = null
        Saved.prefs.edit().remove("account.token").remove("account.user").apply()
        ChatNotifier.stop(Disk.ctx)
    }
}

// MARK: notifications

/**
 * Notifications for new messages, replies, announcements and uploads, from the server's event feed. The app checks
 * every 25 seconds while it's open and right when it opens, and WorkManager checks about every 15 minutes in the
 * background (there is no server push for a sideloaded APK). Each kind can be turned off, and rooms can be muted.
 */
object ChatNotifier {
    private const val CHANNEL = "chat"
    private const val CHANNEL_ANN = "announcements"
    private const val CHANNEL_REPLY = "replies"
    private const val CHANNEL_UP = "uploads"
    private const val WORK = "chat-check"

    val messages = MutableStateFlow(true)
    val replies = MutableStateFlow(true)
    val announcements = MutableStateFlow(true)
    val uploads = MutableStateFlow(true)
    val muted = MutableStateFlow<Set<String>>(emptySet())
    /** The room on screen, which never notifies */
    @Volatile var openRoom: String? = null
    /** Room to open from a tapped notification */
    val pendingRoom = MutableStateFlow<String?>(null)

    fun init(ctx: Context) {
        val p = Saved.prefs
        // 1.5 had one setting: 0 every message, 1 announcements only, 2 off
        val old = p.getInt("notify.mode", 0)
        messages.value = p.getBoolean("notify.messages", old == 0)
        replies.value = p.getBoolean("notify.replies", old != 2)
        announcements.value = p.getBoolean("notify.announcements", old != 2)
        uploads.value = p.getBoolean("notify.uploads", old != 2)
        muted.value = p.getStringSet("notify.muted", emptySet())?.toSet() ?: emptySet()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Chat messages"), NotificationManager.IMPORTANCE_DEFAULT).apply { description = tr("New messages in the student chat") })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_REPLY, tr("Replies to me"), NotificationManager.IMPORTANCE_HIGH).apply { description = tr("When someone replies to your message") })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ANN, tr("Announcements"), NotificationManager.IMPORTANCE_HIGH).apply { description = tr("News from the ASU Bank team") })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_UP, tr("New uploaded files"), NotificationManager.IMPORTANCE_DEFAULT).apply { description = tr("When a new file is uploaded to ASU Bank") })
        }
    }

    fun set(key: String, v: Boolean) {
        when (key) {
            "messages" -> messages.value = v
            "replies" -> replies.value = v
            "announcements" -> announcements.value = v
            "uploads" -> uploads.value = v
        }
        Saved.prefs.edit().putBoolean("notify.$key", v).apply()
    }

    fun setMuted(room: String, mute: Boolean) {
        muted.value = if (mute) muted.value + room else muted.value - room
        Saved.prefs.edit().putStringSet("notify.muted", muted.value).apply()
    }

    fun start(ctx: Context) {
        if (!Account.signedIn) return
        val req = PeriodicWorkRequestBuilder<ChatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UPDATE so phones that had the 1.5 worker get this one
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun stop(ctx: Context) { runCatching { WorkManager.getInstance(ctx).cancelUniqueWork(WORK) } }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun check(ctx: Context) = lock.withLock {
        val token = Account.token ?: return@withLock
        val seen = Saved.prefs.getString("notify.lastSeen", null)
        val r = runCatching { AccountsApi.call("events", mapOf("since" to (seen ?: "")), token = token) }.getOrNull() ?: return@withLock
        val events = r["events"].decodeAs<List<AbEvent>>() ?: emptyList()
        val newest = events.maxOfOrNull { it.created } ?: ""
        // first check after install: remember, don't notify
        if (seen == null) { Saved.prefs.edit().putString("notify.lastSeen", maxOf(newest, r.str("now") ?: "")).apply(); return@withLock }
        if (newest > seen) Saved.prefs.edit().putString("notify.lastSeen", newest).apply()
        events.filter { it.created > seen }.forEach { deliver(ctx, it) }
    }

    /** Whether the user wants a notification for this event */
    fun wants(e: AbEvent): Boolean {
        val me = Account.user.value?.id
        if (e.userId != null && e.userId == me) return false
        if (e.userId != null && e.userId in Account.user.value?.blocked.orEmpty()) return false
        return when (e.type) {
            "upload" -> uploads.value
            "announcement" -> announcements.value
            else -> when {
                e.room == openRoom -> false
                replies.value && me != null && e.replyToUserId == me -> true
                else -> messages.value && e.room !in muted.value
            }
        }
    }

    private fun deliver(ctx: Context, e: AbEvent) {
        if (!wants(e) || !canNotify(ctx)) return
        val me = Account.user.value?.id
        val reply = e.type == "message" && me != null && e.replyToUserId == me
        val who = e.displayName ?: ""
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (e.type == "upload") intent.putExtra("openPath", "/guides/latest") else intent.putExtra("openRoom", e.room)
        val tap = PendingIntent.getActivity(ctx, e.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel = when { e.type == "upload" -> CHANNEL_UP; e.type == "announcement" -> CHANNEL_ANN; reply -> CHANNEL_REPLY; else -> CHANNEL }
        val title = when {
            e.type == "upload" -> tr("📄 New upload on ASU Bank")
            e.type == "announcement" -> "📣 $who"
            reply -> tr("%s replied to you").format(who)
            else -> tr("New message from %s").format(who)
        }
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF8C1D40.toInt())
            .setContentTitle(title)
            .setContentText(e.text)
            .setSubText(if (e.type == "upload") null else e.roomName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.text))
            .setGroup(e.room ?: e.type)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(e.id.hashCode(), n) }
    }
}

class ChatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Disk.ctx = applicationContext
        Saved.init(applicationContext)
        Lang.init(applicationContext)
        if (Account.token == null) Account.init()
        ChatNotifier.init(applicationContext)
        ChatNotifier.check(applicationContext)
        return Result.success()
    }
}

/** Moves an upload to the Drive trash (admins; only files in the upload folder) */
suspend fun adminDeleteUpload(url: String) {
    val id = AB.driveId(url) ?: throw ApiError("This isn't a Google Drive file.")
    Account.call("adminDeleteUpload", mapOf("fileId" to id))
}

fun JsonArray?.objects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject } ?: emptyList()
fun JsonElement?.prim(): String? = runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull()
