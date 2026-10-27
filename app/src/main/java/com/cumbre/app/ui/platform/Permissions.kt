package com.cumbre.app.ui.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cumbre.app.data.SettingsRepository
import com.cumbre.app.ui.components.ConfirmDialog
import com.cumbre.core.permissions.LocationPermissionPolicy
import com.cumbre.core.permissions.PermissionAction
import com.cumbre.core.permissions.PermissionSnapshot
import kotlinx.coroutines.launch

enum class LocationPurpose { SHOW_POSITION, NAVIGATE }

@Stable
class LocationPermissionController internal constructor(
    private val requestImpl: (LocationPurpose, () -> Unit) -> Unit,
    private val isGrantedImpl: () -> Boolean,
    private val isFineImpl: () -> Boolean,
) {
    /** Ejecuta [onGranted] cuando el permiso adecuado esté concedido, explicando antes para qué se usa. */
    fun request(purpose: LocationPurpose, onGranted: () -> Unit) = requestImpl(purpose, onGranted)
    val hasAnyPermission: Boolean get() = isGrantedImpl()
    val hasFinePermission: Boolean get() = isFineImpl()
}

private enum class DialogKind { EXPLAIN, RATIONALE, SETTINGS, NEED_PRECISE }

/**
 * Solicitud progresiva de ubicación: nunca se pide al abrir la app, sino cuando el usuario usa
 * una función que la necesita, y siempre con una explicación previa.
 */
@Composable
fun rememberLocationPermission(settings: SettingsRepository): LocationPermissionController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var pending by remember { mutableStateOf<Pair<LocationPurpose, () -> Unit>?>(null) }

    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    fun fine() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun coarse() = granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { settings.setNotificationPermissionRequested() }
    }

    fun maybeAskNotifications(purpose: LocationPurpose) {
        if (purpose != LocationPurpose.NAVIGATE || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (granted(Manifest.permission.POST_NOTIFICATIONS)) return
        scope.launch {
            if (!settings.current().notificationPermissionRequested) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        scope.launch { settings.setLocationPermissionRequested() }
        val p = pending ?: return@rememberLauncherForActivityResult
        val fineOk = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || fine()
        val coarseOk = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || coarse()
        when {
            fineOk || (coarseOk && p.first == LocationPurpose.SHOW_POSITION) -> {
                pending = null
                maybeAskNotifications(p.first)
                p.second()
            }
            coarseOk -> dialog = DialogKind.NEED_PRECISE
            else -> pending = null
        }
    }

    fun launch() = launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    val controller = remember {
        LocationPermissionController(
            requestImpl = { purpose, onGranted ->
                scope.launch {
                    val activity = context.findActivity()
                    val snapshot = PermissionSnapshot(
                        fineGranted = fine(),
                        coarseGranted = coarse(),
                        shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == true,
                        requestedBefore = settings.current().locationPermissionRequested,
                    )
                    val action = LocationPermissionPolicy.decide(snapshot)
                    pending = purpose to onGranted
                    when {
                        action == PermissionAction.READY ||
                            (action == PermissionAction.APPROXIMATE_ONLY && purpose == LocationPurpose.SHOW_POSITION) -> {
                            pending = null
                            maybeAskNotifications(purpose)
                            onGranted()
                        }
                        action == PermissionAction.APPROXIMATE_ONLY -> dialog = DialogKind.NEED_PRECISE
                        action == PermissionAction.EXPLAIN_THEN_REQUEST -> dialog = DialogKind.EXPLAIN
                        action == PermissionAction.SHOW_RATIONALE -> dialog = DialogKind.RATIONALE
                        else -> dialog = DialogKind.SETTINGS
                    }
                }
            },
            isGrantedImpl = { fine() || coarse() },
            isFineImpl = { fine() },
        )
    }

    when (dialog) {
        DialogKind.EXPLAIN, DialogKind.RATIONALE -> ConfirmDialog(
            title = "Usar tu ubicación",
            text = (if (pending?.first == LocationPurpose.NAVIGATE) {
                "Para seguir la ruta y grabar tu recorrido, Cumbre necesita la ubicación precisa del GPS. "
            } else {
                "Para mostrar dónde estás en el mapa, Cumbre necesita acceder a tu ubicación. "
            }) + "Funciona sin Internet y tus recorridos no se envían a ningún servidor." +
                (if (dialog == DialogKind.RATIONALE) "\n\nAntes rechazaste el permiso; podés concederlo ahora." else ""),
            confirmLabel = "Continuar",
            icon = Icons.Rounded.LocationOn,
            onConfirm = { dialog = null; launch() },
            onDismiss = { dialog = null; pending = null },
        )
        DialogKind.NEED_PRECISE -> ConfirmDialog(
            title = "Se necesita ubicación precisa",
            text = "Con la ubicación aproximada (unos 2 km de error) no es posible seguir un sendero. " +
                "Elegí «Ubicación precisa» cuando Android lo pregunte.",
            confirmLabel = "Pedir ubicación precisa",
            icon = Icons.Rounded.LocationOn,
            onConfirm = { dialog = null; launch() },
            onDismiss = { dialog = null; pending = null },
        )
        DialogKind.SETTINGS -> ConfirmDialog(
            title = "Permiso de ubicación desactivado",
            text = "Rechazaste el permiso de ubicación. Para usar el GPS, activalo en Ajustes › Permisos › Ubicación.",
            confirmLabel = "Abrir Ajustes",
            icon = Icons.Rounded.LocationOn,
            onConfirm = { dialog = null; pending = null; context.openAppSettings() },
            onDismiss = { dialog = null; pending = null },
        )
        null -> Unit
    }
    return controller
}
