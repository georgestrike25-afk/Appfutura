package com.cumbre.core.navigation

import com.cumbre.core.geo.GeoMath
import com.cumbre.core.geo.GeoPoint

/**
 * Geometría de un recorrido preparada para seguimiento. Los segmentos se mantienen separados:
 * no se crean tramos ficticios entre el final de un segmento y el inicio del siguiente, y la
 * distancia acumulada solo suma tramos reales.
 */
class TrackGeometry(segments: List<List<GeoPoint>>) {
    internal val edgesStart: Array<GeoPoint>
    internal val edgesEnd: Array<GeoPoint>
    internal val edgeStartDistance: DoubleArray
    internal val edgeLength: DoubleArray

    val totalDistanceMeters: Double
    val isEmpty: Boolean get() = edgeLength.isEmpty() && singlePoint == null
    private val singlePoint: GeoPoint?

    init {
        val starts = ArrayList<GeoPoint>()
        val ends = ArrayList<GeoPoint>()
        val startDist = ArrayList<Double>()
        val lengths = ArrayList<Double>()
        var acc = 0.0
        var lone: GeoPoint? = null
        for (seg in segments) {
            if (seg.size == 1 && lone == null) lone = seg[0]
            for (i in 1 until seg.size) {
                val a = seg[i - 1]
                val b = seg[i]
                val len = GeoMath.distanceMeters(a, b)
                starts.add(a); ends.add(b); startDist.add(acc); lengths.add(len)
                acc += len
            }
        }
        edgesStart = starts.toTypedArray()
        edgesEnd = ends.toTypedArray()
        edgeStartDistance = startDist.toDoubleArray()
        edgeLength = lengths.toDoubleArray()
        totalDistanceMeters = acc
        singlePoint = if (starts.isEmpty()) lone else null
    }

    val edgeCount: Int get() = edgeLength.size

    /**
     * Punto del recorrido más cercano a [position]. Si se indica [hintProgress] (el progreso
     * anterior), primero se busca en una ventana alrededor de esa distancia para no saltar
     * a otra parte del track en recorridos de ida y vuelta o con cruces.
     */
    fun match(position: GeoPoint, hintProgress: Double? = null, windowBack: Double = 300.0, windowAhead: Double = 1_500.0, acceptWithin: Double = 100.0): TrackMatch? {
        if (edgeCount == 0) {
            val p = singlePoint ?: return null
            return TrackMatch(GeoMath.distanceMeters(position, p), 0.0, -1, p)
        }
        if (hintProgress != null) {
            val local = search(position, hintProgress - windowBack, hintProgress + windowAhead)
            if (local != null && local.distanceToTrackMeters <= acceptWithin) return local
        }
        return search(position, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
    }

    private fun search(position: GeoPoint, from: Double, to: Double): TrackMatch? {
        var best: TrackMatch? = null
        for (i in 0 until edgeCount) {
            val s = edgeStartDistance[i]
            val e = s + edgeLength[i]
            if (e < from || s > to) continue
            val proj = GeoMath.projectOnSegment(position, edgesStart[i], edgesEnd[i])
            if (best == null || proj.distanceMeters < best.distanceToTrackMeters) {
                best = TrackMatch(
                    distanceToTrackMeters = proj.distanceMeters,
                    progressMeters = s + proj.fraction * edgeLength[i],
                    edgeIndex = i,
                    closestPoint = proj.closest,
                )
            }
        }
        return best
    }

    /** Progreso (distancia a lo largo del recorrido) del punto del track más cercano a [point]. */
    fun progressOf(point: GeoPoint): Double = match(point)?.progressMeters ?: 0.0
}

data class TrackMatch(
    val distanceToTrackMeters: Double,
    /** Distancia recorrida a lo largo del track hasta el punto más cercano. */
    val progressMeters: Double,
    val edgeIndex: Int,
    val closestPoint: GeoPoint,
)
