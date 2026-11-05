package org.unihub.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

// Native "Rate our service" (requests/service_rating.html) and the Discord community vote (apps/voting.html).
// Both post to the same Apps Scripts as the website, so answers from the app and the site land in one sheet.

private object FeedbackApi {
    const val RATING = "https://script.google.com/macros/s/AKfycbyA-A0WREgeLnCZYLpe3V1jtDDJ1LC2Pc-iYxG0_5soAwPcbPjLITla3_k8Q4t9Gk2E/exec"
    const val VOTING = "https://script.google.com/macros/s/AKfycbzmBIXkb4uqwWsZ_gYUwSRMTwZVshGucECvWSeCpO76zVAFq5SsWVXCnnTBUdOWcdYK8Q/exec"

    suspend fun post(url: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).post(body.toString().toRequestBody("text/plain;charset=utf-8".toMediaType())).build()
        val text = try { Net.http.newCall(req).execute().use { it.body?.string() } } catch (e: Exception) { null }
            ?: throw ApiError(tr("Check your connection and try again."))
        val o = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        if (o["ok"] is JsonPrimitive && !o.bool("ok")) throw ApiError(o.str("error") ?: tr("Something went wrong."))
        o
    }

    suspend fun get(url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            Net.http.newCall(Request.Builder().url(url).build()).execute().use { json.parseToJsonElement(it.body!!.string()) as JsonObject }
        }.getOrNull()
    }

    val agent get() = "UniHub Android ${BuildConfig.VERSION_NAME}"
}

private val questions = listOf(
    "Easy to navigate" to "سهل التنقل", "Found materials quickly" to "مواد سريعة الوصول", "Loads fast" to "تحميل سريع",
    "Mobile-friendly design" to "مناسب للجوال", "Would recommend" to "سأنصح به", "Accurate search results" to "نتائج بحث دقيقة",
    "Smooth download" to "تحميل سلس", "Well-organized content" to "محتوى منظم", "Feels safe" to "أشعر بالأمان",
    "Will use again" to "سأستخدمه مجددًا",
)

@Composable
fun ServiceRatingScreen() {
    val scope = rememberCoroutineScope()
    var rating by remember { mutableStateOf(7f) }
    val answers = remember { mutableStateMapOf<Int, Boolean>() }
    var suggestion by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val face = listOf("😞", "😞", "😕", "😕", "😐", "😐", "🙂", "🙂", "😄", "🤩")[rating.toInt() - 1]

    fun submit() {
        busy = true; error = null
        scope.launch {
            try {
                FeedbackApi.post(FeedbackApi.RATING, buildJsonObject {
                    put("timestamp", Instant.now().toString())
                    put("rating", rating.toInt())
                    putJsonObject("answers") { questions.indices.forEach { put("q${it + 1}", if (answers[it] == true) "True" else "False") } }
                    put("suggestion", suggestion)
                    put("page", "android-app")
                    put("userAgent", FeedbackApi.agent)
                })
                done = true
            } catch (e: ApiError) { error = e.message }
            busy = false
        }
    }

    Screen("Rate our service") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (done) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("💚", fontSize = 60.sp)
                    Text("Thank you!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Your rating helps us make UniHub better for everyone.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { done = false; answers.clear(); suggestion = ""; rating = 7f }) { Text("Rate again") }
                }
            } else {
                item {
                    Group(header = "How would you rate UniHub?") {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(face, fontSize = 52.sp)
                            Text("${rating.toInt()} / 10", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Slider(rating, { rating = it }, valueRange = 1f..10f, steps = 8)
                        }
                    }
                }
                item {
                    Group(header = "Quick questions", footer = tr("%d of %d answered").format(answers.size, questions.size)) {
                        questions.forEachIndexed { i, (en, ar) ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(en, fontWeight = FontWeight.SemiBold)
                                    if (Lang.code != "ar") Text(ar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Choice(answers[i] == true, Icons.Filled.ThumbUp, Brand.green) { answers[i] = true }
                                Spacer(Modifier.size(6.dp))
                                Choice(answers[i] == false, Icons.Filled.ThumbDown, Color(0xFFD70015)) { answers[i] = false }
                            }
                        }
                    }
                }
                item {
                    Group(header = "Anything we should add or fix?") {
                        OutlinedTextField(suggestion, { suggestion = it.take(1000) }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
                error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                item {
                    Button(
                        onClick = ::submit, enabled = !busy && answers.size == questions.size,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                    ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Send rating") }
                }
            }
        }
    }
}

@Composable
private fun Choice(on: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 42.dp, height = 36.dp).clip(RoundedCornerShape(10.dp))
            .background(if (on) color else color.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (on) Color.White else color, modifier = Modifier.size(20.dp)) }
}

