package org.asubank.app

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

// Native versions of the asubank.org guide pages: how to search, grading scale, WhatsApp groups, thanks list.

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// MARK: - How to search

@Composable
fun SearchGuideScreen() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    fun go(s: String) { if (s.isNotBlank()) { AppState.pendingSearch.value = s.trim(); nav.openTab(Routes.SEARCH) } }
    Screen("How to search") { pad ->
        LazyColumn(Modifier.padding(pad).imePadding()) {
            item { Hero("🔍", "How to find any material", "Everything on ASU Bank is searchable from one box: subject pages and the shared Google Drive.", "اكتب اسم المادة أو رمزها، وستجد الملفات فوراً") }
            item {
                Group(header = "Try it now") {
                    OutlinedTextField(
                        q, { q = it }, placeholder = { Text("Type a subject") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { go(q) }),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Calculus", "MATH101", "Anatomy", "Pharmacology", "Statistics", "English")) { e -> PillChip(e, false) { go(e) } }
                    }
                }
            }
            item {
                Group(header = "In the app") {
                    Step(1, "Open Search", "Tap the Search tab at the bottom. The box is always at the top.")
                    Step(2, "Type the subject or its code", "A few letters are enough: \"calc\", \"MATH101\" or a doctor's name. Arabic names work too.")
                    Step(3, "Pick a result", "Subject pages come first, then files found in the Google Drive. Folders open right in the app.")
                    Step(4, "Narrow it down", "Use the filter button to show only subject pages, only Drive files, or one year.")
                    Step(5, "Save it for later", "Tap the bookmark on any file or subject. It appears in the Saved tab, even offline.")
                }
            }
            item {
                Group(header = "Tips") {
                    ListRow("Search the course name, not the file name", "Files are named by the students who uploaded them.", icon = Icons.Filled.Lightbulb)
                    Divider()
                    ListRow("Try English and Arabic", "If the first name gives nothing, try the other.", icon = Icons.Filled.Translate)
                    Divider()
                    ListRow("Open the subject page", "Every year and doctor in one place.", icon = Icons.Filled.LibraryBooks)
                    Divider()
                    ListRow("New files", "Show up in Latest uploads on the Home tab.", icon = Icons.Filled.History)
                }
            }
            item {
                Group(header = "Can't find it?") {
                    ListRow("Request it from us", icon = Icons.Filled.MoveToInbox, iconColor = Brand.accent) { nav.openPage("/requests/student_request_portal", "Request a material") }
                    Divider()
                    ListRow("Upload it for others", icon = Icons.Filled.CloudUpload, iconColor = Color(0xFF0A84FF)) { nav.openTab(Routes.UPLOAD) }
                    Divider()
                    ListRow("Ask your college's WhatsApp group", icon = Icons.Filled.Groups, iconColor = Color(0xFF25D366)) { nav.openPage("/guides/groups", "WhatsApp groups") }
                }
            }
            item {
                Group(header = "On the website") {
                    Text(
                        "On asubank.org the search box is at the top of every page. Type the subject, press Enter, and the results load from the subject pages and the Drive together.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// MARK: - Grading scale

object Grading {
    data class Band(val min: Double, val en: String, val ar: String, val note: String, val color: Color)
    val bands = listOf(
        Band(92.0, "Excellent with honors", "ممتاز مع مرتبة الشرف", "Top of the scale", Color(0xFF7C3AED)),
        Band(84.0, "Excellent", "ممتاز", "Outstanding work", Color(0xFF1F8A3E)),
        Band(76.0, "Very good", "جيد جداً", "Strong and steady", Color(0xFF0A84FF)),
        Band(68.0, "Good", "جيد", "Clearly acceptable", Color(0xFFE8A00C)),
        Band(60.0, "Pass", "مقبول", "The minimum to pass", Color(0xFFE8590C)),
    )
    fun band(v: Double) = bands.firstOrNull { v >= it.min }
    fun range(i: Int) = if (i == 0) "92 – 100" else "${bands[i].min.toInt()} – under ${bands[i - 1].min.toInt()}"
}

@Serializable private data class GpaRow(val name: String = "", val hours: Int = 3, val mark: String = "")

private fun String.mark() = replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }

@Composable
fun GradingScaleScreen() {
    val p = Saved.prefs
    val ser = remember { ListSerializer(GpaRow.serializer()) }
    var value by remember { mutableStateOf("") }
    val rows = remember {
        mutableStateListOf<GpaRow>().apply {
            addAll(try { json.decodeFromString(ser, p.getString("gpa.rows", "") ?: "") } catch (e: Exception) { emptyList() })
            if (isEmpty()) repeat(3) { add(GpaRow()) }
        }
    }
    var prevAvg by remember { mutableStateOf(p.getString("gpa.prevAvg", "") ?: "") }
    var prevHours by remember { mutableStateOf(p.getString("gpa.prevHours", "") ?: "") }
    LaunchedEffect(rows.toList(), prevAvg, prevHours) {
        p.edit().putString("gpa.rows", json.encodeToString(ser, rows.toList())).putString("gpa.prevAvg", prevAvg).putString("gpa.prevHours", prevHours).apply()
    }
    val n = value.mark()
    val done = rows.mapNotNull { r -> r.mark.mark()?.let { it to r.hours } }
    val hours = done.sumOf { it.second }
    val term = if (hours > 0) done.sumOf { it.first * it.second } / hours else null
    val pa = prevAvg.mark()
    val ph = prevHours.toIntOrNull()
    val cumulative = if (term != null && pa != null && ph != null && ph > 0) (pa * ph + term * hours) / (ph + hours) else null

    Screen("Grading scale") { pad ->
        LazyColumn(Modifier.padding(pad).imePadding()) {
            item { Hero("🎓", "Grading scale", "How your term and cumulative average turn into a grade at ASU.", "سُلّم التقديرات للمعدل الفصلي والتراكمي") }
            item {
                Group(header = "The scale", footer = "Below 60 the average is under the passing limit.") {
                    Grading.bands.forEachIndexed { i, b ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        val on = n != null && Grading.band(n)?.min == b.min
                        Row(
                            Modifier.fillMaxWidth().background(if (on) b.color.copy(alpha = 0.15f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.width(6.dp).height(40.dp).clip(RoundedCornerShape(3.dp)).background(b.color))
                            Column(Modifier.weight(1f)) {
                                Text(b.en, fontWeight = FontWeight.SemiBold)
                                Text(b.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(b.ar, fontWeight = FontWeight.SemiBold)
                                Text(Grading.range(i), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Group(header = "What's my grade?") {
                    OutlinedTextField(
                        value, { value = it }, label = { Text("Your average, 0 to 100") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    if (n != null) GradeResult(n)
                }
            }
            item {
                Group(header = "Term average calculator", footer = "Enter each course's credit hours and mark out of 100.") {
                    rows.forEachIndexed { i, r ->
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(r.name, { rows[i] = r.copy(name = it) }, placeholder = { Text(trf("Course %d", i + 1)) }, singleLine = true, modifier = Modifier.weight(1f))
                            HoursPicker(r.hours) { rows[i] = r.copy(hours = it) }
                            OutlinedTextField(
                                r.mark, { rows[i] = r.copy(mark = it) }, placeholder = { Text("Mark") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(80.dp),
                            )
                            IconButton({ rows.removeAt(i) }) { Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    TextButton({ rows.add(GpaRow()) }, Modifier.padding(horizontal = 8.dp)) { Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.width(6.dp)); Text("Add a course") }
                    if (term != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("Term average", Modifier.weight(1f)); Text("%.2f".format(term), fontWeight = FontWeight.Bold)
                        }
                        GradeResult(term)
                    }
                }
            }
            item {
                Group(header = "Cumulative average", footer = "Uses the term above. The university's own calculation is final.") {
                    OutlinedTextField(prevAvg, { prevAvg = it }, label = { Text("Previous cumulative average") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                    OutlinedTextField(prevHours, { prevHours = it }, label = { Text("Hours passed before this term") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                    if (cumulative != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("New cumulative", Modifier.weight(1f)); Text("%.2f".format(cumulative), fontWeight = FontWeight.Bold)
                        }
                        GradeResult(cumulative)
                    } else Spacer(Modifier.height(10.dp))
                }
            }
            item {
                val nav = LocalNav.current
                Group {
                    ListRow("Official ASU average calculator", icon = Icons.Filled.Functions) { nav.openWeb("https://web.asu.edu.jo/CalculateAvg/", "Calculate your average") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HoursPicker(h: Int, set: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text("$h h") }
        DropdownMenu(open, { open = false }) {
            (1..6).forEach { v -> DropdownMenuItem(text = { Text(trf("%d hours", v)) }, onClick = { set(v); open = false }) }
        }
    }
}

@Composable
private fun GradeResult(v: Double) {
    val b = Grading.band(v)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (b != null) {
            Icon(Icons.Filled.Verified, null, tint = b.color)
            Text(b.en, Modifier.weight(1f), color = b.color, fontWeight = FontWeight.SemiBold)
            Text(b.ar, color = b.color, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
            Text("Under the passing limit (60)", color = MaterialTheme.colorScheme.error)
        }
    }
}

// MARK: - WhatsApp groups and resources

private data class GroupSection(val title: String, val resources: Boolean, val links: List<PageLink>)

private fun groupSections(page: SitePage): List<GroupSection> {
    val out = mutableListOf<GroupSection>()
    var parent = ""
    var title = ""
    var resources = false
    for (b in page.b) {
        val h = b.h
        if (h != null) {
            if (h == "Educational Resources") { resources = true; continue }
            if (h.lowercase().endsWith(" year") && parent.isNotEmpty()) title = "$parent · $h" else { parent = h; title = h }
        } else if (b.links != null && title.isNotEmpty()) out.add(GroupSection(title, resources, b.links))
    }
    return out
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupsScreen(page: SitePage) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val all = remember(page) { groupSections(page) }
    var kind by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    val shown = all.filter { it.resources == (kind == 1) }.mapNotNull { s ->
        if (q.isBlank() || s.title.contains(q.trim(), true)) s
        else s.copy(links = s.links.filter { it.t.contains(q.trim(), true) || (it.s ?: "").contains(q.trim(), true) }).takeIf { it.links.isNotEmpty() }
    }
    Screen("Groups & resources") { pad ->
        LazyColumn(Modifier.padding(pad).imePadding()) {
            item { Hero("💬", "University groups", "Join your college's WhatsApp groups, buses and study resources.", "روابط قروبات الواتساب والمصادر الدراسية") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SegmentedButton(kind == 0, { kind = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("WhatsApp groups") }
                    SegmentedButton(kind == 1, { kind = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Study resources") }
                }
                OutlinedTextField(
                    q, { q = it }, placeholder = { Text("Search groups") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            items(shown, key = { it.title + it.resources }) { s ->
                Group(header = s.title) {
                    s.links.forEachIndexed { i, l ->
                        if (i > 0) Divider()
                        val u = l.u ?: ""
                        val wa = "whatsapp.com" in u || "wa.me" in u
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = { if (u.isNotEmpty()) openLink(nav, ctx, u, l.t) },
                                onLongClick = { if (u.isNotEmpty()) { copyText(ctx, l.t, u); Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show() } },
                            ).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (wa) Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF25D366)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Forum, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            } else KindTile(AB.kind(u, l.t), 30.dp)
                            Column(Modifier.weight(1f)) {
                                BiText(l.t)
                                l.s?.let { BiText(it, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (wa) Text(
                                "Join", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.green,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF25D366).copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Filled.SearchOff, "No groups found", "Try another word.") }
            item {
                Group(footer = "Groups are run by students, not by ASU Bank. Be kind and follow each group's rules. Long-press a group to copy its link.") {
                    ListRow("Add your group's link", icon = Icons.Filled.AddComment, iconColor = Brand.accent) { openExternal(ctx, waMessage("Hi ASU Bank, I'd like to add a WhatsApp group: ")) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// MARK: - Thanks list

@Composable
fun ThanksScreen(page: SitePage?) {
    val ctx = LocalContext.current
    val names = (page?.b?.firstNotNullOfOrNull { it.list } ?: listOf("Qusai", "Lina", "Hxcker", "Osama", "Sama", "اعشق تمردي")).map(::cleanThanksName)
    val palette = listOf(0xFF8C1D40, 0xFF0A84FF, 0xFF1F8A3E, 0xFFAF52DE, 0xFFE8590C, 0xFFFF2D55, 0xFF5E5CE6, 0xFF00A3A3)
    val items by Library.items.collectAsState()
    val subjects by Library.subjects.collectAsState()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Screen("Thanks list") { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(130.dp), Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("💛", fontSize = 52.sp)
                    Text("Thank you!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "ASU Bank is built by students, for students. These people uploaded, sorted and shared the materials you use every day.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Text("شكراً لكل من ساهم في بناء المنصة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("${items.size}" to "Materials", "${subjects.size}" to "Subjects", "${names.size + 1}" to "Helpers").forEach { (n, l) ->
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(n, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
                                Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                // the founder, above everyone else
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
                    Row(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Brand.accent, Color(0xFFB8336A))), RoundedCornerShape(20.dp)).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(Brand.gold), contentAlignment = Alignment.Center) { Text("👑", fontSize = 28.sp) }
                        Column(Modifier.weight(1f)) {
                            Text("Alan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Founder · builds and runs ASU Bank", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            itemsIndexed(names) { i, n ->
                val c = Color(palette[i % palette.size])
                val t by animateFloatAsState(if (shown) 1f else 0f, tween(420, delayMillis = 60 * (i % 12)), label = "card")
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.graphicsLayer { alpha = t; scaleX = 0.85f + 0.15f * t; scaleY = 0.85f + 0.15f * t }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.copy(alpha = 0.75f), c)))
                                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(n.take(1).uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                        Text(n, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(if (i == 0) "⭐ First contributor" else "Contributor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Want your name here?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Upload materials, help sort them, or report broken links. Message ASU-Requests and join the team.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        Button({ openExternal(ctx, waMessage("Hi ASU Bank, I'd like to help the team")) }, Modifier.fillMaxWidth()) { Text("Join the team on WhatsApp") }
                    }
                }
            }
        }
    }
}

/** The web page's cards read "Q" + "Qusai" + "Contributor" as one line in pages.json */
fun cleanThanksName(raw: String): String {
    var s = raw.trim()
    val suffix = listOf("⭐ Founding contributor", "⭐ First contributor", "Contributor").firstOrNull { s.endsWith(it) } ?: return s
    s = s.removeSuffix(suffix).trim()
    if (s.length > 1 && s[0].lowercaseChar() == s[1].lowercaseChar()) s = s.substring(1)
    return s
}
