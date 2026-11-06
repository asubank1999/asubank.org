package org.unihub.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.system.exitProcess

// 1.14.0: disappearing items, voice messages, photos and videos in chats, work that carries on in the background, and the loading effects.

object Disappear {
    val choices = listOf(0 to "Don't disappear", 60 to "1 minute", 3600 to "60 minutes", 86400 to "1 day", 604800 to "1 week")

    private fun instant(iso: String?): Instant? = if (iso.isNullOrEmpty()) null else runCatching { Instant.parse(iso) }.getOrNull()

    fun expired(iso: String?): Boolean = instant(iso)?.isBefore(Instant.now()) ?: false

    /** "5m", "3h", "2d" */
    fun left(iso: String?): String? {
        val at = instant(iso) ?: return null
        val s = maxOf(0L, at.epochSecond - Instant.now().epochSecond)
        return when {
            s < 60 -> "${maxOf(1, s)}s"
            s < 3600 -> "${s / 60}m"
            s < 86400 -> "${s / 3600}h"
            else -> "${s / 86400}d"
        }
    }
}

/** The small timer button next to a message box or in a post */
@Composable
fun DisappearMenu(seconds: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (seconds > 0) accent.copy(alpha = 0.16f) else Color.Transparent).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Timer, tr("Disappears after"), tint = accent, modifier = Modifier.size(22.dp)) }
        DropdownMenu(open, { open = false }) {
            Text("Disappears after", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            Disappear.choices.forEach { (s, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { if (seconds == s) Icon(Icons.Filled.Check, null) },
                    onClick = { open = false; onChange(s) },
                )
            }
        }
    }
}

@Composable
fun ExpiryTag(expires: String?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val left = Disappear.left(expires) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Icons.Filled.Timer, null, Modifier.size(11.dp), tint = tint)
        androidx.compose.material3.Text(left, fontSize = 10.sp, color = tint)
    }
}

// MARK: - files

object MediaFiles {
    /** A voice message, photo or video from a room or post, kept on the phone after the first time */
    suspend fun local(ctx: Context, scope: String, id: String, name: String): File = withContext(Dispatchers.IO) {
        val safe = name.ifEmpty { "file" }.replace(Regex("[/\\\\:]"), "_")
        val dir = File(DmFiles.folder(ctx), id)
        val file = File(dir, safe)
        if (file.length() > 0) return@withContext file
        val data = Account.call("mediaFile", mapOf("scope" to scope, "id" to id)).str("data") ?: throw ApiError(tr("File not found."))
        dir.mkdirs()
        file.writeBytes(Base64.decode(data, Base64.DEFAULT))
        file
    }
}

/** Something on its way to a chat. `load` does the slow part, so the message shows at once. */
class ChatOutgoing(
    val type: String,
    val fileName: String,
    val duration: Int = 0,
    val size: Long = 0,
    val local: String? = null,
    val load: suspend () -> String?,
)

// MARK: - recording

