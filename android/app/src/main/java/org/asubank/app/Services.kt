package org.asubank.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// Native versions of the asubank.org services: course matcher, request a material, student ads, donate.

const val ADMIN_WA = "mailto:necro144@yahoo.com"
fun waMessage(text: String) = "$ADMIN_WA?text=" + Uri.encode(text)

fun copyText(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Big colored header card used by the service and guide screens */
@Composable
fun Hero(emoji: String, title: String, text: String, ar: String? = null) {
    val c = accent
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.85f), c))).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 30.sp)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        if (ar != null) Text(ar, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }
}

@Composable
fun PillChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = accent
    Text(
        text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun FormField(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType = KeyboardType.Text, caps: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, capitalization = if (caps) KeyboardCapitalization.Words else KeyboardCapitalization.None),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ResultDialog(title: String, text: String, ok: Boolean, primary: String? = null, onPrimary: (() -> Unit)? = null, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null, tint = if (ok) Brand.green else MaterialTheme.colorScheme.error) },
        title = { Text(title) }, text = { Text(text) },
        confirmButton = {
            if (primary != null && onPrimary != null) TextButton({ onPrimary(); close() }) { Text(primary) }
            else TextButton(close) { Text("OK") }
        },
        dismissButton = { if (primary != null) TextButton(close) { Text("Done") } },
    )
}

@Composable
private fun SendButton(text: String, sending: Boolean, onClick: () -> Unit) {
    Button(
        onClick, enabled = !sending, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
    ) {
        if (sending) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
        Text(if (sending) "Sending…" else text, fontWeight = FontWeight.SemiBold)
    }
}

private data class Dlg(val title: String, val text: String, val ok: Boolean)

// MARK: - Course matcher

object Matcher {
    const val SCRIPT = "https://script.google.com/macros/s/AKfycbwwb0HGoZc6O5IqVfAVZJx6rHJ3MGg98kbwGc4EiLwCCY1Tc3E9Zo8ZHX4B1YI7kuR-/exec"
    val bridge = ScriptBridge(SCRIPT, "studyForm")
    val years = listOf("Freshman" to "1st year", "Sophomore" to "2nd year", "Junior" to "3rd year", "Senior" to "4th year+", "Graduate" to "Graduate")
    val locations = listOf("Library", "Campus Cafe", "Study Hall", "Online (Zoom/Teams)", "Dorm Common Area", "Off-Campus", "Flexible")
    val contacts = listOf("WhatsApp", "Email", "Phone", "Discord", "Instagram", "Any")
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
    val times = listOf("Morning", "Afternoon", "Evening")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseMatcherScreen() {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val p = Saved.prefs
    var intro by remember { mutableStateOf(!p.getBoolean("matcher.intro.v1", false)) }
    val status by Matcher.bridge.status.collectAsState()
    val subjects by Library.subjects.collectAsState()

    var name by remember { mutableStateOf(p.getString("matcher.name", "") ?: "") }
    var email by remember { mutableStateOf(p.getString("matcher.email", "") ?: "") }
    var phone by remember { mutableStateOf(p.getString("matcher.phone", "") ?: "") }
    var major by remember { mutableStateOf(p.getString("matcher.major", "") ?: "") }
    var gender by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    val courses = remember { mutableStateListOf<String>() }
    val topics = remember { mutableStateListOf<String>() }
    var courseText by remember { mutableStateOf("") }
    var topicText by remember { mutableStateOf("") }
    val free = remember { mutableStateListOf<String>() }
    var location by remember { mutableStateOf("Flexible") }
    var contact by remember { mutableStateOf("WhatsApp") }
    var sending by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }

    val missing = when {
        name.trim().length < 3 -> "Enter your full name."
        !Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(email.trim()) -> "Enter a valid email. Matches are sent there."
        gender.isEmpty() -> "Choose your gender."
        major.isBlank() -> "Enter your major."
        year.isEmpty() -> "Choose your year."
        courses.isEmpty() -> "Add at least one course."
        topics.isEmpty() -> "Add at least one topic you want to study."
        free.isEmpty() -> "Pick at least one time you're free."
        else -> null
    }

