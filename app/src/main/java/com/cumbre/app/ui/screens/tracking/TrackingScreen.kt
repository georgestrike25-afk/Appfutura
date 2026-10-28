package com.cumbre.app.ui.screens.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.map.MapWaypoint
import com.cumbre.app.tracking.TrackingState
import com.cumbre.app.tracking.TrackingStatus
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.components.ConfirmDialog
import com.cumbre.app.ui.components.CumbreMap
import com.cumbre.app.ui.components.EmptyState
import com.cumbre.app.ui.components.MapBottomPanel
import com.cumbre.app.ui.components.MapControlButton
import com.cumbre.app.ui.components.MapZoomControls
import com.cumbre.app.ui.components.MessageBanner
import com.cumbre.app.ui.components.MessageKind
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.SecondaryButton
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.platform.rememberLiveLocation
import com.cumbre.app.ui.platform.toMapPosition
import com.cumbre.app.ui.platform.rememberMapLayers
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.DataText
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.format.Formatters
import com.cumbre.core.recording.GpsSignal
import kotlinx.coroutines.launch

@Composable
fun TrackingScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val state by container.trackingManager.state.collectAsStateWithLifecycle()
    val mapFiles by remember { container.maps.observeActiveMapFiles() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val layers = rememberMapLayers()
    var follow by rememberSaveable { mutableStateOf(true) }
    var courseUp by rememberSaveable { mutableStateOf(false) }
    var lastBearing by remember { mutableStateOf(0f) }
    var finishDialog by remember { mutableStateOf(false) }
    var mapController by remember { mutableStateOf<com.cumbre.app.map.CumbreMapController?>(null) }
    var discardDialog by remember { mutableStateOf(false) }

    // Mantener la pantalla encendida durante la navegación, si el usuario lo prefiere.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn, state.status) {
        view.keepScreenOn = settings.keepScreenOn && state.status == TrackingStatus.RECORDING
        onDispose { view.keepScreenOn = false }
    }

    if (!state.isActive) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                title = "No hay un recorrido en curso",
                text = "Iniciá un recorrido desde el detalle de una ruta o grabá una actividad libre desde Actividad.",
                action = { SecondaryButton("Volver", onClick = { nav.popBackStack() }) },
            )
        }
        return
    }

    // En pausa el servicio libera el GPS; la pantalla muestra la posición mientras está visible.
    val live by rememberLiveLocation(container.locationSource, state.status == TrackingStatus.PAUSED && container.locationSource.hasAnyPermission())
    val fix = if (state.status == TrackingStatus.PAUSED) live ?: state.lastFix else state.lastFix
    val position = fix?.toMapPosition()
    val bearingNow = position?.bearingDegrees
    LaunchedEffect(bearingNow) { bearingNow?.let { lastBearing = it.toFloat() } }

    val routeBounds = remember(state.route) { state.route?.lines?.flatten()?.let { com.cumbre.core.geo.GeoBounds.of(it) } }

    BackHandler { nav.popBackStack() }

    Box(Modifier.fillMaxSize()) {
        CumbreMap(
            modifier = Modifier.fillMaxSize(),
            layers = layers,
            routeLines = state.route?.lines ?: emptyList(),
            waypoints = state.route?.waypoints?.map { MapWaypoint(it.first, it.second) } ?: emptyList(),
            recordedLines = state.recordedLines,
            position = position,
            followPosition = follow,
            rotationDegrees = if (courseUp) lastBearing else 0f,
            fitBounds = if (position == null) routeBounds else null,
            fitKey = state.activityId,
            retainKey = "tracking",
            onWaypointTap = { messenger(it.name ?: "Waypoint") },
            onController = { mapController = it },
        )

        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                MapControlButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver (el recorrido sigue)", onClick = { nav.popBackStack() })
                GpsChip(state)
                Spacer(Modifier.weight(1f))
                if (mapFiles.isNotEmpty()) StatusChip(Icons.Rounded.CloudOff, "Mapa offline")
            }
            val follow0 = state.follow
            if (follow0?.offRoute == true) {
                MessageBanner(
                    title = "Te desviaste del recorrido",
                    text = "Estás a ${Formatters.distance(follow0.distanceToTrackMeters)} del track. Volvé hacia la línea magenta.",
                    kind = MessageKind.WARNING,
                )
            }
            if (state.signal == GpsSignal.LOST && state.status == TrackingStatus.RECORDING) {
                MessageBanner(
                    text = "Sin señal GPS. Se muestra la última posición conocida; no se registran puntos hasta recuperar la señal.",
                    kind = MessageKind.WARNING,
                )
            }
            if (state.recovered && state.status == TrackingStatus.PAUSED) {
                MessageBanner(
                    text = "Recuperamos el recorrido interrumpido con ${state.stats.pointCount} puntos guardados. Tocá «Reanudar» para seguir grabando.",
                    kind = MessageKind.INFO,
                )
            }
        }

        Column(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MapControlButton(
                Icons.Rounded.Explore,
                if (courseUp) "Norte arriba" else "Orientar según el rumbo",
                active = courseUp,
                onClick = { courseUp = !courseUp },
            )
            MapControlButton(
                Icons.Rounded.MyLocation,
                if (follow) "Dejar de seguir mi posición" else "Seguir mi posición",
                tint = CumbreColors.Naranja,
                active = follow,
                onClick = { follow = !follow },
            )
            MapZoomControls(mapController)
        }

        MapBottomPanel(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)) {
            TrackingPanel(
                state = state,
                altitude = fix?.altitudeMeters,
                onPause = { scope.launch { container.trackingManager.pause() } },
                onResume = { scope.launch { container.trackingManager.resume() } },
                onFinish = { finishDialog = true },
            )
        }
    }

    if (finishDialog) {
        AlertDialog(
            onDismissRequest = { finishDialog = false },
            icon = { Icon(Icons.Rounded.Stop, contentDescription = null) },
            title = { Text("¿Finalizar el recorrido?") },
            text = {
                Text(
                    "${Formatters.distance(state.stats.distanceMeters)} en ${Formatters.clock(state.elapsedMillis)}. " +
                        "Se guardará en tu historial y podrás exportarlo como GPX.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    finishDialog = false
                    scope.launch {
                        val id = container.trackingManager.finish()
                        if (id != null) {
                            nav.navigate(Dest.activityDetail(id)) { popUpTo(Dest.TRACKING) { inclusive = true } }
                        }
                    }
                }) { Text("Finalizar y guardar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { finishDialog = false; discardDialog = true }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { finishDialog = false }) { Text("Seguir") }
                }
            },
        )
    }
    if (discardDialog) {
        ConfirmDialog(
            title = "¿Descartar el recorrido?",
            text = "Se borrarán los ${state.stats.pointCount} puntos registrados. Esta acción no se puede deshacer.",
            confirmLabel = "Descartar",
            destructive = true,
            onConfirm = {
                discardDialog = false
                scope.launch {
                    container.trackingManager.discard()
                    messenger("Recorrido descartado.")
                    nav.popBackStack()
                }
            },
            onDismiss = { discardDialog = false },
        )
    }
}

