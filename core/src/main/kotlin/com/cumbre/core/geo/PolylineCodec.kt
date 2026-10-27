package com.cumbre.core.geo

import kotlin.math.roundToLong

/**
 * Codificación compacta de polilíneas (algoritmo de polilínea codificada, precisión 1e-5),
 * usada para guardar miniaturas de tracks en la base de datos.
 */
object PolylineCodec {
    private const val FACTOR = 1e5

    fun encode(points: List<GeoPoint>): String {
        val sb = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.latitude * FACTOR).roundToLong()
            val lon = (p.longitude * FACTOR).roundToLong()
            encodeValue(lat - lastLat, sb)
            encodeValue(lon - lastLon, sb)
            lastLat = lat
            lastLon = lon
        }
        return sb.toString()
    }

    fun decode(encoded: String): List<GeoPoint> {
        val result = ArrayList<GeoPoint>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, next1) = decodeValue(encoded, index) ?: break
            val (dLon, next2) = decodeValue(encoded, next1) ?: break
            index = next2
            lat += dLat
            lon += dLon
            result.add(GeoPoint(lat / FACTOR, lon / FACTOR))
        }
        return result
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }

    private fun decodeValue(s: String, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            if (i >= s.length) return null
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to i
    }
}
