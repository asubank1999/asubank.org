package org.asubank.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** One line in the Notifications tab, from the accounts API (action "notifications") */
@Serializable
data class Notice(
    val id: String,
    val type: String,
    val created: String,
    val text: String? = null,
    val name: String? = null,
    val userId: String? = null,
    val avatar: String? = null,
    val color: String? = null,
    val kind: String? = null,
    val url: String? = null,
)

/** The list and what's unread. Kept in one place so the tab's badge and the screen agree. */
object Notices {
    private const val READ_KEY = "notices.lastRead"
    val items = MutableStateFlow<List<Notice>>(emptyList())
    val loaded = MutableStateFlow(false)
    val failed = MutableStateFlow<String?>(null)
    val lastRead = MutableStateFlow("")

    suspend fun load() {
        if (Account.user.value == null) { items.value = emptyList(); loaded.value = false; return }
        if (lastRead.value.isEmpty()) lastRead.value = Saved.prefs.getString(READ_KEY, "") ?: ""
        if (items.value.isEmpty()) Account.saved("notifications")?.let { items.value = it["items"].decodeAs<List<Notice>>().orEmpty(); loaded.value = true }
        try {
            items.value = Account.callSaving("notifications", mapOf("social2" to true))["items"].decodeAs<List<Notice>>().orEmpty()
            failed.value = null
            // the first time, what's already there isn't "new"
            if (lastRead.value.isEmpty()) markRead()
        } catch (e: ApiError) { failed.value = e.message }
        loaded.value = true
    }

    fun reset() {
        items.value = emptyList()
        loaded.value = false
        lastRead.value = ""
        Saved.prefs.edit().remove(READ_KEY).apply()
    }

    fun markRead() {
        val newest = items.value.maxOfOrNull { it.created } ?: return
        if (newest <= lastRead.value) return
        lastRead.value = newest
        Saved.prefs.edit().putString(READ_KEY, newest).apply()
    }
}

private fun ago(iso: String): String {
    val t = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val d = Duration.between(t, Instant.now())
    return when {
        d.toMinutes() < 1 -> tr("Just now")
        d.toHours() < 1 -> trf("%d min ago", d.toMinutes())
        d.toDays() < 1 -> trf("%d h ago", d.toHours())
        else -> trf("%d d ago", d.toDays())
    }
}

private fun emoji(type: String) = when (type) {
    "upload" -> "📄"
    "announcement" -> "📣"
    "report" -> "🚩"
    "suggestion" -> "💡"
    "group_approved" -> "✅"
    "group_rejected" -> "❌"
    "group_request", "post_comment" -> "💬"
    else -> "🔔"
}

private fun title(n: Notice) = when (n.type) {
    "friend_request" -> tr("New friend request")
    "friend_accept" -> tr("You have a new friend")
    "poke" -> n.name ?: ""
    "post_comment" -> trf("%s commented on your post", n.name ?: "")
    "upload" -> tr("New upload on ASU Bank")
    "announcement" -> tr("Announcement")
    "group_request" -> tr("New group request")
    "group_approved" -> tr("Your group was accepted")
    "group_rejected" -> tr("Your group wasn't accepted")
    "report" -> tr("New report")
    "suggestion" -> tr("New suggestion")
    else -> n.name ?: ""
}

private fun detail(n: Notice): String {
    val who = n.name ?: ""
    return when (n.type) {
        "friend_request" -> trf("%s wants to be your friend", who)
        "friend_accept" -> trf("%s accepted your friend request", who)
        "poke" -> Social.pokeText(n.kind ?: "poke", who, n.text)
        "group_request" -> trf("%s asked to add: %s", who, n.text ?: "")
        else -> n.text ?: ""
    }
}

@Composable
fun NotificationsScreen(back: Boolean = false) {
    val nav = LocalNav.current
    val user by Account.user.collectAsState()
    val list by Notices.items.collectAsState()
    val loaded by Notices.loaded.collectAsState()
    val failed by Notices.failed.collectAsState()
    /** What was unread when the screen opened, so the dots stay while you're looking */
    var readBefore by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(user?.id) {
        Notices.load()
        if (readBefore == null) readBefore = Notices.lastRead.value
        Notices.markRead()
    }

    fun open(n: Notice) {
        when (n.type) {
            "friend_request" -> nav.navigate(Routes.FRIENDS)
            "post_comment" -> nav.navigate(Routes.FEED)
            "friend_accept", "poke" -> n.userId?.takeIf { it.isNotEmpty() }?.let { nav.navigate("profile/$it") }
            "upload" -> n.url?.takeIf { it.isNotEmpty() }?.let { nav.openMaterial(Material(u = it, t = n.text ?: "")) }
            "announcement" -> ChatNotifier.pendingRoom.value = "announcements"
            "group_request" -> nav.navigate("adminGroups")
            "report" -> nav.navigate("adminReports")
            "suggestion" -> nav.navigate("adminSuggestions")
        }
    }

    Screen(tr("Notifications"), back = back, large = !back) { pad ->
        if (user == null) Box(Modifier.padding(pad)) { SignInPrompt("Sign in to see your notifications", "Friend requests, pokes, new uploads and announcements will show up here.") }
        else LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            failed?.takeIf { list.isEmpty() }?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (!loaded) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (list.isEmpty() && failed == null) item { EmptyState(Icons.Filled.Notifications, "Nothing yet", "Friend requests, pokes, new uploads and announcements will show up here.") }
            items(list.size) { i ->
                val n = list[i]
                val before = readBefore
                Row(
                    Modifier.fillMaxWidth().clickable { open(n) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!n.userId.isNullOrEmpty()) Avatar(n.name ?: "", n.color ?: "#8C1D40", 40.dp, false, n.avatar)
                    else Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(emoji(n.type), fontSize = 18.sp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title(n), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val b = detail(n)
                        if (b.isNotEmpty()) Text(b, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(ago(n.created), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (before != null && n.created > before) Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                Divider()
            }
        }
    }
}
