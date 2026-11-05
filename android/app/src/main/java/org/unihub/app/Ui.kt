package org.unihub.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// MARK: theme (colors from the website's design system, assets/app/app.css)

object Brand {
    val accent = Color(0xFF8C1D40)
    val accentDark = Color(0xFFFF6F9A)
    val gold = Color(0xFFFFC627)
    val green = Color(0xFF1F8A3E)
    val orange = Color(0xFFE8590C)
}

private val light: ColorScheme = lightColorScheme(
    primary = Brand.accent, onPrimary = Color.White,
    primaryContainer = Color(0xFFF7DCE5), onPrimaryContainer = Color(0xFF3A0618),
    secondary = Color(0xFF6E5560), secondaryContainer = Color(0xFFF2E4E8),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF111114),
    surface = Color(0xFFF2F2F7), onSurface = Color(0xFF111114),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF7F7FA),
    surfaceVariant = Color(0xFFE9E9EE), onSurfaceVariant = Color(0xFF5E5E66),
    outlineVariant = Color(0xFFDCDCE2),
)

private val dark: ColorScheme = darkColorScheme(
    primary = Brand.accentDark, onPrimary = Color(0xFF3A0618),
    primaryContainer = Color(0xFF5A1530), onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE3BDC8), secondaryContainer = Color(0xFF3A2C31),
    background = Color(0xFF141416), onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF141416), onSurface = Color(0xFFF2F2F7),
    surfaceContainerLowest = Color(0xFF1F1F22), surfaceContainerLow = Color(0xFF1F1F22),
    surfaceContainer = Color(0xFF232326), surfaceContainerHigh = Color(0xFF2E2E32),
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFFA1A1AA),
    outlineVariant = Color(0xFF3A3A3E),
)

/** Pitch black for OLED screens: true black backgrounds, near-black cards */
private val black: ColorScheme = dark.copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color(0xFF0E0E10), surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = Color(0xFF111113), surfaceContainerHigh = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF1C1C1E), outlineVariant = Color(0xFF2A2A2D),
)

/** Themes with their own background color */
private fun tinted(base: ColorScheme, bg: Long, card: Long, high: Long, line: Long) = base.copy(
    background = Color(bg), surface = Color(bg),
    surfaceContainerLowest = Color(card), surfaceContainerLow = Color(card),
    surfaceContainer = Color(card), surfaceContainerHigh = Color(high),
    surfaceVariant = Color(high), outlineVariant = Color(line),
)
private val midnight = tinted(dark, 0xFF0B1426, 0xFF16213A, 0xFF1F2C4A, 0xFF2C3A5C)
private val forestNight = tinted(dark, 0xFF0C1A12, 0xFF16291E, 0xFF1E3528, 0xFF2B4636)
private val plum = tinted(dark, 0xFF1A0F1F, 0xFF281A2F, 0xFF33233B, 0xFF44314E)
private val sand = tinted(light, 0xFFF4ECDD, 0xFFFFFBF2, 0xFFFAF3E5, 0xFFE3D8C3)
private val sky = tinted(light, 0xFFE3EEF9, 0xFFFFFFFF, 0xFFF1F7FD, 0xFFCFDDEB)
private val blush = tinted(light, 0xFFFBEAF0, 0xFFFFFFFF, 0xFFFDF3F6, 0xFFEBD3DB)

data class AccentChoice(val id: Int, val name: String, val light: Long, val dark: Long)

