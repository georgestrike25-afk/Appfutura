package com.cumbre.core

import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.maps.DemFiles
import com.cumbre.core.maps.DemTile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemTilesTest {
    @Test
    fun `nombres de teselas SRTM`() {
        assertEquals(DemTile(-33, -65), DemTile.parse("S33W065.hgt"))
        assertEquals(DemTile(-33, -65), DemTile.parse("s33w065.hgt.zip"))
        assertEquals(DemTile(-33, -65), DemTile.parse("S33W065.SRTMGL1.hgt.zip"))
        assertEquals(DemTile(46, 7), DemTile.parse("carpeta/N46E007.hgt"))
        assertNull(DemTile.parse("argentina.map"))
        assertNull(DemTile.parse("S95W065.hgt"))
        assertEquals("S33W065.hgt", DemTile(-33, -65).fileName)
        assertEquals("N05E010.hgt", DemTile(5, 10).fileName)
    }

    @Test
    fun `tesela que contiene un punto`() {
        assertEquals(DemTile(-33, -65), DemTile.containing(GeoPoint(-32.1, -64.5)))
        assertEquals(DemTile(46, 7), DemTile.containing(GeoPoint(46.5, 7.9)))
    }

    @Test
    fun `extrae solo teselas validas de un ZIP y evita rutas maliciosas`() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("SI20/S33W065.hgt")); z.write(ByteArray(1201 * 1201 * 2)); z.closeEntry()
                z.putNextEntry(ZipEntry("../../S34W065.hgt")); z.write(ByteArray(1201 * 1201 * 2)); z.closeEntry()
                z.putNextEntry(ZipEntry("S35W065.hgt")); z.write(ByteArray(1000)); z.closeEntry()
                z.putNextEntry(ZipEntry("leeme.txt")); z.write("hola".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val dir = Files.createTempDirectory("dem").toFile()
        val r = DemFiles.extractZip(ByteArrayInputStream(bytes), dir)
        assertEquals(setOf(DemTile(-33, -65), DemTile(-34, -65)), r.installed.toSet())
        assertEquals(2, r.skipped)
        assertTrue(File(dir, "S34W065.hgt").exists(), "La entrada con ../ se guarda dentro de la carpeta")
        assertTrue(dir.parentFile.listFiles()!!.none { it.name == "S34W065.hgt" })
        assertEquals(listOf(DemTile(-34, -65), DemTile(-33, -65)), DemFiles.installed(dir))
        val missing = DemFiles.missingFor(listOf(GeoPoint(-32.5, -64.5), GeoPoint(-31.5, -64.5)), DemFiles.installed(dir))
        assertEquals(listOf(DemTile(-32, -65)), missing)
        dir.deleteRecursively()
    }
}
