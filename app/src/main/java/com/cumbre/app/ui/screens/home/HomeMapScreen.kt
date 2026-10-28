package com.cumbre.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.R
import com.cumbre.app.data.MapStyle
import com.cumbre.app.map.MapWaypoint
import com.cumbre.app.map.CumbreMapController
import com.cumbre.app.ui.components.AccentButton
import com.cumbre.app.ui.components.MapCapsule
import com.cumbre.app.ui.components.MapZoomControls
import com.cumbre.app.ui.platform.rememberRecordingStarter
import com.cumbre.core.geo.GeoMath
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.Navigation
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalGpxPicker
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreMap
import com.cumbre.app.ui.components.MapBottomPanel
import com.cumbre.app.ui.components.MapControlButton
import com.cumbre.app.ui.components.MessageBanner
import com.cumbre.app.ui.components.MessageKind
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.SecondaryButton
import com.cumbre.app.ui.components.TrackThumbnail
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.platform.LocationPurpose
import com.cumbre.app.ui.platform.openLocationSettings
import com.cumbre.app.ui.platform.rememberLiveLocation
import com.cumbre.app.ui.platform.rememberLocationPermission
import com.cumbre.app.ui.platform.toMapPosition
import com.cumbre.app.ui.platform.rememberMapLayers
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.app.tracking.TrackingStatus
import com.cumbre.core.format.Formatters
import com.cumbre.core.geo.PolylineCodec
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

