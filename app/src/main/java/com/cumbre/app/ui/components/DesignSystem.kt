package com.cumbre.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cumbre.app.ui.theme.CumbreColors
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.app.ui.theme.Inter
import com.cumbre.app.ui.theme.Spacing
import com.cumbre.core.analysis.Difficulty
import com.cumbre.core.analysis.ProfileFeatures
import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.format.Formatters
import kotlin.math.max

/** Variantes de la silueta del perfil, la firma visual de Cumbre. */
enum class RidgeStyle { HERO, STRIP, MINI }

/**
 * Silueta del perfil de elevación de una ruta. En [RidgeStyle.HERO] marca la cumbre con su altitud.
 * Si se indica [progressFraction], la parte recorrida se pinta distinta y se marca la posición.
 */
@Composable
fun RidgeSilhouette(
    profile: List<ProfilePoint>,
    modifier: Modifier = Modifier,
    style: RidgeStyle = RidgeStyle.STRIP,
    progressFraction: Float? = null,
    summitCaption: String? = null,
    dimmed: Boolean = false,
) {
    if (profile.size < 2) return
    val dark = CumbreTheme.isDark
    val measurer = rememberTextMeasurer()
    val lo = remember(profile) { profile.minOf { it.elevationMeters } }
    val hi = remember(profile) { profile.maxOf { it.elevationMeters } }
    val top = remember(profile) { ProfileFeatures.highest(profile) }
    val total = profile.last().distanceMeters
    val stroke = when (style) {
        RidgeStyle.HERO -> CumbreColors.VerdeSuave
        RidgeStyle.STRIP -> if (dark) Color(0xFF3F6F5E) else Color(0xFF9CC3AE)
        RidgeStyle.MINI -> MaterialTheme.colorScheme.primary
    }
    val fillTop = when (style) {
        RidgeStyle.HERO -> if (dark) Color(0xFF2E6B57) else Color(0xFF2A7A5E)
        RidgeStyle.STRIP -> if (dark) Color(0xFF1C3A31) else Color(0xFFE4EFE9)
        RidgeStyle.MINI -> if (dark) Color(0xFF24493F) else Color(0xFFCFE2D7)
    }
    val fillBottom = when (style) {
        RidgeStyle.HERO -> if (dark) Color(0xFF16302B) else CumbreColors.VerdeMontana
        else -> fillTop
    }
    val progressColor = if (dimmed) Color(0xFF8A9792) else CumbreTheme.extra.recorded
    val summitColor = if (style == RidgeStyle.HERO) CumbreColors.Naranja else MaterialTheme.colorScheme.onSurface
    val captionColor = if (style == RidgeStyle.HERO) Color.White else MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val padTop = when (style) {
            RidgeStyle.HERO -> 26.dp.toPx()
            RidgeStyle.STRIP -> 4.dp.toPx()
            RidgeStyle.MINI -> 6.dp.toPx()
        } + if (progressFraction != null) 14.dp.toPx() else 0f
        val bottom = size.height
        val span = max(hi - lo, 20.0)
        fun x(d: Double) = (d / max(total, 1.0) * size.width).toFloat()
        fun y(e: Double) = (bottom - (e - lo) / span * (bottom - padTop)).toFloat()
        val line = Path().apply {
            profile.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.distanceMeters), y(p.elevationMeters)) else lineTo(x(p.distanceMeters), y(p.elevationMeters)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, bottom)
            lineTo(0f, bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(fillTop, fillBottom), startY = padTop, endY = bottom))
        drawPath(line, stroke, style = Stroke(width = (if (style == RidgeStyle.HERO) 2f else 1.6f).dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
        if (progressFraction != null) {
            val cut = (progressFraction.coerceIn(0f, 1f) * total)
            val done = Path()
            var started = false
            for (p in profile) {
                if (p.distanceMeters > cut) break
                if (!started) { done.moveTo(x(p.distanceMeters), y(p.elevationMeters)); started = true } else done.lineTo(x(p.distanceMeters), y(p.elevationMeters))
            }
            val e = ProfileFeatures.elevationAt(profile, cut) ?: lo
            if (started) {
                done.lineTo(x(cut), y(e))
                drawPath(done, progressColor, style = Stroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
            }
            drawCircle(Color.White, 8.dp.toPx(), Offset(x(cut), y(e)))
            drawCircle(if (dimmed) Color(0xFF8A9792) else CumbreColors.Naranja, 5.dp.toPx(), Offset(x(cut), y(e)))
        }
        if (top != null && style != RidgeStyle.MINI) {
            val sx = x(top.distanceMeters)
            val sy = y(top.elevationMeters)
            val tri = Path().apply {
                moveTo(sx, sy - 13.dp.toPx())
                lineTo(sx + 7.dp.toPx(), sy - 3.dp.toPx())
                lineTo(sx - 7.dp.toPx(), sy - 3.dp.toPx())
                close()
            }
            if (style == RidgeStyle.HERO || progressFraction != null) drawPath(tri, summitColor)
            val caption = summitCaption ?: if (style == RidgeStyle.HERO) Formatters.elevation(top.elevationMeters) else null
            if (caption != null) {
                val ts = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = captionColor)
                val layout = measurer.measure(caption, ts)
                val tx = (sx + 10.dp.toPx()).coerceAtMost(size.width - layout.size.width - 2f)
                drawText(layout, topLeft = Offset(if (tx < sx) sx - layout.size.width - 10.dp.toPx() else tx, sy - 15.dp.toPx()))
            }
        }
    }
}

