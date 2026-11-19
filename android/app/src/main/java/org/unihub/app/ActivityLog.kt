package org.unihub.app

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// 1.22.0: the admins' activity log. Who signs up, signs in and opens UniHub, from which phone or the website,
// and what they did (posts, stories, profile changes, reports). Never the text of messages.

/** Tells the server once per app start (it keeps at most one per 30 minutes) that UniHub was opened */
object Hello {
    fun send(scope: CoroutineScope) {
        scope.launch {
            kotlinx.coroutines.delay(2500)   // after the sign-in check, so the log knows who it was
            runCatching {
                Account.call("hello", mapOf(
                    "source" to "android", "appVersion" to BuildConfig.VERSION_NAME,
                    "device" to "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                    "lang" to java.util.Locale.getDefault().language,
                ))
            }
        }
    }
}

private class ActivityKind(val id: String, val label: String, val icon: ImageVector)

private val activityKinds = listOf(
    ActivityKind("all", "Everything", Icons.Filled.List), ActivityKind("open", "App opens", Icons.Filled.PhoneAndroid),
    ActivityKind("signup", "New accounts", Icons.Filled.PersonAdd), ActivityKind("signin", "Sign-ins", Icons.Filled.Login),
    ActivityKind("failed", "Failed sign-ins", Icons.Filled.GppBad), ActivityKind("content", "Posts and stories", Icons.Filled.Edit),
    ActivityKind("profile", "Profile changes", Icons.Filled.ManageAccounts), ActivityKind("report", "Reports", Icons.Filled.Flag),
)

private fun groupOf(kind: String) = when (kind) {
    "signed up" -> "signup"
    "signed in", "signed out" -> "signin"
    "sign-in failed" -> "failed"
    "opened UniHub" -> "open"
    "posted", "added a story", "commented", "shared a video" -> "content"
    "edited profile", "changed photo", "changed cover", "changed password", "deleted account" -> "profile"
    "sent a report", "sent a suggestion" -> "report"
    else -> "other"
}

private fun platformOf(r: JsonObject): String = when (val s = r.str("source").orEmpty().lowercase()) {
    "ios", "android" -> s
    "" -> ""
    else -> "web"
}

private fun timeOf(r: JsonObject) = runCatching { Instant.parse(r.str("time")) }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen() {
    var all by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var platform by remember { mutableStateOf("all") }
    var kind by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(150) }
    LaunchedEffect(Unit) {
        Account.saved("adminActivity")?.let { all = (it["records"] as? JsonArray).objects() }
        runCatching { Account.callSaving("adminActivity") }
            .onSuccess { all = (it["records"] as? JsonArray).objects(); failed = false }
            .onFailure { failed = true }
        loaded = true
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val q = query.trim().lowercase()
    val shown = all.filter { r ->
        (platform == "all" || platformOf(r) == platform) && (kind == "all" || groupOf(r.str("kind").orEmpty()) == kind) &&
            (q.isEmpty() || listOf("username", "name", "device", "detail").any { r.str(it).orEmpty().lowercase().contains(q) })
    }
    fun since(days: Long) = today.minusDays(days).atStartOfDay(zone).toInstant()
    fun people(from: Instant, p: String? = null) = all.filter { (timeOf(it) ?: Instant.EPOCH) >= from && activityField(it, "userId").isNotEmpty() && (p == null || platformOf(it) == p) }
        .map { activityField(it, "userId") }.toSet().size
    fun count(group: String, from: Instant) = all.count { groupOf(it.str("kind").orEmpty()) == group && (timeOf(it) ?: Instant.EPOCH) >= from }
    val dayFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    Screen("Activity log") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (all.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("At a glance"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityTile(people(since(0)), tr("people today"), Icons.Filled.People)
                        ActivityTile(count("open", since(0)), tr("app opens today"), Icons.Filled.PhoneAndroid)
                        ActivityTile(count("signup", since(0)), tr("new today"), Icons.Filled.PersonAdd)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityTile(people(since(6)), tr("people this week"), Icons.Filled.CalendarMonth)
                        ActivityTile(people(since(6), "ios"), "iPhone", Icons.Filled.PhoneIphone)
                        ActivityTile(people(since(6), "android"), "Android", Icons.Filled.Android)
                        ActivityTile(people(since(6), "web"), "Web", Icons.Filled.Language)
                    }
                    Text(tr("People are counted once, however often they open UniHub. Guests are not counted as people."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("all" to tr("All"), "ios" to "iPhone", "android" to "Android", "web" to "Web")) { (id, label) ->
                            FilterChip(selected = platform == id, onClick = { platform = id; limit = 150 }, label = { Text(label) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activityKinds) { k ->
                            FilterChip(selected = kind == k.id, onClick = { kind = k.id; limit = 150 }, label = { Text(tr(k.label)) },
                                leadingIcon = { Icon(k.icon, null, Modifier.size(16.dp)) })
                        }
                    }
                    OutlinedTextField(query, { query = it }, label = { Text(tr("Search a name, username or phone model")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(trf("%d records", shown.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                !loaded && all.isEmpty() -> item { Group { repeat(6) { SkeletonRow() } } }
                failed && all.isEmpty() -> item { Group { ListRow("The activity log needs the newest server. Try again later.") } }
                shown.isEmpty() -> item { Group { ListRow("Nothing matches.") } }
            }
            var lastDay: LocalDate? = null
            shown.take(limit).forEach { r ->
                val t = timeOf(r)
                val day = t?.atZone(zone)?.toLocalDate()
                if (day != lastDay) {
                    lastDay = day
                    val title = when (day) { today -> tr("Today"); today.minusDays(1) -> tr("Yesterday"); null -> ""; else -> day.format(dayFormat) }
                    item(key = "d$title") {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp))
                    }
                }
                item {
                    val group = groupOf(r.str("kind").orEmpty())
                    val u = r.str("username").orEmpty()
                    val n = r.str("name").orEmpty()
                    val who = if (u.isEmpty() || u == "guest") tr("Guest (not signed in)") else if (n.isEmpty() || n == u) "@$u" else "$n  @$u"
                    val app = when (platformOf(r)) { "ios" -> "iPhone"; "android" -> "Android"; "web" -> "Web"; else -> "" }
                    val line2 = listOf(t?.atZone(zone)?.format(timeFormat).orEmpty(), listOf(app, r.str("appVersion").orEmpty()).filter { it.isNotEmpty() }.joinToString(" "),
                        r.str("device").orEmpty(), r.str("detail").orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
                    Row(
                        Modifier.fillMaxWidth().clickable { if (u.isNotEmpty() && u != "guest") query = u }.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(activityKinds.firstOrNull { it.id == group }?.icon ?: Icons.Filled.Circle, null,
                            tint = if (group == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(who + "  " + tr(r.str("kind").orEmpty()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
            }
            if (shown.size > limit) item {
                TextButton(onClick = { limit += 300 }, modifier = Modifier.fillMaxWidth()) { Text(tr("Show more")) }
            }
        }
    }
}

private fun activityField(o: JsonObject, k: String) = o.str(k).orEmpty()

@Composable
private fun RowScope.ActivityTile(n: Int, label: String, icon: ImageVector) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text("$n", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}
