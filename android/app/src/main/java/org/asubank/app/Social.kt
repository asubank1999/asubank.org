package org.asubank.app

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class Poke(
    val id: String,
    val kind: String = "poke",
    val place: String? = null,
    val created: String? = null,
    val read: Boolean? = null,
    val user: AbUser,
)

@Serializable
data class FeedPost(
    val id: String,
    val text: String = "",
    val image: String = "",
    val comments: Int = 0,
    val created: String? = null,
    val likes: Int = 0,
    val liked: Boolean = false,
    val audience: String? = null,
    val user: AbUser,
)

object Social {
    val pokeKinds = listOf(
        Triple("poke", "Poke", "👉"),
        Triple("meet", "Ask to meet", "📍"),
        Triple("study", "Study together", "📚"),
        Triple("question", "I have a question", "❓"),
        Triple("notes", "I need your notes", "📝"),
        Triple("coffee", "Coffee?", "☕"),
    )

    fun pokeText(kind: String, name: String, place: String?): String {
        val where = place?.trim().orEmpty()
        return when (kind) {
            "meet" -> if (where.isEmpty()) trf("%s wants to meet you", name) else trf("%s wants to meet you at %s", name, where)
            "study" -> trf("%s wants to study with you", name)
            "question" -> trf("%s has a question for you", name)
            "notes" -> trf("%s needs your notes", name)
            "coffee" -> trf("%s wants to get coffee with you", name)
            else -> trf("%s poked you", name)
        }
    }

