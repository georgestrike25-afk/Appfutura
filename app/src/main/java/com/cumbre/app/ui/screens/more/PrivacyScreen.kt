package com.cumbre.app.ui.screens.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.DangerButton
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivacyScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var includeMaps by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                val r = runCatching { container.dataPortability.exportAll(uri) }
                busy = false
                messenger(r.fold({ "Exportación lista: $it archivos GPX." }, { "No se pudo exportar: ${it.message}" }))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Privacidad y datos", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l)) {
            CumbreCard {
                Column(Modifier.padding(Spacing.l)) {
                    Text("Tus datos quedan en tu teléfono", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "• Cumbre no requiere cuenta ni registro.\n" +
                            "• No tiene permiso de acceso a Internet: no envía tus rutas, recorridos ni ubicación a ningún servidor.\n" +
                            "• Las rutas, actividades y mapas se guardan en el almacenamiento privado de la app.\n" +
                            "• Los datos no están cifrados por Cumbre; quedan protegidos por el bloqueo y el cifrado de tu teléfono.\n" +
                            "• La ubicación se usa solo con el mapa visible o mientras grabás un recorrido.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            SectionTitle("Exportar")
            Text("Guardá un archivo ZIP con todas tus rutas y actividades en formato GPX.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.m))
            PrimaryButton(
                if (busy) "Exportando…" else "Exportar todos mis datos",
                enabled = !busy,
                icon = Icons.Rounded.Archive,
                modifier = Modifier.fillMaxWidth(),
                onClick = { exporter.launch("cumbre-${SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())}.zip") },
            )
            SectionTitle("Eliminar")
            Text("Borra todas las rutas, actividades y preferencias del teléfono. No se puede deshacer.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.m))
            DangerButton("Eliminar mis datos", onClick = { confirm = true }, icon = Icons.Rounded.DeleteForever, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text("¿Eliminar todos tus datos?") },
            text = {
                Column {
                    Text("Se borrarán todas las rutas y actividades. Te recomendamos exportarlas antes.")
                    Row(Modifier.fillMaxWidth().clickable { includeMaps = !includeMaps }.padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeMaps, onCheckedChange = { includeMaps = it })
                        Text("Eliminar también los mapas sin conexión")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        if (container.trackingManager.state.value.isActive) container.trackingManager.discard()
                        container.dataPortability.deleteAll(includeMaps)
                        messenger("Se eliminaron tus datos.")
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
