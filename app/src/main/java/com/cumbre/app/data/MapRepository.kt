package com.cumbre.app.data

import android.net.Uri
import com.cumbre.app.data.db.OfflineMapDao
import com.cumbre.app.data.db.OfflineMapEntity
import com.cumbre.core.format.Formatters
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.maps.CoverageResult
import com.cumbre.core.maps.MapCoverage
import com.cumbre.core.maps.MapFileInspector
import com.cumbre.core.maps.MapInspection
import com.cumbre.core.maps.MapProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface MapImportResult {
    data class Success(val map: OfflineMapEntity) : MapImportResult
    data class Failed(val message: String) : MapImportResult
}

data class MapStorageInfo(val usedBytes: Long, val freeBytes: Long)

/** Mapas vectoriales Mapsforge (.map) instalados por el usuario. */
class MapRepository(
    private val dao: OfflineMapDao,
    private val files: FileStore,
) {
    fun observeMaps(): Flow<List<OfflineMapEntity>> = dao.observeAll()

    /** Archivos de mapa habilitados y sin problemas, listos para renderizar. */
    fun observeActiveMapFiles(): Flow<List<File>> = dao.observeAll().map { maps ->
        maps.filter { it.enabled && it.problem == null }.map { File(it.filePath) }
    }

    fun storageInfo(maps: List<OfflineMapEntity>): MapStorageInfo =
        MapStorageInfo(maps.sumOf { it.sizeBytes }, files.freeBytes(files.mapsDir))

    suspend fun importMap(uri: Uri, onProgress: (Long, Long?) -> Unit): MapImportResult = withContext(Dispatchers.IO) {
        val originalName = files.displayName(uri) ?: "mapa.map"
        val declared = files.declaredSize(uri)
        val free = files.freeBytes(files.mapsDir)
        if (declared != null && declared > free - RESERVED_FREE_BYTES) {
            return@withContext MapImportResult.Failed(
                "No hay espacio suficiente: el mapa ocupa ${Formatters.bytes(declared)} y quedan ${Formatters.bytes(free)} libres.",
            )
        }
        val base = files.safeFileName(originalName.substringBeforeLast('.'), "map")
        var target = File(files.mapsDir, base)
        var n = 2
        while (target.exists()) {
            target = File(files.mapsDir, base.removeSuffix(".map") + "-$n.map")
            n++
        }
        val partial = File(target.parentFile, target.name + ".parcial")
        try {
            files.copyFromUri(uri, partial, MAX_MAP_BYTES) { copied -> onProgress(copied, declared) }
        } catch (e: FileTooLargeException) {
            partial.delete()
            return@withContext MapImportResult.Failed("El archivo es demasiado grande.")
        } catch (e: SecurityException) {
            partial.delete()
            return@withContext MapImportResult.Failed("Cumbre no tiene permiso para leer ese archivo.")
        } catch (e: IOException) {
            partial.delete()
            return@withContext MapImportResult.Failed("No se pudo copiar el mapa: ${e.message ?: "error de lectura"}.")
        }
        when (val inspection = MapFileInspector.inspect(partial)) {
            is MapInspection.Invalid -> {
                partial.delete()
                MapImportResult.Failed("«$originalName» no se pudo abrir. ${problemText(inspection.reason)}")
            }
            is MapInspection.Valid -> {
                if (!partial.renameTo(target)) {
                    partial.delete()
                    return@withContext MapImportResult.Failed("No se pudo guardar el mapa en el almacenamiento de la app.")
                }
                val entity = OfflineMapEntity(
                    displayName = prettyName(originalName),
                    originalFileName = originalName,
                    filePath = target.absolutePath,
                    sizeBytes = target.length(),
                    minLat = inspection.bounds.minLatitude,
                    minLon = inspection.bounds.minLongitude,
                    maxLat = inspection.bounds.maxLatitude,
                    maxLon = inspection.bounds.maxLongitude,
                    fileVersion = inspection.fileVersion,
                    dataTimestamp = inspection.dataTimestampMillis,
                    createdBy = inspection.createdBy,
                    importedAt = System.currentTimeMillis(),
                )
                val id = dao.insert(entity)
                MapImportResult.Success(entity.copy(id = id))
            }
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(map: OfflineMapEntity) = withContext(Dispatchers.IO) {
        File(map.filePath).delete()
        dao.delete(map.id)
    }

    /** Comprueba que los mapas registrados sigan existiendo y sean legibles. */
    suspend fun verifyAll() = withContext(Dispatchers.IO) {
        for (map in dao.getAll()) {
            val problem = when (val r = MapFileInspector.inspect(File(map.filePath))) {
                is MapInspection.Valid -> null
                is MapInspection.Invalid -> problemText(r.reason)
            }
            if (problem != map.problem) dao.setProblem(map.id, problem)
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        for (map in dao.getAll()) delete(map)
    }

    fun coverage(routePoints: List<GeoPoint>, maps: List<OfflineMapEntity>): Pair<CoverageResult, List<OfflineMapEntity>> {
        val usable = maps.filter { it.enabled && it.problem == null }
        val result = MapCoverage.evaluate(routePoints, usable.map { it.bounds })
        return result to result.coveringMaps.map { usable[it] }
    }

    private fun prettyName(fileName: String): String =
        fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
            .split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
            .ifBlank { fileName }

    companion object {
        const val MAX_MAP_BYTES = 8L * 1024 * 1024 * 1024
        private const val RESERVED_FREE_BYTES = 100L * 1024 * 1024

        fun problemText(problem: MapProblem): String = when (problem) {
            MapProblem.NOT_FOUND -> "El archivo ya no está disponible en el teléfono."
            MapProblem.UNREADABLE -> "El archivo no se puede leer."
            MapProblem.EMPTY -> "El archivo está vacío."
            MapProblem.NOT_MAPSFORGE -> "No es un mapa Mapsforge (.map) válido o está dañado."
        }
    }
}
