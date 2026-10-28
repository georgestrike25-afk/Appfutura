package com.cumbre.core.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formato de unidades en español de Argentina (coma decimal, punto de miles). */
object Formatters {
    private val locale = Locale.forLanguageTag("es-AR")
    private val symbols = DecimalFormatSymbols(locale).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }

    private fun pattern(p: String) = DecimalFormat(p, symbols)

    /** "850 m", "18,4 km", "125 km". */
    fun distance(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToLong()} m"
        meters < 100_000 -> "${pattern("#,##0.0").format(meters / 1000)} km"
        else -> "${pattern("#,##0").format(meters / 1000)} km"
    }

    /** Valor y unidad por separado, para mostrar la unidad más chica: ("15,0", "km") o ("850", "m"). */
    fun distanceParts(meters: Double): Pair<String, String> = when {
        meters < 1000 -> meters.roundToLong().toString() to "m"
        meters < 100_000 -> pattern("#,##0.0").format(meters / 1000) to "km"
        else -> pattern("#,##0").format(meters / 1000) to "km"
    }

    /** Duración compacta para datos: "6 h 16", "45 min". */
    fun durationCompact(seconds: Long): String {
        val totalMinutes = (seconds + 30) / 60
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h == 0L) "$m min" else "$h h %02d".format(m)
    }

    /** Distancia en km con dos decimales para el panel de navegación: "7,82". */
    fun kilometersValue(meters: Double): String =
        if (meters < 10_000) pattern("0.00").format(meters / 1000) else pattern("#,##0.0").format(meters / 1000)

    /** "1.142 m". */
    fun elevation(meters: Double): String = "${pattern("#,##0").format(meters.roundToLong())} m"

    fun elevationValue(meters: Double): String = pattern("#,##0").format(meters.roundToLong())

    /** "7 h 20 min", "45 min", "0 min". */
    fun duration(seconds: Long): String {
        val totalMinutes = (seconds + 30) / 60
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** Reloj "2:51:08" o "51:08". */
    fun clock(millis: Long): String {
        val s = millis / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** Velocidad en km/h con un decimal: "3,4 km/h". */
    fun speedKmh(mps: Double): String = "${pattern("0.0").format(mps * 3.6)} km/h"

    fun speedKmhValue(mps: Double): String = pattern("0.0").format(mps * 3.6)

    /** Velocidad en m/s: "0,9 m/s". */
    fun speedMps(mps: Double): String = "${pattern("0.0").format(mps)} m/s"

    /** Precisión GPS: "±4 m". */
    fun accuracy(meters: Double): String = "±${meters.roundToLong()} m"

    fun bytes(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${pattern("#,##0").format(size / 1024.0)} KB"
        size < 1024L * 1024 * 1024 -> "${pattern("#,##0").format(size / (1024.0 * 1024))} MB"
        else -> "${pattern("#,##0.0").format(size / (1024.0 * 1024 * 1024))} GB"
    }

    fun signedElevation(meters: Double): String =
        (if (meters >= 0) "+" else "−") + elevation(abs(meters))

    fun count(n: Int): String = pattern("#,##0").format(n)
}
