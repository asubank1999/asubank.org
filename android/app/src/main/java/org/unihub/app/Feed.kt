package org.unihub.app

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import java.time.Instant
import java.util.UUID
import androidx.compose.material.icons.automirrored.filled.Send
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

// The feed: everyone's posts with photos and comments, plus stories that disappear after 24 hours.
// Everything lives in the accounts API, so both apps share it.

@Serializable
data class StoryItem(
    val id: String,
    val image: String = "",
    val video: String = "",
    val audience: String? = null,
    val text: String = "",
    val bg: String = "",
    val created: String? = null,
    val seen: Boolean = false,
    val views: Int? = null,
    val style: String = "",
)

@Serializable
data class StoryGroup(val user: AbUser, val stories: List<StoryItem> = emptyList(), val seen: Boolean = false)

@Serializable
data class PostComment(val id: String, val text: String = "", val created: String? = null, val user: AbUser)

object PhotoTools {
    /** Reads a picked photo the right way up, with its long side no bigger than maxSide */
    fun load(ctx: android.content.Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = ctx.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val turn = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)
        val upright = if (turn == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn) }, true)
        val scale = minOf(1f, maxSide.toFloat() / maxOf(upright.width, upright.height))
        if (scale >= 1f) upright else Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt(), (upright.height * scale).toInt(), true)
    }.getOrNull()

    fun base64(b: Bitmap, quality: Int = 78): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun jpeg(ctx: android.content.Context, uri: Uri, maxSide: Int = 1280): String? = withContext(Dispatchers.IO) {
        load(ctx, uri, maxSide)?.let { base64(it) }
    }

    /** The same Drive photo at a size that looks sharp full screen */
    fun large(url: String) = url.replace(Regex("sz=w\\d+"), "sz=w1600")
}

/** The photo showing full screen, over everything */
object PhotoPeek {
    val url = MutableStateFlow<String?>(null)
    fun show(u: String) { url.value = PhotoTools.large(u) }
}

/** Hold a photo to see it bigger. A tap still reaches whatever the photo sits in. */
fun Modifier.holdToView(photo: String?): Modifier = if (photo.isNullOrEmpty()) this else pointerInput(photo) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var held = true
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation(PointerEventPass.Initial)
            held = false
        }
        if (held) {
            PhotoPeek.show(photo)
            // the rest of this touch belongs to the photo, so the row underneath doesn't open too
            do {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    }
}

@Composable
fun PhotoViewerHost() {
    val url by PhotoPeek.url.collectAsState()
    url?.let { u ->
        var zoom by remember(u) { mutableStateOf(1f) }
        var dx by remember(u) { mutableStateOf(0f) }
        var dy by remember(u) { mutableStateOf(0f) }
        Dialog(onDismissRequest = { PhotoPeek.url.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    u, tr("View photo"),
                    Modifier.fillMaxSize()
                        .pointerInput(u) {
                            detectTransformGestures { _, pan, z, _ ->
                                zoom = (zoom * z).coerceIn(1f, 5f)
                                if (zoom > 1f) { dx += pan.x; dy += pan.y } else { dx = 0f; dy = 0f }
                            }
                        }
                        .pointerInput(u) {
                            detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else 2.5f; dx = 0f; dy = 0f })
                        }
                        .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = dx; translationY = dy },
                    contentScale = ContentScale.Fit,
                )
                IconButton(onClick = { PhotoPeek.url.value = null }, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.Filled.Close, tr("Close"), tint = Color.White)
                }
            }
        }
    }
}

// MARK: - feed

/** Who a post or story is for, remembered for the next one */
object AudiencePref {
    var value: String
        get() = Saved.prefs.getString("ab.audience", "public") ?: "public"
        set(v) { Saved.prefs.edit().putString("ab.audience", v).apply() }
}

fun audienceIcon(a: String?): androidx.compose.ui.graphics.vector.ImageVector = when (a) {
    "public" -> Icons.Filled.Public
    "friends" -> Icons.Filled.Group
    else -> Icons.Filled.School
}

private fun audienceLabel(a: String) = when (a) {
    "public" -> "Everyone, also guests"
    "friends" -> "Friends only"
    else -> "ASU students"
}

