package com.cumbre.core.analysis

import kotlin.math.abs

/**
 * Desnivel acumulado con histéresis: solo cuenta un ascenso o descenso cuando la altitud
 * se aleja más de [thresholdMeters] del último punto de referencia. Esto filtra el ruido
 * típico de GPS y barómetros sin perder subidas reales.
 */
class ElevationCalculator(private val thresholdMeters: Double = DEFAULT_THRESHOLD_M) {
    var gain: Double = 0.0
        private set
    var loss: Double = 0.0
        private set
    private var reference: Double? = null

    fun add(elevation: Double) {
        val ref = reference
        if (ref == null) {
            reference = elevation
            return
        }
        val diff = elevation - ref
        if (abs(diff) >= thresholdMeters) {
            if (diff > 0) gain += diff else loss += -diff
            reference = elevation
        }
    }

    /** Inicia un nuevo tramo (por ejemplo, un nuevo segmento) sin sumar la diferencia entre tramos. */
    fun breakSegment() {
        reference = null
    }

    companion object {
        /** Umbral adecuado para elevaciones de archivos GPX (modelos digitales o barómetro). */
        const val DEFAULT_THRESHOLD_M = 5.0

        /** Umbral para altitud GPS en vivo, más ruidosa. */
        const val GPS_THRESHOLD_M = 10.0

        fun compute(segments: List<List<Double?>>, thresholdMeters: Double = DEFAULT_THRESHOLD_M): Pair<Double, Double> {
            val calc = ElevationCalculator(thresholdMeters)
            for (seg in segments) {
                calc.breakSegment()
                for (e in seg) if (e != null) calc.add(e)
            }
            return calc.gain to calc.loss
        }
    }
}
