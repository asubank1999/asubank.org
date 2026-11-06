package org.unihub.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** One bus line from asubank.org/schedules/bus20251 */
data class BusRoute(val name: String, val pickup: String, val morning: List<String>, val evening: List<String>)

/** The timetable lives in the website page's script, so changing the page changes the app too */
object BusTimes {
    const val PATH = "/schedules/bus20251"
    private const val CACHE = "bus.page"

    fun parse(html: String): List<BusRoute> {
        val start = html.indexOf("const data")
        if (start < 0) return emptyList()
        val body = html.substring(start)
        val row = Regex("""\{\s*name:\s*"([^"]*)"\s*,\s*pickup:\s*"([^"]*)"\s*,\s*morning:\s*\[([^\]]*)]\s*,\s*evening:\s*\[([^\]]*)]\s*\}""")
        val time = Regex(""""([^"]+)"""")
        return row.findAll(body).map { m ->
            BusRoute(
                m.groupValues[1].trim(), m.groupValues[2].trim(),
                time.findAll(m.groupValues[3]).map { it.groupValues[1] }.toList(),
                time.findAll(m.groupValues[4]).map { it.groupValues[1] }.toList(),
            )
        }.toList()
    }

    fun cached(): List<BusRoute> = parse(Saved.prefs.getString(CACHE, "") ?: "")

    suspend fun load(): List<BusRoute>? = withContext(Dispatchers.IO) {
        runCatching {
            val html = Net.http.newCall(Request.Builder().url(AB.SITE + PATH + ".html").build()).execute().use { it.body!!.string() }
            parse(html).takeIf { it.isNotEmpty() }?.also { Saved.prefs.edit().putString(CACHE, html).apply() }
        }.getOrNull()
    }
}

@Composable
fun BusTimesScreen() {
    var routes by remember { mutableStateOf(BusTimes.cached()) }
    var loading by remember { mutableStateOf(routes.isEmpty()) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var evening by remember { mutableStateOf(java.time.LocalTime.now().hour >= 12) }
    LaunchedEffect(Unit) {
        val fresh = BusTimes.load()
        if (fresh != null) routes = fresh else if (routes.isEmpty()) failed = true
        loading = false
    }
    val q = query.trim()
    val shown = if (q.isEmpty()) routes else routes.filter { it.name.contains(q, true) || it.pickup.contains(q, true) }

    Screen("Bus times", large = true) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text(tr("Area or pickup point")) },
                        shape = RoundedCornerShape(14.dp),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(!evening, { evening = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(tr("To the university (AM)")) }
                        SegmentedButton(evening, { evening = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(tr("Going home (PM)")) }
                    }
                }
            }
            when {
                loading && routes.isEmpty() -> items(4) { SkeletonRow() }
                failed -> item { Text(tr("Couldn't load the bus times. Check your connection."), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error) }
                shown.isEmpty() -> item { Text(tr("No bus line matches."), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> items(shown, key = { it.name }) { r -> BusRouteCard(r, evening) }
            }
            item {
                Text(
                    tr("Times can change. The transport office has the final word."),
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BusRouteCard(r: BusRoute, evening: Boolean) {
    val times = if (evening) r.evening else r.morning
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.DirectionsBus, null, tint = MaterialTheme.colorScheme.primary)
                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (r.pickup.isNotEmpty()) Text(r.pickup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (times.isEmpty()) Text(tr("No buses at this time"), style = MaterialTheme.typography.bodySmall)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                times.forEach { t ->
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(t, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
