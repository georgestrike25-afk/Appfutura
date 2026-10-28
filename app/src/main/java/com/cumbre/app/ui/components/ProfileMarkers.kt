package com.cumbre.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cumbre.core.analysis.ProfileFeatures
import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.format.Formatters
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.navigation.TrackGeometry

/** Punto elegido sobre el perfil y cumbre, ubicados sobre el track para dibujarlos en el mapa. */
data class ProfileMarkers(val cursor: GeoPoint?, val summit: Pair<GeoPoint, String>?)

@Composable
fun rememberProfileMarkers(lines: List<List<GeoPoint>>, profile: List<ProfilePoint>, scrubMeters: Double?): ProfileMarkers {
    val geometry = remember(lines) { if (lines.any { it.size >= 2 }) TrackGeometry(lines) else null }
    val summit = remember(geometry, profile) {
        val top = ProfileFeatures.highest(profile) ?: return@remember null
        geometry?.pointAt(top.distanceMeters)?.let { it to Formatters.elevation(top.elevationMeters) }
    }
    val cursor = remember(geometry, scrubMeters) { scrubMeters?.let { geometry?.pointAt(it) } }
    return ProfileMarkers(cursor, summit)
}

/** Texto bajo el perfil: dónde está el punto más alto y cómo usar el perfil. */
@Composable
fun ProfileHint(profile: List<ProfilePoint>, modifier: Modifier = Modifier) {
    val top = remember(profile) { ProfileFeatures.highest(profile) } ?: return
    Text(
        "▲ Punto más alto: ${Formatters.elevation(top.elevationMeters)} en el km ${Formatters.kilometersValue(top.distanceMeters)}. " +
            "Tocá o arrastrá el perfil para ver ese punto en el mapa.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
