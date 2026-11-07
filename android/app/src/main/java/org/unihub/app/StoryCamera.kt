package org.unihub.app

import android.widget.FrameLayout
import android.view.Gravity
import android.view.ViewGroup
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Range
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs

// The story camera: photos and short videos, at 60 frames per second when the phone's camera can do it.

sealed class StoryMedia {
    data class Photo(val uri: Uri) : StoryMedia()
    data class Video(val uri: Uri) : StoryMedia()
}

class StoryDraft(val media: StoryMedia?)

object StoryVideo {
    const val MAX_SECONDS = 30
    const val MAX_BYTES = 12_000_000

    private fun folder(ctx: Context) = File(ctx.cacheDir, "stories").apply { mkdirs() }

    fun newFile(ctx: Context, ext: String) = File(folder(ctx), "story-${System.currentTimeMillis()}.$ext")

    /** The first frame, shown while the video loads */
    fun poster(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val frame = r.getFrameAtTime(0) ?: return@runCatching null
            val scale = minOf(1f, 900f / maxOf(frame.width, frame.height))
            if (scale >= 1f) frame else Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
        } finally { r.release() }
    }.getOrNull()

    /** The video's bytes as base64, or null when it's over the limit or can't be read */
    suspend fun base64(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        readLimited(ctx, uri, MAX_BYTES)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    fun readLimited(ctx: Context, uri: Uri, limit: Int): ByteArray? = runCatching {
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > limit) return@use null
            }
            out.toByteArray()
        }
    }.getOrNull()

    /** A story's video on this phone, so it plays smoothly. Null when it can't be fetched. */
    suspend fun local(ctx: Context, url: String): File? = withContext(Dispatchers.IO) {
        val file = File(folder(ctx), "seen-" + url.hashCode() + ".mp4")
        if (file.length() > 0) return@withContext file
        runCatching {
            Net.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body ?: return@use null
                val tmp = File(file.path + ".part")
                tmp.outputStream().use { body.byteStream().copyTo(it) }
                val head = ByteArray(8)
                tmp.inputStream().use { it.read(head) }
                if (String(head, 4, 4, Charsets.ISO_8859_1) != "ftyp") { tmp.delete(); return@use null }
                tmp.renameTo(file)
                file
            }
        }.getOrNull()
    }
}

private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