@Composable
fun HomeMapScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val openPicker = LocalGpxPicker.current
    val scope = rememberCoroutineScope()

    val mapFiles by remember { container.maps.observeActiveMapFiles() }.collectAsStateWithLifecycle(initialValue = emptyList())

    val layers = rememberMapLayers()
    val tracking by container.trackingManager.state.collectAsStateWithLifecycle()
    val recent by remember { container.routes.observeMostRecent() }.collectAsStateWithLifecycle(initialValue = null)
    val permission = rememberLocationPermission(container.settings)

    var showLocation by rememberSaveable { mutableStateOf(permission.hasAnyPermission) }
    var follow by rememberSaveable { mutableStateOf(false) }
    var courseUp by rememberSaveable { mutableStateOf(false) }
    var lastBearing by remember { mutableStateOf(0f) }
    var layersDialog by remember { mutableStateOf(false) }
    var mapAvailable by remember { mutableStateOf(true) }
    var controller by remember { mutableStateOf<CumbreMapController?>(null) }
    val startRecording = rememberRecordingStarter(nav)

    val live by rememberLiveLocation(container.locationSource, showLocation && !tracking.isActive && permission.hasAnyPermission)
    val fix = if (tracking.isActive) tracking.lastFix else live
    val position = fix?.toMapPosition()
    val bearingNow = position?.bearingDegrees
    LaunchedEffect(bearingNow) { bearingNow?.let { lastBearing = it.toFloat() } }
    val rotation = if (courseUp) lastBearing else 0f

    Box(Modifier.fillMaxSize()) {
        CumbreMap(
            modifier = Modifier.fillMaxSize(),
            layers = layers,
            routeLines = tracking.route?.lines ?: emptyList(),
            waypoints = tracking.route?.waypoints?.map { MapWaypoint(it.first, it.second) } ?: emptyList(),
            recordedLines = tracking.recordedLines,
            position = position,
            followPosition = follow,
            rotationDegrees = rotation,
            retainKey = "home",
            onWaypointTap = { w -> messenger(w.name ?: "Waypoint sin nombre") },
            onMapAvailability = { mapAvailable = it },
            onController = { controller = it },
        )

        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val accuracy = fix?.accuracyMeters
                MapCapsule(
                    if (accuracy != null) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                    when {
                        !permission.hasAnyPermission || !showLocation -> "GPS apagado"
                        accuracy == null -> "Buscando GPS…"
                        else -> "GPS ${Formatters.accuracy(accuracy.toDouble())}"
                    },
                    iconTint = if (accuracy != null) CumbreTheme.extra.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        permission.request(LocationPurpose.SHOW_POSITION) { showLocation = true }
                    },
                )
                val hasMaps = mapFiles.isNotEmpty() && mapAvailable
                MapCapsule(
                    if (hasMaps) Icons.Rounded.CloudOff else Icons.Rounded.DownloadForOffline,
                    if (hasMaps) "Mapa offline" else "Sin mapa",
                    iconTint = if (hasMaps) CumbreTheme.extra.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { nav.navigate(Dest.OFFLINE_MAPS) },
                )
            }
            if (mapFiles.isEmpty()) {
                MessageBanner(
                    text = "Instalá un mapa .map de tu zona para ver senderos y relieve sin conexión. Sin mapa, Cumbre igual muestra tu ruta y tu posición.",
                    kind = MessageKind.INFO,
                    actionLabel = "Mapas sin conexión",
                    onAction = { nav.navigate(Dest.OFFLINE_MAPS) },
                )
            }
            if (showLocation && permission.hasAnyPermission && !container.locationSource.isGpsEnabled()) {
                MessageBanner(
                    text = "La ubicación del teléfono está desactivada.",
                    kind = MessageKind.WARNING,
                    actionLabel = "Activar ubicación",
                    onAction = { context.openLocationSettings() },
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(
                Modifier.align(Alignment.End).padding(end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                MapControlButton(Icons.Rounded.Layers, "Capas y estilo del mapa", onClick = { layersDialog = true })
                MapControlButton(
                    Icons.Rounded.Explore,
                    if (courseUp) "Norte arriba" else "Orientar según el rumbo",
                    active = courseUp,
                    onClick = {
                        courseUp = !courseUp
                        messenger(
                            if (courseUp) "El mapa gira según tu rumbo de marcha (se actualiza al caminar)." else "Norte arriba.",
                        )
                    },
                )
                MapZoomControls(controller)
                MapControlButton(
                    Icons.Rounded.MyLocation,
                    "Centrar en mi ubicación",
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
            }
            MapBottomPanel {
                if (tracking.isActive) {
                    ActiveTrackingCard(
                        name = tracking.activityName,
                        detail = "${Formatters.distance(tracking.stats.distanceMeters)} · ${Formatters.clock(tracking.elapsedMillis)}" +
                            if (tracking.status == TrackingStatus.PAUSED) " · en pausa" else "",
                        onClick = { nav.navigate(Dest.TRACKING) },
                    )
                } else {
                    val r = recent
                    if (r != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { nav.navigate(Dest.routeDetail(r.id)) }) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val here = fix
                                val start = r.start
                                Text(
                                    if (here != null && start != null) {
                                        "El inicio está a ${Formatters.distance(GeoMath.distanceMeters(here.latitude, here.longitude, start.latitude, start.longitude))} de vos"
                                    } else {
                                        Formatters.distance(r.distanceMeters) + (r.elevationGainMeters?.let { " · ↑ ${Formatters.elevation(it)}" } ?: "")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            AccentButton("Iniciar", onClick = { startRecording(r.id) }, icon = Icons.Rounded.Navigation)
                        }
                    } else {
                        PrimaryButton("Importar GPX", onClick = openPicker, icon = Icons.Rounded.UploadFile, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (layersDialog) {
        AlertDialog(
            onDismissRequest = { layersDialog = false },
            icon = { Icon(Icons.Rounded.Layers, contentDescription = null) },
            title = { Text("Estilo del mapa") },
            text = {
                Column {
                    listOf(MapStyle.DEFAULT to "Cumbre (predeterminado)", MapStyle.OSMARENDER to "Osmarender (más detalle)").forEach { (style, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { scope.launch { container.settings.setMapStyle(style) } }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.mapStyle == style, onClick = { scope.launch { container.settings.setMapStyle(style) } })
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .clickable { scope.launch { container.settings.setShowRelief(!settings.showRelief) } }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = settings.showRelief, onCheckedChange = { v -> scope.launch { container.settings.setShowRelief(v) } })
                        Text("Mostrar relieve", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        if (mapFiles.isEmpty()) "No hay mapas instalados." else "${mapFiles.size} ${if (mapFiles.size == 1) "mapa activo" else "mapas activos"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { layersDialog = false; nav.navigate(Dest.OFFLINE_MAPS) }) { Text("Gestionar mapas") } },
            dismissButton = { TextButton(onClick = { layersDialog = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
fun ActiveTrackingCard(name: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondary)
            .clickable(onClick = onClick)
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(CumbreColors.Naranja), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null, tint = CumbreColors.GrisOscuro)
        }
        Column(Modifier.weight(1f)) {
            Text("Actividad en curso", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f))
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f))
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Abrir navegación", tint = MaterialTheme.colorScheme.onSecondary)
    }
}
