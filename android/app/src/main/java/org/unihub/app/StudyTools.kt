package org.unihub.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

// Offline study tools: absence tracker, final mark calculator, flashcards, and study stats from the focus timer.
// Everything is saved on the phone only. The iPhone app has the same tools in StudyTools.swift.

/** Subject names from My lectures, for quick picks */
fun lectureSubjects(): List<String> = Lectures.list.value.map { it.subject.trim() }.filter { it.isNotEmpty() }.distinct()

// MARK: - Absence tracker

@Serializable
data class AbsenceSubject(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val limit: Int,
    val missed: Int = 0,
)

object Absences {
    private const val KEY = "absences"
    val list = MutableStateFlow<List<AbsenceSubject>>(load())

    private fun load(): List<AbsenceSubject> = runCatching {
        json.decodeFromString(ListSerializer(AbsenceSubject.serializer()), Saved.prefs.getString(KEY, null) ?: return emptyList())
    }.getOrDefault(emptyList())

    private fun commit(l: List<AbsenceSubject>) {
        list.value = l
        Saved.prefs.edit().putString(KEY, json.encodeToString(ListSerializer(AbsenceSubject.serializer()), l)).apply()
    }

    fun save(s: AbsenceSubject) = commit(if (list.value.any { it.id == s.id }) list.value.map { if (it.id == s.id) s else it } else list.value + s)
    fun remove(id: String) = commit(list.value.filter { it.id != id })
    fun bump(id: String, by: Int) = commit(list.value.map { if (it.id == id) it.copy(missed = max(0, it.missed + by)) else it })

    /** 15% of a 16-week term, from how many days a week the subject meets in My lectures */
    fun suggestedLimit(name: String): Int {
        val days = Lectures.list.value.filter { it.subject.trim().equals(name.trim(), true) }.flatMap { it.days }.distinct().size
        return max(1, (16 * max(days, 2) * 15) / 100)
    }
}