class VoiceRecorder(private val ctx: Context) {
    var recording by mutableStateOf(false)
        private set
    var seconds by mutableIntStateOf(0)
    var level by mutableFloatStateOf(0f)
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean = try {
        val f = File(DmFiles.folder(ctx).apply { mkdirs() }, "voice-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(22050)
        r.setAudioEncodingBitRate(32000)
        r.setAudioChannels(1)
        r.setOutputFile(f.path)
        r.prepare()
        r.start()
        rec = r
        file = f
        startedAt = System.currentTimeMillis()
        seconds = 0
        recording = true
        true
    } catch (_: Exception) { rec = null; false }

    /** Called about ten times a second while recording */
    fun tick() {
        val r = rec ?: return
        seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        level = (runCatching { r.maxAmplitude }.getOrDefault(0) / 12000f).coerceIn(0f, 1f)
    }

    fun stop(): Pair<File, Int>? {
        val r = rec ?: return null
        val f = file
        val length = maxOf(1, ((System.currentTimeMillis() - startedAt) / 1000).toInt())
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        rec = null
        recording = false
        return if (ok && f != null && f.length() > 0) f to length else null
    }

    fun cancel() {
        val f = file
        val r = rec
        rec = null
        recording = false
        if (r != null) { runCatching { r.stop() }; runCatching { r.release() } }
        f?.delete()
    }

    companion object { const val MAX_SECONDS = 180 }
}

@Composable
fun rememberVoiceRecorder(onLimit: (File, Int) -> Unit = { _, _ -> }): VoiceRecorder {
    val ctx = LocalContext.current
    val rec = remember { VoiceRecorder(ctx) }
    DisposableEffect(Unit) { onDispose { rec.cancel() } }
    LaunchedEffect(rec.recording) {
        while (rec.recording) {
            delay(100)
            rec.tick()
            if (rec.seconds >= VoiceRecorder.MAX_SECONDS) { rec.stop()?.let { onLimit(it.first, it.second) } }
        }
    }
    return rec
}

fun hasMic(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

// MARK: - playing

private object VoiceNow { var stop: (() -> Unit)? = null }

fun voiceTime(s: Int) = "%d:%02d".format(s / 60, s % 60)

/** A voice message: play button, bars that fill while it plays, and how long it is */
@Composable
fun VoiceBubble(id: String, duration: Int, mine: Boolean = false, local: String? = null, fetch: suspend () -> File) {
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else accent
    val bars = remember(id) {
        var seed = id.hashCode()
        List(26) { seed = seed * 1664525 + 1013904223; 6 + ((seed ushr 28) and 15) }
    }

    fun halt() { runCatching { player?.pause() }; playing = false }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() }; player = null; playing = false } }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player ?: break
            progress = runCatching { if (p.duration > 0) p.currentPosition.toFloat() / p.duration else 0f }.getOrDefault(0f)
            delay(80)
        }
    }

    fun tap() {
        if (playing) { halt(); return }
        scope.launch {
            loading = true
            try {
                val p = player ?: withContext(Dispatchers.IO) {
                    val path = local?.let { Uri.parse(it).path ?: it } ?: fetch().path
                    MediaPlayer().apply { setDataSource(path); prepare() }
                }.also { m ->
                    m.setOnCompletionListener { playing = false; progress = 0f; runCatching { it.seekTo(0) } }
                    player = m
                }
                VoiceNow.stop?.invoke()
                VoiceNow.stop = { halt() }
                p.start()
                playing = true
                failed = false
            } catch (_: Exception) { failed = true }
            loading = false
        }
    }

    Row(Modifier.widthIn(min = 200.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (mine) Color.White.copy(alpha = 0.22f) else accent.copy(alpha = 0.14f)).clickable { tap() },
            contentAlignment = Alignment.Center,
        ) {
            if (loading) ABDots(Modifier.size(24.dp), color = fg)
            else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, tr(if (playing) "Pause" else "Play voice message"), tint = fg)
        }
        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            bars.forEachIndexed { i, h ->
                Box(
                    Modifier.width(2.5.dp).height(h.dp).clip(RoundedCornerShape(2.dp))
                        .background(fg.copy(alpha = if (progress > 0f && i.toFloat() / bars.size <= progress) 1f else 0.4f)),
                )
            }
        }
        androidx.compose.material3.Text(if (failed) "!" else voiceTime(duration), fontSize = 11.sp, color = if (mine) fg.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A photo loads with the chat. A video comes down only when it's tapped, so a chat doesn't use up mobile data. */
@Composable
fun ChatMediaTile(type: String, local: String? = null, fetch: suspend () -> File) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(local) { mutableStateOf<File?>(null) }
    var poster by remember(local) { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val video = type == "video"
    LaunchedEffect(local) {
        if (video) {
            if (local != null) poster = withContext(Dispatchers.IO) { StoryVideo.poster(ctx, Uri.parse(local)) }
        } else if (local == null) {
            runCatching { file = fetch() }.onFailure { failed = true }
        }
    }
    Box(
        Modifier.width(220.dp).height(if (video) 280.dp else 220.dp).clip(RoundedCornerShape(12.dp))
            .background(if (video) Color.Black.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !loading) {
                val f = file
                if (!video) { if (f != null) PhotoPeek.show(Uri.fromFile(f).toString()) else local?.let { PhotoPeek.show(it) } }
                else if (f != null) runCatching { shareFile(ctx, f, view = true) }
                else if (local == null) scope.launch {
                    loading = true
                    try { val got = fetch(); file = got; shareFile(ctx, got, view = true) } catch (_: Exception) { failed = true }
                    loading = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val shown: Any? = file?.takeIf { !video } ?: local?.takeIf { !video }
        when {
            shown != null -> AsyncImage(shown, tr("View photo"), Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            poster != null -> androidx.compose.foundation.Image(poster!!.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            failed -> Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            !video -> ABDots(color = accent)
        }
        if (video) {
            if (loading) ABDots(color = Color.White)
            else Icon(Icons.Filled.PlayCircle, tr("Play video"), Modifier.size(52.dp), tint = Color.White.copy(alpha = 0.92f))
        }
    }
}

/** What a message carries */
@Composable
fun ChatAttachment(id: String, type: String, duration: Int, mine: Boolean, local: String?, fetch: suspend () -> File) {
    if (type == "voice") VoiceBubble(id, duration, mine, local, fetch) else ChatMediaTile(type, local, fetch)
}

// MARK: - the message box with attachments and voice

@Composable
fun ChatInputBar(
    text: String, onText: (String) -> Unit, placeholder: String = "Message", sendIcon: ImageVector = Icons.AutoMirrored.Filled.Send,
    allowDocs: Boolean = false, editing: Boolean = false, disappear: Int, onDisappear: (Int) -> Unit,
    sendMedia: (ChatOutgoing) -> Unit, fail: (String) -> Unit, send: () -> Unit,
) {
    val ctx = LocalContext.current
    var attach by remember { mutableStateOf(false) }

    fun deliver(f: File, length: Int) {
        sendMedia(ChatOutgoing("voice", "voice.m4a", length, f.length(), Uri.fromFile(f).toString()) {
            withContext(Dispatchers.IO) { Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) }
        })
    }
    val rec = rememberVoiceRecorder { f, l -> deliver(f, l) }
    fun startRecording() { if (!rec.start()) fail(tr("Couldn't start the recording.")) }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else fail(tr("UniHub can't use the microphone. You can allow it in Settings."))
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendMedia(ChatOutgoing("photo", "photo.jpg", local = uri.toString()) {
            PhotoTools.jpeg(ctx, uri, 1800) ?: throw ApiError(tr("Couldn't load this photo"))
        })
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendMedia(ChatOutgoing("video", "video.mp4", local = uri.toString()) {
            StoryVideo.base64(ctx, uri) ?: throw ApiError(tr("That video is too big. Keep it short."))
        })
    }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = DmFiles.displayName(ctx, uri)
            sendMedia(ChatOutgoing(if (name.lowercase().endsWith(".pdf")) "pdf" else "docx", name) {
                val bytes = withContext(Dispatchers.IO) { StoryVideo.readLimited(ctx, uri, DmFiles.MAX_BYTES) }
                    ?: throw ApiError(tr("That file is too big. The limit is 8 MB."))
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            })
        }
    }

    if (rec.recording) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).clickable { rec.cancel() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Delete, tr("Cancel"), tint = MaterialTheme.colorScheme.error)
            }
            Box(Modifier.size(10.dp).graphicsLayer { scaleX = 0.8f + rec.level * 0.8f; scaleY = scaleX }.clip(CircleShape).background(Color.Red))
            androidx.compose.material3.Text(voiceTime(rec.seconds), fontWeight = FontWeight.SemiBold)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                repeat(16) { i ->
                    Box(Modifier.width(3.dp).height((4f + rec.level * (6 + (i * 7) % 18)).dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.75f)))
                }
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(accent).clickable { rec.stop()?.let { deliver(it.first, it.second) } },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, tr("Send"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
        }
        return
    }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!editing) {
            Box {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)).clickable { attach = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, tr("Attach a file"), tint = accent) }
                DropdownMenu(attach, { attach = false }) {
                    DropdownMenuItem(text = { Text("Photo") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = { attach = false; pickPhoto.launch("image/*") })
                    DropdownMenuItem(text = { Text("Video") }, leadingIcon = { Icon(Icons.Filled.Videocam, null) }, onClick = { attach = false; pickVideo.launch("video/*") })
                    if (allowDocs) DropdownMenuItem(text = { Text("PDF or Word file") }, leadingIcon = { Icon(Icons.Filled.Description, null) }, onClick = {
                        attach = false
                        pickDoc.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    })
                }
            }
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) { DisappearMenu(disappear, onDisappear) }
        }
        BasicTextField(
            text, onText, Modifier.weight(1f), maxLines = 5,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    if (text.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    inner()
                }
            },
        )
        val empty = text.isBlank()
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (empty && editing) accent.copy(alpha = 0.4f) else accent)
                .clickable(enabled = !empty || !editing) {
                    if (!empty) send()
                    else if (hasMic(ctx)) startRecording()
                    else askMic.launch(Manifest.permission.RECORD_AUDIO)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (empty && !editing) Icon(Icons.Filled.Mic, tr("Record a voice message"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            else Icon(sendIcon, tr("Send"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

// MARK: - background work

/** Posting keeps going while the student does something else. A strip at the top shows it, and offers a retry if it failed. */
object BackgroundJobs {
    class Job(val id: String, val title: String, val failed: String? = null, val success: Boolean = false, val run: suspend () -> Unit, val done: () -> Unit)

    /** Outlives every screen, so leaving one doesn't stop its upload */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val jobs = MutableStateFlow<List<Job>>(emptyList())

    fun add(title: String, done: () -> Unit = {}, run: suspend () -> Unit) {
        val j = Job(UUID.randomUUID().toString(), title, run = run, done = done)
        jobs.value = jobs.value + j
        start(j)
    }

    private fun change(id: String, f: (Job) -> Job) { jobs.value = jobs.value.map { if (it.id == id) f(it) else it } }

    fun dismiss(id: String) { jobs.value = jobs.value.filterNot { it.id == id } }

    fun retry(j: Job) {
        change(j.id) { Job(it.id, it.title, run = it.run, done = it.done) }
        start(j)
    }

    private fun start(j: Job) {
        scope.launch {
            try {
                j.run()
                change(j.id) { Job(it.id, it.title, success = true, run = it.run, done = it.done) }
                j.done()
                delay(1200)
                dismiss(j.id)
            } catch (e: Exception) {
                change(j.id) { Job(it.id, it.title, failed = e.message ?: tr("Something went wrong."), run = it.run, done = it.done) }
            }
        }
    }
}

@Composable
fun JobsBanner(modifier: Modifier = Modifier) {
    val jobs by BackgroundJobs.jobs.collectAsState()
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        jobs.forEach { j ->
            key(j.id) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                AnimatedVisibility(shown, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 6.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            when {
                                j.success -> Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E9E4F), modifier = Modifier.size(20.dp))
                                j.failed != null -> Icon(Icons.Filled.Warning, null, tint = Color(0xFFEF8A17), modifier = Modifier.size(20.dp))
                                else -> ABDots(Modifier.size(30.dp, 14.dp), color = accent)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(if (j.success) "Done" else j.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                j.failed?.let { androidx.compose.material3.Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                            }
                            if (j.failed != null) {
                                TextButton(onClick = { BackgroundJobs.retry(j) }) { Text("Try again") }
                                Icon(Icons.Filled.Close, tr("Close"), Modifier.size(20.dp).clickable { BackgroundJobs.dismiss(j.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - loading effects

/** Three dots in the accent color, used wherever something is on its way */
@Composable
fun ABDots(modifier: Modifier = Modifier, color: Color = accent, strokeWidth: Dp = 0.dp) {
    val move = rememberInfiniteTransition(label = "dots")
    val t by move.animateFloat(0f, 1f, infiniteRepeatable(tween(1050, easing = LinearEasing)), label = "dots")
    Canvas(modifier.defaultMinSize(44.dp, 16.dp)) {
        val r = minOf(size.width / 8f, size.height / 2f)
        val gap = r * 2.7f
        for (i in 0..2) {
            val phase = ((t - i * 0.16f) % 1f + 1f) % 1f
            val wave = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
            drawCircle(color.copy(alpha = 0.3f + 0.7f * wave), r * (0.5f + 0.5f * wave), Offset(size.width / 2f + (i - 1) * gap, size.height / 2f))
        }
    }
}

fun Modifier.shimmer(): Modifier = composed {
    val move = rememberInfiniteTransition(label = "shimmer")
    val x by move.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shimmer")
    // SrcAtop in its own layer: the shine shows only on the grey shapes, not as a band across the screen
    graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(x * w, 0f), Offset(x * w + w * 0.6f, 0f)),
            blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
        )
    }
}

/** Grey shapes in the form of what is coming, so the screen feels ready straight away */
@Composable
fun SkeletonPost() {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    Group {
        Column(Modifier.fillMaxWidth().shimmer().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(fill))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(120.dp).height(10.dp).clip(CircleShape).background(fill))
                    Box(Modifier.width(70.dp).height(8.dp).clip(CircleShape).background(fill))
                }
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(fill))
            Box(Modifier.width(200.dp).height(10.dp).clip(CircleShape).background(fill))
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)).background(fill))
        }
    }
}

/** Profile numbers, buttons and about card while the profile loads */
@Composable
fun ProfileSkeleton() {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    Column(Modifier.fillMaxWidth().shimmer(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Box(Modifier.width(64.dp).height(18.dp).clip(CircleShape).background(fill)) } }
        Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(fill))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(fill)) } }
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(fill))
    }
}

@Composable
fun SkeletonRow() {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    Row(Modifier.fillMaxWidth().shimmer().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(fill))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(140.dp).height(11.dp).clip(CircleShape).background(fill))
            Box(Modifier.width(200.dp).height(9.dp).clip(CircleShape).background(fill))
        }
    }
}

@Composable
fun SkeletonBubbles(modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    Column(modifier.fillMaxWidth().shimmer().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(170 to false, 120 to false, 210 to true, 140 to false, 90 to true, 190 to false, 150 to true).forEach { (w, right) ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(Modifier.width(w.dp).height(38.dp).clip(RoundedCornerShape(18.dp)).background(fill))
            }
        }
    }
}

// MARK: - share the app, and restart after a theme change

fun shareApp(ctx: Context) {
    val text = tr("Summaries, past papers and study tools for university students. Get UniHub:") + " " + AppConfig.shareUrl.value
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { ctx.startActivity(Intent.createChooser(i, tr("Share UniHub"))) }
}

object AppRestart {
    /** Opens the app again from the start, so the new colors load cleanly */
    fun now(ctx: Context) {
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                app.startActivity(it)
            }
            exitProcess(0)
        }, 250)
    }
}
