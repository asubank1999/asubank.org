package org.asubank.app

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
    val text: String = "",
    val bg: String = "",
    val created: String? = null,
    val seen: Boolean = false,
    val views: Int? = null,
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

@Composable
fun FeedScreen(back: Boolean = false) {
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    var feedScope by remember { mutableStateOf("all") }
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var groups by remember { mutableStateOf<List<StoryGroup>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var addingStory by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Int?>(null) }

    suspend fun loadStories() { runCatching { groups = Account.call("stories")["groups"].decodeAs<List<StoryGroup>>().orEmpty() } }
    suspend fun load() {
        if (Account.user.value == null) return
        if (posts.isEmpty()) Account.saved("feedAll", feedScope)?.let { posts = it["posts"].decodeAs<List<FeedPost>>().orEmpty() }
        try {
            val list = Account.callSaving("feedAll", mapOf("scope" to feedScope), feedScope)["posts"].decodeAs<List<FeedPost>>().orEmpty()
            posts = list
            more = list.size >= 25
            error = null
        } catch (e: ApiError) { error = e.message }
        loaded = true
    }
    LaunchedEffect(me?.id, feedScope) {
        launch { loadStories() }
        load()
    }

    if (composing) ComposePostDialog({ composing = false }) { scope.launch { load() } }
    if (addingStory) ComposeStoryDialog({ addingStory = false }) { scope.launch { loadStories() } }
    viewing?.let { start -> StoryViewer(groups, start) { viewing = null; scope.launch { loadStories() } } }

    Screen("Feed", back = back, large = true) { pad ->
        if (me == null) {
            Box(Modifier.padding(pad)) { SignInPrompt("Sign in to see the feed", "Post, comment and share stories with other ASU students.") }
            return@Screen
        }
        LazyColumn(Modifier.padding(pad)) {
            item { StoriesRow(groups, { addingStory = true }, { viewing = it }) }
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "Everyone", "friends" to "Friends").forEach { (k, label) ->
                        FilterChip(selected = feedScope == k, onClick = {
                            if (feedScope != k) { feedScope = k; posts = emptyList(); loaded = false }
                        }, label = { Text(tr(label)) })
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
                        tr(if (feedScope == "friends") "No posts from friends yet" else "No posts yet"),
                        tr("Share something with other ASU students."),
                    )
                    else -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            items(posts, key = { it.id }) { p -> PostCard(p) { scope.launch { load() } } }
            if (more && posts.isNotEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                    TextButton(enabled = !loadingMore, onClick = {
                        loadingMore = true
                        scope.launch {
                            runCatching {
                                val list = Account.call("feedAll", mapOf("scope" to feedScope, "offset" to posts.size))["posts"].decodeAs<List<FeedPost>>().orEmpty()
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

// MARK: - stories

@Composable
private fun StoriesRow(groups: List<StoryGroup>, add: () -> Unit, open: (Int) -> Unit) {
    val me by Account.user.collectAsState()
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
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

@Composable
private fun ComposeStoryDialog(onClose: () -> Unit, posted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var bg by remember { mutableStateOf("#8C1D40") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) photo = uri }
    val colors = listOf("#8C1D40", "#0A63C9", "#1F7A3A", "#6E3FD1", "#D2510A", "#00847F", "#C2185B", "#3A3A3C")

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("New story")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)).background(hexColor(bg)), contentAlignment = Alignment.Center) {
                    photo?.let { AsyncImage(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                    if (text.isNotEmpty()) Text(text, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pick.launch("image/*") }) { Text(tr(if (photo == null) "Add a photo" else "Change photo")) }
                    if (photo != null) TextButton(onClick = { photo = null }) { Text(tr("Remove photo"), color = MaterialTheme.colorScheme.error) }
                }
                if (photo == null) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(hexColor(c))
                                .border(if (bg == c) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { bg = c },
                        )
                    }
                }
                OutlinedTextField(text, { if (it.length <= 300) text = it }, Modifier.fillMaxWidth(), label = { Text(tr("Write something")) })
                Text(
                    tr("Your story is seen by ASU Bank students for 24 hours, then it disappears. It's deleted from our database after 2 days."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (photo != null || text.isNotBlank()), onClick = {
                busy = true
                scope.launch {
                    try {
                        val fields = mutableMapOf<String, Any?>("text" to text, "bg" to bg)
                        photo?.let { fields["image"] = PhotoTools.jpeg(ctx, it) ?: throw ApiError(tr("Couldn't load this photo")) }
                        Account.call("storyCreate", fields)
                        posted()
                        onClose()
                    } catch (e: ApiError) { error = e.message }
                    busy = false
                }
            }) { Text(tr(if (busy) "Posting…" else "Post")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } },
    )
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
        val item = story ?: return@LaunchedEffect
        if (!item.seen) launch { runCatching { Account.call("storySeen", mapOf("id" to item.id)) } }
        while (progress < 1f) {
            delay(50)
            if (!paused && reporting == null && !menu) progress += 0.05f / 6f
        }
        forward()
    }
    reporting?.let { t -> ReportDialog(t) { reporting = null } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            story?.let { item ->
                if (item.image.isNotEmpty()) AsyncImage(PhotoTools.large(item.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxSize().background(hexColor(item.bg.ifEmpty { "#8C1D40" })))
                if (item.text.isNotEmpty()) Text(
                    item.text, Modifier.align(Alignment.Center).padding(24.dp), color = Color.White, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            }
            // left side goes back, right side goes on, holding pauses
            Box(
                Modifier.fillMaxSize().pointerInput(g, s, stories.size) {
                    detectTapGestures(
                        onPress = { paused = true; tryAwaitRelease(); paused = false },
                        onTap = { o -> if (o.x < size.width / 2) back() else forward() },
                    )
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
                                if (u.id == me?.id || Account.isAdmin) DropdownMenuItem(text = { Text(tr("Delete")) }, onClick = {
                                    menu = false
                                    val id = story?.id ?: return@DropdownMenuItem
                                    scope.launch { runCatching { Account.call("storyDelete", mapOf("id" to id)) } }
                                    removed = removed + id
                                    val left = stories.size - 1
                                    if (left <= 0) forward() else if (s >= left) s = left - 1
                                })
                                if (u.id != me?.id) DropdownMenuItem(text = { Text(tr("Report")) }, onClick = {
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
                    .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(trf("%d views", views), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// MARK: - comments

@Composable
fun CommentsDialog(post: FeedPost, changed: (Int) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    var comments by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
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
                        else Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    items(comments, key = { it.id }) { c ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(c.user.name, c.user.color, 34.dp, c.user.isAdmin, c.user.avatar)
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.user.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(Social.ago(c.created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(c.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (c.user.id == me?.id || post.user.id == me?.id || Account.isAdmin) IconButton(onClick = {
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
                    FilledIconButton(enabled = !busy && draft.isNotBlank(), onClick = {
                        busy = true
                        scope.launch {
                            try {
                                Account.call("commentAdd", mapOf("postId" to post.id, "text" to draft))
                                draft = ""
                                load()
                            } catch (e: ApiError) { error = e.message }
                            busy = false
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
