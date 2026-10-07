package org.churchpresenter.presentationengine.timeline

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

    /** A cubic segment's control points and end point. */
    private const val CUBIC_POINTS = 3

    fun flatten(path: String): List<Pair<Double, Double>>? {
        val points = parse(path) ?: return null
        if (points.size < 2) return points
        return resampleByArcLength(points)
    }

    private fun parse(path: String): List<Pair<Double, Double>>? {
        val tokens = path.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return try {
            PathReader(tokens).read()
        } catch (_: Exception) {
            null
        }
    }

    /** Walks the path's tokens command by command, keeping the pen and the subpath's start. */
    private class PathReader(private val tokens: List<String>) {
        private val points = mutableListOf<Pair<Double, Double>>()
        private var i = 0
        private var cx = 0.0
        private var cy = 0.0
        private var startX = 0.0
        private var startY = 0.0

        /** The polyline, or null at the first command that is unknown or short of numbers. */
        fun read(): List<Pair<Double, Double>>? {
            while (i < tokens.size) {
                val command = tokens[i]
                i++
                val relative = command.length == 1 && command[0].isLowerCase()
                val ok = when (command.uppercase()) {
                    "M" -> moveTo(relative)
                    "L" -> lineTo(relative)
                    "C" -> curveTo(relative)
                    "Z" -> closePath()
                    "E" -> return points // end marker
                    else -> false
                }
                if (!ok) return null
            }
            return points
        }

        private fun number(): Double? = tokens.getOrNull(i)?.toDoubleOrNull()?.also { i++ }

        private fun nextIsNumber(): Boolean = tokens.getOrNull(i)?.toDoubleOrNull() != null

        /** The next coordinate pair, made absolute when [relative]; null when it is incomplete. */
        private fun point(relative: Boolean): Pair<Double, Double>? {
            val x = number() ?: return null
            val y = number() ?: return null
            return if (relative) (cx + x) to (cy + y) else x to y
        }

        private fun moveTo(relative: Boolean): Boolean {
            val (x, y) = point(relative) ?: return false
            cx = x
            cy = y
            startX = cx
            startY = cy
            points.add(cx to cy)
            return true
        }

        /** Polyline form: L may be followed by several coordinate pairs. */
        private fun lineTo(relative: Boolean): Boolean {
            while (nextIsNumber()) {
                val (x, y) = point(relative) ?: return false
                cx = x
                cy = y
                points.add(cx to cy)
            }
            return true
        }

        private fun curveTo(relative: Boolean): Boolean {
            while (nextIsNumber()) {
                // All three points are relative to the pen where the segment starts.
                val (p1, p2, p3) = List(CUBIC_POINTS) { point(relative) }
                if (p1 == null || p2 == null || p3 == null) return false
                for (step in 1..CURVE_SUBDIVISIONS) {
                    val t = step.toDouble() / CURVE_SUBDIVISIONS
                    points.add(cubic(cx to cy, p1, p2, p3, t))
                }
                cx = p3.first
                cy = p3.second
            }
            return true
        }

        private fun closePath(): Boolean {
            cx = startX
            cy = startY
            points.add(cx to cy)
            return true
        }
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
