package com.cumbre.core.maps

import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoPoint
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.floor

/** Tesela de elevación SRTM (1° × 1°), identificada por su esquina suroeste. */
data class DemTile(val south: Int, val west: Int) {
    /** Nombre estándar, por ejemplo «S33W065.hgt». */
    val fileName: String
        get() = buildString {
            append(if (south < 0) 'S' else 'N')
            append("%02d".format(kotlin.math.abs(south)))
            append(if (west < 0) 'W' else 'E')
            append("%03d".format(kotlin.math.abs(west)))
            append(".hgt")
        }

    val bounds: GeoBounds get() = GeoBounds(south.toDouble(), west.toDouble(), south + 1.0, west + 1.0)

    companion object {
        private val NAME = Regex("""(?i).*?([ns])(\d{1,2})([ew])(\d{1,3})(?:\.[a-z0-9]+)*\.(hgt|zip)$""")

        /** Interpreta nombres como «S33W065.hgt», «s33w065.hgt.zip» o «S33W065.SRTMGL1.hgt.zip». */
        fun parse(name: String): DemTile? {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
            val m = NAME.matchEntire(base) ?: return null
            val lat = m.groupValues[2].toInt()
            val lon = m.groupValues[4].toInt()
            if (lat > 89 || lon > 180) return null
            val south = if (m.groupValues[1].equals("s", true)) -lat else lat
            val west = if (m.groupValues[3].equals("w", true)) -lon else lon
            return DemTile(south, west)
        }

        fun containing(p: GeoPoint) = DemTile(floor(p.latitude).toInt(), floor(p.longitude).toInt())
    }
}

/** Utilidades para incorporar archivos de elevación SRTM (.hgt) de forma segura. */
object DemFiles {
    /** Tamaños válidos de un .hgt: SRTM3 (1201²) y SRTM1 (3601²) muestras de 2 bytes. */
    val VALID_SIZES = setOf(1201L * 1201 * 2, 3601L * 3601 * 2)

    fun isValidHgtSize(size: Long) = size in VALID_SIZES

    data class ExtractResult(val installed: List<DemTile>, val skipped: Int)

    /**
     * Extrae las teselas .hgt válidas de un ZIP a [targetDir]. Ignora rutas internas
     * (evita «zip slip»), nombres no reconocidos y tamaños incorrectos.
     */
    fun extractZip(input: InputStream, targetDir: File, maxEntries: Int = 400): ExtractResult {
        targetDir.mkdirs()
        val installed = ArrayList<DemTile>()
        var skipped = 0
        ZipInputStream(input.buffered()).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > maxEntries) break
                val tile = if (!entry.isDirectory && entry.name.endsWith(".hgt", true)) DemTile.parse(entry.name) else null
                if (tile == null) {
                    if (!entry.isDirectory) skipped++
                    continue
                }
                val target = File(targetDir, tile.fileName)
                val partial = File(targetDir, tile.fileName + ".parcial")
                var written = 0L
                val maxSize = VALID_SIZES.max()
                var tooBig = false
                partial.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > maxSize) { tooBig = true; break }
                        out.write(buf, 0, n)
                    }
                }
                if (!tooBig && isValidHgtSize(written)) {
                    target.delete()
                    if (partial.renameTo(target)) installed.add(tile) else { partial.delete(); skipped++ }
                } else {
                    partial.delete()
                    skipped++
                }
            }
        }
        return ExtractResult(installed, skipped)
    }

    /** Teselas instaladas en una carpeta. */
    fun installed(dir: File): List<DemTile> =
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".hgt", true) && isValidHgtSize(it.length()) }
            ?.mapNotNull { DemTile.parse(it.name) }?.sortedWith(compareBy({ it.south }, { it.west })).orEmpty()

    /** Teselas necesarias para cubrir un recorrido y cuáles faltan. */
    fun missingFor(route: List<GeoPoint>, installed: Collection<DemTile>): List<DemTile> {
        val have = installed.toSet()
        return route.map { DemTile.containing(it) }.distinct().filter { it !in have }
    }
}