@Composable
fun AudienceMenu(audience: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(audienceIcon(audience), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr(audienceLabel(audience)))
        }
        DropdownMenu(open, { open = false }) {
            listOf("public", "students", "friends").forEach { a ->
                DropdownMenuItem(
                    text = { Text(tr(audienceLabel(a))) }, leadingIcon = { Icon(audienceIcon(a), null) },
                    onClick = { open = false; AudiencePref.value = a; onChange(a) },
                )
            }
        }
    }
}

@Composable
fun FeedScreen(back: Boolean = false) {
    val extras by FeedExtras.items
    LaunchedEffect(Unit) { FeedExtras.load() }
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    var chosen by remember { mutableStateOf(Saved.prefs.getString("ab.feed.scope", "friends") ?: "friends") }
    // people without an account only have the guest feed
    val feedScope = if (me == null) "guest" else chosen
    val action = if (me == null) "publicFeed" else "feedAll"
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var groups by remember { mutableStateOf<List<StoryGroup>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<StoryDraft?>(null) }
    var viewing by remember { mutableStateOf<Int?>(null) }

    suspend fun loadStories() {
        runCatching { groups = Account.call(if (Account.user.value == null) "publicStories" else "stories")["groups"].decodeAs<List<StoryGroup>>().orEmpty() }
    }
    suspend fun load() {
        if (posts.isEmpty()) Account.saved(action, feedScope)?.let { posts = it["posts"].decodeAs<List<FeedPost>>().orEmpty() }
        try {
            val list = Account.callSaving(action, mapOf("scope" to feedScope), feedScope)["posts"].decodeAs<List<FeedPost>>().orEmpty()
            posts = list
            more = list.size >= 25
            error = null
        } catch (e: ApiError) { error = e.message }
        loaded = true
    }
    LaunchedEffect(me?.id, feedScope) {
        posts = emptyList()
        loaded = false
        launch { loadStories() }
        load()
    }

    if (composing) ComposePostDialog({ composing = false }) { scope.launch { load() } }
    if (camera) StoryCameraDialog({ camera = false }) { media -> camera = false; draft = StoryDraft(media) }
    draft?.let { d -> ComposeStoryDialog(d.media, { draft = null }) { scope.launch { loadStories() } } }
    viewing?.let { start -> StoryViewer(groups, start) { viewing = null; scope.launch { loadStories() } } }

    SlideToCamera(enabled = me != null, open = { camera = true }) {
    Screen("Feed", back = back, large = true, actions = {
        OnlineButton()
        if (me != null) IconButton(onClick = { camera = true }) { Icon(Icons.Filled.PhotoCamera, tr("Add story")) }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            LazyColumn {
                if (me != null || groups.isNotEmpty()) item { StoriesRow(groups, { camera = true }, { viewing = it }) }
                if (me != null) {
                    item {
                        Group {
                            Row(
                                Modifier.fillMaxWidth().clickable { composing = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                me?.let { Avatar(it.name, it.color, 38.dp, it.isAdmin, it.avatar, hold = false) }
                                Text(tr("What's on your mind?"), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(Icons.Filled.Image, null, tint = accent)
                            }
                        }
                    }
                    item {
                        PillTabs(listOf("friends" to tr("Friends"), "strangers" to tr("Strangers"), "guest" to tr("Guest")), feedScope) { k ->
                            chosen = k
                            Saved.prefs.edit().putString("ab.feed.scope", k).apply()
                        }
                    }
                } else item {
                    Group {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("You're browsing as a guest", fontWeight = FontWeight.SemiBold)
                            Text("Sign in to post, comment, like and add stories.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { Account.signInShown.value = true }) { Text("Sign in or create account") }
                        }
                    }
                }
                if (posts.isEmpty()) item {
                    when {
                        error != null -> Group {
                            Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { scope.launch { load() } }) { Text(tr("Try again")) }
                        }
                        loaded -> EmptyState(
                            Icons.Filled.Forum,
                            tr(when (feedScope) { "friends" -> "No posts from friends yet"; "strangers" -> "No posts from other students yet"; else -> "No public posts yet" }),
                            tr(when (feedScope) {
                                "friends" -> "Add friends, or look at what other students share under Strangers."
                                "guest" -> "Posts shared with everyone show up here. Anyone can read them, also without an account."
                                else -> "Share something with other ASU students."
                            }),
                        )
                        else -> Column { repeat(3) { SkeletonPost() } }
                    }
                }
                // an empty feed still has something to read
                if (posts.isEmpty() && loaded && error == null) items(minOf(3, extras.size)) { n -> FeedExtras.pick(n)?.let { FeedExtraCard(it) } }
                itemsIndexed(posts, key = { _, p -> p.id }) { i, p ->
                    Column(Modifier.animateItem()) {
                        PostCard(p) { scope.launch { load() } }
                        // a news card or a fact after every fourth post
                        if ((i + 1) % 4 == 0) FeedExtras.pick(i / 4)?.let { FeedExtraCard(it) }
                    }
                }
                if (more && posts.isNotEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                        TextButton(enabled = !loadingMore, onClick = {
                            loadingMore = true
                            scope.launch {
                                runCatching {
                                    val list = Account.call(action, mapOf("scope" to feedScope, "offset" to posts.size))["posts"].decodeAs<List<FeedPost>>().orEmpty()
                                    val have = posts.map { it.id }.toSet()
                                    posts = posts + list.filter { it.id !in have }
                                    more = list.size >= 25
                                }
                                loadingMore = false
                            }
                        }) { Text(tr("Load more")) }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    }
}

/** Instagram-style: the whole Feed follows the finger sideways and the camera shows underneath.
 *  Let go past a third of the screen (or flick) and the camera opens; otherwise the Feed springs back. */
@Composable
private fun SlideToCamera(enabled: Boolean, open: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val settle = remember { androidx.compose.animation.core.Animatable(0f) }
    val x = if (dragging) dragX else settle.value
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                fun release(fling: Float) {
                    val from = dragX
                    dragging = false
                    scope.launch {
                        settle.snapTo(from)
                        if (kotlin.math.abs(from) > width * 0.3f || (kotlin.math.abs(fling) > 1400f && from * fling > 0)) {
                            settle.animateTo(kotlin.math.sign(from) * width, tween(170))
                            open()
                            delay(350)
                            settle.snapTo(0f)
                        } else settle.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 500f))
                    }
                }
                // the stories row scrolls by itself and takes its own swipes first
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); dragX = settle.value; dragging = true },
                    onDragEnd = { release(tracker.calculateVelocity().x) },
                    onDragCancel = { release(0f) },
                ) { c, d ->
                    c.consume()
                    tracker.addPosition(c.uptimeMillis, c.position)
                    dragX = (dragX + d).coerceIn(-width, width)
                }
            },
    ) {
        if (x != 0f) {
            val p = (kotlin.math.abs(x) / width).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.graphicsLayer { alpha = (p * 1.4f).coerceAtMost(1f); scaleX = 0.7f + 0.3f * p; scaleY = scaleX },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Text(tr("Add story"), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = x
                shadowElevation = if (x != 0f) 28f else 0f
                if (x != 0f) { shape = RoundedCornerShape((24f * (kotlin.math.abs(x) / width).coerceIn(0f, 1f) * 4f).coerceAtMost(24f).dp); clip = true }
            },
        ) { content() }
    }
}

