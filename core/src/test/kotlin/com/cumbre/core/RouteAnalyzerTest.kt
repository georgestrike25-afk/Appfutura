package com.cumbre.core

import com.cumbre.core.analysis.DurationEstimator
import com.cumbre.core.analysis.ElevationCalculator
import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.analysis.QualityIssue
import com.cumbre.core.analysis.RouteAnalyzer
import com.cumbre.core.gpx.GpxPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteAnalyzerTest {
    @Test
    fun `estadisticas de un track valido`() {
        val (doc, report) = TestFiles.parseOk("valid_track.gpx")
        val a = RouteAnalyzer.analyze(doc, report)
        val s = a.stats
        assertEquals(5, s.pointCount)
        assertEquals(1, s.segmentCount)
        assertEquals(2, s.waypointCount)
        assertEquals(790.0, s.elevationGainMeters!!, 1e-9)
        assertEquals(0.0, s.elevationLossMeters!!, 1e-9)
        assertEquals(2000.0, s.minElevationMeters)
        assertEquals(2790.0, s.maxElevationMeters)
        assertEquals(3 * 3600L, s.recordedDurationSeconds)
        assertNotNull(s.estimatedDurationSeconds)
        assertEquals(1.0, s.elevationCoverage, 1e-9)
        // Distancia: suma de los 4 tramos calculada de forma independiente.
        val pts = doc.lines().single()
        val expected = pts.zipWithNext { p, q -> com.cumbre.core.geo.GeoMath.distanceMeters(p.geo, q.geo) }.sum()
        assertEquals(expected, s.distanceMeters, 1e-6)
        assertTrue(s.distanceMeters in 1_800.0..2_000.0, "distancia en metros: ${s.distanceMeters}")
        assertTrue(a.profile.isNotEmpty())
        assertTrue(a.issues.isEmpty(), "No debería haber avisos: ${a.issues}")
    }

    @Test
    fun `los segmentos separados no suman el hueco entre ellos`() {
        val (doc, report) = TestFiles.parseOk("multi_segment.gpx")
        val a = RouteAnalyzer.analyze(doc, report)
        // Dos tramos de 0,001° de longitud en el ecuador: 2 × 111,19 m.
        assertEquals(222.39, a.stats.distanceMeters, 0.1)
        assertEquals(2, a.stats.segmentCount)
        // El desnivel tampoco suma la diferencia entre segmentos (110 → 120).
        assertEquals(20.0, a.stats.elevationGainMeters!!, 1e-9)
        assertTrue(a.issues.any { it is QualityIssue.SeparatedSegments })
        assertTrue(a.issues.any { it is QualityIssue.EmptySegments && it.count == 1 })
    }

    @Test
    fun `sin elevaciones no inventa desnivel`() {
        val (doc, report) = TestFiles.parseOk("no_elevation.gpx")
        val a = RouteAnalyzer.analyze(doc, report)
        assertNull(a.stats.elevationGainMeters)
        assertNull(a.stats.elevationLossMeters)
        assertNull(a.stats.minElevationMeters)
        assertTrue(a.profile.isEmpty())
        assertTrue(QualityIssue.NoElevation in a.issues)
        assertTrue(QualityIssue.MissingName in a.issues)
        // La duración se estima solo con la distancia horizontal.
        val d = a.stats.distanceMeters
        assertEquals((d / 1000.0 / 4.0 * 3600).toLong(), a.stats.estimatedDurationSeconds)
        assertNull(a.stats.recordedDurationSeconds)
    }

    @Test
    fun `detecta saltos geograficos`() {
        val (doc, report) = TestFiles.parseOk("gap.gpx")
        val a = RouteAnalyzer.analyze(doc, report)
        val gap = a.issues.filterIsInstance<QualityIssue.LargeGap>().single()
        assertEquals(1, gap.count)
        assertEquals(0.019 * 111_195.0, gap.maxGapMeters, 5.0)
        assertEquals(111.19, gap.atDistanceMeters, 0.5)
    }

    @Test
    fun `informa coordenadas invalidas descartadas`() {
        val (doc, report) = TestFiles.parseOk("out_of_range.gpx")
        val a = RouteAnalyzer.analyze(doc, report)
        assertTrue(a.issues.contains(QualityIssue.InvalidCoordinates(4)))
    }

    @Test
    fun `desnivel con histeresis ignora el ruido`() {
        val noisy = listOf(100.0, 102.0, 99.0, 101.0, 100.0, 103.0, 98.0, 100.0)
        val (gain, loss) = ElevationCalculator.compute(listOf(noisy))
        assertEquals(0.0, gain)
        assertEquals(0.0, loss)
        val climb = listOf(100.0, 110.0, 120.0, 118.0, 140.0, 130.0, 100.0)
        val (g2, l2) = ElevationCalculator.compute(listOf(climb))
        assertEquals(40.0, g2, 1e-9)
        assertEquals(40.0, l2, 1e-9)
    }

    @Test
    fun `desnivel parcial cuando faltan elevaciones`() {
        val pts = (0 until 10).map { GpxPoint(0.0, it * 0.001, if (it < 3) 100.0 + it * 10 else null) }
        val a = RouteAnalyzer.analyzeLines(listOf(pts))
        assertNull(a.stats.elevationGainMeters, "Con 30 % de cobertura no se calcula desnivel")
        assertTrue(a.issues.contains(QualityIssue.PartialElevation(30)))
    }

    @Test
    fun `duracion estimada DIN 33466`() {
        // 12 km y 900 m de ascenso: horizontal 3 h, vertical 3 h → 3 + 1,5 = 4,5 h.
        assertEquals(4L * 3600 + 1800, DurationEstimator.estimateSeconds(12_000.0, 900.0, 0.0))
        // 8 km sin desnivel → 2 h.
        assertEquals(7200L, DurationEstimator.estimateSeconds(8_000.0, null, null))
        assertNull(DurationEstimator.estimateSeconds(0.0, 0.0, 0.0))
    }

    @Test
    fun `el perfil reducido conserva la cumbre`() {
        val profile = (0..10_000).map { ProfilePoint(it.toDouble(), if (it == 5_001) 3_000.0 else 1_000.0) }
        val reduced = RouteAnalyzer.downsampleProfile(profile, 100)
        assertTrue(reduced.size <= 103)
        assertEquals(3_000.0, reduced.maxOf { it.elevationMeters })
        assertEquals(profile.first(), reduced.first())
        assertEquals(profile.last(), reduced.last())
    }
}
