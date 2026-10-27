package com.cumbre.core.geo

/** Simplificación Douglas–Peucker para miniaturas y dibujo eficiente. */
object Simplifier {
    fun simplify(points: List<GeoPoint>, toleranceMeters: Double): List<GeoPoint> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.lastIndex)
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            var maxDist = 0.0
            var index = -1
            for (i in start + 1 until end) {
                val d = GeoMath.projectOnSegment(points[i], points[start], points[end]).distanceMeters
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (index != -1 && maxDist > toleranceMeters) {
                keep[index] = true
                stack.addLast(start to index)
                stack.addLast(index to end)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /**
     * Reduce la línea hasta como máximo [maxPoints] puntos, aumentando la tolerancia
     * progresivamente.
     */
    fun simplifyToMax(points: List<GeoPoint>, maxPoints: Int): List<GeoPoint> {
        if (points.size <= maxPoints) return points
        var tolerance = 2.0
        var result = simplify(points, tolerance)
        while (result.size > maxPoints && tolerance < 100_000) {
            tolerance *= 2
            result = simplify(points, tolerance)
        }
        return result
    }
}
