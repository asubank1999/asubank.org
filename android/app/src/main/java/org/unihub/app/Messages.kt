package org.unihub.app

import androidx.compose.ui.graphics.graphicsLayer
import java.util.UUID
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Private messages between friends. Admins can write to anyone. Files stay private: the server hands them only to the two people in the chat.

@Serializable
data class DmMessage(
    val id: String,
    val from: String = "",
    val to: String = "",
    val text: String = "",
    val created: String? = null,
    val fileName: String = "",
    val fileType: String = "",
    val fileSize: Long = 0,
    val hasFile: Boolean = false,
    val duration: Int = 0,
    val expires: String? = null,
    val pending: Boolean = false,
    val local: String? = null,
) {
    val instant: Instant get() = created?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
    val day: LocalDate get() = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val preview: String get() = text.ifEmpty {
        when (fileType) { "photo" -> "📷 " + tr("Photo"); "voice" -> "🎤 " + tr("Voice message"); "video" -> "🎬 " + tr("Video"); else -> "📄 $fileName" }
    }
}

@Serializable
data class DmConversation(val user: AbUser, val last: DmMessage, val unread: Int = 0)

object Dms {
    val conversations = MutableStateFlow<List<DmConversation>>(emptyList())
    val unread = MutableStateFlow(0)
    val loaded = MutableStateFlow(false)

    private fun read(r: kotlinx.serialization.json.JsonObject) {
        conversations.value = r["conversations"].decodeAs<List<DmConversation>>().orEmpty()
        unread.value = r.str("unread")?.toDoubleOrNull()?.toInt() ?: 0
    }

    suspend fun load() {
        if (Account.user.value == null) return
        if (!loaded.value) Account.saved("dmList")?.let { read(it) }
        runCatching { read(Account.callSaving("dmList")) }
        loaded.value = true
    }
}

object DmFiles {
    const val MAX_BYTES = 8_000_000

    /** Inside the folder the app's file sharing can open */
    fun folder(ctx: Context) = File(ctx.cacheDir, "materials/dm")

    suspend fun local(ctx: Context, m: DmMessage): File = withContext(Dispatchers.IO) {
        val safe = m.fileName.ifEmpty { "file" }.replace(Regex("[/\\\\:]"), "_")
        val dir = File(folder(ctx), m.id)
        val file = File(dir, safe)
        if (file.length() > 0) return@withContext file
        val data = Account.call("dmFile", mapOf("id" to m.id)).str("data") ?: throw ApiError(tr("File not found."))
        dir.mkdirs()
        writeWhole(file, Base64.decode(data, Base64.DEFAULT))
        file
    }

    fun sizeText(n: Long) = if (n >= 1_000_000) "%.1f MB".format(n / 1_000_000.0) else "${maxOf(1, n / 1000)} KB"

    fun displayName(ctx: Context, uri: Uri): String = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "file"
}

// MARK: - parts shared with the rooms

/** Two or more choices in one pill, the selected one filled */
@Composable
fun PillTabs(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) accent else Color.Transparent)
                    .clickable { onSelect(key) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The look of every chat message: accent gradient for mine, a soft card for others, joined corners inside a run of messages */
@Composable
fun Modifier.chatBubble(mine: Boolean, joinedAbove: Boolean, joinedBelow: Boolean): Modifier {
    val big = 20.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (!mine && joinedAbove) small else big,
        topEnd = if (mine && joinedAbove) small else big,
        bottomEnd = if (mine && joinedBelow) small else big,
        bottomStart = if (!mine && joinedBelow) small else big,
    )
    val a = accent
    return if (mine) clip(shape).background(Brush.linearGradient(listOf(a, Color(a.red * 0.8f, a.green * 0.8f, a.blue * 0.8f))))
    else clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
}

