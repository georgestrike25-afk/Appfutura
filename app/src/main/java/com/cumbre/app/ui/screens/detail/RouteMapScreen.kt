package com.cumbre.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.map.CumbreMapController
import com.cumbre.app.map.MapWaypoint
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.components.CumbreMap
import com.cumbre.app.ui.components.ElevationProfileChart
import com.cumbre.app.ui.components.LoadingState
import com.cumbre.app.ui.components.MapControlButton
import com.cumbre.app.ui.components.MapZoomControls
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.rememberProfileMarkers
import com.cumbre.app.ui.cumbreViewModel
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.platform.LocationPurpose
import com.cumbre.app.ui.platform.rememberLiveLocation
import com.cumbre.app.ui.platform.rememberLocationPermission
import com.cumbre.app.ui.platform.rememberMapLayers
import com.cumbre.app.ui.platform.toMapPosition
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.format.Formatters
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Mapa de la ruta a pantalla completa, para recorrerla con gestos libres (arrastrar, pellizcar,
 * doble toque) y botones de zoom, como en un visor GPX.
 */
@Composable
fun RouteMapScreen(nav: NavController, routeId: Long) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val vm = cumbreViewModel(key = "route-map-$routeId") { RouteDetailViewModel(routeId, container.routes, container.maps) }
    val state by vm.state.collectAsStateWithLifecycle()
    val tracking by container.trackingManager.state.collectAsStateWithLifecycle()
    val relief by container.relief.state.collectAsStateWithLifecycle()
    val layers = rememberMapLayers()
    val startRecording = com.cumbre.app.ui.platform.rememberRecordingStarter(nav)
    val permission = rememberLocationPermission(container.settings)
    var controller by remember { mutableStateOf<CumbreMapController?>(null) }
    var showLocation by rememberSaveable { mutableStateOf(permission.hasAnyPermission) }
    var follow by rememberSaveable { mutableStateOf(false) }
    val live by rememberLiveLocation(container.locationSource, showLocation && permission.hasAnyPermission)
    val position = live?.toMapPosition()
    var scrub by remember { mutableStateOf<Double?>(null) }
    val markers = rememberProfileMarkers(state.geometry?.lines ?: emptyList(), state.profile, scrub)

    val route = state.route
    if (route == null) {
        LoadingState("Cargando mapa…")
        return
    }
    Box(Modifier.fillMaxSize()) {
        CumbreMap(
            modifier = Modifier.fillMaxSize(),
            layers = layers,
            routeLines = state.geometry?.lines ?: emptyList(),
            waypoints = state.geometry?.waypoints?.map { MapWaypoint(it.name, it.geo) } ?: emptyList(),
            position = position,
            fitBounds = route.bounds,
            fitKey = route.id,
            followPosition = follow,
            cursor = markers.cursor,
            summit = markers.summit,
            onWaypointTap = { w -> messenger(w.name ?: "Waypoint sin nombre") },
            onController = { controller = it },
        )

        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapControlButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = { nav.popBackStack() })
            Spacer(Modifier.width(Spacing.s))
            Surface(shape = MaterialTheme.shapes.medium, color = CumbreTheme.extra.mapControl, shadowElevation = 4.dp, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(route.name, style = MaterialTheme.typography.titleSmall, color = CumbreTheme.extra.onMapControl, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        Formatters.distance(route.distanceMeters) + (route.elevationGainMeters?.let { " · ↑ ${Formatters.elevation(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = CumbreTheme.extra.onMapControl.copy(alpha = 0.75f),
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (relief.installed) {
                MapControlButton(
                    Icons.Rounded.Landscape,
                    if (settings.showRelief) "Ocultar relieve" else "Mostrar relieve",
                    active = settings.showRelief,
                    onClick = { scope.launch { container.settings.setShowRelief(!settings.showRelief) } },
                )
            }
            MapControlButton(Icons.Rounded.ZoomOutMap, "Ver toda la ruta", onClick = {
                follow = false
                route.bounds?.let { controller?.fitBounds(it) }
            })
            MapControlButton(
                Icons.Rounded.MyLocation,
                "Mi ubicación",
                tint = CumbreColors.Naranja,
                active = follow,
                onClick = {
                    permission.request(LocationPurpose.SHOW_POSITION) {
                        showLocation = true
                        follow = !follow || position == null
                        if (position == null) messenger("Buscando señal GPS…")
                    }
                },
            )
            MapZoomControls(controller)
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)),
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                if (state.profile.isNotEmpty()) {
                    ElevationProfileChart(state.profile, height = 84.dp, scrubDistanceMeters = scrub, onScrub = { scrub = it })
                    Spacer(Modifier.height(Spacing.s))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        live?.let { String.format(Locale.ROOT, "%.5f, %.5f", it.latitude, it.longitude) }
                            ?: if (layers.hasMaps) "Arrastrá y pellizcá para recorrer el mapa" else "Sin mapa offline: se ve solo el track",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        if (tracking.isActive && tracking.route?.id == route.id) "Volver al recorrido" else "Iniciar",
                        icon = Icons.Rounded.Navigation,
                        onClick = {
                            when {
                                tracking.isActive && tracking.route?.id == route.id -> nav.navigate(Dest.TRACKING)
                                tracking.isActive -> messenger("Ya hay un recorrido en curso: finalizalo antes de iniciar otro.")
                                else -> startRecording(routeId)
                            }
                        },
                    )
                }
            }
        }
    }
}

