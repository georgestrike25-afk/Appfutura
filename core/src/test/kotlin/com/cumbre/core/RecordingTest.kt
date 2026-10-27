package com.cumbre.core

import com.cumbre.core.recording.ActivityClock
import com.cumbre.core.recording.ActivityStatsAccumulator
import com.cumbre.core.recording.FilterDecision
import com.cumbre.core.recording.GpsSignal
import com.cumbre.core.recording.LocationFilter
import com.cumbre.core.recording.LocationSample
import com.cumbre.core.recording.RecordedPoint
import com.cumbre.core.recording.RecoveryDecision
import com.cumbre.core.recording.RecoveryPolicy
import com.cumbre.core.recording.RejectReason
import com.cumbre.core.recording.SignalMonitor
import com.cumbre.core.recording.StoredActivityInfo
import com.cumbre.core.recording.StoredActivityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordingTest {
    private fun sample(lon: Double, t: Long, acc: Double? = 5.0, alt: Double? = null) =
        LocationSample(0.0, lon, t, alt, acc, null, null)

    @Test
    fun `filtro GPS acepta movimiento real y descarta ruido`() {
        val f = LocationFilter()
        assertIs<FilterDecision.Accept>(f.process(sample(0.0, 0)))
        // 1 m en 1 s con 5 m de precisión: ruido.
        assertEquals(RejectReason.JITTER, (f.process(sample(0.000009, 1_000)) as FilterDecision.Reject).reason)
        // 11 m en 5 s: caminata real.
        val ok = f.process(sample(0.0001, 5_000))
        assertIs<FilterDecision.Accept>(ok)
        assertEquals(11.1, ok.distanceMeters, 0.2)
    }

    @Test
    fun `filtro GPS descarta lecturas imprecisas, desordenadas e imposibles`() {
        val f = LocationFilter(maxAccuracyMeters = 35.0)
        f.process(sample(0.0, 10_000))
        assertEquals(RejectReason.POOR_ACCURACY, (f.process(sample(0.001, 20_000, acc = 80.0)) as FilterDecision.Reject).reason)
        assertEquals(RejectReason.OUT_OF_ORDER, (f.process(sample(0.001, 5_000)) as FilterDecision.Reject).reason)
        // 11 km en 10 s.
        assertEquals(RejectReason.IMPLAUSIBLE_SPEED, (f.process(sample(0.1, 20_000)) as FilterDecision.Reject).reason)
        assertEquals(RejectReason.INVALID_COORDINATES, (f.process(LocationSample(100.0, 0.0, 30_000)) as FilterDecision.Reject).reason)
    }

    @Test
    fun `quieto mucho tiempo guarda un punto de mantenimiento sin sumar distancia`() {
        val f = LocationFilter()
        f.process(sample(0.0, 0))
        val keep = f.process(sample(0.00001, 61_000))
        assertIs<FilterDecision.Accept>(keep)
        assertEquals(0.0, keep.distanceMeters)
    }

    @Test
    fun `estadisticas de actividad y reconstruccion tras cierre`() {
        val points = listOf(
            RecordedPoint(0, 0.0, 0.0, 0, 1000.0, 5.0, 1.2),
            RecordedPoint(0, 0.0, 0.001, 100_000, 1020.0, 5.0, 1.1),
            RecordedPoint(0, 0.0, 0.002, 200_000, 1040.0, 5.0, 1.1),
            // Pausa: nuevo tramo lejos; no se suma la distancia del hueco.
            RecordedPoint(1, 0.0, 0.010, 900_000, 1100.0, 5.0, 1.0),
            RecordedPoint(1, 0.0, 0.011, 1_000_000, 1085.0, 5.0, 1.0),
        )
        val live = ActivityStatsAccumulator()
        points.forEach { live.add(it) }
        val s = live.snapshot(elapsedMillis = 300_000)
        assertEquals(3 * 111.19, s.distanceMeters, 0.5)
        assertEquals(40.0, s.elevationGainMeters, 1e-9) // el salto 1040→1100 entre tramos no cuenta
        assertEquals(15.0, s.elevationLossMeters, 1e-9)
        assertEquals(300_000L, s.movingMillis)
        assertEquals(5, s.pointCount)
        assertEquals(s.distanceMeters / 300.0, s.averageSpeedMps, 1e-9)

        val rebuilt = ActivityStatsAccumulator.rebuild(points.shuffled(java.util.Random(4))).snapshot(300_000)
        assertEquals(s, rebuilt)
    }

    @Test
    fun `reloj de actividad descuenta pausas y sobrevive a la persistencia`() {
        var c = ActivityClock().start(1_000)
        assertEquals(4_000, c.elapsed(5_000))
        c = c.pause(5_000)
        assertEquals(4_000, c.elapsed(60_000))
        c = c.start(60_000)
        val restored = ActivityClock(c.accumulatedMillis, c.runningSinceMillis)
        assertEquals(10_000, restored.elapsed(66_000))
    }

    @Test
    fun `politica de recuperacion de actividades`() {
        val now = 100L * 60 * 60 * 1000
        assertEquals(RecoveryDecision.Nothing, RecoveryPolicy.decide(null, now))
        val finished = StoredActivityInfo(1, StoredActivityState.FINISHED, now, now, 10)
        assertEquals(RecoveryDecision.Nothing, RecoveryPolicy.decide(finished, now))
        val recent = StoredActivityInfo(2, StoredActivityState.RECORDING, now - 60_000, now - 3_600_000, 120)
        assertEquals(RecoveryDecision.OfferResume(2, wasRecording = true), RecoveryPolicy.decide(recent, now))
        val paused = recent.copy(id = 3, state = StoredActivityState.PAUSED)
        assertEquals(RecoveryDecision.OfferResume(3, wasRecording = false), RecoveryPolicy.decide(paused, now))
        val stale = recent.copy(id = 4, lastPointTimeMillis = now - RecoveryPolicy.STALE_AFTER_MILLIS - 1)
        assertEquals(RecoveryDecision.OfferFinish(4), RecoveryPolicy.decide(stale, now))
        val emptyStale = StoredActivityInfo(5, StoredActivityState.RECORDING, null, now - RecoveryPolicy.STALE_AFTER_MILLIS - 1, 0)
        assertEquals(RecoveryDecision.DiscardEmpty(5), RecoveryPolicy.decide(emptyStale, now))
    }

    @Test
    fun `estado de la señal GPS`() {
        assertEquals(GpsSignal.SEARCHING, SignalMonitor.evaluate(null, null, 1_000))
        assertEquals(GpsSignal.GOOD, SignalMonitor.evaluate(1_000, 4.0, 2_000))
        assertEquals(GpsSignal.WEAK, SignalMonitor.evaluate(1_000, 30.0, 2_000))
        assertEquals(GpsSignal.LOST, SignalMonitor.evaluate(1_000, 4.0, 1_000 + SignalMonitor.LOST_AFTER_MILLIS + 1))
        assertTrue(SignalMonitor.LOST_AFTER_MILLIS > 0)
    }
}
