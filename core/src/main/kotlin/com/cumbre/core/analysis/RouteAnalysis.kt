package com.cumbre.core.analysis

import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoPoint

data class ProfilePoint(val distanceMeters: Double, val elevationMeters: Double)

enum class IssueSeverity { INFO, WARNING }

sealed interface QualityIssue {
    val severity: IssueSeverity

    data class InvalidCoordinates(val count: Int) : QualityIssue {
        override val severity = IssueSeverity.WARNING
    }

    data class EmptySegments(val count: Int) : QualityIssue {
        override val severity = IssueSeverity.INFO
    }

    data object NoElevation : QualityIssue {
        override val severity = IssueSeverity.INFO
    }

    data class PartialElevation(val coveragePercent: Int) : QualityIssue {
        override val severity = IssueSeverity.INFO
    }

    data class InvalidElevations(val count: Int) : QualityIssue {
        override val severity = IssueSeverity.INFO
    }

    /** Distancia anormal entre puntos consecutivos de un mismo segmento. */
    data class LargeGap(val count: Int, val maxGapMeters: Double, val atDistanceMeters: Double) : QualityIssue {
        override val severity = IssueSeverity.WARNING
    }

    /** El track está partido en varios segmentos separados entre sí. */
    data class SeparatedSegments(val segmentCount: Int, val maxSeparationMeters: Double) : QualityIssue {
        override val severity = IssueSeverity.INFO
    }

    data object MissingName : QualityIssue {
        override val severity = IssueSeverity.INFO
    }

    data object TooFewPoints : QualityIssue {
        override val severity = IssueSeverity.WARNING
    }

    data object OnlyWaypoints : QualityIssue {
        override val severity = IssueSeverity.WARNING
    }
}

data class RouteStats(
    val distanceMeters: Double,
    /** Nulo cuando no hay elevaciones suficientes para calcularlo con fiabilidad. */
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val minElevationMeters: Double?,
    val maxElevationMeters: Double?,
    val estimatedDurationSeconds: Long?,
    /** Duración real registrada en el archivo, si todos los segmentos tienen marcas de tiempo. */
    val recordedDurationSeconds: Long?,
    val pointCount: Int,
    val segmentCount: Int,
    val waypointCount: Int,
    val elevationCoverage: Double,
    val bounds: GeoBounds?,
    val start: GeoPoint?,
    val end: GeoPoint?,
)

data class RouteAnalysis(
    val stats: RouteStats,
    val profile: List<ProfilePoint>,
    val issues: List<QualityIssue>,
)
