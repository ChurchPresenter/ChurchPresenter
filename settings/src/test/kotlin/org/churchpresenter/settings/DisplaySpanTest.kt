package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The geometry of merged real displays (#721): which sets of monitors tile one picture. */
class DisplaySpanTest {

    private val left = DisplayRect(1920, 0, 1920, 1080)
    private val right = DisplayRect(3840, 0, 1920, 1080)

    @Test
    fun `a screen key reads back as the rectangle it names`() {
        assertEquals(left, parseScreenKey(left.key))
        assertEquals(DisplayRect(-1920, -40, 1920, 1080), parseScreenKey("1920x1080@-1920,-40"))
        assertNull(parseScreenKey(""))
        assertNull(parseScreenKey("0x1080@0,0"), "a monitor with no width is no monitor")
        assertNull(parseScreenKey("wide@0,0"))
    }

    @Test
    fun `two TVs side by side make one 3840 by 1080 picture`() {
        assertNull(spanProblem(listOf(left, right)))
        assertEquals(DisplayRect(1920, 0, 3840, 1080), unionOf(listOf(right, left)))
    }

    @Test
    fun `stacked monitors and a two by two wall can be merged`() {
        val below = DisplayRect(1920, 1080, 1920, 1080)
        assertNull(spanProblem(listOf(left, below)))
        assertNull(spanProblem(listOf(left, right, below, DisplayRect(3840, 1080, 1920, 1080))))
    }

    @Test
    fun `one monitor is nothing to merge`() {
        assertEquals(SpanProblem.TOO_FEW, spanProblem(listOf(left)))
        assertEquals(SpanProblem.TOO_FEW, spanProblem(emptyList()))
    }

    @Test
    fun `a gap, an overlap or an L shape is not one picture`() {
        val gap = DisplayRect(4000, 0, 1920, 1080)
        val overlap = DisplayRect(2000, 0, 1920, 1080)
        val shorter = DisplayRect(3840, 0, 1920, 900)
        assertEquals(SpanProblem.NOT_A_RECTANGLE, spanProblem(listOf(left, gap)))
        assertEquals(SpanProblem.NOT_A_RECTANGLE, spanProblem(listOf(left, overlap)))
        assertEquals(SpanProblem.NOT_A_RECTANGLE, spanProblem(listOf(left, shorter)))
        assertEquals(
            SpanProblem.NOT_A_RECTANGLE,
            spanProblem(listOf(left, right, DisplayRect(1920, 1080, 1920, 1080))),
        )
    }

    @Test
    fun `the union of nothing is refused`() {
        assertFailsWith<IllegalArgumentException> { unionOf(emptyList()) }
    }
}
