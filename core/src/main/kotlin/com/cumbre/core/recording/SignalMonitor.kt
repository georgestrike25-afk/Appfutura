package com.cumbre.core.recording

enum class GpsSignal { SEARCHING, GOOD, WEAK, LOST }

/** Estado de la señal GPS a partir de la última lectura recibida. */
object SignalMonitor {
    const val LOST_AFTER_MILLIS = 20_000L
    const val GOOD_ACCURACY_M = 15.0

    fun evaluate(lastFixTimeMillis: Long?, lastAccuracyMeters: Double?, nowMillis: Long, maxAccuracy: Double = 35.0): GpsSignal = when {
        lastFixTimeMillis == null -> GpsSignal.SEARCHING
        nowMillis - lastFixTimeMillis > LOST_AFTER_MILLIS -> GpsSignal.LOST
        lastAccuracyMeters == null -> GpsSignal.WEAK
        lastAccuracyMeters <= GOOD_ACCURACY_M -> GpsSignal.GOOD
        lastAccuracyMeters <= maxAccuracy -> GpsSignal.WEAK
        else -> GpsSignal.WEAK
    }
}
