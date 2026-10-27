package com.cumbre.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Cálculos geodésicos. Todas las distancias se expresan en metros. */
object GeoMath {
    /** Radio medio terrestre (IUGG) en metros. */
    const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).let { it * it } +
            cos(phi1) * cos(phi2) * sin(dLambda / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double =
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    /** Rumbo inicial de [from] a [to] en grados (0 = norte, sentido horario). */
    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val phi1 = Math.toRadians(from.latitude)
        val phi2 = Math.toRadians(to.latitude)
        val dLambda = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Proyección del punto [p] sobre el segmento [a]-[b].
     *
     * Usa una proyección equirectangular local centrada en [p], suficientemente
     * precisa para tramos de track (decenas o cientos de metros).
     */
    fun projectOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val cosLat = cos(Math.toRadians(p.latitude))
        val k = Math.toRadians(1.0) * EARTH_RADIUS_M
        val ax = (a.longitude - p.longitude) * cosLat * k
        val ay = (a.latitude - p.latitude) * k
        val bx = (b.longitude - p.longitude) * cosLat * k
        val by = (b.latitude - p.latitude) * k
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        val closest = GeoPoint(
            latitude = a.latitude + t * (b.latitude - a.latitude),
            longitude = a.longitude + t * (b.longitude - a.longitude),
        )
        return SegmentProjection(fraction = t, distanceMeters = sqrt(cx * cx + cy * cy), closest = closest)
    }
}

data class SegmentProjection(
    /** Posición relativa sobre el segmento, de 0 (inicio) a 1 (fin). */
    val fraction: Double,
    /** Distancia perpendicular (o al extremo más cercano) en metros. */
    val distanceMeters: Double,
    val closest: GeoPoint,
)
