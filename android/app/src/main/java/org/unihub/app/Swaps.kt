package org.unihub.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** One listing. Contact details never come with it: they arrive only through swapContact. */
@Serializable
data class SwapListing(
    val id: String = "",
    val have: String = "",
    val want: String = "",
    val section: String = "",
    val note: String = "",
    val name: String = "",
    val major: String = "",
    val status: String = "open",
    val created: String = "",
    val mine: Boolean = false,
) {
    /** True when this student has what the other wants and wants what the other has */
    fun matches(o: SwapListing) = have.equals(o.want, ignoreCase = true) && want.equals(o.have, ignoreCase = true)
}

@Serializable
data class SwapContact(
    val fullName: String = "",
    val studentId: String = "",
    val major: String = "",
    val phone: String = "",
    val email: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapsScreen(back: Boolean = true) {
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsState()
    var swaps by remember { mutableStateOf<List<SwapListing>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<SwapListing?>(null) }

    suspend fun load() {
        try {
            swaps = Account.call("swapList")["swaps"].decodeAs<List<SwapListing>>().orEmpty()
            error = null
        } catch (e: ApiError) { error = e.message }
        loading = false
    }
    LaunchedEffect(user?.id) { load() }

    val mine = swaps.filter { it.mine }
    val openMine = mine.filter { it.status == "open" }
    fun isMatch(s: SwapListing) = openMine.any { it.matches(s) }
    val q = query.trim().lowercase()
    val others = swaps.filter { !it.mine && (q.isEmpty() || "${it.have} ${it.want} ${it.major}".lowercase().contains(q)) }
        .sortedByDescending { isMatch(it) }

    if (adding) AddSwapDialog({ adding = false }) { swaps = listOf(it) + swaps }
    opened?.let { s ->
        SwapDetailDialog(s, { opened = null }) { status ->
            swaps = if (status == "deleted") swaps.filter { it.id != s.id } else swaps.map { if (it.id == s.id) it.copy(status = status) else it }
        }
    }

    Screen("Subject swap", back = back, actions = {
        IconButton(onClick = { loading = true; scope.launch { load() } }) { Icon(Icons.Filled.Refresh, null) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Trade a registered subject with another student. Say what you have and what you want."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { if (user != null) adding = true else Account.signInShown.value = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("Post a swap"))
                    }
                    OutlinedTextField(query, { query = it }, label = { Text(tr("Search subjects")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (loading && swaps.isEmpty()) items(4) { SkeletonRow() }
            if (mine.isNotEmpty()) {
                item { SectionTitle(tr("Your swaps")) }
                items(mine, key = { "m" + it.id }) { SwapRow(it, false) { opened = it } }
            }
            item { SectionTitle(tr("Students offering")) }
            if (!loading && others.isEmpty()) item {
                Text(tr("No swaps here yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(others, key = { it.id }) { SwapRow(it, isMatch(it)) { opened = it } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SwapRow(s: SwapListing, match: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (match) Text(tr("Matches your swap"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (s.status == "done") Text(tr("Done"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.have, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.SwapHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.want, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
        }
        Text(listOf(s.name, s.major, s.section).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun AddSwapDialog(onClose: () -> Unit, added: (SwapListing) -> Unit) {
    val scope = rememberCoroutineScope()
    var have by remember { mutableStateOf("") }
    var want by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var major by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = have.isNotBlank() && want.isNotBlank() && fullName.isNotBlank() && studentId.isNotBlank() && (phone.isNotBlank() || email.isNotBlank())

    @Composable
    fun box(label: String, value: String, type: KeyboardType = KeyboardType.Text, change: (String) -> Unit) {
        OutlinedTextField(value, change, label = { Text(tr(label)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth())
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Post a swap")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                box("Subject you have", have) { have = it }
                box("Subject you want", want) { want = it }
                box("Section or time (optional)", section) { section = it }
                box("Note (optional)", note) { note = it }
                Text(tr("Your details"), fontWeight = FontWeight.SemiBold)
                box("Full name", fullName) { fullName = it }
                box("University ID", studentId, KeyboardType.Number) { studentId = it }
                box("Major (optional)", major) { major = it }
                box("Phone number", phone, KeyboardType.Phone) { phone = it }
                box("University or personal email", email, KeyboardType.Email) { email = it }
                Text(tr("Signed-in students can see these when they open your swap. Add a phone number or an email."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (busy) ABDots() else TextButton(enabled = valid, onClick = {
                busy = true
                scope.launch {
                    try {
                        val r = Account.call("swapAdd", mapOf("have" to have.trim(), "want" to want.trim(), "section" to section.trim(), "note" to note.trim(),
                            "fullName" to fullName.trim(), "studentId" to studentId.trim(), "major" to major.trim(), "phone" to phone.trim(), "email" to email.trim()))
                        r["swap"].decodeAs<SwapListing>()?.let(added)
                        onClose()
                    } catch (e: ApiError) { error = e.message }
                    busy = false
                }
            }) { Text(tr("Post")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun SwapDetailDialog(s: SwapListing, onClose: () -> Unit, changed: (String) -> Unit) {
    val ctx = LocalContext.current
    val user by Account.user.collectAsState()
    var contact by remember { mutableStateOf<SwapContact?>(null) }
    var loading by remember { mutableStateOf(user != null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(s.id) {
        if (user != null) {
            try { contact = Account.call("swapContact", mapOf("id" to s.id))["contact"].decodeAs<SwapContact>() }
            catch (e: ApiError) { error = e.message }
            loading = false
        }
    }
    /** Shows at once and saves behind the scenes */
    fun set(status: String) {
        changed(status)
        onClose()
        BackgroundJobs.scope.launch { runCatching { Account.call("swapSet", mapOf("id" to s.id, "status" to status)) } }
    }
    fun open(uri: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Subject swap")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${tr("Has")}: ${s.have}", fontWeight = FontWeight.SemiBold)
                Text("${tr("Wants")}: ${s.want}", fontWeight = FontWeight.SemiBold)
                if (s.section.isNotEmpty()) Text("${tr("Section or time")}: ${s.section}")
                if (s.note.isNotEmpty()) Text(s.note)
                HorizontalDivider()
                if (user == null) {
                    Text(tr("Sign in to see this student's contact details."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onClose(); Account.signInShown.value = true }) { Text(tr("Sign in or create account")) }
                }
                if (loading) ABDots()
                contact?.let { c ->
                    Text("${tr("Name")}: ${c.fullName}")
                    if (c.studentId.isNotEmpty()) Text("${tr("University ID")}: ${c.studentId}")
                    if (c.major.isNotEmpty()) Text("${tr("Major")}: ${c.major}")
                    if (c.phone.isNotEmpty()) TextButton(onClick = { open("tel:" + c.phone.filter { it.isDigit() || it == '+' }) }) {
                        Icon(Icons.Filled.Phone, null); Spacer(Modifier.width(6.dp)); Text(c.phone)
                    }
                    if (c.email.isNotEmpty()) TextButton(onClick = { open("mailto:" + c.email) }) {
                        Icon(Icons.Filled.Email, null); Spacer(Modifier.width(6.dp)); Text(c.email)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (s.mine || user?.role == "admin") {
                    HorizontalDivider()
                    if (s.status == "open") TextButton(onClick = { set("done") }) { Text(tr("Mark as done")) }
                    else TextButton(onClick = { set("open") }) { Text(tr("Open again")) }
                    TextButton(onClick = { set("deleted") }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } },
    )
}
