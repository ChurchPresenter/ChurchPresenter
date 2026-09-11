package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The corner rounding of one chip in a segmented row: round on the outside of the row, square
 * where it meets its neighbour, so a row of them reads as one control.
 */
class SegmentShapeTest {

    private val round = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    private val square = RoundedCornerShape(0.dp)
    private val pill = RoundedCornerShape(8.dp)

    @Test
    fun `the first of several is rounded on its leading edge only`() {
        assertEquals(round, segmentShape(index = 0, count = 3))
    }

    @Test
    fun `the last of several is rounded on its trailing edge only`() {
        assertEquals(
            RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            segmentShape(index = 2, count = 3),
        )
    }

    @Test
    fun `a middle segment is square on both sides`() {
        assertEquals(square, segmentShape(index = 1, count = 3))
    }

    @Test
    fun `a row of one is rounded on both sides`() {
        assertEquals(pill, segmentShape(index = 0, count = 1))
    }

    @Test
    fun `a row of two has no square-sided segment`() {
        assertNotEquals(square, segmentShape(0, 2))
        assertNotEquals(square, segmentShape(1, 2))
    }

    @Test
    fun `every middle of a long row shares one shape`() {
        val middles = (1..6).map { segmentShape(it, 8) }
        assertEquals(1, middles.distinct().size)
        assertEquals(square, middles.first())
    }
}
