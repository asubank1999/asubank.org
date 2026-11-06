package org.unihub.app

import android.app.DatePickerDialog
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Student tools that work offline: exams and tasks with due dates, a focus timer, and the calendar card on Home.

@Serializable
data class PlannerItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val course: String = "",
    val kind: String = "exam", // exam, assignment, task
    val due: String, // yyyy-MM-dd
    val done: Boolean = false,
)

object Planner {
    private const val KEY = "planner"
    val items = MutableStateFlow<List<PlannerItem>>(emptyList())

    fun init() {
        val s = Saved.prefs.getString(KEY, null) ?: return
        runCatching { items.value = json.decodeFromString(ListSerializer(PlannerItem.serializer()), s) }
    }

    private fun commit(list: List<PlannerItem>) {
        items.value = list
        Saved.prefs.edit().putString(KEY, json.encodeToString(ListSerializer(PlannerItem.serializer()), list)).apply()
    }

    fun save(i: PlannerItem) = commit(if (items.value.any { it.id == i.id }) items.value.map { if (it.id == i.id) i else it } else items.value + i)
    fun remove(id: String) = commit(items.value.filter { it.id != id })
    fun toggle(id: String) = commit(items.value.map { if (it.id == id) it.copy(done = !it.done) else it })

    fun upcoming(list: List<PlannerItem>) = list.filter { !it.done }.sortedBy { it.due }

    fun dueText(date: String): String {
        val d = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return ""
        val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
        return when {
            days < 0 -> tr("Overdue")
            days == 0L -> tr("Today")
            days == 1L -> tr("Tomorrow")
            else -> trf("In %d days", days.toInt())
        }
    }
}

