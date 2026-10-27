package com.cumbre.core

import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoMath
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.geo.PolylineCodec
import com.cumbre.core.geo.Simplifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoMathTest {
    @Test
    fun `un grado de latitud mide unos 111 km`() {
        assertEquals(111_195.0, GeoMath.distanceMeters(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test
    fun `distancia conocida Buenos Aires - Cordoba en metros`() {
        // Obelisco (BA) - Plaza San Martín (Córdoba): ~646 km en línea recta.
        val d = GeoMath.distanceMeters(-34.6037, -58.3816, -31.4167, -64.1833)
        assertEquals(646_000.0, d, 3_000.0)
    }

    @Test
    fun `distancia cero y simetria`() {
        val a = GeoPoint(-32.0, -64.9)
        val b = GeoPoint(-32.01, -64.95)
        assertEquals(0.0, GeoMath.distanceMeters(a, a), 1e-9)
        assertEquals(GeoMath.distanceMeters(a, b), GeoMath.distanceMeters(b, a), 1e-9)
    }

    @Test
    fun `proyeccion sobre un segmento`() {
        val a = GeoPoint(0.0, 0.0)
        val b = GeoPoint(0.0, 0.01)
        val p = GeoPoint(0.0005, 0.005) // ~55,6 m al norte del punto medio
        val proj = GeoMath.projectOnSegment(p, a, b)
        assertEquals(0.5, proj.fraction, 1e-3)
        assertEquals(55.6, proj.distanceMeters, 0.5)
        val before = GeoMath.projectOnSegment(GeoPoint(0.0, -0.001), a, b)
        assertEquals(0.0, before.fraction, 1e-9)
        assertEquals(111.2, before.distanceMeters, 0.5)
    }

    @Test
    fun `rumbo hacia el norte y el este`() {
        assertEquals(0.0, GeoMath.bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 1e-6)
        assertEquals(90.0, GeoMath.bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 1e-6)
    }

    @Test
    fun `validacion de coordenadas`() {
        assertTrue(GeoPoint.isValid(-90.0, 180.0))
        assertFalse(GeoPoint.isValid(90.01, 0.0))
        assertFalse(GeoPoint.isValid(0.0, -180.5))
        assertFalse(GeoPoint.isValid(Double.NaN, 0.0))
    }

    @Test
    fun `limites geograficos`() {
        val b = GeoBounds.of(listOf(GeoPoint(-32.0, -65.0), GeoPoint(-31.0, -64.0)))!!
        assertTrue(b.contains(GeoPoint(-31.5, -64.5)))
        assertFalse(b.contains(GeoPoint(-30.0, -64.5)))
        assertEquals(GeoPoint(-31.5, -64.5), b.center)
    }

    @Test
    fun `polilinea codificada ida y vuelta`() {
        val pts = listOf(GeoPoint(-31.98901, -64.93502), GeoPoint(-32.0, -64.92), GeoPoint(38.5, -120.2))
        val decoded = PolylineCodec.decode(PolylineCodec.encode(pts))
        assertEquals(pts.size, decoded.size)
        pts.zip(decoded).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-5)
            assertEquals(a.longitude, b.longitude, 1e-5)
        }
    }

    @Test
    fun `simplificacion conserva extremos y elimina puntos alineados`() {
        val line = (0..100).map { GeoPoint(0.0, it * 0.0001) }
        val s = Simplifier.simplify(line, 1.0)
        assertEquals(2, s.size)
        assertEquals(line.first(), s.first())
        assertEquals(line.last(), s.last())
        val zigzag = (0..200).map { GeoPoint(if (it % 2 == 0) 0.0 else 0.001, it * 0.001) }
        assertTrue(Simplifier.simplifyToMax(zigzag, 50).size <= 50)
    }
}
