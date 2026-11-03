package org.asubank.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// materials.json (made by tools/build_banks.py)

@Serializable
data class Subject(val p: String, val t: String, val ar: String? = null, val c: String = "", val e: String? = null, val n: Int? = null)

@Serializable
data class Material(
    val u: String,
    val t: String,
    val s: String? = null,
    val p: String? = null,
    val c: String? = null,
    val tag: String? = null,
    val d: String? = null, // date the link was added, "YYYY-MM-DD"
)

@Serializable
data class Catalog(val subjects: List<Subject> = emptyList(), val items: List<Material> = emptyList())

/** A material with its search text and kind worked out once */
data class Indexed(val m: Material, val q: String, val kind: Kind, val year: String, val isNew: Boolean)

@Serializable
data class SavedItem(val u: String, val t: String, val s: String = "", val date: Long = 0) {
    val material get() = Material(u = u, t = t, s = s)
}

// pages.json (made from asubank.org by tools/build_app_pages.py)

@Serializable data class PageLink(val t: String = "", val u: String? = null, val s: String? = null, val b: String? = null, val e: String? = null)
@Serializable data class PageFAQ(val q: String = "", val a: String = "", val qa: String? = null, val aa: String? = null)
@Serializable data class PageTable(val h: List<String>? = null, val r: List<List<String>> = emptyList())
@Serializable data class PageHead(val eyebrow: String? = null, val sub: String? = null, val ar: String? = null)

@Serializable
data class PageBlock(
    val head: PageHead? = null,
    val h: String? = null,
    val ar: String? = null,
    val e: String? = null,
    val p: String? = null,
    val links: List<PageLink>? = null,
    val list: List<String>? = null,
    val table: PageTable? = null,
    val img: String? = null,
    val alt: String? = null,
    val faq: List<PageFAQ>? = null,
)

@Serializable data class CalEvent(val d: String, val day: String = "", val n: String = "", val w: Int? = null)
@Serializable data class SitePage(val t: String = "", val b: List<PageBlock> = emptyList(), val cal: List<CalEvent>? = null, val web: Int? = null)

// Google Drive search / latest uploads (Apps Script)

@Serializable
data class DriveFile(
    val name: String = "",
    val url: String = "",
    val mimeType: String? = null,
    val dateUploaded: String? = null,
    val size: JsonElement? = null, // a number, or [] for shortcuts
)

@Serializable data class DriveReply(val success: Boolean? = null, val files: List<DriveFile>? = null)

data class DriveResult(val m: Material, val kind: Kind, val date: String, val size: Long)

enum class Kind(val label: String, val icon: ImageVector, val color: Color) {
    FOLDER("Folder", Icons.Filled.Folder, Color(0xFF0A84FF)),
    PDF("PDF", Icons.Filled.PictureAsPdf, Color(0xFFE5372D)),
    DOC("Document", Icons.Filled.Description, Color(0xFF2B6FD6)),
    PPT("Slides", Icons.Filled.Slideshow, Color(0xFFE8590C)),
    SHEET("Sheet", Icons.Filled.TableChart, Color(0xFF1F8A3E)),
    IMG("Image", Icons.Filled.Image, Color(0xFFAF52DE)),
    VIDEO("Video", Icons.Filled.PlayCircle, Color(0xFFFF2D55)),
    ZIP("Archive", Icons.Filled.FolderZip, Color(0xFF8E8E93)),
    FILE("File", Icons.Filled.InsertDriveFile, Color(0xFF5E5CE6)),
    LINK("Link", Icons.Filled.Link, Color(0xFF5E5CE6)),
    PAGE("Page", Icons.Filled.Article, Color(0xFF8C1D40)),
}

/** Helpers shared with the website (assets/app/app.js) and the iOS app */
object AB {
    const val SITE = "https://asubank.org"
    const val CATALOG = "$SITE/assets/app/materials.json"
    const val PAGES = "$SITE/assets/app/pages.json"
    const val CONFIG = "$SITE/assets/app/app-config.json"
    const val DRIVE_SEARCH = "https://script.google.com/macros/s/AKfycbz9_c0Hr4aOusP5Wskn9fIw6UO6m8k4Esps3wJqKvZv2LQf6qcNFqP99cgU5UFOskVHsQ/exec"
    const val LATEST = "https://script.google.com/macros/s/AKfycbyuzws_2LcDC8nb2KU-02swjehyRf1ivY14EbX1qcJC1chCUTLTOAAwtQ5JlXvNcLez/exec"
    const val UPLOAD = "https://script.google.com/macros/s/AKfycbygD_bFuQN7k9zLKogIip_zEaXmsq9Vw-z6QPfMte1AAuLdq4Vf4-kGIgvy6hgVcOo/exec"
    const val WHATSAPP = "https://chat.whatsapp.com/LS92JgjO26Y5SZK9SVJ9FJ"
    const val RELEASES = "https://api.github.com/repos/asubank1999/asubank.org/releases?per_page=30"
    const val NEW_DAYS = 30L

    private val diacritics = Regex("[ً-ْـ]")
    private val emoji = Regex("✅|✔|⭐|🔥|️")
    private val punct = Regex("""[_\-–—.,:;()\[\]{}|/\\]+""")
    private val spaces = Regex("""\s+""")

    /** Lowercase, strip Arabic diacritics and unify letter forms so "أ" finds "ا" etc. */
    fun norm(s: String): String {
        var x = s.lowercase()
        x = diacritics.replace(x, "")
        x = x.replace(Regex("[أإآ]"), "ا").replace("ة", "ه").replace("ى", "ي")
        x = emoji.replace(x, "")
        x = punct.replace(x, " ")
        return spaces.replace(x, " ").trim()
    }

