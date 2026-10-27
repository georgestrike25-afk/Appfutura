package com.cumbre.core.geo

/** Coordenada geográfica WGS84 en grados decimales. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    companion object {
        fun isValid(latitude: Double, longitude: Double): Boolean =
            latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}

/** Rectángulo geográfico mínimo que contiene un conjunto de puntos. */
data class GeoBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    val center: GeoPoint
        get() = GeoPoint((minLatitude + maxLatitude) / 2.0, (minLongitude + maxLongitude) / 2.0)

    fun contains(point: GeoPoint): Boolean =
        point.latitude in minLatitude..maxLatitude && point.longitude in minLongitude..maxLongitude

    fun contains(other: GeoBounds): Boolean =
        other.minLatitude >= minLatitude && other.maxLatitude <= maxLatitude &&
            other.minLongitude >= minLongitude && other.maxLongitude <= maxLongitude

    fun intersects(other: GeoBounds): Boolean =
        other.minLatitude <= maxLatitude && other.maxLatitude >= minLatitude &&
            other.minLongitude <= maxLongitude && other.maxLongitude >= minLongitude

    companion object {
        fun of(points: Iterable<GeoPoint>): GeoBounds? {
            var minLat = Double.POSITIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var any = false
            for (p in points) {
                any = true
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }
            return if (any) GeoBounds(minLat, minLon, maxLat, maxLon) else null
        }
    }
}
