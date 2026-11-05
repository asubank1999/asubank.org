package org.unihub.app

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.concurrent.TimeUnit

// MARK: Search

private val searchKinds = listOf("all" to "All", "folder" to "Folders", "pdf" to "PDF", "ppt" to "Slides", "doc" to "Docs")

private fun kindMatches(k: Kind, f: String) = when (f) {
    "folder" -> k == Kind.FOLDER
    "pdf" -> k == Kind.PDF
    "ppt" -> k == Kind.PPT
    "doc" -> k == Kind.DOC || k == Kind.SHEET
    else -> true
}

@Composable
fun SearchScreen() {
    val pending by AppState.pendingSearch.collectAsState()
    val items by Library.items.collectAsState()
    val searches by Saved.searches.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var drive by remember { mutableStateOf<List<DriveResult>>(emptyList()) }
    var driveState by remember { mutableStateOf(0) } // 0 idle, 1 loading, 2 done, 3 failed
    var retry by remember { mutableStateOf(0) }
    val subjects by Library.subjects.collectAsState()
    var hints by remember { mutableStateOf(SearchHints()) }
    var onlineAlt by remember { mutableStateOf<String?>(null) }
    val otherLanguage = hints.translated ?: onlineAlt
    val nav = LocalNav.current

    LaunchedEffect(q, items) {
        onlineAlt = null
        delay(150)
        val h = withContext(Dispatchers.Default) { SearchHelp.hints(q, items, subjects) }
        hints = h
        // words the list does not know go to the translator once typing pauses
        val t = q.trim()
        if (h.translated == null && t.length >= 3 && t.any { it.isLetter() }) { delay(800); onlineAlt = SearchHelp.translate(t) }
    }
    LaunchedEffect(pending) { pending?.let { q = it; AppState.pendingSearch.value = null } }
    // Drive search waits for the student to stop typing
    LaunchedEffect(q, retry) {
        drive = emptyList()
        if (q.trim().length < 3) { driveState = 0; return@LaunchedEffect }
        delay(700)
        driveState = 1
        val r = DriveSearch.search(q.trim())
        if (r == null) driveState = 3 else { drive = r; driveState = 2 }
        Saved.searched(q)
    }
    // Drive results for the translated search join the ones already shown
    LaunchedEffect(otherLanguage, driveState) {
        val alt = otherLanguage
        if (alt != null && driveState == 2) DriveSearch.search(alt)?.let { r -> drive = (drive + r).distinctBy { it.m.u } }
    }
    // the same link is often listed on several subject pages: show it once
    val local = remember(q, items, hints, otherLanguage) {
        (AB.match(items, q, 300, hints.alts, hints.skip) + (otherLanguage?.let { AB.match(items, it, 120) } ?: emptyList())).distinctBy { AB.driveId(it.m.u) ?: it.m.u }
    }
    val localShown = local.filter { kindMatches(it.kind, filter) }
    val localUrls = remember(local) { local.map { AB.driveId(it.m.u) ?: it.m.u }.toSet() }
    val driveShown = drive.filter { kindMatches(it.kind, filter) && (AB.driveId(it.m.u) ?: it.m.u) !in localUrls }.distinctBy { it.m.u }

    Screen("Search", back = false) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text("Subject, course code, doctor…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Clear, "Clear") } },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { Saved.searched(q) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchKinds) { (k, label) -> FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) }) }
                }
            }
            if (q.isBlank()) {
                if (searches.isNotEmpty()) item {
                    Group(header = "Recent searches") {
                        searches.forEachIndexed { i, s ->
                            if (i > 0) Divider()
                            ListRow(s, icon = Icons.Filled.History) { q = s }
                        }
                        Divider()
                        ListRow("Clear recent searches", titleColor = MaterialTheme.colorScheme.error) { Saved.clearSearches() }
                    }
                } else item { EmptyState(Icons.Filled.Search, "Search UniHub", trf("Find summaries, past papers and test banks in %d materials and the shared Google Drive.", items.size)) }
                item {
                    Group(header = "What are you looking for?", footer = "Type the course name in Arabic or English, with mistakes too. We translate it, suggest the right word and search both languages.") {
                        listOf("Past exams and finals" to "final", "Summaries" to "summary", "Slides" to "slides", "Test banks" to "test bank").forEachIndexed { i, (label, word) ->
                            if (i > 0) Divider()
                            ListRow(label, icon = Icons.Filled.Search) { q = word }
                        }
                        Divider()
                        ListRow("Not sure of the name? Browse by college", icon = Icons.Filled.NoteAdd) { nav.openPage("/banks/subjects", "All subjects") }
                    }
                }
            } else {
                hints.fixes.firstOrNull()?.let { fix -> item { HelpChips(tr("Did you mean?"), listOf(fix)) { q = it } } }
                if (hints.completions.isNotEmpty()) item { HelpChips(tr("Suggestions"), hints.completions) { q = it } }
                otherLanguage?.let { alt -> item { HelpChips(tr("Also searching for"), listOf(alt)) { q = it } } }
                if (hints.subjects.isNotEmpty()) item {
                    Group(header = "Subject") {
                        hints.subjects.forEachIndexed { i, s ->
                            if (i > 0) Divider()
                            ListRow(s.t, emoji = s.e ?: "📚") { nav.openPage(s.p, s.t) }
                        }
                    }
                }
                item {
                    val narrow = listOf("Final", "Midterm", "Quiz", "Summary", "Slides", "Test bank").filter { AB.norm(it) !in AB.norm(q) }
                    HelpChips(tr("Narrow it down"), narrow) { w -> q = q.trim() + " " + w }
                }
                groupItems(localShown.take(120), header = trf("Materials · %d", localShown.size), key = { "l" + it.m.u }) { MaterialRow(it.m, it.kind) }
                item {
                    Group(header = "Google Drive" + if (driveState == 2) " · ${driveShown.size}" else "") {
                        when (driveState) {
                            1 -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ABDots(Modifier.height(20.dp)); Text("Searching the shared Drive…")
                            }
                            3 -> ListRow("Drive search failed. Tap to try again.", icon = Icons.Filled.Error) { retry++ }
                            2 -> if (driveShown.isEmpty()) ListRow("No other files on Drive", icon = Icons.Filled.SearchOff)
                                 else driveShown.take(150).forEachIndexed { i, r ->
                                     if (i > 0) Divider()
                                     MaterialRow(r.m, r.kind, meta = listOfNotNull(r.date.take(10).ifEmpty { null }, if (r.size > 0) formatBytes(r.size) else null).joinToString(" · "))
                                 }
                            else -> ListRow("Type at least 3 letters to search Google Drive", icon = Icons.Filled.Search)
                        }
                    }
                }
                if (localShown.isEmpty() && driveState == 2 && driveShown.isEmpty()) item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(Icons.Filled.SearchOff, "Nothing found", "Try fewer words, or ask for this material.")
                        Group(header = "Still can't find it?") {
                            ListRow("Browse every subject by college", icon = Icons.Filled.Search) { nav.openPage("/banks/subjects", "All subjects") }
                            Divider()
                            ListRow("Ask other students in the chat", icon = Icons.Filled.NoteAdd) { ChatNotifier.pendingRoom.value = "materials"; nav.openTab(Routes.CHAT) }
                            Divider()
                            ListRow("Request a material", icon = Icons.Filled.CloudUpload) { nav.openPage("/requests/student_request_portal", "Request a material") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Counts bytes as OkHttp writes them, for a real progress bar */
class ProgressBody(private val bytes: ByteArray, private val type: MediaType, private val onProgress: (Float) -> Unit) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        val src = ByteArrayInputStream(bytes).source().buffer()
        var sent = 0L
        val buf = okio.Buffer()
        while (true) {
            val n = src.read(buf, 64 * 1024)
            if (n < 0) break
            sink.write(buf, n)
            sent += n
            onProgress(sent.toFloat() / bytes.size)
        }
        src.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPicker(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value.ifEmpty { "Any" }, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o.ifEmpty { "Any" }) }, onClick = { onPick(o); open = false }) }
        }
    }
}
