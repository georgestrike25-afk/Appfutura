package com.cumbre.core.maps

import com.cumbre.core.geo.GeoBounds
import org.mapsforge.map.reader.MapFile
import java.io.File

sealed interface MapInspection {
    data class Valid(
        val bounds: GeoBounds,
        val fileVersion: Int,
        val dataTimestampMillis: Long,
        val createdBy: String?,
        val comment: String?,
        val sizeBytes: Long,
        val languages: List<String>,
    ) : MapInspection

    data class Invalid(val reason: MapProblem, val detail: String?) : MapInspection
}

enum class MapProblem { NOT_FOUND, UNREADABLE, NOT_MAPSFORGE, EMPTY }

/** Valida un archivo de mapa Mapsforge (.map) leyendo su cabecera. */
object MapFileInspector {
    private const val MAGIC = "mapsforge binary OSM"

    fun inspect(file: File): MapInspection {
        if (!file.exists()) return MapInspection.Invalid(MapProblem.NOT_FOUND, file.name)
        if (!file.canRead()) return MapInspection.Invalid(MapProblem.UNREADABLE, file.name)
        if (file.length() == 0L) return MapInspection.Invalid(MapProblem.EMPTY, file.name)
        if (!hasMagic(file)) return MapInspection.Invalid(MapProblem.NOT_MAPSFORGE, "Cabecera desconocida")
        return try {
            val map = MapFile(file)
            try {
                val info = map.mapFileInfo
                val bb = info.boundingBox
                MapInspection.Valid(
                    bounds = GeoBounds(bb.minLatitude, bb.minLongitude, bb.maxLatitude, bb.maxLongitude),
                    fileVersion = info.fileVersion,
                    dataTimestampMillis = info.mapDate,
                    createdBy = info.createdBy,
                    comment = info.comment,
                    sizeBytes = info.fileSize,
                    languages = info.languagesPreference?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                )
            } finally {
                map.close()
            }
        } catch (e: Exception) {
            MapInspection.Invalid(MapProblem.NOT_MAPSFORGE, e.message)
        }
    }

    private fun hasMagic(file: File): Boolean = try {
        file.inputStream().use { input ->
            val buf = ByteArray(MAGIC.length)
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            read == buf.size && String(buf, Charsets.US_ASCII) == MAGIC
        }
    } catch (_: Exception) {
        false
    }
}
