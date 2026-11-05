package org.unihub.app

import androidx.compose.material.icons.filled.Flag
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

// MARK: opening a material

sealed class VState {
    data object Resolving : VState()
    data class Downloading(val p: Float) : VState()
    data class Folder(val id: String) : VState()
    data class Pdf(val file: File) : VState()
    data class Img(val file: File) : VState()
    data class Web(val url: String) : VState()
    data class Card(val file: File?, val why: String, val preview: String? = null) : VState()
    data class Broken(val why: String) : VState()
}

object Opener {
    fun cacheDir(): File = File(Disk.ctx.cacheDir, "materials").apply { mkdirs() }

    fun driveDownload(id: String) = "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"

    /** Same value on every launch */
    fun stableHash(s: String): String {
        var h = 5381L
        for (b in s.toByteArray()) h = (h * 33) + (b.toLong() and 0xff)
        return java.lang.Long.toString(h and Long.MAX_VALUE, 36)
    }

    /** Opening a shortcut's /view page redirects to the real file */
    suspend fun resolveShortcut(id: String): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
        try {
            Net.http.newCall(Request.Builder().url("https://drive.google.com/file/d/$id/view").header("User-Agent", Net.UA).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val body = r.body?.string()?.take(600_000) ?: ""
                (AB.driveId(r.request.url.toString()) ?: id) to body.contains("application/vnd.google-apps.folder")
            }
        } catch (e: Exception) { null }
    }

    /** Downloads into the cache (or reuses it). Null when the server sent a web page instead of a file. */
    suspend fun download(url: String, key: String, name: String, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val safe = key.take(80).replace("/", "_")
        cacheDir().listFiles()?.firstOrNull { it.nameWithoutExtension == safe }?.let { return@withContext it }
        try {
            Net.http.newCall(Request.Builder().url(url).header("User-Agent", Net.UA).build()).execute().use { r ->
                val type = r.header("Content-Type") ?: ""
                if (!r.isSuccessful || type.startsWith("text/html")) return@use null
                val body = r.body ?: return@use null
                val total = body.contentLength()
                val tmp = File(cacheDir(), "$safe.part")
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) progress(done.toFloat() / total)
                        }
                    }
                }
                val cd = r.header("Content-Disposition") ?: ""
                val fname = Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)""").find(cd)?.groupValues?.get(1) ?: name
                var ext = fname.substringAfterLast('.', "").lowercase().take(5)
                if (ext.isEmpty() || ext == "bin") ext = if (type.contains("pdf")) "pdf" else name.substringAfterLast('.', "").lowercase().take(5)
                if (isPdf(tmp)) ext = "pdf"
                val dest = File(cacheDir(), safe + "." + ext.ifEmpty { "bin" })
                dest.delete()
                tmp.renameTo(dest)
                dest
            }
        } catch (e: Exception) { null }
    }

    fun isPdf(f: File): Boolean = try {
        f.inputStream().use { val b = ByteArray(5); it.read(b) == 5 && String(b) == "%PDF-" }
    } catch (e: Exception) { false }

    fun show(f: File, drivePreview: String?): VState {
        val ext = f.extension.lowercase()
        if (ext == "pdf" || isPdf(f)) return VState.Pdf(f)
        if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")) return VState.Img(f)
        if (ext in setOf("doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "rtf", "odt") && drivePreview != null) return VState.Web(drivePreview)
        val why = when (ext) {
            "zip", "rar", "7z" -> trf("This is a compressed %s file. Open it with a files app.", ext.uppercase())
            "doc", "docx", "ppt", "pptx", "xls", "xlsx" -> "Open it with Word, PowerPoint, Excel or Google Docs."
            else -> "Open it with another app on your phone."
        }
        return VState.Card(f, why, drivePreview)
    }

    suspend fun open(m: Material, progress: (Float) -> Unit): VState {
        val u = m.u
        DriveLinks.broken(u)?.let { return VState.Broken(it) }
        val kind = AB.kind(u, m.t)

        if (kind == Kind.FOLDER && u.contains("google.com")) AB.driveId(u)?.let { return VState.Folder(it) }

        // Google Docs / Slides / Sheets: export to PDF and show natively
        val g = AB.group("""docs\.google\.com/(document|presentation|spreadsheets)/d/""", u)
        val gid = AB.group("""/d/([\w-]{10,})""", u)
        if (g != null && gid != null) {
            val export = if (g == "presentation") "https://docs.google.com/presentation/d/$gid/export/pdf" else "https://docs.google.com/$g/d/$gid/export?format=pdf"
            download(export, gid, m.t + ".pdf", progress)?.let { return show(it, null) }
            return VState.Web("https://docs.google.com/$g/d/$gid/preview")
        }

        // Google Drive files (including shortcuts to files or folders)
        if (u.contains("drive.google.com")) {
            var id = AB.driveId(u)
            if (id != null) {
                DriveLinks.target(id)?.let { (t, folder) -> if (folder) return VState.Folder(t); id = t }
                val fid = id!!
                download(driveDownload(fid), fid, m.t, progress)?.let { return show(it, "https://drive.google.com/file/d/$fid/preview") }
                // Not a downloadable file: maybe a shortcut the server hasn't seen yet
                resolveShortcut(fid)?.let { (t, folder) ->
                    if (folder) return VState.Folder(t)
                    if (t != fid) download(driveDownload(t), t, m.t, progress)?.let { return show(it, "https://drive.google.com/file/d/$t/preview") }
                    return VState.Web("https://drive.google.com/file/d/$t/preview")
                }
                return VState.Web("https://drive.google.com/file/d/$fid/preview")
            }
        }

        // Dropbox files: direct download
        if (u.contains("dropbox.com/scl/fi") || u.contains("dropbox.com/s/")) {
            val dl = Uri.parse(u).buildUpon().clearQuery().apply {
                Uri.parse(u).queryParameterNames.filter { it != "dl" }.forEach { k -> appendQueryParameter(k, Uri.parse(u).getQueryParameter(k)) }
                appendQueryParameter("dl", "1")
            }.build().toString()
            download(dl, "dbx" + stableHash(u), m.t, progress)?.let { return show(it, null) }
        }

        val y = AB.group("""(?:youtube\.com/watch\?v=|youtu\.be/)([\w-]{6,})""", u)
        if (y != null) return VState.Web("https://www.youtube-nocookie.com/embed/$y")
        return VState.Web(AB.absolute(u))
    }
}

fun shareFile(ctx: android.content.Context, f: File, view: Boolean) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"
    val i = if (view) Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
    else Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(Intent.createChooser(i, if (view) "Open with" else "Share file")) } catch (_: Exception) {}
}

@Composable
fun MaterialScreen(m: Material) {
    val ctx = LocalContext.current
    val savedList by Saved.saved.collectAsState()
    var state by remember(m.u) { mutableStateOf<VState>(VState.Resolving) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(m.u) {
        Saved.opened(m)
        state = Opener.open(m) { p -> state = VState.Downloading(p) }
    }
    val file = when (val s = state) { is VState.Pdf -> s.file; is VState.Img -> s.file; is VState.Card -> s.file; else -> null }

    var reporting by remember { mutableStateOf<ReportTarget?>(null) }
    reporting?.let { t -> ReportDialog(t) { reporting = null } }
    Screen(m.t, actions = {
        IconButton(onClick = { Saved.toggle(m) }) {
            val on = savedList.any { it.u == m.u }
            Icon(if (on) Icons.Filled.Star else Icons.Filled.StarBorder, if (on) "Remove from Saved" else "Save", tint = if (on) Brand.gold else MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (file != null) {
                DropdownMenuItem(text = { Text("Open with…") }, leadingIcon = { Icon(Icons.Filled.OpenInNew, null) }, onClick = { menu = false; shareFile(ctx, file, true) })
                DropdownMenuItem(text = { Text("Share file") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { menu = false; shareFile(ctx, file, false) })
            }
            DropdownMenuItem(text = { Text("Share link") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = {
                menu = false
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, AB.absolute(m.u)), "Share link"))
            })
            DropdownMenuItem(text = { Text("Open in Drive / browser") }, leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) }, onClick = { menu = false; openExternal(ctx, AB.absolute(m.u)) })
            DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                menu = false
                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("link", AB.absolute(m.u)))
            })
            DropdownMenuItem(text = { Text("Report this file") }, leadingIcon = { Icon(Icons.Filled.Flag, null) }, onClick = {
                menu = false; reporting = ReportTarget("file", m.u, m.t)
            })
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                VState.Resolving -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ABDots() }
                is VState.Downloading -> Centered {
                    KindTile(AB.kind(m.u, m.t), 64.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(m.t, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 3)
                    Spacer(Modifier.height(16.dp))
                    if (s.p > 0) LinearProgressIndicator(progress = { s.p }, modifier = Modifier.width(220.dp)) else LinearProgressIndicator(Modifier.width(220.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (s.p > 0) trf("Downloading %d%%", (s.p * 100).toInt()) else "Downloading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is VState.Folder -> DriveFolderList(s.id, m.t)
                is VState.Pdf -> PdfViewer(s.file)
                is VState.Img -> ZoomImage(s.file)
                is VState.Web -> WebContent(s.url, allowZoom = true)
                is VState.Card -> Centered {
                    KindTile(AB.kind(m.u, m.t), 72.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(m.t, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    s.file?.let { Text(formatBytes(it.length()), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(8.dp))
                    Text(s.why, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    s.file?.let { f -> Button(onClick = { shareFile(ctx, f, true) }, modifier = Modifier.width(260.dp)) { Text("Open with…") } }
                    s.preview?.let { p -> OutlinedButton(onClick = { state = VState.Web(p) }, modifier = Modifier.width(260.dp)) { Text("Preview in Google Drive") } }
                }
                is VState.Broken -> {
                    val nav = LocalNav.current
                    Centered {
                        Icon(Icons.Filled.Warning, null, tint = Brand.orange, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("This material isn't available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(trf("The owner removed it or made it private (%s). Ask for it and we'll upload it again.", s.why), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { nav.openPage("/requests/student_request_portal", "Request a material") }) { Text("Request this material") }
                    }
                }
            }
        }
    }
}

// MARK: native PDF reader (PdfRenderer), pinch to zoom

@Composable
fun PdfViewer(file: File) {
    val density = LocalDensity.current
    val renderer = remember(file) {
        try { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) } catch (e: Exception) { null }
    }
    DisposableEffect(renderer) { onDispose { try { renderer?.close() } catch (_: Exception) {} } }
    if (renderer == null) { Centered { Text("This PDF can't be opened.") }; return }
    val lock = remember { Mutex() }
    val ratios = remember(renderer) {
        (0 until renderer.pageCount).map { i -> renderer.openPage(i).use { p -> p.width.toFloat() / p.height.coerceAtLeast(1) } }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val list = rememberLazyListState()
    val page = list.firstVisibleItemIndex + 1

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).clipToBounds()
            .pointerInput(Unit) {
                // two fingers zoom and pan; one finger keeps scrolling the pages
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val e = awaitPointerEvent()
                        if (e.changes.count { it.pressed } > 1) {
                            val z = e.calculateZoom()
                            val pan = e.calculatePan()
                            scale = (scale * z).coerceIn(1f, 5f)
                            val maxX = (size.width * (scale - 1)) / 2
                            offset = if (scale == 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), 0f)
                            e.changes.forEach { it.consume() }
                        }
                    } while (e.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
        ) {
            items(ratios.size) { i ->
                var bmp by remember(i) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(i) {
                    bmp = withContext(Dispatchers.IO) {
                        lock.withLock {
                            try {
                                renderer.openPage(i).use { p ->
                                    val w = with(density) { 1400.dp.toPx() }.toInt().coerceAtMost(2000)
                                    val h = (w / ratios[i]).toInt().coerceAtLeast(1)
                                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                                        b.eraseColor(android.graphics.Color.WHITE)
                                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            } catch (e: Exception) { null }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(ratios[i]).shadow(2.dp, RoundedCornerShape(4.dp)).background(Color.White)) {
                    bmp?.let { Image(it.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ABDots(Modifier.size(24.dp)) }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { Text("$page / ${ratios.size}", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
    }
}

@Composable
fun ZoomImage(file: File) {
    val bmp = remember(file) { BitmapFactory.decodeFile(file.path) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    if (bmp == null) { Centered { Text("This image can't be shown.") }; return }
    Image(
        bmp.asImageBitmap(), null,
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val e = awaitPointerEvent()
                        scale = (scale * e.calculateZoom()).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + e.calculatePan()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        contentScale = ContentScale.Fit,
    )
}

// MARK: native Google Drive folder

data class Entry(val id: String, val url: String, val name: String, val type: String, val modified: String) {
    val isFolder get() = type.equals("folder", true) || url.contains("/folders/")
    val kind: Kind get() {
        if (isFolder) return Kind.FOLDER
        if (DriveLinks.target(id)?.second == true) return Kind.FOLDER
        val k = AB.kind(url, name)
        if (k != Kind.FILE) return k
        val t = type.lowercase()
        return when {
            t.contains("pdf") -> Kind.PDF
            t.contains("powerpoint") || t.contains("presentation") || t.contains("slides") -> Kind.PPT
            t.contains("word") || t.contains("docs") -> Kind.DOC
            t.contains("excel") || t.contains("spreadsheet") || t.contains("sheets") -> Kind.SHEET
            t.contains("image") -> Kind.IMG
            t.contains("video") -> Kind.VIDEO
            t.contains("zip") || t.contains("archive") || t.contains("compressed") -> Kind.ZIP
            else -> Kind.FILE
        }
    }
}

object Folders {
    private val titleRe = Regex("""flip-entry-title">([\s\S]*?)</div>""")
    private val hrefRe = Regex("""href="([^"]+)"""")
    private val labelRe = Regex("""aria-label="([^"]*)"""")
    private val typeRe = Regex("""/type/([^"]+)"""")
    private val altRe = Regex("""alt="([^"]+)"""")
    private val modRe = Regex("""flip-entry-last-modified"><div>([^<]*)""")

    fun decode(s: String): String {
        var r = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        r = Regex("&#(\\d+);").replace(r) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        return r
    }

    suspend fun load(id: String): List<Entry>? {
        val html = Net.text("https://drive.google.com/embeddedfolderview?id=$id") ?: return null
        if (html.contains("accounts.google.com/ServiceLogin")) return null
        // each entry on its own: files with a thumbnail have no aria-label, so the type comes from the
        // label, the file-type icon URL or the image alt
        return html.split("class=\"flip-entry\" id=\"entry-").drop(1).mapNotNull { chunk ->
            fun first(re: Regex) = re.find(chunk)?.groupValues?.get(1)?.let { decode(it) }
            val name = first(titleRe) ?: return@mapNotNull null
            val id = chunk.substringBefore('"')
            Entry(
                id, first(hrefRe) ?: "https://drive.google.com/file/d/$id/view", name.trim(),
                first(labelRe) ?: first(typeRe) ?: first(altRe) ?: "", first(modRe) ?: "",
            )
        }.sortedWith(compareBy<Entry>({ !it.isFolder }, { it.name.lowercase() }))
    }
}

@Composable
fun DriveFolderList(id: String, title: String) {
    var entries by remember(id) { mutableStateOf<List<Entry>?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(id, retry) {
        failed = false
        val r = Folders.load(id)
        if (r == null) failed = true else entries = r
    }
    val nq = AB.norm(q)
    LazyColumn(Modifier.fillMaxSize()) {
        when {
            failed && entries == null -> item {
                Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.FolderOff, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Couldn't open this folder", style = MaterialTheme.typography.titleMedium)
                    Text("Check your connection, or the folder may be private.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Button(onClick = { retry++ }) { Text("Try again") }
                }
            }
            entries == null -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { ABDots() } }
            entries!!.isEmpty() -> item { EmptyState(Icons.Filled.FolderOff, "Empty folder", "Nothing has been uploaded here yet.") }
            else -> {
                val all = entries!!
                if (all.size > 8) item {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Search in this folder") },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item {
                    val shown = all.filter { nq.isEmpty() || AB.norm(it.name).contains(nq) }
                    Group(header = trf("%d items", all.size)) {
                        shown.forEachIndexed { i, e ->
                            if (i > 0) Divider()
                            MaterialRow(Material(u = e.url, t = e.name, s = title), e.kind, meta = e.modified.ifEmpty { null }, showSource = false)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// MARK: web pages that need the web (Google forms, uploads...)

@Composable
fun WebScreen(title: String, url: String, allowZoom: Boolean = false) {
    Screen(title.ifEmpty { "UniHub" }) { pad -> Box(Modifier.padding(pad)) { WebContent(url, allowZoom) } }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebContent(url: String, allowZoom: Boolean) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val pages by Pages.pages.collectAsState()
    var progress by remember { mutableFloatStateOf(0f) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray()); fileCallback = null
    }
    val start = AB.sitePath(url)
    BackHandler(enabled = canBack) { web?.goBack() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportZoom(allowZoom)
                    settings.builtInZoomControls = allowZoom
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = settings.userAgentString + " ASUBankApp/android"
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                            val u = req.url.toString()
                            val scheme = req.url.scheme?.lowercase() ?: ""
                            if (scheme != "http" && scheme != "https") { openExternal(ctx, u); return true }
                            val host = req.url.host?.lowercase() ?: ""
                            if (host.contains("whatsapp.com") || host == "wa.me" || host.contains("paypal.") || host == "t.me") { openExternal(ctx, u); return true }
                            // links to other asubank.org pages open their native screens
                            val p = AB.sitePath(u)
                            if (req.isForMainFrame && req.hasGesture() && p != null && p != start && pages[p]?.web != 1) {
                                openLink(nav, ctx, u, "")
                                return true
                            }
                            return false
                        }
                        override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                            view.evaluateJavascript("document.documentElement.setAttribute('data-ab-notab','');document.documentElement.setAttribute('data-ab-app','android');", null)
                        }
                        override fun onPageFinished(view: WebView, u: String?) {
                            canBack = view.canGoBack()
                            view.evaluateJavascript("document.documentElement.setAttribute('data-ab-notab','');document.documentElement.setAttribute('data-ab-app','android');", null)
                            if (!allowZoom) view.evaluateJavascript(
                                "var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                                    "m.content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';", null,
                            )
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, p: Int) { progress = p / 100f }
                        // window.open() and target=_blank: open in this same view
                        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                            val tmp = WebView(view.context)
                            tmp.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                    view.loadUrl(r.url.toString()); tmp.destroy(); return true
                                }
                            }
                            (resultMsg.obj as WebView.WebViewTransport).webView = tmp
                            resultMsg.sendToTarget()
                            return true
                        }
                        override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                            fileCallback?.onReceiveValue(null)
                            fileCallback = cb
                            chooser.launch(params.acceptTypes.firstOrNull { it.isNotBlank() } ?: "*/*")
                            return true
                        }
                    }
                    loadUrl(url)
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
        if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
    }
}
