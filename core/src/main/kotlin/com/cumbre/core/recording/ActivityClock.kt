package com.cumbre.core.recording

/**
 * Tiempo activo de una actividad, descontando pausas. Se persiste como
 * (acumulado, inicio del tramo activo) para poder restaurarlo tras un cierre del proceso.
 */
data class ActivityClock(
    val accumulatedMillis: Long = 0,
    /** Instante (epoch ms) en que empezó el tramo activo actual, o null si está en pausa. */
    val runningSinceMillis: Long? = null,
) {
    val isRunning: Boolean get() = runningSinceMillis != null

    fun elapsed(nowMillis: Long): Long =
        accumulatedMillis + (runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0) } ?: 0)

    fun start(nowMillis: Long): ActivityClock = if (isRunning) this else copy(runningSinceMillis = nowMillis)

    fun pause(nowMillis: Long): ActivityClock =
        if (!isRunning) this else ActivityClock(elapsed(nowMillis), null)
}
