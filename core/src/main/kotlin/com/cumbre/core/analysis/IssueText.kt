package com.cumbre.core.analysis

import com.cumbre.core.format.Formatters

/** Textos para el usuario de cada aviso de calidad. */
object IssueText {
    fun describe(issue: QualityIssue): String = when (issue) {
        is QualityIssue.InvalidCoordinates ->
            "Se descartaron ${issue.count} ${plural(issue.count, "punto", "puntos")} con coordenadas fuera de rango."
        is QualityIssue.EmptySegments ->
            "El archivo tiene ${issue.count} ${plural(issue.count, "segmento vacío", "segmentos vacíos")}; se ignoraron."
        QualityIssue.NoElevation ->
            "El archivo no trae altitudes: no se puede calcular el desnivel ni el perfil."
        is QualityIssue.PartialElevation ->
            "Solo el ${issue.coveragePercent} % de los puntos tiene altitud; el desnivel puede ser incompleto."
        is QualityIssue.InvalidElevations ->
            "Se ignoraron ${issue.count} ${plural(issue.count, "altitud inválida", "altitudes inválidas")}."
        is QualityIssue.LargeGap ->
            "Hay ${if (issue.count == 1) "un salto" else "${issue.count} saltos"} de hasta ${Formatters.distance(issue.maxGapMeters)} " +
                "entre puntos consecutivos (cerca del km ${Formatters.kilometersValue(issue.atDistanceMeters)}). Puede faltar un tramo."
        is QualityIssue.SeparatedSegments ->
            "El recorrido tiene ${issue.segmentCount} tramos separados" +
                (if (issue.maxSeparationMeters >= 1) " (hasta ${Formatters.distance(issue.maxSeparationMeters)} entre tramos)." else ".")
        QualityIssue.MissingName -> "El archivo no tiene nombre; podés ponerle uno."
        QualityIssue.TooFewPoints -> "El recorrido tiene muy pocos puntos para seguirlo."
        QualityIssue.OnlyWaypoints -> "El archivo solo tiene waypoints, sin un track para seguir."
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
}
