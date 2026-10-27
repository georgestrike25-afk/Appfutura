package com.cumbre.core

import com.cumbre.core.analysis.AnalysisCodec
import com.cumbre.core.analysis.IssueText
import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.analysis.QualityIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisCodecTest {
    @Test
    fun `avisos ida y vuelta`() {
        val issues = listOf(
            QualityIssue.InvalidCoordinates(4), QualityIssue.EmptySegments(1), QualityIssue.NoElevation,
            QualityIssue.PartialElevation(30), QualityIssue.InvalidElevations(2), QualityIssue.LargeGap(1, 2112.7, 111.2),
            QualityIssue.SeparatedSegments(2, 890.5), QualityIssue.MissingName, QualityIssue.TooFewPoints, QualityIssue.OnlyWaypoints,
        )
        assertEquals(issues, AnalysisCodec.decodeIssues(AnalysisCodec.encodeIssues(issues)))
        assertEquals(emptyList(), AnalysisCodec.decodeIssues(""))
        assertEquals(listOf(QualityIssue.NoElevation), AnalysisCodec.decodeIssues("DESCONOCIDO|NO_ELEVATION"))
        issues.forEach { assertTrue(IssueText.describe(it).isNotBlank()) }
    }

    @Test
    fun `perfil ida y vuelta`() {
        val profile = listOf(ProfilePoint(0.0, 1640.0), ProfilePoint(9200.0, 2790.4), ProfilePoint(18400.0, 1641.2))
        assertEquals(profile, AnalysisCodec.decodeProfile(AnalysisCodec.encodeProfile(profile)))
        assertEquals(emptyList(), AnalysisCodec.decodeProfile(null))
    }

    @Test
    fun `texto del aviso de salto en español`() {
        val t = IssueText.describe(QualityIssue.LargeGap(1, 640.0, 11_200.0))
        assertEquals("Hay un salto de hasta 640 m entre puntos consecutivos (cerca del km 11,2). Puede faltar un tramo.", t)
    }
}
