package org.unihub.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import kotlin.coroutines.resume

/**
 * Saves a story's photo or video to the phone with a UniHub watermark (logo, name and the owner's @username)
 * in the corner, like TikTok, so a saved story always shows where it came from.
 */
object StorySave {
    class Failure(message: String) : Exception(message)

    /** The watermark, [width] pixels wide: logo, "UniHub" and @username, white with a soft shadow */
    fun watermark(ctx: Context, width: Int, username: String): Bitmap {
        val logoSide = width * 0.26f
        val height = (logoSide * 1.1f).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val shadow = logoSide * 0.12f
        BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)?.let { logo ->
            val top = (height - logoSide) / 2f
            val rect = RectF(0f, top, logoSide, top + logoSide)
            c.save()
            c.clipPath(Path().apply { addRoundRect(rect, logoSide * 0.22f, logoSide * 0.22f, Path.Direction.CW) })
            c.drawBitmap(logo, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(242, 255, 255, 255); textSize = logoSide * 0.46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); setShadowLayer(shadow, 0f, logoSide * 0.03f, Color.argb(140, 0, 0, 0))
        }
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255); textSize = logoSide * 0.3f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); setShadowLayer(shadow, 0f, logoSide * 0.03f, Color.argb(140, 0, 0, 0))
        }
        val x = logoSide * 1.18f
        c.drawText("UniHub", x, height / 2f - logoSide * 0.06f, title)
        c.drawText("@$username", x, height / 2f + logoSide * 0.34f, name)
        return bmp
    }

    private fun insert(ctx: Context, video: Boolean, write: (java.io.OutputStream) -> Unit) {
        val name = "UniHub-story-" + System.currentTimeMillis() + if (video) ".mp4" else ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/UniHub")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), "UniHub").apply { mkdirs() }
                @Suppress("DEPRECATION") put(MediaStore.MediaColumns.DATA, File(dir, name).path)
            }
        }
        val collection: Uri = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = ctx.contentResolver.insert(collection, values) ?: throw Failure(tr("Couldn't save the story. Try again."))
        ctx.contentResolver.openOutputStream(uri)?.use(write) ?: throw Failure(tr("Couldn't save the story. Try again."))
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }

    /** A photo story, stamped in its bottom right corner */
    suspend fun savePhoto(ctx: Context, url: String, username: String) = withContext(Dispatchers.IO) {
        val bytes = runCatching { Net.http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() } }.getOrNull()
        val photo = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: throw Failure(tr("Couldn't download the story. Try again."))
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val mark = watermark(ctx, maxOf(160, (out.width * 0.36f).toInt()), username)
        val pad = out.width * 0.04f
        Canvas(out).drawBitmap(mark, out.width - mark.width - pad, out.height - mark.height - pad, null)
        insert(ctx, false) { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    /** A video story, stamped on every frame in its bottom right corner */
    @OptIn(UnstableApi::class)
    suspend fun saveVideo(ctx: Context, url: String, username: String) {
        val file = StoryVideo.local(ctx, url) ?: throw Failure(tr("Couldn't download the story. Try again."))
        val width = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.path)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
                    val turned = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0) % 180 != 0
                    if (turned) h else w
                } finally { r.release() }
            }.getOrDefault(720)
        }
        val mark = watermark(ctx, maxOf(160, (width * 0.36f).toInt()), username)
        // anchors count from the middle: (1, -1) is the bottom right corner
        val settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(0.92f, -0.94f)
            .setOverlayFrameAnchor(1f, -1f)
            .build()
        val overlay = BitmapOverlay.createStaticBitmapOverlay(mark, settings)
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
            .setEffects(Effects(ImmutableList.of(), ImmutableList.of(OverlayEffect(ImmutableList.of(overlay)))))
            .build()
        val out = File(ctx.cacheDir, "stories/saved-" + System.currentTimeMillis() + ".mp4")
        out.parentFile?.mkdirs()
        // Transformer runs on the main thread and calls back there
        val ok = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val t = Transformer.Builder(ctx)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(true) }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (cont.isActive) cont.resume(false) }
                    })
                    .build()
                t.start(item, out.path)
                cont.invokeOnCancellation { runCatching { t.cancel() } }
            }
        }
        if (!ok) { out.delete(); throw Failure(tr("Couldn't save the story. Try again.")) }
        withContext(Dispatchers.IO) {
            try { insert(ctx, true) { o -> out.inputStream().use { it.copyTo(o) } } } finally { out.delete() }
        }
    }
}