enum class TrackingPillState { RECORDING, PAUSED, FINISHED }

/** Estado de la actividad, siempre visible arriba en la navegación. */
@Composable
fun StatusPill(state: TrackingPillState, modifier: Modifier = Modifier, text: String? = null) {
    val extra = CumbreTheme.extra
    val (bg, fg, label) = when (state) {
        TrackingPillState.RECORDING -> Triple(extra.mapControl, Color(0xFFB3261E), "Grabando")
        TrackingPillState.PAUSED -> Triple(extra.warningContainer, extra.onWarningContainer, "En pausa")
        TrackingPillState.FINISHED -> Triple(extra.successContainer, extra.onSuccessContainer, "Recorrido finalizado")
    }
    Surface(shape = CircleShape, color = bg, shadowElevation = 4.dp, modifier = modifier) {
        Row(Modifier.height(36.dp).padding(start = 12.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                TrackingPillState.RECORDING -> Box(
                    Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFD93025)),
                )
                TrackingPillState.PAUSED -> Icon(Icons.Rounded.PauseCircle, null, tint = fg, modifier = Modifier.size(18.dp))
                TrackingPillState.FINISHED -> Icon(Icons.Rounded.CheckCircle, null, tint = fg, modifier = Modifier.size(18.dp))
            }
            Text(text ?: label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp), color = fg)
        }
    }
}

/** Etiqueta de dificultad estimada, con barras como indicador. */
@Composable
fun DifficultyTag(difficulty: Difficulty?, modifier: Modifier = Modifier) {
    difficulty ?: return
    val icon = when (difficulty) {
        Difficulty.EASY -> Icons.Rounded.SignalCellularAlt1Bar
        Difficulty.MODERATE -> Icons.Rounded.SignalCellularAlt2Bar
        Difficulty.HARD, Difficulty.VERY_HARD -> Icons.Rounded.SignalCellularAlt
    }
    val dark = CumbreTheme.isDark
    Row(
        modifier.clip(MaterialTheme.shapes.extraSmall).background(if (dark) Color(0xFF3A2F1C) else Color(0xFFF1ECE2)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) Color(0xFFE8C88A) else Color(0xFF6B4E1E), modifier = Modifier.size(14.dp))
        Text(difficulty.label, style = MaterialTheme.typography.labelSmall, color = if (dark) Color(0xFFE8C88A) else Color(0xFF6B4E1E))
    }
}

/** Cápsula de estado sobre el mapa (GPS, conexión, mapa offline). */
@Composable
fun MapCapsule(icon: ImageVector, text: String, modifier: Modifier = Modifier, iconTint: Color = CumbreTheme.extra.onMapControl, onClick: (() -> Unit)? = null) {
    Surface(shape = CircleShape, color = CumbreTheme.extra.mapControl, shadowElevation = 4.dp, modifier = modifier) {
        Row(
            Modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it }.height(40.dp).padding(start = 11.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = CumbreTheme.extra.onMapControl, maxLines = 1)
        }
    }
}

/** Acceso rápido de la pantalla de inicio. */
@Composable
fun QuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    CumbreCard(modifier, onClick = onClick) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            IconBadge(icon, iconContainer, iconTint, Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp), maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

data class SegmentOption<T>(val value: T, val label: String, val icon: ImageVector? = null)

/** Selector segmentado (por ejemplo, tema Sistema / Claro / Oscuro). */
@Composable
fun <T> SegmentedChoice(options: List<SegmentOption<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o.value == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(o.value) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                o.icon?.let { Icon(it, null, tint = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
                Text(
                    o.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                    color = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (o.icon != null) 6.dp else 0.dp),
                )
            }
        }
    }
}

/** Encabezado de sección con acción opcional a la derecha. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp))
        }
    }
}
