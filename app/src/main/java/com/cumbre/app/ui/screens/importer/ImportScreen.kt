package com.cumbre.app.ui.screens.importer

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.map.MapWaypoint
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalGpxPicker
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.components.CumbreMap
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.ElevationProfileChart
import com.cumbre.app.ui.components.EmptyState
import com.cumbre.app.ui.components.LoadingState
import com.cumbre.app.ui.components.MessageBanner
import com.cumbre.app.ui.components.MessageKind
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.ProfileHint
import com.cumbre.app.ui.components.rememberProfileMarkers
import com.cumbre.app.ui.components.SecondaryButton
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.cumbreViewModel
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.platform.rememberMapLayers
import com.cumbre.app.ui.screens.detail.QualityIssuesList
import com.cumbre.app.ui.screens.detail.WaypointList
import com.cumbre.app.ui.screens.detail.routeStats
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.analysis.QualityIssue
import com.cumbre.core.format.Formatters
import com.cumbre.core.library.WikilocLinks

@Composable
fun ImportScreen(nav: NavController, uri: Uri?) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val messenger = LocalMessenger.current
    val openPicker = LocalGpxPicker.current
    val vm = cumbreViewModel(key = "import-$uri") { ImportViewModel(uri, container.routes, container.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val mapFiles by remember { container.maps.observeActiveMapFiles() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val layers = rememberMapLayers()

    LaunchedEffect(state) {
        val s = state
        if (s is ImportUiState.Saved) {
            messenger(if (s.wasDuplicate) "Esa ruta ya estaba guardada: abrimos la existente." else "Ruta guardada en tu biblioteca.")
            nav.navigate(Dest.routeDetail(s.routeId)) {
                popUpTo(Dest.IMPORT) { inclusive = true }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Preparar ruta", onBack = { nav.popBackStack() })
        when (val s = state) {
            ImportUiState.Loading, is ImportUiState.Saved -> LoadingState("Analizando el archivo GPX…")
            is ImportUiState.Failed -> Column(Modifier.verticalScroll(rememberScrollState())) {
                EmptyState(
                    icon = Icons.Rounded.Error,
                    title = "No se pudo importar",
                    text = s.message,
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            PrimaryButton("Elegir otro archivo", onClick = { nav.popBackStack(); openPicker() }, icon = Icons.Rounded.UploadFile)
                            SecondaryButton("Volver", onClick = { nav.popBackStack() })
                        }
                    },
                )
            }
            is ImportUiState.Preview -> {
                val p = s.pending
                val lines = remember(p) { p.document.lines().map { l -> l.map { it.geo } } }
                val waypoints = remember(p) { p.document.waypoints.map { MapWaypoint(it.name, it.geo) } }
                var scrub by remember(p) { mutableStateOf<Double?>(null) }
                val markers = rememberProfileMarkers(lines, p.analysis.profile, scrub)
                CumbreMap(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    layers = layers,
                    routeLines = lines,
                    waypoints = waypoints,
                    fitBounds = p.analysis.stats.bounds,
                    cursor = markers.cursor,
                    summit = markers.summit,
                    onWaypointTap = { messenger(it.name ?: "Waypoint") },
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                        p.duplicateOf?.let { existing ->
                            MessageBanner(
                                title = "Esta ruta ya está en tu biblioteca",
                                text = "Coincide con «${existing.name}». Al guardar no se creará un duplicado: se abrirá la ruta existente.",
                                kind = MessageKind.INFO,
                                actionLabel = "Ver ruta guardada",
                                onAction = { nav.navigate(Dest.routeDetail(existing.id)) { popUpTo(Dest.IMPORT) { inclusive = true } } },
                            )
                            Spacer(Modifier.height(Spacing.m))
                        }
                        OutlinedTextField(
                            value = s.name,
                            onValueChange = vm::setName,
                            label = { Text("Nombre de la ruta") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            buildString {
                                append(p.originalFileName ?: "Archivo GPX")
                                append(" · ")
                                append(Formatters.bytes(p.fileSizeBytes))
                                p.document.creator?.let { append(" · creado con $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Spacer(Modifier.height(Spacing.l))
                        com.cumbre.app.ui.components.StatGrid(routeStats(p.analysis.stats))
                        if (p.analysis.profile.isNotEmpty()) {
                            SectionTitle("Perfil de elevación")
                            ElevationProfileChart(
                                p.analysis.profile,
                                height = 120.dp,
                                warningDistanceMeters = p.analysis.issues.filterIsInstance<QualityIssue.LargeGap>().firstOrNull()?.atDistanceMeters,
                                scrubDistanceMeters = scrub,
                                onScrub = { scrub = it },
                            )
                            ProfileHint(p.analysis.profile, Modifier.padding(top = 6.dp))
                        }
                        if (p.analysis.issues.isNotEmpty()) {
                            SectionTitle("Calidad de los datos")
                            QualityIssuesList(p.analysis.issues)
                        }
                        if (p.document.waypoints.isNotEmpty()) {
                            SectionTitle("Waypoints (${p.document.waypoints.size})")
                            WaypointList(p.document.waypoints.map { it.name to it.description })
                        }
                        SectionTitle("Origen (opcional)")
                        OutlinedTextField(
                            value = s.sourceUrl,
                            onValueChange = vm::setSourceUrl,
                            label = { Text("Enlace de la ruta en Wikiloc u otro sitio") },
                            leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Link, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (s.sourceUrl.isNotBlank() && !s.sourceUrl.startsWith("http")) {
                            Text("El enlace debe empezar con https://", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (WikilocLinks.isWikilocUrl(s.sourceUrl)) {
                            Text("Se guardará como ruta de Wikiloc.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(Spacing.xl))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    SecondaryButton("Cancelar", onClick = { nav.popBackStack() }, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        if (p.duplicateOf != null) "Abrir guardada" else "Guardar ruta",
                        onClick = vm::save,
                        icon = Icons.Rounded.Save,
                        enabled = !s.saving && s.name.isNotBlank(),
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
        }
    }
}
