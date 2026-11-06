package org.unihub.app

import androidx.compose.material.icons.filled.PlayCircle
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** A picked file. The bytes stay where they are and are read a chunk at a time, so size doesn't matter. */
class Pending(val uri: Uri, val name: String, val size: Long, val mime: String) {
    var state by mutableStateOf("ready") // ready, uploading, done, failed, rejected
    var progress by mutableStateOf(0f)
    var message by mutableStateOf("")
}

/**
 * Big files go straight to Google Drive in chunks. The upload script only opens the session (action "start"),
 * so there's no size limit in the app or the script, and a dropped connection resumes where it stopped.
 * The queue lives outside the screen, so leaving the screen doesn't stop an upload.
 */
object UploadQueue {
    private const val CHUNK = 8 * 1024 * 1024 // Drive wants multiples of 256 KB
    private val allowed = setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "jpg", "jpeg", "png", "heic", "webp", "zip", "rar", "7z", "csv",
        "mp4", "mov", "m4v", "webm", "mp3", "m4a")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val octet = "application/octet-stream".toMediaType()

    val files = mutableStateListOf<Pending>()
    var subject by mutableStateOf("")
    var kind by mutableStateOf("")
    var year by mutableStateOf("")
    var note by mutableStateOf("")
    var busy by mutableStateOf(false)
    var finished by mutableStateOf<Int?>(null)

    val overall: Float get() {
        val active = files.filter { it.state != "rejected" }
        val total = active.sumOf { it.size.coerceAtLeast(1) }
        if (total <= 0) return 0f
        var sent = 0f
        active.forEach { f ->
            if (f.state == "done") sent += f.size.coerceAtLeast(1).toFloat()
            else if (f.state == "uploading") sent += f.progress * f.size.coerceAtLeast(1)
        }
        return sent / total
    }

    fun add(ctx: Context, uris: List<Uri>) {
        scope.launch {
            for (u in uris) {
                val p = withContext(Dispatchers.IO) { describe(ctx, u) }
                val ext = p.name.substringAfterLast('.', "").lowercase()
                if (ext !in allowed) { p.state = "rejected"; p.message = tr("This file type isn't allowed.") }
                files.add(p)
            }
        }
    }

    private fun describe(ctx: Context, u: Uri): Pending {
        var name = "file"
        var size = 0L
        ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        val mime = ctx.contentResolver.getType(u) ?: "application/octet-stream"
        if (!name.contains('.')) MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { name = "$name.$it" }
        return Pending(u, name, size, mime)
    }

    fun clearDone() {
        files.removeAll { it.state == "done" }
        finished = null
    }

    fun start(ctx: Context) {
        if (busy) return
        busy = true
        finished = null
        val app = ctx.applicationContext
        scope.launch {
            var ok = 0
            for (f in files.toList().filter { it.state == "ready" }) {
                f.state = "uploading"; f.progress = 0f
                val error = withContext(Dispatchers.IO) {
                    try { val url = send(app, f); MyUploads.add(f.name, url); null }
                    catch (e: Exception) { e.message ?: tr("no connection") }
                }
                if (error == null) { f.state = "done"; f.progress = 1f; ok++ }
                else { f.state = "failed"; f.message = trf("Upload failed: %s", error) }
            }
            busy = false
            if (ok > 0) finished = ok
        }
    }

    private fun open(ctx: Context, f: Pending, at: Long): InputStream {
        val s = ctx.contentResolver.openInputStream(f.uri) ?: throw IOException(tr("Couldn't read this file."))
        var left = at
        while (left > 0) {
            val n = s.skip(left)
            if (n <= 0) { if (s.read() < 0) break; left-- } else left -= n
        }
        return s
    }

    /** Some apps don't report a file's size, so count it */
    private fun measure(ctx: Context, f: Pending): Long {
        if (f.size > 0) return f.size
        var total = 0L
        val buf = ByteArray(1 shl 16)
        ctx.contentResolver.openInputStream(f.uri)?.use { s -> while (true) { val n = s.read(buf); if (n < 0) break; total += n } }
        return total
    }

    private fun startSession(f: Pending, size: Long): String {
        val body = buildJsonObject {
            put("action", "start"); put("name", f.name); put("mimeType", f.mime); put("size", size)
            put("subject", subject); put("kind", kind); put("year", year); put("note", note.trim())
        }.toString()
        val req = Request.Builder().url(AB.UPLOAD).post(body.toRequestBody("text/plain;charset=utf-8".toMediaType())).build()
        Net.http.newCall(req).execute().use { r ->
            val o = runCatching { json.parseToJsonElement(r.body?.string() ?: "").jsonObject }.getOrNull()
            val url = (o?.get("uploadUrl") as? JsonPrimitive)?.contentOrNull
            if ((o?.get("success") as? JsonPrimitive)?.booleanOrNull == true && url != null) return url
            throw IOException((o?.get("error") as? JsonPrimitive)?.contentOrNull ?: tr("Upload failed. Please try again."))
        }
    }

    private fun link(body: String?): String {
        val id = runCatching { (json.parseToJsonElement(body ?: "").jsonObject["id"] as? JsonPrimitive)?.contentOrNull }.getOrNull() ?: return ""
        return "https://drive.google.com/file/d/$id/view"
    }

    /** The next byte Drive wants, from a 308 reply. A 308 here is "keep going", never a redirect. */
    private fun nextOffset(range: String?): Long = range?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: 0L

    private suspend fun send(ctx: Context, f: Pending): String {
        val size = measure(ctx, f)
        if (size <= 0) throw IOException(tr("The file is empty."))
        val session = startSession(f, size)
        val http: OkHttpClient = Net.http.newBuilder().followRedirects(false)
            .writeTimeout(5, TimeUnit.MINUTES).readTimeout(5, TimeUnit.MINUTES).build()
        val buf = ByteArray(CHUNK)
        var offset = 0L
        var input = open(ctx, f, 0)
        var inputAt = 0L
        var tries = 0
        try {
            while (true) {
                if (inputAt != offset) { input.close(); input = open(ctx, f, offset); inputAt = offset }
                var len = 0
                while (len < CHUNK) {
                    val n = input.read(buf, len, CHUNK - len)
                    if (n < 0) break
                    len += n
                }
                inputAt += len
                if (len == 0) throw IOException(tr("Couldn't read this file."))
                val base = offset
                val req = Request.Builder().url(session)
                    .header("Content-Range", "bytes $base-${base + len - 1}/$size")
                    .put(ProgressBody(if (len == CHUNK) buf else buf.copyOf(len), octet) { p -> f.progress = (base + p * len) / size })
                    .build()
                var retry = false
                try {
                    http.newCall(req).execute().use { r ->
                        when {
                            r.code == 200 || r.code == 201 -> return link(r.body?.string())
                            r.code == 308 -> { offset = nextOffset(r.header("Range")); tries = 0 }
                            r.code == 404 || r.code == 410 -> throw UploadRefused(tr("The upload expired. Please try again."))
                            r.code in 400..499 && r.code != 408 && r.code != 429 -> throw UploadRefused(tr("Upload failed. Please try again."))
                            else -> { retry = true }
                        }
                        Unit
                    }
                } catch (e: UploadRefused) {
                    throw e
                } catch (e: IOException) {
                    retry = true
                }
                if (retry) {
                    tries++
                    if (tries > 6) throw IOException(tr("Network error. Check your connection and try again."))
                    delay(1000L shl tries)
                    // ask Drive how much arrived
                    val ask = Request.Builder().url(session).header("Content-Range", "bytes */$size").put(ByteArray(0).toRequestBody(null)).build()
                    runCatching {
                        http.newCall(ask).execute().use { r ->
                            when (r.code) {
                                200, 201 -> return link(r.body?.string())
                                308 -> { offset = nextOffset(r.header("Range")) }
                                else -> {}
                            }
                            Unit
                        }
                    }
                }
            }
        } finally {
            runCatching { input.close() }
        }
    }

    private class UploadRefused(message: String) : IOException(message)
}

