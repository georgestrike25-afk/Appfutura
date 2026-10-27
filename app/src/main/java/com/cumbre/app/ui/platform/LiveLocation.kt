package com.cumbre.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cumbre.app.location.AndroidLocationSource
import com.cumbre.app.map.MapPosition
import com.cumbre.app.map.MapTheme
import com.cumbre.app.data.MapStyle
import com.cumbre.core.recording.LocationSample
import com.cumbre.core.recording.SignalMonitor
import kotlinx.coroutines.flow.emptyFlow

/**
 * Ubicación en vivo solo mientras la pantalla está visible: al salir de ella o pasar a segundo
 * plano, la recolección se detiene y se libera el GPS.
 */
@Composable
fun rememberLiveLocation(source: AndroidLocationSource, enabled: Boolean): State<LocationSample?> {
    val flow = remember(enabled) { if (enabled) source.updates() else emptyFlow() }
    val initial = remember(enabled) { if (enabled) source.recentLastKnown() else null }
    return flow.collectAsStateWithLifecycle(initialValue = initial)
}

fun LocationSample.toMapPosition(now: Long = System.currentTimeMillis()): MapPosition = MapPosition(
    point = geo,
    accuracyMeters = accuracyMeters,
    bearingDegrees = if ((speedMps ?: 0.0) > 0.5) bearingDegrees else null,
    stale = now - timeMillis > SignalMonitor.LOST_AFTER_MILLIS,
)

fun MapStyle.toMapTheme(): MapTheme = when (this) {
    MapStyle.DEFAULT -> MapTheme.DEFAULT
    MapStyle.OSMARENDER -> MapTheme.OSMARENDER
}
