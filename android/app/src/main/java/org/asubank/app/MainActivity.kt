package org.asubank.app

import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.People
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Forum
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppActivity : ComponentActivity() {
    /** asubank.org links opened from other apps */
    private val incoming = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Disk.ctx = applicationContext
        Saved.init(applicationContext)
        Lang.init(applicationContext)
        DriveLinks.init()
        Prefs.appearance.value = Saved.prefs.getInt("appearance", 0)
        Prefs.accent.value = Saved.prefs.getInt("accent", 0)
        Account.init()
        ChatNotifier.init(applicationContext)
        ChatNotifier.start(applicationContext)
        TabPrefs.init()
        Lectures.init()
        Lectures.schedule(applicationContext)
        intent?.getStringExtra("openTab")?.let { ChatNotifier.pendingTab.value = it; splashDone = true }
        intent?.getStringExtra("openRoom")?.let { ChatNotifier.pendingRoom.value = it }
        // CI screenshots: --es startTab search --es startQuery calculus --ez acceptTerms true
        intent?.getStringExtra("startQuery")?.let { AppState.pendingSearch.value = it }
        if (intent?.getBooleanExtra("acceptTerms", false) == true) Saved.prefs.edit().putBoolean(TERMS_KEY, true).apply()
        intent?.getIntExtra("appearance", -1)?.takeIf { it >= 0 }?.let { Prefs.appearance.value = it }
        val startTab = intent?.getStringExtra("startTab")
        if (intent?.getStringExtra("openRoom") != null) splashDone = true
        if (startTab != null || intent?.hasExtra("startPath") == true || intent?.getBooleanExtra("noSplash", false) == true) splashDone = true
        incoming.value = intent?.dataString ?: (intent?.getStringExtra("startPath") ?: intent?.getStringExtra("openPath"))?.let { AB.SITE + it }
        if (intent?.hasExtra("openPath") == true) splashDone = true
        if (intent?.getBooleanExtra("rateIntro", false) == true) Saved.prefs.edit().putBoolean("rate.intro.v1", true).apply()
        if (intent?.getBooleanExtra("matcherIntro", false) == true) Saved.prefs.edit().putBoolean("matcher.intro.v1", true).apply()
        setContent { AsuTheme { LangProvider { App(startTab, incoming) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("openRoom")?.let { ChatNotifier.pendingRoom.value = it }
        intent.getStringExtra("openTab")?.let { ChatNotifier.pendingTab.value = it }
        incoming.value = intent.dataString ?: intent.getStringExtra("openPath")?.let { AB.SITE + it }
    }
}

/** The logo splash shows once per process start */
var splashDone = false

const val TERMS_KEY = "terms.accepted.v1"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val homeTab = Tab(Routes.HOME, "Home", Icons.Filled.Home)
private val moreTab = Tab(Routes.MORE, "More", Icons.Filled.MoreHoriz)
private val adminTab = Tab(Routes.ADMIN, "Admin", Icons.Filled.Shield)
/** What people can put between Home and More (More > Customize tab bar) */
private val choosableTabs = listOf(
    Tab(Routes.SEARCH, "Search", Icons.Filled.Search),
    Tab(Routes.CHAT, "Chat", Icons.Filled.Forum),
    Tab(Routes.FRIENDS, "Friends", Icons.Filled.People),
    Tab(Routes.SAVED, "Saved", Icons.Filled.Star),
    Tab(Routes.LECTURES, "My lectures", Icons.Filled.Alarm),
    Tab(Routes.HACKS, "Life hacks", Icons.Filled.Lightbulb),
)
private val allTabs = listOf(homeTab, moreTab, adminTab) + choosableTabs

@Composable
fun App(startTab: String?, incoming: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var accepted by remember { mutableStateOf(Saved.prefs.getBoolean(TERMS_KEY, false)) }
    val mustUpdate by AppConfig.mustUpdate.collectAsState()
    val offerUpdate by AppConfig.offerUpdate.collectAsState()
    val link by incoming.collectAsState()
    var lastTab by remember { mutableStateOf(Routes.HOME) }
    val user by Account.user.collectAsState()
    val signInShown by Account.signInShown.collectAsState()
    val pendingRoom by ChatNotifier.pendingRoom.collectAsState()
    val chosenTabs by TabPrefs.chosen.collectAsState()
    val socialUnread by ChatNotifier.socialUnread.collectAsState()
    val pendingTab by ChatNotifier.pendingTab.collectAsState()
    val isAdmin = user?.isAdmin == true
    val middleTabs = chosenTabs.take(if (isAdmin) 2 else 3).mapNotNull { id -> choosableTabs.firstOrNull { it.route == id } }
    val barTabs = listOf(homeTab) + middleTabs + (if (isAdmin) listOf(adminTab) else emptyList()) + moreTab
    val inBar: (String) -> Boolean = { r -> barTabs.any { it.route == r } }
    LaunchedEffect(pendingTab) {
        pendingTab?.let { t ->
            ChatNotifier.pendingTab.value = null
            if (inBar(t)) { lastTab = t; nav.openTab(t) } else nav.navigate(t)
        }
    }
    val earlyThanks by Account.earlyThanks.collectAsState()
    if (earlyThanks) AlertDialog(
        onDismissRequest = { Account.earlyThanks.value = false },
        title = { Text("Thank you for joining early! 🌱") },
        text = { Text("You joined ASU Bank in its early days, so your profile now has the Early supporter badge. Thank you for helping students at ASU.") },
        confirmButton = { TextButton(onClick = { Account.earlyThanks.value = false }) { Text("You're welcome") } },
    )
    var splash by remember { mutableStateOf(!splashDone) }
    LaunchedEffect(Unit) { if (splash) { delay(600); splashDone = true; splash = false } }
    LaunchedEffect(route) { if (allTabs.any { it.route == route }) lastTab = route ?: Routes.HOME }
    LaunchedEffect(pendingRoom) { if (pendingRoom != null && route != Routes.CHAT) nav.openTab(Routes.CHAT) }
    // Android 13+ asks before showing notifications
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(user?.id) {
        if (user != null && Build.VERSION.SDK_INT >= 33 && !ChatNotifier.canNotify(ctx) && !Saved.prefs.getBoolean("notify.asked", false)) {
            Saved.prefs.edit().putBoolean("notify.asked", true).apply()
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (user != null) ChatNotifier.start(ctx)
    }

    LaunchedEffect(Unit) {
        launch { Library.load(); if (DriveLinks.refresh()) Library.reindex() }
        launch { Pages.load() }
        launch { SearchHelp.load() }
    }
    // Check for a new version on launch and whenever the app comes back
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { AppConfig.check() } }
    // New chat messages while the app is open (the background worker covers the rest)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            Account.refresh()
            while (true) { if (Account.signedIn) ChatNotifier.check(ctx); delay(25_000) }
        }
    }
    LaunchedEffect(startTab) { if (startTab != null && startTab != Routes.HOME) nav.openTab(startTab) }
    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        incoming.value = null
        val p = AB.sitePath(l)
        val q = android.net.Uri.parse(l).getQueryParameter("q")
        if (p == "/guides/search" && q != null) { AppState.pendingSearch.value = q; nav.openTab(Routes.SEARCH) } else openLink(nav, ctx, l, "")
    }

    CompositionLocalProvider(LocalNav provides nav) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    barTabs.forEach { t ->
                        val selected = lastTab == t.route
                        NavigationBarItem(
                            selected = selected,
                            // the tab you are already on goes back to its first screen
                            onClick = {
                                if (lastTab == t.route) { if (!nav.popBackStack(t.route, false)) nav.openTab(t.route) }
                                else { lastTab = t.route; nav.openTab(t.route) }
                            },
                            icon = {
                                if (t.route == Routes.FRIENDS && socialUnread > 0) BadgedBox(badge = { androidx.compose.material3.Badge { Text("$socialUnread") } }) { Icon(t.icon, null) }
                                else Icon(t.icon, null)
                            },
                            label = { Text(tr(t.label)) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            },
        ) { pad ->
            val tabRoutes = remember { allTabs.map { it.route }.toSet() + Routes.UPLOAD }
            fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
                initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes
            NavHost(
                nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad),
                // tabs cross-fade; pushed screens slide in like iOS
                enterTransition = { if (betweenTabs()) fadeIn(tween(180)) else slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260)) },
                exitTransition = { if (betweenTabs()) fadeOut(tween(140)) else slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(260)) },
                popEnterTransition = { if (betweenTabs()) fadeIn(tween(180)) else slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 8 } + fadeIn(tween(260)) },
                popExitTransition = { if (betweenTabs()) fadeOut(tween(140)) else slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                composable(Routes.HOME) { HomeScreen() }
                composable(Routes.SEARCH) { SearchScreen() }
                composable(Routes.UPLOAD) { UploadScreen() }
                composable(Routes.SAVED) { SavedScreen(back = !inBar(Routes.SAVED)) }
                composable(Routes.FRIENDS) { FriendsScreen(back = !inBar(Routes.FRIENDS)) }
                composable(Routes.LECTURES) { LecturesScreen(back = !inBar(Routes.LECTURES)) }
                composable(Routes.HACKS) { LifeHacksScreen(back = !inBar(Routes.HACKS)) }
                composable("tabBar") { TabBarScreen() }
                composable("appIcon") { AppIconScreen() }
                composable("sounds") { SoundsScreen() }
                composable("groupRequest") { GroupRequestScreen() }
                composable("adminGroups") { AdminGroupRequestsScreen() }
                composable(Routes.MORE) { MoreScreen() }
                composable(Routes.CHAT) { ChatTab() }
                composable(Routes.ADMIN) { if (user?.isAdmin == true) AdminTab() else SignInPrompt("Admins only", "Sign in with an admin account to manage ASU Bank.") }
                composable("room/{id}") { e -> ChatRoomScreen(e.arguments?.getString("id") ?: "general") }
                composable("account") { AccountScreen() }
                composable("profile/{id}") { e -> ProfileScreen(e.arguments?.getString("id") ?: "") }
                composable("online") { OnlineScreen() }
                composable("mutedRooms") { MutedRoomsScreen() }
                composable("adminRooms") { AdminRoomsScreen() }
                composable("language") { LanguageScreen() }
                composable("rating") { ServiceRatingScreen() }
                composable("voting") { VotingScreen() }
                composable("theme") { ThemeScreen() }
                composable("savedList") { SavedScreen(back = true) }
                composable("adminUsers") { AdminUsersScreen() }
                composable("adminUser") { AdminUserScreen() }
                composable("adminLog") { AdminLogScreen() }
                composable("adminReports") { AdminReportsScreen() }
                composable("blocked") { BlockedScreen() }
                composable("submissions?admin={admin}", listOf(navArgument("admin") { defaultValue = "" })) { e ->
                    SubmissionsScreen(admin = e.arguments?.getString("admin") == "1" && user?.isAdmin == true)
                }
                composable(
                    "material?u={u}&t={t}&s={s}",
                    listOf(navArgument("u") { type = NavType.StringType; defaultValue = "" }, navArgument("t") { defaultValue = "" }, navArgument("s") { defaultValue = "" }),
                ) { e ->
                    val a = e.arguments
                    MaterialScreen(Material(u = a?.getString("u") ?: "", t = a?.getString("t") ?: "", s = a?.getString("s")?.ifEmpty { null }))
                }
                composable("page?p={p}&t={t}", listOf(navArgument("p") { defaultValue = "/" }, navArgument("t") { defaultValue = "" })) { e ->
                    SitePageScreen(e.arguments?.getString("p") ?: "/", e.arguments?.getString("t") ?: "")
                }
                composable("folder?id={id}&t={t}", listOf(navArgument("id") { defaultValue = "" }, navArgument("t") { defaultValue = "" })) { e ->
                    Screen(e.arguments?.getString("t") ?: "Folder") { p -> Box(Modifier.padding(p)) { DriveFolderList(e.arguments?.getString("id") ?: "", e.arguments?.getString("t") ?: "") } }
                }
                composable("web?u={u}&t={t}", listOf(navArgument("u") { defaultValue = AB.SITE }, navArgument("t") { defaultValue = "" })) { e ->
                    WebScreen(e.arguments?.getString("t") ?: "", e.arguments?.getString("u") ?: AB.SITE)
                }
            }
        }

        if (signInShown) SignInDialog(onClose = { Account.signInShown.value = false })

        if (mustUpdate) UpdateRequired()
        else if (!accepted) WelcomeTerms(onAccept = { Saved.prefs.edit().putBoolean(TERMS_KEY, true).apply(); accepted = true })

        if (offerUpdate && !mustUpdate && !splash) {
            AlertDialog(
                onDismissRequest = { AppConfig.offerUpdate.value = false },
                icon = { Icon(Icons.Filled.SystemUpdate, null) },
                title = { Text("Update available") },
                text = { Text("ASU Bank ${AppConfig.latest.value ?: ""} is out. You have ${AppConfig.current}." + (AppConfig.notes.value?.let { "\n\n$it" } ?: "")) },
                confirmButton = {
                    TextButton(onClick = { AppConfig.offerUpdate.value = false; AppConfig.apkUrl.value?.let { openExternal(ctx, it) } }) { Text("Download") }
                },
                dismissButton = { TextButton(onClick = { AppConfig.offerUpdate.value = false }) { Text("Later") } },
            )
        }

        AnimatedVisibility(visible = splash, enter = fadeIn(), exit = fadeOut(tween(350))) { Splash() }
    }
}

