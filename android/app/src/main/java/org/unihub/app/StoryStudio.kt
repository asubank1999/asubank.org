package org.unihub.app

import android.graphics.Bitmap
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// 1.14.0: the new screen for making a story, text styles and effects, and who saw a story.

@Serializable
data class StoryStyle(
    val font: String = "classic",
    val effect: String = "shadow",
    val color: String = "#FFFFFF",
    val bg2: String = "",
    val align: String = "center",
    val anim: String = "none",
) {
    val fields: Map<String, String> get() = mapOf("font" to font, "effect" to effect, "color" to color, "bg2" to bg2, "align" to align, "anim" to anim)

    companion object {
        fun parse(s: String): StoryStyle = if (s.isEmpty()) StoryStyle() else runCatching { json.decodeFromString<StoryStyle>(s) }.getOrDefault(StoryStyle())

        val fonts = listOf("classic" to "Classic", "modern" to "Modern", "serif" to "Serif", "mono" to "Typewriter", "hand" to "Handwriting", "script" to "Script", "tall" to "Tall", "round" to "Round")
        val effects = listOf("none" to "Plain", "shadow" to "Shadow", "outline" to "Outline", "neon" to "Neon", "box" to "Label", "retro" to "Retro", "rainbow" to "Rainbow", "glow" to "Glow")
        val anims = listOf("none" to "Still", "pop" to "Pop", "pulse" to "Pulse", "float" to "Float", "fade" to "Fade")
        val colors = listOf("#FFFFFF", "#000000", "#FFD60A", "#FF9F0A", "#FF453A", "#FF375F", "#FF6F9A", "#BF5AF2", "#8B5CF6", "#5E5CE6", "#0A84FF", "#64D2FF", "#30D158", "#00C7BE", "#A2845E", "#C7C7CC")
        val backgrounds = listOf(
            "#0B1560" to "", "#8C1D40" to "", "#0A63C9" to "", "#1F7A3A" to "", "#6E3FD1" to "", "#D2510A" to "", "#00847F" to "", "#C2185B" to "", "#3A3A3C" to "", "#000000" to "",
            "#0B1560" to "#8B5CF6", "#FF375F" to "#FF9F0A", "#5E5CE6" to "#64D2FF", "#30D158" to "#00C7BE", "#BF5AF2" to "#FF6F9A", "#FF9F0A" to "#FFD60A", "#1C1C1E" to "#5E5CE6", "#8C1D40" to "#0B1560",
        )
    }

    val family: FontFamily get() = when (font) {
        "modern" -> FontFamily(Font(DeviceFontFamilyName("sans-serif-black")))
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "hand" -> FontFamily(Font(DeviceFontFamilyName("casual")))
        "script" -> FontFamily.Cursive
        "tall" -> FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed")))
        "round" -> FontFamily(Font(DeviceFontFamilyName("sans-serif-medium")))
        else -> FontFamily.SansSerif
    }
}

fun storyBrush(bg: String, bg2: String): Brush =
    if (bg2.isEmpty()) SolidColor(hexColor(bg)) else Brush.linearGradient(listOf(hexColor(bg), hexColor(bg2)))

/** Story text with its font, colour, effect and motion */
@Composable
fun StoryTextView(text: String, style: StoryStyle, modifier: Modifier = Modifier, size: Int = 28) {
    val c = hexColor(style.color)
    val align = when (style.align) { "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center }
    val spin = rememberInfiniteTransition(label = "story")
    val time by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(when (style.anim) { "pulse" -> 700; "float" -> 1600; "fade" -> 1200; else -> 3000 }, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "story")
    val popped = remember(text, style.anim) { Animatable(if (style.anim == "pop") 0.3f else 1f) }
    LaunchedEffect(text, style.anim) { if (style.anim == "pop") popped.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    val base = TextStyle(fontFamily = style.family, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size * 1.25f).sp, textAlign = align)

    @Composable
    fun line(s: TextStyle, m: Modifier = Modifier) = androidx.compose.material3.Text(text, m.fillMaxWidth(), style = s)

    Box(
        modifier.fillMaxWidth().padding(horizontal = 22.dp).graphicsLayer {
            val scale = when (style.anim) { "pop" -> popped.value; "pulse" -> 0.96f + 0.11f * time; else -> 1f }
            scaleX = scale; scaleY = scale
            if (style.anim == "float") translationY = (time - 0.5f) * 20.dp.toPx()
            if (style.anim == "fade") alpha = 0.25f + 0.75f * time
        },
    ) {
        when (style.effect) {
            "none" -> line(base.copy(color = c))
            "outline" -> {
                val edge = if (style.color == "#000000") Color.White else Color.Black
                line(base.copy(color = edge, drawStyle = Stroke(width = 7f)))
                line(base.copy(color = c))
            }
            "neon" -> {
                line(base.copy(color = c, shadow = Shadow(c, blurRadius = 40f)))
                line(base.copy(color = Color.White, shadow = Shadow(c, blurRadius = 14f)))
            }
            "box" -> Box(Modifier.fillMaxWidth(), contentAlignment = when (style.align) { "left" -> Alignment.CenterStart; "right" -> Alignment.CenterEnd; else -> Alignment.Center }) {
                androidx.compose.material3.Text(
                    text, Modifier.clip(RoundedCornerShape(10.dp)).background(c).padding(horizontal = 12.dp, vertical = 6.dp),
                    style = base.copy(color = if (style.color == "#000000") Color.White else Color.Black),
                )
            }
            "retro" -> {
                line(base.copy(color = hexColor("#64D2FF")), Modifier.offset(6.dp, 6.dp))
                line(base.copy(color = hexColor("#FF375F")), Modifier.offset(3.dp, 3.dp))
                line(base.copy(color = c))
            }
            "rainbow" -> line(
                base.copy(
                    brush = Brush.linearGradient(
                        listOf(hexColor("#FF375F"), hexColor("#FF9F0A"), hexColor("#FFD60A"), hexColor("#30D158"), hexColor("#0A84FF"), hexColor("#BF5AF2")),
                        Offset(-600f + 1200f * time, 0f), Offset(600f + 1200f * time, 200f),
                    ),
                ),
            )
            "glow" -> line(base.copy(color = c, shadow = Shadow(Color.White, blurRadius = 30f)))
            else -> line(base.copy(color = c, shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 10f)))
        }
    }
}

