package org.asubank.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Suggest materials, natively. It saves to the same place as the form on asubank.org/upload/suggest_to_whatsapp,
 * and admins read both in Admin > Suggestions.
 */
@Composable
fun SuggestMaterialScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val user by Account.user.collectAsState()
    var note by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    val text = note.trim()

    Screen(tr("Suggest materials")) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            if (sent) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Brand.green, modifier = Modifier.size(52.dp))
                    Text("Thank you!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("We got your suggestion. The ASU Bank team will look at it soon.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    TextButton(onClick = { sent = false }) { Text("Send another") }
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            note, { note = it }, label = { Text("Describe what you're suggesting") },
                            placeholder = { Text("Example: [PHY 202] Dr. Smith, Fall 2023 midterm with solutions.") },
                            supportingText = { Text("Include the course code, professor name and semester if you know them. At least 20 characters.") },
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        )
                        OutlinedTextField(
                            contact, { contact = it }, label = { Text("How can we contact you? (optional)") },
                            placeholder = { Text("Email, WhatsApp or ASU ID") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                        )
                        user?.let { Text(trf("You're sending this as @%s.", it.username), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        Account.call("suggest", mapOf("note" to text, "contact" to contact.trim()))
                                        note = ""; contact = ""; error = null; sent = true
                                    } catch (e: ApiError) { error = e.message }
                                    busy = false
                                }
                            },
                            enabled = !busy && text.length >= 20, modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Send suggestion")
                        }
                    }
                }
                item {
                    Group(header = tr("What to share")) {
                        listOf("Past exams or quizzes (with solutions if possible)", "Study guides or cheat sheets", "Practice problems with answers", "Professor-specific materials").forEach {
                            Text("•  " + tr(it), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    Group(header = tr("Guidelines")) {
                        listOf("Only share materials you're permitted to distribute", "Clearly label documents with course information",
                            "Don't share current semester exams that might still be in use", "No spam or unrelated content", "Respect copyright and academic integrity policies").forEach {
                            Text("•  " + tr(it), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    Group {
                        ListRow("Looking for something specific? Request it", icon = Icons.Filled.FindInPage) { nav.openPage("/requests/request", "Request a material") }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Admins: what students suggested, from the apps and the website */
@Composable
fun AdminSuggestionsScreen() {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        val tag = if (showAll) "all" else "open"
        if (list.isEmpty()) Account.saved("adminSuggestions", tag)?.let { list = (it["suggestions"] as? JsonArray).objects(); loading = false }
        try { list = (Account.callSaving("adminSuggestions", mapOf("all" to showAll), tag)["suggestions"] as? JsonArray).objects(); error = null }
        catch (e: ApiError) { error = e.message }
        loading = false
    }
    fun set(r: JsonObject, status: String) {
        scope.launch {
            try { Account.call("adminSuggestionSet", mapOf("id" to r.str("id"), "status" to status)); load() }
            catch (e: ApiError) { error = e.message }
        }
    }
    LaunchedEffect(showAll) { load() }
    Screen(tr("Suggestions")) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showAll, { showAll = false }, label = { Text("Open") })
                    FilterChip(showAll, { showAll = true }, label = { Text("All") })
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (list.isEmpty()) item { Text("Nothing to review.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list.size) { i ->
                val r = list[i]
                Group {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.str("note") ?: "", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(r.str("username"), r.str("contact")?.ifEmpty { null }, r.str("created")).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (r.str("status") == "open") Row {
                            TextButton(onClick = { set(r, "done") }) { Text("Done") }
                            TextButton(onClick = { set(r, "dismissed") }) { Text("Dismiss") }
                        } else Text(if (r.str("status") == "done") "Handled" else "Dismissed", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
