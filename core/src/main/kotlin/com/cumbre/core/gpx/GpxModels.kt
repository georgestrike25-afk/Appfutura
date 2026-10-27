package com.cumbre.core.gpx

import com.cumbre.core.geo.GeoPoint

/** Punto de track o de ruta. [timeMillis] es epoch UTC en milisegundos. */
data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timeMillis: Long? = null,
) {
    val geo: GeoPoint get() = GeoPoint(latitude, longitude)
}

data class GpxWaypoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timeMillis: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val symbol: String? = null,
    val type: String? = null,
) {
    val geo: GeoPoint get() = GeoPoint(latitude, longitude)
}

/** Un track conserva sus segmentos tal como venían en el archivo original. */
data class GpxTrack(
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
    val segments: List<List<GpxPoint>> = emptyList(),
)

data class GpxRoute(
    val name: String? = null,
    val description: String? = null,
    val points: List<GpxPoint> = emptyList(),
)

data class GpxLink(val href: String, val text: String? = null)

data class GpxMetadata(
    val name: String? = null,
    val description: String? = null,
    val author: String? = null,
    val links: List<GpxLink> = emptyList(),
    val timeMillis: Long? = null,
    val keywords: String? = null,
)

data class GpxDocument(
    val version: String? = null,
    val creator: String? = null,
    val metadata: GpxMetadata = GpxMetadata(),
    val waypoints: List<GpxWaypoint> = emptyList(),
    val tracks: List<GpxTrack> = emptyList(),
    val routes: List<GpxRoute> = emptyList(),
) {
    /**
     * Líneas recorribles del documento: cada segmento de track es una línea independiente.
     * Si el archivo no tiene tracks, se usan las rutas (`rte`).
     */
    fun lines(): List<List<GpxPoint>> {
        val trackLines = tracks.flatMap { it.segments }.filter { it.isNotEmpty() }
        if (trackLines.isNotEmpty()) return trackLines
        return routes.map { it.points }.filter { it.isNotEmpty() }
    }

    /** Nombre más representativo disponible en el archivo. */
    fun displayName(): String? =
        metadata.name?.takeIf { it.isNotBlank() }
            ?: tracks.firstNotNullOfOrNull { it.name?.takeIf { n -> n.isNotBlank() } }
            ?: routes.firstNotNullOfOrNull { it.name?.takeIf { n -> n.isNotBlank() } }

    fun displayDescription(): String? =
        metadata.description?.takeIf { it.isNotBlank() }
            ?: tracks.firstNotNullOfOrNull { it.description?.takeIf { d -> d.isNotBlank() } }
            ?: routes.firstNotNullOfOrNull { it.description?.takeIf { d -> d.isNotBlank() } }

    val totalPointCount: Int
        get() = tracks.sumOf { t -> t.segments.sumOf { it.size } } + routes.sumOf { it.points.size }
}

/** Información sobre datos descartados o corregidos durante la lectura. */
data class GpxParseReport(
    val invalidCoordinates: Int = 0,
    val emptySegments: Int = 0,
    val invalidElevations: Int = 0,
    val invalidTimes: Int = 0,
)

enum class GpxErrorType {
    EMPTY_FILE,
    MALFORMED_XML,
    NOT_GPX,
    UNSAFE_CONTENT,
    NO_GEOMETRY,
    TOO_LARGE,
    READ_ERROR,
}

class GpxException(val type: GpxErrorType, message: String, cause: Throwable? = null) :
    Exception(message, cause)

sealed interface GpxParseResult {
    data class Success(val document: GpxDocument, val report: GpxParseReport) : GpxParseResult
    data class Failure(val type: GpxErrorType, val message: String) : GpxParseResult
}