    fun ago(iso: String?): String {
        val ms = runCatching { java.time.Instant.parse(iso ?: "").toEpochMilli() }.getOrNull() ?: return ""
        return DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

/** Add friend / Requested / Friends, for a person's row or profile */
@Composable
fun FriendButton(userId: String, state: String, changed: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    fun run(action: String, extra: Map<String, Any?> = emptyMap()) {
        busy = true
        scope.launch {
            try {
                val r = Account.call(action, extra + ("userId" to userId))
                changed(r.str("friendship") ?: "none")
            } catch (e: ApiError) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remove this friend?") },
        confirmButton = { TextButton(onClick = { confirm = false; run("friendRemove") }) { Text("Remove friend", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
    when (state) {
        "friends" -> OutlinedButton(onClick = { confirm = true }, enabled = !busy) { Icon(Icons.Filled.HowToReg, null, Modifier.size(18.dp)); Text(" " + tr("Friends")) }
        "outgoing" -> OutlinedButton(onClick = { run("friendRemove") }, enabled = !busy) { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp)); Text(" " + tr("Requested")) }
        "incoming" -> Button(onClick = { run("friendRespond", mapOf("accept" to true)) }, enabled = !busy) { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Text(" " + tr("Accept")) }
        else -> Button(onClick = { run("friendRequest") }, enabled = !busy) { Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp)); Text(" " + tr("Add friend")) }
    }
}

/** "Poke" and the other short messages friends can send each other */
@Composable
fun PokeDialog(user: AbUser, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    var kind by remember { mutableStateOf("poke") }
    var place by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(user.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Social.pokeKinds.forEach { (id, label, emoji) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { kind = id }.padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(emoji)
                        Text(label, Modifier.weight(1f))
                        if (kind == id) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (kind == "meet") OutlinedTextField(
                    place, { if (it.length <= 120) place = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Where and when? Like: the library at 2:00") },
                )
                Spacer(Modifier.height(6.dp))
                Text(Social.pokeText(kind, me?.name ?: "", place), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        Account.call("poke", mapOf("userId" to user.id, "kind" to kind, "place" to if (kind == "meet") place else ""))
                        Toast.makeText(ctx, tr("Sent"), Toast.LENGTH_SHORT).show()
                        onClose()
                    } catch (e: ApiError) { error = e.message }
                    busy = false
                }
            }) { Text(if (busy) "Sending…" else "Send") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
fun ComposePostDialog(onClose: () -> Unit, posted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var audience by remember { mutableStateOf(AudiencePref.value) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photo = uri
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("New post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    text, { if (it.length <= 1000) text = it }, minLines = 5, modifier = Modifier.fillMaxWidth(),
                    label = { Text("What's on your mind?") }, supportingText = { Text("${text.length}/1000") },
                )
                photo?.let {
                    coil.compose.AsyncImage(it, null, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Row {
                    TextButton(onClick = { pick.launch("image/*") }) { Text(tr(if (photo == null) "Add a photo" else "Change photo")) }
                    if (photo != null) TextButton(onClick = { photo = null }) { Text(tr("Remove photo"), color = MaterialTheme.colorScheme.error) }
                }
                AudienceMenu(audience) { audience = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (text.isNotBlank() || photo != null), onClick = {
                busy = true
                scope.launch {
                    try {
                        val fields = mutableMapOf<String, Any?>("text" to text, "audience" to audience)
                        photo?.let { fields["image"] = PhotoTools.jpeg(ctx, it) ?: throw ApiError(tr("Couldn't load this photo")) }
                        Account.call("postCreate", fields)
                        posted()
                        onClose()
                    } catch (e: ApiError) { error = e.message }
                    busy = false
                }
            }) { Text(if (busy) "Posting…" else "Post") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
fun PostCard(post: FeedPost, changed: () -> Unit) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    var liked by remember(post.id, post.liked) { mutableStateOf(post.liked) }
    var likes by remember(post.id, post.likes) { mutableIntStateOf(post.likes) }
    var commentCount by remember(post.id, post.comments) { mutableIntStateOf(post.comments) }
    var showComments by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<ReportTarget?>(null) }
    var blocking by remember { mutableStateOf<BlockTarget?>(null) }
    reporting?.let { t -> ReportDialog(t) { reporting = null } }
    blocking?.let { t -> BlockDialog(t, { blocking = null; changed() }) }
    if (showComments) CommentsDialog(post, { commentCount = it }) { showComments = false }
    Group {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable { if (me == null) Account.signInShown.value = true else nav.navigate("profile/${post.user.id}") },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(post.user.name, post.user.color, 38.dp, post.user.isAdmin, post.user.avatar, post.user.online)
                    Column {
                        Text(post.user.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(Social.ago(post.created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(audienceIcon(post.audience), null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (me != null) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(menu, { menu = false }) {
                        if (post.user.id == me?.id || Account.isAdmin) DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            menu = false
                            scope.launch { runCatching { Account.call("postDelete", mapOf("id" to post.id)) }; changed() }
                        })
                        if (post.user.id != me?.id) {
                            DropdownMenuItem(text = { Text("Report") }, onClick = { menu = false; reporting = ReportTarget("post", post.id, post.text) })
                            if (!post.user.isAdmin) DropdownMenuItem(text = { Text("Block") }, onClick = { menu = false; blocking = BlockTarget(post.user.id, post.user.name) })
                        }
                    }
                }
            }
            if (post.text.isNotEmpty()) Text(post.text)
            if (post.image.isNotEmpty()) coil.compose.AsyncImage(
                post.image, tr("View photo"),
                Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { PhotoPeek.show(post.image) },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable {
                    if (me == null) { Account.signInShown.value = true; return@clickable }
                    val on = !liked
                    liked = on
                    likes += if (on) 1 else -1
                    scope.launch { runCatching { Account.call("postLike", mapOf("id" to post.id, "on" to on)) } }
                }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (liked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text("$likes", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { if (me == null) Account.signInShown.value = true else showComments = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.ChatBubbleOutline, tr("Comments"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text("$commentCount", style = MaterialTheme.typography.bodySmall)
            }
            }
        }
    }
}

@Composable
private fun PersonRow(u: AbUser, trailing: @Composable () -> Unit) {
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().clickable { nav.navigate("profile/${u.id}") }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(u.name, u.color, 42.dp, u.isAdmin, u.avatar, u.online)
        Column(Modifier.weight(1f)) {
            Text(u.name, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(u.major?.ifEmpty { null } ?: u.college?.ifEmpty { null } ?: "@${u.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing()
    }
}

/** The Friends tab: posts from friends, friends, requests and pokes, and finding people */
@Composable
fun FriendsScreen(back: Boolean = false) {
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsState()
    var segment by remember { mutableIntStateOf(0) }
    var friends by remember { mutableStateOf<List<AbUser>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<AbUser>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<AbUser>>(emptyList()) }
    var pokes by remember { mutableStateOf<List<Poke>>(emptyList()) }
    var feed by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var people by remember { mutableStateOf<List<AbUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var poking by remember { mutableStateOf<AbUser?>(null) }
    var composing by remember { mutableStateOf(false) }

    suspend fun loadFeed() { runCatching { feed = Account.callSaving("feed")["posts"].decodeAs<List<FeedPost>>().orEmpty() } }
    fun readFriends(r: kotlinx.serialization.json.JsonObject) {
        friends = r["friends"].decodeAs<List<AbUser>>().orEmpty()
        incoming = r["incoming"].decodeAs<List<AbUser>>().orEmpty()
        outgoing = r["outgoing"].decodeAs<List<AbUser>>().orEmpty()
        pokes = r["pokes"].decodeAs<List<Poke>>().orEmpty()
    }
    suspend fun load() {
        // what we knew last time, straight away
        if (loading) {
            Account.saved("friends")?.let { readFriends(it); loading = false }
            Account.saved("feed")?.let { feed = it["posts"].decodeAs<List<FeedPost>>().orEmpty() }
        }
        // the feed doesn't hold up the friends list
        scope.launch { loadFeed() }
        try {
            readFriends(Account.callSaving("friends"))
            error = null
        } catch (e: ApiError) { error = e.message }
        loading = false
    }
    suspend fun search() {
        try { people = Account.call("searchUsers", mapOf("q" to query.trim()))["people"].decodeAs<List<AbUser>>().orEmpty(); error = null }
        catch (e: ApiError) { error = e.message }
    }
    LaunchedEffect(user?.id) { ChatNotifier.socialUnread.value = 0; if (user != null) load() }
    LaunchedEffect(segment) {
        if (segment == 3 && people.isEmpty() && user != null) search()
        if (segment == 2 && pokes.any { it.read != true }) runCatching { Account.call("pokesRead") }
    }
    poking?.let { u -> PokeDialog(u) { poking = null } }
    if (composing) ComposePostDialog({ composing = false }) { scope.launch { loadFeed() } }
    val alerts = incoming.size + pokes.count { it.read != true }

    Screen("Friends", back = back, large = !back, actions = {
        if (user != null) IconButton(onClick = { loading = true; scope.launch { load() } }) { Icon(Icons.Filled.Refresh, null) }
    }) { pad ->
        if (user == null) {
            Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                Icon(Icons.Filled.People, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Text("Add your classmates as friends", fontWeight = FontWeight.SemiBold)
                Text("Sign in to add friends, poke them and share posts.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { Account.signInShown.value = true }) { Text("Sign in") }
            }
            return@Screen
        }
        CommunityRulesGate()
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(segment == 0, { segment = 0 }, label = { Text("Feed") })
                    FilterChip(segment == 1, { segment = 1 }, label = { Text("Friends") })
                    FilterChip(segment == 2, { segment = 2 }, label = { Text(if (alerts > 0) "${tr("Alerts")} ($alerts)" else tr("Alerts")) })
                    FilterChip(segment == 3, { segment = 3 }, label = { Text("Find") })
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            when (segment) {
                0 -> {
                    item { Group { ListRow("Write a post", icon = Icons.Filled.Edit) { composing = true } } }
                    if (feed.isEmpty() && !loading) item { EmptyState(Icons.Filled.DynamicFeed, "No posts yet", "Add friends or write the first one.") }
                    items(feed, key = { it.id }) { p -> PostCard(p) { scope.launch { loadFeed() } } }
                }
                1 -> {
                    if (friends.isEmpty() && !loading) item {
                        EmptyState(Icons.Filled.People, "No friends yet", "You haven't added any friends yet.")
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(onClick = { segment = 3 }) { Text("Find people") } }
                    }
                    if (friends.isNotEmpty()) item {
                        Group(header = trf("%d friends", friends.size)) {
                            friends.forEachIndexed { i, u ->
                                if (i > 0) Divider()
                                PersonRow(u) {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable { poking = u },
                                        contentAlignment = Alignment.Center,
                                    ) { Text("👉", fontSize = 18.sp) }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (incoming.isEmpty() && pokes.isEmpty() && outgoing.isEmpty() && !loading) item { EmptyState(Icons.Filled.NotificationsNone, "Nothing new", "Friend requests and pokes show up here.") }
                    if (incoming.isNotEmpty()) item {
                        Group(header = "Friend requests") {
                            incoming.forEachIndexed { i, u ->
                                if (i > 0) Divider()
                                PersonRow(u) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledIconButton(onClick = { scope.launch { runCatching { Account.call("friendRespond", mapOf("userId" to u.id, "accept" to true)) }; load() } }) { Icon(Icons.Filled.Check, tr("Accept")) }
                                        FilledTonalIconButton(onClick = { scope.launch { runCatching { Account.call("friendRespond", mapOf("userId" to u.id, "accept" to false)) }; load() } }) { Icon(Icons.Filled.Close, tr("Decline")) }
                                    }
                                }
                            }
                        }
                    }
                    if (pokes.isNotEmpty()) item {
                        Group(header = "Pokes and messages") {
                            pokes.forEachIndexed { i, p ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Avatar(p.user.name, p.user.color, 38.dp, false, p.user.avatar)
                                    Column(Modifier.weight(1f)) {
                                        Text(Social.pokeText(p.kind, p.user.name, p.place), fontWeight = if (p.read == true) FontWeight.Normal else FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(Social.ago(p.created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { poking = p.user }) { Text("Reply") }
                                }
                            }
                        }
                    }
                    if (outgoing.isNotEmpty()) item {
                        Group(header = "Requests you sent") {
                            outgoing.forEachIndexed { i, u ->
                                if (i > 0) Divider()
                                PersonRow(u) { FriendButton(u.id, "outgoing") { scope.launch { load() } } }
                            }
                        }
                    }
                }
                else -> {
                    item {
                        OutlinedTextField(
                            query, { query = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("Name, username, major or college") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { scope.launch { search() } }),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        if (query.isEmpty()) Text(
                            "People from your college and major show up here. Add them to your profile to see more.",
                            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (people.isEmpty() && !loading) item { EmptyState(Icons.Filled.SearchOff, "No people found", "Try another name.") }
                    if (people.isNotEmpty()) item {
                        Group {
                            people.forEachIndexed { i, u ->
                                if (i > 0) Divider()
                                PersonRow(u) {
                                    FriendButton(u.id, u.friendship ?: "none") { s -> people = people.map { if (it.id == u.id) it.copy(friendship = s) else it } }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** A person's page: photo, counts, friend and poke buttons, studies and posts */
@Composable
fun ProfileScreen(userId: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    var user by remember { mutableStateOf<AbUser?>(null) }
    var friendship by remember { mutableStateOf("none") }
    var canMessage by remember { mutableStateOf(false) }
    var friendCount by remember { mutableIntStateOf(0) }
    var postCount by remember { mutableIntStateOf(0) }
    var messageCount by remember { mutableIntStateOf(0) }
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf<ReportTarget?>(null) }
    var blocking by remember { mutableStateOf<BlockTarget?>(null) }
    var poking by remember { mutableStateOf<AbUser?>(null) }
    var composing by remember { mutableStateOf(false) }
    var coverBusy by remember { mutableStateOf(false) }
    var coverMenu by remember { mutableStateOf(false) }

    fun readProfile(r: kotlinx.serialization.json.JsonObject) {
        user = r["user"].decodeAs<AbUser>()
        friendship = r.str("friendship") ?: "none"
        canMessage = r.bool("canMessage")
        friendCount = r.str("friendCount")?.toDoubleOrNull()?.toInt() ?: 0
        postCount = r.str("postCount")?.toDoubleOrNull()?.toInt() ?: 0
        messageCount = r.str("messageCount")?.toDoubleOrNull()?.toInt() ?: 0
        posts = r["posts"].decodeAs<List<FeedPost>>().orEmpty()
    }
    suspend fun load() {
        if (user == null) Account.saved("profile", userId)?.let { readProfile(it) }
        try {
            readProfile(Account.callSaving("profile", mapOf("userId" to userId, "social" to true), userId))
            error = null
        } catch (e: ApiError) { error = e.message }
    }
    LaunchedEffect(userId) { load() }
    fun setCover(uri: Uri?) {
        coverBusy = true
        scope.launch {
            try {
                Account.setCover(uri?.let { PhotoTools.jpeg(ctx, it) ?: throw ApiError(tr("Couldn't load this photo")) })
                load()
            } catch (e: ApiError) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
            coverBusy = false
        }
    }
    val pickCover = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) setCover(uri)
    }
    reporting?.let { t -> ReportDialog(t) { reporting = null } }
    blocking?.let { t -> BlockDialog(t, { blocking = null; scope.launch { load() } }) }
    poking?.let { u -> PokeDialog(u) { poking = null } }
    if (composing) ComposePostDialog({ composing = false }) { scope.launch { load() } }
    val isMe = userId == me?.id

    Screen(user?.name ?: tr("Profile")) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                val u = user
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val c = hexColor(u?.color ?: "#8C1D40")
                    Box(Modifier.fillMaxWidth().height(202.dp)) {
                        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(c, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)))))
                        val cover = u?.cover.orEmpty()
                        if (cover.isNotEmpty()) coil.compose.AsyncImage(
                            cover, tr("View cover photo"),
                            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)).clickable { PhotoPeek.show(cover) },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            // tall photos show their upper part, where faces and the sky usually are
                            alignment = androidx.compose.ui.BiasAlignment(0f, -0.5f),
                        )
                        if (isMe) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                            IconButton(
                                onClick = { coverMenu = true }, enabled = !coverBusy,
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                            ) { Icon(Icons.Filled.PhotoCamera, tr("Change cover photo"), tint = Color.White, modifier = Modifier.size(20.dp)) }
                            DropdownMenu(coverMenu, { coverMenu = false }) {
                                DropdownMenuItem(text = { Text(tr("Choose a cover photo")) }, onClick = { coverMenu = false; pickCover.launch("image/*") })
                                if (cover.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Remove cover photo")) }, onClick = { coverMenu = false; setCover(null) })
                            }
                        }
                        // a solid disc behind the photo. A ring drawn around it left a see-through gap, and cut through the star.
                        Box(Modifier.align(Alignment.BottomCenter), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background))
                            Avatar(u?.name ?: "", u?.color ?: "#8C1D40", 96.dp, u?.isAdmin == true, u?.avatar, u?.online)
                        }
                    }
                    if (u == null) {
                        if (error == null) CircularProgressIndicator() else Text(error!!, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(u.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("@${u.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (u.online == true) tr("Online now") else lastSeenText(u.lastSeen), style = MaterialTheme.typography.bodySmall,
                            color = if (u.online == true) Brand.green else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BadgeRow(u.badges)
                        if (u.isAdmin) Badge(tr("ADMIN"), Brand.orange)
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 10.dp)) {
                            listOf(friendCount to "Friends", postCount to "Posts", messageCount to "Messages", u.badges.size to "Badges").forEach { (n, label) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$n", fontWeight = FontWeight.Bold)
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                        if (isMe) {
                            OutlinedButton(onClick = { nav.navigate("account") }) { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Text(" " + tr("Edit profile")) }
                        } else if (me != null && u.id !in me?.blocked.orEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FriendButton(u.id, friendship) { s -> friendship = s; scope.launch { load() } }
                                if (friendship == "friends") Button(onClick = { poking = u }) { Text("👉 " + tr("Poke")) }
                            }
                            if (canMessage) OutlinedButton(onClick = { nav.navigate("dm/${u.id}") }) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                                Text(" " + tr("Send a message"))
                            }
                        }
                    }
                }
            }
            user?.let { u ->
                if (!u.bio.isNullOrEmpty()) item { Group(header = tr("About")) { Text(u.bio, Modifier.padding(16.dp)) } }
                if (!u.college.isNullOrEmpty() || !u.major.isNullOrEmpty() || !u.year.isNullOrEmpty()) item {
                    Group(header = "Studies") {
                        if (!u.college.isNullOrEmpty()) ListRow(u.college, tr("College"), icon = Icons.Filled.AccountBalance)
                        if (!u.major.isNullOrEmpty()) { Divider(); ListRow(u.major, tr("Major"), icon = Icons.Filled.School) }
                        if (!u.year.isNullOrEmpty()) { Divider(); ListRow(tr(u.year), tr("Year"), icon = Icons.Filled.CalendarMonth) }
                    }
                }
                if (!u.interests.isNullOrEmpty()) item { Group(header = "Interests") { Text(u.interests, Modifier.padding(16.dp)) } }
                if (!u.phone.isNullOrEmpty()) item {
                    Group(header = tr("Phone")) {
                        ListRow(u.phone, icon = Icons.Filled.Phone) {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + u.phone.filter { it.isDigit() || it == '+' }))) }
                        }
                        Divider()
                        ListRow("WhatsApp", emoji = "💬") {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + u.phone.filter { it.isDigit() }))) }
                        }
                    }
                }
                if (isMe) item { Group { ListRow("Write a post", icon = Icons.Filled.Edit) { composing = true } } }
                items(posts, key = { it.id }) { p -> PostCard(p) { scope.launch { load() } } }
                if (me != null && !isMe) item {
                    Group {
                        ListRow(tr("Report this person"), icon = Icons.Filled.Flag) { reporting = ReportTarget("user", u.id, u.name) }
                        if (!u.isAdmin) {
                            Divider()
                            if (u.id in me?.blocked.orEmpty()) ListRow(tr("Unblock"), icon = Icons.Filled.Block) { scope.launch { runCatching { Moderation.setBlocked(u.id, false) } } }
                            else ListRow(tr("Block"), icon = Icons.Filled.Block, titleColor = MaterialTheme.colorScheme.error) { blocking = BlockTarget(u.id, u.name) }
                        }
                    }
                }
                if (Account.isAdmin && !isMe) item {
                    Group { ListRow(tr("Manage this user"), icon = Icons.Filled.Shield) { AdminSelection.user = u; nav.navigate("adminUser") } }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