@Composable
private fun TrackingPanel(
    state: TrackingState,
    altitude: Double?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val follow = state.follow
    if (follow != null) {
        // Con una ruta, lo principal es cuánto falta para llegar (distancia y tiempo).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (follow.nearEnd) "Llegaste" else "Te falta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(if (follow.nearEnd) "¡Final!" else Formatters.distance(follow.remainingMeters), style = DataText.Hero)
                state.etaSeconds?.takeIf { !follow.nearEnd }?.let { eta ->
                    Text(
                        "≈ ${Formatters.duration(eta)} · llegada ${arrivalClock(eta)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Formatters.clock(state.elapsedMillis), style = DataText.Large)
                Text(
                    if (state.status == TrackingStatus.PAUSED) "En pausa" else "Recorrido ${Formatters.distance(state.stats.distanceMeters)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Formatters.kilometersValue(state.stats.distanceMeters), style = DataText.Hero)
                    Text(" km", style = DataText.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    if (state.route != null) "Recorrido · esperando posición sobre el track" else "Recorrido",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Formatters.clock(state.elapsedMillis), style = DataText.Large)
                Text(if (state.status == TrackingStatus.PAUSED) "En pausa" else "Tiempo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(Modifier.padding(top = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
        PanelStat(state.currentSpeedMps?.takeIf { state.status == TrackingStatus.RECORDING }?.let { Formatters.speedKmhValue(it) } ?: "—", "km/h", "Vel. actual", Modifier.weight(1f))
        PanelStat(if (state.stats.elapsedMillis > 60_000) Formatters.speedKmhValue(state.stats.averageSpeedMps) else "—", "km/h", "Vel. media", Modifier.weight(1f))
        PanelStat(altitude?.let { Formatters.elevationValue(it) } ?: "—", "m", "Altitud", Modifier.weight(1f))
        PanelStat(if (state.stats.pointCount > 10) "↑ ${Formatters.elevationValue(state.stats.elevationGainMeters)}" else "—", "m", "Desnivel est.", Modifier.weight(1f))
    }
    follow?.nextWaypoint?.let { wp ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.m)) {
            Icon(Icons.Rounded.Flag, contentDescription = null, tint = CumbreColors.Naranja, modifier = Modifier.size(20.dp))
            Text(
                "${wp.name ?: "Próximo waypoint"} · a ${Formatters.distance(follow.distanceToNextWaypointMeters ?: 0.0)}" +
                    (state.etaToWaypointSeconds?.let { " (≈ ${Formatters.duration(it)})" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.s),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.status == TrackingStatus.RECORDING) {
            SecondaryButton("Pausar", onClick = onPause, icon = Icons.Rounded.Pause, modifier = Modifier.weight(1f))
        } else {
            PrimaryButton("Reanudar", onClick = onResume, icon = Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f))
        }
        PrimaryButton(
            "Finalizar",
            onClick = onFinish,
            icon = Icons.Rounded.Stop,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun arrivalClock(etaSeconds: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(System.currentTimeMillis() + etaSeconds * 1000))

@Composable
private fun PanelStat(value: String, unit: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = DataText.Medium, maxLines = 1)
            if (value != "—") Text(" $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun GpsChip(state: TrackingState) {
    val extra = CumbreTheme.extra
    val acc = state.lastFix?.accuracyMeters
    val (icon, text, tint) = when (state.signal) {
        GpsSignal.SEARCHING -> Triple(Icons.Rounded.GpsNotFixed, "Buscando GPS…", MaterialTheme.colorScheme.onSurfaceVariant)
        GpsSignal.GOOD -> Triple(Icons.Rounded.GpsFixed, "GPS ${acc?.let { Formatters.accuracy(it) } ?: ""}", extra.success)
        GpsSignal.WEAK -> Triple(Icons.Rounded.GpsNotFixed, "GPS débil ${acc?.let { Formatters.accuracy(it) } ?: ""}", extra.warning)
        GpsSignal.LOST -> Triple(Icons.Rounded.GpsOff, "Sin señal", MaterialTheme.colorScheme.error)
    }
    StatusChip(icon, text.trim(), tint)
}

@Composable
private fun StatusChip(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        Modifier.clip(MaterialTheme.shapes.medium).background(CumbreTheme.extra.mapControl).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = CumbreTheme.extra.onMapControl, modifier = Modifier.padding(start = 6.dp))
    }
}
