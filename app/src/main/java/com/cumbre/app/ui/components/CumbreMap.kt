package com.cumbre.app.ui.components

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.cumbre.app.map.CumbreMapController
import com.cumbre.app.map.MapPosition
import com.cumbre.app.map.MapTheme
import com.cumbre.app.map.MapWaypoint
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoPoint
import java.io.File

/**
 * Conserva los mapas de las pantallas principales (Mapa y Navegación) mientras vive la
 * actividad, para no recrear el motor cartográfico al cambiar de sección.
 */
class MapControllerStore {
    private val retained = HashMap<String, CumbreMapController>()

    fun obtain(key: String, create: () -> CumbreMapController): CumbreMapController =
        retained.getOrPut(key, create)

    fun destroyAll() {
        retained.values.forEach { it.destroy() }
        retained.clear()
    }
}

val LocalMapControllerStore = compositionLocalOf<MapControllerStore?> { null }

/**
 * Mapa Mapsforge integrado en Compose. Si [retainKey] no es nulo, el motor se conserva entre
 * navegaciones; si es nulo, se crea y se libera con la pantalla.
 */
@Composable
fun CumbreMap(
    modifier: Modifier = Modifier,
    mapFiles: List<File>,
    theme: MapTheme,
    routeLines: List<List<GeoPoint>> = emptyList(),
    waypoints: List<MapWaypoint> = emptyList(),
    recordedLines: List<List<GeoPoint>> = emptyList(),
    position: MapPosition? = null,
    fitBounds: GeoBounds? = null,
    fitKey: Any? = fitBounds,
    followPosition: Boolean = false,
    rotationDegrees: Float = 0f,
    highlighted: GeoPoint? = null,
    retainKey: String? = null,
    onWaypointTap: ((MapWaypoint) -> Unit)? = null,
    onMapAvailability: ((Boolean) -> Unit)? = null,
    onController: ((CumbreMapController) -> Unit)? = null,
) {
    val context = LocalContext.current
    val dark = CumbreTheme.isDark
    val store = LocalMapControllerStore.current
    val controller = remember(retainKey) {
        if (retainKey != null && store != null) store.obtain(retainKey) { CumbreMapController(context.applicationContext, dark) }
        else CumbreMapController(context, dark)
    }
    DisposableEffect(controller) {
        onController?.invoke(controller)
        onDispose { if (retainKey == null || store == null) controller.destroy() }
    }
    AndroidView(
        factory = {
            (controller.mapView.parent as? ViewGroup)?.removeView(controller.mapView)
            controller.mapView
        },
        modifier = modifier,
        onRelease = { view -> (view.parent as? ViewGroup)?.removeView(view) },
    )
    val trackColor = CumbreTheme.extra.track.toArgb()
    val haloColor = CumbreTheme.extra.trackHalo.toArgb()
    val recordedColor = CumbreTheme.extra.recorded.toArgb()
    LaunchedEffect(controller, mapFiles, theme) {
        val ok = controller.setMapFiles(mapFiles, theme)
        onMapAvailability?.invoke(ok)
    }
    SideEffect {
        controller.setDark(dark)
        controller.onWaypointTap = onWaypointTap
        controller.setRoute(routeLines, waypoints, trackColor, haloColor)
        controller.setRecorded(recordedLines, recordedColor)
        controller.setPosition(position)
        controller.setHighlighted(highlighted)
    }
    LaunchedEffect(controller, fitKey) {
        fitBounds?.let { controller.fitBounds(it) }
    }
    LaunchedEffect(controller, position?.point, followPosition) {
        if (followPosition) position?.let { controller.centerOn(it.point, maxOf(controller.zoomLevel, 15)) }
    }
    LaunchedEffect(controller, rotationDegrees) {
        controller.setRotation(rotationDegrees)
    }
}
