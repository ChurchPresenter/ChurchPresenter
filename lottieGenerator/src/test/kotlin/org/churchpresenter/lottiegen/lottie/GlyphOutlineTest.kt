package org.churchpresenter.lottiegen.lottie

import java.awt.geom.PathIterator
import kotlin.test.Test
import kotlin.test.assertEquals

class GlyphOutlineTest {

    /** A path iterator over raw segments, so one can start without a move or carry an unknown code. */
    private class Segments(private val segments: List<Pair<Int, DoubleArray>>) : PathIterator {
        private var i = 0
        override fun getWindingRule() = PathIterator.WIND_NON_ZERO
        override fun isDone() = i >= segments.size
        override fun next() { i++ }
        override fun currentSegment(coords: FloatArray): Int = error("unused")
        override fun currentSegment(coords: DoubleArray): Int {
            val (type, values) = segments[i]
            values.copyInto(coords)
            return type
        }
    }

    private fun seg(type: Int, vararg c: Double) = type to c

    @Test
    fun `segments before a move are dropped and a closing vertex on the start is merged`() {
        val contours = outlineToContours(
            Segments(
                listOf(
                    seg(PathIterator.SEG_LINETO, 1.0, 1.0),
                    seg(PathIterator.SEG_QUADTO, 1.0, 1.0, 2.0, 2.0),
                    seg(PathIterator.SEG_CUBICTO, 1.0, 1.0, 2.0, 2.0, 3.0, 3.0),
                    seg(PathIterator.SEG_CLOSE),
                    seg(PathIterator.SEG_MOVETO, 0.0, 0.0),
                    seg(PathIterator.SEG_LINETO, 4.0, 0.0),
                    seg(PathIterator.SEG_QUADTO, 4.0, 4.0, 0.0, 4.0),
                    seg(PathIterator.SEG_CUBICTO, 0.0, 3.0, 0.0, 1.0, 0.0, 0.0),
                    seg(99),
                    seg(PathIterator.SEG_CLOSE),
                    seg(PathIterator.SEG_MOVETO, 9.0, 9.0),
                    seg(PathIterator.SEG_CLOSE),
                    seg(PathIterator.SEG_MOVETO, 5.0, 5.0),
                    seg(PathIterator.SEG_LINETO, 6.0, 5.0),
                    seg(PathIterator.SEG_CLOSE),
                ),
            ),
        )
        assertEquals(listOf(3, 1, 2), contours.map { it.v.size })
        assertEquals(listOf(0.0, 1.0), contours.first().inTan[0].toList())
    }
}
