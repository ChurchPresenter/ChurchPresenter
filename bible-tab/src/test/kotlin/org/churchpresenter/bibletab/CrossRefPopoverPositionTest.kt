package org.churchpresenter.bibletab

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossRefPopoverPositionTest {

    private val window = IntSize(1000, 800)
    private val popover = IntSize(380, 420)

    private fun at(anchor: IntRect, popup: IntSize = popover, win: IntSize = window): IntOffset =
        CrossRefPopoverPosition.calculatePosition(anchor, win, LayoutDirection.Ltr, popup)

    private fun chip(left: Int, top: Int) = IntRect(left, top, left + 40, top + 20)

    @Test
    fun `hangs under the chip with their right edges aligned`() {
        val anchor = chip(left = 600, top = 100)
        assertEquals(IntOffset(anchor.right - popover.width, anchor.bottom + 6), at(anchor))
    }

    @Test
    fun `flips above the chip when there is no room below`() {
        val anchor = chip(left = 600, top = 700)
        assertEquals(IntOffset(anchor.right - popover.width, anchor.top - 6 - popover.height), at(anchor))
    }

    @Test
    fun `stays below while it exactly fits`() {
        val anchor = chip(left = 600, top = 348)
        val offset = at(anchor)
        assertEquals(anchor.bottom + 6, offset.y, "374 + 420 == 794, inside an 800px window")
        assertTrue(offset.y + popover.height <= window.height)
    }

    @Test
    fun `never runs off the left edge of a narrow window`() {
        val offset = at(chip(left = 10, top = 100), win = IntSize(300, 800))
        assertEquals(0, offset.x)
    }

    @Test
    fun `never runs off the right edge`() {
        val offset = at(chip(left = 980, top = 100))
        assertTrue(offset.x + popover.width <= window.width, "clamped to the window")
    }

    @Test
    fun `clamps rather than going negative when it is taller than the window`() {
        val anchor = chip(left = 600, top = 400)
        val offset = at(anchor, popup = IntSize(380, 900))
        assertEquals(0, offset.y, "a popover taller than the window starts at the top")
        assertEquals(anchor.right - 380, offset.x, "it still fits across, so the right edges align")
    }
}
