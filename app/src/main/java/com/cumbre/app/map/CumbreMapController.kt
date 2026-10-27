package com.cumbre.app.map

import android.content.Context
import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoPoint
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Join
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Dimension
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition as MfMapPosition
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File

enum class MapTheme(val theme: XmlRenderTheme) {
    DEFAULT(MapsforgeThemes.DEFAULT),
    OSMARENDER(MapsforgeThemes.OSMARENDER),
}

/**
 * Envoltura del [MapView] de Mapsforge: renderiza mapas vectoriales locales (.map) sin conexión
 * y dibuja encima el track, el recorrido grabado y los marcadores. Si no hay mapas instalados,
 * se ve solo el fondo liso con el track: nunca se simula cartografía.
 */
class CumbreMapController(context: Context, dark: Boolean) {
    private val density = context.resources.displayMetrics.density
    val mapView: MapView = MapView(context)
    private val tileCache: TileCache
    private var tileLayer: TileRendererLayer? = null
    private var dataStore: MultiMapDataStore? = null
    private var mapKey: String = ""

    private val routeLayers = ArrayList<Polyline>()
    private val recordedLayers = ArrayList<Polyline>()
    private val markers = MarkersLayer(density)
    private var routeKey: Any? = null
    private var recordedSize = -1
    private var dark = dark

    /** Mapas que no se pudieron abrir en el último [setMapFiles]. */
    var failedMaps: List<File> = emptyList()
        private set

    var onWaypointTap: ((MapWaypoint) -> Unit)?
        get() = markers.onWaypointTap
        set(value) { markers.onWaypointTap = value }

    init {
        mapView.isClickable = true
        mapView.setBuiltInZoomControls(false)
        mapView.mapScaleBar.isVisible = true
        mapView.mapScaleBar.setMarginVertical((8 * density).toInt())
        mapView.setZoomLevelMin(3)
        mapView.setZoomLevelMax(20)
        applyBackground()
        tileCache = AndroidUtil.createTileCache(
            context,
            "cumbre-${System.identityHashCode(this)}",
            mapView.model.displayModel.tileSize,
            1f,
            mapView.model.frameBufferModel.overdrawFactor,
        )
        mapView.layerManager.layers.add(markers)
        mapView.model.mapViewPosition.addObserver { markers.mapViewPositionZoom = mapView.model.mapViewPosition.zoomLevel }
        markers.mapViewPositionZoom = mapView.model.mapViewPosition.zoomLevel
        // Vista inicial: Argentina.
        mapView.model.mapViewPosition.mapPosition = MfMapPosition(LatLong(-38.4, -63.6), 4.toByte())
    }

    private fun applyBackground() {
        mapView.model.displayModel.setBackgroundColor(if (dark) 0xFF1A2321.toInt() else 0xFFF2F1E8.toInt())
    }

    fun setDark(dark: Boolean) {
        if (this.dark == dark) return
        this.dark = dark
        applyBackground()
        routeKey = null
        mapView.layerManager.redrawLayers()
    }

    /** Carga los mapas indicados (combinados) con el tema elegido. Devuelve true si hay cartografía. */
    fun setMapFiles(files: List<File>, theme: MapTheme): Boolean {
        val key = files.joinToString("|") { it.absolutePath + ":" + it.length() } + "#" + theme.name
        if (key == mapKey) return tileLayer != null
        mapKey = key
        tileLayer?.let {
            mapView.layerManager.layers.remove(it)
            it.onDestroy()
        }
        tileLayer = null
        dataStore?.close()
        dataStore = null
        tileCache.purge()
        val failed = ArrayList<File>()
        if (files.isNotEmpty()) {
            val store = MultiMapDataStore(MultiMapDataStore.DataPolicy.DEDUPLICATE)
            var added = 0
            for (f in files) {
                try {
                    store.addMapDataStore(MapFile(f), false, false)
                    added++
                } catch (e: Exception) {
                    failed.add(f)
                }
            }
            if (added > 0) {
                dataStore = store
                val layer = AndroidUtil.createTileRendererLayer(
                    tileCache, mapView.model.mapViewPosition, store, theme.theme, false, true, false,
                )
                tileLayer = layer
                mapView.layerManager.layers.add(0, layer)
            } else {
                store.close()
            }
        }
        failedMaps = failed
        return tileLayer != null
    }

    val hasMap: Boolean get() = tileLayer != null

