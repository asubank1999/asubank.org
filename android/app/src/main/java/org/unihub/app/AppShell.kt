package org.unihub.app

import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The logo for a moment while the app starts */
@Composable
fun Splash() {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer { alpha = t.value.coerceIn(0f, 1f); scaleX = 0.82f + 0.18f * t.value; scaleY = 0.82f + 0.18f * t.value },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painterResource(R.drawable.logo), null,
                Modifier.size(120.dp).shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent).clip(RoundedCornerShape(28.dp)),
            )
            Text("UniHub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Study materials for ASU students", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** More > Theme */
@Composable
fun ThemeScreen() {
    val appearance by Prefs.appearance.collectAsState()
    val accentId by Prefs.accent.collectAsState()
    val modes = listOf(
        Triple("Match my phone", "Follows your phone's light or dark mode", Icons.Filled.PhoneAndroid),
        Triple("Light", "Always light", Icons.Filled.LightMode),
        Triple("Dark", "Always dark", Icons.Filled.DarkMode),
        Triple("Pitch black (OLED)", "True black everywhere; saves battery on OLED screens", Icons.Filled.NightsStay),
        Triple("Midnight", "Dark navy background", Icons.Filled.Bedtime),
        Triple("Forest night", "Dark green background", Icons.Filled.Park),
        Triple("Plum", "Dark purple background", Icons.Filled.AutoAwesome),
        Triple("Sand", "Warm light background", Icons.Filled.WbTwilight),
        Triple("Sky", "Soft blue background", Icons.Filled.Cloud),
        Triple("Blush", "Soft pink background", Icons.Filled.Favorite),
    )
    val dark = isDarkTheme()
    Screen("Theme") { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Group(header = "Appearance") {
                    modes.forEachIndexed { i, (title, sub, icon) ->
                        if (i > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().clickable { Prefs.setAppearance(i) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (i == 3) Color.Black else accent)
                                    .border(1.dp, if (i == 3) Color.Gray.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (appearance == i) Icon(Icons.Filled.Check, null, tint = accent)
                        }
                    }
                }
            }
            item {
                Group(header = "Accent color", footer = "Used for buttons, links and highlights across the app.") {
                    Prefs.accents.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
                            row.forEach { a ->
                                val c = Color(if (dark) a.dark else a.light)
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { Prefs.setAccent(a.id) }.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        Modifier.size(52.dp).border(3.dp, if (accentId == a.id) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                            .padding(5.dp).clip(CircleShape).background(c),
                                        contentAlignment = Alignment.Center,
                                    ) { if (accentId == a.id) Icon(Icons.Filled.Check, null, tint = if (dark) Color.Black else Color.White) }
                                    Text(a.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
