package com.cumbre.app.ui.screens.activity

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cumbre.app.data.ActivityRepository
import com.cumbre.app.data.db.ActivityEntity
import com.cumbre.core.analysis.ElevationCalculator
import com.cumbre.core.analysis.RouteAnalysis
import com.cumbre.core.analysis.RouteAnalyzer
import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.gpx.GpxPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ActivityTrack(val lines: List<List<GeoPoint>>, val bounds: GeoBounds?, val analysis: RouteAnalysis?)

data class ActivityDetailState(
    val loading: Boolean = true,
    val activity: ActivityEntity? = null,
    val track: ActivityTrack? = null,
)

class ActivityDetailViewModel(private val id: Long, private val repo: ActivityRepository) : ViewModel() {
    private val track = MutableStateFlow<ActivityTrack?>(null)

    val state: StateFlow<ActivityDetailState> = combine(repo.observe(id), track) { a, t ->
        ActivityDetailState(loading = false, activity = a, track = t)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityDetailState())

    init {
        viewModelScope.launch {
            val points = repo.points(id)
            track.value = withContext(Dispatchers.Default) {
                val segments = points.groupBy { it.segment }.toSortedMap().values.toList()
                val lines = segments.map { seg -> seg.map { GeoPoint(it.latitude, it.longitude) } }
                val gpxLines = segments.map { seg -> seg.map { GpxPoint(it.latitude, it.longitude, it.altitudeMeters, it.timeMillis) } }
                val analysis = if (points.size >= 2) {
                    RouteAnalyzer.analyzeLines(gpxLines, elevationThreshold = ElevationCalculator.GPS_THRESHOLD_M)
                } else {
                    null
                }
                ActivityTrack(lines, GeoBounds.of(lines.flatten()), analysis)
            }
        }
    }

    fun rename(name: String) = viewModelScope.launch { repo.rename(id, name) }
    suspend fun delete() = repo.delete(id)
    suspend fun export(uri: Uri): Boolean = state.value.activity?.let { repo.exportTo(it, uri) } ?: false
    suspend fun shareUri(): Uri? = state.value.activity?.let { repo.shareUri(it) }
    suspend fun saveAsRoute(): Long? = state.value.activity?.let { repo.saveAsRoute(it) }
}
