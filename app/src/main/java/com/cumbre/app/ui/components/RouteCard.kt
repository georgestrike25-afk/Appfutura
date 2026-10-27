package com.cumbre.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cumbre.app.data.db.RouteEntity
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.DataText
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.format.Formatters
import com.cumbre.core.geo.PolylineCodec
import com.cumbre.core.library.RouteSource
import com.cumbre.core.maps.CoverageLevel

fun sourceLabel(source: String): String = when (runCatching { RouteSource.valueOf(source) }.getOrNull()) {
    RouteSource.WIKILOC -> "Wikiloc"
    RouteSource.RECORDED -> "Mi actividad"
    RouteSource.IMPORTED, null -> "Importada"
}

@Composable
fun RouteCard(route: RouteEntity, coverage: CoverageLevel?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val points = remember(route.thumbnail) { PolylineCodec.decode(route.thumbnail) }
    CumbreCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            TrackThumbnail(points, Modifier.size(84.dp).clip(MaterialTheme.shapes.medium))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        route.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (route.favorite) Icon(Icons.Rounded.Star, contentDescription = "Favorita", tint = CumbreColors.Naranja, modifier = Modifier.size(20.dp))
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(Formatters.distance(route.distanceMeters), style = DataText.Small, maxLines = 1)
                    route.elevationGainMeters?.let {
                        Text("↑ ${Formatters.elevation(it)}", style = DataText.Small, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    route.estimatedDurationSeconds?.let {
                        Text(Formatters.duration(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (route.minElevationMeters != null && route.maxElevationMeters != null) {
                    Text(
                        "${Formatters.elevation(route.minElevationMeters)} – ${Formatters.elevation(route.maxElevationMeters)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OfflineBadge(coverage)
                    Tag(sourceLabel(route.source))
                }
            }
        }
    }
}