/** done(null) means a text story */
@SuppressLint("MissingPermission")
@Composable
fun StoryCameraDialog(onClose: () -> Unit, done: (StoryMedia?) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    var granted by remember { mutableStateOf(has(ctx, Manifest.permission.CAMERA)) }
    var asked by remember { mutableStateOf(false) }
    var videoMode by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(false) }
    var sixty by remember { mutableStateOf(false) }
    var recordingNow by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r[Manifest.permission.CAMERA] == true
        asked = true
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (ctx.contentResolver.getType(uri)?.startsWith("video") == true) {
                val size = runCatching { ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: 0L
                if (size > StoryVideo.MAX_BYTES) Toast.makeText(ctx, tr("That video is too big. Keep it short."), Toast.LENGTH_LONG).show()
                else done(StoryMedia.Video(uri))
            } else done(StoryMedia.Photo(uri))
        }
    }
    LaunchedEffect(Unit) {
        val need = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter { !has(ctx, it) }
        if (need.isNotEmpty()) ask.launch(need.toTypedArray())
    }

    LaunchedEffect(granted, videoMode, front) {
        if (!granted) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(ctx)
        val p = suspendCancellableCoroutine<ProcessCameraProvider> { c -> future.addListener({ c.resume(future.get()) }, main) }
        provider = p
        p.unbindAll()
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        imageCapture = null
        videoCapture = null
        sixty = false
        runCatching {
            if (!videoMode) {
                val photo = ImageCapture.Builder().build()
                p.bindToLifecycle(owner, selector, preview, photo)
                imageCapture = photo
            } else {
                val ranges = selector.filter(p.availableCameraInfos).firstOrNull()?.supportedFrameRateRanges.orEmpty()
                val best = ranges.firstOrNull { it.lower == 60 && it.upper == 60 } ?: ranges.firstOrNull { it.upper == 60 }
                fun build(range: Range<Int>?): VideoCapture<Recorder> {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                        .setTargetVideoEncodingBitRate(2_800_000)
                        .build()
                    val b = VideoCapture.Builder(recorder)
                    if (range != null) b.setTargetFrameRate(range)
                    return b.build()
                }
                // some phones list 60 frames per second but can't do it together with the preview
                val fast = if (best != null) runCatching {
                    val v = build(best)
                    p.bindToLifecycle(owner, selector, preview, v)
                    v
                }.getOrNull() else null
                if (fast != null) {
                    videoCapture = fast
                    sixty = true
                } else {
                    p.unbindAll()
                    val v = build(null)
                    p.bindToLifecycle(owner, selector, preview, v)
                    videoCapture = v
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { recording?.stop() }; provider?.unbindAll() } }

    fun takePhoto() {
        val c = imageCapture ?: return
        busy = true
        val file = StoryVideo.newFile(ctx, "jpg")
        c.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), main, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) { busy = false; done(StoryMedia.Photo(Uri.fromFile(file))) }
            override fun onError(e: ImageCaptureException) {
                busy = false
                Toast.makeText(ctx, tr("Couldn't take the photo. Try again."), Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun toggleRecording() {
        recording?.let { it.stop(); recording = null; return }
        val c = videoCapture ?: return
        val file = StoryVideo.newFile(ctx, "mp4")
        val options = FileOutputOptions.Builder(file)
            .setDurationLimitMillis(StoryVideo.MAX_SECONDS * 1000L)
            .setFileSizeLimit(StoryVideo.MAX_BYTES.toLong())
            .build()
        var pending = c.output.prepareRecording(ctx, options)
        if (has(ctx, Manifest.permission.RECORD_AUDIO)) pending = pending.withAudioEnabled()
        seconds = 0
        recordingNow = true
        recording = pending.start(main) { e ->
            when (e) {
                is VideoRecordEvent.Status -> seconds = (e.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                is VideoRecordEvent.Finalize -> {
                    recordingNow = false
                    recording = null
                    val fine = e.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                        e.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                        e.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                    if (fine && file.length() > 0) done(StoryMedia.Video(Uri.fromFile(file)))
                    else Toast.makeText(ctx, tr("Couldn't record the video. Try again."), Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var drag by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.fillMaxSize().background(Color.Black).pointerInput(recordingNow) {
                if (recordingNow) return@pointerInput
                detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (abs(drag) > 60.dp.toPx()) videoMode = !videoMode }) { c, d ->
                    c.consume()
                    drag += d
                }
            },
        ) {
            if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
            else if (asked) Column(
                Modifier.align(Alignment.Center).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("The camera is off", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("You can allow it in Settings, or pick from your photos.", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close"), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (recordingNow) Text(
                    "%d:%02d / 0:%02d".format(seconds / 60, seconds % 60, StoryVideo.MAX_SECONDS), color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFD32F2F)).padding(horizontal = 10.dp, vertical = 4.dp),
                ) else if (videoMode && sixty) Text(
                    "60 FPS", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.gold).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { front = !front }, enabled = !recordingNow) { Icon(Icons.Filled.Cameraswitch, tr("Switch camera"), tint = Color.White) }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(false to "Photo", true to "Video").forEach { (v, label) ->
                        Text(
                            label, color = if (videoMode == v) Color.Black else Color.White, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (videoMode == v) Color.White else Color.Black.copy(alpha = 0.4f))
                                .clickable(enabled = !recordingNow) { videoMode = v }.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        RoundCameraButton(enabled = !recordingNow, onClick = {
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }) { Icon(Icons.Filled.PhotoLibrary, tr("Pick from your photos"), tint = Color.White) }
                    }
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape)
                            .clickable(enabled = granted && !busy) { if (videoMode) toggleRecording() else takePhoto() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) ABDots(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
                        else Box(
                            Modifier.size(if (recordingNow) 30.dp else 60.dp)
                                .clip(if (recordingNow) RoundedCornerShape(6.dp) else CircleShape)
                                .background(if (videoMode) Color(0xFFD32F2F) else Color.White),
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        RoundCameraButton(enabled = !recordingNow, onClick = { done(null) }) { Icon(Icons.Filled.TextFields, tr("Text story"), tint = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundCameraButton(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A story's video, centered. onLength gets its length in seconds, or the usual story length when it can't play.
 *  onPlayed gets how far it has played and onEnd fires when it has ended, so the progress bar follows the real video. */
@Composable
fun StoryVideoPlayer(url: String, paused: Boolean, onPlayed: (Double) -> Unit = {}, onEnd: () -> Unit = {}, onLength: (Double) -> Unit) {
    val ctx = LocalContext.current
    var file by remember(url) { mutableStateOf<File?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var prepared by remember(url) { mutableStateOf(false) }
    var view by remember(url) { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(view, prepared) {
        val v = view ?: return@LaunchedEffect
        while (prepared) {
            if (v.isPlaying) onPlayed(v.currentPosition / 1000.0)
            kotlinx.coroutines.delay(50)
        }
    }
    LaunchedEffect(url) {
        file = StoryVideo.local(ctx, url)
        if (file == null) {
            failed = true
            onLength(6.0)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val f = file
        if (f == null) {
            if (!failed) ABDots(color = Color.White)
        } else key(f.path) {
            AndroidView(
                factory = { c ->
                    // VideoView keeps the video's shape inside what it's given. Given the whole screen it stays as big as it fits;
                    // left to wrap its content it fell back to the video's small stored size.
                    FrameLayout(c).apply {
                        addView(
                            VideoView(c).apply {
                                view = this
                                setOnCompletionListener { onEnd() }
                                setOnPreparedListener { mp ->
                                    prepared = true
                                    onLength((mp.duration / 1000.0).coerceAtLeast(1.0))
                                    start()
                                }
                                setOnErrorListener { _, _, _ ->
                                    onLength(6.0)
                                    true
                                }
                                setVideoPath(f.path)
                            },
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER),
                        )
                    }
                },
                update = { root ->
                    val v = root.getChildAt(0) as VideoView
                    if (prepared) {
                        if (paused && v.isPlaying) v.pause()
                        else if (!paused && !v.isPlaying) v.start()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
