package org.asubank.app

import android.widget.Toast
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Delete
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val arabic = Regex("[؀-ۿ]")
fun isArabic(s: String) = arabic.findAll(s).count() > s.length / 4

/** Shows any asubank.org path as the best native screen */
@Composable
fun SitePageScreen(path: String, title: String) {
    val pages by Pages.pages.collectAsState()
    val loaded by Pages.loaded.collectAsState()
    val subjects by Library.subjects.collectAsState()
    val page = pages[path]
    when {
        path == "/apps/photoshoot_booking" -> PhotoshootScreen()
        BuildConfig.STORE_BUILD && path in Moderation.paidPaths -> StoreNoticeScreen()
        path == "/banks/subjects" -> SubjectsDirectory()
        path.startsWith("/banks/") && subjects.any { it.p == path } -> SubjectScreen(subjects.first { it.p == path })
        path in SiteRouter.latestPaths -> LatestScreen()
        path == "/guides/halls" || path == "/guides/colleges_hall" -> HallsScreen()
        path == "/requests/portal_to_rating" || path == "/requests/rating" -> TeacherRatingsScreen()
        path == "/requests/matcher" || path == "/requests/matcher_confirm" -> CourseMatcherScreen()
        path in setOf("/requests/student_request_portal", "/requests/request", "/requests/student_request_main") -> RequestMaterialScreen()
        path == "/upload/suggest_to_whatsapp" -> SuggestMaterialScreen()
        path == "/app/whatsnew" -> WhatsNewScreen()
        path == "/app/coverage" -> CollegeCoverageScreen()
        path == "/requests/student_ads" -> StudentAdsScreen()
        path == "/apps/donate" -> DonateScreen()
        path == "/app/theme" -> ThemeScreen()
        path == "/app/planner" -> PlannerScreen()
        path == "/app/focus" -> FocusTimerScreen()
        path == "/app/language" -> LanguageScreen()
        path == "/requests/service_rating" -> ServiceRatingScreen()
        path == "/apps/voting" -> VotingScreen()
        path in setOf("/upload/submit_file", "/upload/student_submissions_board", "/upload/submissions") -> SubmissionsScreen()
        path == "/apps/adminpanel_subs" -> SubmissionsScreen(admin = Account.isAdmin)
        path in setOf("/account", "/account/", "/account/index") -> AccountScreen()
        path == "/guides/tutorial" -> SearchGuideScreen()
        path == "/guides/grading-scale-asu" -> GradingScaleScreen()
        path == "/guides/thankslist" -> ThanksScreen(page)
        path == "/guides/groups" && page != null -> GroupsScreen(page)
        path == "/guides/importantlinks" && pages["/guides/links"] != null -> NativePage("/guides/links", pages.getValue("/guides/links"), title)
        page != null && !page.cal.isNullOrEmpty() -> CalendarScreen(if (page.t.length > 24 || page.t.isEmpty()) "Academic calendar" else page.t, path, page.cal)
        page != null && page.web == 1 -> WebScreen(title.ifEmpty { page.t }, AB.absolute(path), allowZoom = false)
        page != null -> NativePage(path, page, title)
        !loaded -> Screen(title) { p -> Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else -> WebScreen(title, AB.absolute(path), allowZoom = false)
    }
}

private data class PageSection(val title: String?, val ar: String?, val blocks: MutableList<PageBlock> = mutableListOf())

@Composable
fun NativePage(path: String, page: SitePage, title: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var arabicFaq by rememberSaveable { mutableStateOf(false) }
    val sections = remember(page) {
        val out = mutableListOf(PageSection(null, null))
        for (b in page.b) {
            if (b.h != null) out.add(PageSection(b.h, b.ar)) else out.last().blocks.add(b)
        }
        out.filter { it.title != null || it.blocks.isNotEmpty() }
    }
    val head = page.b.firstOrNull { it.head != null }?.head
    Screen(page.t.ifEmpty { title }, large = true, actions = {
        IconButton(onClick = {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, AB.absolute(path)), "Share"))
        }) { Icon(Icons.Filled.Share, "Share") }
        IconButton(onClick = { openExternal(ctx, AB.absolute(path)) }) { Icon(Icons.Filled.OpenInBrowser, "Open in browser") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (head != null && (head.sub != null || head.eyebrow != null || head.ar != null)) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    head.eyebrow?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold) }
                    head.sub?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    head.ar?.takeIf { !Lang.rtl }?.let { Text(it, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) }
                }
            }
            if (page.b.any { b -> b.faq?.any { it.qa != null } == true }) item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SegmentedButton(!arabicFaq, { arabicFaq = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("English") }
                    SegmentedButton(arabicFaq, { arabicFaq = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("العربية") }
                }
            }
            for (s in sections) {
                val visible = s.blocks.filter { it.head == null }
                if (s.title == null && visible.isEmpty()) continue
                item {
                    Group(header = listOfNotNull(s.title, s.ar).joinToString(" · ").ifEmpty { null }) {
                        if (visible.isEmpty()) Text(" ", Modifier.height(1.dp))
                        visible.forEachIndexed { i, b ->
                            if (i > 0) Divider()
                            Block(b, arabicFaq) { u, t -> openLink(nav, ctx, u, t) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun BiText(s: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    val shown = tr(s)
    val rtl = isArabic(shown)
    Text(
        shown, style = style.copy(textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr), color = color,
        textAlign = if (rtl) TextAlign.Right else TextAlign.Left, modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Block(b: PageBlock, arabicFaq: Boolean, open: (String, String) -> Unit) {
    when {
        b.p != null -> BiText(b.p, modifier = Modifier.padding(16.dp))
        b.links != null -> b.links.forEachIndexed { i, l ->
            if (i > 0) Divider()
            ListRow(l.t, l.s, emoji = l.e, badge = l.b, rtl = isArabic(l.t), onClick = l.u?.let { u -> { open(u, l.t) } })
        }
        b.list != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.list.forEach { item ->
                val rtl = isArabic(item)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!rtl) Text("•", color = accent, fontWeight = FontWeight.Bold)
                    BiText(item, modifier = Modifier.weight(1f))
                    if (rtl) Text("•", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        b.table != null -> {
            val h = b.table.h ?: emptyList()
            if (b.table.r.isEmpty()) Text("Nothing listed yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            b.table.r.forEachIndexed { i, row ->
                if (i > 0) Divider()
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEachIndexed { j, cell ->
                        if (cell.isBlank()) return@forEachIndexed
                        val label = h.getOrNull(j)
                        if (j == 0) BiText(cell, MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                        else BiText(if (label != null) "$label: $cell" else cell, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        b.img != null -> AsyncImage(
            model = AB.absolute(b.img), contentDescription = b.alt,
            contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(12.dp)),
        )
        b.faq != null -> b.faq.forEachIndexed { i, f ->
            if (i > 0) Divider()
            var open by remember { mutableStateOf(false) }
            val q = if (arabicFaq && f.qa != null) f.qa else f.q
            val a = if (arabicFaq && f.aa != null) f.aa else f.a
            Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BiText(q, MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(open) { BiText(a, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(top = 8.dp)) }
            }
        }
        b.e != null -> Text(b.e, Modifier.padding(16.dp))
    }
}

// MARK: Calendar

@Composable
fun CalendarScreen(title: String, path: String, events: List<CalEvent>) {
    val ctx = LocalContext.current
    var showPast by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val dated = remember(events) { events.mapNotNull { e -> AB.date(e.d)?.let { it to e } }.sortedBy { it.first } }
    val next = dated.firstOrNull { !it.first.isBefore(today) }
    val nq = AB.norm(q)
    val shown = dated.filter { (showPast || !it.first.isBefore(today)) && (nq.isEmpty() || AB.norm(it.second.n).contains(nq)) }
    val months = shown.groupBy { it.first.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + it.first.year }

    Screen(title, large = true, actions = {
        IconButton(onClick = { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, AB.absolute(path)), "Share")) }) {
            Icon(Icons.Filled.Share, "Share")
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (next != null && q.isEmpty()) item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8C1D40), Color(0xFF5A0F28)))).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NEXT UP", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(countdown(next.first, today), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    BiText(next.second.n, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), Color.White)
                    Text(next.first.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)), color = Color.White.copy(alpha = 0.85f))
                }
            }
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Search events") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Clear, "Clear") } },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Group {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Show past events", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(showPast, { showPast = it })
                    }
                }
            }
            months.forEach { (month, list) ->
                item {
                    Group(header = month) {
                        list.forEachIndexed { i, (date, e) ->
                            if (i > 0) Divider()
                            CalRow(date, e, today)
                        }
                    }
                }
            }
            if (shown.isEmpty()) item {
                EmptyState(Icons.Filled.CalendarMonth, "No events", if (q.isEmpty()) "Turn on “Show past events” to see the whole year." else trf("Nothing matches “%s”.", q))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CalRow(date: LocalDate, e: CalEvent, today: LocalDate) {
    val past = date.isBefore(today)
    val isToday = date == today
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(
            Modifier.width(52.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isToday) accent else accent.copy(alpha = 0.12f)).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isToday) Color.White else accent)
            Text(date.dayOfMonth.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f).then(if (past) Modifier.background(Color.Transparent) else Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BiText(e.n, MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Row {
                Text(if (isToday) "Today" else countdown(date, today), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else accent)
                Spacer(Modifier.weight(1f))
                Text(listOfNotNull(e.day.ifEmpty { null }, e.w?.takeIf { it > 0 }?.let { trf("Week %d", it) }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun countdown(d: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, d)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days > 1 -> trf("In %d days", days)
        days == -1L -> "Yesterday"
        else -> trf("%d days ago", -days)
    }
}

// MARK: Latest uploads

@Composable
fun LatestScreen() {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(LatestCache.read()) }
    var state by remember { mutableStateOf(if (files.isEmpty()) 0 else 2) } // 0 loading, 2 done, 3 failed
    val removedFiles by RemovedFiles.ids.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("all") }
    val user by Account.user.collectAsState()
    val ctx = LocalContext.current
    var deleting by remember { mutableStateOf<DriveResult?>(null) }
    suspend fun load() {
        val r = DriveSearch.latest()
        if (r != null) { files = r; state = 2; LatestCache.write(r) } else if (files.isEmpty()) state = 3
    }
    LaunchedEffect(Unit) { load() }
    val types = listOf("all" to "All", "pdf" to "PDF", "ppt" to "Slides", "doc" to "Docs", "img" to "Images")
    val nq = AB.norm(q)
    val filtered = files.filter { r ->
        !RemovedFiles.has(r.m.u, removedFiles) && (type == "all" || r.kind.name.lowercase() == type) && (nq.isEmpty() || AB.norm(r.m.t).contains(nq))
    }.take(400)
    val today = LocalDate.now()
    val groups = filtered.groupBy { r ->
        val age = AB.date(r.date)?.let { ChronoUnit.DAYS.between(it, today) } ?: 999
        when { age < 7 -> "This week"; age < 30 -> "This month"; else -> "Earlier" }
    }

    Screen("Latest uploads", large = true, actions = {
        IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "Refresh") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Search latest uploads") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(types) { (k, label) -> FilterChip(type == k, { type = k }, { Text(label) }) }
                }
            }
            when (state) {
                0 -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                3 -> item {
                    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load the latest files.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { state = 0; scope.launch { load() } }) { Text("Try again") }
                    }
                }
                else -> {
                    listOf("This week", "This month", "Earlier").forEach { g ->
                        val list = groups[g] ?: return@forEach
                        item {
                            Group(header = "$g · ${list.size}") {
                                list.forEachIndexed { i, r ->
                                    if (i > 0) Divider()
                                    val meta = listOfNotNull(if (r.size > 0) formatBytes(r.size) else null, r.date.take(10).ifEmpty { null }).joinToString(" · ")
                                    if (user?.isAdmin == true) Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) { MaterialRow(r.m, r.kind, meta = meta) }
                                        IconButton(onClick = { deleting = r }) { Icon(Icons.Filled.Delete, "Delete upload", tint = MaterialTheme.colorScheme.error) }
                                    } else MaterialRow(r.m, r.kind, meta = meta)
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) item { EmptyState(Icons.Filled.FindInPage, "No files", "Nothing matches these filters.") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete from Drive?") },
            text = { Text("It moves to the Drive trash and disappears from ASU Bank for everyone. A file owned by another Drive account is only removed from ASU Bank.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try { adminDeleteUpload(r.m.u); files = files.filter { it.m.u != r.m.u }; Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show() }
                        catch (e: ApiError) { Toast.makeText(ctx, e.message ?: "Couldn't delete", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

// MARK: Subjects

@Composable
fun SubjectsDirectory() {
    val nav = LocalNav.current
    val subjects by Library.subjects.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val nq = AB.norm(q)
    val cats = remember(subjects, nq) {
        subjects.filter { it.c != "Archive" && (nq.isEmpty() || AB.norm(it.t + " " + (it.ar ?: "")).contains(nq)) }.groupBy { it.c }.toList()
    }
    Screen("All subjects", large = true) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Find a subject") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            cats.forEach { (cat, list) ->
                item {
                    Group(header = cat) {
                        list.forEachIndexed { i, s ->
                            if (i > 0) Divider()
                            ListRow(s.t, listOfNotNull(s.ar.takeIf { Lang.code != "ar" }, s.n?.let { trf("%d materials", it) }).joinToString(" · "), emoji = s.e ?: "📚") { nav.openPage(s.p, s.t) }
                        }
                    }
                }
            }
            if (subjects.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun SubjectScreen(s: Subject) {
    val items by Library.items.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("all") }
    val all = remember(items, s) { items.filter { it.m.p == s.p } }
    val kinds = remember(all) { listOf("all") + all.map { it.kind.name.lowercase() }.distinct() }
    val nq = AB.norm(q)
    val shown = all.filter { (kind == "all" || it.kind.name.lowercase() == kind) && (nq.isEmpty() || it.q.contains(nq)) }
    Screen(s.t, large = true) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            s.ar?.let { ar -> item { Text(ar, Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = TextAlign.Right, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text(trf("Search in %s", tr(s.t))) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (kinds.size > 2) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(kinds) { k ->
                        FilterChip(kind == k, { kind = k }, { Text(if (k == "all") "All" else Kind.valueOf(k.uppercase()).label) })
                    }
                }
            }
            groupItems(shown, header = trf("%d materials", shown.size)) { MaterialRow(it.m, it.kind, showSource = false) }
            if (shown.isEmpty()) item { EmptyState(Icons.Filled.FindInPage, "No materials", "Nothing matches these filters.") }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Suppress("unused")
private val keepSize = Modifier.size(1.dp)