object Prefs {
    /** 0 match the phone, 1 light, 2 dark, 3 pitch black (OLED), 4 midnight, 5 forest night, 6 plum, 7 sand, 8 sky, 9 blush */
    val appearance = MutableStateFlow(0)
    val accent = MutableStateFlow(0)
    val accents = listOf(
        AccentChoice(0, "ASU maroon", 0xFF8C1D40, 0xFFFF6F9A),
        AccentChoice(1, "Ocean", 0xFF0A63C9, 0xFF4DA3FF),
        AccentChoice(2, "Forest", 0xFF1F7A3A, 0xFF4CD07A),
        AccentChoice(3, "Violet", 0xFF6E3FD1, 0xFFB18CFF),
        AccentChoice(4, "Sunset", 0xFFD2510A, 0xFFFF9A52),
        AccentChoice(5, "Teal", 0xFF00847F, 0xFF3FD6CF),
        AccentChoice(6, "Graphite", 0xFF3A3A3C, 0xFFD1D1D6),
        AccentChoice(7, "Rose", 0xFFC2185B, 0xFFFF7EB0),
        AccentChoice(8, "Royal blue", 0xFF2F3FC4, 0xFF8C9BFF),
        AccentChoice(9, "Mint", 0xFF0F8A6D, 0xFF5BE3BF),
        AccentChoice(10, "Gold", 0xFFA8780A, 0xFFFFC627),
        AccentChoice(11, "Crimson", 0xFFB3132B, 0xFFFF6B7D),
        AccentChoice(12, "Lavender", 0xFF7A5AA6, 0xFFC9AEFC),
        AccentChoice(13, "Coffee", 0xFF6F4E37, 0xFFD2A77F),
        AccentChoice(14, "Emerald", 0xFF047857, 0xFF34D399),
        AccentChoice(15, "Indigo", 0xFF4338CA, 0xFFA5B4FC),
        AccentChoice(16, "Sky", 0xFF0369A1, 0xFF7DD3FC),
        AccentChoice(17, "Berry", 0xFF9D174D, 0xFFF9A8D4),
        AccentChoice(18, "Olive", 0xFF5F6B1A, 0xFFC5D36A),
        AccentChoice(19, "Copper", 0xFF9A4A1F, 0xFFE8A67C),
    )
    fun setAppearance(v: Int) { appearance.value = v; Saved.prefs.edit().putInt("appearance", v).apply() }
    fun setAccent(v: Int) { accent.value = v; Saved.prefs.edit().putInt("accent", v).apply() }
}

@Composable
fun isDarkTheme(): Boolean {
    val a by Prefs.appearance.collectAsState()
    return when (a) { 1, 7, 8, 9 -> false; 2, 3, 4, 5, 6 -> true; else -> isSystemInDarkTheme() }
}