/** The video, as big as fits and centered, playing over and over behind the tools */
@Composable
private fun LoopingVideo(uri: Uri, muted: Boolean, modifier: Modifier = Modifier) {
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    LaunchedEffect(muted, player) { runCatching { player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f) } }
    key(uri) {
        AndroidView(
            factory = { c ->
                FrameLayout(c).apply {
                    addView(
                        VideoView(c).apply {
                            setOnPreparedListener { mp -> mp.isLooping = true; player = mp; start() }
                            setOnErrorListener { _, _, _ -> true }
                            setVideoURI(uri)
                        },
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER),
                    )
                }
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
fun ComposeStoryDialog(media: StoryMedia?, onClose: () -> Unit, posted: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf((media as? StoryMedia.Photo)?.uri) }
    var video by remember { mutableStateOf((media as? StoryMedia.Video)?.uri) }
    var audience by remember { mutableStateOf(AudiencePref.value) }
    var bg by remember { mutableStateOf("#0B1560") }
    var style by remember { mutableStateOf(StoryStyle()) }
    var tool by remember { mutableStateOf("") }
    var muted by remember { mutableStateOf(false) }
    var pickMenu by remember { mutableStateOf(false) }
    var audienceOpen by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { photo = uri; video = null } }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { video = uri; photo = null } }
    val hasMedia = photo != null || video != null
    val canPost = hasMedia || text.isNotBlank()
    val tools = (if (hasMedia) emptyList<String>() else listOf("Background")) + listOf("Font", "Effect", "Color", "Motion")
    val current = tool.takeIf { it in tools } ?: tools.first()

    fun post() {
        val words = text.take(300)
        val b = bg
        val look = style.fields
        val who = audience
        val shot = photo
        val clip = video
        BackgroundJobs.add(tr("Posting your story…"), done = posted) {
            val fields = mutableMapOf<String, Any?>("text" to words, "bg" to b, "audience" to who, "style" to look)
            if (clip != null) {
                fields["video"] = StoryVideo.base64(ctx, clip) ?: throw ApiError(tr("That video is too big. Keep it short."))
                val still: Bitmap? = withContext(Dispatchers.IO) { StoryVideo.poster(ctx, clip) }
                still?.let { fields["image"] = withContext(Dispatchers.IO) { PhotoTools.base64(it) } }
            } else if (shot != null) {
                fields["image"] = PhotoTools.jpeg(ctx, shot) ?: throw ApiError(tr("Couldn't load this photo"))
            }
            Account.call("storyCreate", fields)
        }
        onClose()
    }

    @Composable
    fun round(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, tr(label), tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }

    @Composable
    fun chip(label: String, on: Boolean, family: FontFamily? = null, onClick: () -> Unit) {
        Box(
            Modifier.height(38.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClick).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = if (on) Color.Black else Color.White, fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val v = video
            val p = photo
            when {
                v != null -> LoopingVideo(v, muted)
                p != null -> AsyncImage(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else -> Box(Modifier.fillMaxSize().background(storyBrush(bg, style.bg2)))
            }
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    round(Icons.Filled.Close, "Cancel", onClose)
                    Spacer(Modifier.weight(1f))
                    if (v != null) round(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Sound") { muted = !muted }
                    Box {
                        round(Icons.Filled.Image, "Add a photo or video") { pickMenu = true }
                        DropdownMenu(pickMenu, { pickMenu = false }) {
                            DropdownMenuItem(text = { Text("Photo") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = { pickMenu = false; pickPhoto.launch("image/*") })
                            DropdownMenuItem(text = { Text("Video") }, leadingIcon = { Icon(Icons.Filled.Videocam, null) }, onClick = { pickMenu = false; pickVideo.launch("video/*") })
                        }
                    }
                    if (hasMedia) round(Icons.Filled.Delete, if (v != null) "Remove video" else "Remove photo") { photo = null; video = null }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (text.isNotEmpty()) StoryTextView(text, style, size = 30)
                    else if (!hasMedia) StoryTextView(tr("Write something"), style.copy(anim = "none"), Modifier.graphicsLayer { alpha = 0.55f }, 30)
                }
                Surface(Modifier.padding(8.dp), shape = RoundedCornerShape(24.dp), color = Color(0xE61C1C20)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicTextField(
                            text, { if (it.length <= 300) text = it }, Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), cursorBrush = SolidColor(Color.White), maxLines = 3,
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 11.dp)) {
                                    if (text.isEmpty()) Text("Write something", color = Color.White.copy(alpha = 0.6f))
                                    inner()
                                }
                            },
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            tools.forEach { t ->
                                val on = t == current
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.White.copy(alpha = 0.14f)).clickable { tool = t }.padding(horizontal = 13.dp, vertical = 7.dp),
                                ) { Text(t, color = if (on) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).clickable {
                                    style = style.copy(align = when (style.align) { "center" -> "left"; "left" -> "right"; else -> "center" })
                                }.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Icon(
                                    when (style.align) { "left" -> Icons.AutoMirrored.Filled.FormatAlignLeft; "right" -> Icons.AutoMirrored.Filled.FormatAlignRight; else -> Icons.Filled.FormatAlignCenter },
                                    tr("Text alignment"), tint = Color.White, modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            when (current) {
                                "Background" -> StoryStyle.backgrounds.forEach { (a, b) ->
                                    Box(
                                        Modifier.size(38.dp).clip(CircleShape).background(storyBrush(a, b))
                                            .border(if (bg == a && style.bg2 == b) 3.dp else 0.5.dp, Color.White, CircleShape)
                                            .clickable { bg = a; style = style.copy(bg2 = b) },
                                    )
                                }
                                "Font" -> StoryStyle.fonts.forEach { (id, name) -> chip(name, style.font == id, StoryStyle(font = id).family) { style = style.copy(font = id) } }
                                "Effect" -> StoryStyle.effects.forEach { (id, name) -> chip(name, style.effect == id) { style = style.copy(effect = id) } }
                                "Color" -> StoryStyle.colors.forEach { c ->
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).background(hexColor(c))
                                            .border(if (style.color == c) 3.dp else 0.5.dp, Color.White, CircleShape).clickable { style = style.copy(color = c) },
                                    )
                                }
                                else -> StoryStyle.anims.forEach { (id, name) -> chip(name, style.anim == id) { style = style.copy(anim = id) } }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { audienceOpen = true }, contentAlignment = Alignment.Center) {
                                    Icon(audienceIcon(audience), tr("Who can see this"), tint = Color.White)
                                }
                                DropdownMenu(audienceOpen, { audienceOpen = false }) {
                                    listOf("public" to "Everyone, also guests", "students" to "ASU students", "friends" to "Friends only").forEach { (a, label) ->
                                        DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(audienceIcon(a), null) }, onClick = {
                                            audienceOpen = false; audience = a; AudiencePref.value = a
                                        })
                                    }
                                }
                            }
                            Row(
                                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = if (canPost) 1f else 0.45f)).clickable(enabled = canPost) { post() },
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text("Share story", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "Your story disappears after 24 hours and is deleted from our database after 2 days.",
                            color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Serializable
private data class StoryViewerRow(val user: AbUser, val at: String? = null)

/** Who saw a story, with the exact date and time */
@Composable
fun StoryViewersDialog(storyId: String, onClose: () -> Unit) {
    val nav = LocalNav.current
    var viewers by remember { mutableStateOf<List<StoryViewerRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    LaunchedEffect(storyId) {
        try { viewers = Account.call("storyViewers", mapOf("id" to storyId))["viewers"].decodeAs<List<StoryViewerRow>>().orEmpty() }
        catch (e: ApiError) { error = e.message }
        loaded = true
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close")) }
                    Text("Viewers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(Modifier.weight(1f)) {
                    if (!loaded) items(5) { SkeletonRow() }
                    else if (error != null) item { Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                    else if (viewers.isEmpty()) item { EmptyState(Icons.Filled.Visibility, tr("No views yet"), tr("People who see your story show up here.")) }
                    items(viewers, key = { it.user.id }) { v ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onClose(); nav.navigate("profile/${v.user.id}") }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(v.user.name, v.user.color, 44.dp, v.user.isAdmin, v.user.avatar, v.user.online, hold = false)
                            Column(Modifier.weight(1f)) {
                                Text(v.user.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                v.at?.let { at ->
                                    runCatching { fmt.format(Instant.parse(at)) }.getOrNull()?.let {
                                        androidx.compose.material3.Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
