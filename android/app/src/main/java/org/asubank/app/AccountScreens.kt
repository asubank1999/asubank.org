package org.asubank.app

import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Block
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** A round avatar: the profile photo, or the first letter on the user's color. `online` adds a green or grey dot. */
@Composable
fun Avatar(name: String, color: String, size: Dp = 40.dp, admin: Boolean = false, photo: String? = null, online: Boolean? = null) {
    Box {
        Box(Modifier.size(size).clip(CircleShape).background(hexColor(color)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
            if (!photo.isNullOrEmpty()) AsyncImage(photo, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        if (admin) Box(
            Modifier.align(Alignment.BottomEnd).size(size * 0.4f).clip(CircleShape).background(Brand.gold),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Shield, null, tint = Color.Black, modifier = Modifier.size(size * 0.26f)) }
        if (online != null) Box(
            Modifier.align(Alignment.TopEnd).size(maxOf(10.dp, size * 0.26f)).clip(CircleShape)
                .background(if (online) Brand.green else Color.Gray)
                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
        )
    }
}

/** A user's badges: "🤝 Helper" pills, or just the emoji when compact */
@Composable
fun BadgeRow(badges: List<String>, compact: Boolean = false) {
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        badges.forEach { b ->
            if (compact) Text(Badges.emoji(b), fontSize = 11.sp)
            else Text(
                "${Badges.emoji(b)} ${Badges.name(b)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** "Last seen 5 minutes ago" */
fun lastSeenText(iso: String?): String {
    val t = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return tr("Offline")
    val m = Duration.between(t, Instant.now()).toMinutes()
    val ago = when {
        m < 1 -> tr("just now")
        m < 60 -> tr("%d min ago").format(m)
        m < 60 * 24 -> tr("%d h ago").format(m / 60)
        else -> tr("%d days ago").format(m / (60 * 24))
    }
    return tr("Last seen %s").format(ago)
}

/** Shrinks a picked photo to a 256 px square JPEG, as base64 */
fun avatarJpeg(ctx: android.content.Context, uri: Uri): String? = runCatching {
    val src = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
    val side = minOf(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val small = Bitmap.createScaledBitmap(square, 256, 256, true)
    val out = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 82, out)
    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

@Composable
fun SwitchRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

/** Someone's profile: photo, badges, bio, online or last seen, phone if they share it */
/** Which rooms may notify about new messages */
@Composable
fun MutedRoomsScreen() {
    val rooms by Account.rooms.collectAsState()
    val muted by ChatNotifier.muted.collectAsState()
    Screen(tr("Rooms")) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(footer = tr("Turn a room off to stop its message notifications. Replies to you still notify if that's on.")) {
                    rooms.filter { it.archived != true && it.adminOnly != true }.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        SwitchRow("${r.emoji} ${r.name}", null, r.id !in muted) { on -> ChatNotifier.setMuted(r.id, !on) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, hint: String? = null) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        supportingText = hint?.let { { Text(it) } },
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
        trailingIcon = if (password) { { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show password") } } } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Sign in / create an account (username and password; university email and ID are optional) */
@Composable
fun SignInDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var uniId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var needCode by remember { mutableStateOf(false) }
    var codeSentTo by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null
        if (mode == 1 && password != confirm) { error = "The passwords don't match."; return }
        busy = true
        scope.launch {
            try {
                if (mode == 0) Account.signIn(login, password)
                else Account.register(login, password, displayName, email, uniId, code)
                onClose()
            } catch (e: ApiError) {
                error = e.message
                if (e.needCode) needCode = true
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)))
                    Text(if (mode == 0) "Welcome back" else "Join ASU Bank", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "An account lets you chat with other students and ask for help. It works on the app and on asubank.org.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Sign in", "Create account").forEachIndexed { i, l ->
                            SegmentedButton(selected = mode == i, onClick = { mode = i; error = null }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                        }
                    }
                    Field(login, { login = it }, if (mode == 0) "Username or email" else "Username", hint = if (mode == 1) "3–20 letters, numbers, . or _" else null)
                    Field(password, { password = it }, "Password", password = true, hint = if (mode == 1) "At least 6 characters" else null)
                    if (mode == 1) {
                        Field(confirm, { confirm = it }, "Confirm password", password = true)
                        Field(displayName, { displayName = it }, "Display name (optional)")
                        Field(email, { email = it }, "University email (optional)", keyboard = KeyboardType.Email)
                        Field(uniId, { uniId = it }, "University ID (optional)", keyboard = KeyboardType.Number)
                        if (needCode) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Admin verification", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        codeSentTo?.let { trf("We sent a 6-digit code to %s.", it) } ?: "This name belongs to an ASU Bank admin. Send a code to the owner's email to prove it's you.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Field(code, { code = it }, "6-digit code", keyboard = KeyboardType.Number)
                                    TextButton(onClick = {
                                        scope.launch {
                                            try { codeSentTo = Account.sendAdminCode(login, email, uniId) } catch (e: ApiError) { error = e.message }
                                        }
                                    }) { Text(if (codeSentTo == null) "Send the code" else "Send again") }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                    Button(
                        onClick = ::submit, enabled = !busy && login.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(if (mode == 0) "Sign in" else "Create account")
                    }
                    if (mode == 0) Text(
                        "Forgot your password? Ask an admin in the chat or on WhatsApp to reset it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Shown in place of signed-in-only screens */
@Composable
fun SignInPrompt(title: String = "Sign in to chat", text: String = "Talk with other ASU students, ask for help and share materials. Chat is only for signed-in students.") {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { Account.signInShown.value = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Sign in or create an account") }
    }
}

/** More > your account */
@Composable
fun AccountScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsState()
    val nMessages by ChatNotifier.messages.collectAsState()
    val nReplies by ChatNotifier.replies.collectAsState()
    val nAnn by ChatNotifier.announcements.collectAsState()
    val nUploads by ChatNotifier.uploads.collectAsState()
    val nSocial by ChatNotifier.social.collectAsState()
    val muted by ChatNotifier.muted.collectAsState()
    var photoMenu by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            photoBusy = true
            val data = avatarJpeg(ctx, uri)
            try { if (data != null) Account.setAvatar(data) } catch (e: ApiError) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
            photoBusy = false
        }
    }
    var editing by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var confirmOut by remember { mutableStateOf(false) }

    Screen("Account") { pad ->
        val u = user
        if (u == null) { Box(Modifier.padding(pad)) { SignInPrompt("Not signed in", "Sign in to chat with other students and keep your account in sync with asubank.org.") }; return@Screen }
        LazyColumn(Modifier.padding(pad)) {
            item {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        Box(Modifier.clip(CircleShape).clickable { photoMenu = true }) { Avatar(u.name, u.color, 96.dp, u.isAdmin, u.avatar) }
                        Box(
                            Modifier.align(Alignment.BottomStart).size(30.dp).clip(CircleShape).background(accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (photoBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            else Icon(Icons.Filled.CameraAlt, tr("Change photo"), tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        DropdownMenu(photoMenu, { photoMenu = false }) {
                            DropdownMenuItem(text = { Text("Choose a photo") }, onClick = { photoMenu = false; pick.launch("image/*") })
                            if (!u.avatar.isNullOrEmpty()) DropdownMenuItem(text = { Text("Remove photo") }, onClick = {
                                photoMenu = false
                                scope.launch { photoBusy = true; runCatching { Account.setAvatar(null) }; photoBusy = false }
                            })
                        }
                    }
                    Text(u.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("@${u.username}" + if (u.isAdmin) " · " + tr("Admin") else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BadgeRow(u.badges)
                    if (!u.bio.isNullOrEmpty()) Text(u.bio, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Group(header = "Profile") {
                    ListRow("Display name", icon = Icons.Filled.Edit, trailing = u.displayName.ifEmpty { "–" }) { editing = true }
                    Divider()
                    ListRow("University email", trailing = u.email?.ifEmpty { null } ?: "–") { editing = true }
                    Divider()
                    ListRow("University ID", trailing = u.uniId?.ifEmpty { null } ?: "–") { editing = true }
                    Divider()
                    ListRow("Phone", trailing = u.phone?.ifEmpty { null }?.let { it + if (u.showPhone == true) "" else " 🔒" } ?: "–") { editing = true }
                    Divider()
                    ListRow("About", trailing = u.bio?.ifEmpty { null }?.take(24) ?: "–") { editing = true }
                    Divider()
                    ListRow("College", trailing = u.college?.ifEmpty { null }?.let { tr(it) } ?: "–") { editing = true }
                    Divider()
                    ListRow("Major", trailing = u.major?.ifEmpty { null }?.take(24) ?: "–") { editing = true }
                }
            }
            item {
                Group(header = "Notifications", footer = "Android checks about every 15 minutes, right when you open the app, and every few seconds while it's open.") {
                    SwitchRow(tr("New chat messages"), Icons.Filled.Forum, nMessages) { ChatNotifier.set("messages", it) }
                    Divider()
                    SwitchRow(tr("Replies to me"), Icons.AutoMirrored.Filled.Reply, nReplies) { ChatNotifier.set("replies", it) }
                    Divider()
                    SwitchRow(tr("Announcements"), Icons.Filled.Campaign, nAnn) { ChatNotifier.set("announcements", it) }
                    Divider()
                    SwitchRow(tr("New uploaded files"), Icons.Filled.UploadFile, nUploads) { ChatNotifier.set("uploads", it) }
                    Divider()
                    SwitchRow(tr("Friends and pokes"), Icons.Filled.People, nSocial) { ChatNotifier.set("social", it) }
                    Divider()
                    ListRow("Notification sounds", icon = Icons.Filled.MusicNote) { nav.navigate("sounds") }
                    if (nMessages) {
                        Divider()
                        ListRow("Rooms", icon = Icons.Filled.Tag, trailing = if (muted.isEmpty()) tr("All") else tr("%d muted").format(muted.size)) { nav.navigate("mutedRooms") }
                    }
                    if (!ChatNotifier.canNotify(ctx)) {
                        Divider()
                        ListRow(tr("Notifications are off for ASU Bank"), tr("Tap to turn them on in Android settings"), icon = Icons.Filled.Notifications, titleColor = MaterialTheme.colorScheme.error) {
                            runCatching {
                                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                            }
                        }
                    }
                }
            }
            item {
                Group(header = "Security") {
                    ListRow("Change password", icon = Icons.Filled.Key) { changing = true }
                    Divider()
                    ListRow("Blocked people", icon = Icons.Filled.Block) { nav.navigate("blocked") }
                    Divider()
                    ListRow("Sign out", icon = Icons.Filled.Logout) { confirmOut = true }
                    Divider()
                    ListRow("Delete account", icon = Icons.Filled.Delete, titleColor = MaterialTheme.colorScheme.error) { deleting = true }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (editing) user?.let { u ->
        var name by remember { mutableStateOf(u.displayName) }
        var email by remember { mutableStateOf(u.email ?: "") }
        var uni by remember { mutableStateOf(u.uniId ?: "") }
        var phone by remember { mutableStateOf(u.phone ?: "") }
        var showPhone by remember { mutableStateOf(u.showPhone == true) }
        var bio by remember { mutableStateOf(u.bio ?: "") }
        var major by remember { mutableStateOf(u.major ?: "") }
        var college by remember { mutableStateOf(u.college ?: "") }
        var year by remember { mutableStateOf(u.year ?: "") }
        var yearOpen by remember { mutableStateOf(false) }
        var interests by remember { mutableStateOf(u.interests ?: "") }
        var collegeOpen by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit profile") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(name, { name = it }, "Display name")
                    Field(bio, { if (it.length <= 160) bio = it }, "A line about you (optional)")
                    androidx.compose.foundation.layout.Box {
                        androidx.compose.material3.OutlinedButton(onClick = { collegeOpen = true }, Modifier.fillMaxWidth()) {
                            Text(tr("College") + ": " + (if (college.isEmpty()) tr("Not set") else tr(college)))
                        }
                        androidx.compose.material3.DropdownMenu(collegeOpen, { collegeOpen = false }) {
                            (listOf("") + Colleges).forEach { c ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(if (c.isEmpty()) "Not set" else c) }, onClick = { college = c; collegeOpen = false })
                            }
                        }
                    }
                    Field(major, { if (it.length <= 60) major = it }, "Your major")
                    androidx.compose.foundation.layout.Box {
                        androidx.compose.material3.OutlinedButton(onClick = { yearOpen = true }, Modifier.fillMaxWidth()) {
                            Text(tr("Year") + ": " + (if (year.isEmpty()) tr("Not set") else tr(year)))
                        }
                        androidx.compose.material3.DropdownMenu(yearOpen, { yearOpen = false }) {
                            (listOf("") + StudyYears).forEach { c ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(if (c.isEmpty()) "Not set" else c) }, onClick = { year = c; yearOpen = false })
                            }
                        }
                    }
                    Field(interests, { if (it.length <= 160) interests = it }, "Football, coding, photography…")
                    Field(phone, { phone = it }, "Phone number (optional)", keyboard = KeyboardType.Phone)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show my number on my profile", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(showPhone, { showPhone = it })
                    }
                    Field(email, { email = it }, "University email (optional)", keyboard = KeyboardType.Email)
                    Field(uni, { uni = it }, "University ID (optional)", keyboard = KeyboardType.Number)
                    Text("Only admins see your email, ID and number (unless you show the number).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { try { Account.updateProfile(name, email, uni, phone, showPhone, bio, major, college, year, interests); editing = false } catch (e: ApiError) { err = e.message } } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    if (changing) {
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { changing = false },
            title = { Text("Change password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(old, { old = it }, "Current password", password = true)
                    Field(new, { new = it }, "New password", password = true, hint = "At least 6 characters")
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { Account.changePassword(old, new); changing = false; Toast.makeText(ctx, "Password changed", Toast.LENGTH_SHORT).show() } catch (e: ApiError) { err = e.message }
                    }
                }) { Text("Change") }
            },
            dismissButton = { TextButton(onClick = { changing = false }) { Text("Cancel") } },
        )
    }
    if (deleting) {
        var pw by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your account and profile are removed. This can't be undone.")
                    Field(pw, { pw = it }, "Password", password = true)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { try { Account.deleteAccount(pw); deleting = false; nav.popBackStack() } catch (e: ApiError) { err = e.message } } }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
    if (confirmOut) AlertDialog(
        onDismissRequest = { confirmOut = false },
        title = { Text("Sign out?") },
        confirmButton = { TextButton(onClick = { confirmOut = false; scope.launch { Account.signOut() } }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmOut = false }) { Text("Cancel") } },
    )
}
