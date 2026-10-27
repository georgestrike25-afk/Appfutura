package com.cumbre.app.ui.screens.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.map.MapWaypoint
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.components.ConfirmDialog
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreMap
import com.cumbre.app.ui.components.ElevationProfileChart
import com.cumbre.app.ui.components.EmptyState
import com.cumbre.app.ui.components.LoadingState
import com.cumbre.app.ui.components.MapControlButton
import com.cumbre.app.ui.components.MessageBanner
import com.cumbre.app.ui.components.MessageKind
import com.cumbre.app.ui.components.OfflineBadge
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.SecondaryButton
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.components.StatGrid
import com.cumbre.app.ui.components.Tag
import com.cumbre.app.ui.components.TextInputDialog
import com.cumbre.app.ui.components.sourceLabel
import com.cumbre.app.ui.cumbreViewModel
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.platform.LocationPurpose
import com.cumbre.app.ui.platform.openUrl
import com.cumbre.app.ui.platform.rememberLocationPermission
import com.cumbre.app.ui.platform.shareFile
import com.cumbre.app.ui.platform.toMapTheme
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.analysis.QualityIssue
import com.cumbre.core.format.Formatters
import com.cumbre.core.maps.CoverageLevel
import kotlinx.coroutines.launch

private enum class DetailDialog { RENAME, LINK, DELETE, NO_MAP, BUSY }

