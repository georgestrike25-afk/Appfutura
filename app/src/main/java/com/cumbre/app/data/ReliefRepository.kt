package com.cumbre.app.data

import android.net.Uri
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.maps.DemFiles
import com.cumbre.core.maps.DemTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class ReliefState(val tiles: List<DemTile> = emptyList(), val sizeBytes: Long = 0) {
    val installed: Boolean get() = tiles.isNotEmpty()
}

sealed interface ReliefImportResult {
    data class Success(val added: List<DemTile>, val skipped: Int) : ReliefImportResult
    data class Failed(val message: String) : ReliefImportResult
}

/**
 * Archivos de elevación SRTM (.hgt) que Mapsforge usa para sombrear el relieve sin conexión.
 * Se aceptan .hgt sueltos o ZIP que los contengan.
 */
class ReliefRepository(private val files: FileStore) {
    private val _state = MutableStateFlow(ReliefState())
    val state: StateFlow<ReliefState> = _state.asStateFlow()

    val directory: File get() = files.demDir

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { refresh() }
    }

    private fun refresh() {
        val dir = files.demDir
        val tiles = DemFiles.installed(dir)
        _state.value = ReliefState(tiles, tiles.sumOf { File(dir, it.fileName).length() })
    }

    suspend fun import(uri: Uri): ReliefImportResult = withContext(Dispatchers.IO) {
        val name = files.displayName(uri) ?: ""
        val dir = files.demDir
        try {
            val result = when {
                name.endsWith(".zip", true) -> {
                    val input = files.openInput(uri)
                    input.use { DemFiles.extractZip(it, dir) }.let { ReliefImportResult.Success(it.installed, it.skipped) }
                }
                name.endsWith(".hgt", true) -> {
                    val tile = DemTile.parse(name)
                        ?: return@withContext ReliefImportResult.Failed("«$name» no tiene un nombre de tesela SRTM válido (por ejemplo, S33W065.hgt).")
                    val target = File(dir, tile.fileName)
                    val partial = File(dir, tile.fileName + ".parcial")
                    val size = files.copyFromUri(uri, partial, DemFiles.VALID_SIZES.max())
                    if (!DemFiles.isValidHgtSize(size)) {
                        partial.delete()
                        return@withContext ReliefImportResult.Failed("«$name» no tiene el tamaño de un archivo SRTM (.hgt) válido.")
                    }
                    target.delete()
                    partial.renameTo(target)
                    ReliefImportResult.Success(listOf(tile), 0)
                }
                else -> ReliefImportResult.Failed("Elegí un archivo .hgt o un .zip con archivos .hgt.")
            }
            refresh()
            if (result is ReliefImportResult.Success && result.added.isEmpty()) {
                ReliefImportResult.Failed("El ZIP no contiene archivos de elevación .hgt válidos.")
            } else {
                result
            }
        } catch (e: FileTooLargeException) {
            ReliefImportResult.Failed("El archivo no tiene el tamaño de un archivo SRTM (.hgt) válido.")
        } catch (e: SecurityException) {
            ReliefImportResult.Failed("Cumbre no tiene permiso para leer ese archivo.")
        } catch (e: IOException) {
            ReliefImportResult.Failed("No se pudo leer el archivo: ${e.message ?: "error de lectura"}.")
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        files.demDir.listFiles()?.forEach { it.delete() }
        refresh()
    }

    fun missingFor(route: List<GeoPoint>): List<DemTile> = DemFiles.missingFor(route, _state.value.tiles)
}
