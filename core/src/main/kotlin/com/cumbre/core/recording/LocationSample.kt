package com.cumbre.core.recording

import com.cumbre.core.geo.GeoPoint

/** Lectura de ubicación independiente de Android. */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Double? = null,
    val speedMps: Double? = null,
    val bearingDegrees: Double? = null,
) {
    val geo: GeoPoint get() = GeoPoint(latitude, longitude)
}
