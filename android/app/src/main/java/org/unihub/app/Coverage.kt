package org.unihub.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** How the library's files are spread over the colleges, counted live from materials.json. General files aren't a college, so they're left out. */
data class CollegeShare(val name: String, val files: Int, val percent: Int)

fun collegeShares(subjects: List<Subject>): List<CollegeShare> {
    val totals = subjects.filter { it.c != "Archive" && it.c != "Popular & general" }
        .groupBy { it.c }.mapValues { (_, l) -> l.sumOf { it.n ?: 0 } }.filterValues { it > 0 }
    val all = totals.values.sum()
    if (all == 0) return emptyList()
    return totals.map { (c, n) -> CollegeShare(c, n, maxOf(1, (n * 100.0 / all).roundToInt())) }.sortedByDescending { it.files }
}

@Composable
fun CollegeCoverageScreen() {
    val nav = LocalNav.current
    val subjects by Library.subjects.collectAsState()
    val shares = collegeShares(subjects)
    Screen(tr("Materials by college")) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Group(footer = tr("Each college's share of the files sorted by college. It shows where we have the most, not how much of a college's courses is covered. General files and the archive aren't counted, and search covers them too.")) {
                    if (shares.isEmpty()) Text("Loading…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    shares.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text("${s.percent}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxWidth(s.percent / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                            }
                            Text(trf("%d files", s.files), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Group {
                    ListRow("Subjects directory", icon = Icons.Filled.GridView) { nav.openPage("/banks/subjects", "All subjects") }
                }
            }
        }
    }
}
