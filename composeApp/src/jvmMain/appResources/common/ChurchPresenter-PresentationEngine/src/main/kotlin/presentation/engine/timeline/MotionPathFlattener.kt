package presentation.engine.timeline

/**
 * Flattens a PowerPoint animMotion path string (`M 0 0 L 0.25 0.083 C … Z E`, coordinates in
 * normalized slide units relative to the shape's resting position) into a polyline of offset
 * points. Cubic segments are subdivided; the result is re-sampled by arc length so playback
 * speed along the path is uniform.
 *
 * Returns null when the path cannot be parsed.
 */
internal object MotionPathFlattener {

    private const val CURVE_SUBDIVISIONS = 16
    private const val RESAMPLE_POINTS = 48

    fun flatten(path: String): List<Pair<Double, Double>>? {
        val points = parse(path) ?: return null
        if (points.size < 2) return points
        return resampleByArcLength(points)
    }

    private fun parse(path: String): List<Pair<Double, Double>>? {
        val tokens = path.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val points = mutableListOf<Pair<Double, Double>>()
        var cx = 0.0
        var cy = 0.0
        var startX = 0.0
        var startY = 0.0
        var i = 0

        fun number(): Double? = tokens.getOrNull(i)?.toDoubleOrNull()?.also { i++ }

        try {
            while (i < tokens.size) {
                val command = tokens[i]
                i++
                val relative = command.length == 1 && command[0].isLowerCase()
                when (command.uppercase()) {
                    "M" -> {
                        val x = number() ?: return null
                        val y = number() ?: return null
                        cx = if (relative) cx + x else x
                        cy = if (relative) cy + y else y
                        startX = cx
                        startY = cy
                        points.add(cx to cy)
                    }
                    "L" -> {
                        // Polyline form: L may be followed by several coordinate pairs.
                        while (tokens.getOrNull(i)?.toDoubleOrNull() != null) {
                            val x = number() ?: return null
                            val y = number() ?: return null
                            cx = if (relative) cx + x else x
                            cy = if (relative) cy + y else y
                            points.add(cx to cy)
                        }
                    }
                    "C" -> {
                        while (tokens.getOrNull(i)?.toDoubleOrNull() != null) {
                            val x1 = number() ?: return null
                            val y1 = number() ?: return null
                            val x2 = number() ?: return null
                            val y2 = number() ?: return null
                            val x3 = number() ?: return null
                            val y3 = number() ?: return null
                            val p1 = if (relative) (cx + x1) to (cy + y1) else x1 to y1
                            val p2 = if (relative) (cx + x2) to (cy + y2) else x2 to y2
                            val p3 = if (relative) (cx + x3) to (cy + y3) else x3 to y3
                            for (step in 1..CURVE_SUBDIVISIONS) {
                                val t = step.toDouble() / CURVE_SUBDIVISIONS
                                points.add(cubic(cx to cy, p1, p2, p3, t))
                            }
                            cx = p3.first
                            cy = p3.second
                        }
                    }
                    "Z" -> {
                        cx = startX
                        cy = startY
                        points.add(cx to cy)
                    }
                    "E" -> return points // end marker
                    else -> return null
                }
            }
        } catch (_: Exception) {
            return null
        }
        return points
    }

    private fun cubic(
        p0: Pair<Double, Double>,
        p1: Pair<Double, Double>,
        p2: Pair<Double, Double>,
        p3: Pair<Double, Double>,
        t: Double
    ): Pair<Double, Double> {
        val u = 1 - t
        val x = u * u * u * p0.first + 3 * u * u * t * p1.first + 3 * u * t * t * p2.first + t * t * t * p3.first
        val y = u * u * u * p0.second + 3 * u * u * t * p1.second + 3 * u * t * t * p2.second + t * t * t * p3.second
        return x to y
    }

    private fun resampleByArcLength(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val cumulative = DoubleArray(points.size)
        for (index in 1 until points.size) {
            val dx = points[index].first - points[index - 1].first
            val dy = points[index].second - points[index - 1].second
            cumulative[index] = cumulative[index - 1] + kotlin.math.hypot(dx, dy)
        }
        val total = cumulative.last()
        if (total <= 0.0) return listOf(points.first(), points.last())
        val result = mutableListOf<Pair<Double, Double>>()
        var seg = 0
        for (index in 0 until RESAMPLE_POINTS) {
            val target = total * index / (RESAMPLE_POINTS - 1)
            while (seg < points.size - 2 && cumulative[seg + 1] < target) seg++
            val segLen = cumulative[seg + 1] - cumulative[seg]
            val t = if (segLen <= 0.0) 0.0 else (target - cumulative[seg]) / segLen
            result.add(
                (points[seg].first + (points[seg + 1].first - points[seg].first) * t) to
                    (points[seg].second + (points[seg + 1].second - points[seg].second) * t)
            )
        }
        return result
    }
}