    fun group(pattern: String, s: String): String? = Regex(pattern).find(s)?.groupValues?.getOrNull(1)
    private fun has(pattern: String, s: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(s)

    fun yearOf(t: String): String = group("""(?:^|\D)(20[12]\d)[123]?(?!\d)""", t) ?: ""

    fun driveId(u: String): String? =
        group("""/(?:file|document|presentation|spreadsheets)/d/([\w-]{10,})""", u)
            ?: group("""[?&]id=([\w-]{10,})""", u)
            ?: group("""/folders/([\w-]{10,})""", u)

    fun kind(u: String, t: String = ""): Kind {
        val s = "$u $t".lowercase()
        if (has("""^/(?!/)""", u) || has("""^https?://(www\.)?asubank\.org""", u)) return Kind.PAGE
        if (has("""drive\.google\.com/(drive/(u/\d/)?folders|embeddedfolderview|folderview)""", u) || has("""dropbox\.com/(scl/fo|sh)/""", u) || has("""sharepoint\.com/:f:""", u)) return Kind.FOLDER
        if (has("""sharepoint\.com/:v:|youtube\.com/(playlist|@)""", u)) return Kind.VIDEO
        if (has("""docs\.google\.com/document""", u)) return Kind.DOC
        if (has("""docs\.google\.com/presentation""", u)) return Kind.PPT
        if (has("""docs\.google\.com/spreadsheets""", u)) return Kind.SHEET
        if (has("""\.pdf\b""", s)) return Kind.PDF
        if (has("""\.(pptx?|key)\b""", s)) return Kind.PPT
        if (has("""\.(docx?|rtf|odt)\b""", s)) return Kind.DOC
        if (has("""\.(xlsx?|csv)\b""", s)) return Kind.SHEET
        if (has("""\.(jpe?g|png|gif|webp|heic)\b""", s)) return Kind.IMG
        if (has("""\.(mp4|mov|mkv)\b|youtube\.com|youtu\.be""", s)) return Kind.VIDEO
        if (has("""\.(zip|rar|7z)\b""", s)) return Kind.ZIP
        if (has("""drive\.google\.com/(file|open|uc)""", u)) {
            val id = driveId(u)
            if (id != null && DriveLinks.target(id)?.second == true) return Kind.FOLDER
            return Kind.FILE
        }
        if (has("""dropbox\.com""", u)) return Kind.FILE
        return Kind.LINK
    }

    /** Kind from Drive's mime type; shortcuts use the resolved target (drive-map.json) */
    fun kind(mime: String?, url: String, name: String): Kind {
        val m = mime ?: ""
        return when {
            m.contains("folder") -> Kind.FOLDER
            m.contains("pdf") -> Kind.PDF
            m.contains("presentation") -> Kind.PPT
            m.contains("wordprocessing") || m.endsWith(".document") -> Kind.DOC
            m.contains("spreadsheet") -> Kind.SHEET
            m.startsWith("image/") -> Kind.IMG
            m.startsWith("video/") -> Kind.VIDEO
            m.contains("zip") || m.contains("rar") -> Kind.ZIP
            else -> kind(url, name)
        }
    }

    fun date(d: String?): LocalDate? = try {
        if (d == null || d.length < 10) null else LocalDate.parse(d.take(10))
    } catch (e: Exception) { null }

    /** NEW badges last 30 days after a link is added (same rule as the website) */
    fun isNew(d: String?): Boolean = date(d)?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) in 0 until NEW_DAYS } ?: false

    fun absolute(u: String): String = if (u.startsWith("/") && !u.startsWith("//")) SITE + u else u

    /** "/guides/faqs" for any asubank.org link (no .html, query or trailing slash); null for other sites */
    fun sitePath(u: String): String? {
        var p = u.trim()
        val m = Regex("""^https?://(www\.)?asubank\.org""", RegexOption.IGNORE_CASE).find(p)
        if (m != null) {
            p = p.substring(m.range.last + 1)
            if (p.isEmpty()) p = "/"
        }
        if (!p.startsWith("/") || p.startsWith("//")) return null
        val cut = p.indexOfFirst { it == '?' || it == '#' }
        if (cut >= 0) p = p.substring(0, cut)
        p = p.replace(Regex("""\.html?$"""), "")
        while (p.length > 1 && p.endsWith("/")) p = p.dropLast(1)
        return if (p == "/index") "/" else p
    }

    fun index(m: Material) = Indexed(m, norm(m.t + " " + (m.s ?: "") + " " + (m.c ?: "")), kind(m.u, m.t), yearOf(m.t), isNew(m.d))

    /** Score items for a query; every word must match (same ranking as the website) */
    fun match(items: List<Indexed>, q: String, limit: Int = 200): List<Indexed> {
        val nq = norm(q)
        if (nq.isEmpty()) return emptyList()
        val toks = nq.split(" ")
        val out = ArrayList<Pair<Int, Indexed>>()
        for (it in items) {
            var score = 0
            var ok = true
            for (t in toks) {
                val p = it.q.indexOf(t)
                if (p < 0) { ok = false; break }
                score += if (p == 0) 6 else if (it.q[p - 1] == ' ') 4 else 1
            }
            if (!ok) continue
            val tn = norm(it.m.t)
            if (tn.startsWith(nq)) score += 8 else if (tn.contains(nq)) score += 4
            if (it.kind == Kind.FOLDER) score += 1
            if (it.isNew) score += 1
            out.add(score to it)
        }
        return out.sortedByDescending { it.first }.take(limit).map { it.second }
    }

    /** -1, 0, 1 for dotted versions like 1.10.2 */
    fun compare(a: String, b: String): Int {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val l = x.getOrElse(i) { 0 }
            val r = y.getOrElse(i) { 0 }
            if (l != r) return if (l < r) -1 else 1
        }
        return 0
    }
}
