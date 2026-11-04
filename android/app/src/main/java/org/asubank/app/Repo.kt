package org.asubank.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

object Net {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Body text, or null on any failure / non-200 */
    suspend fun text(url: String, noCache: Boolean = true): String? = withContext(Dispatchers.IO) {
        try {
            val b = Request.Builder().url(url).header("User-Agent", UA)
            if (noCache) b.header("Cache-Control", "no-cache")
            http.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        } catch (e: Exception) { null }
    }

    const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36 ASUBankApp"
}

/** Small JSON files kept in app storage so the app works offline */
object Disk {
    lateinit var ctx: Context
    fun file(name: String) = File(ctx.filesDir, name)
    fun read(name: String): String? = try { file(name).takeIf { it.exists() }?.readText() } catch (e: Exception) { null }
    fun write(name: String, s: String) { try { file(name).writeText(s) } catch (_: Exception) {} }
}

/** The material catalog (materials.json) */
object Library {
    val subjects = MutableStateFlow<List<Subject>>(emptyList())
    val items = MutableStateFlow<List<Indexed>>(emptyList())
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    private var raw: String? = null

    fun categories(): List<Pair<String, List<Subject>>> =
        subjects.value.filter { it.c != "Archive" }.groupBy { it.c }.toList()

    fun subject(path: String) = subjects.value.firstOrNull { it.p == path }
    fun items(s: Subject) = items.value.filter { it.m.p == s.p }

    suspend fun load() {
        if (items.value.isEmpty()) withContext(Dispatchers.IO) { Disk.read("materials.json") }?.let { apply(it) }
        loading.value = true
        val t = Net.text(AB.CATALOG)
        if (t != null && apply(t)) withContext(Dispatchers.IO) { Disk.write("materials.json", t) }
        failed.value = items.value.isEmpty()
        loading.value = false
    }

    /** Re-applies the catalog after shortcut / broken-link data changed */
    suspend fun reindex() { raw?.let { val t = it; raw = null; apply(t) } }

    /** Parsing stalled the screen at every launch, so it runs off the main thread, and only when the catalog actually changed */
    private suspend fun apply(t: String): Boolean {
        if (t == raw) return true
        return try {
            val (c, indexed) = withContext(Dispatchers.Default) {
                val c = json.decodeFromString(Catalog.serializer(), t)
                // Links the server found dead are hidden (tools/check_links.py -> broken.json)
                c to c.items.filter { DriveLinks.broken(it.u) == null }.map { AB.index(it) }
            }
            raw = t
            subjects.value = c.subjects
            items.value = indexed
            true
        } catch (e: Exception) { false }
    }
}

/** All asubank.org pages as native content */
object Pages {
    val pages = MutableStateFlow<Map<String, SitePage>>(emptyMap())
    val loaded = MutableStateFlow(false)
    private val ser = MapSerializer(String.serializer(), SitePage.serializer())

    private var raw: String? = null

    suspend fun load() {
        if (pages.value.isEmpty()) withContext(Dispatchers.IO) { Disk.read("pages.json") }?.let { apply(it) }
        val t = Net.text(AB.PAGES)
        if (t != null && apply(t)) withContext(Dispatchers.IO) { Disk.write("pages.json", t) }
        loaded.value = true
    }

    private suspend fun apply(t: String): Boolean {
        if (t == raw) return true
        return try {
            val parsed = withContext(Dispatchers.Default) { json.decodeFromString(ser, t) }
            raw = t
            pages.value = parsed
            true
        } catch (e: Exception) { false }
    }
}

/** Drive shortcuts resolved to their real file/folder, and links that no longer work (tools/check_links.py) */
object DriveLinks {
    @Volatile private var map: Map<String, List<String>> = emptyMap()
    @Volatile private var dead: Map<String, String> = emptyMap()
    private val mapSer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    private val deadSer = MapSerializer(String.serializer(), String.serializer())

    fun init() {
        Disk.read("drive-map.json")?.let { runCatching { map = json.decodeFromString(mapSer, it) } }
        Disk.read("broken.json")?.let { runCatching { dead = json.decodeFromString(deadSer, it) } }
    }

