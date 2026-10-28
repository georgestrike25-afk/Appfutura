package com.cumbre.core

import com.cumbre.core.maps.IncomingFileDetector
import com.cumbre.core.maps.IncomingFileKind
import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingFileDetectorTest {
    private val zipHeader = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0)

    @Test
    fun `detecta por contenido aunque el nombre o el tipo no ayuden`() {
        assertEquals(IncomingFileKind.MAP, IncomingFileDetector.detect("descarga", "mapsforge binary OSM...".toByteArray()))
        assertEquals(IncomingFileKind.GPX, IncomingFileDetector.detect(null, "<?xml version=\"1.0\"?><gpx>".toByteArray()))
        assertEquals(IncomingFileKind.GPX, IncomingFileDetector.detect("ruta", "﻿<gpx version=\"1.1\">".toByteArray()))
        assertEquals(IncomingFileKind.RELIEF, IncomingFileDetector.detect("relieve.zip", zipHeader, listOf("S32W065.hgt")))
        assertEquals(IncomingFileKind.UNKNOWN, IncomingFileDetector.detect("fotos.zip", zipHeader, listOf("a.jpg")))
    }

    @Test
    fun `usa el nombre como respaldo`() {
        assertEquals(IncomingFileKind.RELIEF, IncomingFileDetector.detect("S32W065.hgt", ByteArray(64)))
        assertEquals(IncomingFileKind.GPX, IncomingFileDetector.detect("Cascada.GPX", ByteArray(64)))
        assertEquals(IncomingFileKind.UNKNOWN, IncomingFileDetector.detect("documento.pdf", "%PDF-1.7".toByteArray()))
    }
}
