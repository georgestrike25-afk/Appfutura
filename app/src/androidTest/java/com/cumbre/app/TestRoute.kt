package com.cumbre.app

import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import com.cumbre.app.data.PrepareResult
import java.io.File
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sin

/**
 * Ruta sintética para las pruebas en emulador. Es la misma geometría que recorre el script
 * `scripts/emulador-gps.py` con `adb emu geo fix`, para simular caminar por la ruta.
 * No es una ruta real: se nombra «Ruta de prueba» en todas las capturas.
 */
object TestRoute {
    const val NAME = "Ruta de prueba"
    const val POINTS = 400

    fun lat(i: Int) = -31.3750 + i * 0.00009
    fun lon(i: Int) = -64.6000 + 0.0006 * sin(i / 40.0)
    fun ele(i: Int) = 820.0 + 310.0 * exp(-((i - 220.0) / 110.0).let { it * it })

    fun gpx(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<gpx version="1.1" creator="Cumbre pruebas" xmlns="http://www.topografix.com/GPX/1/1">""")
        append("<metadata><name>$NAME</name></metadata>")
        append("""<wpt lat="${f(lat(220))}" lon="${f(lon(220))}"><name>Mirador</name></wpt>""")
        append("<trk><name>$NAME</name><trkseg>")
        for (i in 0 until POINTS) append("""<trkpt lat="${f(lat(i))}" lon="${f(lon(i))}"><ele>${"%.1f".format(Locale.ROOT, ele(i))}</ele></trkpt>""")
        append("</trkseg></trk></gpx>")
    }

    private fun f(v: Double) = "%.6f".format(Locale.ROOT, v)

    /** Importa la ruta de prueba (o devuelve la existente) como lo haría el usuario. */
    suspend fun ensureImported(container: AppContainer, cacheDir: File): Long {
        val file = File(cacheDir, "ruta-de-prueba.gpx").apply { writeText(gpx()) }
        return when (val r = container.routes.prepareFromFile(file, file.name, file.length())) {
            is PrepareResult.Ready -> r.pending.duplicateOf?.id ?: container.routes.confirmImport(r.pending, NAME, null)
            is PrepareResult.Failed -> error("No se pudo preparar la ruta de prueba: ${r.message}")
        }
    }
}

/** Ejecuta un comando de shell con permisos de la instrumentación y devuelve su salida. */
fun Instrumentation.shell(command: String): String {
    val pfd = uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
}
