package org.asubank.app

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.Locale

// MARK: - groups people ask to add

@Serializable
data class ApprovedGroup(val id: String = "", val title: String = "", val category: String? = null, val url: String = "")

private val groupCategories = listOf("Course", "Club", "Dorm or housing", "Other")

@Composable
private fun StatusBadge(status: String) = when (status) {
    "approved" -> Badge(tr("Accepted"), Brand.green)
    "rejected" -> Badge(tr("Rejected"), MaterialTheme.colorScheme.error)
    else -> Badge(tr("Waiting"), Brand.orange)
}

/** Asks for a group to be listed. The owner accepts or rejects it in the Admin tab. */
@Composable
fun GroupRequestScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsState()
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf<List<kotlinx.serialization.json.JsonObject>>(emptyList()) }
    LaunchedEffect(user?.id) {
        if (user != null) runCatching { mine = (Account.call("myGroupRequests")["requests"] as? kotlinx.serialization.json.JsonArray).objects() }
    }
    val valid = title.trim().length >= 3 && url.trim().lowercase().startsWith("https://")
    Screen("Add your group") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                sent -> {
                    Icon(Icons.Filled.CheckCircle, null, tint = Brand.green, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally))
                    Text("Request sent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text("The owner will review your group. We'll notify you when it's accepted or rejected.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Done") }
                }
                user == null -> {
                    Text("Sign in so we can tell you when your group is accepted.")
                    Button(onClick = { Account.signInShown.value = true }) { Text("Sign in") }
                }
                else -> {
                    OutlinedTextField(title, { if (it.length <= 80) title = it }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Box {
                        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
                            Text(tr("Category") + ": " + (if (category.isEmpty()) tr("Not set") else tr(category)))
                        }
                        DropdownMenu(open, { open = false }) {
                            (listOf("") + Colleges + groupCategories).forEach { c ->
                                DropdownMenuItem(text = { Text(if (c.isEmpty()) "Not set" else c) }, onClick = { category = c; open = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        url, { url = it.trim() }, label = { Text("Invite link") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://chat.whatsapp.com/…") },
                        supportingText = { Text("Paste the group's invite link, starting with https://") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(notes, { if (it.length <= 500) notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    Account.call("groupRequest", mapOf("title" to title, "category" to category, "url" to url.trim(), "notes" to notes))
                                    sent = true
                                } catch (e: ApiError) { error = e.message }
                                busy = false
                            }
                        },
                        enabled = valid && !busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Sending…" else "Send") }
                    if (mine.isNotEmpty()) {
                        Text("Your requests", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        mine.forEach { r ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(r.str("title") ?: "", Modifier.weight(1f), maxLines = 1)
                                StatusBadge(r.str("status") ?: "pending")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Admins: groups people asked to add */
@Composable
fun AdminGroupRequestsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<kotlinx.serialization.json.JsonObject>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try { list = (Account.call("adminGroupRequests", mapOf("all" to showAll))["requests"] as? kotlinx.serialization.json.JsonArray).objects(); error = null }
        catch (e: ApiError) { error = e.message }
        loading = false
    }
    fun set(id: String?, status: String) {
        scope.launch {
            try { Account.call("adminGroupSet", mapOf("id" to id, "status" to status)); load() } catch (e: ApiError) { error = e.message }
        }
    }
    LaunchedEffect(showAll) { load() }
    Screen("Group requests") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showAll, { showAll = false }, label = { Text("Waiting") })
                    FilterChip(showAll, { showAll = true }, label = { Text("All") })
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            else if (list.isEmpty()) item { Text("Nothing to review.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list.size) { i ->
                val r = list[i]
                val status = r.str("status") ?: "pending"
                Group {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.str("title") ?: "", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            StatusBadge(status)
                        }
                        r.str("category")?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            r.str("url") ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                            modifier = Modifier.clickable { r.str("url")?.let { openExternal(ctx, it) } },
                        )
                        r.str("notes")?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text("@${r.str("username") ?: ""} · ${Social.ago(r.str("created"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            if (status != "approved") TextButton(onClick = { set(r.str("id"), "approved") }) { Text("Accept", color = Brand.green) }
                            if (status != "rejected") TextButton(onClick = { set(r.str("id"), "rejected") }) { Text("Reject", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - photoshoot

/** Books a session with ASU Lens & Light Studio. Posts to the same script as the website's form. */
@Composable
fun PhotoshootScreen() {
    val endpoint = "https://script.google.com/macros/s/AKfycbxMdRZImJfRkBjjG_l-DhOrJLZsVghrpuRybmGZwQAv8jSiBEYRb639e0Sl4wUV7S2H2A/exec"
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val u = Account.user.value
    val parts = remember { u?.name?.split(" ")?.filter { it.isNotEmpty() }.orEmpty() }
    var first by remember { mutableStateOf(parts.firstOrNull() ?: "") }
    var middle by remember { mutableStateOf("") }
    var last by remember { mutableStateOf(if (parts.size > 1) parts.last() else "") }
    var phone by remember { mutableStateOf(u?.phone ?: "") }
    var email by remember { mutableStateOf(u?.email ?: "") }
    val tomorrow = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) } }
    var y by remember { mutableIntStateOf(tomorrow.get(Calendar.YEAR)) }
    var mo by remember { mutableIntStateOf(tomorrow.get(Calendar.MONTH)) }
    var d by remember { mutableIntStateOf(tomorrow.get(Calendar.DAY_OF_MONTH)) }
    var session by remember { mutableStateOf("portrait") }
    var from by remember { mutableIntStateOf(10 * 60) }
    var to by remember { mutableIntStateOf(11 * 60) }
    var outside by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var edits by remember { mutableIntStateOf(0) }
    var premium by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var promo by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val hours = ((to - from).coerceAtLeast(0)) / 60.0
    val hourFee = hours * 5
    val editFee = edits * 3.0
    val travel = if (outside) 5.0 else 0.0
    val subtotal = hourFee + editFee + travel
    val day = "%04d-%02d-%02d".format(Locale.ENGLISH, y, mo + 1, d)
    val today = "%tF".format(Locale.ENGLISH, Calendar.getInstance())
    val code = promo.trim().uppercase(Locale.ENGLISH)
    val promoPercent = if (code == "FRIENDSONLY70" && today <= "2028-12-12") 70 else 0
    val promoAmount = subtotal * promoPercent / 100
    val total = subtotal - promoAmount
    val valid = first.isNotBlank() && last.isNotBlank() && phone.isNotBlank() && hours > 0
    fun clock(m: Int) = "%02d:%02d".format(Locale.ENGLISH, m / 60, m % 60)
    fun jd(v: Double) = "%.2f ".format(Locale.ENGLISH, v) + tr("JD")

    fun submit() {
        busy = true
        scope.launch {
            val name = listOf(first, middle, last).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            val body = buildJsonObject {
                put("fullName", name); put("firstName", first.trim()); put("middleName", middle.trim()); put("lastName", last.trim())
                put("phone", phone.trim()); put("email", email.trim()); put("shootDate", day); put("timeFrom", clock(from)); put("timeTo", clock(to))
                put("durationHours", "%.2f".format(Locale.ENGLISH, hours)); put("sessionType", session)
                put("location", address.trim().ifEmpty { if (outside) "Not specified" else "ASU Campus" })
                put("locationZone", if (outside) "outside" else "asu"); put("editPhotos", edits)
                put("editType", if (premium) "Premium (Photoshop + paid tools)" else "Standard"); put("notes", notes)
                putJsonObject("pricing") {
                    put("hourlyFee", hourFee); put("editFee", editFee); put("travelFee", travel); put("subtotal", subtotal)
                    put("promoCode", if (promoPercent > 0) code else null); put("promoPercentOff", promoPercent)
                    put("promoAmount", promoAmount); put("estimatedTotal", total)
                }
                put("submittedAt", java.time.Instant.now().toString())
            }.toString()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url(endpoint).post(body.toRequestBody("text/plain;charset=utf-8".toMediaType())).build()
                    Net.http.newCall(req).execute().use { it.code < 400 }
                }.getOrDefault(false)
            }
            if (ok) { sent = true; error = null } else error = tr("Couldn't send your booking. Check your connection and try again.")
            busy = false
        }
    }

    Screen("Book a photoshoot") { pad ->
        if (sent) {
            Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                Icon(Icons.Filled.CheckCircle, null, tint = Brand.green, modifier = Modifier.size(64.dp))
                Text("Booking submitted!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(trf("The photographer will review your request and contact you on %s with the final invoice and payment details.", phone), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { sent = false; notes = ""; promo = ""; edits = 0; address = "" }) { Text("Book another session") }
            }
            return@Screen
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📸", fontSize = 36.sp)
                    Text("ASU Lens & Light Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Every frame, a story. Fill in your details and see the price as you go. The photographer reviews your request and sends the final invoice.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Group(header = "Your details") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(first, { first = it }, label = { Text("First name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(middle, { middle = it }, label = { Text("Middle name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(last, { last = it }, label = { Text("Last name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp or phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Group(header = "Session") {
                    ListRow("Date", trailing = day, icon = Icons.Filled.CalendarMonth) {
                        DatePickerDialog(ctx, { _, a, b, c -> y = a; mo = b; d = c }, y, mo, d).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
                    }
                    Divider()
                    ListRow("From", trailing = clock(from), icon = Icons.Filled.Schedule) {
                        TimePickerDialog(ctx, { _, h, m -> from = h * 60 + m }, from / 60, from % 60, false).show()
                    }
                    Divider()
                    ListRow("To", trailing = clock(to), icon = Icons.Filled.Schedule) {
                        TimePickerDialog(ctx, { _, h, m -> to = h * 60 + m }, to / 60, to % 60, false).show()
                    }
                    if (hours <= 0) Text("The end time must be after the start time.", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Divider()
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("portrait" to "Portrait", "graduation" to "Graduation", "group" to "Group", "event" to "Event", "product" to "Product / Commercial", "other" to "Other").forEach { (k, l) ->
                            FilterChip(session == k, { session = k }, label = { Text(l) })
                        }
                    }
                }
            }
            item {
                Group(header = "Location", footer = if (outside) "Outside ASU adds a 5 JD travel fee." else "No travel fee on campus.") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!outside, { outside = false }, label = { Text("ASU campus") })
                            FilterChip(outside, { outside = true }, label = { Text("Outside ASU") })
                        }
                        OutlinedTextField(address, { address = it }, label = { Text(if (outside) "Address" else "Where on campus? (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Group(
                    header = "Photos and editing",
                    footer = if (premium) "Photoshop and paid tools: skin retouching, compositing and advanced grading. 3 JD a photo as a base price; complex edits may cost more." else "Basic retouching, color grading, crop and export. 3 JD a photo.",
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Photos to edit", Modifier.weight(1f))
                        IconButton(onClick = { if (edits > 0) edits-- }) { Icon(Icons.Filled.Remove, null) }
                        Text("$edits", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { if (edits < 500) edits++ }) { Icon(Icons.Filled.Add, null) }
                    }
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!premium, { premium = false }, label = { Text("Standard") })
                        FilterChip(premium, { premium = true }, label = { Text("Premium") })
                    }
                }
            }
            item {
                Group {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            promo, { promo = it }, label = { Text("Promo code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                if (promoPercent > 0) Text(trf("%d%% off applied", promoPercent), color = Brand.green)
                                else if (code.isNotEmpty()) Text("This promo code isn't valid.", color = MaterialTheme.colorScheme.error)
                            },
                        )
                    }
                }
            }
            item {
                Group(header = "Your estimate", footer = "This is an estimate; the photographer confirms the final amount. You can pay by PayPal, CliQ or cash.") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        @Composable fun line(a: String, b: String) = Row(Modifier.fillMaxWidth()) { Text(a, Modifier.weight(1f)); Text(b, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        line(trf("Session (%s h × 5 JD)", "%.1f".format(Locale.ENGLISH, hours)), jd(hourFee))
                        line(trf("Editing (%d × 3 JD)", edits), jd(editFee))
                        line(tr("Travel"), if (outside) jd(travel) else tr("Free"))
                        if (promoPercent > 0) line(tr("Promo discount"), "−" + jd(promoAmount))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Estimated total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(jd(total), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = ::submit, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Sending…" else "Send booking request") }
                }
            }
        }
    }
}

// MARK: - weather

data class WeatherHour(val hour: Int, val temp: Int, val rain: Int, val code: Int)

@Serializable private data class WeatherHourly(
    val time: List<String> = emptyList(),
    val temperature_2m: List<Double?> = emptyList(),
    val precipitation_probability: List<Int?> = emptyList(),
    val weather_code: List<Int?> = emptyList(),
)
@Serializable private data class WeatherReply(val hourly: WeatherHourly = WeatherHourly())

object Weather {
    val hours = MutableStateFlow<List<WeatherHour>>(emptyList())
    val tomorrow = MutableStateFlow(false)
    private var loaded = 0L
    /** Campus location only; nothing about the student is sent */
    private const val URL = "https://api.open-meteo.com/v1/forecast?latitude=32.04&longitude=35.90&hourly=temperature_2m,precipitation_probability,weather_code&timezone=Asia%2FAmman&forecast_days=2"

    fun emoji(c: Int) = when (c) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "☁️"
    }

    fun advice(h: List<WeatherHour>): String? = when {
        h.maxOf { it.rain } >= 50 -> "Take an umbrella, rain is likely."
        h.maxOf { it.temp } >= 32 -> "It's hot. Drink water and stay in the shade."
        h.minOf { it.temp } <= 8 -> "It's cold. Take a jacket."
        else -> null
    }

    private fun apply(text: String): Boolean {
        val r = runCatching { json.decodeFromString<WeatherReply>(text) }.getOrNull() ?: return false
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Amman"))
        val late = cal.get(Calendar.HOUR_OF_DAY) >= 16
        if (late) cal.add(Calendar.DAY_OF_YEAR, 1)
        val prefix = "%04d-%02d-%02d".format(Locale.ENGLISH, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val w = r.hourly
        val out = w.time.mapIndexedNotNull { i, t ->
            if (!t.startsWith(prefix)) return@mapIndexedNotNull null
            val h = t.drop(11).take(2).toIntOrNull() ?: return@mapIndexedNotNull null
            if (h !in 7..16) return@mapIndexedNotNull null
            WeatherHour(h, Math.round(w.temperature_2m.getOrNull(i) ?: 0.0).toInt(), w.precipitation_probability.getOrNull(i) ?: 0, w.weather_code.getOrNull(i) ?: 3)
        }
        if (out.isEmpty()) return false
        hours.value = out
        tomorrow.value = late
        return true
    }

    suspend fun load() {
        if (hours.value.isNotEmpty() && System.currentTimeMillis() - loaded < 30 * 60_000) return
        val text = Net.text(URL)
        if (text != null && apply(text)) {
            Saved.prefs.edit().putString("weather.cache", text).apply()
            loaded = System.currentTimeMillis()
        } else if (hours.value.isEmpty()) Saved.prefs.getString("weather.cache", null)?.let { apply(it) }
    }
}

/** Home: the weather on campus from 7:00 AM till 4:00 PM */
@Composable
fun WeatherCard() {
    val hours by Weather.hours.collectAsState()
    val tomorrow by Weather.tomorrow.collectAsState()
    LaunchedEffect(Unit) { Weather.load() }
    if (hours.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (tomorrow) "Tomorrow on campus" else "Today on campus", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("7 AM – 4 PM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            hours.forEach { h ->
                Column(Modifier.widthIn(min = 38.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val label = when { h.hour == 12 -> "12 PM"; h.hour > 12 -> "${h.hour - 12} PM"; else -> "${h.hour} AM" }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Weather.emoji(h.code), fontSize = 20.sp)
                    Text("${h.temp}°", fontWeight = FontWeight.SemiBold)
                    Text("${h.rain}%", style = MaterialTheme.typography.labelSmall, color = if (h.rain >= 40) Color(0xFF1E88E5) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Weather.advice(hours)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// MARK: - life hacks

@Serializable data class HackItem(val t: String = "", val d: String = "")
@Serializable data class HackSection(val emoji: String = "", val title: String = "", val items: List<HackItem> = emptyList())

/** Emergency numbers and study, money and health tips. The tips come from assets/app/life-hacks.json. */
@Composable
fun LifeHacksScreen(back: Boolean = true) {
    val ctx = LocalContext.current
    var sections by remember { mutableStateOf<List<HackSection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    fun apply(text: String): Boolean {
        val all = runCatching { json.decodeFromString<Map<String, List<HackSection>>>(text) }.getOrNull() ?: return false
        sections = all[Lang.code] ?: all["en"].orEmpty()
        return sections.isNotEmpty()
    }
    LaunchedEffect(Unit) {
        Saved.prefs.getString("hacks.cache", null)?.let { apply(it) }
        Net.text("https://asubank.org/assets/app/life-hacks.json")?.let { if (apply(it)) Saved.prefs.edit().putString("hacks.cache", it).apply() }
        loading = false
    }
    Screen("Life hacks", back = back, large = !back) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Group(header = "In an emergency") {
                    ListRow("Call 911", "Jordan's emergency number: ambulance, fire and police", icon = Icons.Filled.Phone, iconColor = Color(0xFFE53935)) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))) }
                    }
                }
            }
            items(sections, key = { it.title }) { s ->
                Group(header = "${s.emoji} ${s.title}") {
                    s.items.forEachIndexed { i, h ->
                        if (i > 0) Divider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(h.t, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(h.d, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            else if (sections.isEmpty()) item { Text("Couldn't load the tips. Check your connection and try again.", Modifier.padding(16.dp)) }
            item { Text("These are general tips, not medical or professional advice. In an emergency, call for help first.", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// MARK: - notification sounds

object NotifySounds {
    val all = listOf(
        "default" to "Phone default", "chime" to "Chime", "bell" to "Bell", "ping" to "Ping",
        "marimba" to "Marimba", "soft" to "Soft", "alert" to "Alert", "drops" to "Drops",
    )
    const val MESSAGES = "sound.messages"
    const val LECTURES = "sound.lectures"

    fun raw(id: String): Int? = when (id) {
        "chime" -> R.raw.ab_chime
        "bell" -> R.raw.ab_bell
        "ping" -> R.raw.ab_ping
        "marimba" -> R.raw.ab_marimba
        "soft" -> R.raw.ab_soft
        "alert" -> R.raw.ab_alert
        "drops" -> R.raw.ab_drops
        else -> null
    }

    fun get(key: String) = Saved.prefs.getString(key, "default") ?: "default"
    fun custom(key: String) = Saved.prefs.getString("$key.uri", null)

    fun set(key: String, id: String, uri: String? = null) {
        Saved.prefs.edit().putString(key, id).putString("$key.uri", uri).apply()
    }

    /** Android fixes a channel's sound when it's created, so every sound gets its own channel */
    fun channel(ctx: Context, base: String, name: String, importance: Int, key: String): String {
        val id = get(key)
        val customUri = custom(key)?.takeIf { id == "phone" }
        val sound = customUri?.let { Uri.parse(it) } ?: raw(id)?.let { Uri.parse("android.resource://${ctx.packageName}/$it") }
        val channelId = when {
            sound == null -> base
            customUri != null -> "${base}_c${customUri.hashCode()}"
            else -> "${base}_$id"
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            val c = NotificationChannel(channelId, if (sound == null) name else "$name · ${tr(all.firstOrNull { it.first == id }?.second ?: "Phone sound")}", importance)
            if (sound != null) c.setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            nm.createNotificationChannel(c)
        }
        return channelId
    }

    fun preview(ctx: Context, id: String) {
        val r = raw(id)
        if (r != null) MediaPlayer.create(ctx, r)?.apply { setOnCompletionListener { it.release() }; start() }
        else runCatching { RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
    }
}

@Composable
fun SoundsScreen() {
    val ctx = LocalContext.current
    var messages by remember { mutableStateOf(NotifySounds.get(NotifySounds.MESSAGES)) }
    var lectures by remember { mutableStateOf(NotifySounds.get(NotifySounds.LECTURES)) }
    var picking by remember { mutableStateOf(NotifySounds.MESSAGES) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        @Suppress("DEPRECATION")
        val uri = res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            NotifySounds.set(picking, "phone", uri.toString())
            if (picking == NotifySounds.MESSAGES) messages = "phone" else lectures = "phone"
        }
    }
    @Composable
    fun section(title: String, key: String, value: String, change: (String) -> Unit) {
        Group(header = title) {
            NotifySounds.all.forEachIndexed { i, (id, name) ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().clickable { NotifySounds.set(key, id); change(id); NotifySounds.preview(ctx, id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.weight(1f))
                    if (value == id) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Divider()
            Row(
                Modifier.fillMaxWidth().clickable {
                    picking = key
                    picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL))
                }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("A ringtone from my phone…", Modifier.weight(1f))
                if (value == "phone") Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    Screen("Notification sounds") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { section("Notifications", NotifySounds.MESSAGES, messages) { messages = it } }
            item { section("Lecture reminders", NotifySounds.LECTURES, lectures) { lectures = it } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// MARK: - lectures

@Serializable
data class Lecture(
    val id: String = java.util.UUID.randomUUID().toString(),
    val subject: String = "",
    val hall: String = "",
    /** 1 Sunday ... 7 Saturday */
    val days: List<Int> = emptyList(),
    val hour: Int = 9,
    val minute: Int = 0,
    val enabled: Boolean = true,
) {
    fun timeText(): String {
        val h = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d %s".format(Locale.ENGLISH, h, if (hour < 12) "AM" else "PM")
    }
}

/** Lectures stay on this phone. Each one reminds 10 minutes before it starts, every week. */
object Lectures {
    private const val KEY = "lectures"
    private const val ACTION = "org.asubank.app.LECTURE"
    private const val CHANNEL = "lectures"
    const val BEFORE = 10
    val list = MutableStateFlow<List<Lecture>>(emptyList())

    fun init() {
        list.value = Saved.prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<Lecture>>(it) }.getOrNull() }.orEmpty()
    }

    private fun commit(ctx: Context, new: List<Lecture>) {
        cancelAll(ctx, list.value)
        list.value = new.sortedBy { it.hour * 60 + it.minute }
        Saved.prefs.edit().putString(KEY, json.encodeToString(list.value)).apply()
        schedule(ctx)
    }

    fun save(ctx: Context, l: Lecture) = commit(ctx, if (list.value.any { it.id == l.id }) list.value.map { if (it.id == l.id) l else it } else list.value + l)
    fun remove(ctx: Context, id: String) = commit(ctx, list.value.filter { it.id != id })
    fun setEnabled(ctx: Context, id: String, on: Boolean) = commit(ctx, list.value.map { if (it.id == id) it.copy(enabled = on) else it })

    private fun intent(ctx: Context, id: String, day: Int): PendingIntent {
        val i = Intent(ctx, LectureReceiver::class.java).setAction(ACTION).setData(Uri.parse("asubank://lecture/$id/$day"))
            .putExtra("id", id)
        return PendingIntent.getBroadcast(ctx, "$id$day".hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancelAll(ctx: Context, lectures: List<Lecture>) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        lectures.forEach { l -> (1..7).forEach { d -> am.cancel(intent(ctx, l.id, d)) } }
    }

    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** The next time this lecture on this weekday should remind, 10 minutes early */
    private fun nextFire(l: Lecture, day: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_WEEK, day)
        c.set(Calendar.HOUR_OF_DAY, l.hour)
        c.set(Calendar.MINUTE, l.minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MINUTE, -BEFORE)
        val now = System.currentTimeMillis()
        while (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 7)
        return c.timeInMillis
    }

    @android.annotation.SuppressLint("ScheduleExactAlarm", "MissingPermission")
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        list.value.filter { it.enabled }.forEach { l ->
            l.days.forEach { d ->
                val at = nextFire(l, d)
                val p = intent(ctx, l.id, d)
                if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        }
    }

    fun notify(ctx: Context, id: String?) {
        val l = list.value.firstOrNull { it.id == id } ?: return
        if (!l.enabled) return
        val channel = NotifySounds.channel(ctx, CHANNEL, tr("Lecture reminders"), NotificationManager.IMPORTANCE_HIGH, NotifySounds.LECTURES)
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("openTab", Routes.LECTURES)
        val tap = PendingIntent.getActivity(ctx, l.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (l.hall.isEmpty()) trf("Starts at %s", l.timeText()) else trf("Hall %s · starts at %s", l.hall, l.timeText())
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF8C1D40.toInt())
            .setContentTitle(trf("%s in 10 minutes", l.subject))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(l.id.hashCode(), n) }
    }

    /** The next lecture still to come today */
    fun nextToday(): Lecture? {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val day = c.get(Calendar.DAY_OF_WEEK)
        return list.value.filter { it.enabled && day in it.days && it.hour * 60 + it.minute >= now }.minByOrNull { it.hour * 60 + it.minute }
    }
}

/** Fires the reminder and sets the next one; sets them all again after a restart */
class LectureReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Disk.ctx = ctx.applicationContext
        Saved.init(ctx.applicationContext)
        Lang.init(ctx.applicationContext)
        Lectures.init()
        if (intent.action?.endsWith("LECTURE") == true) Lectures.notify(ctx, intent.getStringExtra("id"))
        Lectures.schedule(ctx)
    }
}

private fun dayNames(short: Boolean): List<String> {
    val locale = Locale(Lang.code)
    val s = java.text.DateFormatSymbols(locale)
    val names = if (short) s.shortWeekdays else s.weekdays
    return (1..7).map { names[it] }
}

@Composable
fun LecturesScreen(back: Boolean = true) {
    val ctx = LocalContext.current
    val list by Lectures.list.collectAsState()
    var editing by remember { mutableStateOf<Lecture?>(null) }
    var exact by remember { mutableStateOf(Lectures.canExact(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val days = remember { dayNames(false) }
    editing?.let { l -> LectureDialog(l) { editing = null } }
    Screen("My lectures", back = back, large = !back, actions = {
        IconButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            editing = Lecture()
        }) { Icon(Icons.Filled.Add, tr("Add a lecture")) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            if (!exact && list.isNotEmpty()) item {
                Group(footer = "Without this, Android may ring a few minutes late.") {
                    ListRow("Allow exact reminders", icon = Icons.Filled.Alarm, iconColor = Brand.orange) {
                        if (Build.VERSION.SDK_INT >= 31) runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }
                        exact = Lectures.canExact(ctx)
                    }
                }
            }
            if (list.isEmpty()) item {
                EmptyState(Icons.Filled.Alarm, "Never miss a lecture", "Add your subject, hall and time. We'll remind you 10 minutes before it starts.")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                        editing = Lecture()
                    }) { Text("Add a lecture") }
                }
            }
            (1..7).forEach { d ->
                val today = list.filter { d in it.days }
                if (today.isNotEmpty()) item(key = "day$d") {
                    Group(header = days[d - 1]) {
                        today.forEachIndexed { i, l ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().clickable { editing = l }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.subject, fontWeight = FontWeight.SemiBold)
                                    Text(if (l.hall.isEmpty()) l.timeText() else "${l.timeText()} · ${tr("Hall")} ${l.hall}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(l.enabled, { Lectures.setEnabled(ctx, l.id, it) })
                            }
                        }
                    }
                }
            }
            item {
                val nav = LocalNav.current
                Group { ListRow("Notification sounds", icon = Icons.Filled.MusicNote) { nav.navigate("sounds") } }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LectureDialog(start: Lecture, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = Lectures.list.value.none { it.id == start.id }
    var subject by remember { mutableStateOf(start.subject) }
    var hall by remember { mutableStateOf(start.hall) }
    var hour by remember { mutableIntStateOf(start.hour) }
    var minute by remember { mutableIntStateOf(start.minute) }
    val days = remember { mutableStateListOf<Int>().apply { addAll(start.days) } }
    val names = remember { dayNames(true) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Add a lecture" else "Edit lecture") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hall, { hall = it }, label = { Text("Hall") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, false).show() }, Modifier.fillMaxWidth()) {
                    Text(tr("Starts at") + ": " + start.copy(hour = hour, minute = minute).timeText())
                }
                Text("Days", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d ->
                        val on = d in days
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { if (on) days.remove(d) else days.add(d) },
                            contentAlignment = Alignment.Center,
                        ) { Text(names[d - 1], fontSize = 10.sp, maxLines = 1, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                Text("We'll remind you 10 minutes before, every week on these days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isNew) TextButton(onClick = { Lectures.remove(ctx, start.id); onClose() }) { Text("Delete this lecture", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = subject.isNotBlank() && days.isNotEmpty(), onClick = {
                Lectures.save(ctx, start.copy(subject = subject.trim(), hall = hall.trim(), days = days.toList(), hour = hour, minute = minute))
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Home: what's next, when there's a lecture coming up today */
@Composable
fun NextLectureCard() {
    val nav = LocalNav.current
    val list by Lectures.list.collectAsState()
    val next = remember(list) { Lectures.nextToday() } ?: return
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { nav.openTab(Routes.LECTURES) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Next lecture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(next.subject, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (next.hall.isEmpty()) next.timeText() else "${next.timeText()} · ${tr("Hall")} ${next.hall}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// MARK: - tab bar

/** Which tabs sit between Home and More. Apps only. */
object TabPrefs {
    private const val KEY = "tabs"
    private const val DEFAULT = "search,chat,friends"
    /** route, name */
    val options = listOf(
        Routes.SEARCH to "Search", Routes.CHAT to "Chat", Routes.FRIENDS to "Friends",
        Routes.SAVED to "Saved", Routes.LECTURES to "My lectures", Routes.HACKS to "Life hacks",
    )
    val chosen = MutableStateFlow(DEFAULT.split(","))
    fun init() { chosen.value = parse(Saved.prefs.getString(KEY, DEFAULT) ?: DEFAULT) }
    private fun parse(s: String) = s.split(",").filter { id -> options.any { it.first == id } }.ifEmpty { listOf(Routes.SEARCH) }
    fun set(list: List<String>) {
        chosen.value = list
        Saved.prefs.edit().putString(KEY, list.joinToString(",")).apply()
    }
    fun reset() = set(DEFAULT.split(","))
}

@Composable
fun TabBarScreen() {
    val chosen by TabPrefs.chosen.collectAsState()
    val user by Account.user.collectAsState()
    val limit = if (user?.isAdmin == true) 2 else 3
    val ids = chosen.take(limit)
    Screen("Customize tab bar") { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Group(header = "In your tab bar", footer = trf("Home and More always stay, and you can add up to %d more.", limit)) {
                    ListRow("Home", icon = Icons.Filled.Lock)
                    ids.forEachIndexed { i, id ->
                        Divider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(TabPrefs.options.first { it.first == id }.second, Modifier.weight(1f))
                            IconButton(enabled = i > 0, onClick = { TabPrefs.set(ids.toMutableList().apply { add(i - 1, removeAt(i)) }) }) { Icon(Icons.Filled.ArrowUpward, null) }
                            IconButton(enabled = i < ids.size - 1, onClick = { TabPrefs.set(ids.toMutableList().apply { add(i + 1, removeAt(i)) }) }) { Icon(Icons.Filled.ArrowDownward, null) }
                            IconButton(enabled = ids.size > 1, onClick = { TabPrefs.set(ids - id) }) { Icon(Icons.Filled.RemoveCircle, tr("Remove")) }
                        }
                    }
                    if (user?.isAdmin == true) { Divider(); ListRow("Admin", icon = Icons.Filled.Lock) }
                    Divider()
                    ListRow("More", icon = Icons.Filled.Lock)
                }
            }
            item {
                Group(header = "Add a tab") {
                    TabPrefs.options.filter { it.first !in ids }.forEachIndexed { i, (id, name) ->
                        if (i > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = ids.size < limit) { TabPrefs.set(ids + id) }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.AddCircle, null, tint = if (ids.size < limit) Brand.green else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(name)
                        }
                    }
                }
            }
            item { Group { ListRow("Reset to the default", icon = Icons.Filled.Refresh) { TabPrefs.reset() } } }
        }
    }
}
