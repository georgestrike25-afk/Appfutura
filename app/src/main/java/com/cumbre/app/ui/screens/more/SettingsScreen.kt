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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.cumbre.app.ui.components.SegmentOption
import com.cumbre.app.ui.components.SegmentedChoice
import com.cumbre.app.ui.components.Tag
import com.cumbre.app.ui.components.TagStyle
import com.cumbre.app.ui.platform.isIgnoringBatteryOptimizations
import com.cumbre.app.ui.platform.oemBatteryTip
import com.cumbre.app.ui.platform.requestIgnoreBatteryOptimizations
import com.cumbre.app.ui.platform.openAppSettings
import com.cumbre.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Se vuelve a leer el estado de permisos y batería al regresar de los ajustes del sistema.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    fun granted(p: String) = resumes >= 0 && context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Ajustes", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l)) {
            SectionTitle("Apariencia", Modifier.padding(top = 0.dp))
            SegmentedChoice(
                listOf(
                    SegmentOption(ThemeMode.SYSTEM, "Sistema", Icons.Rounded.BrightnessAuto),
                    SegmentOption(ThemeMode.LIGHT, "Claro", Icons.Rounded.LightMode),
                    SegmentOption(ThemeMode.DARK, "Oscuro", Icons.Rounded.DarkMode),
                ),
                settings.themeMode,
                { scope.launch { container.settings.setThemeMode(it) } },
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
            SectionTitle("Grabación con la pantalla apagada")
            CumbreCard {
                val unrestricted = remember(resumes) { context.isIgnoringBatteryOptimizations() }
                ListRow(
                    "Batería sin restricciones",
                    subtitle = if (unrestricted) {
                        "Activado: Android no cerrará Cumbre mientras grabás"
                    } else {
                        "Recomendado: el ahorro de batería puede cortar la grabación"
                    },
                    icon = Icons.Rounded.BatteryChargingFull,
                    onClick = { context.requestIgnoreBatteryOptimizations() },
                    trailing = { StatusTag(unrestricted) },
                )
                val tip = remember { oemBatteryTip() }
                if (tip != null) {
                    Text(
                        "En ${tip.brand}: ${tip.steps}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.m),
                    )
                    ListRow("Abrir ajustes de la app", icon = Icons.Rounded.Settings, onClick = { context.openAppSettings() })
                }
            }
            Text(
                "La grabación sigue aunque apagues la pantalla, cambies de app o quites Cumbre de recientes, hasta que toques «Finalizar». Podés pausar y reanudar desde la notificación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
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
                    trailing = { StatusTag(fine) },
                )
                val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)
                ListRow(
                    "Notificaciones",
                    subtitle = if (notif) "Activadas (recorrido en curso y avisos de desvío)" else "Desactivadas: no verás el recorrido en curso ni los avisos",
                    icon = Icons.Rounded.Notifications,
                    onClick = { context.openAppSettings() },
                    trailing = { StatusTag(notif) },
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

@Composable
private fun StatusTag(ok: Boolean) {
    Tag(if (ok) "Activo" else "Revisar", icon = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, style = if (ok) TagStyle.POSITIVE else TagStyle.WARNING)
}
