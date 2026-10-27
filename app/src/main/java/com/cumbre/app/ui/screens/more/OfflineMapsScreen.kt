package com.cumbre.app.ui.screens.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.cumbre.app.data.MapImportResult
import com.cumbre.app.data.MapRepository
import com.cumbre.app.data.db.OfflineMapEntity
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalMessenger
import com.cumbre.app.ui.components.ConfirmDialog
import com.cumbre.app.ui.components.CumbreCard
import com.cumbre.app.ui.components.CumbreTopBar
import com.cumbre.app.ui.components.IconBadge
import com.cumbre.app.ui.components.MessageBanner
import com.cumbre.app.ui.components.MessageKind
import com.cumbre.app.ui.components.PrimaryButton
import com.cumbre.app.ui.components.QuietButton
import com.cumbre.app.ui.components.SectionTitle
import com.cumbre.app.ui.components.Tag
import com.cumbre.app.ui.components.TagStyle
import com.cumbre.app.ui.cumbreViewModel
import com.cumbre.app.ui.platform.openUrl
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.format.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MapImportProgress(val copiedBytes: Long, val totalBytes: Long?)

class OfflineMapsViewModel(private val repo: MapRepository) : ViewModel() {
    val maps: StateFlow<List<OfflineMapEntity>?> = repo.observeMaps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val progress = MutableStateFlow<MapImportProgress?>(null)
    val error = MutableStateFlow<String?>(null)

    fun importMap(uri: Uri, onDone: (String) -> Unit) {
        if (progress.value != null) return
        error.value = null
        progress.value = MapImportProgress(0, null)
        viewModelScope.launch {
            val result = repo.importMap(uri) { copied, total -> progress.value = MapImportProgress(copied, total) }
            progress.value = null
            when (result) {
                is MapImportResult.Success -> onDone("Mapa «${result.map.displayName}» instalado.")
                is MapImportResult.Failed -> error.value = result.message
            }
        }
    }

    fun setEnabled(map: OfflineMapEntity, enabled: Boolean) = viewModelScope.launch { repo.setEnabled(map.id, enabled) }
    fun delete(map: OfflineMapEntity) = viewModelScope.launch { repo.delete(map) }
    fun storage(list: List<OfflineMapEntity>) = repo.storageInfo(list)
}

@Composable
fun OfflineMapsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val vm = cumbreViewModel { OfflineMapsViewModel(container.maps) }
    val maps by vm.maps.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<OfflineMapEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importMap(uri, messenger)
    }

    Column(Modifier.fillMaxSize()) {
        CumbreTopBar(title = "Mapas sin conexión", onBack = { nav.popBackStack() })
        LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.weight(1f)) {
            val list = maps ?: emptyList()
            item {
                val info = vm.storage(list)
                CumbreCard {
                    Column(Modifier.padding(Spacing.l)) {
                        Row {
                            Text("Espacio usado por mapas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(Formatters.bytes(info.usedBytes), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(Spacing.s))
                        val total = (info.usedBytes + info.freeBytes).coerceAtLeast(1)
                        LinearProgressIndicator(
                            progress = { (info.usedBytes.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text("${Formatters.bytes(info.freeBytes)} libres en el teléfono", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            progress?.let { p ->
                item {
                    CumbreCard {
                        Column(Modifier.padding(Spacing.l)) {
                            Text("Importando mapa…", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(Spacing.s))
                            val total = p.totalBytes
                            if (total != null && total > 0) {
                                LinearProgressIndicator(progress = { (p.copiedBytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${Formatters.bytes(p.copiedBytes)} de ${Formatters.bytes(total)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(Formatters.bytes(p.copiedBytes), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            error?.let { item { MessageBanner(it, MessageKind.ERROR, title = "No se pudo instalar el mapa") } }
            item { SectionTitle(if (list.isEmpty()) "No hay mapas instalados" else "Instalados") }
            items(list, key = { it.id }) { map -> MapRow(map, onToggle = { vm.setEnabled(map, it) }, onDelete = { toDelete = map }) }
            item {
                SectionTitle("¿De dónde saco mapas?")
                CumbreCard {
                    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(
                            "Cumbre usa mapas vectoriales Mapsforge (.map), hechos con datos de OpenStreetMap. " +
                                "Descargá el archivo de tu provincia o región desde el navegador (con Wi-Fi) y después importalo acá.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Fuentes gratuitas: el servidor oficial de Mapsforge (download.mapsforge.org) y OpenAndroMaps (mapas para senderismo, descomprimí el ZIP antes de importar).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        QuietButton("Mapas de Argentina (Mapsforge)", onClick = { context.openUrl("https://download.mapsforge.org/maps/v5/south-america/") }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                        QuietButton("OpenAndroMaps", onClick = { context.openUrl("https://www.openandromaps.org/en/downloads/south-america") }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                        Text("Datos de mapa © colaboradores de OpenStreetMap (ODbL).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        PrimaryButton(
            "Importar archivo .map",
            onClick = { picker.launch(arrayOf("*/*")) },
            icon = Icons.Rounded.Add,
            enabled = progress == null,
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
        )
    }

    toDelete?.let { map ->
        ConfirmDialog(
            title = "¿Eliminar «${map.displayName}»?",
            text = "Se liberarán ${Formatters.bytes(map.sizeBytes)}. Las rutas que lo usaban se verán sin fondo de mapa hasta que instales otro.",
            confirmLabel = "Eliminar",
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = { vm.delete(map); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun MapRow(map: OfflineMapEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    CumbreCard {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                IconBadge(Icons.Rounded.Map, CumbreTheme.extra.successContainer, CumbreTheme.extra.onSuccessContainer)
                Column(Modifier.weight(1f)) {
                    Text(map.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${map.originalFileName} · ${Formatters.bytes(map.sizeBytes)} · v${map.fileVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (map.dataTimestamp > 0) {
                        Text(
                            "Datos del ${SimpleDateFormat("d/M/yyyy", Locale.ROOT).format(Date(map.dataTimestamp))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = map.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Eliminar mapa", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            map.problem?.let {
                Spacer(Modifier.height(Spacing.s))
                MessageBanner(it, MessageKind.ERROR)
            } ?: run {
                if (!map.enabled) {
                    Spacer(Modifier.height(Spacing.s))
                    Tag("Desactivado: no se usa al dibujar", style = TagStyle.NEUTRAL)
                }
            }
        }
    }
}
