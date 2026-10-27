package com.cumbre.core.permissions

data class PermissionSnapshot(
    val fineGranted: Boolean,
    val coarseGranted: Boolean,
    /** Valor de shouldShowRequestPermissionRationale para ubicación precisa. */
    val shouldShowRationale: Boolean,
    /** Si ya se pidió el permiso alguna vez (para distinguir "nunca preguntado" de "denegado para siempre"). */
    val requestedBefore: Boolean,
)

enum class PermissionAction {
    /** Ubicación precisa concedida: se puede usar el GPS. */
    READY,

    /** Solo ubicación aproximada: funciona, pero se pide precisa para navegar. */
    APPROXIMATE_ONLY,

    /** Mostrar explicación propia y luego pedir el permiso del sistema. */
    EXPLAIN_THEN_REQUEST,

    /** El usuario ya rechazó: explicar de nuevo antes de volver a pedir. */
    SHOW_RATIONALE,

    /** Denegado de forma permanente: solo puede habilitarse desde Ajustes. */
    OPEN_SETTINGS,
}

/** Decide cómo pedir la ubicación de forma progresiva y respetuosa. */
object LocationPermissionPolicy {
    fun decide(s: PermissionSnapshot): PermissionAction = when {
        s.fineGranted -> PermissionAction.READY
        s.coarseGranted -> PermissionAction.APPROXIMATE_ONLY
        !s.requestedBefore -> PermissionAction.EXPLAIN_THEN_REQUEST
        s.shouldShowRationale -> PermissionAction.SHOW_RATIONALE
        else -> PermissionAction.OPEN_SETTINGS
    }

    /** Precisión aproximada (2 km aprox.) no sirve para seguir un sendero. */
    fun canNavigate(action: PermissionAction): Boolean = action == PermissionAction.READY

    fun canShowPosition(action: PermissionAction): Boolean =
        action == PermissionAction.READY || action == PermissionAction.APPROXIMATE_ONLY
}
