package org.unihub.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The launcher entries in the manifest. Exactly one is on at a time. ".MainActivity" is the original icon and keeps its name so existing home screen icons survive. */
object AppIcons {
    data class Choice(val alias: String, val name: String, val preview: Int)

    val choices = listOf(
        Choice("MainActivity", "Classic", R.drawable.icon_preview_default),
        Choice("IconNavy", "Midnight book", R.drawable.icon_preview_navy),
        Choice("IconCap", "Graduation cap", R.drawable.icon_preview_cap),
        Choice("IconCards", "Study cards", R.drawable.icon_preview_cards),
        Choice("IconBook", "Book and cap", R.drawable.icon_preview_book),
    )

    fun current(ctx: Context): String = choices.firstOrNull { c ->
        val state = ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx.packageName, "org.unihub.app." + c.alias))
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }?.alias ?: "MainActivity"

    fun set(ctx: Context, alias: String) {
        val pm = ctx.packageManager
        // on first, so the app is never without a launcher entry
        pm.setComponentEnabledSetting(ComponentName(ctx.packageName, "org.unihub.app.$alias"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        choices.filter { it.alias != alias }.forEach {
            pm.setComponentEnabledSetting(ComponentName(ctx.packageName, "org.unihub.app." + it.alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

/** More > App icon */
@Composable
fun AppIconScreen() {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(ctx)) }
    Screen("App icon") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(footer = "Pick the icon you want on your home screen. It can take a few seconds to change, and you may need to add it to your home screen again.") {
                    AppIcons.choices.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { c ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable {
                                        if (c.alias != current) { AppIcons.set(ctx, c.alias); current = c.alias }
                                    }.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Image(
                                        painterResource(c.preview), null,
                                        Modifier.size(72.dp).clip(RoundedCornerShape(17.dp))
                                            .border(if (c.alias == current) 3.dp else 0.dp, if (c.alias == current) accent else Color.Transparent, RoundedCornerShape(17.dp)),
                                    )
                                    Text(c.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