// MARK: - stories

@Composable
private fun StoriesRow(groups: List<StoryGroup>, add: () -> Unit, open: (Int) -> Unit) {
    val me by Account.user.collectAsState()
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (me != null) item {
            Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { add() }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Avatar(me?.name ?: "", me?.color ?: "#8C1D40", 60.dp, photo = me?.avatar, hold = false)
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(accent)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, tr("Add story"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
                }
                Text(tr("Add story"), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
            }
        }
        itemsIndexed(groups, key = { _, g -> g.user.id }) { i, g ->
            Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { open(i) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.border(2.5.dp, if (g.seen) MaterialTheme.colorScheme.outlineVariant else accent, CircleShape).padding(4.dp)) {
                    Avatar(g.user.name, g.user.color, 54.dp, photo = g.user.avatar, hold = false)
                }
                Text(
                    if (g.user.id == me?.id) tr("Your story") else g.user.name, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

/** News about UniHub and random facts, mixed into the Feed. They come live from assets/app/feed-extras.json. */
@kotlinx.serialization.Serializable
data class FeedExtra(val id: String = "", val kind: String = "", val emoji: String = "", val title: String = "", val text: String = "", val only: String = "")

object FeedExtras {
    val items = mutableStateOf<List<FeedExtra>>(emptyList())
    private var fetched = false
    private fun apply(text: String): Boolean {
        val all = runCatching { json.decodeFromString<Map<String, List<FeedExtra>>>(text) }.getOrNull() ?: return false
        val list = (all[Lang.code] ?: all["en"].orEmpty()).filter { it.only != "web" }
        if (list.isEmpty()) return false
        items.value = list
        return true
    }
    suspend fun load() {
        if (items.value.isEmpty()) Saved.prefs.getString("feedExtras.cache", null)?.let { apply(it) }
        if (fetched) return
        fetched = true
        Net.text("https://asubank.org/assets/app/feed-extras.json")?.let { if (apply(it)) Saved.prefs.edit().putString("feedExtras.cache", it).apply() }
    }
    /** the n-th card to show; a different order each day, the same all day */
    fun pick(n: Int): FeedExtra? {
        val l = items.value
        if (l.isEmpty()) return null
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        return l[(n + day) % l.size]
    }
}

@Composable
fun FeedExtraCard(item: FeedExtra) {
    Group {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text(item.emoji, fontSize = 24.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (item.kind == "news") Text(tr("UniHub news").uppercase(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StoryViewer(groups: List<StoryGroup>, start: Int, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    var g by remember { mutableIntStateOf(start) }
    var s by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var removed by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<ReportTarget?>(null) }
    var videoLength by remember { mutableStateOf<Double?>(null) }
    var videoPlayed by remember { mutableStateOf<Double?>(null) }
    var videoEnded by remember { mutableStateOf(false) }
    var swipe by remember { mutableFloatStateOf(0f) }
    var viewersOf by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // saving a story: the photo or video gets the UniHub watermark; anyone can save
    var saving by remember { mutableStateOf(false) }
    fun saveStory() {
        val item = groups.getOrNull(g)?.stories?.filter { it.id !in removed }?.getOrNull(s) ?: return
        val owner = groups.getOrNull(g)?.user ?: return
        saving = true
        android.widget.Toast.makeText(ctx, tr("Saving…"), android.widget.Toast.LENGTH_SHORT).show()
        scope.launch {
            val msg = try {
                if (item.video.isNotEmpty()) StorySave.saveVideo(ctx, item.video, owner.username)
                else StorySave.savePhoto(ctx, PhotoTools.large(item.image), owner.username)
                tr("Saved to your gallery")
            } catch (e: Exception) { e.message ?: tr("Couldn't save the story. Try again.") }
            saving = false
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val storageAsk = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) saveStory() else android.widget.Toast.makeText(ctx, tr("Allow storage to save stories."), android.widget.Toast.LENGTH_LONG).show()
    }
    // reactions and replies reach the owner as a private message, like Instagram
    fun sendToOwner(text: String, note: String) {
        val to = groups.getOrNull(g)?.user?.id ?: return
        scope.launch {
            val msg = try { Account.call("dmSend", mapOf("userId" to to, "text" to text)); note } catch (e: ApiError) { e.message ?: tr("Something went wrong.") }
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val group = groups.getOrNull(g)
    val stories = group?.stories?.filter { it.id !in removed }.orEmpty()
    val story = stories.getOrNull(s)

    fun forward() {
        if (s + 1 < stories.size) s += 1
        else if (g + 1 < groups.size) { g += 1; s = 0 }
        else onClose()
    }
    fun back() {
        if (s > 0) s -= 1
        else if (g > 0) { g -= 1; s = maxOf(0, groups[g].stories.filter { it.id !in removed }.size - 1) }
        else progress = 0f
    }

    LaunchedEffect(g, s, story?.id) {
        progress = 0f
        videoLength = null
        videoPlayed = null
        videoEnded = false
        val item = story ?: return@LaunchedEffect
        if (!item.seen && Account.user.value != null) launch { runCatching { Account.call("storySeen", mapOf("id" to item.id)) } }
        val isVideo = item.video.isNotEmpty()
        val stopped = { paused || typing || saving || reporting != null || menu || viewersOf != null }
        while (true) {
            delay(50)
            if (isVideo) {
                // the bar follows the real video and the story moves on when the video has ended,
                // so a video that loads or plays slowly is never cut short
                val length = videoLength ?: continue
                if (videoEnded) break
                val at = videoPlayed
                if (at != null) progress = (at / length).toFloat().coerceAtMost(1f)
                else if (!stopped()) { progress += 0.05f / length.toFloat(); if (progress >= 1f) break }   // the video could not play
            } else {
                if (!stopped()) progress += 0.05f / 6f
                if (progress >= 1f) break
            }
        }
        forward()
    }
    reporting?.let { t -> ReportDialog(t) { reporting = null } }
    viewersOf?.let { id -> StoryViewersDialog(id) { viewersOf = null } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Crossfade(story, Modifier.fillMaxSize(), tween(200), label = "story") { item ->
                if (item != null) Box(Modifier.fillMaxSize()) {
                    val look = StoryStyle.parse(item.style)
                    if (item.image.isNotEmpty()) AsyncImage(PhotoTools.large(item.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Box(Modifier.fillMaxSize().background(storyBrush(item.bg.ifEmpty { "#8C1D40" }, look.bg2)))
                    if (item.video.isNotEmpty()) key(item.id) {
                        StoryVideoPlayer(item.video, paused || typing || saving || reporting != null || menu || viewersOf != null,
                            onPlayed = { videoPlayed = it }, onEnd = { videoEnded = true }) { videoLength = it }
                    }
                    if (item.text.isNotEmpty()) StoryTextView(item.text, look, Modifier.align(Alignment.Center))
                }
            }
            // left side goes back, right side goes on, holding pauses
            Box(
                Modifier.fillMaxSize().pointerInput(g, s, stories.size) {
                    detectTapGestures(
                        onPress = { paused = true; tryAwaitRelease(); paused = false },
                        onTap = { o -> if (o.x < size.width / 2) back() else forward() },
                    )
                }.pointerInput(g) {
                    // swiping sideways goes to the next or previous person's stories
                    detectHorizontalDragGestures(
                        onDragStart = { swipe = 0f },
                        onDragEnd = {
                            if (swipe < -70.dp.toPx()) { if (g + 1 < groups.size) { g += 1; s = 0 } else onClose() }
                            else if (swipe > 70.dp.toPx() && g > 0) { g -= 1; s = 0 }
                        },
                    ) { c, d ->
                        c.consume()
                        swipe += d
                    }
                },
            )
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stories.indices.forEach { i ->
                        LinearProgressIndicator(
                            progress = { if (i < s) 1f else if (i == s) progress.coerceIn(0f, 1f) else 0f },
                            Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color.White, trackColor = Color.White.copy(alpha = 0.3f), drawStopIndicator = {},
                        )
                    }
                }
                group?.user?.let { u ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(u.name, u.color, 36.dp, photo = u.avatar, hold = false)
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(Social.ago(story?.created), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Color.White) }
                            DropdownMenu(menu, { menu = false }) {
                                if (story != null && (story.video.isNotEmpty() || story.image.isNotEmpty())) DropdownMenuItem(
                                    text = { Text(tr("Save to your gallery")) },
                                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                                    enabled = !saving,
                                    onClick = {
                                        menu = false
                                        val needs = android.os.Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (needs) storageAsk.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) else saveStory()
                                    },
                                )
                                if (me != null && (u.id == me?.id || Account.isAdmin)) DropdownMenuItem(text = { Text(tr("Delete")) }, onClick = {
                                    menu = false
                                    val id = story?.id ?: return@DropdownMenuItem
                                    scope.launch { runCatching { Account.call("storyDelete", mapOf("id" to id)) } }
                                    removed = removed + id
                                    val left = stories.size - 1
                                    if (left <= 0) forward() else if (s >= left) s = left - 1
                                })
                                if (me != null && u.id != me?.id) DropdownMenuItem(text = { Text(tr("Report")) }, onClick = {
                                    menu = false
                                    story?.let { reporting = ReportTarget("story", it.id, it.text) }
                                })
                            }
                        }
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close"), tint = Color.White) }
                    }
                }
            }
            val views = story?.views
            if (views != null && group?.user?.id == me?.id) Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f)).clickable { viewersOf = story?.id }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(trf("%d views", views), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            val owner = group?.user
            if (me != null && owner != null && owner.id != me?.id) Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!typing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("❤️", "😂", "😮", "😢", "👏", "🔥").forEach { e ->
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                                .clickable { sendToOwner("$e Reacted to your story", tr("Sent") + " " + e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fun go() {
                        val t = reply.trim()
                        if (t.isEmpty()) return
                        reply = ""
                        focus.clearFocus()
                        sendToOwner("↩️ Replied to your story: $t", tr("Reply sent"))
                    }
                    OutlinedTextField(
                        reply, { reply = it.take(500) },
                        Modifier.weight(1f).onFocusChanged { typing = it.isFocused },
                        placeholder = { Text(tr("Send a reply…"), color = Color.White.copy(alpha = 0.75f)) },
                        singleLine = true, shape = RoundedCornerShape(50),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { go() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                            focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                        ),
                    )
                    if (reply.isNotBlank()) IconButton(onClick = { go() }) {
                        Icon(Icons.AutoMirrored.Filled.Send, tr("Send"), tint = Color.White)
                    }
                }
            }
        }
    }
}

// MARK: - comments

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CommentsDialog(post: FeedPost, changed: (Int) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val me by Account.user.collectAsState()
    var comments by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf<ReportTarget?>(null) }

    suspend fun load() {
        try {
            comments = Account.call("comments", mapOf("postId" to post.id))["comments"].decodeAs<List<PostComment>>().orEmpty()
            error = null
            changed(comments.size)
        } catch (e: ApiError) { error = e.message }
        loaded = true
    }
    LaunchedEffect(post.id) { load() }
    reporting?.let { t -> ReportDialog(t) { reporting = null } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close")) }
                    Text(tr("Comments"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(Modifier.weight(1f)) {
                    if (comments.isEmpty()) item {
                        if (loaded) EmptyState(Icons.Filled.ChatBubbleOutline, tr("No comments yet"), tr("Be the first to comment."))
                        else Column { repeat(4) { SkeletonRow() } }
                    }
                    items(comments, key = { it.id }) { c ->
                        var held by remember { mutableStateOf(false) }
                        androidx.compose.material3.DropdownMenu(held, { held = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Copy") }, onClick = {
                                held = false
                                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Comment", c.text))
                            })
                            if (c.user.id == me?.id || post.user.id == me?.id || Account.isAdmin) androidx.compose.material3.DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = {
                                held = false
                                comments = comments.filterNot { it.id == c.id }
                                changed(comments.size)
                                scope.launch { runCatching { Account.call("commentDelete", mapOf("id" to c.id)) }; load() }
                            })
                            if (c.user.id != me?.id) androidx.compose.material3.DropdownMenuItem(text = { Text("Report") }, onClick = { held = false; reporting = ReportTarget("comment", c.id, c.text) })
                        }
                        Row(
                            Modifier.animateItem().fillMaxWidth().graphicsLayer { alpha = if (c.id.startsWith("local-")) 0.6f else 1f }.combinedClickable(onClick = {}, onLongClick = { held = true }).padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Avatar(c.user.name, c.user.color, 34.dp, c.user.isAdmin, c.user.avatar)
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.user.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(Social.ago(c.created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(c.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (c.user.id == me?.id || post.user.id == me?.id || Account.isAdmin) IconButton(onClick = {
                                comments = comments.filterNot { it.id == c.id }
                                changed(comments.size)
                                scope.launch { runCatching { Account.call("commentDelete", mapOf("id" to c.id)) }; load() }
                            }, Modifier.size(32.dp)) { Icon(Icons.Filled.Delete, tr("Delete"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (c.user.id != me?.id) IconButton(onClick = { reporting = ReportTarget("comment", c.id, c.text) }, Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Flag, tr("Report"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    error?.let { e -> item { Text(e, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
                }
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        draft, { if (it.length <= 500) draft = it }, Modifier.weight(1f),
                        placeholder = { Text(tr("Write a comment")) }, shape = RoundedCornerShape(24.dp), maxLines = 3,
                    )
                    FilledIconButton(enabled = draft.isNotBlank(), onClick = {
                        // the comment shows at once; only a failure brings the words back
                        val words = draft.trim().take(500)
                        val u = me
                        if (words.isNotEmpty() && u != null) {
                            val temp = PostComment("local-" + UUID.randomUUID(), words, Instant.now().toString(), u)
                            draft = ""
                            comments = comments + temp
                            changed(comments.size)
                            BackgroundJobs.scope.launch {
                                try {
                                    Account.call("commentAdd", mapOf("postId" to post.id, "text" to words))
                                    load()
                                } catch (e: Exception) {
                                    comments = comments.filterNot { it.id == temp.id }
                                    changed(comments.size)
                                    if (draft.isEmpty()) draft = words
                                    error = e.message
                                }
                            }
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, tr("Send")) }
                }
            }
        }
    }
}

/** Colors for the Home banner, from the accent color */
fun heroBrush(light: Long): Brush {
    val c = Color(light)
    return Brush.linearGradient(listOf(c, Color(c.red * 0.62f, c.green * 0.62f, c.blue * 0.62f)))
}
