package org.unihub.app

import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.draw.clip
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.AccountCircle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** A link on the home page or the More tab */
data class SiteLink(
    val emoji: String,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val added: String? = null, // yyyy-MM-dd; shows NEW for 30 days
    val web: String? = null,
    val tab: String? = null,
    val external: String? = null,
) {
    val shownBadge get() = if (AB.isNew(added)) "NEW" else badge
}

object Links {
    /** The three tools students use most, first on Home */
    val top = listOf(
        SiteLink("📝", "Rate your teachers", "See averages and rate anonymously.", web = "/requests/portal_to_rating"),
        SiteLink("👥", "Course matcher", "Find classmates with similar schedules", web = "/requests/matcher_confirm"),
        SiteLink("🔄", "Subject swap", "Trade a registered subject", web = "/swap"),
    )
    val quick = listOf(
        SiteLink("📅", "Calendar 2026–2027", "This year's important dates.", added = "2026-08-31", web = "/schedules/calendar27"),
        SiteLink("⏰", "My lectures", "Your timetable, with reminders.", tab = Routes.LECTURES),
        SiteLink("✅", "Exams & tasks", "Everything you have coming up.", web = "/app/planner"),
        SiteLink("🆕", "Latest uploads", "Newest PDFs, midterms, finals and quizzes.", web = "/guides/latest"),
        SiteLink("⚡", "Google Drive materials", "Search every uploaded file.", badge = "FAST", tab = Routes.SEARCH),
        SiteLink("🎯", "Focus timer", "Study in short sessions with breaks.", web = "/app/focus"),
        SiteLink("🙋", "Absence tracker", "Count missed lectures, stay under the limit.", added = "2026-11-08", web = "/app/absences"),
        SiteLink("🧮", "Final mark calculator", "What you need on the final to pass.", added = "2026-11-08", web = "/app/finalmark"),
        SiteLink("🃏", "Flashcards", "Make cards and test yourself.", added = "2026-11-08", web = "/app/flashcards"),
        SiteLink("🔥", "Study stats", "Your streak and minutes studied.", added = "2026-11-08", web = "/app/stats"),
        SiteLink("📥", "Recent uploads", "Latest study materials and resources.", web = "/portal/confirm_device"),
        SiteLink("📬", "Request a material", "Can't find it? Ask for it.", web = "/requests/student_request_portal"),
        SiteLink("📤", "Upload files", "Share your study materials.", tab = Routes.UPLOAD),
        SiteLink("➕", "Useful gate", "University resources in one place.", web = "/guides/links"),
    )
    val newHere = listOf(
        SiteLink("🎓", "New Student Guide", "Admissions, fees, majors, scholarships", added = "2026-09-13", web = "/guides/aspu_qa"),
        SiteLink("🔗", "ASU shortcuts", "My ASU, EduGate, Teams, absences", web = "/guides/asu"),
        SiteLink("🎬", "How to search for files", "Three ways to find any material", web = "/guides/tutorial"),
        SiteLink("👥", "Course WhatsApp groups", "Groups for courses, clubs and interests", web = "/guides/groups"),
        SiteLink("📊", "Grading scale", web = "/guides/grading-scale-asu"),
        SiteLink("📝", "Placement tests", web = "/guides/placement"),
        SiteLink("🏫", "University halls", "Type a hall number, find the building and floor", web = "/guides/halls"),
        SiteLink("🗺️", "Study plans", external = "https://drive.google.com/drive/folders/1QNvzQm4TjBg4x-dRpFp9roUBxdu2dQRY?usp=sharing"),
    )
    val schedules = listOf(
        SiteLink("📅", "Calendar 2026–2027", added = "2026-08-31", web = "/schedules/calendar27"),
        SiteLink("🗓️", "Academic calendar 2026", "Important dates and deadlines", web = "/schedules/cal2026"),
        SiteLink("🌙", "Ramadan lecture timetable", web = "/schedules/ramadan_timetable"),
        SiteLink("📅", "Calendar 2025–2026", badge = "OLD", web = "/schedules/asu_date_asu2025_2026"),
        SiteLink("🚌", "Bus times", badge = "OUTDATED", web = "/schedules/bus20251"),
    )
    val community = listOf(
        SiteLink("💬", "WhatsApp community", "Updates, shared files and help", external = AB.WHATSAPP),
        SiteLink("🖥️", "Upload your presentations", "For the campus display screen", web = "/upload/submit_file"),
        SiteLink("📋", "Submitted files (students)", "Files uploaded by students", web = "/upload/student_submissions_board"),
        SiteLink("📢", "Student ads & services", "Tutoring, design help, items for sale", web = "/requests/student_ads"),
        SiteLink("📸", "Campus event photos", web = "/guides/gallery"),
        SiteLink("📷", "Book a photoshoot", web = "/apps/photoshoot_booking"),
        SiteLink("⭐", "Rate our service", "10 quick questions", web = "/requests/service_rating"),
        SiteLink("🗳️", "Vote", "Community votes and polls", web = "/apps/voting"),
    )
    val help = listOf(
        SiteLink("❓", "FAQs", web = "/guides/faqs"),
        SiteLink("✉️", "Contact us", web = "/guides/contact"),
        SiteLink("🙏", "Thanks list", web = "/guides/thankslist"),
        SiteLink("📜", "WhatsApp group rules", web = "/legal/wa_tos"),
        SiteLink("📋", "Terms of Service", web = "/legal/uni_tos"),
    )
}