    /** True when something changed */
    suspend fun refresh(): Boolean {
        var changed = false
        Net.text("${AB.SITE}/assets/app/drive-map.json")?.let { t ->
            runCatching { json.decodeFromString(mapSer, t) }.getOrNull()?.let { if (it != map) { map = it; changed = true }; Disk.write("drive-map.json", t) }
        }
        Net.text("${AB.SITE}/assets/app/broken.json")?.let { t ->
            runCatching { json.decodeFromString(deadSer, t) }.getOrNull()?.let { if (it != dead) { dead = it; changed = true }; Disk.write("broken.json", t) }
        }
        return changed
    }

    /** (target id, is folder) */
    fun target(id: String): Pair<String, Boolean>? = map[id]?.let { t -> t.firstOrNull()?.let { it to (t.getOrNull(1) == "folder") } }
    fun broken(u: String): String? = if (RemovedFiles.has(u)) "This file was removed from ASU Bank." else dead[u]
}

/** Files admins removed: hidden everywhere, because the other indexes keep listing a file for a while */
object RemovedFiles {
    val ids = MutableStateFlow<Set<String>>(emptySet())
    private const val KEY = "deletedFiles.v1"

    fun init() { Saved.prefs.getStringSet(KEY, null)?.let { ids.value = it.toSet() } }

    private fun store(set: Set<String>) {
        ids.value = set
        Saved.prefs.edit().putStringSet(KEY, set).apply()
    }

    /** True when the list changed */
    suspend fun refresh(): Boolean {
        val r = runCatching { AccountsApi.call("deletedFiles") }.getOrNull() ?: return false
        val list = (r["ids"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.prim() }?.toSet() ?: return false
        if (list == ids.value) return false
        store(list)
        return true
    }

    fun add(new: List<String>) = store(ids.value + new)

    /** By the file's own id, or through the shortcut that points to it */
    fun has(u: String, set: Set<String> = ids.value): Boolean {
        if (set.isEmpty()) return false
        val id = AB.driveId(u) ?: return false
        return id in set || DriveLinks.target(id)?.first in set
    }
}

/** The list from last time, so Latest uploads opens with files in it while the new list loads */
@kotlinx.serialization.Serializable
data class SavedFile(val m: Material, val kind: String, val date: String, val size: Long)

object LatestCache {
    fun read(): List<DriveResult> = Disk.read("latest-uploads.json")?.let { t ->
        runCatching { json.decodeFromString<List<SavedFile>>(t) }.getOrNull()
    }.orEmpty().filter { DriveLinks.broken(it.m.u) == null }.map { f ->
        DriveResult(f.m, runCatching { Kind.valueOf(f.kind) }.getOrDefault(Kind.FILE), f.date, f.size)
    }

    fun write(list: List<DriveResult>) {
        runCatching { Disk.write("latest-uploads.json", json.encodeToString(list.take(400).map { SavedFile(it.m, it.kind.name, it.date, it.size) })) }
    }
}

/** Saved files, recently opened files and recent searches (kept on this device) */
object Saved {
    lateinit var prefs: SharedPreferences
    val saved = MutableStateFlow<List<SavedItem>>(emptyList())
    val recent = MutableStateFlow<List<SavedItem>>(emptyList())
    val searches = MutableStateFlow<List<String>>(emptyList())
    private val ser = ListSerializer(SavedItem.serializer())
    private val sser = ListSerializer(String.serializer())

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("ab", Context.MODE_PRIVATE)
        saved.value = read("saved")
        recent.value = read("recent")
        searches.value = prefs.getString("searches", null)?.let { runCatching { json.decodeFromString(sser, it) }.getOrNull() } ?: emptyList()
    }

    private fun read(k: String) = prefs.getString(k, null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()
    private fun write(k: String, v: List<SavedItem>) = prefs.edit().putString(k, json.encodeToString(ser, v)).apply()

    fun isSaved(u: String) = saved.value.any { it.u == u }
    fun toggle(m: Material) {
        saved.value = if (isSaved(m.u)) saved.value.filter { it.u != m.u }
        else listOf(SavedItem(m.u, m.t, m.s ?: "", System.currentTimeMillis())) + saved.value
        write("saved", saved.value)
    }
    fun remove(u: String) { saved.value = saved.value.filter { it.u != u }; write("saved", saved.value) }
    fun opened(m: Material) {
        recent.value = (listOf(SavedItem(m.u, m.t, m.s ?: "", System.currentTimeMillis())) + recent.value.filter { it.u != m.u }).take(30)
        write("recent", recent.value)
    }
    fun clearRecent() { recent.value = emptyList(); write("recent", emptyList()) }
    fun searched(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        searches.value = (listOf(t) + searches.value.filter { !it.equals(t, true) }).take(12)
        prefs.edit().putString("searches", json.encodeToString(sser, searches.value)).apply()
    }
    fun clearSearches() { searches.value = emptyList(); prefs.edit().remove("searches").apply() }
}

/** Remote switches in app-config.json and the newest APK on GitHub Releases */
object AppConfig {
    val donate = MutableStateFlow(true)
    val latest = MutableStateFlow<String?>(null)
    val mustUpdate = MutableStateFlow(false)
    val offerUpdate = MutableStateFlow(false)
    val apkUrl = MutableStateFlow<String?>(null)
    val notes = MutableStateFlow<String?>(null)
    val checking = MutableStateFlow(false)
    private var checkedAt = 0L

