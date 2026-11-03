package org.asubank.app

import android.widget.Toast
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
import androidx.compose.material3.Text
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

@Composable
fun Avatar(name: String, color: String, size: Dp = 40.dp, admin: Boolean = false) {
    Box {
        Box(Modifier.size(size).clip(CircleShape).background(hexColor(color)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
        if (admin) Box(
            Modifier.align(Alignment.BottomEnd).size(size * 0.4f).clip(CircleShape).background(Brand.gold),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Shield, null, tint = Color.Black, modifier = Modifier.size(size * 0.26f)) }
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
                                        codeSentTo?.let { "We sent a 6-digit code to $it." } ?: "This name belongs to an ASU Bank admin. Send a code to the owner's email to prove it's you.",
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
    val mode by ChatNotifier.mode.collectAsState()
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
                    Avatar(u.name, u.color, 84.dp, u.isAdmin)
                    Text(u.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("@${u.username}" + if (u.isAdmin) " · Admin" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Group(header = "Profile") {
                    ListRow("Display name", icon = Icons.Filled.Edit, trailing = u.displayName.ifEmpty { "–" }) { editing = true }
                    Divider()
                    ListRow("University email", trailing = u.email?.ifEmpty { null } ?: "–") { editing = true }
                    Divider()
                    ListRow("University ID", trailing = u.uniId?.ifEmpty { null } ?: "–") { editing = true }
                }
            }
            item {
                Group(header = "Notifications", footer = "Android checks for new messages about every 15 minutes, and every few seconds while the app is open.") {
                    listOf("Every new message", "Announcements only", "Off").forEachIndexed { i, l ->
                        if (i > 0) Divider()
                        ListRow(l, icon = if (i == 0) Icons.Filled.Notifications else null, trailing = if (mode == i) "✓" else null) { ChatNotifier.setMode(i) }
                    }
                }
            }
            item {
                Group(header = "Security") {
                    ListRow("Change password", icon = Icons.Filled.Key) { changing = true }
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
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(name, { name = it }, "Display name")
                    Field(email, { email = it }, "University email (optional)", keyboard = KeyboardType.Email)
                    Field(uni, { uni = it }, "University ID (optional)", keyboard = KeyboardType.Number)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { try { Account.updateProfile(name, email, uni); editing = false } catch (e: ApiError) { err = e.message } } }) { Text("Save") } },
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
