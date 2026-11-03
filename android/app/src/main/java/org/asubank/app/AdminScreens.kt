package org.asubank.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.Text
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
    val nav = LocalNav.current
    var stats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var announcing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val s = Account.call("adminStats")["stats"] as? JsonObject
            stats = s?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.intOrNull?.let { k to it } }?.toMap() ?: emptyMap()
        } catch (e: ApiError) { error = e.message }
    }
    val tiles = listOf(
        "users" to "Students", "activeToday" to "Active today", "activeWeek" to "Active this week",
        "newWeek" to "New this week", "messagesToday" to "Messages today", "banned" to "Banned",
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
                    ListRow("Users", "Ban, reset passwords, make admins", icon = Icons.Filled.People) { nav.navigate("adminUsers") }
                    Divider()
                    ListRow("Send an announcement", "Notifies everyone", icon = Icons.Filled.Campaign) { announcing = true }
                    Divider()
                    ListRow("Uploads", "Tap the bin next to an upload to delete it", icon = Icons.Filled.CloudUpload) { nav.openPage("/guides/latest", "Latest uploads") }
                    Divider()
                    ListRow("Student submissions", icon = Icons.Filled.ListAlt) { nav.navigate("submissions?admin=1") }
                    Divider()
                    ListRow("Saved materials", icon = Icons.Filled.Star) { nav.navigate("savedList") }
                    Divider()
                    ListRow("Admin activity", icon = Icons.Filled.History) { nav.navigate("adminLog") }
                }
            }
            item {
                Group(header = "Chat moderation", footer = "Open any chat room and long-press a message to delete it or ban its sender.") {
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
    var users by remember { mutableStateOf<List<AbUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(if (query.isEmpty()) 0 else 400)
        loading = true
        try { users = Account.call("adminUsers", mapOf("q" to query))["users"].decodeAs<List<AbUser>>().orEmpty(); error = null }
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
            if (loading && users.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            item {
                Group(footer = "${shown.size} account${if (shown.size == 1) "" else "s"}") {
                    shown.forEachIndexed { i, u ->
                        if (i > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(u.name, u.color, 36.dp, u.isAdmin)
                            Column(Modifier.weight(1f)) {
                                Text(u.name, fontWeight = FontWeight.SemiBold)
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
                    Avatar(user.name, user.color, 72.dp, admin)
                    Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (banned) Badge("BANNED", MaterialTheme.colorScheme.error)
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            item {
                Group(header = "Details") {
                    ListRow("Username", trailing = user.username); Divider()
                    ListRow("University email", trailing = user.email?.ifEmpty { null } ?: "–"); Divider()
                    ListRow("University ID", trailing = user.uniId?.ifEmpty { null } ?: "–"); Divider()
                    ListRow("Joined", trailing = short(user.created)); Divider()
                    ListRow("Last seen", trailing = short(user.lastSeen))
                    if (!user.banReason.isNullOrEmpty()) { Divider(); ListRow("Ban reason", user.banReason) }
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
            if (busy) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
    }

    temp?.let { p ->
        AlertDialog(
            onDismissRequest = { temp = null },
            title = { Text("Temporary password") },
            text = { Text("Send this to @${user.username}. They should change it after signing in.\n\n$p", fontWeight = FontWeight.Medium) },
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
        title = { Text("Delete @${user.username}?") },
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
