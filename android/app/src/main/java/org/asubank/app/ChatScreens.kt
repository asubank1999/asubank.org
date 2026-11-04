package org.asubank.app

import androidx.compose.material.icons.filled.Flag
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Who used the chat lately (the `online` action), shared by the Chat tab and the online list */
object Presence {
    val users = kotlinx.coroutines.flow.MutableStateFlow<List<OnlineUser>>(emptyList())
    val count = kotlinx.coroutines.flow.MutableStateFlow(0)
    suspend fun load() {
        runCatching {
            val r = Account.call("online")
            users.value = r["users"].decodeAs<List<OnlineUser>>().orEmpty()
            count.value = r.str("onlineCount")?.toIntOrNull() ?: users.value.count { it.online }
        }
    }
}

/** The Chat tab: rooms, or a sign-in prompt */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatTab() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsState()
    val rooms by Account.rooms.collectAsState()
    val pending by ChatNotifier.pendingRoom.collectAsState()
    val online by Presence.users.collectAsState()
    if (user != null) CommunityRulesGate()
    val onlineCount by Presence.count.collectAsState()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChatRoom?>(null) }
    LaunchedEffect(pending, user) {
        val r = pending ?: return@LaunchedEffect
        if (user != null) { ChatNotifier.pendingRoom.value = null; nav.navigate("room/$r") }
    }
    LaunchedEffect(user) { if (user != null) { Account.refresh(); Presence.load() } }

    Screen("Chat", back = false, large = true, actions = {
        if (user?.isAdmin == true) IconButton(onClick = { adding = true }) { Icon(Icons.Filled.AddComment, tr("Add a room")) }
        if (user != null) IconButton(onClick = { nav.navigate("account") }) { Avatar(user!!.name, user!!.color, 30.dp, user!!.isAdmin, user!!.avatar) }
    }) { pad ->
        val u = user
        if (u == null) { Box(Modifier.padding(pad)) { SignInPrompt() }; return@Screen }
        LazyColumn(Modifier.padding(pad)) {
            if (online.isNotEmpty()) item {
                Group {
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate("online") }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            online.take(6).forEach { o -> Avatar(o.displayName, o.color ?: "#8C1D40", 32.dp, photo = o.avatar, online = o.online) }
                        }
                        Text(tr("%d online now").format(onlineCount), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp).weight(1f))
                        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Group(footer = "Be kind and helpful. Don't share exam answers during an exam, private info or anything illegal. Admins can remove messages and ban accounts.") {
                    rooms.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        Box {
                            Row(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(onClick = { nav.navigate("room/${r.id}") }, onLongClick = { if (u.isAdmin) menuFor = r.id })
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(r.emoji, fontSize = 22.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                                        if (r.adminOnly == true) Badge(tr("ADMINS POST"), Brand.orange)
                                        if (r.locked == true) Icon(Icons.Filled.Lock, tr("Locked"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (r.archived == true) Badge(tr("ARCHIVED"), Color.Gray)
                                    }
                                    if (r.about.isNotEmpty()) Text(r.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            DropdownMenu(menuFor == r.id, { menuFor = null }) {
                                DropdownMenuItem(text = { Text("Edit room") }, leadingIcon = { Icon(Icons.Filled.Tune, null) }, onClick = { menuFor = null; editing = r })
                                DropdownMenuItem(
                                    text = { Text(if (r.locked == true) "Unlock" else "Lock") },
                                    leadingIcon = { Icon(if (r.locked == true) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
                                    onClick = { menuFor = null; scope.launch { quickRoom(r, locked = r.locked != true) } },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (r.archived == true) "Unarchive" else "Archive") },
                                    leadingIcon = { Icon(Icons.Filled.Archive, null) },
                                    onClick = { menuFor = null; scope.launch { quickRoom(r, archived = r.archived != true) } },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    tr("Signed in as @%s").format(u.username), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
    if (adding) RoomEditor(null) { adding = false }
    editing?.let { r -> RoomEditor(r) { editing = null } }
}

/** Everyone who used the chat lately: online first, then last seen */
@Composable
fun OnlineScreen() {
    val nav = LocalNav.current
    val users by Presence.users.collectAsState()
    LaunchedEffect(Unit) { Presence.load() }
    Screen(tr("Who's online")) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            listOf(true, false).forEach { on ->
                val list = users.filter { it.online == on }
                if (list.isNotEmpty()) item {
                    Group(header = (if (on) tr("Online · %d") else tr("Offline · %d")).format(list.size)) {
                        list.forEachIndexed { i, o ->
                            if (i > 0) Divider()
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.navigate("profile/${o.id}") }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Avatar(o.displayName, o.color ?: "#8C1D40", 40.dp, o.role == "admin", o.avatar, o.online)
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(o.displayName, fontWeight = FontWeight.SemiBold)
                                        BadgeRow(o.badges, compact = true)
                                    }
                                    Text(
                                        if (o.online) tr("Online now") else lastSeenText(o.lastSeen), style = MaterialTheme.typography.bodySmall,
                                        color = if (o.online) Brand.green else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) { today -> tr("Today"); today.minusDays(1) -> tr("Yesterday"); else -> d.format(dayFmt) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRoomScreen(roomId: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rooms by Account.rooms.collectAsState()
    val user by Account.user.collectAsState()
    var serverRoom by remember { mutableStateOf<ChatRoom?>(null) }
    val room = serverRoom ?: rooms.firstOrNull { it.id == roomId } ?: ChatRoom(roomId, roomId.replaceFirstChar { it.uppercase() })
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val people = remember { mutableStateMapOf<String, ChatPerson>() }
    var pinned by remember { mutableStateOf<ChatMessage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var sending by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var banTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var roomSettings by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val admin = Account.isAdmin
    var reporting by remember { mutableStateOf<ReportTarget?>(null) }
    var blocking by remember { mutableStateOf<BlockTarget?>(null) }
    reporting?.let { t -> ReportDialog(t) { reporting = null } }
    blocking?.let { t -> BlockDialog(t, { blocking = null }, { id -> messages.removeAll { it.userId == id } }) }
    val canPost = admin || (room.adminOnly != true && room.locked != true && room.archived != true)

    suspend fun fetch() {
        val f = mutableMapOf<String, Any?>("room" to roomId)
        messages.lastOrNull()?.let { f["after"] = it.id }
        try {
            val r = Account.call("chat", f)
            val gone = (r["deleted"] as? JsonArray)?.mapNotNull { it.prim() }?.toSet() ?: emptySet()
            if (gone.isNotEmpty()) messages.removeAll { it.id in gone }
            (r["edited"] as? JsonArray).objects().forEach { e ->
                val i = messages.indexOfFirst { it.id == e.str("id") }
                if (i >= 0 && messages[i].text != e.str("text")) messages[i] = messages[i].copy(text = e.str("text") ?: messages[i].text, edited = e.str("edited"))
            }
            val have = messages.map { it.id }.toSet()
            val blocked = Account.user.value?.blocked.orEmpty()
            messages.removeAll { it.userId in blocked }
            val new = r["messages"].decodeAs<List<ChatMessage>>().orEmpty().filter { it.id !in have && it.userId !in blocked }
            if (new.isNotEmpty()) messages.addAll(new)
            r["people"].decodeAs<Map<String, ChatPerson>>()?.let { people.putAll(it) }
            r["room"].decodeAs<ChatRoom>()?.let { serverRoom = it }
            pinned = r["pinned"].decodeAs<ChatMessage>()
            error = null
        } catch (e: ApiError) { error = e.message }
        loading = false
    }

    DisposableEffect(roomId) {
        ChatNotifier.openRoom = roomId
        onDispose { if (ChatNotifier.openRoom == roomId) ChatNotifier.openRoom = null }
    }
    // about every 3 seconds while the room is on screen
    LaunchedEffect(roomId) { while (true) { fetch(); delay(3000) } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1) }

    fun send() {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        sending = true
        scope.launch {
            try {
                val e = editing
                if (e != null) {
                    Account.call("editMessage", mapOf("id" to e.id, "text" to t))
                    val i = messages.indexOfFirst { it.id == e.id }
                    if (i >= 0) messages[i] = messages[i].copy(text = t, edited = Instant.now().toString())
                } else {
                    val r = Account.call("send", mapOf("room" to roomId, "text" to t, "replyTo" to (replyTo?.id ?: "")))
                    r["message"].decodeAs<ChatMessage>()?.let { m -> if (messages.none { it.id == m.id }) messages.add(m) }
                }
                text = ""; replyTo = null; editing = null; error = null
            } catch (e: ApiError) { error = e.message }
            sending = false
        }
    }

    fun pin(m: ChatMessage?) {
        scope.launch {
            try { Account.call("adminPin", mapOf("room" to roomId, "id" to (m?.id ?: ""))); pinned = m } catch (e: ApiError) { error = e.message }
        }
    }

    Screen("${room.emoji} ${room.name}", actions = {
        if (admin) IconButton(onClick = { roomSettings = true }) { Icon(Icons.Filled.Tune, tr("Edit room")) }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            pinned?.let { p ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().clickable {
                    val i = messages.indexOfFirst { it.id == p.id }
                    if (i >= 0) scope.launch { list.animateScrollToItem(i + 1) }
                }) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PushPin, null, tint = accent, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("Pinned message", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                            Text(p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        if (admin) IconButton(onClick = { pin(null) }) { Icon(Icons.Filled.Close, tr("Unpin")) }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                if (loading && messages.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else if (messages.isEmpty()) EmptyState(Icons.Filled.Person, tr("No messages yet"), if (canPost) tr("Be the first to say something.") else tr("Announcements from the team show up here."))
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
                            Bubble(
                                m, mine, grouped, reply, people[m.userId], pinned?.id == m.id,
                                Modifier.combinedClickable(onClick = {}, onLongClick = { menuFor = m.id }),
                                onAvatar = { nav.navigate("profile/${m.userId}") },
                            )
                            DropdownMenu(expanded = menuFor == m.id, onDismissRequest = { menuFor = null }) {
                                if (canPost) DropdownMenuItem(text = { Text("Reply") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) }, onClick = { editing = null; replyTo = m; menuFor = null })
                                DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Message", m.text))
                                    menuFor = null
                                })
                                if (mine) DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = {
                                    replyTo = null; editing = m; text = m.text; menuFor = null
                                })
                                if (!mine) DropdownMenuItem(text = { Text("View profile") }, leadingIcon = { Icon(Icons.Filled.Person, null) }, onClick = {
                                    menuFor = null; nav.navigate("profile/${m.userId}")
                                })
                                if (!mine) DropdownMenuItem(text = { Text("Report message") }, leadingIcon = { Icon(Icons.Filled.Flag, null) }, onClick = {
                                    menuFor = null; reporting = ReportTarget("message", m.id, m.text.take(120))
                                })
                                if (!mine && m.role != "admin") DropdownMenuItem(text = { Text("Block") }, leadingIcon = { Icon(Icons.Filled.Block, null) }, onClick = {
                                    menuFor = null; blocking = BlockTarget(m.userId, m.displayName.ifEmpty { m.username })
                                })
                                if (admin) DropdownMenuItem(
                                    text = { Text(if (pinned?.id == m.id) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                                    onClick = { menuFor = null; pin(if (pinned?.id == m.id) null else m) },
                                )
                                if (mine || admin) DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                                    menuFor = null
                                    scope.launch {
                                        try { Account.call("deleteMessage", mapOf("id" to m.id)); messages.removeAll { it.id == m.id } } catch (e: ApiError) { error = e.message }
                                    }
                                })
                                if (admin && !mine) DropdownMenuItem(text = { Text(tr("Ban @%s").format(m.username)) }, leadingIcon = { Icon(Icons.Filled.Block, null) }, onClick = { banTarget = m; menuFor = null })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                if (!canPost) Text(
                    when {
                        room.archived == true -> tr("This room is archived.")
                        room.locked == true -> tr("This room is locked. Only admins can post.")
                        else -> tr("Only admins post here. You'll get a notification for each announcement.")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 8.dp),
                ) else Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    (editing ?: replyTo)?.let { r ->
                        Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(30.dp).background(accent, RoundedCornerShape(2.dp)))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(
                                    if (editing != null) tr("Editing your message") else tr("Replying to %s").format(r.displayName.ifEmpty { r.username }),
                                    style = MaterialTheme.typography.labelMedium, color = accent,
                                )
                                Text(r.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { if (editing != null) text = ""; replyTo = null; editing = null }) { Icon(Icons.Filled.Close, tr("Cancel")) }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            text, { if (it.length <= 1000) text = it }, placeholder = { Text("Message") },
                            maxLines = 5, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f),
                        )
                        FilledIconButton(onClick = ::send, enabled = text.isNotBlank() && !sending, modifier = Modifier.size(48.dp)) {
                            if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(if (editing != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send, tr("Send"))
                        }
                    }
                }
            }
        }
    }

    if (roomSettings) RoomEditor(room) { roomSettings = false; scope.launch { fetch() } }
    banTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { banTarget = null },
            title = { Text(tr("Ban @%s?").format(m.username)) },
            text = { Text("They can't sign in or chat until an admin unbans them in the Admin tab.") },
            confirmButton = {
                TextButton(onClick = {
                    banTarget = null
                    scope.launch {
                        try {
                            Account.call("adminBan", mapOf("userId" to m.userId, "banned" to true, "reason" to "Removed from the chat by an admin"))
                            Toast.makeText(ctx, tr("@%s is banned").format(m.username), Toast.LENGTH_SHORT).show()
                        } catch (e: ApiError) { error = e.message }
                    }
                }) { Text("Ban", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { banTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Bubble(
    m: ChatMessage, mine: Boolean, grouped: Boolean, reply: ChatMessage?, person: ChatPerson?, isPinned: Boolean,
    modifier: Modifier, onAvatar: () -> Unit,
) {
    val admin = m.role == "admin"
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = if (grouped) 0.dp else 6.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            if (grouped) Spacer(Modifier.width(34.dp))
            else Box(Modifier.clip(CircleShape).clickable(onClick = onAvatar)) {
                Avatar(m.displayName.ifEmpty { m.username }, m.color, 30.dp, admin, person?.avatar, person?.online)
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(
            modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp))
                .background(if (mine) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            if (!mine && !grouped) Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(m.displayName.ifEmpty { m.username }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = hexColor(m.color))
                if (admin) Text(tr("ADMIN"), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                    modifier = Modifier.clip(CircleShape).background(Brand.gold).padding(horizontal = 5.dp, vertical = 1.dp))
                BadgeRow(person?.badges.orEmpty(), compact = true)
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
                listOfNotNull(
                    if (isPinned) "📌" else null,
                    if (!m.edited.isNullOrEmpty()) tr("edited") else null,
                    m.instant.atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt),
                ).joinToString(" · "),
                fontSize = 10.sp, color = fg.copy(alpha = 0.65f), modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
