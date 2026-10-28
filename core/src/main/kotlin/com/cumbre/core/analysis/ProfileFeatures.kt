package com.cumbre.core.analysis

/** Punto destacado del perfil (por ejemplo, la cumbre). */
data class ProfileHighlight(val distanceMeters: Double, val elevationMeters: Double)

object ProfileFeatures {
    /** Punto más alto del recorrido según el perfil. */
    fun highest(profile: List<ProfilePoint>): ProfileHighlight? =
        profile.maxByOrNull { it.elevationMeters }?.let { ProfileHighlight(it.distanceMeters, it.elevationMeters) }

    /** Punto más bajo del recorrido según el perfil. */
    fun lowest(profile: List<ProfilePoint>): ProfileHighlight? =
        profile.minByOrNull { it.elevationMeters }?.let { ProfileHighlight(it.distanceMeters, it.elevationMeters) }

    /** Altitud interpolada a una distancia del inicio. */
    fun elevationAt(profile: List<ProfilePoint>, distanceMeters: Double): Double? {
        if (profile.isEmpty()) return null
        if (distanceMeters <= profile.first().distanceMeters) return profile.first().elevationMeters
        if (distanceMeters >= profile.last().distanceMeters) return profile.last().elevationMeters
        val i = profile.indexOfFirst { it.distanceMeters >= distanceMeters }
        val a = profile[i - 1]
        val b = profile[i]
        val t = if (b.distanceMeters == a.distanceMeters) 0.0 else (distanceMeters - a.distanceMeters) / (b.distanceMeters - a.distanceMeters)
        return a.elevationMeters + t * (b.elevationMeters - a.elevationMeters)
    }
}
