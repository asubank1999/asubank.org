package org.asubank.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The Chat tab: rooms, or a sign-in prompt */
@Composable
fun ChatTab() {
    val nav = LocalNav.current
    val user by Account.user.collectAsState()
    val rooms by Account.rooms.collectAsState()
    val pending by ChatNotifier.pendingRoom.collectAsState()
    LaunchedEffect(pending, user) {
        val r = pending ?: return@LaunchedEffect
        if (user != null) { ChatNotifier.pendingRoom.value = null; nav.navigate("room/$r") }
    }
    LaunchedEffect(user) { if (user != null) Account.refresh() }

    Screen("Chat", back = false, large = true, actions = {
        if (user != null) IconButton(onClick = { nav.navigate("account") }) { Avatar(user!!.name, user!!.color, 30.dp, user!!.isAdmin) }
    }) { pad ->
        val u = user
        if (u == null) { Box(Modifier.padding(pad)) { SignInPrompt() }; return@Screen }
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(footer = "Be kind and helpful. Don't share exam answers during an exam, private info or anything illegal. Admins can remove messages and ban accounts.") {
                    rooms.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        ListRow(r.name, r.about, emoji = r.emoji, badge = if (r.adminOnly == true) "ADMINS POST" else null) { nav.navigate("room/${r.id}") }
                    }
                }
            }
            item {
                Text(
                    "Signed in as @${u.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> d.format(dayFmt) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRoomScreen(roomId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rooms by Account.rooms.collectAsState()
    val user by Account.user.collectAsState()
    val room = rooms.firstOrNull { it.id == roomId } ?: ChatRoom(roomId, roomId.replaceFirstChar { it.uppercase() })
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var sending by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var banTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val list = rememberLazyListState()
    val canPost = room.adminOnly != true || Account.isAdmin

    suspend fun fetch() {
        val f = mutableMapOf<String, Any?>("room" to room.id)
        messages.lastOrNull()?.let { f["after"] = it.id }
        try {
            val r = Account.call("chat", f)
            val gone = (r["deleted"] as? JsonArray)?.mapNotNull { it.prim() }?.toSet() ?: emptySet()
            if (gone.isNotEmpty()) messages.removeAll { it.id in gone }
            val have = messages.map { it.id }.toSet()
            val new = r["messages"].decodeAs<List<ChatMessage>>().orEmpty().filter { it.id !in have }
            if (new.isNotEmpty()) messages.addAll(new)
            r["latest"].decodeAs<ChatLatest>()?.let { ChatNotifier.markSeen(it.created) }
            error = null
        } catch (e: ApiError) { error = e.message }
        loading = false
    }

    DisposableEffect(room.id) {
        ChatNotifier.openRoom = room.id
        onDispose { if (ChatNotifier.openRoom == room.id) ChatNotifier.openRoom = null }
    }
    // about every 3 seconds while the room is on screen
    LaunchedEffect(room.id) { while (true) { fetch(); delay(3000) } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1) }

    fun send() {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        sending = true
        scope.launch {
            try {
                val r = Account.call("send", mapOf("room" to room.id, "text" to t, "replyTo" to (replyTo?.id ?: "")))
                r["message"].decodeAs<ChatMessage>()?.let { m -> if (messages.none { it.id == m.id }) messages.add(m) }
                text = ""; replyTo = null; error = null
            } catch (e: ApiError) { error = e.message }
            sending = false
        }
    }

    Screen("${room.emoji} ${room.name}") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
                if (loading && messages.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else if (messages.isEmpty()) EmptyState(Icons.Filled.Person, "No messages yet", if (canPost) "Be the first to say something." else "Announcements from the team show up here.")
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(messages.size, key = { messages[it].id }) { i ->
                        val m = messages[i]
                        val day = m.instant.atZone(ZoneId.systemDefault()).toLocalDate()
                        val prev = messages.getOrNull(i - 1)
                        val prevDay = prev?.instant?.atZone(ZoneId.systemDefault())?.toLocalDate()
                        if (day != prevDay) Text(
                            dayLabel(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        )
                        val mine = m.userId == user?.id
                        val grouped = prev != null && prev.userId == m.userId && day == prevDay
                        val reply = m.replyTo?.let { id -> messages.firstOrNull { it.id == id } }
                        Box {
                            Bubble(m, mine, grouped, reply, Modifier.combinedClickable(onClick = {}, onLongClick = { menuFor = m.id }))
                            DropdownMenu(expanded = menuFor == m.id, onDismissRequest = { menuFor = null }) {
                                if (canPost) DropdownMenuItem(text = { Text("Reply") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) }, onClick = { replyTo = m; menuFor = null })
                                DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Message", m.text))
                                    menuFor = null
                                })
                                if (mine || Account.isAdmin) DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                                    menuFor = null
                                    scope.launch {
                                        try { Account.call("deleteMessage", mapOf("id" to m.id)); messages.removeAll { it.id == m.id } } catch (e: ApiError) { error = e.message }
                                    }
                                })
                                if (Account.isAdmin && !mine) DropdownMenuItem(text = { Text("Ban @${m.username}") }, leadingIcon = { Icon(Icons.Filled.Block, null) }, onClick = { banTarget = m; menuFor = null })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                if (!canPost) Text(
                    "Only admins post here. You'll get a notification for each announcement.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 8.dp),
                ) else Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    replyTo?.let { r ->
                        Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(30.dp).background(accent, RoundedCornerShape(2.dp)))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text("Replying to ${r.displayName.ifEmpty { r.username }}", style = MaterialTheme.typography.labelMedium, color = accent)
                                Text(r.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { replyTo = null }) { Icon(Icons.Filled.Close, "Cancel reply") }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            text, { if (it.length <= 1000) text = it }, placeholder = { Text("Message") },
                            maxLines = 5, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f),
                        )
                        FilledIconButton(onClick = ::send, enabled = text.isNotBlank() && !sending, modifier = Modifier.size(48.dp)) {
                            if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    }
                }
            }
        }
    }

    banTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { banTarget = null },
            title = { Text("Ban @${m.username}?") },
            text = { Text("They can't sign in or chat until an admin unbans them in the Admin tab.") },
            confirmButton = {
                TextButton(onClick = {
                    banTarget = null
                    scope.launch {
                        try {
                            Account.call("adminBan", mapOf("userId" to m.userId, "banned" to true, "reason" to "Removed from the chat by an admin"))
                            Toast.makeText(ctx, "@${m.username} is banned", Toast.LENGTH_SHORT).show()
                        } catch (e: ApiError) { error = e.message }
                    }
                }) { Text("Ban", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { banTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean, grouped: Boolean, reply: ChatMessage?, modifier: Modifier) {
    val admin = m.role == "admin"
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = if (grouped) 0.dp else 6.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            if (grouped) Spacer(Modifier.width(34.dp)) else Avatar(m.displayName.ifEmpty { m.username }, m.color, 30.dp, admin)
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp))
                .background(if (mine) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            if (!mine && !grouped) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(m.displayName.ifEmpty { m.username }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = hexColor(m.color))
                if (admin) Text("ADMIN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                    modifier = Modifier.clip(CircleShape).background(Brand.gold).padding(horizontal = 5.dp, vertical = 1.dp))
            }
            reply?.let { r ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Box(Modifier.width(3.dp).height(32.dp).background(fg.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(r.displayName.ifEmpty { r.username }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = 0.85f))
                        Text(r.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg.copy(alpha = 0.75f))
                    }
                }
            }
            Text(m.text, color = fg, style = MaterialTheme.typography.bodyLarge)
            Text(
                m.instant.atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt),
                fontSize = 10.sp, color = fg.copy(alpha = 0.65f), modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
