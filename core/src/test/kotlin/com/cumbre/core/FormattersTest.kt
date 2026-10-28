package com.cumbre.core

import com.cumbre.core.format.Formatters
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test
    fun `distancias en metros y kilometros con coma decimal`() {
        assertEquals("850 m", Formatters.distance(850.4))
        assertEquals("18,4 km", Formatters.distance(18_420.0))
        assertEquals("1,0 km", Formatters.distance(1_000.0))
        assertEquals("125 km", Formatters.distance(125_300.0))
        assertEquals("7,82", Formatters.kilometersValue(7_820.0))
    }

    @Test
    fun `elevacion con separador de miles`() {
        assertEquals("1.142 m", Formatters.elevation(1_142.4))
        assertEquals("2.790 m", Formatters.elevation(2_790.0))
        assertEquals("86 m", Formatters.elevation(86.0))
    }

    @Test
    fun `duracion en horas y minutos`() {
        assertEquals("7 h 20 min", Formatters.duration(7 * 3600L + 20 * 60))
        assertEquals("45 min", Formatters.duration(45 * 60L))
        assertEquals("2 h", Formatters.duration(7200))
        assertEquals("2:51:08", Formatters.clock((2 * 3600 + 51 * 60 + 8) * 1000L))
        assertEquals("5:03", Formatters.clock(303_000))
    }

    @Test
    fun `valores con unidad separada y duracion compacta`() {
        assertEquals("15,0" to "km", Formatters.distanceParts(15_000.0))
        assertEquals("850" to "m", Formatters.distanceParts(850.2))
        assertEquals("6 h 16", Formatters.durationCompact(6 * 3600L + 16 * 60))
        assertEquals("2 h 05", Formatters.durationCompact(2 * 3600L + 5 * 60))
        assertEquals("45 min", Formatters.durationCompact(45 * 60L))
    }

    @Test
    fun `velocidades`() {
        assertEquals("3,6 km/h", Formatters.speedKmh(1.0))
        assertEquals("1,0 m/s", Formatters.speedMps(1.0))
        assertEquals("±4 m", Formatters.accuracy(4.2))
    }

    @Test
    fun `tamaños de archivo`() {
        assertEquals("287 MB", Formatters.bytes(287L * 1024 * 1024))
        assertEquals("2,1 GB", Formatters.bytes((2.1 * 1024 * 1024 * 1024).toLong()))
    }
}