    fun submit() {
        if (missing != null) { dlg = Dlg("Almost there", missing, false); return }
        sending = true
        p.edit().putString("matcher.name", name).putString("matcher.email", email).putString("matcher.phone", phone).putString("matcher.major", major).apply()
        val order = Matcher.days.flatMap { d -> Matcher.times.map { "$d $it" } } + "Weekends"
        val data = buildJsonObject {
            put("name", name.trim()); put("email", email.trim()); put("phone", phone.trim()); put("gender", gender)
            put("major", major.trim()); put("year", year)
            putJsonArray("courses") { courses.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("topics") { topics.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("availability") { order.filter { it in free }.forEach { add(JsonPrimitive(it)) } }
            put("location", location); put("contactMethod", contact)
        }
        scope.launch {
            val r = Matcher.bridge.call("saveStudent", listOf(data), 45_000)
            sending = false
            r.onSuccess { v ->
                val n = ((v as? JsonObject)?.get("matchesFound") as? JsonPrimitive)?.intOrNull ?: 0
                dlg = if (n > 0) Dlg(trf("Matches found: %d", n), trf("Check your email (%s) for their contact details.", email), true)
                else Dlg("Profile saved", "We'll email you as soon as a classmate with similar courses and times signs up.", true)
                courses.clear(); topics.clear(); free.clear()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }.onFailure { dlg = Dlg("Couldn't save", bridgeError(it), false) }
        }
    }

    Screen("Course matcher") { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            BridgeHost(Matcher.bridge)
            if (intro) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item { Hero("👥", "Find your study buddies", "Tell us your courses and free times. When a classmate matches at least half of them, you both get each other's contact by email.", "اعثر على زملاء يدرسون نفس موادك") }
                    item {
                        Group(header = "How it works") {
                            ListRow("Fill in your courses and times", "Takes about a minute", icon = Icons.Filled.EditNote, iconColor = Brand.accent)
                            Divider()
                            ListRow("We look for matches", "Courses, topics and free time", icon = Icons.Filled.Groups, iconColor = Color(0xFF0A84FF))
                            Divider()
                            ListRow("You both get an email", "With the contact method you chose", icon = Icons.Filled.Email, iconColor = Brand.green)
                        }
                    }
                    item {
                        Group(footer = "Your details are only shared with students you match with.") {
                            ListRow("Your privacy", "Nothing is public. You can ask us to delete your profile at any time.", icon = Icons.Filled.Lock, iconColor = Color(0xFF8E8E93))
                        }
                    }
                    item { SendButton("Continue", false) { p.edit().putBoolean("matcher.intro.v1", true).apply(); intro = false } }
                }
            } else {
                val suggestions = remember(courseText, subjects) {
                    val q = courseText.trim()
                    if (q.length < 2) emptyList() else subjects.filter { it.t.contains(q, true) || (it.ar ?: "").contains(q) }.map { it.t }.distinct().filter { it !in courses }.take(6)
                }
                LazyColumn(Modifier.fillMaxSize().imePadding()) {
                    item {
                        Group(header = "About you") {
                            FormField(name, { name = it }, "Full name", caps = true)
                            FormField(email, { email = it }, "Email (matches are sent here)", KeyboardType.Email)
                            FormField(phone, { phone = it }, "Phone (optional)", KeyboardType.Phone)
                            FormField(major, { major = it }, "Major, e.g. Pharmacy", caps = true)
                            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Male", gender == "Male") { gender = "Male" }
                                PillChip("Female", gender == "Female") { gender = "Female" }
                            }
                            FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Matcher.years.forEach { (k, v) -> PillChip(v, year == k) { year = k } }
                            }
                        }
                    }
                    item {
                        Group(header = "Courses", footer = "Type a course and tap Add, or pick a suggestion.") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FormField(courseText, { courseText = it }, "Course, e.g. Calculus 1", caps = true, modifier = Modifier.weight(1f))
                                TextButton({ courseText.trim().takeIf { it.isNotEmpty() && it !in courses }?.let { courses.add(it) }; courseText = "" }, Modifier.padding(end = 8.dp)) { Text("Add") }
                            }
                            if (suggestions.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(suggestions) { s -> PillChip("+ $s", false) { courses.add(s); courseText = "" } }
                            }
                            Tags(courses)
                        }
                    }
                    item {
                        Group(header = "Topics", footer = "What do you want to study together? e.g. Exam prep, Homework, Labs") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FormField(topicText, { topicText = it }, "Topic", caps = true, modifier = Modifier.weight(1f))
                                TextButton({ topicText.trim().takeIf { it.isNotEmpty() && it !in topics }?.let { topics.add(it) }; topicText = "" }, Modifier.padding(end = 8.dp)) { Text("Add") }
                            }
                            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Exam prep", "Homework", "Labs", "Projects", "Revision").filter { it !in topics }.forEach { t -> PillChip("+ $t", false) { topics.add(t) } }
                            }
                            Tags(topics)
                        }
                    }
                    item {
                        Group(header = "When are you free?") {
                            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                                Spacer(Modifier.width(48.dp))
                                Matcher.times.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Matcher.days.forEach { d ->
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.take(3), Modifier.width(48.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Matcher.times.forEach { t ->
                                        val slot = "$d $t"
                                        val on = slot in free
                                        Box(
                                            Modifier.weight(1f).padding(3.dp).height(38.dp).clip(RoundedCornerShape(10.dp))
                                                .background(if (on) accent else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { if (on) free.remove(slot) else free.add(slot); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                            contentAlignment = Alignment.Center,
                                        ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                                    }
                                }
                            }
                            Row(Modifier.padding(16.dp)) { PillChip("Weekends", "Weekends" in free) { if ("Weekends" in free) free.remove("Weekends") else free.add("Weekends") } }
                        }
                    }
                    item {
                        Group(header = "Where and how") {
                            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Matcher.locations.forEach { l -> PillChip(l, location == l) { location = l } }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Matcher.contacts.forEach { c -> PillChip(c, contact == c) { contact = c } }
                            }
                        }
                    }
                    item {
                        SendButton("Find my matches", sending) { submit() }
                        val hint = missing ?: if (status == ScriptBridge.Status.CONNECTING) "Connecting…" else null
                        if (hint != null) Text(hint, Modifier.fillMaxWidth().padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
    dlg?.let { d -> ResultDialog(d.title, d.text, d.ok) { dlg = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(list: MutableList<String>) {
    if (list.isEmpty()) { Spacer(Modifier.height(12.dp)); return }
    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.toList().forEach { t ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).clickable { list.remove(t) }
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Icon(Icons.Filled.Close, "Remove", Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

// MARK: - Request a material

object Requests {
    const val SCRIPT = "https://script.google.com/macros/s/AKfycbz_bpogYUiNV8qW9KKw1MK0DUZHd0e3LoxfFndzMeOVirchHBYJ-Jy1bMn2XsWq9sVtxQ/exec"
    val bridge = ScriptBridge(SCRIPT, "requestForm")
    const val COMMUNITY = "https://chat.whatsapp.com/LS92JgjO26Y5SZK9SVJ9FJ"
    const val MAX = 15 * 1024 * 1024
    data class Service(val name: String, val emoji: String, val text: String)
    val services = listOf(
        Service("PDF Summary", "📄", "The key points of your PDF"),
        Service("Questions & Answers", "💬", "Full Q&A from your PDF"),
        Service("Questions Without Answers", "📝", "Practice questions"),
        Service("Test Bank", "📁", "Exam-style question bank"),
        Service("Multiple Choice Questions", "✅", "MCQs with or without answers"),
        Service("Teaching Method", "👨‍🏫", "A plan to study it easily"),
        Service("Explanation", "🧠", "The hard parts, made simple"),
        Service("Tutoring", "🎓", "One-on-one help"),
    )
}

private class PickedFile(val name: String, val bytes: ByteArray)

private fun readPdf(ctx: Context, uri: Uri): PickedFile? = try {
    var name = "file.pdf"
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
    ctx.contentResolver.openInputStream(uri)?.use { PickedFile(name, it.readBytes()) }
} catch (e: Exception) { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestMaterialScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val p = Saved.prefs
    val status by Requests.bridge.status.collectAsState()
    var service by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var course by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf(p.getString("request.whatsapp", "") ?: "") }
    var notes by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val f = withContext(Dispatchers.IO) { readPdf(ctx, uri) }
            when {
                f == null -> dlg = Dlg("Couldn't open", "Couldn't read that file. Try another PDF.", false)
                f.bytes.size > Requests.MAX -> dlg = Dlg("File too big", "Please choose a PDF up to 15 MB.", false)
                else -> file = f
            }
        }
    }

    val digits = whatsapp.count { it.isDigit() }
    val missing = when {
        service.isEmpty() -> "Choose a service."
        file == null -> "Attach the PDF you want it made from."
        course.isBlank() -> "Enter the subject or course."
        digits !in 9..15 -> "Enter your WhatsApp number so we can send it to you."
        payment.isEmpty() -> "Choose how you'll pay."
        else -> null
    }

    fun submit() {
        if (missing != null) { dlg = Dlg("Almost there", missing, false); return }
        val f = file ?: return
        sending = true
        p.edit().putString("request.whatsapp", whatsapp).apply()
        scope.launch {
            val b64 = withContext(Dispatchers.Default) { Base64.encodeToString(f.bytes, Base64.NO_WRAP) }
            val data = buildJsonObject {
                put("service", service); put("course", course.trim()); put("whatsapp", whatsapp.trim()); put("instructions", notes.trim())
                put("payment", payment); put("filename", f.name); put("mimeType", "application/pdf"); put("base64", b64)
            }
            val r = Requests.bridge.call("handleFormSubmission", listOf(data), 120_000)
            sending = false
            r.onSuccess { v ->
                val msg = (v as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                dlg = Dlg("Request sent!", msg ?: "We got your request. Message us on WhatsApp to confirm the payment.", true)
                file = null; service = ""; course = ""; notes = ""
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }.onFailure { dlg = Dlg("Couldn't send", bridgeError(it), false) }
        }
    }

    Screen("Request a material") { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            BridgeHost(Requests.bridge)
            LazyColumn(Modifier.fillMaxSize().imePadding()) {
                item { Hero("✨", "Made for you from your PDF", "Send us a PDF and we'll turn it into a summary, questions, a test bank and more. 3 JD, reviewed by our team.", "اطلب ملخصاً أو بنك أسئلة مخصصاً لك من ملفك") }
                item {
                    Group(header = "Before you pay") {
                        ListRow("Search ASU Bank first", "It may already be there, for free", icon = Icons.Filled.Search, iconColor = Brand.accent) { nav.openTab(Routes.SEARCH) }
                        Divider()
                        ListRow("Ask the WhatsApp community", "Students often share what you need", icon = Icons.Filled.Groups, iconColor = Color(0xFF25D366)) { openExternal(ctx, Requests.COMMUNITY) }
                    }
                }
                item {
                    Group(header = "1. What do you need?") {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Requests.services.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { s ->
                                        val on = service == s.name
                                        Column(
                                            Modifier.weight(1f).heightIn(min = 96.dp).clip(RoundedCornerShape(14.dp))
                                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                .border(BorderStroke(2.dp, if (on) accent else Color.Transparent), RoundedCornerShape(14.dp))
                                                .clickable { service = s.name; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Text(s.emoji, fontSize = 22.sp)
                                            Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(s.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                item {
                    Group(header = "2. Your file and details", footer = "We send the finished file to your WhatsApp.") {
                        Row(
                            Modifier.fillMaxWidth().clickable { picker.launch(arrayOf("application/pdf")) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            KindTile(Kind.PDF)
                            Column(Modifier.weight(1f)) {
                                Text(file?.name ?: "Choose a PDF", maxLines = 1)
                                Text(file?.let { formatBytes(it.bytes.size.toLong()) } ?: "Up to 15 MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (file == null) "Choose" else "Change", color = accent, fontWeight = FontWeight.SemiBold)
                        }
                        FormField(course, { course = it }, "Subject / course, e.g. Calculus 1", caps = true)
                        FormField(whatsapp, { whatsapp = it }, "Your WhatsApp, e.g. 079 123 4567", KeyboardType.Phone)
                        FormField(notes, { notes = it }, "Anything special? (optional)")
                        Spacer(Modifier.height(10.dp))
                    }
                }
                item {
                    Group(header = "3. Payment · 3 JD", footer = "After you submit, message us on WhatsApp to confirm your payment.") {
                        PayRow("CliQ", "Alias ALANQ99 · Bank of Jordan", Icons.Filled.AccountBalance, payment == "CliQ") { payment = "CliQ" }
                        Divider()
                        PayRow("In person", "Pay on campus", Icons.Filled.People, payment == "In-Person") { payment = "In-Person" }
                        if (payment == "CliQ") {
                            Divider()
                            ListRow(if (copied) "Copied ALANQ99" else "Copy CliQ alias ALANQ99", icon = if (copied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy) {
                                copyText(ctx, "CliQ alias", "ALANQ99"); copied = true
                            }
                        }
                    }
                }
                item {
                    SendButton("Send request", sending) { submit() }
                    val hint = missing ?: if (status == ScriptBridge.Status.CONNECTING) "Connecting…" else null
                    if (hint != null) Text(hint, Modifier.fillMaxWidth().padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
    dlg?.let { d ->
        if (d.ok) ResultDialog(d.title, d.text, true, "Message us on WhatsApp", { openExternal(ctx, waMessage("Hi ASU Bank, about my material request")) }) { dlg = null }
        else ResultDialog(d.title, d.text, false) { dlg = null }
    }
}

@Composable
private fun PayRow(title: String, detail: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Brand.green), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(on, onClick)
    }
}

// MARK: - Student ads

@Composable
fun StudentAdsScreen() {
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Screen("Student ads") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Hero("📣", "Student ads", "Advertise your services, products and talents to the whole university. Coming soon.", "أعلن عن خدماتك ومنتجاتك لطلاب الجامعة — قريباً") }
            item {
                Group(header = "Why advertise here") {
                    ListRow("Affordable", "Student-friendly price: 10 JD in total.", icon = Icons.Filled.Sell, iconColor = Brand.orange)
                    Divider()
                    ListRow("Targeted", "Shown to ASU students in the app and on asubank.org.", icon = Icons.Filled.TrackChanges, iconColor = Color(0xFF0A84FF))
                    Divider()
                    ListRow("Fair and safe", "You pay the second half only if your ad is accepted.", icon = Icons.Filled.VerifiedUser, iconColor = Brand.green)
                }
            }
            item {
                Group(header = "Pricing · 10 JD total") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Price("5 JD", "When you send your ad", Modifier.weight(1f))
                        VerticalDivider(Modifier.height(44.dp))
                        Price("5 JD", "Only if it's accepted", Modifier.weight(1f))
                    }
                    Divider()
                    ListRow("CliQ alias: ALANQ99", if (copied) "Copied" else "Bank of Jordan · tap to copy", icon = Icons.Filled.AccountBalance, iconColor = Brand.green) {
                        copyText(ctx, "CliQ alias", "ALANQ99"); copied = true
                    }
                }
            }
            item {
                Group(footer = "Ads are reviewed by the ASU Bank team before they go live.") {
                    ListRow("Tell me when it opens", icon = Icons.Filled.NotificationsActive, iconColor = Brand.accent) { openExternal(ctx, waMessage("Hi ASU Bank, tell me when student ads open. I'd like to advertise: ")) }
                    Divider()
                    ListRow("Ask a question", icon = Icons.Filled.HelpOutline, iconColor = Color(0xFF5E5CE6)) { openExternal(ctx, waMessage("Hi ASU Bank, I have a question about student ads")) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Price(v: String, t: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
        Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// MARK: - Donate

@Composable
fun DonateScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    var copied by remember { mutableStateOf(false) }
    Screen("Support ASU Bank") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Hero("🤍", "Support ASU Bank", "ASU Bank is free and run by a volunteer student. Donations keep it online and growing.", "المنصة مجانية وتطوعية — تبرعك يساعدنا على الاستمرار") }
            item {
                Group(header = "Donate", footer = "Donations are optional and never unlock content. Everything on ASU Bank stays free. ASU Bank never asks for money from anyone else.") {
                    ListRow("CliQ alias: ALANQ99", if (copied) "Copied" else "Bank of Jordan · tap to copy", emoji = "🏦") { copyText(ctx, "CliQ alias", "ALANQ99"); copied = true }
                    Divider()
                    ListRow("PayPal", "paypal.me/necro144 · cards and PayPal balance", emoji = "💙") { openExternal(ctx, "https://www.paypal.com/paypalme/necro144") }
                }
            }
            item {
                Group {
                    ListRow("People who helped ASU Bank", icon = Icons.Filled.Favorite) { nav.openPage("/guides/thankslist", "Thanks list") }
                    Divider()
                    ListRow("Questions about donating", icon = Icons.Filled.Email) { nav.openPage("/guides/contact", "Contact us") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
