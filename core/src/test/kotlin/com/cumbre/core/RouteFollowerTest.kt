package com.cumbre.core

import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.navigation.RouteFollower
import com.cumbre.core.navigation.TrackGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteFollowerTest {
    // Línea recta hacia el este sobre el ecuador: 0,01° ≈ 1.112 m.
    private val line = (0..10).map { GeoPoint(0.0, it * 0.001) }
    private val geometry = TrackGeometry(listOf(line))

    @Test
    fun `progreso, distancia restante y distancia al track`() {
        val f = RouteFollower(geometry)
        val s = f.update(GeoPoint(0.0002, 0.005))!!
        assertEquals(1_111.95, geometry.totalDistanceMeters, 0.5)
        assertEquals(555.97, s.progressMeters, 0.5)
        assertEquals(555.97, s.remainingMeters, 0.5)
        assertEquals(22.24, s.distanceToTrackMeters, 0.3)
        assertFalse(s.offRoute)
    }

    @Test
    fun `detecta desvio tras varias lecturas y lo avisa una sola vez`() {
        val f = RouteFollower(geometry, offRouteThresholdMeters = 50.0)
        val far = GeoPoint(0.001, 0.005) // ~111 m del track
        val s1 = f.update(far, accuracyMeters = 8.0)!!
        val s2 = f.update(far, accuracyMeters = 8.0)!!
        val s3 = f.update(far, accuracyMeters = 8.0)!!
        val s4 = f.update(far, accuracyMeters = 8.0)!!
        assertFalse(s1.offRoute); assertFalse(s2.offRoute)
        assertTrue(s3.offRoute); assertTrue(s3.offRouteStarted)
        assertTrue(s4.offRoute); assertFalse(s4.offRouteStarted)
        val back = f.update(GeoPoint(0.0001, 0.0051), accuracyMeters = 8.0)!!
        assertFalse(back.offRoute)
    }

    @Test
    fun `una lectura imprecisa no dispara el aviso de desvio`() {
        val f = RouteFollower(geometry, offRouteThresholdMeters = 50.0)
        repeat(5) { f.update(GeoPoint(0.001, 0.005), accuracyMeters = 120.0) }
        assertFalse(f.update(GeoPoint(0.001, 0.005), accuracyMeters = 120.0)!!.offRoute)
    }

    @Test
    fun `en un recorrido de ida y vuelta no salta al tramo de regreso`() {
        val out = (0..10).map { GeoPoint(0.0, it * 0.001) }
        val back = (9 downTo 0).map { GeoPoint(0.00005, it * 0.001) }
        val g = TrackGeometry(listOf(out + back))
        val f = RouteFollower(g)
        // Avanzando por la ida: progreso creciente cerca de 0..1112 m.
        f.update(GeoPoint(0.0, 0.001))
        f.update(GeoPoint(0.0, 0.002))
        val s = f.update(GeoPoint(0.00003, 0.003))!!
        assertTrue(s.progressMeters < 500, "Debería seguir en la ida: ${s.progressMeters}")
    }

    @Test
    fun `proximo waypoint a lo largo del track`() {
        val f = RouteFollower(
            geometry,
            waypoints = listOf(
                "Refugio" to GeoPoint(0.0003, 0.007),
                "Lejos" to GeoPoint(0.05, 0.05),
                "Inicio" to GeoPoint(0.0, 0.0),
            ),
        )
        assertEquals(listOf("Inicio", "Refugio"), f.waypointsOnTrack.map { it.name })
        val s = f.update(GeoPoint(0.0, 0.002))!!
        assertEquals("Refugio", s.nextWaypoint?.name)
        assertEquals(556.0, s.distanceToNextWaypointMeters!!, 1.0)
        val end = f.update(GeoPoint(0.0, 0.0098))!!
        assertNull(end.nextWaypoint)
        assertTrue(end.nearEnd)
    }

    @Test
    fun `segmentos separados no crean tramos ficticios`() {
        val g = TrackGeometry(listOf(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001)), listOf(GeoPoint(0.0, 0.01), GeoPoint(0.0, 0.011))))
        assertEquals(222.39, g.totalDistanceMeters, 0.1)
        // Un punto en el hueco entre segmentos queda lejos del track.
        val m = g.match(GeoPoint(0.0, 0.0055))!!
        assertTrue(m.distanceToTrackMeters > 400)
        assertNotNull(m)
    }
}
