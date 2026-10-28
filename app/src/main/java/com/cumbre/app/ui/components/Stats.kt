package com.cumbre.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.DataText
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.maps.CoverageLevel

/** Dato a mostrar. Si [unit] no es nulo, se dibuja más chico junto al valor. */
data class StatValue(val value: String, val label: String, val unit: String? = null)

@Composable
fun StatItem(stat: StatValue, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stat.value, style = DataText.Medium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            stat.unit?.let {
                Text(
                    " $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(stat.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Grilla de estadísticas en filas de [columns] elementos. */
@Composable
fun StatGrid(stats: List<StatValue>, modifier: Modifier = Modifier, columns: Int = 3) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { StatItem(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Column(Modifier.weight(1f)) {} }
            }
        }
    }
}

enum class TagStyle { POSITIVE, NEUTRAL, WARNING }

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, style: TagStyle = TagStyle.NEUTRAL) {
    val extra = CumbreTheme.extra
    val (bg, fg) = when (style) {
        TagStyle.POSITIVE -> extra.successContainer to extra.onSuccessContainer
        TagStyle.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        TagStyle.WARNING -> extra.warningContainer to extra.onWarningContainer
    }
    Row(
        modifier.clip(MaterialTheme.shapes.extraSmall).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/** Estado de disponibilidad de cartografía offline para una ruta. */
@Composable
fun OfflineBadge(level: CoverageLevel?, mapName: String? = null, modifier: Modifier = Modifier) {
    when (level) {
        CoverageLevel.FULL -> Tag(mapName?.let { "Mapa offline: $it" } ?: "Offline", modifier, Icons.Rounded.OfflinePin, TagStyle.POSITIVE)
        CoverageLevel.PARTIAL -> Tag("Mapa offline parcial", modifier, Icons.Rounded.Warning, TagStyle.WARNING)
        CoverageLevel.NONE -> Tag("Sin mapa offline", modifier, Icons.Rounded.CloudOff, TagStyle.NEUTRAL)
        null -> Tag("Comprobando mapa…", modifier, Icons.Rounded.CheckCircle, TagStyle.NEUTRAL)
    }
}
