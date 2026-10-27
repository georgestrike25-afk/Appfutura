package com.cumbre.core

import com.cumbre.core.gpx.GpxDocument
import com.cumbre.core.gpx.GpxLink
import com.cumbre.core.gpx.GpxMetadata
import com.cumbre.core.gpx.GpxParseResult
import com.cumbre.core.gpx.GpxParser
import com.cumbre.core.gpx.GpxPoint
import com.cumbre.core.gpx.GpxTrack
import com.cumbre.core.gpx.GpxWaypoint
import com.cumbre.core.gpx.GpxWriter
import com.cumbre.core.recording.RecordedPoint
import com.cumbre.core.recording.RecordingExport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GpxWriterTest {
    @Test
    fun `exportar y volver a importar conserva la ruta`() {
        val (original, _) = TestFiles.parseOk("valid_track.gpx")
        val xml = GpxWriter.writeToString(original)
        val reparsed = GpxParser().parse(xml.byteInputStream())
        assertIs<GpxParseResult.Success>(reparsed)
        val doc = reparsed.document
        assertEquals(original.metadata.name, doc.metadata.name)
        assertEquals(original.waypoints.map { it.name }, doc.waypoints.map { it.name })
        assertEquals(original.lines().size, doc.lines().size)
        val a = original.lines().flatten()
        val b = doc.lines().flatten()
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (p, q) ->
            assertEquals(p.latitude, q.latitude, 1e-7)
            assertEquals(p.longitude, q.longitude, 1e-7)
            assertEquals(p.elevation!!, q.elevation!!, 0.05)
            assertEquals(p.timeMillis, q.timeMillis)
        }
    }

    @Test
    fun `escapa caracteres especiales y genera GPX 1_1`() {
        val doc = GpxDocument(
            metadata = GpxMetadata(name = "Ruta <A&B> \"x\"", links = listOf(GpxLink("https://x.com/?a=1&b=2"))),
            waypoints = listOf(GpxWaypoint(1.0, 2.0, name = "Puesto 'Don José'")),
            tracks = listOf(GpxTrack(segments = listOf(listOf(GpxPoint(1.0, 2.0), GpxPoint(1.1, 2.1))))),
        )
        val xml = GpxWriter.writeToString(doc)
        assertTrue(xml.contains("version=\"1.1\""))
        assertTrue(xml.contains("xmlns=\"http://www.topografix.com/GPX/1/1\""))
        assertTrue(xml.contains("Ruta &lt;A&amp;B&gt; &quot;x&quot;"))
        val r = GpxParser().parse(xml.byteInputStream())
        assertIs<GpxParseResult.Success>(r)
        assertEquals("Ruta <A&B> \"x\"", r.document.metadata.name)
        assertEquals("Puesto 'Don José'", r.document.waypoints.single().name)
        assertEquals("https://x.com/?a=1&b=2", r.document.metadata.links.single().href)
    }

    @Test
    fun `una actividad grabada se exporta con sus tramos y tiempos`() {
        val points = listOf(
            RecordedPoint(0, -31.0, -64.0, 1_000, 1000.0, 5.0, 1.0),
            RecordedPoint(0, -31.001, -64.0, 11_000, 1005.0, 5.0, 1.0),
            RecordedPoint(1, -31.002, -64.0, 100_000, 1010.0, 5.0, 1.0),
            RecordedPoint(1, -31.003, -64.0, 110_000, null, 5.0, 1.0),
        )
        val doc = RecordingExport.toGpx("Salida del domingo", 1_000, points)
        val r = GpxParser().parse(GpxWriter.writeToString(doc).byteInputStream())
        assertIs<GpxParseResult.Success>(r)
        val segs = r.document.tracks.single().segments
        assertEquals(2, segs.size)
        assertEquals(110_000L, segs[1][1].timeMillis)
        assertEquals(null, segs[1][1].elevation)
        assertEquals("Salida del domingo", r.document.metadata.name)
    }
}
