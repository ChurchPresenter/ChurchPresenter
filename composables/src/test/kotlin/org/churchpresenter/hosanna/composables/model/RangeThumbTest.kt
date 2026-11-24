package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RangeThumbTest {

    @Test
    fun `a press grabs the nearer thumb, or the one it is beyond`() {
        assertEquals(RangeThumb.Start, nearestRangeThumb(10.0, 20.0, 70.0))
        assertEquals(RangeThumb.End, nearestRangeThumb(90.0, 20.0, 70.0))
        assertEquals(RangeThumb.Start, nearestRangeThumb(30.0, 20.0, 70.0))
        assertEquals(RangeThumb.End, nearestRangeThumb(60.0, 20.0, 70.0))
        assertEquals(RangeThumb.End, nearestRangeThumb(45.0, 20.0, 70.0))
        assertEquals(RangeThumb.End, nearestRangeThumb(50.0, 50.0, 50.0))
    }

    @Test
    fun `thumbs move but never cross`() {
        assertEquals(30.0 to 70.0, moveRangeThumb(RangeThumb.Start, 30.0, 20.0, 70.0))
        assertEquals(70.0 to 70.0, moveRangeThumb(RangeThumb.Start, 90.0, 20.0, 70.0))
        assertEquals(20.0 to 50.0, moveRangeThumb(RangeThumb.End, 50.0, 20.0, 70.0))
        assertEquals(20.0 to 20.0, moveRangeThumb(RangeThumb.End, 5.0, 20.0, 70.0))
    }
}
