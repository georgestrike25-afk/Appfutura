package com.cumbre.core.analysis

import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoMath
import com.cumbre.core.gpx.GpxDocument
import com.cumbre.core.gpx.GpxParseReport
import com.cumbre.core.gpx.GpxPoint

/** Analiza un documento GPX: estadísticas, perfil de elevación y avisos de calidad. */
object RouteAnalyzer {
    /** Separación entre puntos consecutivos a partir de la cual se considera un salto. */
    const val GAP_THRESHOLD_M = 1_000.0

    /** Cobertura mínima de elevaciones para calcular desnivel. */
    const val MIN_ELEVATION_COVERAGE = 0.5

    const val PROFILE_SAMPLES = 200

    fun analyze(
        document: GpxDocument,
        report: GpxParseReport = GpxParseReport(),
        profileSamples: Int = PROFILE_SAMPLES,
    ): RouteAnalysis = analyzeLines(
        lines = document.lines(),
        waypointCount = document.waypoints.size,
        hasName = document.displayName() != null,
        report = report,
        profileSamples = profileSamples,
    )

    fun analyzeLines(
        lines: List<List<GpxPoint>>,
        waypointCount: Int = 0,
        hasName: Boolean = true,
        report: GpxParseReport = GpxParseReport(),
        profileSamples: Int = PROFILE_SAMPLES,
        elevationThreshold: Double = ElevationCalculator.DEFAULT_THRESHOLD_M,
    ): RouteAnalysis {
        val issues = ArrayList<QualityIssue>()
        var distance = 0.0
        var pointCount = 0
        var withElevation = 0
        var minEle = Double.POSITIVE_INFINITY
        var maxEle = Double.NEGATIVE_INFINITY
        var gapCount = 0
        var maxGap = 0.0
        var maxGapAt = 0.0
        var maxSeparation = 0.0
        val rawProfile = ArrayList<ProfilePoint>()
        val elevationCalc = ElevationCalculator(elevationThreshold)
        var recordedSeconds = 0L
        var allTimed = lines.isNotEmpty()

        var previousEnd: GpxPoint? = null
        for (line in lines) {
            elevationCalc.breakSegment()
            previousEnd?.let { prev ->
                val sep = GeoMath.distanceMeters(prev.latitude, prev.longitude, line.first().latitude, line.first().longitude)
                if (sep > maxSeparation) maxSeparation = sep
            }
            var prev: GpxPoint? = null
            for (p in line) {
                pointCount++
                if (prev != null) {
                    val d = GeoMath.distanceMeters(prev.latitude, prev.longitude, p.latitude, p.longitude)
                    if (d > GAP_THRESHOLD_M) {
                        gapCount++
                        if (d > maxGap) {
                            maxGap = d
                            maxGapAt = distance
                        }
                    }
                    distance += d
                }
                p.elevation?.let { e ->
                    withElevation++
                    if (e < minEle) minEle = e
                    if (e > maxEle) maxEle = e
                    elevationCalc.add(e)
                    rawProfile.add(ProfilePoint(distance, e))
                }
                prev = p
            }
            val first = line.first().timeMillis
            val last = line.last().timeMillis
            if (first != null && last != null && last >= first) {
                recordedSeconds += (last - first) / 1000
            } else {
                allTimed = false
            }
            previousEnd = line.last()
        }

        val coverage = if (pointCount == 0) 0.0 else withElevation.toDouble() / pointCount
        val elevationReliable = coverage >= MIN_ELEVATION_COVERAGE && withElevation >= 2
        val gain = if (elevationReliable) elevationCalc.gain else null
        val loss = if (elevationReliable) elevationCalc.loss else null

        if (report.invalidCoordinates > 0) issues.add(QualityIssue.InvalidCoordinates(report.invalidCoordinates))
        if (report.emptySegments > 0) issues.add(QualityIssue.EmptySegments(report.emptySegments))
        if (report.invalidElevations > 0) issues.add(QualityIssue.InvalidElevations(report.invalidElevations))
        if (pointCount == 0) {
            issues.add(if (waypointCount > 0) QualityIssue.OnlyWaypoints else QualityIssue.TooFewPoints)
        } else if (pointCount < 2) {
            issues.add(QualityIssue.TooFewPoints)
        }
        if (pointCount > 0) {
            when {
                withElevation == 0 -> issues.add(QualityIssue.NoElevation)
                coverage < 0.95 -> issues.add(QualityIssue.PartialElevation((coverage * 100).toInt()))
            }
        }
        if (gapCount > 0) issues.add(QualityIssue.LargeGap(gapCount, maxGap, maxGapAt))
        if (lines.size > 1) issues.add(QualityIssue.SeparatedSegments(lines.size, maxSeparation))
        if (!hasName) issues.add(QualityIssue.MissingName)

        val allPoints = lines.asSequence().flatten().map { it.geo }.asIterable()
        val stats = RouteStats(
            distanceMeters = distance,
            elevationGainMeters = gain,
            elevationLossMeters = loss,
            minElevationMeters = if (withElevation > 0) minEle else null,
            maxElevationMeters = if (withElevation > 0) maxEle else null,
            estimatedDurationSeconds = DurationEstimator.estimateSeconds(distance, gain, loss),
            recordedDurationSeconds = if (allTimed && recordedSeconds > 0) recordedSeconds else null,
            pointCount = pointCount,
            segmentCount = lines.size,
            waypointCount = waypointCount,
            elevationCoverage = coverage,
            bounds = GeoBounds.of(allPoints),
            start = lines.firstOrNull()?.firstOrNull()?.geo,
            end = lines.lastOrNull()?.lastOrNull()?.geo,
        )
        val profile = if (elevationReliable) downsampleProfile(rawProfile, profileSamples) else emptyList()
        return RouteAnalysis(stats, profile, issues)
    }

    /**
     * Reduce el perfil a [samples] puntos conservando, en cada intervalo de distancia,
     * el mínimo y el máximo para no aplanar las cumbres.
     */
    fun downsampleProfile(profile: List<ProfilePoint>, samples: Int): List<ProfilePoint> {
        if (profile.size <= samples || samples < 4) return profile
        val total = profile.last().distanceMeters
        if (total <= 0.0) return listOf(profile.first(), profile.last())
        val buckets = samples / 2
        val result = ArrayList<ProfilePoint>(samples + 2)
        result.add(profile.first())
        var index = 0
        for (b in 0 until buckets) {
            val limit = total * (b + 1) / buckets
            var minP: ProfilePoint? = null
            var maxP: ProfilePoint? = null
            while (index < profile.size && profile[index].distanceMeters <= limit) {
                val p = profile[index]
                if (minP == null || p.elevationMeters < minP.elevationMeters) minP = p
                if (maxP == null || p.elevationMeters > maxP.elevationMeters) maxP = p
                index++
            }
            if (minP != null && maxP != null) {
                if (minP === maxP) {
                    result.add(minP)
                } else if (minP.distanceMeters <= maxP.distanceMeters) {
                    result.add(minP); result.add(maxP)
                } else {
                    result.add(maxP); result.add(minP)
                }
            }
        }
        if (result.last() !== profile.last()) result.add(profile.last())
        return result
    }
}
