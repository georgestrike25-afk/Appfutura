package com.cumbre.app.ui.screens.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cumbre.app.data.MapStyle
import com.cumbre.app.data.ThemeMode
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.components.ChipOption
import com.cumbre.app.ui.components.ChoiceChips
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.ListRow
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.platform.openAppSettings
import com.cumbre.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Ajustes", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l)) {
            SectionTitle("Apariencia", Modifier.padding(top = 0.dp))
            ChoiceChips(
                listOf(ChipOption(ThemeMode.SYSTEM, "Según el sistema"), ChipOption(ThemeMode.LIGHT, "Claro"), ChipOption(ThemeMode.DARK, "Oscuro")),
                settings.themeMode,
                { scope.launch { container.settings.setThemeMode(it) } },
                contentPadding = PaddingValues(),
            )
            SectionTitle("Estilo del mapa")
            ChoiceChips(
                listOf(ChipOption(MapStyle.DEFAULT, "Cumbre"), ChipOption(MapStyle.OSMARENDER, "Osmarender")),
                settings.mapStyle,
                { scope.launch { container.settings.setMapStyle(it) } },
                contentPadding = PaddingValues(),
            )
            CumbreCard(Modifier.padding(top = Spacing.m)) {
                ListRow(
                    "Mostrar relieve",
                    subtitle = "Sombreado de montañas (requiere datos de elevación en Mapas sin conexión)",
                    icon = Icons.Rounded.Landscape,
                    trailing = { Switch(settings.showRelief, { v -> scope.launch { container.settings.setShowRelief(v) } }) },
                )
            }
            SectionTitle("Navegación")
            CumbreCard {
                ListRow(
                    "Aviso de desvío",
                    subtitle = "Vibra y notifica si te alejás del track",
                    icon = Icons.Rounded.Vibration,
                    trailing = { Switch(settings.offRouteAlertEnabled, { v -> scope.launch { container.settings.setOffRouteAlert(v) } }) },
                )
                if (settings.offRouteAlertEnabled) {
                    Column(Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.m)) {
                        Text("Distancia para avisar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.s))
                        ChoiceChips(
                            listOf(25, 50, 100, 200).map { ChipOption(it, "$it m") },
                            settings.offRouteThresholdMeters,
                            { scope.launch { container.settings.setOffRouteThreshold(it) } },
                            contentPadding = PaddingValues(),
                        )
                    }
                }
                ListRow(
                    "Pantalla siempre encendida",
                    subtitle = "Mientras grabás un recorrido (consume más batería)",
                    icon = Icons.Rounded.LightMode,
                    trailing = { Switch(settings.keepScreenOn, { v -> scope.launch { container.settings.setKeepScreenOn(v) } }) },
                )
            }
            SectionTitle("Permisos")
            CumbreCard {
                val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
                ListRow(
                    "Ubicación",
                    subtitle = when {
                        fine -> "Precisa: concedida"
                        coarse -> "Solo aproximada: no alcanza para navegar"
                        else -> "No concedida. Se pide al usar el GPS."
                    },
                    icon = Icons.Rounded.LocationOn,
                    onClick = { context.openAppSettings() },
                )
                val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)
                ListRow(
                    "Notificaciones",
                    subtitle = if (notif) "Activadas (recorrido en curso y avisos de desvío)" else "Desactivadas: no verás el recorrido en curso ni los avisos",
                    icon = Icons.Rounded.Notifications,
                    onClick = { context.openAppSettings() },
                )
            }
            Text(
                "Cumbre usa la ubicación solo mientras ves el mapa o grabás un recorrido. No solicita ubicación en segundo plano permanente: la grabación usa un servicio visible con notificación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.m, bottom = Spacing.xl),
            )
        }
    }
}
