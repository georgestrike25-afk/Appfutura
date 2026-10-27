package com.cumbre.core.maps

import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoPoint

enum class CoverageLevel { FULL, PARTIAL, NONE }

data class CoverageResult(
    val level: CoverageLevel,
    /** Fracción de puntos del recorrido cubiertos por algún mapa instalado (0..1). */
    val fraction: Double,
    /** Índices de los mapas que cubren al menos un punto. */
    val coveringMaps: List<Int>,
)

/** Comprueba si el área de un recorrido está cubierta por los mapas instalados. */
object MapCoverage {
    fun evaluate(routePoints: List<GeoPoint>, maps: List<GeoBounds>): CoverageResult {
        if (routePoints.isEmpty() || maps.isEmpty()) return CoverageResult(CoverageLevel.NONE, 0.0, emptyList())
        val used = sortedSetOf<Int>()
        var covered = 0
        for (p in routePoints) {
            val idx = maps.indexOfFirst { it.contains(p) }
            if (idx >= 0) {
                covered++
                used.add(idx)
            }
        }
        val fraction = covered.toDouble() / routePoints.size
        val level = when {
            covered == routePoints.size -> CoverageLevel.FULL
            covered == 0 -> CoverageLevel.NONE
            else -> CoverageLevel.PARTIAL
        }
        return CoverageResult(level, fraction, used.toList())
    }
}