@Composable
fun AsuTheme(content: @Composable () -> Unit) {
    val a by Prefs.appearance.collectAsState()
    val ac by Prefs.accent.collectAsState()
    val isDark = isDarkTheme()
    val base = when (a) {
        3 -> black
        4 -> midnight
        5 -> forestNight
        6 -> plum
        7 -> sand
        8 -> sky
        9 -> blush
        else -> if (isDark) dark else light
    }
    val choice = Prefs.accents.firstOrNull { it.id == ac } ?: Prefs.accents[0]
    val scheme = if (choice.id == 0) base else {
        val p = Color(if (isDark) choice.dark else choice.light)
        if (isDark) base.copy(primary = p, onPrimary = Color.Black, primaryContainer = lerp(Color.Black, p, 0.32f), onPrimaryContainer = lerp(Color.White, p, 0.2f))
        else base.copy(primary = p, onPrimary = Color.White, primaryContainer = lerp(Color.White, p, 0.16f), onPrimaryContainer = lerp(Color.Black, p, 0.6f))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val accent: Color @Composable get() = MaterialTheme.colorScheme.primary

// MARK: navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val UPLOAD = "upload"
    const val SAVED = "saved"
    const val MORE = "more"
    const val CHAT = "chat"
    const val ADMIN = "admin"
    const val FRIENDS = "friends"
    const val LECTURES = "lectures"
    const val HACKS = "hacks"
    const val NOTIFICATIONS = "notifications"
    const val FEED = "feed"
}

val LocalNav = staticCompositionLocalOf<NavController> { error("no nav") }

private fun enc(s: String) = Uri.encode(s)

fun NavController.openMaterial(m: Material) = navigate("material?u=${enc(m.u)}&t=${enc(m.t)}&s=${enc(m.s ?: "")}")
fun NavController.openPage(path: String, title: String) = navigate("page?p=${enc(path)}&t=${enc(title)}")
fun NavController.openFolder(id: String, title: String) = navigate("folder?id=${enc(id)}&t=${enc(title)}")
fun NavController.openWeb(url: String, title: String) = navigate("web?u=${enc(url)}&t=${enc(title)}")
fun NavController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

sealed class SiteTarget {
    data class Tab(val route: String) : SiteTarget()
    data class Page(val path: String, val title: String) : SiteTarget()
    data class Mat(val m: Material) : SiteTarget()
    data class External(val url: String) : SiteTarget()
}

object SiteRouter {
    val latestPaths = setOf(
        "/guides/latest", "/guides/latest_v2", "/portal/confirm_device", "/portal/indexuserportal",
        "/portal/agreement-page", "/portal/agreement-page_mobile", "/portal/agreement-page_desktop", "/portal/agreement-page_android",
    )

    /** Where a link from a page or the home screen should go */
    fun target(u: String, title: String): SiteTarget? {
        AB.sitePath(u)?.let { path ->
            return when (path) {
                "/" -> SiteTarget.Tab(Routes.HOME)
                "/guides/search" -> SiteTarget.Tab(Routes.SEARCH)
                "/guides/saved" -> SiteTarget.Tab(Routes.SAVED)
                "/upload/uploadmethod", "/upload", "/upload/dupload", "/upload/gupload", "/upload/googledrive_fileupload", "/upload/discord_fileupload" -> SiteTarget.Tab(Routes.UPLOAD)
                "/chat", "/chat/", "/chat/index" -> SiteTarget.Tab(Routes.CHAT)
                "/admin", "/admin/", "/admin/index" -> SiteTarget.Tab(if (Account.isAdmin) Routes.ADMIN else Routes.MORE)
                else -> SiteTarget.Page(path, title)
            }
        }
        val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: ""
        if (scheme == "http" || scheme == "https") {
            // chats and payments open in their own apps; files open in the in-app viewer
            // ASU's own sites (my.asu.edu.jo, edugate...) send an incomplete certificate chain that Android's in-app
            // viewer rejects, and Microsoft 365 needs its sign-in, so they open in the browser or their app
            if (host == "asu.edu.jo" || host.endsWith(".asu.edu.jo") || host.endsWith("cloud.microsoft") || host.endsWith(".microsoft.com") ||
                host.endsWith(".office.com") || host == "office.com") {
                return SiteTarget.External(u)
            }
            if (host.contains("whatsapp.com") || host == "wa.me" || host.contains("paypal.") || host.contains("t.me") || host.contains("instagram.com")) {
                return SiteTarget.External(u)
            }
            return SiteTarget.Mat(Material(u = u, t = title, s = host.removePrefix("www.")))
        }
        return SiteTarget.External(u) // mailto:, tel:, whatsapp:
    }
}

fun openExternal(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

fun openLink(nav: NavController, ctx: Context, u: String, title: String) {
    when (val t = SiteRouter.target(u, title)) {
        is SiteTarget.Tab -> nav.openTab(t.route)
        is SiteTarget.Page -> nav.openPage(t.path, t.title)
        is SiteTarget.Mat -> nav.openMaterial(t.m)
        is SiteTarget.External -> openExternal(ctx, t.url)
        null -> Unit
    }
}

// MARK: building blocks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    back: Boolean = true,
    large: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val nav = LocalNav.current
    val behavior = if (large) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val navIcon: @Composable () -> Unit = {
        if (back) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (large) LargeTopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = navIcon, actions = actions, colors = colors, scrollBehavior = behavior)
            else TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = navIcon, actions = actions, colors = colors, scrollBehavior = behavior)
        },
        content = content,
    )
}