@Composable
private fun UpdateRequired() {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Icon(Icons.Filled.SystemUpdate, null, tint = accent, modifier = Modifier.size(64.dp))
                Text("Update required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    trf("This version of ASU Bank (%s) no longer works. Please install %s to keep using the app.", AppConfig.current, AppConfig.latest.value ?: tr("the latest version")),
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("يرجى تحديث التطبيق للاستمرار.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { AppConfig.apkUrl.value?.let { openExternal(ctx, it) } }, modifier = Modifier.fillMaxWidth()) { Text("Download the update") }
            }
        }
    }
}

/** Terms everyone accepts once, after the app first loads (also opened from More) */
@Composable
fun WelcomeTerms(onAccept: (() -> Unit)? = null, onClose: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var agree by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { onClose?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = onClose != null, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(76.dp).background(Brand.accent, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MenuBook, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        Text("Welcome to ASU Bank", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (onAccept != null) "Please read this before you start" else "About this app", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Group(header = "About this app") {
                        Term(Icons.Filled.Favorite, "A volunteer, non-profit project", "ASU Bank is free and run by volunteers. Nobody makes money from it.")
                        Divider()
                        Term(Icons.Filled.AccountBalance, "Not from the university", "This app is not made by, owned by, or affiliated with Applied Science Private University (ASPU).")
                        Divider()
                        Term(Icons.Filled.Person, "Made by a fellow student", "It's built by a college friend who studies English Language and Literature and wants to help all ASPU students search and find what they need.")
                        Divider()
                        Term(Icons.Filled.VerifiedUser, "Use materials responsibly", "Files are shared by students. Check them against your official course resources.")
                    }
                    Group(header = "بالعربية") {
                        Text(
                            "ASU Bank مشروع تطوعي غير ربحي، وليس تابعاً لجامعة العلوم التطبيقية الخاصة ولا صادراً عنها. أنشأه زميل جامعي يدرس اللغة الإنجليزية وآدابها، بهدف مساعدة جميع طلاب الجامعة في البحث عن احتياجاتهم والوصول إليها بسهولة.",
                            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    Group {
                        ListRow("Read the full Terms of Service", icon = Icons.Filled.MenuBook) { openExternal(ctx, "${AB.SITE}/legal/uni_tos") }
                        if (onAccept != null) {
                            Divider()
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = agree, onCheckedChange = { agree = it })
                                Text("I understand and agree / أوافق", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (onAccept != null) Button(onClick = onAccept, enabled = agree, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Accept and continue") }
                        else Button(onClick = { onClose?.invoke() }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors()) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Term(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
