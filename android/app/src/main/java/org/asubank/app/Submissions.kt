package org.asubank.app

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Student submissions (presentations for the campus display screen), natively.
 * Same Apps Script as /upload/submit_file and /upload/student_submissions_board (JSONP GET API).
 */
object Submissions {
    private const val API = "https://script.google.com/macros/s/AKfycbxT65oWO1Pve_9i4xThLiKBDZ1KTHlOoetrcOMo1A9QjoK8cm7djR077INB-yVSVaWN/exec"

    @Serializable data class Link(val url: String = "", val platform: String? = null, val type: String? = null, val label: String? = null)

    @Serializable
    data class Item(
        val id: String,
        val studentName: String? = null,
        val presentationTitle: String? = null,
        val fileType: String? = null,
        val primaryUrl: String? = null,
        val platform: String? = null,
        val status: String? = null,
        val submittedDate: String? = null,
        val notes: String? = null,
        val backupLinks: List<Link>? = null,
    ) {
        val title get() = presentationTitle?.ifEmpty { null } ?: "Untitled"
        val name get() = studentName?.ifEmpty { null } ?: "Unknown student"
        val kind get() = when (fileType) {
            "pdf" -> Kind.PDF; "pptx" -> Kind.PPT; "docx" -> Kind.DOC; "image" -> Kind.IMG; "video" -> Kind.VIDEO
            else -> AB.kind(primaryUrl ?: "", title)
        }
        val day get() = submittedDate?.take(15)
    }

    /** Calls the JSONP API and unwraps `cb(...)` */
    suspend fun get(params: Map<String, String>): JsonObject {
        val b = Uri.parse(API).buildUpon()
        params.forEach { (k, v) -> b.appendQueryParameter(k, v) }
        b.appendQueryParameter("callback", "cb")
        var s = Net.text(b.build().toString()) ?: throw ApiError("Couldn't reach the submissions server.")
        val a = s.indexOf('('); val z = s.lastIndexOf(')')
        if (a >= 0 && z > a) s = s.substring(a + 1, z)
        val o = runCatching { json.parseToJsonElement(s) as JsonObject }.getOrNull() ?: throw ApiError("The submissions server sent something unexpected.")
        if (o.bool("success")) return o
        throw ApiError(o.str("error") ?: "Something went wrong.")
    }

    suspend fun list(): List<Item> = get(mapOf("action" to "list"))["data"].decodeAs<List<Item>>().orEmpty()
}

@Composable
private fun StatusBadge(s: String?) = when (s) {
    "approved" -> Badge("APPROVED", Brand.green)
    "pending" -> Badge("PENDING", Brand.orange)
    "review" -> Badge("IN REVIEW", Color(0xFF0A84FF))
    else -> Unit
}

