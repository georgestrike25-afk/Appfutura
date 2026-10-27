package com.cumbre.core.library

enum class RouteSource { IMPORTED, WIKILOC, RECORDED }

enum class RouteCategory { ALL, MINE, IMPORTED, FAVORITES, RECENT }

enum class RouteSort { RECENT_FIRST, NAME, DISTANCE, ELEVATION }

/** Datos mínimos de una ruta para filtrar y ordenar la biblioteca. */
data class RouteSummary(
    val id: Long,
    val name: String,
    val source: RouteSource,
    val distanceMeters: Double,
    val elevationGainMeters: Double?,
    val estimatedDurationSeconds: Long?,
    val favorite: Boolean,
    val createdAtMillis: Long,
    val lastOpenedAtMillis: Long?,
)

data class RangeFilter(val min: Double? = null, val max: Double? = null) {
    fun matches(value: Double?): Boolean {
        if (min == null && max == null) return true
        if (value == null) return false
        return (min == null || value >= min) && (max == null || value <= max)
    }
}

data class LibraryQuery(
    val category: RouteCategory = RouteCategory.ALL,
    val text: String = "",
    val distanceKm: RangeFilter = RangeFilter(),
    val elevationGainM: RangeFilter = RangeFilter(),
    val durationHours: RangeFilter = RangeFilter(),
    val sort: RouteSort = RouteSort.RECENT_FIRST,
)

object RouteLibrary {
    const val RECENT_LIMIT = 20

    fun apply(routes: List<RouteSummary>, query: LibraryQuery): List<RouteSummary> {
        val text = normalize(query.text.trim())
        var result = routes.asSequence()
            .filter { r ->
                when (query.category) {
                    RouteCategory.ALL -> true
                    RouteCategory.MINE -> r.source == RouteSource.RECORDED
                    RouteCategory.IMPORTED -> r.source == RouteSource.IMPORTED || r.source == RouteSource.WIKILOC
                    RouteCategory.FAVORITES -> r.favorite
                    RouteCategory.RECENT -> r.lastOpenedAtMillis != null
                }
            }
            .filter { text.isEmpty() || normalize(it.name).contains(text) }
            .filter { query.distanceKm.matches(it.distanceMeters / 1000.0) }
            .filter { query.elevationGainM.matches(it.elevationGainMeters) }
            .filter { query.durationHours.matches(it.estimatedDurationSeconds?.let { s -> s / 3600.0 }) }
            .toList()
        result = if (query.category == RouteCategory.RECENT) {
            result.sortedByDescending { it.lastOpenedAtMillis }.take(RECENT_LIMIT)
        } else {
            when (query.sort) {
                RouteSort.RECENT_FIRST -> result.sortedByDescending { maxOf(it.createdAtMillis, it.lastOpenedAtMillis ?: 0) }
                RouteSort.NAME -> result.sortedBy { normalize(it.name) }
                RouteSort.DISTANCE -> result.sortedBy { it.distanceMeters }
                RouteSort.ELEVATION -> result.sortedByDescending { it.elevationGainMeters ?: -1.0 }
            }
        }
        return result
    }

    /** Minúsculas y sin tildes, para que "champaqui" encuentre "Champaquí". */
    fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