@Composable
fun KindTile(kind: Kind, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.verticalGradient(listOf(kind.color.copy(alpha = 0.85f), kind.color))),
        contentAlignment = Alignment.Center,
    ) { Icon(kind.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
fun Badge(text: String, color: Color = accent) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Rounded white group, like an inset grouped list on iOS / a card on Android */
@Composable
fun Group(modifier: Modifier = Modifier, header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (header != null) Text(
            tr(header).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 8.dp),
        )
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
        if (footer != null) Text(
            footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
    }
}

@Composable
fun Divider() = HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color? = null,
    emoji: String? = null,
    badge: String? = null,
    trailing: String? = null,
    titleColor: Color? = null,
    rtl: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            emoji != null -> Text(emoji, fontSize = 22.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
            icon != null && iconColor != null -> Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconColor), contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            icon != null -> Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = if (rtl && !Lang.rtl) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge, color = titleColor ?: MaterialTheme.colorScheme.onSurface, textAlign = if (rtl) TextAlign.End else TextAlign.Start)
                if (badge != null) Badge(badge, if (badge == "NEW") Brand.green else Brand.orange)
            }
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = if (rtl) TextAlign.End else TextAlign.Start)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MaterialRow(m: Material, kind: Kind? = null, meta: String? = null, showSource: Boolean = true) {
    val removedFiles by RemovedFiles.ids.collectAsState()
    if (RemovedFiles.has(m.u, removedFiles)) return
    val nav = LocalNav.current
    val savedList by Saved.saved.collectAsState()
    val k = kind ?: AB.kind(m.u, m.t)
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (reporting) ReportDialog(ReportTarget("file", m.u, m.t)) { reporting = false }
    if (deleting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(if (k == Kind.FOLDER) "Remove from UniHub?" else "Delete from Drive?") },
        text = { Text(if (k == Kind.FOLDER) "It disappears from UniHub for everyone. The folder itself stays in Drive." else "It moves to the Drive trash and disappears from UniHub for everyone. A file owned by another Drive account is only removed from UniHub.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                deleting = false
                scope.launch {
                    try { adminDeleteUpload(m.u) }
                    catch (e: ApiError) { Toast.makeText(ctx, e.message ?: "Couldn't delete", Toast.LENGTH_LONG).show() }
                }
            }) { Text(if (k == Kind.FOLDER) "Remove from UniHub" else "Delete from Drive", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
    // hold a file to save or report it
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text(if (savedList.any { it.u == m.u }) "Remove from Saved" else "Save") }, onClick = { menu = false; Saved.toggle(m) })
        if (k != Kind.PAGE) DropdownMenuItem(text = { Text("Report this file") }, onClick = { menu = false; reporting = true })
        if (Account.user.value?.isAdmin == true && k != Kind.PAGE && AB.driveId(m.u) != null) {
            DropdownMenuItem(text = { Text(if (k == Kind.FOLDER) "Remove from UniHub" else "Delete from Drive", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
        }
    }
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (k == Kind.PAGE) nav.openPage(AB.sitePath(m.u) ?: m.u, m.t) else nav.openMaterial(m) },
            onLongClick = { menu = true },
        ).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindTile(k)
        Column(Modifier.weight(1f)) {
            Text(m.t.trim('_', ' ', ':'), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (AB.isNew(m.d)) Badge("NEW", Brand.green)
                if (m.tag == "pin") Badge("ALL FILES")
                if (m.tag == "warn") Badge("CLOSED", Brand.orange)
                Text(
                    listOfNotNull(k.label, meta ?: if (showSource) m.s else null).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (savedList.any { it.u == m.u }) Icon(Icons.Filled.Star, "Saved", tint = Brand.gold, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SectionTitle(title: String, arabic: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (arabic != null) Text(arabic, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.1f GB", b / 1073741824.0)
    b >= 1L shl 20 -> String.format("%.1f MB", b / 1048576.0)
    b >= 1L shl 10 -> String.format("%.0f KB", b / 1024.0)
    else -> "$b B"
}

/**
 * The same rounded group as [Group], but each row is its own lazy item, so long lists
 * (subject pages, search results) only build the rows on screen and scroll smoothly.
 */
fun <T> androidx.compose.foundation.lazy.LazyListScope.groupItems(
    list: List<T>,
    header: String? = null,
    key: ((T) -> Any)? = null,
    row: @Composable (T) -> Unit,
) {
    if (list.isEmpty()) return
    if (header != null) item(contentType = "groupHeader") {
        Text(
            tr(header).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, bottom = 6.dp, top = 14.dp),
        )
    }
    val last = list.lastIndex
    items(list.size, key = key?.let { k -> { i: Int -> k(list[i]) } }, contentType = { "groupRow" }) { i ->
        val r = 16.dp
        val shape = RoundedCornerShape(topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp, bottomStart = if (i == last) r else 0.dp, bottomEnd = if (i == last) r else 0.dp)
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column {
                if (i > 0) Divider()
                row(list[i])
            }
        }
    }
}
