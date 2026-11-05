package org.unihub.app

import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/** One version's changes, from whats-new.json (built from RELEASE_NOTES.md by tools/gen_whats_new.py) */
@Serializable
data class WhatsNewEntry(val version: String, val changes: List<String> = emptyList())

object WhatsNew {
    private const val SEEN_KEY = "whatsnew.seen"
    /** CI screenshots: --ez showWhatsNew true shows it; other CI launches must not be covered by it */
    var screenshotLaunch = false
    var forceShow = false

    fun all(ctx: Context): List<WhatsNewEntry> = runCatching {
        json.decodeFromString<List<WhatsNewEntry>>(ctx.assets.open("whats-new.json").bufferedReader().use { it.readText() })
    }.getOrElse { emptyList() }

    /** This version's notes, once, the first launch after an update. A new install has nothing to compare with. */
    fun pending(ctx: Context, accepted: Boolean): WhatsNewEntry? {
        val current = AppConfig.current
        if (forceShow) return all(ctx).firstOrNull { it.version == current }
        if (screenshotLaunch) return null
        if (Saved.prefs.getString(SEEN_KEY, null) == current) return null
        Saved.prefs.edit().putString(SEEN_KEY, current).apply()
        if (!accepted) return null
        return all(ctx).firstOrNull { it.version == current }
    }
}

@Composable
private fun ChangeLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Shown over the app the first time it opens after an update */
@Composable
fun WhatsNewDialog(entry: WhatsNewEntry, onClose: () -> Unit, onSeeAll: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(trf("What's new in %s", entry.version)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.changes.forEach { ChangeLine(it) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onSeeAll) { Text("See all versions") } },
    )
}

/** More > What's New: every version, newest first */
@Composable
fun WhatsNewScreen() {
    val ctx = LocalContext.current
    val list = remember { WhatsNew.all(ctx) }
    Screen(tr("What's New")) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(list.size) { i ->
                val entry = list[i]
                Group(header = trf("Version %s", entry.version)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entry.changes.forEach { ChangeLine(it) }
                    }
                }
            }
        }
    }
}
