package com.airmousepro.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmousepro.data.ChangeEntry
import com.airmousepro.data.ChangeKind

private val NewColor = Color(0xFF30D158)
private val ImprovedColor = Color(0xFF0A84FF)
private val RemovedColor = Color(0xFFFF9F0A)

private fun colorFor(kind: ChangeKind) = when (kind) {
    ChangeKind.New -> NewColor
    ChangeKind.Improved -> ImprovedColor
    ChangeKind.Removed -> RemovedColor
}

private fun iconFor(key: String): ImageVector = when (key) {
    "bluetooth" -> Icons.Rounded.Bluetooth
    "brightness" -> Icons.Rounded.LightMode
    "bubble" -> Icons.Rounded.Mouse
    "shield" -> Icons.Rounded.Shield
    "gamepad" -> Icons.Rounded.Gamepad
    "guide" -> Icons.Rounded.TipsAndUpdates
    "volume" -> Icons.Rounded.VolumeUp
    else -> Icons.Rounded.AutoAwesome
}

/**
 * The tour shown after an update. It uses the same pager as the setup guide but only walks through the entries
 * the user has not seen: an intro page, then one page per change, tagged New, Improved or Removed.
 */
@Composable
fun WhatsNewGuide(entries: List<ChangeEntry>, versionName: String, onFinish: () -> Unit) {
    val pages: List<GuidePage> = buildList {
        add { active ->
            val added = entries.count { it.kind == ChangeKind.New }
            val improved = entries.count { it.kind == ChangeKind.Improved }
            val removed = entries.count { it.kind == ChangeKind.Removed }
            val summary = listOfNotNull(
                added.takeIf { it > 0 }?.let { "$it new" },
                improved.takeIf { it > 0 }?.let { "$it improved" },
                removed.takeIf { it > 0 }?.let { "$it removed" },
            ).joinToString(", ")
            GuidePageLayout(
                active, hero = { BadgeHero(Icons.Rounded.AutoAwesome, ImprovedColor) },
                title = "What's new in v$versionName",
                body = "Here is what changed since you last opened the app: $summary.",
            )
        }
        entries.forEach { e ->
            add { active ->
                val color = colorFor(e.kind)
                GuidePageLayout(
                    active, hero = { BadgeHero(iconFor(e.icon), color) },
                    title = e.title,
                    body = e.body,
                    extra = {
                        Column(
                            Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { Text(e.kind.label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                    },
                )
            }
        }
    }
    GuideHost(pages = pages, lastLabel = "Got it", skipLabel = "Close", onFinish = onFinish, onSkip = onFinish)
}
