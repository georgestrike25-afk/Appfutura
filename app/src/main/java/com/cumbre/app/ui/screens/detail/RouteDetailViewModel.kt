package com.cumbre.app.ui.screens.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cumbre.app.data.MapRepository
import com.cumbre.app.data.RouteGeometry
import com.cumbre.app.data.RouteRepository
import com.cumbre.app.data.db.OfflineMapEntity
import com.cumbre.app.data.db.RouteEntity
import com.cumbre.core.analysis.AnalysisCodec
import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.analysis.QualityIssue
import com.cumbre.core.analysis.RouteStats
import com.cumbre.core.maps.CoverageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RouteDetailState(
    val loading: Boolean = true,
    val route: RouteEntity? = null,
    val geometry: RouteGeometry? = null,
    val geometryMissing: Boolean = false,
    val stats: RouteStats? = null,
    val profile: List<ProfilePoint> = emptyList(),
    val issues: List<QualityIssue> = emptyList(),
    val coverage: CoverageResult? = null,
    val coveringMaps: List<OfflineMapEntity> = emptyList(),
    val anyMapsInstalled: Boolean = false,
)

class RouteDetailViewModel(
    private val routeId: Long,
    private val routes: RouteRepository,
    private val maps: MapRepository,
) : ViewModel() {
    private val geometry = MutableStateFlow<RouteGeometry?>(null)
    private val geometryMissing = MutableStateFlow(false)

    val state: StateFlow<RouteDetailState> = combine(
        routes.observeRoute(routeId), geometry, geometryMissing, maps.observeMaps(),
    ) { route, geo, missing, mapList ->
        if (route == null) return@combine RouteDetailState(loading = false)
        val points = geo?.lines?.flatten() ?: emptyList()
        val (coverage, covering) = if (points.isNotEmpty()) maps.coverage(points, mapList) else null to emptyList()
        RouteDetailState(
            loading = false,
            route = route,
            geometry = geo,
            geometryMissing = missing,
            stats = route.toStats(),
            profile = AnalysisCodec.decodeProfile(route.profile),
            issues = AnalysisCodec.decodeIssues(route.issues),
            coverage = coverage,
            coveringMaps = covering,
            anyMapsInstalled = mapList.isNotEmpty(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteDetailState())

    init {
        viewModelScope.launch {
            val route = routes.getRoute(routeId) ?: return@launch
            routes.markOpened(routeId)
            val geo = routes.loadGeometry(route)
            if (geo == null) geometryMissing.value = true else geometry.value = geo
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val r = state.value.route ?: return@launch
        routes.setFavorite(r.id, !r.favorite)
    }

    fun rename(name: String) = viewModelScope.launch { routes.rename(routeId, name) }

    fun setSourceUrl(url: String) = viewModelScope.launch {
        val r = state.value.route ?: return@launch
        routes.setSourceUrl(r, url.trim().ifBlank { null })
    }

    suspend fun delete() {
        state.value.route?.let { routes.delete(it) }
    }

    suspend fun export(target: Uri): Boolean = state.value.route?.let { routes.exportTo(it, target) } ?: false

    suspend fun shareUri(): Uri? = state.value.route?.let { routes.shareUri(it) }
}

fun RouteEntity.toStats() = RouteStats(
    distanceMeters = distanceMeters,
    elevationGainMeters = elevationGainMeters,
    elevationLossMeters = elevationLossMeters,
    minElevationMeters = minElevationMeters,
    maxElevationMeters = maxElevationMeters,
    estimatedDurationSeconds = estimatedDurationSeconds,
    recordedDurationSeconds = recordedDurationSeconds,
    pointCount = pointCount,
    segmentCount = segmentCount,
    waypointCount = waypointCount,
    elevationCoverage = if (elevationGainMeters != null) 1.0 else 0.0,
    bounds = bounds,
    start = start,
    end = null,
)