fun follow(l: SiteLink, nav: androidx.navigation.NavController, ctx: Context) {
    when {
        l.tab != null -> nav.openTab(l.tab)
        l.web != null -> nav.openPage(l.web, l.title)
        l.external != null -> {
            // Drive folders open natively; chats open in their app
            val id = AB.driveId(l.external)
            if (l.external.contains("drive.google.com") && id != null) nav.openFolder(id, l.title) else openExternal(ctx, l.external)
        }
    }
}

@Composable
fun LinkRows(links: List<SiteLink>) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    links.filter { !BuildConfig.STORE_BUILD || it.web !in Moderation.paidPaths }.forEachIndexed { i, l ->
        if (i > 0) Divider()
        ListRow(l.title, l.subtitle, emoji = l.emoji, badge = l.shownBadge) { follow(l, nav, ctx) }
    }
}

// MARK: Home

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val recent by Saved.recent.collectAsState()
    val saved by Saved.saved.collectAsState()
    val items by Library.items.collectAsState()
    val subjects by Library.subjects.collectAsState()
    var q by remember { mutableStateOf("") }

    val accentId by Prefs.accent.collectAsState()
    val notices by Notices.items.collectAsState()
    val noticesRead by Notices.lastRead.collectAsState()
    val unread = notices.any { it.created > noticesRead }
    val hidden by HomePrefs.hidden.collectAsState()
    val compact by HomePrefs.compact.collectAsState()
    val remembers by HomePrefs.remember.collectAsState()
    val me by Account.user.collectAsState()
    var continueMenu by remember { mutableStateOf(false) }
    fun on(id: String) = id !in hidden
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MeButton()
                Text("UniHub", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OnlineButton()
                if (me?.isAdmin == true) IconButton(onClick = { nav.navigate(Routes.ADMIN) }) { Icon(Icons.Filled.Shield, tr("Admin")) }
                IconButton(onClick = { nav.navigate(Routes.NOTIFICATIONS) }) {
                    if (unread) BadgedBox(badge = { androidx.compose.material3.Badge() }) { Icon(Icons.Filled.Notifications, tr("Notifications")) }
                    else Icon(Icons.Filled.NotificationsNone, tr("Notifications"))
                }
            }
        }
        if (on("online")) item { OnlineBox(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
        if (on("top")) {
        // three small squares in one row, right under Who's online
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Links.top.forEach { l ->
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f).heightIn(min = 96.dp).clip(RoundedCornerShape(16.dp)).clickable { follow(l, nav, ctx) },
                    ) {
                        Box {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) { Text(l.emoji, fontSize = 20.sp) }
                                Text(
                                    l.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                )
                            }
                            if (l.shownBadge != null) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(Brand.orange))
                        }
                    }
                }
            }
        }
        }
        if (on("weather")) item { WeatherCard() }
        if (on("calendar")) item { HomeCalendarCard() }
        if (on("lecture")) item { NextLectureCard() }
        if (on("due")) item { HomeDueCard() }
        if (on("due")) item { HomeStudyCard() }
        if (on("hero")) item {
            Box(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).clip(RoundedCornerShape(22.dp))
                    .background(heroBrush((Prefs.accents.firstOrNull { it.id == accentId } ?: Prefs.accents[0]).light))
                    .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Summaries, past papers and test banks for ASU students." + if (items.isNotEmpty()) " " + trf("%d materials in %d subjects.", items.size, subjects.size) else "",
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { AppState.pendingSearch.value = q; nav.openTab(Routes.SEARCH) }),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(Modifier.weight(1f)) {
                                    if (q.isEmpty()) Text(
                                        "Search materials, e.g. calculus final", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    inner()
                                }
                            }
                        },
                    )
                }
            }
        }
        item { SponsorCard() }
        if (on("continue") && remembers && recent.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    SectionTitle("Continue")
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp)) {
                        IconButton(onClick = { continueMenu = true }) { Icon(Icons.Filled.MoreHoriz, tr("Continue")) }
                        androidx.compose.material3.DropdownMenu(continueMenu, { continueMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Clear the Continue list") }, onClick = { continueMenu = false; Saved.clearRecent() })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Hide Continue") }, onClick = { continueMenu = false; HomePrefs.setShown("continue", false) })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Customize") }, onClick = { continueMenu = false; nav.navigate("customize") })
                        }
                    }
                }
            }
            item { Group { recent.take(4).forEachIndexed { i, s -> if (i > 0) Divider(); MaterialRow(s.material) } } }
        }
        if (on("saved") && saved.isNotEmpty()) {
            item { SectionTitle("Saved") }
            item { Group { saved.take(4).forEachIndexed { i, s -> if (i > 0) Divider(); MaterialRow(s.material) } } }
        }
        if (on("quick")) {
        item { SectionTitle("Quick links", "روابط سريعة") }
        items(Links.quick.chunked(if (compact) 3 else 2)) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { l ->
                    Surface(
                        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f).height(if (compact) 92.dp else 128.dp).clickable { follow(l, nav, ctx) },
                    ) {
                        Column(Modifier.padding(if (compact) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(l.emoji, fontSize = if (compact) 20.sp else 26.sp)
                                Spacer(Modifier.weight(1f))
                                l.shownBadge?.let { Badge(it, if (it == "NEW") Brand.green else Brand.orange) }
                            }
                            Text(l.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!compact) l.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                repeat((if (compact) 3 else 2) - pair.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        }
        if (on("schedules")) {
            item { SectionTitle("Calendars & schedules") }
            item { Group { LinkRows(Links.schedules) } }
        }
        if (on("subjects")) {
            item { SectionTitle("Subjects", "المواد") }
            item {
                Group {
                    ListRow("All subjects", trf("%d subjects by faculty", subjects.size), icon = Icons.Filled.GridView) { nav.openPage("/banks/subjects", "All subjects") }
                }
            }
        }
        if (on("newhere")) {
            item { SectionTitle("New here?", "طالب جديد") }
            item { Group { LinkRows(Links.newHere) } }
        }
        if (on("community")) {
            item { SectionTitle("Community & services") }
            item { Group { LinkRows(Links.community) } }
        }
        item { SectionTitle("Help") }
        item { Group { LinkRows(Links.help) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// MARK: Saved

@Composable
fun SavedScreen(back: Boolean = false) {
    val saved by Saved.saved.collectAsState()
    val recent by Saved.recent.collectAsState()
    Screen("Saved", back = back, large = true, actions = {
        if (recent.isNotEmpty()) TextButton(onClick = { Saved.clearRecent() }) { Text("Clear recent") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (saved.isEmpty() && recent.isEmpty()) {
                item { EmptyState(Icons.Filled.StarBorder, "Nothing saved yet", "Tap the star on any material to keep it here, even offline.") }
            }
            groupItems(saved, header = trf("Saved · %d", saved.size), key = { "s" + it.u }) { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { MaterialRow(s.material) }
                    IconButton(onClick = { Saved.remove(s.u) }) { Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (recent.isNotEmpty()) item {
                Group(header = "Recently opened") { recent.take(15).forEachIndexed { i, s -> if (i > 0) Divider(); MaterialRow(s.material) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// MARK: More

@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val appearance by Prefs.appearance.collectAsState()
    val user by Account.user.collectAsState()
    val donate by AppConfig.donate.collectAsState()
    val latest by AppConfig.latest.collectAsState()
    val checking by AppConfig.checking.collectAsState()
    val items by Library.items.collectAsState()
    val subjects by Library.subjects.collectAsState()
    var sizes by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sizes = Storage.measure(ctx) }

    Screen("More", back = false, large = true) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(header = "You") {
                    val u = user
                    if (u != null) Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate("account") }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Avatar(u.name, u.color, 44.dp, u.isAdmin, u.avatar)
                        Column(Modifier.weight(1f)) {
                            Text(u.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("@${u.username}" + if (u.isAdmin) " · Admin" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else ListRow("Sign in or create an account", "Chat with students, synced with asubank.org", icon = Icons.Filled.AccountCircle) { Account.signInShown.value = true }
                    Divider()
                    ListRow("Theme", listOf("Match my phone", "Light", "Dark", "Pitch black", "Midnight", "Forest night", "Plum", "Sand", "Sky", "Blush")[appearance.coerceIn(0, 9)], icon = Icons.Filled.Palette) { nav.navigate("theme") }
                    Divider()
                    ListRow("Customize", icon = Icons.Filled.Tune) { nav.navigate("customize") }
                    Divider()
                    ListRow("Safety & legal", icon = Icons.Filled.VerifiedUser) { nav.navigate("safety") }
                    Divider()
                    ListRow("Report something", icon = Icons.Filled.Flag) { nav.navigate("report") }
                    Divider()
                    ListRow("Who's online", icon = Icons.Filled.Circle, iconColor = Color(0xFF22B14C)) { nav.navigate("online") }
                    Divider()
                    ListRow("Customize tab bar", icon = Icons.Filled.Dashboard) { nav.navigate("tabBar") }
                    Divider()
                    ListRow("App icon", icon = Icons.Filled.Apps) { nav.navigate("appIcon") }
                    Divider()
                    ListRow("Language", tr(Lang.choices.first { it.first == Lang.choice.value }.second), icon = Icons.Filled.Language) { nav.navigate("language") }
                    Divider()
                    ListRow("Upload files", icon = Icons.Filled.CloudUpload) { nav.openTab(Routes.UPLOAD) }
                    Divider(); ListRow("Saved", icon = Icons.Filled.Star) { nav.navigate("savedList") }
                    Divider(); ListRow("Notifications", icon = Icons.Filled.Notifications) { nav.navigate(Routes.NOTIFICATIONS) }
                    Divider(); ListRow("Friends", icon = Icons.Filled.People) { nav.navigate(Routes.FRIENDS) }
                    Divider(); ListRow("Subject swap", icon = Icons.Filled.SwapHoriz) { nav.navigate(Routes.SWAPS) }
                    Divider(); ListRow("Feed", icon = Icons.Filled.DynamicFeed) { nav.navigate(Routes.FEED) }
                    Divider(); ListRow("Share UniHub", icon = Icons.Filled.Share) { shareApp(ctx) }
                    Divider(); ListRow("My lectures", icon = Icons.Filled.Alarm) { nav.navigate(Routes.LECTURES) }
                    Divider(); ListRow("Exams & tasks", icon = Icons.Filled.Checklist) { nav.openPage("/app/planner", "") }
                    Divider(); ListRow("Focus timer", icon = Icons.Filled.Timer) { nav.openPage("/app/focus", "") }
                    Divider(); ListRow("Study stats", icon = Icons.Filled.LocalFireDepartment) { nav.openPage("/app/stats", "") }
                    Divider(); ListRow("Absence tracker", icon = Icons.Filled.EventBusy) { nav.openPage("/app/absences", "") }
                    Divider(); ListRow("Final mark calculator", icon = Icons.Filled.Calculate) { nav.openPage("/app/finalmark", "") }
                    Divider(); ListRow("Flashcards", icon = Icons.Filled.Style) { nav.openPage("/app/flashcards", "") }
                    Divider(); ListRow("Life hacks", icon = Icons.Filled.Lightbulb) { nav.navigate(Routes.HACKS) }
                    if (u != null) { Divider(); ListRow("My profile", icon = Icons.Filled.AccountCircle) { nav.navigate("profile/${u.id}") } }
                    Divider()
                    ListRow("Student submissions", "Presentations for the campus screen", icon = Icons.Filled.Slideshow) { nav.navigate("submissions?admin=") }
                }
            }
            item { Group(header = "Calendars & schedules") { LinkRows(Links.schedules) } }
            item {
                Group(header = "All subjects") {
                    ListRow("Subjects directory", icon = Icons.Filled.GridView) { nav.openPage("/banks/subjects", "All subjects") }
                    Divider()
                    ListRow("Materials by college", icon = Icons.Filled.BarChart) { nav.openPage("/app/coverage", "Materials by college") }
                    Divider()
                    ListRow("Latest uploads", icon = Icons.Filled.AutoAwesome) { nav.openPage("/guides/latest", "Latest uploads") }
                    Divider()
                    ListRow("Request a material", icon = Icons.Filled.MoveToInbox) { nav.openPage("/requests/student_request_portal", "Request a material") }
                    Divider()
                    ListRow("ASU resources (Useful gate)", icon = Icons.Filled.Apps) { nav.openPage("/guides/links", "ASU resources") }
                }
            }
            item { Group(header = "New students") { LinkRows(Links.newHere) } }
            item { Group(header = "Community & services") { LinkRows(Links.community) } }
            item { Group(header = "Help") { LinkRows(Links.help) } }
            if (donate) item {
                Group(header = "Support UniHub", footer = "UniHub is free and student-run. Donations help keep it running.") {
                    ListRow("Donate with PayPal", "paypal.me/necro144", icon = Icons.Filled.CreditCard, iconColor = Color(0xFF0070BA)) {
                        openExternal(ctx, "https://www.paypal.com/paypalme/necro144")
                    }
                    Divider()
                    ListRow("CliQ alias: ALANQ99", "Bank of Jordan · tap to copy", icon = Icons.Filled.Payments, iconColor = Color(0xFF25A244)) {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("CliQ alias", "ALANQ99"))
                        Toast.makeText(ctx, "Copied ALANQ99", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            item {
                val s = sizes
                Group(header = "Storage", footer = "Clearing the cache frees space and reloads files from asubank.org. Your saved materials and settings stay.") {
                    ListRow("App", icon = Icons.Filled.Inventory2, trailing = s?.let { formatBytes(it.first) } ?: "…")
                    Divider()
                    ListRow("Documents & data", icon = Icons.Filled.Storage, trailing = s?.let { formatBytes(it.third) } ?: "…")
                    Divider()
                    ListRow("Cache", icon = Icons.Filled.Inventory2, trailing = s?.let { formatBytes(it.second) } ?: "…")
                    Divider()
                    ListRow(if (clearing) "Clearing…" else "Clear cache", icon = Icons.Filled.Delete, titleColor = MaterialTheme.colorScheme.error) {
                        if (!clearing) confirmClear = true
                    }
                }
            }
            item {
                Group(header = "About") {
                    ListRow("Version", icon = Icons.Filled.Info, trailing = "${AppConfig.current} (${BuildConfig.VERSION_CODE})")
                    Divider()
                    ListRow(
                        if (AppConfig.updateAvailable) trf("Update to %s", latest ?: "") else "Check for updates",
                        icon = Icons.Filled.SystemUpdate,
                        trailing = if (checking) "Checking…" else if (latest != null && !AppConfig.updateAvailable) "Up to date" else null,
                    ) {
                        if (AppConfig.updateAvailable) AppConfig.apkUrl.value?.let { openExternal(ctx, it) }
                        else scope.launch {
                            AppConfig.check(force = true)
                            if (!AppConfig.updateAvailable) Toast.makeText(ctx, "You have the latest version", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Divider()
                    ListRow("What's New", icon = Icons.Filled.NewReleases) { nav.openPage("/app/whatsnew", "What's New") }
                    Divider()
                    ListRow("About & terms", icon = Icons.Filled.VolunteerActivism) { showTerms = true }
                    Divider()
                    ListRow("Contact us", icon = Icons.Filled.Email) { nav.openPage("/guides/contact", "Contact us") }
                    Divider()
                    ListRow("Privacy policy", icon = Icons.Filled.Policy) { nav.openPage("/legal/privacy", "Privacy policy") }
                    Divider()
                    ListRow("Community rules", icon = Icons.Filled.VerifiedUser) { nav.openPage("/legal/community", "Community rules") }
                    Divider()
                    ListRow("Report a problem", icon = Icons.Filled.Flag) { nav.navigate("report") }
                }
            }
            item {
                Group(footer = "UniHub ${AppConfig.current} · " + trf("%d materials in %d subjects.", items.size, subjects.size) + "\n" + tr("Peer-verified study materials by Applied Science University students, for ASU students.")) {
                    ListRow("Open asubank.org in the browser", icon = Icons.Filled.OpenInBrowser) { openExternal(ctx, AB.SITE) }
                    Divider()
                    ListRow("Share UniHub", icon = Icons.Filled.Share) {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "UniHub — summaries, past papers and test banks for ASU students: ${AB.SITE}"), "Share UniHub"))
                    }
                    Divider()
                    ListRow("Refresh materials", icon = Icons.Filled.Refresh) { scope.launch { Library.load(); Pages.load() } }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear the cache?") },
        text = { Text("Downloaded files, images and web data will be fetched again when you need them.") },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                clearing = true
                scope.launch { Storage.clear(ctx); sizes = Storage.measure(ctx); clearing = false; Toast.makeText(ctx, "Cache cleared", Toast.LENGTH_SHORT).show() }
            }) { Text("Clear cache", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    if (showTerms) WelcomeTerms(onClose = { showTerms = false })
}
