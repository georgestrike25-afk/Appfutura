package com.cumbre.core.analysis

import com.cumbre.core.gpx.GpxDocument
import java.security.MessageDigest
import kotlin.math.roundToLong

/**
 * Huella de la geometría de una ruta. Dos archivos con los mismos puntos (aunque cambie
 * el nombre, el formato o los espacios del XML) producen la misma huella, lo que permite
 * detectar importaciones duplicadas.
 */
object RouteFingerprint {
    fun of(document: GpxDocument): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = java.nio.ByteBuffer.allocate(16)
        val lines = document.lines()
        for ((i, line) in lines.withIndex()) {
            buffer.clear()
            buffer.putLong(-1L - i)
            buffer.putLong(line.size.toLong())
            digest.update(buffer.array(), 0, 16)
            for (p in line) {
                buffer.clear()
                buffer.putLong((p.latitude * 1e6).roundToLong())
                buffer.putLong((p.longitude * 1e6).roundToLong())
                digest.update(buffer.array(), 0, 16)
            }
        }
        for (w in document.waypoints) {
            buffer.clear()
            buffer.putLong((w.latitude * 1e6).roundToLong())
            buffer.putLong((w.longitude * 1e6).roundToLong())
            digest.update(buffer.array(), 0, 16)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
