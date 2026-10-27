package com.cumbre.core.recording

enum class StoredActivityState { RECORDING, PAUSED, FINISHED }

data class StoredActivityInfo(
    val id: Long,
    val state: StoredActivityState,
    val lastPointTimeMillis: Long?,
    val startedAtMillis: Long,
    val pointCount: Int,
)

sealed interface RecoveryDecision {
    data object Nothing : RecoveryDecision

    /** El servicio murió durante la grabación: se reanuda automáticamente en pausa y se informa. */
    data class OfferResume(val activityId: Long, val wasRecording: Boolean) : RecoveryDecision

    /** Actividad abandonada hace mucho: se propone finalizarla con los datos guardados. */
    data class OfferFinish(val activityId: Long) : RecoveryDecision

    /** Actividad vacía sin puntos y antigua: puede descartarse sin perder datos. */
    data class DiscardEmpty(val activityId: Long) : RecoveryDecision
}

/** Decide qué hacer con una actividad que quedó abierta tras cerrarse la app o el proceso. */
object RecoveryPolicy {
    const val STALE_AFTER_MILLIS = 12 * 60 * 60 * 1000L

    fun decide(stored: StoredActivityInfo?, nowMillis: Long): RecoveryDecision {
        if (stored == null || stored.state == StoredActivityState.FINISHED) return RecoveryDecision.Nothing
        val lastActivity = stored.lastPointTimeMillis ?: stored.startedAtMillis
        val stale = nowMillis - lastActivity > STALE_AFTER_MILLIS
        return when {
            stored.pointCount == 0 && stale -> RecoveryDecision.DiscardEmpty(stored.id)
            stale -> RecoveryDecision.OfferFinish(stored.id)
            else -> RecoveryDecision.OfferResume(stored.id, stored.state == StoredActivityState.RECORDING)
        }
    }
}
