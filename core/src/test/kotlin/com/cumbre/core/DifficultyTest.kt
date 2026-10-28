package com.cumbre.core

import com.cumbre.core.analysis.Difficulty
import com.cumbre.core.analysis.DifficultyEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DifficultyTest {
    @Test
    fun `dificultad estimada por esfuerzo y desnivel`() {
        assertEquals(Difficulty.EASY, DifficultyEstimator.estimate(5_000.0, 150.0))
        assertEquals(Difficulty.MODERATE, DifficultyEstimator.estimate(15_000.0, 823.0))
        assertEquals(Difficulty.HARD, DifficultyEstimator.estimate(9_600.0, 1_010.0))
        assertEquals(Difficulty.VERY_HARD, DifficultyEstimator.estimate(25_000.0, 1_600.0))
        // Sin elevaciones, se estima solo con la distancia.
        assertEquals(Difficulty.MODERATE, DifficultyEstimator.estimate(12_000.0, null))
        assertNull(DifficultyEstimator.estimate(0.0, 100.0))
        assertEquals(23.23, DifficultyEstimator.effortKm(15_000.0, 823.0), 1e-9)
    }
}