@Composable
fun AbsenceScreen(back: Boolean = true) {
    val list by Absences.list.collectAsState()
    var editing by remember { mutableStateOf<AbsenceSubject?>(null) }
    editing?.let { e -> AbsenceEditor(e) { editing = null } }
    val missing = lectureSubjects().filter { n -> list.none { it.name.equals(n, true) } }

    Screen("Absence tracker", back = back, actions = {
        IconButton(onClick = { editing = AbsenceSubject(name = "", limit = 4) }) { Icon(Icons.Filled.Add, tr("Add")) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (list.isEmpty()) item {
                EmptyState(Icons.Filled.EventBusy, tr("Track your absences"), tr("Add a subject, then tap + each time you miss a lecture. We warn you before you reach the limit."))
            }
            if (list.isNotEmpty()) item {
                Group(footer = "Most universities in Jordan allow about 15% of a subject's lectures. Check your own university's rule and set the limit to match.") {
                    list.forEachIndexed { i, s -> if (i > 0) Divider(); AbsenceRow(s) { editing = s } }
                }
            }
            if (missing.isNotEmpty()) item {
                Group(header = "From My lectures") {
                    missing.forEachIndexed { i, n ->
                        if (i > 0) Divider()
                        ListRow(n, trf("Limit %d absences", Absences.suggestedLimit(n)), icon = Icons.Filled.AddCircle) {
                            Absences.save(AbsenceSubject(name = n, limit = Absences.suggestedLimit(n)))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AbsenceRow(s: AbsenceSubject, edit: () -> Unit) {
    val left = s.limit - s.missed
    val color = when { left <= 0 -> MaterialTheme.colorScheme.error; left == 1 -> Brand.orange; else -> Brand.green }
    Column(Modifier.fillMaxWidth().clickable { edit() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    when {
                        left < 0 -> trf("%d over the limit", -left)
                        left == 0 -> tr("At the limit: don't miss another lecture")
                        left == 1 -> tr("1 absence left")
                        else -> trf("%d absences left", left)
                    },
                    style = MaterialTheme.typography.bodySmall, color = color,
                )
            }
            FilledTonalIconButton(onClick = { Absences.bump(s.id, -1) }, enabled = s.missed > 0) { Text("−", fontSize = 20.sp) }
            Text("${s.missed}/${s.limit}", Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            FilledTonalIconButton(onClick = { Absences.bump(s.id, 1) }) { Text("+", fontSize = 20.sp) }
        }
        LinearProgressIndicator(
            progress = { (s.missed.toFloat() / max(1, s.limit)).coerceIn(0f, 1f) },
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = color, trackColor = color.copy(alpha = 0.15f),
        )
    }
}

@Composable
private fun AbsenceEditor(start: AbsenceSubject, onClose: () -> Unit) {
    val exists = Absences.list.value.any { it.id == start.id }
    var name by remember { mutableStateOf(start.name) }
    var limit by remember { mutableStateOf(start.limit.toString()) }
    var missed by remember { mutableStateOf(start.missed.toString()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr(if (exists) "Edit" else "Add a subject")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(tr("Subject")) }, singleLine = true)
                OutlinedTextField(limit, { limit = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text(tr("Allowed absences")) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(missed, { missed = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text(tr("Missed so far")) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (exists) TextButton(onClick = { Absences.remove(start.id); onClose() }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (limit.toIntOrNull() ?: 0) > 0, onClick = {
                Absences.save(start.copy(name = name.trim(), limit = limit.toInt(), missed = missed.toIntOrNull() ?: 0)); onClose()
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } },
    )
}

// MARK: - Final mark calculator

@Composable
fun FinalMarkScreen(back: Boolean = true) {
    val p = Saved.prefs
    var have by remember { mutableStateOf(p.getString("final.have", "") ?: "") }
    var outOf by remember { mutableStateOf(p.getString("final.outOf", "50") ?: "50") }
    var finalW by remember { mutableStateOf(p.getString("final.weight", "50") ?: "50") }
    var target by remember { mutableStateOf(p.getString("final.target", "50") ?: "50") }
    LaunchedEffect(have, outOf, finalW, target) {
        p.edit().putString("final.have", have).putString("final.outOf", outOf).putString("final.weight", finalW).putString("final.target", target).apply()
    }
    val h = have.toDoubleOrNull()
    val o = outOf.toDoubleOrNull()
    val f = finalW.toDoubleOrNull()
    val ok = h != null && o != null && f != null && o > 0 && f > 0 && h <= o
    fun need(goal: Double) = goal - (h ?: 0.0)

    Screen("Final mark calculator", back = back) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(header = "Your marks so far", footer = "Add up everything before the final: midterm, quizzes, assignments, labs. Example: 22 out of 50.") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NumField(have, { have = it }, "Marks so far", Modifier.weight(1f))
                            NumField(outOf, { outOf = it }, "Out of", Modifier.weight(1f))
                        }
                        NumField(finalW, { finalW = it }, "The final is out of", Modifier.fillMaxWidth())
                    }
                }
            }
            if (ok) item {
                val total = o!! + f!!
                Group(header = "What you need on the final", footer = trf("The total is out of %s.", fmtMark(total))) {
                    val goals = (listOf(50.0, 60.0, 70.0, 80.0, 90.0).map { it * total / 100 } + listOfNotNull(target.toDoubleOrNull())).distinct().sorted()
                    goals.forEachIndexed { i, g ->
                        if (i > 0) Divider()
                        val n = need(g)
                        val (txt, col) = when {
                            n <= 0 -> tr("Already reached") to Brand.green
                            n > f -> tr("Not possible") to MaterialTheme.colorScheme.error
                            else -> trf("%s / %s", fmtMark(ceil(n * 2) / 2), fmtMark(f)) to (if (n / f > 0.8) Brand.orange else MaterialTheme.colorScheme.onSurface)
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(trf("To get %s", fmtMark(g)) + if (g == target.toDoubleOrNull()) "  ⭐" else "", Modifier.weight(1f))
                            Text(txt, fontWeight = FontWeight.Bold, color = col)
                        }
                    }
                }
            }
            if (!ok && h != null && o != null && h > o) item {
                Text(tr("Your marks so far can't be more than the total."), Modifier.padding(horizontal = 32.dp), color = MaterialTheme.colorScheme.error)
            }
            item {
                Group(header = "Your own goal", footer = "Usually 50 is the pass mark out of 100. Check your grading scale.") {
                    Column(Modifier.padding(16.dp)) { NumField(target, { target = it }, "Goal total", Modifier.fillMaxWidth()) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NumField(v: String, set: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(v, { s -> set(s.filter { it.isDigit() || it == '.' }.take(6)) }, modifier, label = { Text(tr(label)) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

private fun fmtMark(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(java.util.Locale.ENGLISH, "%.1f", d)

// MARK: - Flashcards

@Serializable data class Flashcard(val id: String = java.util.UUID.randomUUID().toString(), val front: String, val back: String)
@Serializable data class FlashDeck(val id: String = java.util.UUID.randomUUID().toString(), val name: String, val cards: List<Flashcard> = emptyList())

object Flashcards {
    private const val KEY = "flashcards"
    val decks = MutableStateFlow<List<FlashDeck>>(load())

    private fun load(): List<FlashDeck> = runCatching {
        json.decodeFromString(ListSerializer(FlashDeck.serializer()), Saved.prefs.getString(KEY, null) ?: return emptyList())
    }.getOrDefault(emptyList())

    private fun commit(l: List<FlashDeck>) {
        decks.value = l
        Saved.prefs.edit().putString(KEY, json.encodeToString(ListSerializer(FlashDeck.serializer()), l)).apply()
    }

    fun save(d: FlashDeck) = commit(if (decks.value.any { it.id == d.id }) decks.value.map { if (it.id == d.id) d else it } else decks.value + d)
    fun remove(id: String) = commit(decks.value.filter { it.id != id })
    fun deck(id: String) = decks.value.firstOrNull { it.id == id }
}

@Composable
fun FlashcardsScreen(back: Boolean = true) {
    val decks by Flashcards.decks.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<FlashDeck?>(null) }
    naming?.let { d ->
        TextDialog(if (Flashcards.deck(d.id) == null) "New deck" else "Rename", "Deck name, e.g. Biochemistry", d.name, onClose = { naming = null }) {
            Flashcards.save(d.copy(name = it)); naming = null
        }
    }
    val current = open?.let { id -> decks.firstOrNull { it.id == id } }
    if (current != null) { DeckScreen(current, back = { open = null }, rename = { naming = current }); return }

    Screen("Flashcards", back = back, actions = {
        IconButton(onClick = { naming = FlashDeck(name = "") }) { Icon(Icons.Filled.Add, tr("New deck")) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (decks.isEmpty()) item {
                EmptyState(Icons.Filled.Style, tr("Make your own flashcards"), tr("Make a deck for a subject, write a question on the front and the answer on the back, then test yourself."))
            }
            if (decks.isNotEmpty()) item {
                Group {
                    decks.forEachIndexed { i, d ->
                        if (i > 0) Divider()
                        ListRow(d.name, trf("%d cards", d.cards.size), icon = Icons.Filled.Style) { open = d.id }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DeckScreen(d: FlashDeck, back: () -> Unit, rename: () -> Unit) {
    var card by remember { mutableStateOf<Flashcard?>(null) }
    var studying by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { if (studying) studying = false else back() }
    card?.let { c -> CardEditor(d, c) { card = null } }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(tr("Delete this deck?")) },
        text = { Text(trf("%d cards", d.cards.size)) },
        confirmButton = { TextButton(onClick = { Flashcards.remove(d.id); confirmDelete = false; back() }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } },
    )
    if (studying && d.cards.isNotEmpty()) { StudyCards(d) { studying = false }; return }

    Screen(d.name, back = false, actions = {
        IconButton(onClick = back) { Icon(Icons.Filled.Close, tr("Close")) }
        IconButton(onClick = rename) { Icon(Icons.Filled.Edit, tr("Rename")) }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, tr("Delete")) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { studying = true }, enabled = d.cards.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, null); Text(" " + tr("Study"))
                    }
                    OutlinedButton(onClick = { card = Flashcard(front = "", back = "") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null); Text(" " + tr("Add a card"))
                    }
                }
            }
            if (d.cards.isEmpty()) item { EmptyState(Icons.Filled.Style, tr("No cards yet"), tr("Add your first card.")) }
            if (d.cards.isNotEmpty()) item {
                Group {
                    d.cards.forEachIndexed { i, c -> if (i > 0) Divider(); ListRow(c.front, c.back) { card = c } }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CardEditor(d: FlashDeck, c: Flashcard, onClose: () -> Unit) {
    val exists = d.cards.any { it.id == c.id }
    var front by remember { mutableStateOf(c.front) }
    var backText by remember { mutableStateOf(c.back) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr(if (exists) "Edit card" else "Add a card")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(front, { front = it }, Modifier.fillMaxWidth(), label = { Text(tr("Front (question)")) }, minLines = 2)
                OutlinedTextField(backText, { backText = it }, Modifier.fillMaxWidth(), label = { Text(tr("Back (answer)")) }, minLines = 2)
                if (exists) TextButton(onClick = { Flashcards.save(d.copy(cards = d.cards.filter { it.id != c.id })); onClose() }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = front.isNotBlank() && backText.isNotBlank(), onClick = {
                val n = c.copy(front = front.trim(), back = backText.trim())
                Flashcards.save(d.copy(cards = if (exists) d.cards.map { if (it.id == c.id) n else it } else d.cards + n))
                if (exists) onClose() else { front = ""; backText = "" } // stay open to add the next card
            }) { Text(tr(if (exists) "Save" else "Add")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr(if (exists) "Cancel" else "Done")) } },
    )
}

/** One round through the cards; the ones marked "Again" come back until all are known */
@Composable
private fun StudyCards(d: FlashDeck, close: () -> Unit) {
    var queue by remember { mutableStateOf(d.cards.shuffled()) }
    var flipped by remember { mutableStateOf(false) }
    var known by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(1) }
    var again by remember { mutableStateOf(listOf<Flashcard>()) }
    val turn by animateFloatAsState(if (flipped) 180f else 0f, label = "flip")
    fun next(knew: Boolean) {
        val c = queue.first()
        if (knew) known++
        val a = if (knew) again else again + c
        flipped = false
        val rest = queue.drop(1)
        if (rest.isEmpty() && a.isNotEmpty()) { queue = a.shuffled(); again = emptyList(); round++ }
        else { queue = rest; again = a }
    }

    Screen(d.name, back = false, actions = { IconButton(onClick = close) { Icon(Icons.Filled.Close, tr("Close")) } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (queue.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text("🎉", fontSize = 56.sp)
                Text(tr("You know every card!"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(trf("%d cards in %d rounds", d.cards.size, round), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { queue = d.cards.shuffled(); known = 0; round = 1; again = emptyList() }) { Text(tr("Study again")) }
                OutlinedButton(onClick = close) { Text(tr("Done")) }
                return@Column
            }
            Text(trf("Round %d · %d left · %d known", round, queue.size + again.size, known), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            val c = queue.first()
            Box(
                Modifier.fillMaxWidth().weight(1f).graphicsLayer { rotationY = turn; cameraDistance = 12 * density }
                    .clip(RoundedCornerShape(20.dp)).background(if (turn > 90f) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { flipped = !flipped }.padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.graphicsLayer { rotationY = if (turn > 90f) 180f else 0f }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr(if (turn > 90f) "Answer" else "Question"), style = MaterialTheme.typography.labelMedium, color = accent)
                    Spacer(Modifier.height(10.dp))
                    Text(if (turn > 90f) c.back else c.front, fontSize = 22.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                }
            }
            if (!flipped) {
                Text(tr("Tap the card to see the answer"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { next(false) }, Modifier.weight(1f)) { Text(tr("Again")) }
                    Button(onClick = { next(true) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Brand.green)) { Text(tr("Knew it"), color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun TextDialog(title: String, label: String, start: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr(title)) },
        text = { OutlinedTextField(v, { v = it }, Modifier.fillMaxWidth(), label = { Text(tr(label)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = v.isNotBlank(), onClick = { onSave(v.trim()) }) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } },
    )
}

// MARK: - Study stats (filled by the focus timer)

object StudyLog {
    private const val KEY = "study.log"
    private val ser = MapSerializer(String.serializer(), MapSerializer(String.serializer(), Int.serializer()))
    /** day (yyyy-MM-dd) -> subject -> minutes */
    val days = MutableStateFlow<Map<String, Map<String, Int>>>(load())
    val goal = MutableStateFlow(Saved.prefs.getInt("study.goal", 60))

    private fun load(): Map<String, Map<String, Int>> = runCatching { json.decodeFromString(ser, Saved.prefs.getString(KEY, null) ?: return emptyMap()) }.getOrDefault(emptyMap())

    fun add(minutes: Int, subject: String) {
        val day = LocalDate.now().toString()
        val s = subject.ifBlank { "General" }
        val m = days.value.toMutableMap()
        val d = (m[day] ?: emptyMap()).toMutableMap()
        d[s] = (d[s] ?: 0) + minutes
        m[day] = d
        // keep a year
        val cut = LocalDate.now().minusDays(366).toString()
        days.value = m.filterKeys { it >= cut }
        Saved.prefs.edit().putString(KEY, json.encodeToString(ser, days.value)).apply()
    }

    fun setGoal(m: Int) { goal.value = m; Saved.prefs.edit().putInt("study.goal", m).apply() }

    fun minutes(map: Map<String, Map<String, Int>>, day: LocalDate) = map[day.toString()]?.values?.sum() ?: 0

    /** Days in a row with some study, ending today (or yesterday, so the streak isn't lost before you study today) */
    fun streak(map: Map<String, Map<String, Int>>): Int {
        var d = LocalDate.now()
        if (minutes(map, d) == 0) d = d.minusDays(1)
        var n = 0
        while (minutes(map, d) > 0) { n++; d = d.minusDays(1) }
        return n
    }
}

@Composable
fun StudyStatsScreen(back: Boolean = true) {
    val map by StudyLog.days.collectAsState()
    val goal by StudyLog.goal.collectAsState()
    val nav = LocalNav.current
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val bySubject = week.flatMap { d -> map[d.toString()]?.entries ?: emptySet() }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        .entries.sortedByDescending { it.value }
    val todayMin = StudyLog.minutes(map, today)
    val streak = StudyLog.streak(map)

    Screen("Study stats", back = back) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("🔥", trf("%d days", streak), tr("Streak"), Modifier.weight(1f))
                    StatTile("⏱️", "$todayMin/$goal", tr("Minutes today"), Modifier.weight(1f))
                    StatTile("📅", "${week.sumOf { StudyLog.minutes(map, it) }}", tr("This week"), Modifier.weight(1f))
                }
            }
            item {
                Group(header = "Last 7 days") {
                    val top = max(goal, week.maxOf { StudyLog.minutes(map, it) })
                    Row(Modifier.fillMaxWidth().height(160.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        week.forEach { d ->
                            val m = StudyLog.minutes(map, d)
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                if (m > 0) Text("$m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(
                                    Modifier.fillMaxWidth().fillMaxHeight((m.toFloat() / top).coerceIn(0.02f, 0.8f)).clip(RoundedCornerShape(6.dp))
                                        .background(if (m >= goal) Brand.green else accent.copy(alpha = if (m > 0) 0.8f else 0.15f)),
                                )
                                Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Group(footer = "Minutes count when a focus timer session ends. Green days reached your goal.") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(trf("Daily goal: %d minutes", goal), Modifier.weight(1f))
                        TextButton(enabled = goal > 15, onClick = { StudyLog.setGoal(goal - 15) }) { Text("−", fontSize = 20.sp) }
                        TextButton(enabled = goal < 600, onClick = { StudyLog.setGoal(goal + 15) }) { Text("+", fontSize = 20.sp) }
                    }
                    Divider()
                    ListRow("Focus timer", "Start a session", icon = Icons.Filled.Timer) { nav.openPage("/app/focus", "") }
                }
            }
            if (bySubject.isNotEmpty()) item {
                Group(header = "This week by subject") {
                    bySubject.forEachIndexed { i, (s, m) ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(if (s == "General") tr("General") else s, Modifier.weight(1f), maxLines = 1)
                            Text(trf("%d min", m), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 22.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Home: streak and today's minutes, once the student has used the focus timer */
@Composable
fun HomeStudyCard() {
    val nav = LocalNav.current
    val map by StudyLog.days.collectAsState()
    val goal by StudyLog.goal.collectAsState()
    if (map.isEmpty()) return
    val today = StudyLog.minutes(map, LocalDate.now())
    val streak = StudyLog.streak(map)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { nav.openPage("/app/stats", "") }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (streak > 0) "🔥" else "📚", fontSize = 28.sp)
        Column(Modifier.weight(1f)) {
            Text(if (streak > 0) trf("%d-day study streak", streak) else tr("Start a new streak today"), fontWeight = FontWeight.SemiBold)
            Text(trf("%d of %d minutes today", today, goal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CircularProgressIndicator(progress = { (today.toFloat() / max(1, goal)).coerceIn(0f, 1f) }, Modifier.size(34.dp), strokeWidth = 4.dp,
            color = if (today >= goal) Brand.green else accent, trackColor = accent.copy(alpha = 0.15f))
    }
}
