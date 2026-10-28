package com.cumbre.core.maps

/** Tipo de archivo recibido con «Abrir con» o «Compartir». */
enum class IncomingFileKind { GPX, MAP, RELIEF, UNKNOWN }

/**
 * Identifica un archivo por su contenido (cabecera) y, como respaldo, por su nombre.
 * Muchas apps entregan los archivos como «application/octet-stream», así que el tipo MIME
 * no alcanza para saber qué son.
 */
object IncomingFileDetector {
    private const val MAPSFORGE_MAGIC = "mapsforge binary OSM"

    /**
     * @param name nombre visible del archivo (puede faltar).
     * @param header primeros bytes del archivo (al menos 64).
     * @param zipEntryNames nombres de las primeras entradas, si el archivo es un ZIP.
     */
    fun detect(name: String?, header: ByteArray, zipEntryNames: List<String> = emptyList()): IncomingFileKind {
        val lower = name?.lowercase().orEmpty()
        val text = String(header, Charsets.ISO_8859_1)
        return when {
            text.startsWith(MAPSFORGE_MAGIC) -> IncomingFileKind.MAP
            isZip(header) -> when {
                zipEntryNames.any { it.endsWith(".hgt", true) && DemTile.parse(it) != null } -> IncomingFileKind.RELIEF
                zipEntryNames.any { it.endsWith(".gpx", true) } -> IncomingFileKind.UNKNOWN
                else -> IncomingFileKind.UNKNOWN
            }
            lower.endsWith(".hgt") && DemTile.parse(lower) != null -> IncomingFileKind.RELIEF
            looksLikeXml(text) -> IncomingFileKind.GPX
            lower.endsWith(".gpx") -> IncomingFileKind.GPX
            lower.endsWith(".map") -> IncomingFileKind.MAP
            else -> IncomingFileKind.UNKNOWN
        }
    }

    fun isZip(header: ByteArray): Boolean =
        header.size >= 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            header[2] == 3.toByte() && header[3] == 4.toByte()

    private fun looksLikeXml(text: String): Boolean {
        val t = text.trimStart('﻿', 'ï', '»', '¿', ' ', '\n', '\r', '\t')
        return t.startsWith("<?xml") || t.startsWith("<gpx", ignoreCase = true)
    }
}