private val uploadKinds = listOf("Final", "Midterm", "Quiz", "Summary", "Notes", "Slides", "Test bank", "Assignment")

@Composable
private fun SourceTile(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = accent)
        Text(label, color = accent, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun UploadScreen() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val subjects by Library.subjects.collectAsState()
    var agreed by remember { mutableStateOf(Saved.prefs.getBoolean("upload.agreed", false)) }
    var rulesOpen by remember { mutableStateOf(false) }
    val years = remember { (0 until 8).map { (LocalDate.now().year - it).toString() } }
    val q = UploadQueue
    val busy = q.busy
    val shareNav = LocalNav.current

    // the phone mustn't sleep halfway through a big file
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { q.add(ctx, it) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { q.add(ctx, it) }

    Screen("Upload", large = true) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Group { ListRow("Share a YouTube video", icon = Icons.Filled.PlayCircle) { shareNav.navigate("shareVideo") } } }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.82f), accent))).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (q.finished != null) Icons.Filled.CheckCircle else Icons.Filled.CloudUpload, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Column {
                            val done = q.finished
                            if (done != null) Text(trf("Thank you! %d files uploaded. They're in Latest uploads now.", done), color = Color.White, fontWeight = FontWeight.SemiBold)
                            else {
                                Text("Share your study files", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text("Any file size, as many files as you like.", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (busy) {
                        val all = q.overall
                        LinearProgressIndicator(progress = { all }, modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f))
                        Text("${(all * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    if (q.finished != null) OutlinedButton(onClick = { q.clearDone() }) { Text("Upload more", color = Color.White) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceTile("Files", Icons.Filled.Folder, !busy, Modifier.weight(1f)) { filePicker.launch(arrayOf("*/*")) }
                    SourceTile("Photos", Icons.Filled.PhotoLibrary, !busy, Modifier.weight(1f)) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                }
            }
            if (q.files.isNotEmpty()) item {
                val waiting = q.files.filter { it.state == "ready" }.sumOf { it.size }
                Group(header = if (waiting > 0) tr("Your files") + " · " + formatBytes(waiting) else "Your files") {
                    q.files.forEachIndexed { i, f ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KindTile(AB.kind("", f.name))
                            Column(Modifier.weight(1f)) {
                                androidx.compose.material3.Text(f.name, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                when (f.state) {
                                    "uploading" -> {
                                        LinearProgressIndicator(progress = { f.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                                        Text("${(f.progress * 100).toInt()}% · ${formatBytes(f.size)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                    "failed", "rejected" -> Text(f.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    "done" -> Text("Uploaded", color = Brand.green, style = MaterialTheme.typography.bodySmall)
                                    else -> Text(formatBytes(f.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            when (f.state) {
                                "done" -> Icon(Icons.Filled.CheckCircle, null, tint = Brand.green)
                                "uploading" -> {}
                                "failed" -> {
                                    IconButton(onClick = { if (!busy) f.state = "ready" }) { Icon(Icons.Filled.Refresh, tr("Try again"), tint = accent) }
                                    IconButton(onClick = { if (!busy) q.files.remove(f) }) { Icon(Icons.Filled.Close, tr("Remove")) }
                                }
                                else -> IconButton(onClick = { if (!busy) q.files.remove(f) }) { Icon(Icons.Filled.Close, tr("Remove")) }
                            }
                        }
                    }
                }
            }
            item {
                Group(header = "Details", footer = "Files are named like “[Subject] [Final 2025] name.pdf” so other students can find them.") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UploadPicker("Subject", q.subject, listOf("") + subjects.filter { it.c != "Archive" }.map { it.t }) { q.subject = it }
                        UploadPicker("Type", q.kind, listOf("") + uploadKinds) { q.kind = it }
                        UploadPicker("Year", q.year, listOf("") + years) { q.year = it }
                        OutlinedTextField(value = q.note, onValueChange = { q.note = it }, label = { Text("Doctor / note (optional)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Group(footer = if (busy) "Keep the app open until your files finish. You can leave this screen." else null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("I agree to the upload rules", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = agreed, onCheckedChange = { agreed = it; Saved.prefs.edit().putBoolean("upload.agreed", it).apply() })
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth().clickable { rulesOpen = !rulesOpen }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Upload rules", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Icon(if (rulesOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    }
                    if (rulesOpen) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• " + tr("Hide your university number and name on scans."))
                        Text("• " + tr("No illegal, private or copyrighted material."))
                        Text("• " + tr("Use a clear name, e.g. “Calculus 1 Midterm 2025 Dr Ahmad.pdf”."))
                    }
                }
            }
            item {
                val ready = q.files.count { it.state == "ready" }
                Button(
                    onClick = { q.start(ctx) }, enabled = agreed && !busy && ready > 0,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) {
                    Icon(Icons.Filled.CloudUpload, null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (busy) tr("Uploading…") else if (ready > 1) trf("Upload %d files", ready) else tr("Upload"))
                }
            }
        }
    }
}
