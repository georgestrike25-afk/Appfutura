package com.cumbre.core

import com.cumbre.core.gpx.GpxErrorType
import com.cumbre.core.gpx.GpxParseResult
import com.cumbre.core.gpx.GpxParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpxParserTest {
    @Test
    fun `lee un GPX valido con metadatos, waypoints y track`() {
        val (doc, report) = TestFiles.parseOk("valid_track.gpx")
        assertEquals("1.1", doc.version)
        assertEquals("Cerro Champaquí por Los Reartes", doc.metadata.name)
        assertEquals("Ascenso clásico", doc.metadata.description)
        assertEquals("Montañista", doc.metadata.author)
        assertEquals("https://www.wikiloc.com/wikiloc/view.do?id=123456", doc.metadata.links.single().href)
        assertEquals("Ver en Wikiloc", doc.metadata.links.single().text)
        assertEquals(2, doc.waypoints.size)
        assertEquals("Cumbre", doc.waypoints[0].name)
        assertEquals("Summit", doc.waypoints[0].symbol)
        assertEquals("Agua potable", doc.waypoints[1].description)
        val track = doc.tracks.single()
        assertEquals("Champaquí", track.name)
        assertEquals("hiking", track.type)
        assertEquals(1, track.segments.size)
        assertEquals(5, track.segments[0].size)
        val first = track.segments[0][0]
        assertEquals(-32.0, first.latitude, 1e-9)
        assertEquals(-64.92, first.longitude, 1e-9)
        assertEquals(2000.0, first.elevation)
        assertEquals(1710057600000L, first.timeMillis)
        assertEquals(0, report.invalidCoordinates)
        assertEquals("Cerro Champaquí por Los Reartes", doc.displayName())
    }

    @Test
    fun `conserva los segmentos separados y cuenta los vacios`() {
        val (doc, report) = TestFiles.parseOk("multi_segment.gpx")
        val segments = doc.tracks.single().segments
        assertEquals(2, segments.size)
        assertEquals(2, segments[0].size)
        assertEquals(2, segments[1].size)
        assertEquals(1, report.emptySegments)
        assertEquals(2, doc.lines().size)
    }

    @Test
    fun `acepta tracks sin elevacion`() {
        val (doc, _) = TestFiles.parseOk("no_elevation.gpx")
        val pts = doc.lines().single()
        assertEquals(3, pts.size)
        assertTrue(pts.all { it.elevation == null && it.timeMillis == null })
        assertNull(doc.displayName())
    }

    @Test
    fun `descarta coordenadas fuera de rango sin rechazar la ruta`() {
        val (doc, report) = TestFiles.parseOk("out_of_range.gpx")
        assertEquals(2, doc.lines().single().size)
        assertEquals(4, report.invalidCoordinates)
    }

    @Test
    fun `falla con mensaje claro si todas las coordenadas son invalidas`() {
        val r = TestFiles.parse("only_invalid.gpx")
        assertIs<GpxParseResult.Failure>(r)
        assertEquals(GpxErrorType.NO_GEOMETRY, r.type)
        assertTrue(r.message.contains("2 coordenadas"))
    }

    @Test
    fun `archivo vacio`() {
        val r = TestFiles.parse("empty.gpx")
        assertIs<GpxParseResult.Failure>(r)
        assertEquals(GpxErrorType.EMPTY_FILE, r.type)
    }

    @Test
    fun `archivo dañado o truncado`() {
        val r = TestFiles.parse("corrupt.gpx")
        assertIs<GpxParseResult.Failure>(r)
        assertEquals(GpxErrorType.MALFORMED_XML, r.type)
    }

    @Test
    fun `texto que no es XML`() {
        val r = GpxParser().parse(ByteArrayInputStream("esto no es un gpx".toByteArray()))
        assertIs<GpxParseResult.Failure>(r)
        assertEquals(GpxErrorType.MALFORMED_XML, r.type)
    }

    @Test
    fun `XML que no es GPX`() {
        val r = TestFiles.parse("not_gpx.xml")
        assertIs<GpxParseResult.Failure>(r)
        assertEquals(GpxErrorType.NOT_GPX, r.type)
    }

    @Test
    fun `rechaza DOCTYPE y entidades externas`() {
        val r = TestFiles.parse("xxe.gpx")
        assertIs<GpxParseResult.Failure>(r)
        assertEquals(GpxErrorType.UNSAFE_CONTENT, r.type)
    }

    @Test
    fun `GPX 1_0 con ruta rte y metadatos en la raiz`() {
        val (doc, _) = TestFiles.parseOk("gpx10_route.gpx")
        assertEquals("1.0", doc.version)
        assertEquals("Ruta vieja", doc.metadata.name)
        assertTrue(doc.metadata.links.single().href.contains("wikiloc.com"))
        assertEquals(1, doc.routes.size)
        assertEquals(3, doc.lines().single().size)
    }

    @Test
    fun `GPX sin namespace tambien se acepta`() {
        val xml = """<gpx version="1.1"><trk><trkseg><trkpt lat="1" lon="2"/><trkpt lat="1.001" lon="2"/></trkseg></trk></gpx>"""
        val r = GpxParser().parse(ByteArrayInputStream(xml.toByteArray()))
        assertIs<GpxParseResult.Success>(r)
        assertEquals(2, r.document.lines().single().size)
    }

    @Test
    fun `limita la cantidad de puntos para no agotar la memoria`() {
        val sb = StringBuilder("<gpx version=\"1.1\"><trk><trkseg>")
        repeat(20) { sb.append("<trkpt lat=\"0\" lon=\"0.$it\"/>") }
        sb.append("</trkseg></trk></gpx>")
        val r = GpxParser(maxPoints = 10).parse(ByteArrayInputStream(sb.toString().toByteArray()))
        assertIs<GpxParseResult.Failure>(r)
        assertEquals(GpxErrorType.TOO_LARGE, r.type)
    }

    @Test
    fun `error de lectura del archivo`() {
        val failing = object : InputStream() {
            var n = 0
            override fun read(): Int {
                if (n++ < 20) return '<'.code
                throw IOException("permiso denegado")
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (n++ < 1) { b[off] = '<'.code.toByte(); return 1 }
                throw IOException("permiso denegado")
            }
        }
        val r = GpxParser().parse(failing)
        assertIs<GpxParseResult.Failure>(r)
        assertEquals(GpxErrorType.READ_ERROR, r.type)
    }

    @Test
    fun `fechas con zona horaria y sin ella`() {
        val xml = """<gpx version="1.1"><trk><trkseg>
            <trkpt lat="1" lon="2"><time>2024-03-10T05:00:00-03:00</time></trkpt>
            <trkpt lat="1" lon="2.001"><time>2024-03-10T08:00:00.500Z</time></trkpt>
            <trkpt lat="1" lon="2.002"><time>2024-03-10T08:00:01</time></trkpt>
            <trkpt lat="1" lon="2.003"><time>ayer</time></trkpt>
            </trkseg></trk></gpx>"""
        val r = GpxParser().parse(ByteArrayInputStream(xml.toByteArray()))
        assertIs<GpxParseResult.Success>(r)
        val pts = r.document.lines().single()
        assertEquals(1710057600000L, pts[0].timeMillis)
        assertEquals(1710057600500L, pts[1].timeMillis)
        assertEquals(1710057601000L, pts[2].timeMillis)
        assertNull(pts[3].timeMillis)
        assertEquals(1, r.report.invalidTimes)
        assertNotNull(pts[3])
    }
}
