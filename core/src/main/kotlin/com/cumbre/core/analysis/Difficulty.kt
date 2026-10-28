package com.cumbre.core.analysis

enum class Difficulty(val label: String, val level: Int) {
    EASY("Fácil", 1),
    MODERATE("Moderada", 2),
    HARD("Difícil", 3),
    VERY_HARD("Muy difícil", 4),
}

/**
 * Dificultad orientativa a partir del esfuerzo: kilómetros esfuerzo (distancia + 1 km por
 * cada 100 m de ascenso, criterio usado en senderismo) y desnivel positivo. No considera
 * el tipo de terreno ni la exposición, que el GPX no informa.
 */
object DifficultyEstimator {
    fun effortKm(distanceMeters: Double, gainMeters: Double?): Double =
        distanceMeters / 1000.0 + (gainMeters ?: 0.0) / 100.0

    fun estimate(distanceMeters: Double, gainMeters: Double?): Difficulty? {
        if (distanceMeters <= 0.0) return null
        val effort = effortKm(distanceMeters, gainMeters)
        val gain = gainMeters ?: 0.0
        return when {
            effort >= 36 || gain >= 1_600 -> Difficulty.VERY_HARD
            effort >= 24 || gain >= 1_000 -> Difficulty.HARD
            effort >= 10 || gain >= 400 -> Difficulty.MODERATE
            else -> Difficulty.EASY
        }
    }
}
