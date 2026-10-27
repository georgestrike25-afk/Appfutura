package com.cumbre.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cumbre.app.ui.components.MessageBanner
import com.cumbre.app.ui.components.MessageKind
import com.cumbre.app.ui.components.StatValue
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.analysis.IssueSeverity
import com.cumbre.core.analysis.IssueText
import com.cumbre.core.analysis.QualityIssue
import com.cumbre.core.analysis.RouteStats
import com.cumbre.core.format.Formatters

/** Estadísticas visibles de una ruta. Los datos que no se pueden calcular se muestran como «—». */
fun routeStats(s: RouteStats): List<StatValue> = buildList {
    add(StatValue(Formatters.distance(s.distanceMeters), "Distancia"))
    add(StatValue(s.elevationGainMeters?.let { "↑ ${Formatters.elevation(it)}" } ?: "—", "Desnivel +"))
    add(StatValue(s.elevationLossMeters?.let { "↓ ${Formatters.elevation(it)}" } ?: "—", "Desnivel −"))
    add(StatValue(s.estimatedDurationSeconds?.let { Formatters.duration(it) } ?: "—", "Duración est."))
    add(StatValue(s.minElevationMeters?.let { Formatters.elevation(it) } ?: "—", "Altitud mín."))
    add(StatValue(s.maxElevationMeters?.let { Formatters.elevation(it) } ?: "—", "Altitud máx."))
    add(StatValue(Formatters.count(s.pointCount), "Puntos"))
    add(StatValue(s.segmentCount.toString(), if (s.segmentCount == 1) "Tramo" else "Tramos"))
    s.recordedDurationSeconds?.let { add(StatValue(Formatters.duration(it), "Tiempo registrado")) }
        ?: add(StatValue(s.waypointCount.toString(), "Waypoints"))
}

@Composable
fun QualityIssuesList(issues: List<QualityIssue>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        issues.sortedByDescending { it.severity == IssueSeverity.WARNING }.forEach { issue ->
            MessageBanner(
                text = IssueText.describe(issue),
                kind = if (issue.severity == IssueSeverity.WARNING) MessageKind.WARNING else MessageKind.INFO,
            )
        }
    }
}

@Composable
fun WaypointList(items: List<Pair<String?, String?>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items.take(50).forEachIndexed { i, (name, desc) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                Column {
                    Text(name ?: "Waypoint ${i + 1}", style = MaterialTheme.typography.bodyMedium)
                    if (!desc.isNullOrBlank()) {
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    }
                }
            }
        }
        if (items.size > 50) {
            Text("y ${items.size - 50} más…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
