package org.asubank.app

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
import androidx.compose.material3.Text
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
    // the same link is often listed on several subject pages: show it once
    val local = remember(q, items) { AB.match(items, q, 300).distinctBy { AB.driveId(it.m.u) ?: it.m.u } }
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
                } else item { EmptyState(Icons.Filled.Search, "Search ASU Bank", "Find summaries, past papers and test banks in ${items.size} materials and the shared Google Drive.") }
            } else {
                groupItems(localShown.take(120), header = "Materials · ${localShown.size}", key = { "l" + it.m.u }) { MaterialRow(it.m, it.kind) }
                item {
                    Group(header = "Google Drive" + if (driveState == 2) " · ${driveShown.size}" else "") {
                        when (driveState) {
                            1 -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.height(20.dp)); Text("Searching the shared Drive…")
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
                    val nav = LocalNav.current
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(Icons.Filled.SearchOff, "Nothing found", "Try fewer words, or ask for this material.")
                        Button(onClick = { nav.openPage("/requests/student_request_portal", "Request a material") }) { Text("Request a material") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// MARK: Upload (same Apps Script as asubank.org/upload/uploadmethod)

private class Pending(val uri: Uri, val name: String, val size: Long, val mime: String) {
    var state by mutableStateOf("ready") // ready, uploading, done, failed
    var progress by mutableStateOf(0f)
    var message by mutableStateOf("")
}

private const val MAX_BYTES = 25L * 1024 * 1024
private val allowed = setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "jpg", "jpeg", "png", "heic", "webp", "zip", "rar")
private val uploadKinds = listOf("Final", "Midterm", "Quiz", "Summary", "Notes", "Slides", "Test bank", "Assignment")

/** Counts bytes as OkHttp writes them, for a real progress bar */
private class ProgressBody(private val bytes: ByteArray, private val type: MediaType, private val onProgress: (Float) -> Unit) : RequestBody() {
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
fun UploadScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val subjects by Library.subjects.collectAsState()
    var agreed by remember { mutableStateOf(Saved.prefs.getBoolean("upload.agreed", false)) }
    val files = remember { mutableStateListOf<Pending>() }
    var subject by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val years = remember { (0 until 8).map { (LocalDate.now().year - it).toString() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        for (u in uris) {
            var name = "file"
            var size = 0L
            ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            val p = Pending(u, name, size, ctx.contentResolver.getType(u) ?: "application/octet-stream")
            when {
                ext !in allowed -> { p.state = "failed"; p.message = "This file type isn't allowed." }
                size > MAX_BYTES -> { p.state = "failed"; p.message = "Bigger than 25 MB." }
            }
            files.add(p)
        }
    }

    fun uploadAll() {
        busy = true
        message = null
        scope.launch {
            var ok = 0
            for (f in files.filter { it.state == "ready" || it.state == "failed" && it.message.startsWith("Upload") }) {
                f.state = "uploading"; f.progress = 0f
                val result = withContext(Dispatchers.IO) {
                    try {
                        val data = ctx.contentResolver.openInputStream(f.uri)!!.use { it.readBytes() }
                        val body = buildJsonObject {
                            put("name", f.name); put("mimeType", f.mime); put("data", Base64.encodeToString(data, Base64.NO_WRAP))
                            put("subject", subject); put("kind", kind); put("year", year); put("note", note.trim())
                        }.toString().toByteArray()
                        val client = Net.http.newBuilder().writeTimeout(5, TimeUnit.MINUTES).readTimeout(5, TimeUnit.MINUTES).build()
                        val req = Request.Builder().url(AB.UPLOAD)
                            .post(ProgressBody(body, "text/plain;charset=utf-8".toMediaType()) { p -> f.progress = p })
                            .build()
                        client.newCall(req).execute().use { r ->
                            val o = runCatching { json.parseToJsonElement(r.body?.string() ?: "").jsonObject }.getOrNull()
                            if ((o?.get("success") as? JsonPrimitive)?.booleanOrNull == true) null
                            else (o?.get("error") as? JsonPrimitive)?.contentOrNull ?: "Upload failed. Please try again."
                        }
                    } catch (e: Exception) { "Upload failed: ${e.message ?: "no connection"}" }
                }
                if (result == null) { f.state = "done"; ok++ } else { f.state = "failed"; f.message = result }
            }
            busy = false
            message = if (ok > 0) "Thank you! $ok file${if (ok == 1) "" else "s"} uploaded. They'll appear after a quick check." else "Nothing was uploaded."
        }
    }

    Screen("Upload", back = false, large = true) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(footer = "Files go to the shared ASU Bank Drive and are checked before they appear.") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• PDF, Word, PowerPoint, Excel, images or ZIP, up to 25 MB each.")
                        Text("• Hide your university number and name on scans.")
                        Text("• No illegal, private or copyrighted material.")
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("I agree to these rules", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = agreed, onCheckedChange = { agreed = it; Saved.prefs.edit().putBoolean("upload.agreed", it).apply() })
                    }
                }
            }
            item {
                Group(header = "Your files", footer = if (!agreed) "Turn on “I agree” first." else null) {
                    ListRow("Choose files", "From your phone, Drive or Downloads", icon = Icons.Filled.NoteAdd, titleColor = if (agreed && !busy) accent else MaterialTheme.colorScheme.onSurfaceVariant) {
                        if (agreed && !busy) picker.launch(arrayOf("*/*"))
                    }
                    files.forEach { f ->
                        Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KindTile(AB.kind("", f.name))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                when (f.state) {
                                    "uploading" -> LinearProgressIndicator(progress = { f.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                    "failed" -> Text(f.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    "done" -> Text("Uploaded", color = Brand.green, style = MaterialTheme.typography.bodySmall)
                                    else -> Text(formatBytes(f.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            when (f.state) {
                                "done" -> Icon(Icons.Filled.CheckCircle, null, tint = Brand.green)
                                "uploading" -> {}
                                else -> IconButton(onClick = { if (!busy) files.remove(f) }) { Icon(Icons.Filled.Close, "Remove") }
                            }
                        }
                    }
                }
            }
            item {
                Group(header = "Details (optional)") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Picker("Subject", subject, listOf("") + subjects.filter { it.c != "Archive" }.map { it.t }) { subject = it }
                        Picker("Type", kind, listOf("") + uploadKinds) { kind = it }
                        Picker("Year", year, listOf("") + years) { year = it }
                        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (doctor, semester…)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val ready = files.count { it.state == "ready" }
                    Button(onClick = { uploadAll() }, enabled = agreed && !busy && ready > 0, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Icon(Icons.Filled.CloudUpload, null)
                        Spacer(Modifier.padding(4.dp))
                        Text(if (busy) "Uploading…" else if (ready > 1) "Upload $ready files" else "Upload")
                    }
                    message?.let { Text(it, fontWeight = FontWeight.Medium) }
                    if (files.any { it.state == "done" }) OutlinedButton(onClick = { files.clear(); message = null }) { Text("Upload more") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
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
