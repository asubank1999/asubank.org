package org.unihub.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** One subject or study word in both languages, from assets/app/search-help.json */
@Serializable
data class HelpTerm(val en: String, val ar: String, val alt: List<String> = emptyList(), val p: String? = null) {
    val forms get() = listOf(en, ar) + alt
}

@Serializable
private data class HelpFile(val stop: List<String> = emptyList(), val terms: List<HelpTerm> = emptyList())

/** What the search screen can offer for the text typed so far */
data class SearchHints(
    /** Other spellings and languages for each word; the search finds any of them */
    val alts: Map<String, List<String>> = emptyMap(),
    /** Filler words ("مادة", "looking for") that titles don't have to contain */
    val skip: Set<String> = emptySet(),
    /** The search in the other language */
    val translated: String? = null,
    val fixes: List<String> = emptyList(),
    val completions: List<String> = emptyList(),
    val subjects: List<Subject> = emptyList(),
)

/**
 * Helps students find a material: translates between Arabic and English, fixes typos and points to subjects.
 * The word list lives on the website, so it can grow without an app update.
 */
object SearchHelp {
    @Volatile private var terms: List<HelpTerm> = emptyList()
    @Volatile private var stop: Set<String> = emptySet()
    @Volatile private var lookup: Map<String, HelpTerm> = emptyMap()
    private var vocabulary: Set<String> = emptySet()
    private var vocabularySize = -1
    private val online = HashMap<String, String>()

    suspend fun load() {
        if (terms.isEmpty()) Disk.read("search-help.json")?.let { apply(it) }
        val t = Net.text("${AB.SITE}/assets/app/search-help.json")
        if (t != null && apply(t)) Disk.write("search-help.json", t)
    }

    private fun apply(t: String): Boolean = try {
        val f = json.decodeFromString(HelpFile.serializer(), t)
        val map = HashMap<String, HelpTerm>()
        f.terms.forEach { x -> x.forms.forEach { map.putIfAbsent(AB.norm(it), x) } }
        terms = f.terms
        stop = f.stop.map { AB.norm(it) }.toSet()
        lookup = map
        synchronized(this) { vocabularySize = -1 }
        true
    } catch (e: Exception) { false }

    private fun isArabicWord(s: String) = s.any { it in '؀'..'ۿ' }

    /** "الفيزياء" and "فيزياء" are the same word */
    private fun term(word: String) = lookup[word] ?: if (word.startsWith("ال")) lookup[word.drop(2)] else null

    fun hints(query: String, items: List<Indexed>, subjects: List<Subject>): SearchHints {
        val nq = AB.norm(query)
        if (nq.length < 2) return SearchHints()
        val words = nq.split(" ")
        var skip = words.filter { it in stop }.toSet()
        if (skip.size == words.size) skip = emptySet()
        val wanted = words.filter { it !in skip }

        val alts = HashMap<String, List<String>>()
        val other = ArrayList<String>()
        var i = 0
        while (i < wanted.size) {
            // two-word names first ("test bank", "جبر خطي")
            val pair = if (i + 1 < wanted.size) term(wanted[i] + " " + wanted[i + 1]) else null
            if (pair != null) {
                other.add(if (isArabicWord(wanted[i])) pair.en else pair.ar)
                i += 2
                continue
            }
            val w = wanted[i]
            val t = term(w)
            if (t != null) {
                alts[w] = t.forms.map { AB.norm(it) }.filter { it != w }
                other.add(if (isArabicWord(w)) t.en else t.ar)
            } else other.add(w)
            i++
        }
        val joined = other.joinToString(" ")
        val translated = joined.takeIf { AB.norm(it) != wanted.joinToString(" ") }

        // subjects by name, or the subject a word belongs to
        val found = ArrayList<Subject>()
        for (s in subjects) {
            if (s.c == "Archive") continue
            val name = AB.norm(s.t + " " + (s.ar ?: ""))
            if (wanted.all { w -> w in name || alts[w].orEmpty().any { it in name } }) found.add(s)
        }
        for (w in wanted) {
            val p = term(w)?.p ?: continue
            subjects.firstOrNull { it.p == p }?.let { if (it !in found) found.add(it) }
        }

        // finish the word being typed
        val completions = ArrayList<String>()
        val last = wanted.lastOrNull()
        if (last != null && last.length >= 2 && term(last) == null) {
            val lead = wanted.dropLast(1).joinToString(" ")
            for (t in terms) {
                val f = t.forms.firstOrNull { AB.norm(it).startsWith(last) } ?: continue
                val word = if (isArabicWord(last)) f else t.en
                completions.add(if (lead.isEmpty()) word else "$lead $word")
                if (completions.size >= 5) break
            }
        }

        // typos: only for words no title contains
        buildVocabulary(items)
        var changed = false
        val fixed = wanted.map { w ->
            if (w.length >= 4 && term(w) == null && items.none { w in it.q }) closest(w)?.also { changed = true } ?: w else w
        }
        return SearchHints(alts, skip, translated, if (changed) listOf(fixed.joinToString(" ")) else emptyList(), completions.distinct(), found.take(3))
    }

    @Synchronized private fun buildVocabulary(items: List<Indexed>) {
        if (vocabularySize == items.size) return
        vocabularySize = items.size
        val counts = HashMap<String, Int>()
        for (it in items) for (w in AB.norm(it.m.t).split(" ").toSet()) if (w.length >= 4) counts[w] = (counts[w] ?: 0) + 1
        val v = counts.filterValues { it >= 3 }.keys.toMutableSet()
        for (t in terms) for (f in t.forms) for (w in AB.norm(f).split(" ")) if (w.length >= 4) v.add(w)
        vocabulary = v
    }

    private fun closest(w: String): String? {
        val allowed = if (w.length >= 7) 2 else 1
        var best: String? = null
        var bestD = Int.MAX_VALUE
        for (v in vocabulary) {
            if (abs(v.length - w.length) > allowed) continue
            val d = distance(w, v, allowed)
            if (d <= allowed && (d < bestD || (d == bestD && v < (best ?: "")))) { best = v; bestD = d }
        }
        return best
    }

    private fun distance(a: String, b: String, limit: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val row = IntArray(b.length + 1)
            row[0] = i
            var low = i
            for (j in 1..b.length) {
                row[j] = minOf(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (row[j] < low) low = row[j]
            }
            if (low > limit) return limit + 1
            prev = row
        }
        return prev[b.length]
    }

    /** Translates text the word list doesn't know, through the accounts Apps Script (Google Translate) */
    suspend fun translate(text: String): String? {
        val key = AB.norm(text)
        online[key]?.let { return it.ifEmpty { null } }
        val t = runCatching { AccountsApi.call("translate", mapOf("text" to text)).str("text") }.getOrNull()?.trim() ?: return null
        online[key] = t
        return t.ifEmpty { null }
    }
}

/** A titled row of tappable suggestions */
@Composable
fun HelpChips(title: String, options: List<String>, pick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { o ->
                Text(
                    o,
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).clickable { pick(o) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
