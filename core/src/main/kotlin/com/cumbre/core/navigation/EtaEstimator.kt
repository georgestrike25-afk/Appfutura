package com.cumbre.core.navigation

import com.cumbre.core.analysis.DurationEstimator
import com.cumbre.core.analysis.ElevationCalculator
import com.cumbre.core.analysis.ProfilePoint

/** Desnivel pendiente entre dos puntos del recorrido, según el perfil de la ruta. */
data class RemainingClimb(val gainMeters: Double, val lossMeters: Double)

/**
 * Estimación del tiempo que falta para llegar.
 *
 * - Al comenzar, usa la norma DIN 33466 con la distancia y el desnivel pendientes.
 * - Cuando ya hay suficiente actividad en movimiento, se ajusta al ritmo real del usuario:
 *   compara cuánto tardó realmente con lo que DIN preveía para el tramo recorrido y aplica
 *   ese factor al tramo que falta (así una persona rápida o lenta obtiene un tiempo realista
 *   también en las subidas).
 */
object EtaEstimator {
    /** Mínimos para confiar en el ritmo propio. */
    const val MIN_MOVING_MILLIS = 10 * 60_000L
    const val MIN_DONE_METERS = 800.0

    fun remainingClimb(profile: List<ProfilePoint>, fromMeters: Double, toMeters: Double = Double.MAX_VALUE): RemainingClimb? {
        if (profile.size < 2) return null
        val calc = ElevationCalculator()
        var any = false
        for (p in profile) {
            if (p.distanceMeters < fromMeters || p.distanceMeters > toMeters) continue
            calc.add(p.elevationMeters)
            any = true
        }
        return if (any) RemainingClimb(calc.gain, calc.loss) else RemainingClimb(0.0, 0.0)
    }

    /**
     * @param remainingMeters distancia pendiente a lo largo del track.
     * @param remaining desnivel pendiente (nulo si el GPX no tiene elevaciones).
     * @param doneMeters distancia ya recorrida en la actividad.
     * @param done desnivel ya realizado según el perfil (para calibrar el ritmo).
     * @param movingMillis tiempo en movimiento de la actividad.
     */
    fun estimateSeconds(
        remainingMeters: Double,
        remaining: RemainingClimb?,
        doneMeters: Double = 0.0,
        done: RemainingClimb? = null,
        movingMillis: Long = 0,
    ): Long? {
        if (remainingMeters <= 0.0) return 0
        val dinRemaining = DurationEstimator.estimateSeconds(remainingMeters, remaining?.gainMeters, remaining?.lossMeters) ?: return null
        if (movingMillis < MIN_MOVING_MILLIS || doneMeters < MIN_DONE_METERS) return dinRemaining
        val dinDone = DurationEstimator.estimateSeconds(doneMeters, done?.gainMeters, done?.lossMeters) ?: return dinRemaining
        if (dinDone <= 0) return dinRemaining
        // Factor de ritmo personal, acotado para evitar valores absurdos tras paradas o tramos en vehículo.
        val factor = ((movingMillis / 1000.0) / dinDone).coerceIn(0.4, 2.5)
        return (dinRemaining * factor).toLong()
    }
}
