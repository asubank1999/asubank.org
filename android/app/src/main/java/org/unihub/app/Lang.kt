package org.unihub.app

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * App language: System, English, Arabic or Spanish (More > Language). Translations come from assets/i18n.json,
 * which tools/gen_i18n.py writes from i18n/strings.json (the same file the iOS app's Localizable.strings come from).
 */
object Lang {
    val choices = listOf("system" to "System language", "en" to "English", "ar" to "Arabic", "es" to "Spanish", "fr" to "French", "tr" to "Turkish", "de" to "German", "ru" to "Russian")
    val native = mapOf("ar" to "العربية", "es" to "Español", "en" to "English", "fr" to "Français", "tr" to "Türkçe", "de" to "Deutsch", "ru" to "Русский")
    val choice = MutableStateFlow("system")
    @Volatile private var map: Map<String, String> = emptyMap()
    private var all: JsonObject? = null

    /** The language in use: the choice, or the phone's language when it's one we have */
    val code: String get() = choice.value.takeIf { it != "system" }
        ?: Locale.getDefault().language.takeIf { it in setOf("ar", "es", "fr", "tr", "de", "ru") } ?: "en"

    val rtl get() = code == "ar"

    fun init(ctx: Context) {
        choice.value = Saved.prefs.getString("language", "system") ?: "system"
        if (all == null) all = runCatching {
            json.parseToJsonElement(ctx.assets.open("i18n.json").bufferedReader().use { it.readText() }) as JsonObject
        }.getOrNull()
        load()
    }

    private fun load() {
        map = (all?.get(code) as? JsonObject)?.mapValues { (it.value as? JsonPrimitive)?.content ?: it.key } ?: emptyMap()
    }

    fun set(c: String) {
        Saved.prefs.edit().putString("language", c).apply()
        choice.value = c
        load()
    }

    fun t(s: String): String = map[s] ?: s
}

/** A translated string (English text stays as it is when there's no translation) */
fun tr(s: String): String = Lang.t(s)

/** A translated string with values in it: trf("Page %d", 3) */
fun trf(s: String, vararg args: Any?): String = runCatching { String.format(java.util.Locale.ENGLISH, tr(s), *args) }.getOrDefault(s)

/** Right-to-left for Arabic, and rebuilds the screens when the language changes */
@Composable
fun LangProvider(content: @Composable () -> Unit) {
    val c by Lang.choice.collectAsState()
    CompositionLocalProvider(LocalLayoutDirection provides if (Lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        key(c) { content() }
    }
}

/**
 * Material Text that translates its string. The app's files use this one (they don't import the material3 Text),
 * so every label in the app follows the language setting without wrapping each string.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text = tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
    fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
    lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
    onTextLayout = onTextLayout, style = style,
)

@Composable
fun LanguageScreen() {
    val c by Lang.choice.collectAsState()
    Screen("Language") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(footer = "System language follows your phone's settings. Material names and files stay as they were uploaded.") {
                    Lang.choices.forEachIndexed { i, (code, name) ->
                        if (i > 0) Divider()
                        ListRow(name, Lang.native[code]?.takeIf { code != "system" }, trailing = if (c == code) "✓" else null) { if (c != code) Lang.set(code) }
                    }
                }
            }
        }
    }
}