private val games = listOf(
    "Valorant" to "🎯", "League of Legends" to "⚔️", "CS2" to "🔫", "Fortnite" to "🏗️", "Minecraft" to "⛏️", "GTA V" to "🚗",
    "Rocket League" to "🚀", "Apex Legends" to "🪂", "Overwatch 2" to "🛡️", "PUBG" to "🎒", "Call of Duty" to "💥", "Rainbow Six" to "🌈",
    "Dota 2" to "🧙", "Roblox" to "🧱", "Among Us" to "🔪", "Fall Guys" to "🍬", "FIFA / EA FC" to "⚽", "NBA 2K" to "🏀",
    "Clash Royale" to "👑", "Clash of Clans" to "🏰", "Free Fire" to "🔥", "Mobile Legends" to "📱", "Genshin Impact" to "🌸",
    "Elden Ring" to "🗡️", "Stardew Valley" to "🌾", "Terraria" to "🌳", "Chess.com" to "♟️", "Uno / Cards" to "🃏", "Skribbl.io" to "✏️",
    "Jackbox" to "📺",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VotingScreen() {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<JsonObject?>(null) }
    var voted by remember { mutableStateOf(Saved.prefs.getBoolean("voted", false)) }
    var vote by remember { mutableStateOf<String?>(null) }
    var interested by remember { mutableStateOf<Boolean?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var other by remember { mutableStateOf("") }
    var suggestion by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { stats = FeedbackApi.get(FeedbackApi.VOTING)?.get("stats") as? JsonObject }

    fun submit() {
        val v = vote ?: return
        busy = true; error = null
        scope.launch {
            try {
                val r = FeedbackApi.post(FeedbackApi.VOTING, buildJsonObject {
                    put("timestamp", Instant.now().toString())
                    put("vote", v)
                    put("interested", interested)
                    putJsonArray("games") { picked.forEach { add(JsonPrimitive(it)) } }
                    put("otherGame", other)
                    put("suggestion", suggestion)
                    put("page", "android-app")
                    put("userAgent", FeedbackApi.agent)
                })
                (r["stats"] as? JsonObject)?.let { stats = it }
                voted = true
                Saved.prefs.edit().putBoolean("voted", true).apply()
            } catch (e: ApiError) { error = e.message }
            busy = false
        }
    }

    Screen("Discord community vote") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Results(stats) }
            if (voted) item {
                Group {
                    ListRow(tr("Thanks, your vote is in!"), emoji = "✅")
                    Divider()
                    ListRow(tr("Vote again")) { voted = false }
                }
            } else {
                item {
                    Group(header = "Should we open the Discord gaming community?") {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigChoice(vote == "YES", "✅", tr("Open it"), Brand.green, Modifier.weight(1f)) { vote = "YES" }
                            BigChoice(vote == "NO", "❌", tr("Don't open"), Color(0xFFD70015), Modifier.weight(1f)) { vote = "NO" }
                        }
                    }
                }
                item {
                    Group(header = "Would you personally be interested in joining?") {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                            listOf(true to "Yes", false to "No").forEachIndexed { i, (v, l) ->
                                SegmentedButton(selected = interested == v, onClick = { interested = v }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                            }
                        }
                    }
                }
                item {
                    Group(header = "Which games would you play?", footer = tr("%d picked").format(picked.size)) {
                        FlowRow(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            games.forEach { (g, e) ->
                                val on = g in picked
                                Text(
                                    "$e $g", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(if (on) accent else MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { picked = if (on) picked - g else picked + g }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                        OutlinedTextField(other, { other = it.take(80) }, placeholder = { Text("Another game?") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp))
                    }
                }
                item {
                    Group(header = "Ideas for the community (optional)") {
                        OutlinedTextField(suggestion, { suggestion = it.take(1000) }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
                error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                item {
                    Button(onClick = ::submit, enabled = !busy && vote != null, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Send my vote")
                    }
                }
            }
        }
    }
}

@Composable
private fun BigChoice(on: Boolean, emoji: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (on) color else color.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(label, fontWeight = FontWeight.Bold, color = if (on) Color.White else color)
    }
}

@Composable
private fun Results(stats: JsonObject?) {
    Group(header = "Live results") {
        if (stats == null) { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Group }
        val total = (stats["totalVotes"] as? JsonPrimitive)?.intOrNull ?: 0
        val yes = (stats["yesPercent"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val interested = (stats["interestedCount"] as? JsonPrimitive)?.intOrNull ?: 0
        val top = (stats["topGames"] as? JsonArray).objects()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(tr("%d votes").format(total), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(tr("%d interested").format(interested), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                if (yes > 0) Box(Modifier.weight(yes.toFloat().coerceAtLeast(0.01f)).height(12.dp).background(Brand.green))
                if (yes < 100) Box(Modifier.weight((100 - yes).toFloat().coerceAtLeast(0.01f)).height(12.dp).background(Color(0xFFD70015).copy(alpha = 0.75f)))
            }
            Row {
                Text("✅ ${yes.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("❌ ${100 - yes.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (top.isNotEmpty()) {
                Text("Most wanted games", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                top.take(5).forEach { g ->
                    Row {
                        Text(g.str("name") ?: "", modifier = Modifier.weight(1f))
                        Text(g.str("count") ?: "0", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
