package org.unihub.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/** The dated record that someone accepted the Terms of Service. The server's clock is what counts. */
object TermsProof {
    /** Same as TERMS_VERSION in accounts-api.gs. A new version needs a new TERMS_KEY too. */
    const val VERSION = "2026-11-06"
    private const val DATE_KEY = "terms.date.v2"

    val device: String get() = Saved.prefs.getString("device.id", null) ?: UUID.randomUUID().toString().also { Saved.prefs.edit().putString("device.id", it).apply() }
    val acceptedAt: Long get() = Saved.prefs.getLong(DATE_KEY, 0L)

    fun accepted() {
        Saved.prefs.edit().putLong(DATE_KEY, System.currentTimeMillis()).apply()
        BackgroundJobs.scope.launch { sync() }
    }

    /** Sends the record once for this device, and once for each account that signs in on it */
    suspend fun sync() {
        val at = acceptedAt
        if (at == 0L) return
        val key = "terms.sent.$VERSION." + (Account.user.value?.id ?: "device")
        if (Saved.prefs.getBoolean(key, false)) return
        runCatching {
            AccountsApi.call(
                "termsAccept",
                mapOf("device" to device, "version" to VERSION, "source" to "android", "appVersion" to AppConfig.current, "acceptedAt" to Instant.ofEpochMilli(at).toString()),
                Account.token,
            )
            Saved.prefs.edit().putBoolean(key, true).apply()
        }
    }

    fun dateText(millis: Long): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
}

