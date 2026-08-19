package presentation.engine

import presentation.engine.timeline.MotionPathFlattener
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flattening a PowerPoint `animMotion` path into the polyline the player walks.
 *
 * The load-bearing property is the arc-length resampling: the points must be evenly spaced along
 * the path, not evenly spaced in parameter. Without it a shape crawls through curves and races
 * down straights, which is the difference between an animation looking authored and looking
 * broken. Assertions are on that invariant rather than on exact coordinates, since the
 * subdivision count is an implementation detail.
 */
class MotionPathFlattenerTest {

    private fun spacings(points: List<Pair<Double, Double>>): List<Double> =
        points.zipWithNext { (ax, ay), (bx, by) -> hypot(bx - ax, by - ay) }

    @Test
    fun `a straight line flattens to a polyline from start to end`() {
        val points = MotionPathFlattener.flatten("M 0 0 L 0.5 0 E")!!
        assertTrue(points.size >= 2)
        assertEquals(0.0, points.first().first, 1e-6)
        assertEquals(0.0, points.first().second, 1e-6)
        assertEquals(0.5, points.last().first, 1e-6)
        assertEquals(0.0, points.last().second, 1e-6)
    }

    @Test
    fun `points along a straight line are evenly spaced`() {
        val gaps = spacings(MotionPathFlattener.flatten("M 0 0 L 1 0 E")!!)
        val mean = gaps.average()
        assertTrue(gaps.all { abs(it - mean) < mean * 0.05 }, "even spacing, got $gaps")
    }

    @Test
    fun `a curve is resampled to near-uniform spacing rather than uniform parameter`() {
        // A cubic with clustered control points travels much faster at one end in parameter space;
        // after arc-length resampling the step size must still be near-constant.
        val points = MotionPathFlattener.flatten("M 0 0 C 0.05 0.4 0.1 0.4 1 0 E")!!
        val gaps = spacings(points)
        val mean = gaps.average()
        assertTrue(mean > 0.0)
        assertTrue(
            gaps.all { abs(it - mean) < mean * 0.35 },
            "no segment is wildly longer than the mean (mean=$mean): ${gaps.map { "%.4f".format(it) }}",
        )
    }

    @Test
    fun `a multi-segment polyline keeps its corners in order`() {
        val points = MotionPathFlattener.flatten("M 0 0 L 0.5 0 L 0.5 0.5 E")!!
        assertEquals(0.0 to 0.0, points.first().let { it.first to it.second })
        assertEquals(0.5, points.last().first, 1e-6)
        assertEquals(0.5, points.last().second, 1e-6)
        // The path turns, so some point must sit near the corner.
        assertTrue(points.any { abs(it.first - 0.5) < 0.05 && abs(it.second) < 0.05 })
    }

    @Test
    fun `an L command may carry several coordinate pairs`() {
        val points = MotionPathFlattener.flatten("M 0 0 L 0.2 0 0.4 0 0.6 0 E")!!
        assertEquals(0.6, points.last().first, 1e-6)
    }

    @Test
    fun `lower-case commands are relative to the current point`() {
        val absolute = MotionPathFlattener.flatten("M 0 0 L 0.3 0 E")!!
        val relative = MotionPathFlattener.flatten("M 0 0 l 0.3 0 E")!!
        assertEquals(absolute.last().first, relative.last().first, 1e-6)

        // Two relative steps accumulate rather than restating an absolute position.
        val twoSteps = MotionPathFlattener.flatten("M 0 0 l 0.2 0 l 0.2 0 E")!!
        assertEquals(0.4, twoSteps.last().first, 1e-6)
    }

    @Test
    fun `a closed path returns to its start`() {
        val points = MotionPathFlattener.flatten("M 0 0 L 0.4 0 L 0.4 0.4 Z E")!!
        assertEquals(points.first().first, points.last().first, 1e-6)
        assertEquals(points.first().second, points.last().second, 1e-6)
    }

    @Test
    fun `a path with a single point is returned unchanged rather than resampled`() {
        val points = MotionPathFlattener.flatten("M 0.5 0.5 E")!!
        assertEquals(1, points.size)
        assertEquals(0.5, points.single().first, 1e-6)
    }

    @Test
    fun `commas are accepted as coordinate separators`() {
        val points = MotionPathFlattener.flatten("M 0,0 L 0.5,0.25 E")!!
        assertEquals(0.5, points.last().first, 1e-6)
        assertEquals(0.25, points.last().second, 1e-6)
    }

    @Test
    fun `an unparseable path yields null so the effect can degrade`() {
        assertNull(MotionPathFlattener.flatten(""))
        assertNull(MotionPathFlattener.flatten("   "))
        assertNull(MotionPathFlattener.flatten("M 0"), "a truncated coordinate pair")
        assertNull(MotionPathFlattener.flatten("M zero zero L 1 1"))
    }
}