@Composable
fun SubmissionsScreen(admin: Boolean = false) {
    var items by remember { mutableStateOf<List<Submissions.Item>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Submissions.Item?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        loading = true
        try { items = Submissions.list(); error = null } catch (e: ApiError) { error = e.message }
        loading = false
    }
    val q = AB.norm(query)
    val shown = items.filter { (filter == "all" || it.status == filter) && (q.isEmpty() || AB.norm("${it.title} ${it.name}").contains(q)) }

    Screen(if (admin) "Submissions (admin)" else "Student submissions", actions = {
        IconButton(onClick = { submitting = true }) { Icon(Icons.Filled.Add, "Submit a file") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple(items.size, "Total", accent),
                        Triple(items.count { it.status == "approved" }, "Approved", Brand.green),
                        Triple(items.count { it.status != "approved" }, "Waiting", Brand.orange),
                    ).forEach { (n, l, c) ->
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("$n", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c)
                                Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Search titles or names") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All", "approved" to "Approved", "pending" to "Pending", "review" to "In review").forEach { (k, l) ->
                        FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(l) })
                    }
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (loading && items.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (shown.isEmpty()) item { Box(Modifier.padding(top = 40.dp)) { EmptyState(Icons.Filled.Inbox, "Nothing here", "No submissions match.") } }
            else item {
                Group {
                    shown.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().clickable { open = s }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            KindTile(s.kind)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(listOfNotNull(s.name, s.day).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                StatusBadge(s.status)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    open?.let { SubmissionDetail(it, admin, onClose = { open = null }, changed = { reload++ }) }
    if (submitting) SubmitDialog(onClose = { submitting = false }, done = { reload++ })
}

@Composable
private fun SubmissionDetail(item: Submissions.Item, admin: Boolean, onClose: () -> Unit, changed: () -> Unit) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf(Saved.prefs.getString("subs.secret", "") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.name); StatusBadge(item.status) }
                item.day?.let { Text("Submitted $it", style = MaterialTheme.typography.bodySmall) }
                item.notes?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                item.primaryUrl?.takeIf { it.isNotEmpty() }?.let { u ->
                    TextButton(onClick = { onClose(); openLink(nav, ctx, u, item.title) }) { Text("Open the file (${item.platform ?: "link"})") }
                }
                item.backupLinks.orEmpty().filter { it.url.isNotEmpty() }.forEachIndexed { i, l ->
                    TextButton(onClick = { onClose(); openLink(nav, ctx, l.url, item.title) }) { Text("Backup ${i + 1} (${l.platform ?: "link"})") }
                }
                if (admin) {
                    OutlinedTextField(
                        secret, { secret = it }, label = { Text("Admin secret") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (admin) TextButton(enabled = secret.isNotEmpty() && !busy, onClick = {
                busy = true
                Saved.prefs.edit().putString("subs.secret", secret).apply()
                scope.launch {
                    try {
                        Submissions.get(mapOf("action" to "delete", "id" to item.id, "secret" to secret))
                        Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show()
                        changed(); onClose()
                    } catch (e: ApiError) { error = e.message }
                    busy = false
                }
            }) { Icon(Icons.Filled.Delete, null); Text(" Delete", color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onClose) { Text("Done") }
        },
        dismissButton = { if (admin) TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
private fun SubmitDialog(onClose: () -> Unit, done: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Saved.prefs.getString("subs.name", "") ?: "") }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("pdf") }
    var platform by remember { mutableStateOf("Google Drive") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val backups = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && title.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://"))
    val types = listOf("pdf" to "📄 PDF", "pptx" to "📊 PowerPoint", "docx" to "📝 Word", "image" to "🖼️ Image", "video" to "🎬 Video")

    fun submit() {
        busy = true
        Saved.prefs.edit().putString("subs.name", name).apply()
        val links = backups.filter { it.isNotBlank() }
        val payload = buildJsonObject {
            put("studentName", name.trim()); put("presentationTitle", title.trim()); put("fileType", type)
            put("platform", platform); put("primaryUrl", url.trim()); put("notes", notes); put("isBackup", links.isNotEmpty())
            putJsonArray("backupLinks") { links.forEach { l -> addJsonObject { put("url", l.trim()); put("platform", "Other"); put("type", type); put("label", title) } } }
        }
        scope.launch {
            try { Submissions.get(mapOf("action" to "submit", "data" to payload.toString())); sent = true; done() } catch (e: ApiError) { error = e.message }
            busy = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Screen("Submit a file", back = false, actions = {
                TextButton(onClick = onClose) { Text(if (sent) "Done" else "Cancel") }
                if (!sent) TextButton(onClick = ::submit, enabled = valid && !busy) { Text(if (busy) "Sending…" else "Submit") }
            }) { pad ->
                if (sent) Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Brand.green, modifier = Modifier.height(64.dp))
                    Text("Submitted!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("The team reviews it before it goes on the campus display screen.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(title, { title = it }, label = { Text("Presentation title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Type", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { (k, l) -> FilterChip(selected = type == k, onClick = { type = k }, label = { Text(l) }) }
                    }
                    Text("Stored on", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScrollRow(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Google Drive", "OneDrive", "Dropbox", "Other").forEach { p -> FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p) }) }
                    }
                    OutlinedTextField(
                        url, { url = it.trim() }, label = { Text("Link to the file (https://…)") }, singleLine = true,
                        supportingText = { Text("Share the file so anyone with the link can view it.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                    )
                    backups.forEachIndexed { i, b ->
                        OutlinedTextField(
                            b, { backups[i] = it.trim() }, label = { Text("Backup link ${i + 1}") }, singleLine = true,
                            trailingIcon = { IconButton(onClick = { backups.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove") } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(onClick = { backups.add("") }) { Icon(Icons.Filled.Add, null); Text(" Add a backup link") }
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollRow(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