@Composable
fun SafetyScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val me by Account.user.collectAsState()
    var showTerms by remember { mutableStateOf(false) }
    var serverDate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(me?.id) {
        TermsProof.sync()
        if (Account.signedIn) runCatching {
            val at = (Account.call("termsStatus")["accepted"] as? JsonArray).objects().firstOrNull()?.str("at")
            if (at != null) serverDate = trf("Saved on your account: %s", TermsProof.dateText(Instant.parse(at).toEpochMilli()))
        }
    }
    if (showTerms) WelcomeTerms(onClose = { showTerms = false })
    Screen("Safety & legal") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Group(header = "Feel safe here", footer = "Report anything, inside or outside the app. You can report without an account, and only the UniHub team reads your report.") {
                    ListRow("Report something", icon = Icons.Filled.Flag) { nav.navigate("report") }
                    if (me != null) {
                        Divider(); ListRow("My reports", icon = Icons.Filled.Inbox) { nav.navigate("myReports") }
                        Divider(); ListRow("Blocked people", icon = Icons.Filled.Block) { nav.navigate("blocked") }
                    }
                }
            }
            item {
                Group(header = "Emergency") {
                    ListRow("If someone is in danger right now, call 911. UniHub can't reach emergency services.", icon = Icons.Filled.WarningAmber)
                    Divider()
                    ListRow("Call 911", icon = Icons.Filled.Call) { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))) } }
                }
            }
            item {
                Group(header = "Rules and your rights") {
                    val at = TermsProof.acceptedAt
                    if (at != 0L) {
                        ListRow(trf("You accepted the Terms of Service on %s.", TermsProof.dateText(at)), serverDate, icon = Icons.Filled.VerifiedUser)
                        Divider()
                    }
                    ListRow("About & terms", icon = Icons.Filled.Info) { showTerms = true }
                    Divider(); ListRow("Terms of Service", icon = Icons.Filled.Description) { nav.openPage("/legal/uni_tos", "Terms of Service") }
                    Divider(); ListRow("Privacy policy", icon = Icons.Filled.Policy) { nav.openPage("/legal/privacy", "Privacy policy") }
                    Divider(); ListRow("Community rules", icon = Icons.Filled.VerifiedUser) { nav.openPage("/legal/community", "Community rules") }
                    Divider(); ListRow("Copyright and removal requests", icon = Icons.Filled.Copyright) { nav.openPage("/legal/copyright", "Copyright and removal requests") }
                    if (me != null) {
                        Divider(); ListRow("Delete your account", icon = Icons.Filled.NoAccounts) { nav.openPage("/legal/delete-account", "Delete your account") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The full report form. Works without an account. */
@Composable
fun ReportCenterScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()
    val reasons = listOf(
        "safety" to "Someone's safety is at risk",
        "harassment" to "Harassment or bullying",
        "hate" to "Hate speech",
        "sexual" to "Sexual content",
        "violence" to "Violence or threats",
        "privacy" to "Shares someone's private information",
        "impersonation" to "Pretending to be someone else",
        "scam" to "Scam or fraud",
        "copyright" to "Copyright: I own this or represent the owner",
        "wrong" to "Wrong, broken or misleading file",
        "cheating" to "Cheating or a rule of the university",
        "spam" to "Spam or advertising",
        "bug" to "A bug in the app or website",
        "account" to "A problem with my account",
        "other" to "Something else",
    )
    val places = listOf(
        "app" to "In the app", "website" to "On the website", "whatsapp" to "In a WhatsApp group",
        "campus" to "On campus", "elsewhere" to "Somewhere else",
    )
    var reason by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("app") }
    var what by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var anonymous by remember { mutableStateOf(false) }
    var truthful by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reference by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photo = PhotoTools.load(ctx, uri, 1600)
    }
    val ready = reason.isNotEmpty() && truthful && !busy && details.trim().length >= 10

    Screen("Report something") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            val ref = reference
            if (ref != null) {
                item {
                    Group {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("We got your report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Brand.green)
                            Text("Only the UniHub team reads it. Urgent reports are looked at first.")
                            Text(trf("Reference: %s", ref), fontFamily = FontFamily.Monospace)
                        }
                        Divider()
                        ListRow("Copy link", icon = Icons.Filled.Description) {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Reference", ref))
                        }
                        if (me != null) {
                            Divider(); ListRow("My reports", icon = Icons.Filled.Inbox) { nav.navigate("myReports") }
                        }
                    }
                }
            } else {
                item { Group { ListRow("If someone is in danger right now, call 911. UniHub can't reach emergency services.", icon = Icons.Filled.WarningAmber) } }
                item {
                    Group {
                        ChoiceRow("What is it about?", listOf("" to "Choose") + reasons, reason, Icons.Filled.Flag) { reason = it }
                        Divider()
                        ChoiceRow("Where did it happen?", places, place, Icons.Filled.Info) { place = it }
                    }
                }
                item {
                    Group(header = "What happened?") {
                        OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth().heightIn(min = 130.dp).padding(12.dp))
                    }
                }
                item {
                    Group(header = "Details (optional)") {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(what, { what = it }, label = { Text("Who or what: a name, username, file or link") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(whenText, { whenText = it }, label = { Text("When did it happen?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item {
                    Group(footer = "Screenshots stay private. Only the UniHub team can open them.") {
                        val p = photo
                        if (p != null) {
                            Image(
                                p.asImageBitmap(), null,
                                Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(12.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Divider()
                            ListRow("Remove", titleColor = MaterialTheme.colorScheme.error) { photo = null }
                        } else ListRow("Add a screenshot", icon = Icons.Filled.AddPhotoAlternate) { pick.launch("image/*") }
                    }
                }
                item {
                    Group(
                        footer = if (me != null) "With your name hidden, you can still follow the report under My reports."
                        else "Leave a way to reach you if you want a reply. Without one, we can't answer you.",
                    ) {
                        if (me != null) SwitchRow("Hide my name from the team", null, anonymous) { anonymous = it }
                        if (me == null || anonymous) {
                            OutlinedTextField(contact, { contact = it }, label = { Text("Your email or phone (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
                        }
                    }
                }
                item {
                    Group(footer = "False reports made to harm someone break the community rules.") {
                        Row(Modifier.fillMaxWidth().clickable { truthful = !truthful }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(truthful, { truthful = it })
                            Text("What I wrote is true, to the best of my knowledge", style = MaterialTheme.typography.bodyMedium)
                        }
                        error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                        Button(
                            enabled = ready,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        val data = photo?.let { PhotoTools.base64(it) }
                                        val r = AccountsApi.call(
                                            "report",
                                            mapOf(
                                                "kind" to (if (place == "app") "app" else "outside"), "targetId" to "", "targetName" to what.trim(),
                                                "reason" to reason, "place" to place,
                                                "details" to details.trim() + (if (whenText.isBlank()) "" else "\n\nWhen: " + whenText.trim()),
                                                "contact" to contact.trim(), "anonymous" to anonymous, "photo" to data,
                                            ),
                                            Account.token,
                                        )
                                        reference = r.str("ref") ?: "-"
                                        error = null
                                    } catch (e: ApiError) { error = e.message }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        ) { Text("Send report") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The reports this account sent, with what happened to them */
@Composable
fun MyReportsScreen() {
    var items by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            items = (Account.call("myReports")["reports"] as? JsonArray).objects()
            error = null
        } catch (e: ApiError) { error = e.message }
        loading = false
    }
    Screen("My reports") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            error?.let { e -> item { Text(e, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
            if (loading && items.isEmpty()) item { ABDots(Modifier.padding(16.dp)) }
            else if (items.isEmpty() && error == null) item { EmptyState(Icons.Filled.Inbox, tr("No reports yet"), tr("Reports you send show here.")) }
            items.forEach { r ->
                item {
                    val status = r.str("status")
                    Group {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Moderation.reasons.firstOrNull { it.first == r.str("reason") }?.second ?: (r.str("reason") ?: ""), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Badge(tr(when (status) { "done" -> "Handled"; "dismissed" -> "Closed"; else -> "Open" }), if (status == "open") Brand.orange else Brand.green)
                            }
                            r.str("targetName")?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
                            r.str("reply")?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = accent) }
                            Text(trf("Reference: %s", r.str("ref") ?: "") + " · " + (r.str("created") ?: "").take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Admins: when an account accepted the terms */
@Composable
fun AdminTermsScreen() {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var who by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // everyone is listed: tap a person to see their record, or type to filter
    var people by remember { mutableStateOf<List<AbUser>>(emptyList()) }
    var peopleLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Account.saved("adminUsers")?.let { r -> people = r["users"].decodeAs<List<AbUser>>().orEmpty() }
        runCatching { Account.callSaving("adminUsers", mapOf("q" to "")) }.getOrNull()?.let { r -> people = r["users"].decodeAs<List<AbUser>>() ?: people }
        peopleLoaded = true
    }
    fun find(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        busy = true
        scope.launch {
            try {
                val r = Account.call("adminTerms", mapOf("query" to t))
                who = r.str("username")
                rows = (r["accepted"] as? JsonArray).objects()
                error = null
            } catch (e: ApiError) { who = null; rows = emptyList(); error = e.message }
            busy = false
        }
    }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) people else people.filter { u -> listOf(u.username, u.name, u.email.orEmpty(), u.uniId.orEmpty()).any { it.lowercase().contains(q) } }
    Screen("Terms records") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Group {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(query, { query = it }, label = { Text("Username, email or university ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (peopleLoaded && shown.isEmpty()) Button(enabled = q.isNotEmpty() && !busy, onClick = { find(query) }, modifier = Modifier.fillMaxWidth()) { Text("Look up this account") }
                    }
                }
            }
            who?.let { w ->
                item {
                    Group(header = "@$w") {
                        if (rows.isEmpty()) ListRow("No record for this account yet.")
                        rows.forEachIndexed { i, r ->
                            if (i > 0) Divider()
                            ListRow(r.str("at") ?: "", listOfNotNull(r.str("version"), r.str("source"), r.str("appVersion")).joinToString(" · "))
                        }
                    }
                }
            }
            item {
                Group(header = if (people.isEmpty()) null else trf("Everyone (%d)", shown.size)) {
                    if (!peopleLoaded) repeat(5) { SkeletonRow() }
                    shown.forEachIndexed { i, u ->
                        if (i > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().clickable { find(u.username) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Avatar(u.name, u.color, 34.dp, admin = u.isAdmin, photo = u.avatar)
                            Column(Modifier.weight(1f)) {
                                Text(u.name, maxLines = 1)
                                Text("@" + u.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (who == u.username) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
