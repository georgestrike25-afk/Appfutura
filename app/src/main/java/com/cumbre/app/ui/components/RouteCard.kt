package com.cumbre.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.analysis.AnalysisCodec
import com.cumbre.core.analysis.DifficultyEstimator
import com.cumbre.core.format.Formatters
import com.cumbre.core.geo.PolylineCodec
import com.cumbre.core.library.RouteSource
import com.cumbre.core.maps.CoverageLevel

fun sourceLabel(source: String): String = when (runCatching { RouteSource.valueOf(source) }.getOrNull()) {
    RouteSource.WIKILOC -> "Wikiloc"
    RouteSource.RECORDED -> "Mi actividad"
    RouteSource.IMPORTED, null -> "Importada"
}

/** Tarjeta de ruta: miniatura, etiquetas (mapa, dificultad, origen), cifras en columnas y perfil. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteCard(route: RouteEntity, coverage: CoverageLevel?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val points = remember(route.thumbnail) { PolylineCodec.decode(route.thumbnail) }
    val profile = remember(route.profile) { AnalysisCodec.decodeProfile(route.profile) }
    val difficulty = remember(route.distanceMeters, route.elevationGainMeters) {
        DifficultyEstimator.estimate(route.distanceMeters, route.elevationGainMeters)
    }
    CumbreCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            TrackThumbnail(points, Modifier.size(88.dp).clip(MaterialTheme.shapes.medium))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        route.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (route.favorite) Icon(Icons.Rounded.Star, contentDescription = "Favorita", tint = CumbreColors.Naranja, modifier = Modifier.size(22.dp))
                }
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OfflineBadge(coverage)
                    DifficultyTag(difficulty)
                    Tag(sourceLabel(route.source))
                }
            }
        }
        val (d, du) = Formatters.distanceParts(route.distanceMeters)
        StatGrid(
            listOfNotNull(
                StatValue(d, "Distancia", du),
                StatValue(route.elevationGainMeters?.let { Formatters.elevationValue(it) } ?: "—", "Desnivel +", route.elevationGainMeters?.let { "m" }),
                StatValue(route.estimatedDurationSeconds?.let { Formatters.durationCompact(it) } ?: "—", "Estimado"),
            ),
            columns = 3,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = Spacing.m, bottom = 4.dp),
        )
        if (profile.size >= 2) {
            RidgeSilhouette(profile, Modifier.fillMaxWidth().height(36.dp), style = RidgeStyle.STRIP)
        } else {
            Spacer(Modifier.height(Spacing.m))
        }
    }
}
