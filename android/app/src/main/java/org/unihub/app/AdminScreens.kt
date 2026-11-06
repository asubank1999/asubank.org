package org.unihub.app

import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Flag
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The user an admin opened from the list (passed to the next screen) */
object AdminSelection { var user: AbUser? = null }

private fun short(iso: String?): String = iso?.let {
    runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)) }.getOrDefault(it)
} ?: "–"

@Composable
fun AdminTab() {
    fun pending(r: JsonObject?) = r?.get("pending").prim()?.toDoubleOrNull()?.toInt() ?: 0
    var pendingGroups by remember { mutableStateOf(pending(Account.saved("adminGroupRequests", "open"))) }
    var pendingSuggestions by remember { mutableStateOf(pending(Account.saved("adminSuggestions", "open"))) }
    LaunchedEffect(Unit) { runCatching { pendingGroups = pending(Account.callSaving("adminGroupRequests", mapOf("all" to false), "open")) } }
    LaunchedEffect(Unit) { runCatching { pendingSuggestions = pending(Account.callSaving("adminSuggestions", mapOf("all" to false), "open")) } }
    val nav = LocalNav.current
    fun readStats(r: JsonObject?): Map<String, Int> =
        (r?.get("stats") as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.intOrNull?.let { k to it } }?.toMap() ?: emptyMap()
    var stats by remember { mutableStateOf(readStats(Account.saved("adminStats"))) }
    var error by remember { mutableStateOf<String?>(null) }
    var announcing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            stats = readStats(Account.callSaving("adminStats"))
        } catch (e: ApiError) { error = e.message }
    }
    val tiles = listOf(
        "users" to "Students", "online" to "Online now", "activeToday" to "Active today", "activeWeek" to "Active this week",
        "newWeek" to "New this week", "messagesToday" to "Messages today", "banned" to "Banned", "rooms" to "Chat rooms",
    )

    Screen("Admin", back = false, large = true) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (k, l) ->
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(stats[k]?.toString() ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
                                        Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Group(header = "Manage") {
                    ListRow("Group requests", icon = Icons.Filled.AddComment, trailing = if (pendingGroups > 0) "$pendingGroups" else null) { nav.navigate("adminGroups") }
                    Divider()
                    ListRow("Suggestions", icon = Icons.Filled.Lightbulb, trailing = if (pendingSuggestions > 0) "$pendingSuggestions" else null) { nav.navigate("adminSuggestions") }
                    Divider()
                    ListRow("Reports", icon = Icons.Filled.Flag) { nav.navigate("adminReports") }
                    Divider()
                    ListRow("Terms records", icon = Icons.Filled.Policy) { nav.navigate("adminTerms") }
                    Divider()
                    ListRow("Users", "Ban, reset passwords, make admins", icon = Icons.Filled.People) { nav.navigate("adminUsers") }
                    Divider()
                    ListRow("Send an announcement", "Notifies everyone", icon = Icons.Filled.Campaign) { announcing = true }
                    Divider()
                    ListRow("Chat rooms", "Add, rename, lock, archive or delete", icon = Icons.Filled.Forum) { nav.navigate("adminRooms") }
                    Divider()
                    ListRow("Uploads", "Tap the bin, or hold any file, to delete it", icon = Icons.Filled.CloudUpload) { nav.openPage("/guides/latest", "Latest uploads") }
                    Divider()
                    ListRow("Student submissions", icon = Icons.Filled.ListAlt) { nav.navigate("submissions?admin=1") }
                    Divider()
                    ListRow("Saved materials", icon = Icons.Filled.Star) { nav.navigate("savedList") }
                    Divider()
                    ListRow("Admin activity", icon = Icons.Filled.History) { nav.navigate("adminLog") }
                }
            }
            item {
                Group(header = "Chat moderation", footer = "Open any chat room and long-press a message to pin, delete it or ban its sender. Open a user to give them badges.") {
                    Account.rooms.value.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        ListRow(r.name, emoji = r.emoji) { nav.navigate("room/${r.id}") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (announcing) AnnounceDialog { announcing = false }
}

@Composable
private fun AnnounceDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Announcement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Posted in 📣 Announcements. Everyone with notifications on gets it.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    try { Account.call("send", mapOf("room" to "announcements", "text" to text.trim())); Toast.makeText(ctx, "Announcement sent", Toast.LENGTH_SHORT).show(); onClose() }
                    catch (e: ApiError) { err = e.message }
                    busy = false
                }
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
fun AdminUsersScreen() {
    val nav = LocalNav.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    var users by remember { mutableStateOf(Account.saved("adminUsers")?.get("users").decodeAs<List<AbUser>>().orEmpty()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(if (query.isEmpty()) 0 else 400)
        loading = true
        try {
            val r = if (query.isEmpty()) Account.callSaving("adminUsers", mapOf("q" to "")) else Account.call("adminUsers", mapOf("q" to query))
            users = r["users"].decodeAs<List<AbUser>>().orEmpty(); error = null
        }
        catch (e: ApiError) { error = e.message }
        loading = false
    }
    val shown = when (filter) { 1 -> users.filter { it.isAdmin }; 2 -> users.filter { it.banned == true }; else -> users }

    Screen("Users") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Username, name, email or ID") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Admins", "Banned").forEachIndexed { i, l -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) }) }
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (loading && users.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { ABDots() } }
            item {
                Group(footer = trf("%d accounts", shown.size)) {
                    shown.forEachIndexed { i, u ->
                        if (i > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(u.name, u.color, 36.dp, u.isAdmin, u.avatar, u.online)
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(u.name, fontWeight = FontWeight.SemiBold)
                                    BadgeRow(u.badges, compact = true)
                                }
                                Text("@${u.username}" + (u.email?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (u.banned == true) Badge("BANNED", MaterialTheme.colorScheme.error)
                            TextButton(onClick = { AdminSelection.user = u; nav.navigate("adminUser") }) { Text("Open") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUserScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = AdminSelection.user ?: return
    var banned by remember { mutableStateOf(user.banned == true) }
    var admin by remember { mutableStateOf(user.isAdmin) }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var temp by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var badges by remember { mutableStateOf(user.badges) }
    val me = Account.user.value?.id == user.id

    fun act(action: String, fields: Map<String, Any?> = emptyMap(), done: () -> Unit) {
        busy = true
        scope.launch {
            try { Account.call(action, fields + ("userId" to user.id)); done(); error = null } catch (e: ApiError) { error = e.message }
            busy = false
        }
    }

    Screen("@${user.username}") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Avatar(user.name, user.color, 72.dp, admin, user.avatar, user.online)
                    Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    BadgeRow(badges)
                    if (banned) Badge("BANNED", MaterialTheme.colorScheme.error)
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            item {
                Group(header = "Details") {
                    ListRow("Username", trailing = user.username); Divider()
                    ListRow("University email", trailing = user.email?.ifEmpty { null } ?: "–"); Divider()
                    ListRow("University ID", trailing = user.uniId?.ifEmpty { null } ?: "–"); Divider()
                    ListRow("Phone", trailing = user.phone?.ifEmpty { null } ?: "–"); Divider()
                    if (!user.bio.isNullOrEmpty()) { ListRow("About", user.bio); Divider() }
                    if (!user.college.isNullOrEmpty()) { ListRow("College", trailing = tr(user.college)); Divider() }
                    if (!user.major.isNullOrEmpty()) { ListRow("Major", user.major); Divider() }
                    ListRow("Joined", trailing = short(user.created)); Divider()
                    ListRow("Last seen", trailing = if (user.online == true) tr("Online now") else short(user.lastSeen))
                    if (!user.banReason.isNullOrEmpty()) { Divider(); ListRow("Ban reason", user.banReason) }
                }
            }
            item {
                Group(header = "Badges", footer = "Badges show on their profile and next to their name in the chat.") {
                    Badges.all.forEachIndexed { i, (id, name, emoji) ->
                        if (i > 0) Divider()
                        ListRow("$emoji  ${tr(name)}", trailing = if (id in badges) "✓" else null) {
                            val next = if (id in badges) badges - id else badges + id
                            act("adminSetBadges", mapOf("badges" to next)) { badges = next }
                        }
                    }
                }
            }
            item {
                Group(header = "Actions") {
                    if (banned) ListRow("Unban", icon = Icons.Filled.CheckCircle) { act("adminBan", mapOf("banned" to false)) { banned = false } }
                    else if (!me) {
                        OutlinedTextField(reason, { reason = it }, label = { Text("Ban reason (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                        ListRow("Ban", icon = Icons.Filled.Block, titleColor = MaterialTheme.colorScheme.error) { act("adminBan", mapOf("banned" to true, "reason" to reason)) { banned = true } }
                    }
                    Divider()
                    ListRow("Reset password", "Gives them a temporary password", icon = Icons.Filled.Key) {
                        busy = true
                        scope.launch {
                            try { temp = Account.call("adminResetPassword", mapOf("userId" to user.id)).str("password"); error = null } catch (e: ApiError) { error = e.message }
                            busy = false
                        }
                    }
                    if (!me) {
                        Divider()
                        ListRow(if (admin) "Remove admin" else "Make admin", icon = Icons.Filled.Shield) { act("adminSetRole", mapOf("role" to if (admin) "user" else "admin")) { admin = !admin } }
                        Divider()
                        ListRow("Delete account", icon = Icons.Filled.Delete, titleColor = MaterialTheme.colorScheme.error) { confirmDelete = true }
                    }
                }
            }
            if (busy) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { ABDots() } }
        }
    }

    temp?.let { p ->
        AlertDialog(
            onDismissRequest = { temp = null },
            title = { Text("Temporary password") },
            text = { Text(trf("Send this to @%s. They should change it after signing in.", user.username) + "\n\n$p", fontWeight = FontWeight.Medium) },
            confirmButton = {
                TextButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Password", p))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    temp = null
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { temp = null }) { Text("Done") } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(trf("Delete @%s?", user.username)) },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; act("adminDeleteUser") { nav.popBackStack() } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
fun AdminLogScreen() {
    var log by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { log = (Account.call("adminLog")["log"] as? JsonArray).objects() } catch (e: ApiError) { error = e.message }
    }
    Screen("Admin activity") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            item {
                Group {
                    if (log.isEmpty()) ListRow("Nothing yet")
                    log.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        ListRow("${r.str("action") ?: ""} · ${r.str("target") ?: ""}", listOfNotNull(r.str("detail")?.ifEmpty { null }, short(r.str("time"))).joinToString("\n"))
                    }
                }
            }
        }
    }
}

/** Admins: all chat rooms, tap one to edit it, + to add */
@Composable
fun AdminRoomsScreen() {
    val rooms by Account.rooms.collectAsState()
    var editing by remember { mutableStateOf<ChatRoom?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Account.reloadRooms() }
    Screen("Chat rooms", actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, tr("Add a room")) } }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(footer = "Locked rooms are read-only. Archived rooms are hidden from students but kept.") {
                    rooms.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        val flags = listOfNotNull(
                            if (r.adminOnly == true) tr("admins post") else null, if (r.locked == true) "🔒" else null, if (r.archived == true) tr("archived") else null,
                        ).joinToString(" · ")
                        ListRow(r.name, r.about, emoji = r.emoji, trailing = flags.ifEmpty { null }) { editing = r }
                    }
                }
            }
        }
    }
    if (adding) RoomEditor(null) { adding = false }
    editing?.let { r -> RoomEditor(r) { editing = null } }
}