@Composable
fun RouteDetailScreen(nav: NavController, routeId: Long) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = cumbreViewModel(key = "route-$routeId") { RouteDetailViewModel(routeId, container.routes, container.maps) }
    val state by vm.state.collectAsStateWithLifecycle()
    val mapFiles by remember { container.maps.observeActiveMapFiles() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tracking by container.trackingManager.state.collectAsStateWithLifecycle()
    val permission = rememberLocationPermission(container.settings)
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DetailDialog?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        if (uri != null) scope.launch { messenger(if (vm.export(uri)) "GPX exportado." else "No se pudo exportar el archivo.") }
    }

    fun startNavigation() {
        if (tracking.isActive) {
            dialog = DetailDialog.BUSY
            return
        }
        permission.request(LocationPurpose.NAVIGATE) {
            scope.launch {
                runCatching { container.trackingManager.start(routeId) }
                    .onSuccess { nav.navigate(Dest.TRACKING) }
                    .onFailure { messenger("No se pudo iniciar el recorrido: ${it.message}") }
            }
        }
    }

    val route = state.route
    when {
        state.loading -> LoadingState("Cargando ruta…")
        route == null -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            EmptyState(Icons.Rounded.ErrorOutline, "La ruta no existe", "Puede que la hayas eliminado.", action = {
                SecondaryButton("Volver", onClick = { nav.popBackStack() })
            })
        }
        else -> Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    CumbreMap(
                        modifier = Modifier.fillMaxSize(),
                        mapFiles = mapFiles,
                        theme = settings.mapStyle.toMapTheme(),
                        routeLines = state.geometry?.lines ?: emptyList(),
                        waypoints = state.geometry?.waypoints?.map { MapWaypoint(it.name, it.geo) } ?: emptyList(),
                        fitBounds = route.bounds,
                        fitKey = route.id,
                        onWaypointTap = { messenger(it.name ?: "Waypoint") },
                    )
                    Row(
                        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MapControlButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = { nav.popBackStack() })
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            MapControlButton(
                                if (route.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                if (route.favorite) "Quitar de favoritas" else "Marcar como favorita",
                                tint = CumbreColors.Naranja,
                                onClick = { vm.toggleFavorite() },
                            )
                            Box {
                                MapControlButton(Icons.Rounded.MoreVert, "Más acciones", onClick = { menu = true })
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Cambiar nombre") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; dialog = DetailDialog.RENAME })
                                    DropdownMenuItem(text = { Text("Exportar GPX") }, leadingIcon = { Icon(Icons.Rounded.Download, null) }, onClick = {
                                        menu = false
                                        exportLauncher.launch(container.files.safeFileName(route.name, "gpx"))
                                    })
                                    DropdownMenuItem(text = { Text("Compartir archivo") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = {
                                        menu = false
                                        scope.launch { vm.shareUri()?.let { context.shareFile(it, "application/gpx+xml", route.name) } ?: messenger("No se encontró el archivo de la ruta.") }
                                    })
                                    DropdownMenuItem(text = { Text("Enlace de origen") }, leadingIcon = { Icon(Icons.Rounded.Link, null) }, onClick = { menu = false; dialog = DetailDialog.LINK })
                                    DropdownMenuItem(
                                        text = { Text("Eliminar ruta", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { menu = false; dialog = DetailDialog.DELETE },
                                    )
                                }
                            }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OfflineBadge(state.coverage?.level, state.coveringMaps.firstOrNull()?.displayName)
                        Tag(sourceLabel(route.source))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(route.name, style = MaterialTheme.typography.headlineSmall)
                    route.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it.take(400), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (state.geometryMissing) {
                        Spacer(Modifier.height(Spacing.m))
                        MessageBanner("No se encontró el archivo GPX de esta ruta en el teléfono. Podés eliminarla e importarla de nuevo.", MessageKind.ERROR)
                    }
                    Spacer(Modifier.height(Spacing.l))
                    state.stats?.let { StatGrid(routeStats(it)) }
                    if (state.profile.isNotEmpty()) {
                        SectionTitle("Perfil de elevación")
                        ElevationProfileChart(
                            state.profile,
                            warningDistanceMeters = state.issues.filterIsInstance<QualityIssue.LargeGap>().firstOrNull()?.atDistanceMeters,
                        )
                    }
                    SectionTitle("Mapa sin conexión")
                    OfflineSection(state.coverage?.level, state.coveringMaps.map { it.displayName }, state.anyMapsInstalled) { nav.navigate(Dest.OFFLINE_MAPS) }
                    if (state.issues.isNotEmpty()) {
                        SectionTitle("Calidad de los datos")
                        QualityIssuesList(state.issues)
                    }
                    state.geometry?.waypoints?.takeIf { it.isNotEmpty() }?.let { wps ->
                        SectionTitle("Waypoints (${wps.size})")
                        WaypointList(wps.map { it.name to it.description })
                    }
                    route.sourceUrl?.let { url ->
                        SectionTitle("Origen")
                        SecondaryButton(
                            if (route.source == "WIKILOC") "Ver en Wikiloc" else "Abrir enlace de origen",
                            onClick = { context.openUrl(url) },
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "Importada el ${java.text.SimpleDateFormat("d/M/yyyy", java.util.Locale.ROOT).format(java.util.Date(route.createdAt))}" +
                            (route.originalFileName?.let { " · $it" } ?: "") + " · ${Formatters.bytes(route.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xl),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                SecondaryButton("Compartir", onClick = {
                    scope.launch { vm.shareUri()?.let { context.shareFile(it, "application/gpx+xml", route.name) } }
                }, icon = Icons.Rounded.Share)
                PrimaryButton(
                    if (tracking.isActive && tracking.route?.id == route.id) "Volver al recorrido" else "Iniciar recorrido",
                    onClick = {
                        when {
                            tracking.isActive && tracking.route?.id == route.id -> nav.navigate(Dest.TRACKING)
                            state.geometryMissing -> messenger("Falta el archivo GPX de la ruta.")
                            state.coverage?.level == CoverageLevel.NONE && !tracking.isActive -> dialog = DetailDialog.NO_MAP
                            else -> startNavigation()
                        }
                    },
                    icon = Icons.Rounded.Navigation,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    when (dialog) {
        DetailDialog.RENAME -> TextInputDialog(
            title = "Cambiar nombre",
            initial = route?.name ?: "",
            label = "Nombre",
            confirmLabel = "Guardar",
            onConfirm = { vm.rename(it); dialog = null },
            onDismiss = { dialog = null },
        )
        DetailDialog.LINK -> TextInputDialog(
            title = "Enlace de origen",
            initial = route?.sourceUrl ?: "",
            label = "https://…",
            confirmLabel = "Guardar",
            allowEmpty = true,
            onConfirm = { vm.setSourceUrl(it); dialog = null },
            onDismiss = { dialog = null },
        )
        DetailDialog.DELETE -> ConfirmDialog(
            title = "¿Eliminar la ruta?",
            text = "Se borrará «${route?.name}» y su archivo GPX del teléfono. Las actividades grabadas no se eliminan.",
            confirmLabel = "Eliminar",
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                dialog = null
                scope.launch {
                    vm.delete()
                    messenger("Ruta eliminada.")
                    nav.popBackStack()
                }
            },
            onDismiss = { dialog = null },
        )
        DetailDialog.NO_MAP -> ConfirmDialog(
            title = "Sin mapa offline para esta zona",
            text = "Podés seguir el recorrido igual: vas a ver la línea del track y tu posición GPS, pero sin el fondo del mapa. " +
                "Para ver senderos y relieve sin conexión, instalá un mapa de la región antes de salir.",
            confirmLabel = "Iniciar igual",
            dismissLabel = "Ver mapas",
            icon = Icons.Rounded.Map,
            onConfirm = { dialog = null; startNavigation() },
            onDismiss = { dialog = null; nav.navigate(Dest.OFFLINE_MAPS) },
        )
        DetailDialog.BUSY -> ConfirmDialog(
            title = "Ya hay un recorrido en curso",
            text = "Finalizá o descartá la actividad actual («${tracking.activityName}») antes de iniciar otra.",
            confirmLabel = "Ir al recorrido",
            onConfirm = { dialog = null; nav.navigate(Dest.TRACKING) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun OfflineSection(level: CoverageLevel?, mapNames: List<String>, anyInstalled: Boolean, onManage: () -> Unit) {
    CumbreCard {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val text = when (level) {
                CoverageLevel.FULL -> "Todo el recorrido está cubierto por: ${mapNames.joinToString()}. Podés seguirlo sin Internet."
                CoverageLevel.PARTIAL -> "Parte del recorrido queda fuera de los mapas instalados (${mapNames.joinToString()}). Instalá el mapa de la región vecina."
                CoverageLevel.NONE -> if (anyInstalled) "Ninguno de tus mapas cubre esta zona." else "No tenés mapas sin conexión instalados."
                null -> "Comprobando cobertura…"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OfflineBadge(level)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Text(
                "La ruta y tu posición GPS siempre funcionan sin conexión; el mapa de fondo depende de los archivos instalados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (level != CoverageLevel.FULL) {
                SecondaryButton("Mapas sin conexión", onClick = onManage, icon = Icons.Rounded.Map, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