    val current: String get() = BuildConfig.VERSION_NAME
    val updateAvailable: Boolean get() = latest.value?.let { AB.compare(current, it) < 0 } ?: false

    suspend fun check(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - checkedAt < 30 * 60_000) return
        checkedAt = System.currentTimeMillis()
        checking.value = true
        var minimum: String? = null
        var configLatest: String? = null
        Net.text(AB.CONFIG)?.let { t ->
            runCatching {
                val o = json.parseToJsonElement(t).jsonObject
                donate.value = !BuildConfig.STORE_BUILD && (o["donate"] as? JsonPrimitive)?.booleanOrNull ?: !BuildConfig.STORE_BUILD
                (o["android"] as? JsonObject)?.let { a ->
                    minimum = (a["minimum"] as? JsonPrimitive)?.contentOrNull
                    configLatest = (a["latest"] as? JsonPrimitive)?.contentOrNull
                    (a["notes"] as? JsonPrimitive)?.contentOrNull?.let { notes.value = it }
                }
            }
        }
        // The newest android-v* GitHub Release, with its APK
        var relVersion: String? = null
        // a Play build only ever updates through Google Play
        if (BuildConfig.STORE_BUILD) apkUrl.value = "https://play.google.com/store/apps/details?id=org.asubank.app"
        else Net.text(AB.RELEASES)?.let { t ->
            runCatching {
                for (r in json.parseToJsonElement(t).jsonArray) {
                    val o = r.jsonObject
                    val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: continue
                    if (!tag.startsWith("android-v") || o["draft"]?.jsonPrimitive?.booleanOrNull == true) continue
                    val v = tag.removePrefix("android-v")
                    if (relVersion != null && AB.compare(v, relVersion!!) <= 0) continue
                    val apk = (o["assets"] as? JsonArray)?.firstOrNull {
                        it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true
                    }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                    if (apk != null) { relVersion = v; apkUrl.value = apk }
                }
            }
        }
        // Only versions that can actually be downloaded count
        latest.value = relVersion ?: configLatest?.takeIf { apkUrl.value != null }
        checking.value = false
        if (apkUrl.value == null) return
        minimum?.let { if (AB.compare(current, it) < 0) mustUpdate.value = true }
        val n = latest.value ?: return
        if (AB.compare(current, n) < 0 && !mustUpdate.value) {
            val key = "update.offered.$n"
            if (force || !Saved.prefs.getBoolean(key, false)) {
                Saved.prefs.edit().putBoolean(key, true).apply()
                offerUpdate.value = true
            }
        }
    }
}

/** Shared state between tabs (Home hands a query to Search) */
object AppState {
    val query = MutableStateFlow("")
    val pendingSearch = MutableStateFlow<String?>(null)
}

