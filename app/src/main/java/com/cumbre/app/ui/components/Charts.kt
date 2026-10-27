package com.cumbre.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.Inter
import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.format.Formatters
import com.cumbre.core.geo.GeoPoint
import kotlin.math.cos
import kotlin.math.max

/**
 * Perfil de elevación dibujado a partir de los datos reales del track. Muestra altitud
 * mínima y máxima y, opcionalmente, la posición actual sobre el recorrido.
 */
@Composable
fun ElevationProfileChart(
    profile: List<ProfilePoint>,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    markerDistanceMeters: Double? = null,
    warningDistanceMeters: Double? = null,
) {
    if (profile.size < 2) return
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val marker = CumbreTheme.extra.position
    val warning = CumbreTheme.extra.warning
    val measurer = rememberTextMeasurer()
    val minE = remember(profile) { profile.minOf { it.elevationMeters } }
    val maxE = remember(profile) { profile.maxOf { it.elevationMeters } }
    val total = profile.last().distanceMeters
    val description = "Perfil de elevación: de ${Formatters.elevation(minE)} a ${Formatters.elevation(maxE)} en ${Formatters.distance(total)}"
    Box(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val labelStyle = TextStyle(fontFamily = Inter, fontSize = 9.5.sp, color = label)
            val top = 14.dp.toPx()
            val bottom = size.height - 14.dp.toPx()
            val left = 0f
            val right = size.width
            val range = max(maxE - minE, 20.0)
            val base = minE - range * 0.05
            val span = range * 1.1
            fun px(d: Double) = (left + (d / max(total, 1.0)) * (right - left)).toFloat()
            fun py(e: Double) = (bottom - ((e - base) / span) * (bottom - top)).toFloat()

            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawLine(grid, Offset(left, py(maxE)), Offset(right, py(maxE)), 1.dp.toPx(), pathEffect = dash)
            drawLine(grid, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())

            val path = Path()
            profile.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(px(p.distanceMeters), py(p.elevationMeters)) else path.lineTo(px(p.distanceMeters), py(p.elevationMeters))
            }
            val area = Path().apply {
                addPath(path)
                lineTo(px(profile.last().distanceMeters), bottom)
                lineTo(px(profile.first().distanceMeters), bottom)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.40f), line.copy(alpha = 0.04f)), startY = top, endY = bottom))
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            warningDistanceMeters?.let { d ->
                drawLine(warning, Offset(px(d), top), Offset(px(d), bottom), 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            }
            markerDistanceMeters?.let { d ->
                val x = px(d.coerceIn(0.0, total))
                val e = elevationAt(profile, d)
                drawLine(marker, Offset(x, top), Offset(x, bottom), 1.5.dp.toPx())
                drawCircle(marker, 5.dp.toPx(), Offset(x, py(e)))
            }
            drawLabel(measurer, Formatters.elevation(maxE), labelStyle, Offset(2f, 0f))
            drawLabel(measurer, Formatters.elevation(minE), labelStyle, Offset(2f, bottom + 1.dp.toPx()))
            val totalText = Formatters.distance(total)
            val w = measurer.measure(totalText, labelStyle).size.width
            drawLabel(measurer, totalText, labelStyle, Offset(right - w - 2f, bottom + 1.dp.toPx()))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(m: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    drawText(m, text, topLeft = at, style = style)
}

private fun elevationAt(profile: List<ProfilePoint>, d: Double): Double {
    val idx = profile.indexOfFirst { it.distanceMeters >= d }
    if (idx <= 0) return profile.first().elevationMeters
    val a = profile[idx - 1]
    val b = profile[idx]
    val t = if (b.distanceMeters == a.distanceMeters) 0.0 else (d - a.distanceMeters) / (b.distanceMeters - a.distanceMeters)
    return a.elevationMeters + t * (b.elevationMeters - a.elevationMeters)
}

/** Miniatura del recorrido dibujada a partir de la geometría real (no es una imagen de mapa). */
@Composable
fun TrackThumbnail(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val contour = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val track = CumbreTheme.extra.track
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        drawRect(bg)
        // Curvas decorativas tenues que evocan un mapa topográfico.
        for (i in 1..3) {
            drawCircle(contour, radius = size.minDimension * 0.18f * i, center = Offset(size.width * 0.68f, size.height * 0.3f), style = Stroke(0.8.dp.toPx()))
        }
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val k = cos(Math.toRadians((minLat + maxLat) / 2))
        val w = max((maxLon - minLon) * k, 1e-9)
        val h = max(maxLat - minLat, 1e-9)
        val pad = 8.dp.toPx()
        val scale = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        val ox = (size.width - w * scale) / 2
        val oy = (size.height - h * scale) / 2
        fun o(p: GeoPoint) = Offset(
            (ox + (p.longitude - minLon) * k * scale).toFloat(),
            (oy + (maxLat - p.latitude) * scale).toFloat(),
        )
        val path = Path()
        points.forEachIndexed { i, p -> val q = o(p); if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }
        drawPath(path, track, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(androidx.compose.ui.graphics.Color.White, 4.5.dp.toPx(), o(points.first()))
        drawCircle(start, 3.dp.toPx(), o(points.first()))
        drawCircle(androidx.compose.ui.graphics.Color.White, 4.5.dp.toPx(), o(points.last()))
        drawCircle(end, 3.dp.toPx(), o(points.last()))
    }
}
