package org.asubank.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** What a report is about: "message", "user", "file" or "other" */
data class ReportTarget(val kind: String, val targetId: String, val name: String)
data class BlockTarget(val userId: String, val name: String)

object Moderation {
    val reasons = listOf(
        "spam" to "Spam or advertising",
        "harassment" to "Harassment or bullying",
        "hate" to "Hate speech",
        "sexual" to "Sexual content",
        "violence" to "Violence or threats",
        "privacy" to "Shares someone's private information",
        "copyright" to "Copyright: I own this or represent the owner",
        "wrong" to "Wrong, broken or misleading file",
        "other" to "Something else",
    )
    val rules = listOf(
        "Be respectful. No harassment, bullying, threats or hate speech.",
        "No sexual or violent content, and nothing illegal.",
        "Don't share other people's private information.",
        "Only share files you have the right to share.",
        "No spam or advertising.",
        "We have zero tolerance for abuse. Admins can remove content and ban accounts that break these rules.",
    )

    /** Paid services stay out of store builds until they use the store's own payments */
    val paidPaths = setOf("/apps/donate", "/requests/student_ads", "/requests/student_request_portal", "/requests/request", "/requests/student_request_main")

    suspend fun setBlocked(userId: String, on: Boolean) {
        Account.call("block", mapOf("userId" to userId, "on" to on))
        Account.refresh()
    }
}

/** Lets anyone report a message, a person, a file or a problem. Signing in isn't needed. */
@Composable
fun ReportDialog(target: ReportTarget, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { androidx.compose.material3.Icon(Icons.Filled.Flag, null) },
        title = { Text("Report") },
        text = {
            if (sent) Text("Thank you. Our team will review your report.")
            else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (target.name.isNotEmpty()) Text(target.name, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                Moderation.reasons.forEach { (id, label) ->
                    Row(Modifier.fillMaxWidth().clickable { reason = id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reason == id, { reason = id })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(details, { details = it }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
                if (!Account.signedIn) OutlinedTextField(contact, { contact = it }, label = { Text("Your email or phone (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (sent) TextButton(onClick = onClose) { Text("Done") }
            else TextButton(enabled = reason.isNotEmpty() && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        AccountsApi.call(
                            "report",
                            mapOf("kind" to target.kind, "targetId" to target.targetId, "targetName" to target.name, "reason" to reason, "details" to details, "contact" to contact),
                            Account.token,
                        )
                        sent = true
                        error = null
                    } catch (e: ApiError) { error = e.message }
                    busy = false
                }
            }) { Text("Send report") }
        },
        dismissButton = { if (!sent) TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** The "block this person?" question, shared by the chat and profile screens */
@Composable
fun BlockDialog(target: BlockTarget, onClose: () -> Unit, blocked: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(trf("Block %s?", target.name)) },
        text = { Text(error ?: tr("You won't see their messages or get notifications from them. You can undo this in your account.")) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try { Moderation.setBlocked(target.userId, true); blocked(target.userId); onClose() }
                    catch (e: ApiError) { error = e.message }
                }
            }) { Text("Block", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Everyone agrees to the community rules once, before using the chat */
@Composable
fun CommunityRulesGate() {
    var accepted by remember { mutableStateOf(Saved.prefs.getBoolean("chat.rulesAccepted", false)) }
    if (accepted) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Community rules") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Moderation.rules.forEach { Text("• " + tr(it)) }
                Text("To report something, press and hold a message or open a profile.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { Saved.prefs.edit().putBoolean("chat.rulesAccepted", true).apply(); accepted = true }) { Text("I agree") }
        },
    )
}

/** A row in More that reports any problem */
@Composable
fun ReportProblemRow() {
    var open by remember { mutableStateOf(false) }
    ListRow("Report a problem", icon = Icons.Filled.Flag) { open = true }
    if (open) ReportDialog(ReportTarget("other", "", "")) { open = false }
}

/** People you have blocked, with a way to undo it */
@Composable
fun BlockedScreen() {
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try { people = (Account.call("blockedList")["people"] as? JsonArray).objects(); error = null } catch (e: ApiError) { error = e.message }
        loading = false
    }
    LaunchedEffect(Unit) { load() }
    Screen(tr("Blocked people")) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            item {
                Group(footer = if (people.isEmpty()) null else "Tap someone to unblock them.") {
                    if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
                    else if (people.isEmpty()) ListRow("You haven't blocked anyone.")
                    people.forEachIndexed { i, p ->
                        if (i > 0) Divider()
                        ListRow(p.str("displayName") ?: "", "@" + (p.str("username") ?: "")) {
                            scope.launch {
                                try { Moderation.setBlocked(p.str("id") ?: "", false); load() } catch (e: ApiError) { error = e.message }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Admins: what people reported, and what to do about it */
@Composable
fun AdminReportsScreen() {
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try { reports = (Account.call("adminReports", mapOf("all" to showAll))["reports"] as? JsonArray).objects(); error = null } catch (e: ApiError) { error = e.message }
        loading = false
    }
    fun set(r: JsonObject, status: String, deleteMessage: Boolean = false) {
        scope.launch {
            try {
                if (deleteMessage) Account.call("deleteMessage", mapOf("id" to r.str("targetId")))
                Account.call("adminReportSet", mapOf("id" to r.str("id"), "status" to status))
                load()
            } catch (e: ApiError) { error = e.message }
        }
    }
    LaunchedEffect(showAll) { load() }
    Screen(tr("Reports")) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showAll, { showAll = false }, label = { Text("Open") })
                    FilterChip(showAll, { showAll = true }, label = { Text("All") })
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            else if (reports.isEmpty()) item { Text("Nothing to review.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(reports.size) { i ->
                val r = reports[i]
                val label = Moderation.reasons.firstOrNull { it.first == r.str("reason") }?.second ?: (r.str("reason") ?: "")
                Group {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                        r.str("targetName")?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        r.str("targetUser")?.takeIf { it.isNotEmpty() }?.let { Text("@$it", style = MaterialTheme.typography.bodySmall) }
                        r.str("details")?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            listOfNotNull(r.str("reporterName"), r.str("contact")?.ifEmpty { null }, r.str("created")).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (r.str("status") == "open") Row {
                            TextButton(onClick = { set(r, "done") }) { Text("Done") }
                            TextButton(onClick = { set(r, "dismissed") }) { Text("Dismiss") }
                            if (r.str("kind") == "post") TextButton(onClick = {
                                scope.launch {
                                    try {
                                        Account.call("postDelete", mapOf("id" to r.str("targetId")))
                                        set(r, "done")
                                    } catch (e: ApiError) { error = e.message }
                                }
                            }) { Text("Delete post", color = MaterialTheme.colorScheme.error) }
                            if (r.str("kind") == "message") TextButton(onClick = { set(r, "done", deleteMessage = true) }) {
                                Text("Delete message", color = MaterialTheme.colorScheme.error)
                            }
                        } else Text(if (r.str("status") == "done") "Handled" else "Dismissed", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** Where a paid service would be, in a store build */
@Composable
fun StoreNoticeScreen() {
    Screen(tr("ASU Bank")) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("This service isn't available in this version of the app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
