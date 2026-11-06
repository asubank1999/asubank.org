package org.unihub.app

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.max
import kotlin.math.roundToInt

/** More > Customize: what Home shows, the Continue list, text size. Kept on this device. */
object HomePrefs {
    val sections = listOf(
        "online" to "Who's online", "weather" to "Weather", "calendar" to "Calendar", "lecture" to "Next lecture",
        "due" to "Exams & tasks", "hero" to "Search banner", "continue" to "Continue", "saved" to "Saved",
        "quick" to "Quick links", "schedules" to "Calendars & schedules", "subjects" to "Subjects",
        "newhere" to "New students", "community" to "Community & services",
    )
    val textSizes = listOf(0 to "Match my phone", 1 to "Small", 2 to "Normal", 3 to "Large", 4 to "Extra large")
    val startTabs = listOf(Routes.HOME to "Home", Routes.FEED to "Feed", Routes.CHAT to "Chat", Routes.SEARCH to "Search")

    val hidden = MutableStateFlow<Set<String>>(emptySet())
    val compact = MutableStateFlow(false)
    /** Whether opened files are remembered for Continue */
    val remember = MutableStateFlow(true)
    val start = MutableStateFlow(Routes.HOME)
    val textSize = MutableStateFlow(0)

    fun init() {
        val p = Saved.prefs
        hidden.value = (p.getString("home.hidden", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()
        compact.value = p.getBoolean("home.compact", false)
        remember.value = p.getBoolean("recent.on", true)
        start.value = p.getString("start.tab", Routes.HOME) ?: Routes.HOME
        textSize.value = p.getInt("text.size", 0)
    }

    fun setShown(id: String, on: Boolean) {
        hidden.value = if (on) hidden.value - id else hidden.value + id
        Saved.prefs.edit().putString("home.hidden", hidden.value.joinToString(",")).apply()
    }
    fun setCompact(v: Boolean) { compact.value = v; Saved.prefs.edit().putBoolean("home.compact", v).apply() }
    fun setRemember(v: Boolean) {
        remember.value = v
        Saved.prefs.edit().putBoolean("recent.on", v).apply()
        if (!v) Saved.clearRecent()
    }
    fun setStart(v: String) { start.value = v; Saved.prefs.edit().putString("start.tab", v).apply() }
    fun setTextSize(v: Int) { textSize.value = v; Saved.prefs.edit().putInt("text.size", v).apply() }
    fun reset() {
        hidden.value = emptySet()
        Saved.prefs.edit().putString("home.hidden", "").apply()
        setCompact(false); setRemember(true); setStart(Routes.HOME); setTextSize(0)
    }

    /** How much bigger text is than what the phone asks for */
    fun textScale(v: Int) = when (v) { 1 -> 0.88f; 3 -> 1.14f; 4 -> 1.28f; else -> 1f }
}

/** A row that opens a short list to choose from */
@Composable
fun ChoiceRow(title: String, choices: List<Pair<String, String>>, selected: String, icon: ImageVector? = null, onChoose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListRow(title, trailing = tr(choices.firstOrNull { it.first == selected }?.second ?: ""), icon = icon) { open = true }
        DropdownMenu(open, { open = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onChoose(id) })
            }
        }
    }
}

