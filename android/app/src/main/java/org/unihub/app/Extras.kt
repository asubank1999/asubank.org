package org.unihub.app

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

// MARK: University halls

data class Building(val code: String, val name: String, val ar: String)

object Halls {
    val buildings = listOf(
        Building("1", "College of IT", "كلية تكنولوجيا المعلومات"),
        Building("2", "College of Law", "كلية الحقوق"),
        Building("3", "Workshops", "الورش"),
        Building("4", "College of Pharmacy", "كلية الصيدلة"),
        Building("5", "College of Business", "كلية إدارة الأعمال"),
        Building("6", "Gymnasium", "الصالة الرياضية"),
        Building("7", "College of Nursing", "كلية التمريض"),
        Building("8", "College of Arts", "كلية الآداب"),
        Building("9", "College of Engineering", "كلية الهندسة"),
        Building("10", "Deanship", "العمادة"),
        Building("21", "College of Dentistry", "كلية طب الأسنان"),
        Building("G/B", "Clinics Building", "مبنى العيادات"),
    )

    data class Decoded(val building: String, val buildingDigits: String, val floor: Int, val room: String) {
        val place get() = buildings.firstOrNull { it.code == building }
    }

    /** 1206 = building 1, floor 2, room 06. Five digits (10xxx, 21xxx) have a two-digit building. */
    fun decode(input: String): Decoded? {
        val d = input.filter { it.isDigit() }
        if (d.length != 4 && d.length != 5) return null
        val b = if (d.length == 5) 2 else 1
        return Decoded(d.substring(0, b).trimStart('0').ifEmpty { "0" }, d.substring(0, b), d[b].digitToInt(), d.substring(b + 1))
    }

    fun floorName(f: Int) = when (f) { 0 -> "Ground floor"; 1 -> "1st floor"; 2 -> "2nd floor"; 3 -> "3rd floor"; else -> trf("Floor %d", f) }
    fun floorAr(f: Int) = listOf("الطابق الأرضي", "الطابق الأول", "الطابق الثاني", "الطابق الثالث", "الطابق الرابع", "الطابق الخامس", "الطابق السادس", "الطابق السابع", "الطابق الثامن", "الطابق التاسع").getOrElse(f) { "الطابق $f" }
}

private val BuildingBlue = Color(0xFF0A84FF)
private val FloorOrange = Color(0xFFE8590C)
private val RoomGreen = Color(0xFF1F8A3E)

