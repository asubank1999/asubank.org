package org.unihub.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Message reactions (rooms and private chats) and the story reaction / reply lines in private chats.

object Reactions {
    /** The same list the server accepts */
    val all = listOf("❤️", "😂", "😮", "😢", "👍", "🙏", "🔥", "👏")

    /** The server's recent reaction changes: [{ id, reactions }] -> message id to reactions */
    fun changes(v: JsonElement?): Map<String, Map<String, String>> {
        val out = mutableMapOf<String, Map<String, String>>()
        (v as? JsonArray)?.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            out[id] = map(o["reactions"])
        }
        return out
    }

    fun map(v: JsonElement?): Map<String, String> =
        (v as? JsonObject)?.mapNotNull { (k, x) -> (x as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()

    /** My reaction toggled: the same emoji again takes it back */
    fun toggled(map: Map<String, String>, me: String, emoji: String): Map<String, String> =
        if (map[me] == emoji) map - me else map + (me to emoji)
}

/** Little emoji counts under a bubble; tapping one reacts with it too (or takes mine back) */
@Composable
fun ReactionChips(reactions: Map<String, String>, me: String?, modifier: Modifier = Modifier, tap: (String) -> Unit) {
    if (reactions.isEmpty()) return
    val counts = reactions.values.groupingBy { it }.eachCount()
    val order = Reactions.all.filter { it in counts } + counts.keys.filter { it !in Reactions.all }.sorted()
    Row(modifier.padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        order.forEach { e ->
            val isMine = me != null && reactions[me] == e
            val accent = MaterialTheme.colorScheme.primary
            Row(
                Modifier.clip(CircleShape)
                    .background(if (isMine) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, if (isMine) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { tap(e) }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                androidx.compose.material3.Text(e, fontSize = 13.sp)
                val n = counts[e] ?: 0
                if (n > 1) androidx.compose.material3.Text("$n", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The row of emojis at the top of a message's long-press menu */
@Composable
fun ReactionPickerRow(current: String?, pick: (String) -> Unit) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Reactions.all.forEach { e ->
            androidx.compose.material3.Text(
                e, fontSize = 22.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (current == e) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { pick(e) }
                    .padding(5.dp),
            )
        }
    }
}

/** Story reactions and replies arrive as private messages. Older apps send them as plain text
 * ("😂 Reacted to your story", "↩️ Replied to your story: hi"), so the text is read and each person sees it from their own side. */
object StoryNote {
    sealed class Kind {
        data class Reaction(val emoji: String) : Kind()
        data class Reply(val text: String) : Kind()
    }

    private const val REPLY = "↩️ Replied to your story: "
    private const val REACT = " Reacted to your story"

    fun parse(text: String): Kind? {
        if (text.startsWith(REPLY)) return Kind.Reply(text.removePrefix(REPLY))
        if (text.endsWith(REACT)) {
            val e = text.removeSuffix(REACT).trim()
            if (e.isNotEmpty() && e.length <= 8) return Kind.Reaction(e)
        }
        return null
    }

    /** "her story", "his story" or "their story", from what the person shows on their profile */
    fun their(gender: String?): String = when (gender?.lowercase()) {
        "female" -> tr("her story")
        "male" -> tr("his story")
        else -> tr("their story")
    }

    /** The line above the bubble: "You reacted 😂 to her story" / "Sara replied to your story" */
    fun line(k: Kind, sentByMe: Boolean, otherName: String, otherGender: String?): String = when (k) {
        is Kind.Reaction -> if (sentByMe) tr("You reacted %s to %s").format(k.emoji, their(otherGender)) else tr("%s reacted %s to your story").format(otherName, k.emoji)
        is Kind.Reply -> if (sentByMe) tr("You replied to %s").format(their(otherGender)) else tr("%s replied to your story").format(otherName)
    }

    /** For chat lists and notifications */
    fun preview(text: String, sentByMe: Boolean, otherName: String, otherGender: String?): String {
        val k = parse(text) ?: return text
        val l = line(k, sentByMe, otherName, otherGender)
        return if (k is Kind.Reply) "$l: ${k.text}" else l
    }
}