@Composable
fun CustomizeScreen() {
    val nav = LocalNav.current
    val hidden by HomePrefs.hidden.collectAsState()
    val compact by HomePrefs.compact.collectAsState()
    val remembers by HomePrefs.remember.collectAsState()
    val start by HomePrefs.start.collectAsState()
    val textSize by HomePrefs.textSize.collectAsState()
    val recent by Saved.recent.collectAsState()
    Screen("Customize") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Group(header = "Look and feel") {
                    ListRow("Theme", icon = Icons.Filled.Palette) { nav.navigate("theme") }
                    Divider(); ListRow("Customize tab bar", icon = Icons.Filled.Dashboard) { nav.navigate("tabBar") }
                    Divider(); ListRow("App icon", icon = Icons.Filled.Apps) { nav.navigate("appIcon") }
                    Divider(); ListRow("Sounds", icon = Icons.Filled.VolumeUp) { nav.navigate("sounds") }
                    Divider(); ListRow("Language", icon = Icons.Filled.Language) { nav.navigate("language") }
                }
            }
            item {
                Group(
                    header = "Continue",
                    footer = "Continue lists the files you opened lately. It stays on this phone. Turn off Remember and the app stops keeping this list.",
                ) {
                    SwitchRow("Remember what I open", Icons.Filled.History, remembers) { HomePrefs.setRemember(it) }
                    Divider()
                    SwitchRow("Show Continue on Home", Icons.Filled.Home, "continue" !in hidden) { HomePrefs.setShown("continue", it) }
                    if (recent.isNotEmpty()) {
                        Divider()
                        ListRow("Clear the Continue list", icon = Icons.Filled.DeleteOutline, titleColor = MaterialTheme.colorScheme.error) { Saved.clearRecent() }
                    }
                }
            }
            item {
                Group(header = "What Home shows", footer = "Good for small screens: turn off what you don't use, so more fits on one screen.") {
                    HomePrefs.sections.filter { it.first != "continue" }.forEachIndexed { i, (id, name) ->
                        if (i > 0) Divider()
                        SwitchRow(name, null, id !in hidden) { HomePrefs.setShown(id, it) }
                    }
                    Divider()
                    SwitchRow("Smaller Quick links", Icons.Filled.ViewModule, compact) { HomePrefs.setCompact(it) }
                }
            }
            item {
                Group(header = "General") {
                    ChoiceRow("Open the app on", HomePrefs.startTabs, start, Icons.Filled.Home) { HomePrefs.setStart(it) }
                    Divider()
                    ChoiceRow("Text size", HomePrefs.textSizes.map { it.first.toString() to it.second }, textSize.toString(), Icons.Filled.TextFields) { HomePrefs.setTextSize(it.toInt()) }
                }
            }
            item { Group { ListRow("Reset to the default", icon = Icons.Filled.Refresh) { HomePrefs.reset() } } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// MARK: top bar and who's online

/** Your photo, top left. Opens your profile, or sign in. */
@Composable
fun MeButton() {
    val nav = LocalNav.current
    val u by Account.user.collectAsState()
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable {
            val me = u
            if (me != null) nav.navigate("profile/${me.id}") else Account.signInShown.value = true
        },
        contentAlignment = Alignment.Center,
    ) {
        val me = u
        if (me != null) Avatar(me.name, me.color, 34.dp, me.isAdmin, me.avatar, hold = false)
        else Icon(Icons.Filled.AccountCircle, tr("My profile"), Modifier.size(32.dp), tint = accent)
    }
}

private val onlineGreen = Color(0xFF22B14C)

/** Keeps the number fresh while a screen shows it */
@Composable
private fun KeepOnlineFresh() {
    val u by Account.user.collectAsState()
    LaunchedEffect(u?.id) {
        while (true) {
            Presence.load()
            delay(60_000)
        }
    }
}

private fun openOnline(nav: androidx.navigation.NavController) {
    if (Account.signedIn) nav.navigate("online") else Account.signInShown.value = true
}

/** A green dot and how many are online, for top bars */
@Composable
fun OnlineButton() {
    val nav = LocalNav.current
    val count by Presence.count.collectAsState()
    KeepOnlineFresh()
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(onlineGreen.copy(alpha = 0.16f)).clickable { openOnline(nav) }.padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(onlineGreen))
        Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** The box that says who is online: faces, how many, and a way to the full list */
@Composable
fun OnlineBox(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val users by Presence.users.collectAsState()
    val count by Presence.count.collectAsState()
    val me by Account.user.collectAsState()
    KeepOnlineFresh()
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { openOnline(nav) },
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val faces = users.filter { it.online }.take(5)
            if (faces.isEmpty()) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(onlineGreen.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Groups, null, Modifier.size(18.dp), tint = onlineGreen)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
                    faces.forEach { o -> Avatar(o.displayName, o.color ?: "#8C1D40", 32.dp, photo = o.avatar, hold = false) }
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(onlineGreen))
                    Text(tr("%d online now").format(count), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(if (me != null) "See everyone" else "Sign in to see who", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        }
    }
}

// MARK: cover photo