    fun setRoute(lines: List<List<GeoPoint>>, waypoints: List<MapWaypoint>, trackColor: Int, haloColor: Int) {
        val key = listOf(System.identityHashCode(lines), lines.size, waypoints.size, trackColor, haloColor)
        if (key == routeKey) return
        routeKey = key
        val layers = mapView.layerManager.layers
        routeLayers.forEach { layers.remove(it, false) }
        routeLayers.clear()
        val insertAt = layers.indexOf(markers).coerceAtLeast(0).let { idx -> minOf(idx, layers.size()) }
        val created = ArrayList<Polyline>()
        for (line in lines) {
            if (line.size < 2) continue
            val pts = line.map { LatLong(it.latitude, it.longitude) }
            created.add(polyline(haloColor, 8.5f, pts, alpha = 0.55f))
            created.add(polyline(trackColor, 5f, pts))
        }
        // Se insertan debajo del recorrido grabado y de los marcadores.
        val recordedIdx = recordedLayers.firstOrNull()?.let { layers.indexOf(it) }?.takeIf { it >= 0 }
        layers.addAll(recordedIdx ?: insertAt, ArrayList<Layer>(created), false)
        routeLayers.addAll(created)
        markers.start = lines.firstOrNull { it.isNotEmpty() }?.first()
        markers.end = lines.lastOrNull { it.isNotEmpty() }?.last()
        markers.waypoints = waypoints
        mapView.layerManager.redrawLayers()
    }

    fun clearRoute() = setRoute(emptyList(), emptyList(), 0, 0)

    fun setRecorded(lines: List<List<GeoPoint>>, color: Int) {
        val total = lines.sumOf { it.size }
        if (total == recordedSize) return
        recordedSize = total
        val layers = mapView.layerManager.layers
        recordedLayers.forEach { layers.remove(it, false) }
        recordedLayers.clear()
        val created = lines.filter { it.size >= 2 }.map { line ->
            polyline(color, 3.5f, line.map { LatLong(it.latitude, it.longitude) }, dash = floatArrayOf(2f * density, 6f * density))
        }
        val idx = layers.indexOf(markers).coerceAtLeast(0)
        layers.addAll(idx, ArrayList<Layer>(created), false)
        recordedLayers.addAll(created)
        mapView.layerManager.redrawLayers()
    }

    fun setPosition(position: MapPosition?) {
        markers.position = position
        markers.requestRedraw()
    }

    fun setHighlighted(point: GeoPoint?) {
        markers.highlighted = point
        markers.requestRedraw()
    }

    fun centerOn(point: GeoPoint, zoom: Int? = null) {
        val mvp = mapView.model.mapViewPosition
        val z = (zoom ?: mvp.zoomLevel.toInt()).coerceIn(3, 20).toByte()
        mvp.setMapPosition(MfMapPosition(LatLong(point.latitude, point.longitude), z), true)
    }

    fun fitBounds(bounds: GeoBounds, maxZoom: Int = 16) {
        val w = mapView.width
        val h = mapView.height
        if (w <= 0 || h <= 0) {
            mapView.post { fitBounds(bounds, maxZoom) }
            return
        }
        val padding = (48 * density).toInt()
        val bb = BoundingBox(bounds.minLatitude, bounds.minLongitude, bounds.maxLatitude, bounds.maxLongitude)
        val zoom = LatLongUtils.zoomForBounds(
            Dimension((w - 2 * padding).coerceAtLeast(1), (h - 2 * padding).coerceAtLeast(1)),
            bb,
            mapView.model.displayModel.tileSize,
        ).toInt().coerceIn(3, maxZoom)
        val c = bounds.center
        mapView.model.mapViewPosition.mapPosition = MfMapPosition(LatLong(c.latitude, c.longitude), zoom.toByte())
    }

    /** Orientación del mapa: 0 = norte arriba; con el rumbo, el mapa gira para que el avance quede hacia arriba. */
    fun setRotation(degrees: Float) {
        val w = mapView.width
        val h = mapView.height
        if (w <= 0 || h <= 0) return
        mapView.rotate(Rotation(-degrees, w / 2f, h / 2f))
        mapView.layerManager.redrawLayers()
    }

    val zoomLevel: Int get() = mapView.model.mapViewPosition.zoomLevel.toInt()

    fun zoomIn() = mapView.model.mapViewPosition.zoomIn(true)
    fun zoomOut() = mapView.model.mapViewPosition.zoomOut(true)

    fun destroy() {
        dataStore?.close()
        mapView.destroyAll()
    }

    private fun polyline(color: Int, widthDp: Float, points: List<LatLong>, alpha: Float = 1f, dash: FloatArray? = null): Polyline {
        val paint = AndroidGraphicFactory.INSTANCE.createPaint()
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        paint.setColor((color and 0x00FFFFFF) or (a shl 24))
        paint.setStrokeWidth(widthDp * density)
        paint.setStyle(Style.STROKE)
        paint.setStrokeCap(Cap.ROUND)
        paint.setStrokeJoin(Join.ROUND)
        dash?.let { paint.setDashPathEffect(it) }
        // El grosor crece suavemente con el zoom para que el track siga siendo legible.
        return Polyline(paint, AndroidGraphicFactory.INSTANCE).apply {
            setStrokeIncrease(1.12)
            addPoints(points)
        }
    }
}
