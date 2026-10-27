package com.cumbre.app.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cumbre.app.data.db.ActivityEntity
import com.cumbre.app.tracking.TrackingStatus
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.components.AccentButton
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.EmptyState
import com.cumbre.app.ui.components.IconBadge
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.components.StatGrid
import com.cumbre.app.ui.components.StatValue
import com.cumbre.app.ui.navigation.Dest
import com.cumbre.app.ui.platform.LocationPurpose
import com.cumbre.app.ui.platform.rememberLocationPermission
import com.cumbre.app.ui.screens.home.ActiveTrackingCard
import com.cumbre.app.ui.theme.DataText
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.format.Formatters
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("EEEE d 'de' MMMM · HH:mm", Locale.forLanguageTag("es-AR"))

@Composable
fun ActivityListScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val tracking by container.trackingManager.state.collectAsStateWithLifecycle()
    val history by remember { container.activities.observeFinished() }.collectAsStateWithLifecycle(initialValue = null)
    val permission = rememberLocationPermission(container.settings)

    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Actividad")
        LazyColumn(
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                if (tracking.isActive) {
                    ActiveTrackingCard(
                        name = tracking.activityName,
                        detail = "${Formatters.distance(tracking.stats.distanceMeters)} · ${Formatters.clock(tracking.elapsedMillis)}" +
                            if (tracking.status == TrackingStatus.PAUSED) " · en pausa" else "",
                        onClick = { nav.navigate(Dest.TRACKING) },
                    )
                } else {
                    CumbreCard {
                        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                IconBadge(Icons.Rounded.FiberManualRecord, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.error)
                                Column(Modifier.weight(1f)) {
                                    Text("Grabar una actividad", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Registrá tu recorrido con GPS aunque no sigas una ruta. Funciona sin conexión y con la pantalla apagada.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            AccentButton(
                                "Iniciar actividad",
                                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    permission.request(LocationPurpose.NAVIGATE) {
                                        scope.launch {
                                            runCatching { container.trackingManager.start(null) }
                                                .onSuccess { nav.navigate(Dest.TRACKING) }
                                                .onFailure { messenger("No se pudo iniciar: ${it.message}") }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            val list = history
            if (list != null && list.isNotEmpty()) {
                item {
                    SectionTitle("Totales")
                    CumbreCard {
                        StatGrid(
                            listOf(
                                StatValue(list.size.toString(), if (list.size == 1) "Actividad" else "Actividades"),
                                StatValue(Formatters.distance(list.sumOf { it.distanceMeters }), "Distancia"),
                                StatValue("↑ ${Formatters.elevation(list.sumOf { it.elevationGainMeters })}", "Desnivel est."),
                            ),
                            Modifier.padding(Spacing.l),
                        )
                    }
                    SectionTitle("Historial")
                }
                items(list, key = { it.id }) { a -> ActivityRow(a) { nav.navigate(Dest.activityDetail(a.id)) } }
            } else if (list != null) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Hiking,
                        title = "Sin actividades todavía",
                        text = "Cuando finalices un recorrido, vas a verlo acá con su distancia, tiempo y desnivel.",
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(a: ActivityEntity, onClick: () -> Unit) {
    CumbreCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(Spacing.l)) {
            Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                dateFormat.format(Date(a.startedAt)).replaceFirstChar { it.titlecase() } + (a.routeName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Text(Formatters.distance(a.distanceMeters), style = DataText.Small)
                Text(Formatters.clock(a.accumulatedMillis), style = DataText.Small)
                Text("↑ ${Formatters.elevation(a.elevationGainMeters)}", style = DataText.Small, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