/** Move and zoom a picked photo inside the shape a cover photo has */
@Composable
fun CoverCropDialog(bitmap: Bitmap, onClose: () -> Unit, onDone: (Bitmap) -> Unit) {
    val ratio = 2.4f
    var box by remember { mutableStateOf(IntSize(1, 1)) }
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    // pixels on screen per photo pixel
    fun k(s: Float) = max(box.width.toFloat() / bitmap.width, box.height.toFloat() / bitmap.height) * s
    // the photo always fills the frame
    fun clamp(o: Offset, s: Float): Offset {
        val x = max(0f, (bitmap.width * k(s) - box.width) / 2)
        val y = max(0f, (bitmap.height * k(s) - box.height) / 2)
        return Offset(o.x.coerceIn(-x, x), o.y.coerceIn(-y, y))
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("Cancel") }
                    Text("Adjust cover photo", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = {
                        val w = 1280
                        val h = (w / ratio).roundToInt()
                        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val f = w.toFloat() / box.width
                        val dw = bitmap.width * k(scale) * f
                        val dh = bitmap.height * k(scale) * f
                        val left = (w - dw) / 2 + off.x * f
                        val top = (h - dh) / 2 + off.y * f
                        android.graphics.Canvas(out).drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                        onDone(out)
                    }) { Text("Use photo", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.weight(1f))
                Canvas(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(14.dp))
                        .onSizeChanged { box = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                off = clamp(off + pan, scale)
                            }
                        },
                ) {
                    val q = k(scale)
                    val dw = (bitmap.width * q).roundToInt()
                    val dh = (bitmap.height * q).roundToInt()
                    drawImage(
                        image,
                        dstOffset = IntOffset(((size.width - dw) / 2 + off.x).roundToInt(), ((size.height - dh) / 2 + off.y).roundToInt()),
                        dstSize = IntSize(dw, dh),
                    )
                }
                Text(
                    "Drag to move, pinch to zoom", Modifier.fillMaxWidth().padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Slider(scale, { scale = it; off = clamp(off, it) }, Modifier.padding(horizontal = 24.dp), valueRange = 1f..5f)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

// MARK: YouTube links

/** Share a YouTube video, the same list as asubank.org/upload/uploadmethod */
@Composable
fun ShareVideoScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var links by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<ReportTarget?>(null) }
    suspend fun load() {
        runCatching { links = (AccountsApi.call("linksList", emptyMap(), Account.token)["links"] as? JsonArray).objects().take(100) }
    }
    LaunchedEffect(Unit) { load() }
    reporting?.let { t -> ReportDialog(t) { reporting = null } }
    Screen("Share a video") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                if (me == null) Group(footer = "Sharing a video needs an account.") {
                    ListRow("Sign in or create account", icon = Icons.Filled.AccountCircle) { Account.signInShown.value = true }
                } else Group(header = "Share a YouTube video", footer = "It's published at once, for every student. Share only videos that are public on YouTube.") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(url, { url = it }, label = { Text("YouTube link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(subject, { subject = it }, label = { Text("Subject (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (sent) Text("Shared. Thank you!", color = Brand.green)
                        androidx.compose.material3.Button(
                            enabled = !busy && "youtu" in url.lowercase() && title.trim().length >= 3,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        Account.call("linkAdd", mapOf("url" to url.trim(), "title" to title.trim(), "subject" to subject.trim()))
                                        url = ""; title = ""; subject = ""; error = null; sent = true
                                        load()
                                    } catch (e: ApiError) { error = e.message; sent = false }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Share video") }
                    }
                }
            }
            if (links.isNotEmpty()) item { SectionTitle("Videos students shared") }
            items(links) { l ->
                var menu by remember { mutableStateOf(false) }
                val u = l.str("u") ?: ""
                val t = l.str("t") ?: ""
                Group {
                    Box {
                        ListRow(t, l.str("s"), icon = Icons.Filled.PlayCircle, iconColor = Color(0xFFE5372D), trailing = "⋯") { menu = true }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; openExternal(ctx, u) })
                            DropdownMenuItem(text = { Text("Report") }, onClick = { menu = false; reporting = ReportTarget("file", u, t) })
                            if (l.str("canDelete") == "true") DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = {
                                menu = false
                                links = links - l
                                scope.launch { runCatching { Account.call("linkDelete", mapOf("id" to l.str("id"))) }; load() }
                            })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
