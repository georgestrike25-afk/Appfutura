package com.cumbre.core.analysis

import java.util.Locale

/** Serialización compacta del perfil y los avisos para guardarlos en la base de datos. */
object AnalysisCodec {
    fun encodeProfile(profile: List<ProfilePoint>): String =
        profile.joinToString(";") { String.format(Locale.ROOT, "%.0f:%.1f", it.distanceMeters, it.elevationMeters) }

    fun decodeProfile(text: String?): List<ProfilePoint> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(';').mapNotNull { pair ->
            val parts = pair.split(':')
            val d = parts.getOrNull(0)?.toDoubleOrNull()
            val e = parts.getOrNull(1)?.toDoubleOrNull()
            if (d != null && e != null) ProfilePoint(d, e) else null
        }
    }

    fun encodeIssues(issues: List<QualityIssue>): String = issues.joinToString("|") { issue ->
        when (issue) {
            is QualityIssue.InvalidCoordinates -> "INVALID_COORDINATES:${issue.count}"
            is QualityIssue.EmptySegments -> "EMPTY_SEGMENTS:${issue.count}"
            QualityIssue.NoElevation -> "NO_ELEVATION"
            is QualityIssue.PartialElevation -> "PARTIAL_ELEVATION:${issue.coveragePercent}"
            is QualityIssue.InvalidElevations -> "INVALID_ELEVATIONS:${issue.count}"
            is QualityIssue.LargeGap -> String.format(Locale.ROOT, "LARGE_GAP:%d:%.1f:%.1f", issue.count, issue.maxGapMeters, issue.atDistanceMeters)
            is QualityIssue.SeparatedSegments -> String.format(Locale.ROOT, "SEPARATED_SEGMENTS:%d:%.1f", issue.segmentCount, issue.maxSeparationMeters)
            QualityIssue.MissingName -> "MISSING_NAME"
            QualityIssue.TooFewPoints -> "TOO_FEW_POINTS"
            QualityIssue.OnlyWaypoints -> "ONLY_WAYPOINTS"
        }
    }

    fun decodeIssues(text: String?): List<QualityIssue> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split('|').mapNotNull { token ->
            val p = token.split(':')
            fun i(n: Int) = p.getOrNull(n)?.toIntOrNull() ?: 0
            fun d(n: Int) = p.getOrNull(n)?.toDoubleOrNull() ?: 0.0
            when (p[0]) {
                "INVALID_COORDINATES" -> QualityIssue.InvalidCoordinates(i(1))
                "EMPTY_SEGMENTS" -> QualityIssue.EmptySegments(i(1))
                "NO_ELEVATION" -> QualityIssue.NoElevation
                "PARTIAL_ELEVATION" -> QualityIssue.PartialElevation(i(1))
                "INVALID_ELEVATIONS" -> QualityIssue.InvalidElevations(i(1))
                "LARGE_GAP" -> QualityIssue.LargeGap(i(1), d(2), d(3))
                "SEPARATED_SEGMENTS" -> QualityIssue.SeparatedSegments(i(1), d(2))
                "MISSING_NAME" -> QualityIssue.MissingName
                "TOO_FEW_POINTS" -> QualityIssue.TooFewPoints
                "ONLY_WAYPOINTS" -> QualityIssue.OnlyWaypoints
                else -> null
            }
        }
    }
}
