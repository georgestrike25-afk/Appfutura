package com.cumbre.core

import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.navigation.EtaEstimator
import com.cumbre.core.navigation.RemainingClimb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EtaEstimatorTest {
    private val profile = listOf(
        ProfilePoint(0.0, 1000.0), ProfilePoint(2000.0, 1300.0), ProfilePoint(4000.0, 1600.0),
        ProfilePoint(6000.0, 1300.0), ProfilePoint(8000.0, 1000.0),
    )

    @Test
    fun `desnivel pendiente desde la posicion actual`() {
        assertEquals(RemainingClimb(600.0, 600.0), EtaEstimator.remainingClimb(profile, 0.0))
        assertEquals(RemainingClimb(300.0, 600.0), EtaEstimator.remainingClimb(profile, 2000.0))
        assertEquals(RemainingClimb(0.0, 600.0), EtaEstimator.remainingClimb(profile, 4000.0))
        assertNull(EtaEstimator.remainingClimb(emptyList(), 0.0))
    }

    @Test
    fun `al comenzar usa DIN 33466 con el desnivel pendiente`() {
        // 4 km con 300 m de subida y 600 m de bajada: horizontal 1 h, vertical 1 h + 1,2 h = 2,2 h → 2,2 + 0,5 = 2,7 h.
        val s = EtaEstimator.estimateSeconds(4_000.0, RemainingClimb(300.0, 600.0))
        assertEquals((2.7 * 3600).toLong(), s)
        assertEquals(0L, EtaEstimator.estimateSeconds(0.0, null))
        // Sin elevaciones: solo distancia horizontal (3 km a 4 km/h = 45 min).
        assertEquals(45 * 60L, EtaEstimator.estimateSeconds(3_000.0, null))
    }

    @Test
    fun `con suficiente actividad se ajusta al ritmo propio`() {
        // Recorrió 4 km llanos (DIN: 1 h) en 30 min en movimiento: es el doble de rápido.
        val fast = EtaEstimator.estimateSeconds(4_000.0, RemainingClimb(0.0, 0.0), 4_000.0, RemainingClimb(0.0, 0.0), 30 * 60_000L)
        assertEquals(30 * 60L, fast)
        // Muy poco recorrido todavía: se mantiene DIN.
        val early = EtaEstimator.estimateSeconds(4_000.0, RemainingClimb(0.0, 0.0), 300.0, RemainingClimb(0.0, 0.0), 5 * 60_000L)
        assertEquals(3600L, early)
        // El factor se acota: tras una parada larga no da tiempos absurdos.
        val slow = EtaEstimator.estimateSeconds(4_000.0, RemainingClimb(0.0, 0.0), 1_000.0, RemainingClimb(0.0, 0.0), 5 * 3_600_000L)
        assertEquals((3600 * 2.5).toLong(), slow)
    }
}