@Composable
fun HallsScreen() {
    var number by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val decoded = Halls.decode(number)
    Screen("University halls", large = true) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding()) {
            item {
                Group(header = "Find a hall") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Type the number written on the door or in your timetable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = number,
                            onValueChange = { v ->
                                val digits = v.filter { it.isDigit() }.take(5)
                                if (Halls.decode(digits) != null && Halls.decode(number) == null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                number = digits
                            },
                            placeholder = { Text("e.g. 1206") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = { if (number.isNotEmpty()) IconButton(onClick = { number = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AnimatedVisibility(decoded != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            decoded?.let { HallResult(it) }
                        }
                        if (decoded == null && number.isNotEmpty()) Text(
                            if (number.length < 4) "Keep typing: hall numbers have 4 digits (5 for buildings 10 and 21)." else "That doesn't look like a hall number.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Group(header = "How to read a hall number") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Digit("1", BuildingBlue); Spacer(Modifier.width(4.dp)); Digit("2", FloorOrange); Spacer(Modifier.width(4.dp)); Digit("06", RoomGreen)
                        }
                        Legend(BuildingBlue, "Building", "1 = College of IT")
                        Legend(FloorOrange, "Floor", "2 = 2nd floor")
                        Legend(RoomGreen, "Room", "06 = room 06 on that floor")
                        Text("Buildings 10 and 21 use two digits, so their halls have 5 digits: 21105 = Dentistry, 1st floor, room 05.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BiText("الرقم الأول للمبنى، ثم الطابق، ثم رقم القاعة. مثال: 1206 = كلية تكنولوجيا المعلومات، الطابق الثاني، القاعة 06",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Group(header = "Building numbers", footer = "Tap a building to start typing one of its halls.") {
                    Halls.buildings.forEachIndexed { i, b ->
                        if (i > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().clickable { if (b.code.all { it.isDigit() }) number = b.code }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.heightIn(min = 36.dp).widthIn(min = 36.dp).background(BuildingBlue, RoundedCornerShape(9.dp)).padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(b.code, color = Color.White, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.name, style = MaterialTheme.typography.bodyLarge)
                                Text(b.ar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HallResult(d: Halls.Decoded) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Digit(d.buildingDigits, BuildingBlue); Spacer(Modifier.width(4.dp)); Digit(d.floor.toString(), FloorOrange); Spacer(Modifier.width(4.dp)); Digit(d.room, RoomGreen)
            }
            ResultRow(BuildingBlue, Icons.Filled.Domain, d.place?.name ?: trf("Unknown building %s", d.building), d.place?.ar ?: "Not in the list of buildings")
            ResultRow(FloorOrange, Icons.Filled.Layers, Halls.floorName(d.floor), Halls.floorAr(d.floor))
            ResultRow(RoomGreen, Icons.Filled.MeetingRoom, trf("Room %s", d.room.toString()), if (Lang.code == "ar") "" else "القاعة ${d.room}")
        }
    }
}

@Composable
private fun ResultRow(color: Color, icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(color, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Digit(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color.copy(alpha = 0.13f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
private fun Legend(color: Color, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: Rate your teachers

/**
 * The ratings live in the "Rate Your Teacher" Apps Script, which only talks to its own page (google.script.run).
 * A hidden WebView loads that page; a script injected into every frame runs our calls inside the rating page
 * and hands the results back through a JavaScript interface.
 */
object Ratings {
    const val SCRIPT = "https://script.google.com/macros/s/AKfycbwdw7QBPLeBz1X-xBP6JeZEF5t7i4Ru1VfWshVqanRKd6xN0J2gQqvvovnTrYI_bpKt/exec"
    private val banned = listOf("fuck", "shit", "bitch", "asshole", "idiot", "dumb", "retard", "gay", "whore", "slut", "stupid", "kill", "suicide", "sex", "porn", "dick", "pussy", "cock", "boob")
    fun rude(s: String): Boolean {
        val t = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).filter { it in 'a'..'z' || it in '0'..'9' }
        return banned.any { t.contains(it) }
    }
    fun color(v: Double) = when { v <= 3 -> Color(0xFFE5372D); v <= 6 -> Color(0xFFF59F00); else -> Color(0xFF1F8A3E) }
    val clientId: String get() {
        Saved.prefs.getString("rate.clientId", null)?.let { return it }
        val id = "cid_" + UUID.randomUUID().toString().replace("-", "")
        Saved.prefs.edit().putString("rate.clientId", id).apply()
        return id
    }
}

data class Teacher(val name: String, val average: Double, val count: Int)

private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }

private fun teacherOf(e: JsonElement?): Teacher? {
    val o = e as? JsonObject ?: return null
    val n = ((o["display"] ?: o["name"]) as? JsonPrimitive)?.content?.trim().orEmpty()
    if (n.isEmpty()) return null
    return Teacher(n, o["average"].num() ?: 0.0, (o["count"] as? JsonPrimitive)?.intOrNull ?: o["count"].num()?.toInt() ?: 0)
}

object RatingBridge {
    enum class Status { CONNECTING, READY, FAILED }

    val status = MutableStateFlow(Status.CONNECTING)
    val teachers = MutableStateFlow<List<Teacher>>(emptyList())
    val loading = MutableStateFlow(false)

    private val queue = ConcurrentLinkedQueue<String>()
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<Result<JsonElement>>>()
    private val ids = AtomicInteger()
    private var web: WebView? = null

    private const val PROBE = """
(function(){
  function ready(){ try { return window.google && google.script && google.script.run && document.getElementById('teacherInput') && window.ABBridge; } catch(e) { return false; } }
  var told = false;
  function loop(){
    if (!ready()) { setTimeout(loop, 250); return; }
    if (!told) { told = true; ABBridge.ready(); }
    var c = ABBridge.next();
    while (c) {
      (function(c){
        var r = google.script.run
          .withSuccessHandler(function(v){ ABBridge.done(c.id, JSON.stringify(v === undefined ? null : v), ''); })
          .withFailureHandler(function(e){ ABBridge.done(c.id, '', (e && e.message) ? e.message : String(e)); });
        r[c.fn].apply(r, c.args);
      })(JSON.parse(c));
      c = ABBridge.next();
    }
    setTimeout(loop, 120);
  }
  loop();
})();
"""

    @Suppress("unused")
    private object Js {
        @JavascriptInterface fun ready() { if (status.value != Status.READY) { status.value = Status.READY } }
        @JavascriptInterface fun next(): String? = queue.poll()
        @JavascriptInterface fun done(id: Int, result: String, error: String) {
            val d = waiting.remove(id) ?: return
            if (error.isNotEmpty()) d.complete(Result.failure(Exception(error)))
            else d.complete(Result.success(try { json.parseToJsonElement(result) } catch (e: Exception) { JsonNull }))
        }
    }

    val supported get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "RequiresFeature")
    fun create(ctx: android.content.Context): WebView = WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(4, 4)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame && status.value == Status.CONNECTING) status.value = Status.FAILED
            }
        }
        addJavascriptInterface(Js, "ABBridge")
        WebViewCompat.addDocumentStartJavaScript(this, PROBE, setOf("*"))
        web = this
    }

    suspend fun connect() {
        if (!supported) { status.value = Status.FAILED; return }
        if (status.value == Status.READY) return
        status.value = Status.CONNECTING
        web?.loadUrl(Ratings.SCRIPT)
        val ok = withTimeoutOrNull(30_000) { while (status.value != Status.READY) delay(100) } != null
        if (!ok && status.value == Status.CONNECTING) status.value = Status.FAILED
        if (status.value == Status.READY) refresh()
    }

    fun release(w: WebView) { if (web === w) { web = null; status.value = Status.CONNECTING }; w.destroy() }

    private suspend fun call(fn: String, vararg args: Any): Result<JsonElement> {
        val id = ids.incrementAndGet()
        val d = CompletableDeferred<Result<JsonElement>>()
        waiting[id] = d
        queue.add(buildJsonObject {
            put("id", id); put("fn", fn)
            putJsonArray("args") { args.forEach { a -> add(if (a is Number) JsonPrimitive(a) else JsonPrimitive(a.toString())) } }
        }.toString())
        return withTimeoutOrNull(25_000) { d.await() } ?: run { waiting.remove(id); Result.failure(Exception("The ratings server didn't answer. Try again.")) }
    }

    suspend fun refresh() {
        loading.value = true
        call("bootstrap", "").getOrNull()?.let { r ->
            val items = ((r as? JsonObject)?.get("data") as? JsonObject)?.get("items") as? JsonArray
            if (items != null) teachers.value = withContext(Dispatchers.Default) { items.mapNotNull { teacherOf(it) }.distinctBy { it.name.lowercase() } }
        }
        loading.value = false
    }

    /** Null when saved, otherwise the error message */
    suspend fun rate(name: String, rating: Int): String? {
        val r = call("addRating", name, rating, Ratings.clientId)
        return r.exceptionOrNull()?.message?.removePrefix("Error: ") ?: run { refresh(); null }
    }
}

enum class RatingSort(val label: String) { VOTES("Most votes"), TOP("Top rated"), NAME("A–Z") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherRatingsScreen(back: Boolean = true) {
    val nav = LocalNav.current
    var introSeen by remember { mutableStateOf(Saved.prefs.getBoolean("rate.intro.v1", false)) }
    val status by RatingBridge.status.collectAsState()
    val teachers by RatingBridge.teachers.collectAsState()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(RatingSort.VOTES) }
    var rating by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Screen("Rate your teachers", back = back, large = introSeen) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            // the hidden page that talks to the ratings sheet
            AndroidView(factory = { RatingBridge.create(it) }, onRelease = { RatingBridge.release(it) }, modifier = Modifier.size(1.dp).alpha(0f))
            LaunchedEffect(retry) { RatingBridge.connect() }

            if (!introSeen) {
                RatingIntro(onTerms = { nav.openPage("/legal/uni_tos", "Terms of Service") }) {
                    Saved.prefs.edit().putBoolean("rate.intro.v1", true).apply(); introSeen = true
                }
                return@Box
            }
            val nq = AB.norm(query)
            val shown = remember(teachers, nq, sort) {
                val f = teachers.filter { nq.isEmpty() || AB.norm(it.name).contains(nq) }
                when (sort) {
                    RatingSort.TOP -> f.sortedWith(compareByDescending<Teacher> { it.average }.thenByDescending { it.count })
                    RatingSort.VOTES -> f.sortedWith(compareByDescending<Teacher> { it.count }.thenByDescending { it.average })
                    RatingSort.NAME -> f.sortedBy { it.name.lowercase() }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Group {
                        ListRow("Rate a teacher", "Anonymous · takes 5 seconds", icon = Icons.Filled.RateReview, iconColor = Brand.accent,
                            onClick = if (status == RatingBridge.Status.READY) ({ rating = "" }) else null)
                    }
                }
                when (status) {
                    RatingBridge.Status.CONNECTING -> item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ABDots(); Spacer(Modifier.height(12.dp)); Text("Loading ratings…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    RatingBridge.Status.FAILED -> item {
                        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.WifiOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Couldn't load the ratings", style = MaterialTheme.typography.titleMedium)
                            Text("Check your connection and try again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (RatingBridge.supported) Button(onClick = { retry++ }) { Text("Try again") }
                            TextButton(onClick = { nav.openWeb(Ratings.SCRIPT, "Rate your teachers") }) { Text("Open the web version") }
                        }
                    }
                    RatingBridge.Status.READY -> {
                        item {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search teachers") },
                                leadingIcon = { Icon(Icons.Filled.Search, null) },
                                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        item {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                RatingSort.entries.forEachIndexed { i, s ->
                                    SegmentedButton(selected = sort == s, onClick = { sort = s }, shape = SegmentedButtonDefaults.itemShape(i, RatingSort.entries.size)) { Text(s.label) }
                                }
                            }
                        }
                        item {
                            Text(
                                trf("%d TEACHERS RATED", teachers.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 32.dp, top = 14.dp, bottom = 6.dp),
                            )
                        }
                        if (shown.isEmpty()) item {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (query.isEmpty()) "No ratings yet" else trf("No teacher named “%s” yet", query), style = MaterialTheme.typography.titleMedium)
                                Text("Be the first to rate them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = { rating = query }) { Text(if (query.isEmpty()) "Rate a teacher" else trf("Rate %s", query)) }
                            }
                        }
                        items(shown, key = { it.name.lowercase() }) { t ->
                            TeacherRow(t, Modifier.animateItem()) { rating = t.name }
                        }
                        item {
                            Text(
                                "Tap a teacher to add your rating. Averages are out of 10.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 32.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    rating?.let { start ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { rating = null }, sheetState = sheet) {
            RateSheet(start, teachers) { scope.launch { sheet.hide(); rating = null } }
        }
    }
}

@Composable
private fun TeacherRow(t: Teacher, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 48.dp, height = 40.dp).background(Ratings.color(t.average), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(String.format(java.util.Locale.US, "%.1f", t.average), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2)
                LinearProgressIndicator(
                    progress = { (t.average / 10).toFloat().coerceIn(0.03f, 1f) }, color = Ratings.color(t.average),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {}, gapSize = 0.dp,
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                )
                Text(trf("%d votes", t.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RatingIntro(onTerms: () -> Unit, onContinue: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(84.dp).background(Brush.linearGradient(listOf(Brand.accent, Color(0xFFB3305A))), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.RateReview, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
                Text("Rate your teachers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Share your experience and help other students choose their sections.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        IntroPoint(Icons.Filled.VisibilityOff, "100% anonymous", "Nobody, not even the admins, can see who rated.")
                        IntroPoint(Icons.Filled.BarChart, "Real averages", "Every teacher shows an average out of 10 and how many students voted.")
                        IntroPoint(Icons.Filled.PersonAdd, "Teacher not listed?", "Be the first to rate them, and others will join in.")
                        IntroPoint(Icons.Filled.VolunteerActivism, "Be fair", "Rate the teaching, not the person. Rude names are blocked.")
                    }
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Continue", fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onTerms) { Text("Terms of Service") }
                Text("By continuing you agree to the Terms of Service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IntroPoint(icon: ImageVector, title: String, text: String) {
    Row {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RateSheet(start: String, known: List<Teacher>, close: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(start) }
    var score by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val trimmed = name.trim()
    val current = known.firstOrNull { it.name.equals(trimmed, true) }
    val nq = AB.norm(trimmed)
    val suggestions = if (nq.length < 2) emptyList() else known.filter { AB.norm(it.name).contains(nq) && it.name != trimmed }.take(4)
    fun label(n: Int) = when (n) { in 1..2 -> "Very poor"; in 3..4 -> "Poor"; in 5..6 -> "OK"; in 7..8 -> "Good"; else -> "Excellent" }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (start.isEmpty()) "Rate a teacher" else "Rate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = name, onValueChange = { name = it }, enabled = !done, singleLine = true,
            label = { Text("Teacher's full name") }, placeholder = { Text("e.g. Dr. Ahmad Malik") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        )
        suggestions.forEach { t ->
            Row(Modifier.fillMaxWidth().clickable { name = t.name }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↳  ${t.name}", modifier = Modifier.weight(1f))
                Text(String.format(java.util.Locale.US, "%.1f", t.average), color = Ratings.color(t.average), fontWeight = FontWeight.Bold)
            }
        }
        if (current != null && current.count > 0) {
            Text("${tr("Current:")} ${String.format(java.util.Locale.US, "%.1f", current.average)} / 10 · ${trf("%d votes", current.count)}",
                color = Ratings.color(current.average), fontWeight = FontWeight.SemiBold)
        }
        val tone by animateColorAsState(if (score == 0) MaterialTheme.colorScheme.onSurfaceVariant else Ratings.color(score.toDouble()), label = "tone")
        Text(if (score == 0) "Tap a score" else "$score / 10 · ${label(score)}", style = MaterialTheme.typography.titleMedium, color = tone,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        listOf(1, 6).forEach { first ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (first until first + 5).forEach { n ->
                    val on = n <= score
                    val bump by animateFloatAsState(if (n == score) 1.08f else 1f, spring(dampingRatio = 0.5f), label = "bump")
                    Box(
                        Modifier.weight(1f).height(48.dp).scale(bump)
                            .background(if (on) tone else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .clickable(enabled = !done) { score = n; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        message?.let { Text(it, color = if (done) Color(0xFF1F8A3E) else Color(0xFFE8590C), fontWeight = FontWeight.Medium) }
        Button(
            onClick = {
                if (done) { close(); return@Button }
                if (Ratings.rude(trimmed)) { message = "That name isn't allowed."; return@Button }
                saving = true; message = null
                scope.launch {
                    val err = RatingBridge.rate(trimmed, score)
                    saving = false
                    if (err != null) message = err else { done = true; message = "Thanks! Your rating was saved."; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                }
            },
            enabled = !saving && (done || (trimmed.length >= 3 && score > 0)),
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(),
        ) {
            if (saving) ABDots(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (done) "Done" else "Save rating", fontWeight = FontWeight.SemiBold)
        }
        Text("Your rating is anonymous.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}
