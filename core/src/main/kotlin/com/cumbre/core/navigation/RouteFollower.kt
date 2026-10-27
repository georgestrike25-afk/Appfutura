package com.cumbre.core.navigation

import com.cumbre.core.geo.GeoMath
import com.cumbre.core.geo.GeoPoint

data class FollowWaypoint(val name: String?, val point: GeoPoint, val progressMeters: Double)

data class FollowState(
    val distanceToTrackMeters: Double,
    val progressMeters: Double,
    val remainingMeters: Double,
    val totalMeters: Double,
    val offRoute: Boolean,
    /** true solo en la actualización en la que se detecta el desvío (para avisar una vez). */
    val offRouteStarted: Boolean,
    val nextWaypoint: FollowWaypoint?,
    /** Distancia a lo largo del track hasta el próximo waypoint. */
    val distanceToNextWaypointMeters: Double?,
    val closestPoint: GeoPoint,
    val nearEnd: Boolean,
)

/**
 * Seguimiento de un recorrido GPX. No genera indicaciones de giro: solo informa la posición
 * relativa al track real (distancia a la línea, progreso, distancia restante y próximo waypoint)
 * y detecta desvíos con histéresis para evitar falsas alarmas por ruido GPS.
 */
class RouteFollower(
    private val geometry: TrackGeometry,
    waypoints: List<Pair<String?, GeoPoint>> = emptyList(),
    var offRouteThresholdMeters: Double = DEFAULT_OFF_ROUTE_M,
    private val confirmFixes: Int = 3,
    /** Distancia máxima a la que se proyecta un waypoint sobre el track. */
    private val waypointSnapMeters: Double = 250.0,
) {
    private val projectedWaypoints: List<FollowWaypoint> = waypoints.mapNotNull { (name, point) ->
        val m = geometry.match(point) ?: return@mapNotNull null
        if (m.distanceToTrackMeters <= waypointSnapMeters) FollowWaypoint(name, point, m.progressMeters) else null
    }.sortedBy { it.progressMeters }

    private var lastProgress: Double? = null
    private var outsideCount = 0
    private var offRoute = false

    val waypointsOnTrack: List<FollowWaypoint> get() = projectedWaypoints

    fun update(position: GeoPoint, accuracyMeters: Double? = null): FollowState? {
        val match = geometry.match(position, lastProgress, acceptWithin = offRouteThresholdMeters * 2) ?: return null
        // Evita que el progreso retroceda por ruido; permite retrocesos reales (> 50 m).
        val previous = lastProgress
        val progress = if (previous != null && match.progressMeters < previous && previous - match.progressMeters < 50.0) {
            previous
        } else {
            match.progressMeters
        }
        lastProgress = progress

        val reliable = accuracyMeters == null || accuracyMeters <= offRouteThresholdMeters
        var started = false
        if (match.distanceToTrackMeters > offRouteThresholdMeters && reliable) {
            outsideCount++
            if (!offRoute && outsideCount >= confirmFixes) {
                offRoute = true
                started = true
            }
        } else if (match.distanceToTrackMeters <= offRouteThresholdMeters * 0.7) {
            outsideCount = 0
            offRoute = false
        }

        val next = projectedWaypoints.firstOrNull { it.progressMeters > progress + 10.0 }
        val remaining = (geometry.totalDistanceMeters - progress).coerceAtLeast(0.0)
        return FollowState(
            distanceToTrackMeters = match.distanceToTrackMeters,
            progressMeters = progress,
            remainingMeters = remaining,
            totalMeters = geometry.totalDistanceMeters,
            offRoute = offRoute,
            offRouteStarted = started,
            nextWaypoint = next,
            distanceToNextWaypointMeters = next?.let { it.progressMeters - progress },
            closestPoint = match.closestPoint,
            nearEnd = remaining < 50.0 && match.distanceToTrackMeters < offRouteThresholdMeters,
        )
    }

    /** Distancia en línea recta desde [position] hasta el inicio del recorrido. */
    fun distanceToStart(position: GeoPoint, start: GeoPoint): Double = GeoMath.distanceMeters(position, start)

    companion object {
        const val DEFAULT_OFF_ROUTE_M = 50.0
    }
}
