package com.cumbre.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cumbre.app.map.MapTheme
import com.cumbre.app.ui.LocalAppContainer
import com.cumbre.app.ui.LocalSettings
import java.io.File

/** Capas de fondo del mapa: archivos .map activos, estilo y carpeta de relieve (si está activado). */
data class MapLayers(val files: List<File>, val theme: MapTheme, val reliefDir: File?) {
    val hasMaps: Boolean get() = files.isNotEmpty()
}

@Composable
fun rememberMapLayers(): MapLayers {
    val container = LocalAppContainer.current
    val settings = LocalSettings.current
    val files by remember { container.maps.observeActiveMapFiles() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val relief by container.relief.state.collectAsStateWithLifecycle()
    return remember(files, settings.mapStyle, settings.showRelief, relief.tiles) {
        MapLayers(
            files = files,
            theme = settings.mapStyle.toMapTheme(),
            reliefDir = if (settings.showRelief && relief.installed) container.relief.directory else null,
        )
    }
}