/** Drive search (Apps Script); never called with an empty query */
object DriveSearch {
    fun size(e: kotlinx.serialization.json.JsonElement?): Long = (e as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0L

    fun map(files: List<DriveFile>, source: String): List<DriveResult> = files
        .filter { DriveLinks.broken(it.url) == null && it.url.isNotEmpty() }
        .map { f -> DriveResult(Material(u = f.url, t = f.name, s = source, d = f.dateUploaded), AB.kind(f.mimeType, f.url, f.name), f.dateUploaded ?: "", size(f.size)) }

    suspend fun search(q: String): List<DriveResult>? {
        if (q.isBlank()) return emptyList()
        val url = "${AB.DRIVE_SEARCH}?search=${android.net.Uri.encode(q)}&sort=date&order=desc&t=${System.currentTimeMillis() / 1000}"
        val t = Net.text(url) ?: return null
        val r = runCatching { json.decodeFromString(DriveReply.serializer(), t) }.getOrNull() ?: return null
        if (r.success != true) return null
        return map(r.files ?: emptyList(), "Google Drive")
    }

    /** Your uploads, then the upload folder, then the indexed Drive (each file once), newest first */
    suspend fun latest(): List<DriveResult>? = coroutineScope {
        val removed = async { RemovedFiles.refresh() }
        val folder = async { UploadFolder.load() }
        val indexed = Net.text("${AB.LATEST}?t=${System.currentTimeMillis() / 1000}&sort=date&order=desc")
            ?.let { t -> runCatching { json.decodeFromString(DriveReply.serializer(), t) }.getOrNull() }
            ?.takeIf { it.success == true }?.let { map(it.files ?: emptyList(), "Latest uploads") } ?: emptyList()
        val mine = MyUploads.all().map { DriveResult(Material(u = it.url, t = it.name, s = "Your upload", d = it.date), AB.kind(it.url, it.name), it.date, 0) }
        removed.await()
        val merged = (mine + folder.await() + indexed).filter { DriveLinks.broken(it.m.u) == null }.distinctBy { AB.driveId(it.m.u) ?: it.m.u }
        if (merged.isEmpty()) null else merged.sortedByDescending { it.date }
    }
}

/** Files this phone uploaded, shown in Latest uploads right away (before Drive indexes them) */
object MyUploads {
    @kotlinx.serialization.Serializable data class Item(val name: String, val url: String, val date: String)
    private const val KEY = "myUploads.v1"

    fun all(): List<Item> = Saved.prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<Item>>(it) }.getOrNull() } ?: emptyList()

    fun add(name: String, url: String) {
        if (url.isEmpty()) return
        val now = java.time.OffsetDateTime.now().toString()
        val list = (listOf(Item(name, url, now)) + all().filter { it.url != url }).take(50)
        Saved.prefs.edit().putString(KEY, json.encodeToString(list)).apply()
    }

    fun remove(url: String) {
        val id = AB.driveId(url) ?: url
        Saved.prefs.edit().putString(KEY, json.encodeToString(all().filter { (AB.driveId(it.url) ?: it.url) != id })).apply()
    }
}

/**
 * The folder the upload form saves into ("asubank.xyz Upload+"), read like any shared folder.
 * The latest-uploads script indexes the older Drive folders only, so new uploads come from here.
 */
object UploadFolder {
    const val ID = "1ubxr47KfFwm7ocn0ikCpJLp79-dxe8aB"

    /** Drive's folder view shows "3:45 PM" (today), "Nov 2" (this year) or "9/8/25" */
    fun isoDate(s: String): String {
        val t = s.trim()
        val today = java.time.LocalDate.now()
        if (t.contains(":")) return today.toString()
        runCatching { return java.time.LocalDate.parse(t, java.time.format.DateTimeFormatter.ofPattern("M/d/yy", java.util.Locale.US)).toString() }
        runCatching {
            var d = java.time.LocalDate.parse("$t ${today.year}", java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy", java.util.Locale.US))
            if (d.isAfter(today)) d = d.minusYears(1)
            return d.toString()
        }
        return ""
    }

    suspend fun load(): List<DriveResult> = (Folders.load(ID) ?: emptyList()).filter { !it.name.startsWith("[TEST]") && !it.name.contains("upload-test", true) }.map { e ->
        val d = isoDate(e.modified)
        DriveResult(Material(u = e.url, t = e.name, s = "Shared by students", d = d.ifEmpty { null }), e.kind, d, 0)
    }
}

/** App size and cache the More tab can clear. Saved materials and settings are never touched. */
object Storage {
    private fun size(f: File?): Long = if (f == null || !f.exists()) 0 else if (f.isFile) f.length() else (f.listFiles()?.sumOf { size(it) } ?: 0)

    /** Kept until the cache is cleared: the More screen asks every time you come back to it */
    private var known: Triple<Long, Long, Long>? = null

    suspend fun measure(ctx: Context): Triple<Long, Long, Long> = known ?: withContext(Dispatchers.IO) {
        val app = size(File(ctx.applicationInfo.sourceDir))
        val cache = size(ctx.cacheDir)
        val data = size(ctx.filesDir) + size(File(ctx.applicationInfo.dataDir, "shared_prefs"))
        Triple(app, cache, data).also { known = it }
    }

    suspend fun clear(ctx: Context) = withContext(Dispatchers.IO) {
        known = null
        // downloaded materials and the web view cache both live in cacheDir
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }
}
