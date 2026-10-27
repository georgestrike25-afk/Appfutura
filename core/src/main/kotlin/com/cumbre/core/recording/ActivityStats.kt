package com.cumbre.core.recording

import com.cumbre.core.analysis.ElevationCalculator
import com.cumbre.core.geo.GeoMath

data class ActivityStatsSnapshot(
    val distanceMeters: Double,
    /** Tiempo activo (sin pausas). */
    val elapsedMillis: Long,
    val movingMillis: Long,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val maxSpeedMps: Double,
    val pointCount: Int,
) {
    val averageSpeedMps: Double
        get() = if (elapsedMillis > 0) distanceMeters / (elapsedMillis / 1000.0) else 0.0

    val movingSpeedMps: Double
        get() = if (movingMillis > 0) distanceMeters / (movingMillis / 1000.0) else 0.0
}

/** Punto grabado, tal como se persiste. */
data class RecordedPoint(
    val segment: Int,
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val altitudeMeters: Double?,
    val accuracyMeters: Double?,
    val speedMps: Double?,
)

/**
 * Acumula las estadísticas de una actividad a medida que llegan puntos aceptados.
 * Puede reconstruirse a partir de los puntos persistidos tras un cierre inesperado.
 */
class ActivityStatsAccumulator {
    private var distance = 0.0
    private var movingMillis = 0L
    private var maxSpeed = 0.0
    private var count = 0
    private var lastPoint: RecordedPoint? = null
    private val elevation = ElevationCalculator(ElevationCalculator.GPS_THRESHOLD_M)

    fun add(point: RecordedPoint, distanceFromPrevious: Double? = null) {
        count++
        val prev = lastPoint
        if (prev != null && prev.segment == point.segment) {
            val d = distanceFromPrevious
                ?: GeoMath.distanceMeters(prev.latitude, prev.longitude, point.latitude, point.longitude)
            val dt = point.timeMillis - prev.timeMillis
            distance += d
            if (dt > 0) {
                val speed = d / (dt / 1000.0)
                if (speed >= MOVING_SPEED_MPS) movingMillis += dt
            }
        } else {
            elevation.breakSegment()
        }
        point.speedMps?.let { if (it > maxSpeed && it < 50.0) maxSpeed = it }
        point.altitudeMeters?.let { elevation.add(it) }
        lastPoint = point
    }

    fun snapshot(elapsedMillis: Long) = ActivityStatsSnapshot(
        distanceMeters = distance,
        elapsedMillis = elapsedMillis,
        movingMillis = movingMillis,
        elevationGainMeters = elevation.gain,
        elevationLossMeters = elevation.loss,
        maxSpeedMps = maxSpeed,
        pointCount = count,
    )

    companion object {
        const val MOVING_SPEED_MPS = 0.3

        fun rebuild(points: List<RecordedPoint>): ActivityStatsAccumulator {
            val acc = ActivityStatsAccumulator()
            points.sortedWith(compareBy({ it.segment }, { it.timeMillis })).forEach { acc.add(it) }
            return acc
        }
    }
}
