package com.cumbre.core.recording

import com.cumbre.core.gpx.GpxDocument
import com.cumbre.core.gpx.GpxMetadata
import com.cumbre.core.gpx.GpxPoint
import com.cumbre.core.gpx.GpxTrack

/** Convierte una actividad grabada en un documento GPX, conservando los tramos (pausas). */
object RecordingExport {
    fun toGpx(name: String, startedAtMillis: Long, points: List<RecordedPoint>, description: String? = null): GpxDocument {
        val segments = points
            .sortedWith(compareBy({ it.segment }, { it.timeMillis }))
            .groupBy { it.segment }
            .toSortedMap()
            .values
            .map { seg -> seg.map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.timeMillis) } }
            .filter { it.isNotEmpty() }
        return GpxDocument(
            version = "1.1",
            creator = "Cumbre",
            metadata = GpxMetadata(name = name, description = description, timeMillis = startedAtMillis),
            tracks = listOf(GpxTrack(name = name, type = "hiking", segments = segments)),
        )
    }
}