private val dmDayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dmTimeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun ChatDayPill(day: LocalDate) {
    val today = LocalDate.now()
    val label = when (day) { today -> tr("Today"); today.minusDays(1) -> tr("Yesterday"); else -> day.format(dmDayFmt) }
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

// MARK: - conversations

/** The Messages side of the Chat tab */
@Composable
fun DmConversations() {
    val nav = LocalNav.current
    val list by Dms.conversations.collectAsState()
    val loaded by Dms.loaded.collectAsState()
    Column {
        Group { ListRow("New message", icon = Icons.Filled.Edit) { nav.navigate("newmessage") } }
        if (list.isEmpty()) {
            if (loaded) EmptyState(Icons.Filled.Send, tr("No private messages yet"), tr("You can message your friends. Only the two of you see the chat."))
            else Group { repeat(5) { SkeletonRow() } }
        } else Group {
            list.forEachIndexed { i, c ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("dm/${c.user.id}") }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(c.user.name, c.user.color, 50.dp, c.user.isAdmin, c.user.avatar, c.user.online, hold = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.user.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            Text(
                                Social.ago(c.last.created), style = MaterialTheme.typography.bodySmall,
                                color = if (c.unread > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.last.preview, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (c.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (c.unread > 0) Text(
                                "${c.unread}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Choosing who to write to: friends, and for admins anyone */
@Composable
fun NewMessageScreen() {
    val nav = LocalNav.current
    var friends by remember { mutableStateOf<List<AbUser>>(emptyList()) }
    var found by remember { mutableStateOf<List<AbUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { friends = Account.call("friends")["friends"].decodeAs<List<AbUser>>().orEmpty() }
        loaded = true
    }
    LaunchedEffect(query) {
        if (!Account.isAdmin || query.trim().length < 2) { found = emptyList(); return@LaunchedEffect }
        delay(400)
        runCatching { found = Account.call("searchUsers", mapOf("q" to query.trim()))["people"].decodeAs<List<AbUser>>().orEmpty() }
    }
    val shown = friends.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    @Composable
    fun person(u: AbUser) {
        Row(
            Modifier.fillMaxWidth().clickable { nav.navigate("dm/${u.id}") }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(u.name, u.color, 40.dp, u.isAdmin, u.avatar, u.online, hold = false)
            Text(u.name, fontWeight = FontWeight.SemiBold)
        }
    }

    Screen(tr("New message")) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search people") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                )
            }
            if (shown.isEmpty() && found.isEmpty() && loaded) item {
                EmptyState(Icons.Filled.Group, tr("No friends yet"), tr("Add friends first. Then you can message them here."))
            }
            if (shown.isNotEmpty()) item { Group(header = "Friends") { shown.forEachIndexed { i, u -> if (i > 0) Divider(); person(u) } } }
            if (found.isNotEmpty()) item { Group(header = "Everyone") { found.forEachIndexed { i, u -> if (i > 0) Divider(); person(u) } } }
        }
    }
}

// MARK: - one conversation

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DmThreadScreen(userId: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    val messages = remember { mutableStateListOf<DmMessage>() }
    var other by remember { mutableStateOf<AbUser?>(null) }
    var canSend by remember { mutableStateOf(true) }
    var seenUpTo by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var disappear by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf<ReportTarget?>(null) }
    var blocking by remember { mutableStateOf<BlockTarget?>(null) }
    val list = rememberLazyListState()
    reporting?.let { t -> ReportDialog(t) { reporting = null } }
    blocking?.let { t -> BlockDialog(t, { blocking = null }) }

    suspend fun fetch() {
        val f = mutableMapOf<String, Any?>("userId" to userId)
        messages.lastOrNull { !it.pending }?.let { f["after"] = it.id }
        try {
            val r = Account.call("dmThread", f)
            val gone = (r["deleted"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty()
            messages.removeAll { it.id in gone || Disappear.expired(it.expires) }
            val have = messages.map { it.id }.toSet()
            val new = r["messages"].decodeAs<List<DmMessage>>().orEmpty().filter { it.id !in have && it.id !in gone && !Disappear.expired(it.expires) }
            // what is still on its way stays last
            if (new.isNotEmpty()) messages.addAll(messages.indexOfFirst { it.pending }.takeIf { it >= 0 } ?: messages.size, new)
            r["user"].decodeAs<AbUser>()?.let { other = it }
            if (r.containsKey("canSend")) canSend = r.bool("canSend")
            r.str("seenUpTo")?.let { seenUpTo = it }
            error = null
        } catch (e: ApiError) { error = e.message }
        loading = false
    }

    /** The message shows at once and is replaced by the real one when the server has it. Leaving the chat doesn't stop it. */
    fun send(body: String, o: ChatOutgoing? = null) {
        val temp = DmMessage(
            "local-" + UUID.randomUUID(), me?.id ?: "", userId, body, Instant.now().toString(), o?.fileName ?: "", o?.type ?: "", o?.size ?: 0, o != null,
            o?.duration ?: 0, pending = true, local = o?.local,
        )
        val gone = disappear
        messages.add(temp)
        BackgroundJobs.scope.launch {
            try {
                val f = mutableMapOf<String, Any?>("userId" to userId, "text" to body, "expiresIn" to gone)
                if (o != null) {
                    f["file"] = o.load() ?: throw ApiError(tr("Couldn't send this file."))
                    f["fileName"] = o.fileName
                    f["duration"] = o.duration
                }
                val m = Account.call("dmSend", f)["message"].decodeAs<DmMessage>()
                val i = messages.indexOfFirst { it.id == temp.id }
                if (m != null && i >= 0 && messages.none { it.id == m.id }) messages[i] = m.copy(local = o?.local) else messages.removeAll { it.id == temp.id }
                error = null
            } catch (e: Exception) {
                messages.removeAll { it.id == temp.id }
                error = e.message
                if (body.isNotEmpty() && text.isEmpty()) text = body
            }
        }
    }

    fun open(m: DmMessage) {
        opening = m.id
        scope.launch {
            try { shareFile(ctx, DmFiles.local(ctx, m), view = true) }
            catch (e: ApiError) { error = e.message }
            catch (e: java.io.IOException) { error = tr("Couldn't open this file.") }
            opening = null
        }
    }

    DisposableEffect(userId) {
        ChatNotifier.openDM = userId
        onDispose { if (ChatNotifier.openDM == userId) ChatNotifier.openDM = null }
    }
    LaunchedEffect(userId) { while (true) { fetch(); delay(3000) } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }

    val lastSeenMine = if (seenUpTo.isEmpty()) null else messages.lastOrNull { it.from == me?.id && (it.created ?: "") <= seenUpTo }?.id
    val name = other?.name ?: ""

    Screen(name, actions = {
        other?.let { u ->
            IconButton(onClick = { nav.navigate("profile/${u.id}") }) { Avatar(u.name, u.color, 32.dp, photo = u.avatar, online = u.online, hold = false) }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
                if (loading && messages.isEmpty()) SkeletonBubbles()
                else if (messages.isEmpty()) Column(Modifier.align(Alignment.Center).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    other?.let { Avatar(it.name, it.color, 72.dp, photo = it.avatar, hold = false) }
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text("Say hello. Only the two of you see this chat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                    items(messages.size, key = { messages[it].id }) { i ->
                        val m = messages[i]
                        val prev = messages.getOrNull(i - 1)
                        val next = messages.getOrNull(i + 1)
                        if (prev == null || prev.day != m.day) ChatDayPill(m.day)
                        val mine = m.from == me?.id
                        val above = prev?.from == m.from && prev.day == m.day
                        val below = next?.from == m.from && next.day == m.day
                        Column(Modifier.animateItem().fillMaxWidth().padding(top = if (above) 1.dp else 6.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            Box {
                                Column(
                                    Modifier.widthIn(max = 290.dp).chatBubble(mine, above, below)
                                        .graphicsLayer { alpha = if (m.pending) 0.72f else 1f }
                                        .combinedClickable(onClick = { if (m.hasFile && !m.pending && m.fileType in setOf("pdf", "docx")) open(m) }, onLongClick = { menuFor = m.id })
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    if (m.hasFile) {
                                        if (m.fileType in setOf("photo", "voice", "video")) ChatAttachment(m.id, m.fileType, m.duration, mine, m.local) { DmFiles.local(ctx, m) }
                                        else Row(Modifier.width(220.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(
                                                if (m.fileType == "pdf") Icons.Filled.PictureAsPdf else Icons.Filled.Description, null,
                                                tint = if (mine) fg else if (m.fileType == "pdf") Color(0xFFD32F2F) else Color(0xFF1976D2),
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(m.fileName, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(DmFiles.sizeText(m.fileSize), color = fg.copy(alpha = 0.75f), fontSize = 11.sp)
                                            }
                                            if (opening == m.id) ABDots(Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
                                        }
                                    }
                                    if (m.text.isNotEmpty()) Text(m.text, color = fg, style = MaterialTheme.typography.bodyLarge)
                                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        ExpiryTag(m.expires, fg.copy(alpha = 0.7f))
                                        if (m.pending) Icon(Icons.Filled.Schedule, null, Modifier.size(11.dp), tint = fg.copy(alpha = 0.7f))
                                        androidx.compose.material3.Text(m.instant.atZone(ZoneId.systemDefault()).toLocalTime().format(dmTimeFmt), fontSize = 10.sp, color = fg.copy(alpha = 0.65f))
                                    }
                                }
                                DropdownMenu(menuFor == m.id, { menuFor = null }) {
                                    if (m.text.isNotEmpty()) DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Message", m.text))
                                        menuFor = null
                                    })
                                    if (mine) { if (!m.pending) DropdownMenuItem(text = { Text("Delete for everyone") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                                        menuFor = null
                                        // gone at once for me; the other person's phone removes it on its next check
                                        val at = messages.indexOfFirst { it.id == m.id }
                                        messages.removeAll { it.id == m.id }
                                        BackgroundJobs.scope.launch {
                                            try { Account.call("dmDelete", mapOf("id" to m.id)) }
                                            catch (e: Exception) { error = e.message; if (at >= 0) messages.add(minOf(at, messages.size), m) }
                                        }
                                    }) } else {
                                        DropdownMenuItem(text = { Text("Report message") }, leadingIcon = { Icon(Icons.Filled.Flag, null) }, onClick = {
                                            menuFor = null
                                            reporting = ReportTarget("dm", m.id, m.preview.take(120))
                                        })
                                        if (other?.isAdmin != true) DropdownMenuItem(text = { Text("Block") }, leadingIcon = { Icon(Icons.Filled.Block, null) }, onClick = {
                                            menuFor = null
                                            blocking = BlockTarget(userId, name)
                                        })
                                    }
                                }
                            }
                            if (m.id == lastSeenMine) Text("Seen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp, top = 2.dp))
                        }
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                if (!canSend) Row(
                    Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" " + tr("You can only send private messages to friends."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    ChatInputBar(
                        text, { if (it.length <= 2000) text = it }, allowDocs = true, disappear = disappear, onDisappear = { disappear = it },
                        sendMedia = { o -> send("", o) }, fail = { error = it },
                    ) {
                        val t = text.trim()
                        if (t.isNotEmpty()) { text = ""; send(t) }
                    }
                }
            }
        }
    }
}
