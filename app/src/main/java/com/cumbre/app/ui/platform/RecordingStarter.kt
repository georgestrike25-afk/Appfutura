package com.cumbre.app.ui.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.LocalSettings
import com.cumbre.app.ui.navigation.Dest
import kotlinx.coroutines.launch

/**
 * Inicia una actividad (libre con `null` o siguiendo una ruta) con el mismo blindaje en ambos
 * casos: permiso de ubicación precisa y, la primera vez, la guía para que Android no cierre
 * Cumbre por ahorro de batería con la pantalla apagada.
 */
@Composable
fun rememberRecordingStarter(nav: NavController): (routeId: Long?) -> Unit {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = rememberLocationPermission(container.settings)
    var pendingRoute by remember { mutableStateOf<Long?>(null) }
    var askBattery by remember { mutableStateOf(false) }

    fun launch(routeId: Long?) {
        scope.launch {
            runCatching { container.trackingManager.start(routeId) }
                .onSuccess { nav.navigate(Dest.TRACKING) }
                .onFailure { messenger("No se pudo iniciar: ${it.message}") }
        }
    }

    if (askBattery) {
        val tip = remember { oemBatteryTip() }
        AlertDialog(
            onDismissRequest = { askBattery = false },
            icon = { Icon(Icons.Rounded.BatteryAlert, contentDescription = null) },
            title = { Text("Que nada corte tu grabación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Cumbre graba con la pantalla apagada hasta que vos la finalices. Para que el ahorro de batería del teléfono no la cierre, permití que Cumbre funcione sin restricciones.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    tip?.let {
                        Text("En ${it.brand}: ${it.steps}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askBattery = false
                    scope.launch { container.settings.setBatteryGuideSeen() }
                    context.requestIgnoreBatteryOptimizations()
                    launch(pendingRoute)
                }) { Text("Permitir y empezar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askBattery = false
                    scope.launch { container.settings.setBatteryGuideSeen() }
                    launch(pendingRoute)
                }) { Text("Empezar igual") }
            },
        )
    }

    return { routeId ->
        permission.request(LocationPurpose.NAVIGATE) {
            if (!context.isIgnoringBatteryOptimizations() && !settings.batteryGuideSeen) {
                pendingRoute = routeId
                askBattery = true
            } else {
                launch(routeId)
            }
        }
    }
}
