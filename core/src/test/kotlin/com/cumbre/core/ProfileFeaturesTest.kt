package com.cumbre.core

import com.cumbre.core.analysis.ProfileFeatures
import com.cumbre.core.analysis.ProfileHighlight
import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.analysis.RouteAnalyzer
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.navigation.TrackGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProfileFeaturesTest {
    @Test
    fun `punto mas alto y altitud interpolada`() {
        val p = listOf(ProfilePoint(0.0, 800.0), ProfilePoint(1000.0, 1100.0), ProfilePoint(2000.0, 900.0))
        assertEquals(ProfileHighlight(1000.0, 1100.0), ProfileFeatures.highest(p))
        assertEquals(ProfileHighlight(0.0, 800.0), ProfileFeatures.lowest(p))
        assertEquals(950.0, ProfileFeatures.elevationAt(p, 500.0)!!, 1e-9)
        assertEquals(800.0, ProfileFeatures.elevationAt(p, -10.0))
        assertEquals(900.0, ProfileFeatures.elevationAt(p, 5000.0))
    }

    @Test
    fun `punto del track a una distancia, en la misma escala que el perfil`() {
        val line = (0..10).map { GeoPoint(0.0, it * 0.001) }
        val g = TrackGeometry(listOf(line))
        val mid = g.pointAt(g.totalDistanceMeters / 2)!!
        assertEquals(0.005, mid.longitude, 1e-9)
        assertEquals(line.first(), g.pointAt(-5.0))
        assertEquals(0.01, g.pointAt(1e9)!!.longitude, 1e-9)
    }

    @Test
    fun `la cumbre del perfil se ubica sobre el track`() {
        val (doc, report) = TestFiles.parseOk("valid_track.gpx")
        val analysis = RouteAnalyzer.analyze(doc, report)
        val top = ProfileFeatures.highest(analysis.profile)!!
        assertEquals(2790.0, top.elevationMeters)
        val g = TrackGeometry(doc.lines().map { l -> l.map { it.geo } })
        val point = assertNotNull(g.pointAt(top.distanceMeters))
        // La cumbre del archivo de prueba es su último punto.
        assertEquals(-31.989, point.latitude, 1e-6)
        assertEquals(-64.935, point.longitude, 1e-6)
    }
}