@Composable
fun PlannerScreen(back: Boolean = true) {
    val items by Planner.items.collectAsState()
    var editing by remember { mutableStateOf<PlannerItem?>(null) }
    editing?.let { e -> PlannerEditor(e) { editing = null } }
    val upcoming = Planner.upcoming(items)
    val done = items.filter { it.done }.sortedByDescending { it.due }

    Screen("Exams & tasks", back = back, actions = {
        IconButton(onClick = { editing = PlannerItem(title = "", due = LocalDate.now().plusDays(7).toString()) }) { Icon(Icons.Filled.Add, tr("Add")) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (items.isEmpty()) item {
                EmptyState(Icons.Filled.Checklist, tr("Nothing planned yet"), tr("Add your exams and assignments, and the next ones show on Home."))
            }
            if (upcoming.isNotEmpty()) item { Group(header = "Coming up") { upcoming.forEachIndexed { i, p -> if (i > 0) Divider(); PlannerRow(p) { editing = p } } } }
            if (done.isNotEmpty()) item { Group(header = "Done") { done.forEachIndexed { i, p -> if (i > 0) Divider(); PlannerRow(p) { editing = p } } } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PlannerRow(p: PlannerItem, edit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { edit() }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { Planner.toggle(p.id) }) {
            Icon(
                if (p.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                tr(if (p.done) "Mark as not done" else "Mark as done"),
                tint = if (p.done) Brand.green else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(p.title, textDecoration = if (p.done) TextDecoration.LineThrough else null)
            Text(
                listOf(p.course, p.due).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!p.done) Text(Planner.dueText(p.due), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent, modifier = Modifier.padding(end = 6.dp))
    }
}

@Composable
private fun PlannerEditor(start: PlannerItem, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val exists = Planner.items.value.any { it.id == start.id }
    var title by remember { mutableStateOf(start.title) }
    var course by remember { mutableStateOf(start.course) }
    var kind by remember { mutableStateOf(start.kind) }
    var due by remember { mutableStateOf(start.due) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr(if (exists) "Edit" else "New item")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("exam" to "Exam", "assignment" to "Assignment", "task" to "Task").forEach { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(tr(label), maxLines = 1, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(tr("Title")) }, singleLine = true)
                OutlinedTextField(course, { course = it }, Modifier.fillMaxWidth(), label = { Text(tr("Course (optional)")) }, singleLine = true)
                OutlinedButton(onClick = {
                    val d = runCatching { LocalDate.parse(due) }.getOrDefault(LocalDate.now())
                    DatePickerDialog(ctx, { _, y, m, day -> due = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }) { Icon(Icons.Filled.Event, null, Modifier.size(18.dp)); Text(" $due") }
                if (exists) TextButton(onClick = { Planner.remove(start.id); onClose() }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                Planner.save(start.copy(title = title.trim(), course = course.trim(), kind = kind, due = due))
                onClose()
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } },
    )
}

/** Focus timer: work, then a break. Counts from an end time, so it stays right if the screen locks. */
@Composable
fun FocusTimerScreen(back: Boolean = true) {
    val ctx = LocalContext.current
    val prefs = Saved.prefs
    var minutes by remember { mutableIntStateOf(prefs.getInt("focus.minutes", 25)) }
    var end by remember { mutableStateOf<Long?>(null) }
    var left by remember { mutableStateOf(0L) }
    var onBreak by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val today = LocalDate.now().toString()
    var count by remember { mutableIntStateOf(if (prefs.getString("focus.day", "") == today) prefs.getInt("focus.count", 0) else 0) }
    val total = (if (onBreak) 5 else minutes) * 60_000L
    val remaining = end?.let { maxOf(0L, it - now) } ?: if (left > 0) left else total

    val window = (ctx as? android.app.Activity)?.window
    DisposableEffect(end != null) {
        if (end != null) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(end) {
        while (end != null) {
            delay(500)
            now = System.currentTimeMillis()
            val e = end ?: break
            if (now >= e) {
                if (!onBreak) {
                    count += 1
                    prefs.edit().putString("focus.day", today).putInt("focus.count", count).apply()
                }
                onBreak = !onBreak
                end = now + (if (onBreak) 5 else minutes) * 60_000L
            }
        }
    }

    Screen("Focus timer", back = back) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(tr(if (onBreak) "Break" else "Focus"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { (remaining.toFloat() / total).coerceIn(0f, 1f) }, Modifier.size(220.dp),
                                strokeWidth = 12.dp, trackColor = accent.copy(alpha = 0.15f),
                            )
                            Text("%02d:%02d".format(remaining / 60000, remaining / 1000 % 60), fontSize = 46.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                now = System.currentTimeMillis()
                                val e = end
                                if (e != null) { left = maxOf(0L, e - now); end = null }
                                else { end = now + if (left > 0) left else total; left = 0 }
                            }) { Text(tr(if (end == null) "Start" else "Pause")) }
                            OutlinedButton(onClick = { end = null; left = 0; onBreak = false }) { Text(tr("Reset")) }
                        }
                    }
                }
            }
            item {
                Group(footer = "Keep this screen open. A 5 minute break starts after each session.") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(trf("Focus for %d minutes", minutes), Modifier.weight(1f))
                        TextButton(enabled = end == null && minutes > 5, onClick = { minutes -= 5; prefs.edit().putInt("focus.minutes", minutes).apply() }) { Text("−", fontSize = 20.sp) }
                        TextButton(enabled = end == null && minutes < 90, onClick = { minutes += 5; prefs.edit().putInt("focus.minutes", minutes).apply() }) { Text("+", fontSize = 20.sp) }
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(tr("Sessions today"), Modifier.weight(1f))
                        Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// MARK: - Home cards

/** First thing on Home: the next date on the university calendar */
@Composable
fun HomeCalendarCard() {
    val nav = LocalNav.current
    val pages by Pages.pages.collectAsState()
    val path = "/schedules/calendar27"
    val next = remember(pages) {
        val today = LocalDate.now()
        pages[path]?.cal?.filter { !it.n.startsWith("الأسبوع") }?.mapNotNull { e -> runCatching { LocalDate.parse(e.d.take(10)) }.getOrNull()?.let { e to it } }
            ?.filter { !it.second.isBefore(today) }?.minByOrNull { it.second }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { nav.openPage(path, "") }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(tr("University calendar"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(next?.first?.n ?: tr("See this year's important dates"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        next?.let {
            Text(
                Planner.dueText(it.second.toString()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

/** The next exams and tasks the student added */
@Composable
fun HomeDueCard() {
    val nav = LocalNav.current
    val items by Planner.items.collectAsState()
    val next = Planner.upcoming(items).take(3)
    if (next.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { nav.openPage("/app/planner", "") }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        next.forEach { i ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    when (i.kind) { "exam" -> Icons.Filled.School; "assignment" -> Icons.Filled.Description; else -> Icons.Filled.CheckCircle },
                    null, tint = accent, modifier = Modifier.size(20.dp),
                )
                Text(i.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(Planner.dueText(i.due), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
