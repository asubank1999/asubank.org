package org.asubank.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    background = Color.Black, onBackground = Color(0xFFF2F2F7),
    surface = Color.Black, onSurface = Color(0xFFF2F2F7),
    surfaceContainerLowest = Color(0xFF1C1C1E), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFFA1A1AA),
    outlineVariant = Color(0xFF38383A),
)

object Prefs {
    /** 0 system, 1 light, 2 dark */
    val appearance = MutableStateFlow(0)
    fun setAppearance(v: Int) { appearance.value = v; Saved.prefs.edit().putInt("appearance", v).apply() }
}

@Composable
fun AsuTheme(content: @Composable () -> Unit) {
    val a by Prefs.appearance.collectAsState()
    val isDark = when (a) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (isDark) dark else light, content = content)
}

val accent: Color @Composable get() = MaterialTheme.colorScheme.primary

// MARK: navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val UPLOAD = "upload"
    const val SAVED = "saved"
    const val MORE = "more"
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
                "/upload/uploadmethod", "/upload" -> SiteTarget.Tab(Routes.UPLOAD)
                else -> SiteTarget.Page(path, title)
            }
        }
        val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: ""
        if (scheme == "http" || scheme == "https") {
            // chats and payments open in their own apps; files open in the in-app viewer
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
            header.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Column(Modifier.weight(1f), horizontalAlignment = if (rtl) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor ?: MaterialTheme.colorScheme.onSurface, textAlign = if (rtl) TextAlign.End else TextAlign.Start)
                if (badge != null) Badge(badge, if (badge == "NEW") Brand.green else Brand.orange)
            }
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = if (rtl) TextAlign.End else TextAlign.Start)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MaterialRow(m: Material, kind: Kind? = null, meta: String? = null, showSource: Boolean = true) {
    val nav = LocalNav.current
    val savedList by Saved.saved.collectAsState()
    val k = kind ?: AB.kind(m.u, m.t)
    Row(
        Modifier.fillMaxWidth().clickable {
            if (k == Kind.PAGE) nav.openPage(AB.sitePath(m.u) ?: m.u, m.t) else nav.openMaterial(m)
        }.padding(horizontal = 16.dp, vertical = 10.dp),
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
