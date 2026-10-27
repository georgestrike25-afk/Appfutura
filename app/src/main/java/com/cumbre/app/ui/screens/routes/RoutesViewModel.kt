package com.cumbre.app.ui.screens.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cumbre.app.data.MapRepository
import com.cumbre.app.data.RouteRepository
import com.cumbre.app.data.db.RouteEntity
import com.cumbre.core.geo.PolylineCodec
import com.cumbre.core.library.LibraryQuery
import com.cumbre.core.library.RangeFilter
import com.cumbre.core.library.RouteCategory
import com.cumbre.core.library.RouteLibrary
import com.cumbre.core.library.RouteSort
import com.cumbre.core.library.RouteSource
import com.cumbre.core.library.RouteSummary
import com.cumbre.core.maps.CoverageLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class RouteItem(val route: RouteEntity, val coverage: CoverageLevel)

enum class DistanceFilter(val label: String, val range: RangeFilter) {
    ANY("Cualquier distancia", RangeFilter()),
    SHORT("Hasta 5 km", RangeFilter(max = 5.0)),
    MEDIUM("5 a 15 km", RangeFilter(5.0, 15.0)),
    LONG("Más de 15 km", RangeFilter(min = 15.0)),
}

enum class ElevationFilter(val label: String, val range: RangeFilter) {
    ANY("Cualquier desnivel", RangeFilter()),
    LOW("Hasta 500 m", RangeFilter(max = 500.0)),
    MID("500 a 1.000 m", RangeFilter(500.0, 1000.0)),
    HIGH("Más de 1.000 m", RangeFilter(min = 1000.0)),
}

enum class DurationFilter(val label: String, val range: RangeFilter) {
    ANY("Cualquier duración", RangeFilter()),
    SHORT("Hasta 3 h", RangeFilter(max = 3.0)),
    MID("3 a 6 h", RangeFilter(3.0, 6.0)),
    LONG("Más de 6 h", RangeFilter(min = 6.0)),
}

data class RoutesFilterState(
    val category: RouteCategory = RouteCategory.ALL,
    val text: String = "",
    val distance: DistanceFilter = DistanceFilter.ANY,
    val elevation: ElevationFilter = ElevationFilter.ANY,
    val duration: DurationFilter = DurationFilter.ANY,
    val sort: RouteSort = RouteSort.RECENT_FIRST,
) {
    val activeFilters: Int
        get() = listOf(distance != DistanceFilter.ANY, elevation != ElevationFilter.ANY, duration != DurationFilter.ANY).count { it }
}

data class RoutesUiState(
    val loading: Boolean = true,
    val totalRoutes: Int = 0,
    val items: List<RouteItem> = emptyList(),
)

class RoutesViewModel(routes: RouteRepository, maps: MapRepository) : ViewModel() {
    private val _filters = MutableStateFlow(RoutesFilterState())
    val filters: StateFlow<RoutesFilterState> = _filters

    val state: StateFlow<RoutesUiState> = combine(routes.observeRoutes(), maps.observeMaps(), _filters) { all, mapList, f ->
        val byId = all.associateBy { it.id }
        val summaries = all.map {
            RouteSummary(
                id = it.id,
                name = it.name,
                source = runCatching { RouteSource.valueOf(it.source) }.getOrDefault(RouteSource.IMPORTED),
                distanceMeters = it.distanceMeters,
                elevationGainMeters = it.elevationGainMeters,
                estimatedDurationSeconds = it.estimatedDurationSeconds,
                favorite = it.favorite,
                createdAtMillis = it.createdAt,
                lastOpenedAtMillis = it.lastOpenedAt,
            )
        }
        val query = LibraryQuery(f.category, f.text, f.distance.range, f.elevation.range, f.duration.range, f.sort)
        val filtered = RouteLibrary.apply(summaries, query)
        val items = filtered.mapNotNull { s ->
            val r = byId[s.id] ?: return@mapNotNull null
            val coverage = maps.coverage(PolylineCodec.decode(r.thumbnail), mapList).first.level
            RouteItem(r, coverage)
        }
        RoutesUiState(loading = false, totalRoutes = all.size, items = items)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutesUiState())

    fun setCategory(c: RouteCategory) = _filters.update { it.copy(category = c) }
    fun setText(t: String) = _filters.update { it.copy(text = t) }
    fun setSort(s: RouteSort) = _filters.update { it.copy(sort = s) }
    fun setDistance(d: DistanceFilter) = _filters.update { it.copy(distance = d) }
    fun setElevation(e: ElevationFilter) = _filters.update { it.copy(elevation = e) }
    fun setDuration(d: DurationFilter) = _filters.update { it.copy(duration = d) }
    fun clearFilters() = _filters.update { it.copy(distance = DistanceFilter.ANY, elevation = ElevationFilter.ANY, duration = DurationFilter.ANY) }
}
