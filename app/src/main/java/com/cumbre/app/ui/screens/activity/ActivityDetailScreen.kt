package com.cumbre.app.ui.screens.activity

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.components.ConfirmDialog
import com.cumbre.app.ui.components.CumbreMap
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.ElevationProfileChart
import com.cumbre.app.ui.components.EmptyState
import com.cumbre.app.ui.components.LoadingState
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.ProfileHint
import com.cumbre.app.ui.components.rememberProfileMarkers
import com.cumbre.app.ui.components.SecondaryButton
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.components.StatGrid
import com.cumbre.app.ui.components.StatValue
import com.cumbre.app.ui.components.TextInputDialog
import com.cumbre.app.ui.cumbreViewModel
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.platform.shareFile
import com.cumbre.app.ui.platform.rememberMapLayers
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.format.Formatters
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityDetailScreen(nav: NavController, activityId: Long) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = cumbreViewModel(key = "activity-$activityId") { ActivityDetailViewModel(activityId, container.activities) }
    val state by vm.state.collectAsStateWithLifecycle()
    val mapFiles by remember { container.maps.observeActiveMapFiles() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val layers = rememberMapLayers()
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        if (uri != null) scope.launch { messenger(if (vm.export(uri)) "GPX exportado." else "No se pudo exportar el archivo.") }
    }

    val a = state.activity
    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(
            title = a?.name ?: "Actividad",
            onBack = { nav.popBackStack() },
            actions = {
                if (a != null) {
                    IconButton(onClick = { rename = true }) { Icon(Icons.Rounded.Edit, "Cambiar nombre", tint = CumbreTheme.extra.onTopBar) }
                    IconButton(onClick = { delete = true }) { Icon(Icons.Rounded.Delete, "Eliminar", tint = CumbreTheme.extra.onTopBar) }
                }
            },
        )
        when {
            state.loading -> LoadingState("Cargando actividad…")
            a == null -> EmptyState(Icons.Rounded.ErrorOutline, "La actividad no existe", "Puede que la hayas eliminado.")
            else -> {
                val track = state.track
                var scrub by remember { mutableStateOf<Double?>(null) }
                val markers = rememberProfileMarkers(track?.lines ?: emptyList(), track?.analysis?.profile ?: emptyList(), scrub)
                CumbreMap(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    layers = layers,
                    routeLines = track?.lines ?: emptyList(),
                    fitBounds = track?.bounds,
                    fitKey = track?.bounds,
                    cursor = markers.cursor,
                    summit = markers.summit,
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                        Text(
                            SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-AR")).format(Date(a.startedAt))
                                .replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        a.routeName?.let {
                            Text("Siguiendo la ruta «$it»", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val stats = track?.analysis?.stats
                        StatGrid(
                            listOf(
                                StatValue(Formatters.distance(a.distanceMeters), "Distancia"),
                                StatValue(Formatters.clock(a.accumulatedMillis), "Tiempo activo"),
                                StatValue(Formatters.clock(a.movingMillis), "En movimiento"),
                                StatValue(if (a.accumulatedMillis > 0) Formatters.speedKmh(a.distanceMeters / (a.accumulatedMillis / 1000.0)) else "—", "Vel. media"),
                                StatValue("↑ ${Formatters.elevation(a.elevationGainMeters)}", "Desnivel + est."),
                                StatValue("↓ ${Formatters.elevation(a.elevationLossMeters)}", "Desnivel − est."),
                                StatValue(stats?.minElevationMeters?.let { Formatters.elevation(it) } ?: "—", "Altitud mín."),
                                StatValue(stats?.maxElevationMeters?.let { Formatters.elevation(it) } ?: "—", "Altitud máx."),
                                StatValue(Formatters.count(a.pointCount), "Puntos GPS"),
                            ),
                            Modifier.padding(top = Spacing.l),
                        )
                        track?.analysis?.profile?.takeIf { it.isNotEmpty() }?.let {
                            SectionTitle("Perfil de altitud (GPS)")
                            ElevationProfileChart(it, height = 110.dp, scrubDistanceMeters = scrub, onScrub = { d -> scrub = d })
                            ProfileHint(it, Modifier.padding(top = 6.dp))
                        }
                        Text(
                            "El desnivel se estima con la altitud del GPS, que es menos precisa que la horizontal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.m, bottom = Spacing.xl),
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        SecondaryButton("Exportar", onClick = { exportLauncher.launch(container.files.safeFileName(a.name, "gpx")) }, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f))
                        SecondaryButton("Compartir", onClick = {
                            scope.launch { vm.shareUri()?.let { context.shareFile(it, "application/gpx+xml", a.name) } }
                        }, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f))
                    }
                    if (a.savedRouteId != null) {
                        PrimaryButton("Ver en Mis rutas", onClick = { nav.navigate(Dest.routeDetail(a.savedRouteId)) }, icon = Icons.Rounded.Route, modifier = Modifier.fillMaxWidth())
                    } else {
                        PrimaryButton("Guardar como ruta", onClick = {
                            scope.launch {
                                val id = vm.saveAsRoute()
                                if (id != null) {
                                    messenger("Guardada en «Mis rutas».")
                                } else {
                                    messenger("La actividad tiene muy pocos puntos para guardarla como ruta.")
                                }
                            }
                        }, icon = Icons.Rounded.Route, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (rename && a != null) {
        TextInputDialog("Cambiar nombre", a.name, "Nombre", "Guardar", onConfirm = { vm.rename(it); rename = false }, onDismiss = { rename = false })
    }
    if (delete && a != null) {
        ConfirmDialog(
            title = "¿Eliminar la actividad?",
            text = "Se borrarán «${a.name}» y todos sus puntos GPS. Si la guardaste como ruta, la ruta se conserva.",
            confirmLabel = "Eliminar",
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                delete = false
                scope.launch { vm.delete(); messenger("Actividad eliminada."); nav.popBackStack() }
            },
            onDismiss = { delete = false },
        )
    }
}
