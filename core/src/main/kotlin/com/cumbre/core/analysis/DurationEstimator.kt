package com.cumbre.core.analysis

import kotlin.math.max
import kotlin.math.min

/**
 * Estimación de tiempo de marcha según la norma DIN 33466 (usada por clubes alpinos):
 * 4 km/h en horizontal, 300 m/h de ascenso y 500 m/h de descenso; el tiempo total es el mayor
 * de los componentes más la mitad del menor. No incluye descansos.
 */
object DurationEstimator {
    const val HORIZONTAL_KMH = 4.0
    const val ASCENT_M_PER_H = 300.0
    const val DESCENT_M_PER_H = 500.0

    fun estimateSeconds(distanceMeters: Double, gainMeters: Double?, lossMeters: Double?): Long? {
        if (distanceMeters <= 0.0) return null
        val horizontalH = distanceMeters / 1000.0 / HORIZONTAL_KMH
        val verticalH = (gainMeters ?: 0.0) / ASCENT_M_PER_H + (lossMeters ?: 0.0) / DESCENT_M_PER_H
        val hours = max(horizontalH, verticalH) + min(horizontalH, verticalH) / 2.0
        return (hours * 3600.0).toLong()
    }
}
