package com.cumbre.core

import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.maps.CoverageLevel
import com.cumbre.core.maps.MapCoverage
import com.cumbre.core.maps.MapFileInspector
import com.cumbre.core.maps.MapInspection
import com.cumbre.core.maps.MapProblem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MapsTest {
    @Test
    fun `mapa ausente`() {
        val r = MapFileInspector.inspect(File("/no/existe/cordoba.map"))
        assertIs<MapInspection.Invalid>(r)
        assertEquals(MapProblem.NOT_FOUND, r.reason)
    }

    @Test
    fun `archivo vacio o que no es Mapsforge`() {
        val empty = File.createTempFile("vacio", ".map").apply { deleteOnExit() }
        assertEquals(MapProblem.EMPTY, (MapFileInspector.inspect(empty) as MapInspection.Invalid).reason)
        val junk = File.createTempFile("basura", ".map").apply { deleteOnExit(); writeText("PK\u0003\u0004 esto es un zip") }
        assertEquals(MapProblem.NOT_MAPSFORGE, (MapFileInspector.inspect(junk) as MapInspection.Invalid).reason)
    }

    @Test
    fun `cabecera Mapsforge truncada se informa como incompatible`() {
        val f = File.createTempFile("truncado", ".map").apply { deleteOnExit() }
        f.writeBytes("mapsforge binary OSM".toByteArray() + ByteArray(10))
        val r = MapFileInspector.inspect(f)
        assertIs<MapInspection.Invalid>(r)
        assertEquals(MapProblem.NOT_MAPSFORGE, r.reason)
    }

    @Test
    fun `cobertura de un recorrido por los mapas instalados`() {
        val cordoba = GeoBounds(-35.0, -66.0, -29.0, -61.0)
        val mendoza = GeoBounds(-37.6, -70.6, -32.0, -66.5)
        val route = listOf(GeoPoint(-31.99, -64.93), GeoPoint(-32.0, -64.92))
        assertEquals(CoverageLevel.FULL, MapCoverage.evaluate(route, listOf(mendoza, cordoba)).level)
        assertEquals(listOf(1), MapCoverage.evaluate(route, listOf(mendoza, cordoba)).coveringMaps)
        val crossing = listOf(GeoPoint(-32.5, -65.0), GeoPoint(-32.5, -65.8), GeoPoint(-32.5, -67.0), GeoPoint(-32.5, -67.5))
        val partial = MapCoverage.evaluate(crossing, listOf(cordoba))
        assertEquals(CoverageLevel.PARTIAL, partial.level)
        assertEquals(0.5, partial.fraction, 1e-9)
        assertEquals(CoverageLevel.NONE, MapCoverage.evaluate(route, listOf(mendoza)).level)
        assertEquals(CoverageLevel.NONE, MapCoverage.evaluate(route, emptyList()).level)
    }
}
