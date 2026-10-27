package com.cumbre.core

import com.cumbre.core.analysis.RouteFingerprint
import com.cumbre.core.gpx.GpxParseResult
import com.cumbre.core.gpx.GpxParser
import com.cumbre.core.gpx.GpxWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class FingerprintTest {
    @Test
    fun `el mismo recorrido con otro nombre o formato se detecta como duplicado`() {
        val (doc, _) = TestFiles.parseOk("valid_track.gpx")
        val renamed = doc.copy(metadata = doc.metadata.copy(name = "Otro nombre"), creator = "Otra app")
        val reexported = GpxParser().parse(GpxWriter.writeToString(renamed).byteInputStream())
        assertIs<GpxParseResult.Success>(reexported)
        assertEquals(RouteFingerprint.of(doc), RouteFingerprint.of(renamed))
        assertEquals(RouteFingerprint.of(doc), RouteFingerprint.of(reexported.document))
    }

    @Test
    fun `recorridos distintos tienen huellas distintas`() {
        val a = TestFiles.parseOk("valid_track.gpx").document
        val b = TestFiles.parseOk("no_elevation.gpx").document
        assertNotEquals(RouteFingerprint.of(a), RouteFingerprint.of(b))
        val shifted = a.copy(tracks = a.tracks.map { t ->
            t.copy(segments = t.segments.map { s -> s.map { it.copy(latitude = it.latitude + 0.0001) } })
        })
        assertNotEquals(RouteFingerprint.of(a), RouteFingerprint.of(shifted))
    }

    @Test
    fun `dividir un segmento cambia la huella`() {
        val a = TestFiles.parseOk("valid_track.gpx").document
        val seg = a.tracks.single().segments.single()
        val split = a.copy(tracks = listOf(a.tracks.single().copy(segments = listOf(seg.take(2), seg.drop(2)))))
        assertNotEquals(RouteFingerprint.of(a), RouteFingerprint.of(split))
    }
}
