package com.cumbre.core.recording

import com.cumbre.core.geo.GeoMath
import com.cumbre.core.geo.GeoPoint

sealed interface FilterDecision {
    /** El punto se guarda. [distanceMeters] es la distancia desde el último punto aceptado del tramo. */
    data class Accept(val sample: LocationSample, val distanceMeters: Double) : FilterDecision

    data class Reject(val sample: LocationSample, val reason: RejectReason) : FilterDecision
}

enum class RejectReason {
    INVALID_COORDINATES,
    POOR_ACCURACY,
    OUT_OF_ORDER,
    /** Salto imposible a pie (posición errónea). */
    IMPLAUSIBLE_SPEED,
    /** Movimiento menor que el error del GPS: se ignora para no inflar la distancia. */
    JITTER,
}

/**
 * Filtro de ruido GPS para la grabación de actividades. Descarta lecturas imprecisas,
 * duplicadas o imposibles, pero no oculta desplazamientos reales: un movimiento sostenido
 * siempre termina superando el umbral de "jitter".
 */
class LocationFilter(
    var maxAccuracyMeters: Double = 35.0,
    private val minDistanceMeters: Double = 3.0,
    private val maxSpeedMps: Double = 50.0,
    /** Cada cuánto se guarda un punto aunque no haya movimiento (para conservar la traza temporal). */
    private val keepAliveMillis: Long = 60_000,
) {
    private var last: LocationSample? = null

    fun reset(lastAccepted: LocationSample? = null) {
        last = lastAccepted
    }

    /** Comienza un tramo nuevo (después de una pausa): no se suma distancia entre tramos. */
    fun breakSegment() {
        last = null
    }

    fun process(sample: LocationSample): FilterDecision {
        if (!GeoPoint.isValid(sample.latitude, sample.longitude)) {
            return FilterDecision.Reject(sample, RejectReason.INVALID_COORDINATES)
        }
        val acc = sample.accuracyMeters
        if (acc != null && acc > maxAccuracyMeters) return FilterDecision.Reject(sample, RejectReason.POOR_ACCURACY)
        val prev = last ?: run {
            last = sample
            return FilterDecision.Accept(sample, 0.0)
        }
        val dt = sample.timeMillis - prev.timeMillis
        if (dt <= 0) return FilterDecision.Reject(sample, RejectReason.OUT_OF_ORDER)
        val d = GeoMath.distanceMeters(prev.latitude, prev.longitude, sample.latitude, sample.longitude)
        if (d / (dt / 1000.0) > maxSpeedMps) return FilterDecision.Reject(sample, RejectReason.IMPLAUSIBLE_SPEED)
        val noise = maxOf(minDistanceMeters, ((acc ?: 0.0) + (prev.accuracyMeters ?: 0.0)) * 0.5)
        if (d < noise && dt < keepAliveMillis) return FilterDecision.Reject(sample, RejectReason.JITTER)
        last = sample
        // Si el movimiento no supera el ruido (punto de mantenimiento), no suma distancia.
        return FilterDecision.Accept(sample, if (d < noise) 0.0 else d)
    }
}
