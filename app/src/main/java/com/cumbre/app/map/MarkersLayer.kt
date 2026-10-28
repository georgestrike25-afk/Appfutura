package com.cumbre.app.map

import android.graphics.Paint
import android.graphics.Path
import com.cumbre.core.geo.GeoPoint
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import kotlin.math.hypot

data class MapWaypoint(val name: String?, val point: GeoPoint)

data class MapPosition(
    val point: GeoPoint,
    val accuracyMeters: Double?,
    val bearingDegrees: Double?,
    /** La señal se perdió: se muestra el último dato válido atenuado. */
    val stale: Boolean,
)

/**
 * Capa de marcadores dibujada con el Canvas de Android: inicio, final, waypoints y posición GPS
 * con su círculo de precisión. Se dibuja en coordenadas del mapa, por lo que acompaña la rotación.
 */
class MarkersLayer(private val density: Float) : Layer() {
    @Volatile var start: GeoPoint? = null
    @Volatile var end: GeoPoint? = null
    @Volatile var waypoints: List<MapWaypoint> = emptyList()
    @Volatile var position: MapPosition? = null
    @Volatile var highlighted: GeoPoint? = null
    var onWaypointTap: ((MapWaypoint) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.5f * density; isFakeBoldText = true }
    private val labelHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12.5f * density; isFakeBoldText = true; style = Paint.Style.STROKE; strokeWidth = 3.5f * density
        strokeJoin = Paint.Join.ROUND
    }

    override fun draw(boundingBox: BoundingBox, zoomLevel: Byte, canvas: Canvas, topLeftPoint: Point, rotation: Rotation) {
        val c = AndroidGraphicFactory.getCanvas(canvas)
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)
        fun x(p: GeoPoint) = (MercatorProjection.longitudeToPixelX(p.longitude, mapSize) - topLeftPoint.x).toFloat()
        fun y(p: GeoPoint) = (MercatorProjection.latitudeToPixelY(p.latitude, mapSize) - topLeftPoint.y).toFloat()
        val d = density

        for (w in waypoints) {
            val cx = x(w.point); val cy = y(w.point)
            fill.color = WHITE; c.drawCircle(cx, cy, 10 * d, fill)
            stroke.color = MOUNTAIN; stroke.strokeWidth = 2 * d; c.drawCircle(cx, cy, 10 * d, stroke)
            drawFlag(c, cx, cy, MOUNTAIN, d)
            // Nombre del waypoint junto al marcador, legible sobre cualquier fondo.
            val name = w.name
            if (!name.isNullOrBlank() && zoomLevel >= LABEL_MIN_ZOOM) {
                val text = if (name.length > 32) name.take(31) + "…" else name
                labelHalo.color = WHITE
                label.color = MOUNTAIN
                c.drawText(text, cx + 14 * d, cy + 4.5f * d, labelHalo)
                c.drawText(text, cx + 14 * d, cy + 4.5f * d, label)
            }
        }
        start?.let { s ->
            val cx = x(s); val cy = y(s)
            fill.color = WHITE; c.drawCircle(cx, cy, 10 * d, fill)
            fill.color = FOREST; c.drawCircle(cx, cy, 7 * d, fill)
        }
        end?.let { e ->
            val cx = x(e); val cy = y(e)
            fill.color = WHITE; c.drawCircle(cx, cy, 11 * d, fill)
            fill.color = DARK; c.drawCircle(cx, cy, 8.5f * d, fill)
            drawFlag(c, cx, cy, WHITE, d * 0.85f)
        }
        highlighted?.let { h ->
            stroke.color = ORANGE; stroke.strokeWidth = 3 * d
            c.drawCircle(x(h), y(h), 16 * d, stroke)
        }
        position?.let { pos ->
            val cx = x(pos.point); val cy = y(pos.point)
            val alpha = if (pos.stale) 110 else 255
            pos.accuracyMeters?.let { acc ->
                val res = MercatorProjection.calculateGroundResolution(pos.point.latitude, mapSize)
                val r = (acc / res).toFloat()
                if (r > 12 * d) {
                    fill.color = ORANGE; fill.alpha = 40; c.drawCircle(cx, cy, r, fill)
                    stroke.color = ORANGE; stroke.alpha = 90; stroke.strokeWidth = 1 * d; c.drawCircle(cx, cy, r, stroke)
                    fill.alpha = 255; stroke.alpha = 255
                }
            }
            val bearing = pos.bearingDegrees
            if (bearing != null && !pos.stale) {
                c.save()
                c.rotate(bearing.toFloat(), cx, cy)
                path.reset()
                path.moveTo(cx, cy - 15 * d)
                path.lineTo(cx + 10 * d, cy + 10 * d)
                path.lineTo(cx, cy + 5 * d)
                path.lineTo(cx - 10 * d, cy + 10 * d)
                path.close()
                fill.color = ORANGE; fill.alpha = alpha; c.drawPath(path, fill)
                stroke.color = WHITE; stroke.alpha = alpha; stroke.strokeWidth = 2.5f * d; stroke.strokeJoin = Paint.Join.ROUND
                c.drawPath(path, stroke)
                c.restore()
            } else {
                fill.color = WHITE; fill.alpha = alpha; c.drawCircle(cx, cy, 11 * d, fill)
                fill.color = if (pos.stale) GREY else ORANGE; fill.alpha = alpha; c.drawCircle(cx, cy, 8 * d, fill)
            }
            fill.alpha = 255; stroke.alpha = 255
        }
    }

    private fun drawFlag(c: android.graphics.Canvas, cx: Float, cy: Float, color: Int, d: Float) {
        stroke.color = color; stroke.strokeWidth = 1.8f * d; stroke.strokeCap = Paint.Cap.ROUND
        c.drawLine(cx - 3 * d, cy - 5 * d, cx - 3 * d, cy + 5.5f * d, stroke)
        path.reset()
        path.moveTo(cx - 3 * d, cy - 5 * d)
        path.lineTo(cx + 5 * d, cy - 2.5f * d)
        path.lineTo(cx - 3 * d, cy + 0.5f * d)
        path.close()
        fill.color = color; c.drawPath(path, fill)
    }

    // Mapsforge pasa layerXY = null para capas sin posición propia (como esta): debe ser anulable,
    // si no Kotlin lanza NullPointerException al tocar el mapa.
    override fun onTap(tapLatLong: LatLong?, layerXY: Point?, tapXY: Point?): Boolean {
        if (tapLatLong == null) return false
        val listener = onWaypointTap ?: return false
        val model = displayModel ?: return false
        val zoom = mapViewPositionZoom ?: return false
        val mapSize = MercatorProjection.getMapSize(zoom, model.tileSize)
        val tx = MercatorProjection.longitudeToPixelX(tapLatLong.longitude, mapSize)
        val ty = MercatorProjection.latitudeToPixelY(tapLatLong.latitude, mapSize)
        val hit = waypoints.minByOrNull { w ->
            hypot(MercatorProjection.longitudeToPixelX(w.point.longitude, mapSize) - tx, MercatorProjection.latitudeToPixelY(w.point.latitude, mapSize) - ty)
        } ?: return false
        val dist = hypot(MercatorProjection.longitudeToPixelX(hit.point.longitude, mapSize) - tx, MercatorProjection.latitudeToPixelY(hit.point.latitude, mapSize) - ty)
        if (dist > 28 * density) return false
        listener(hit)
        return true
    }

    /** Nivel de zoom actual, lo informa el controlador del mapa. */
    @Volatile var mapViewPositionZoom: Byte? = null

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val MOUNTAIN = 0xFF0F403A.toInt()
        private const val FOREST = 0xFF267D5B.toInt()
        private const val ORANGE = 0xFFFF8A00.toInt()
        private const val DARK = 0xFF1E1E1E.toInt()
        private const val GREY = 0xFF8A9590.toInt()
        private const val LABEL_MIN_ZOOM: Byte = 12
    }
}