/** Create or change a room (admins) */
@Composable
fun RoomEditor(room: ChatRoom?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(room?.name ?: "") }
    var about by remember { mutableStateOf(room?.about ?: "") }
    var emoji by remember { mutableStateOf(room?.emoji ?: "💬") }
    var adminOnly by remember { mutableStateOf(room?.adminOnly == true) }
    var locked by remember { mutableStateOf(room?.locked == true) }
    var archived by remember { mutableStateOf(room?.archived == true) }
    var order by remember { mutableStateOf(room?.order ?: Account.rooms.value.size) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val emojis = listOf("💬", "🆘", "📚", "💊", "💻", "📈", "📣", "🎓", "🧪", "⚖️", "🎨", "🏀", "🎮", "🕌", "☕", "🔬", "🧮", "🌍")

    fun run(action: String, fields: Map<String, Any?>) {
        scope.launch {
            try {
                val r = Account.call(action, fields)
                if (r["rooms"] != null) Account.setRooms(r["rooms"]) else Account.reloadRooms()
                onClose()
            } catch (e: ApiError) { err = e.message }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (room == null) "New room" else "Edit room") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(about, { about = it.take(100) }, label = { Text("What it's for") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojis.forEach { e ->
                        Text(
                            e, fontSize = 22.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (e == emoji) accent.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { emoji = e }.padding(6.dp),
                        )
                    }
                }
                SwitchRow(tr("Only admins can post"), null, adminOnly) { adminOnly = it }
                SwitchRow(tr("Locked (read-only for now)"), null, locked) { locked = it }
                SwitchRow(tr("Archived (hidden from students)"), null, archived) { archived = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Position %d").format(order + 1), Modifier.weight(1f))
                    TextButton(onClick = { if (order > 0) order-- }) { Text("−") }
                    TextButton(onClick = { order++ }) { Text("+") }
                }
                if (room != null) {
                    TextButton(onClick = { confirm = "clear" }) { Text("Clear all messages", color = MaterialTheme.colorScheme.error) }
                    if (room.id != "announcements") TextButton(onClick = { confirm = "delete" }) { Text("Delete room", color = MaterialTheme.colorScheme.error) }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val f = mutableMapOf<String, Any?>("name" to name, "about" to about, "emoji" to emoji, "adminOnly" to adminOnly,
                    "locked" to locked, "archived" to archived, "order" to order)
                room?.let { f["id"] = it.id }
                run("adminRoomSave", f)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (c == "delete") "Delete this room?" else "Clear every message in this room?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (c == "delete") run("adminRoomDelete", mapOf("id" to room?.id)) else run("adminClearRoom", mapOf("room" to room?.id))
                }) { Text(if (c == "delete") "Delete" else "Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** Quick lock / archive from a long-press on a room */
suspend fun quickRoom(r: ChatRoom, locked: Boolean? = null, archived: Boolean? = null) {
    runCatching {
        val res = Account.call("adminRoomSave", mapOf("id" to r.id, "name" to r.name, "emoji" to r.emoji, "about" to r.about, "adminOnly" to (r.adminOnly == true),
            "locked" to (locked ?: (r.locked == true)), "archived" to (archived ?: (r.archived == true)), "order" to (r.order ?: 0)))
        Account.setRooms(res["rooms"])
    }
}
