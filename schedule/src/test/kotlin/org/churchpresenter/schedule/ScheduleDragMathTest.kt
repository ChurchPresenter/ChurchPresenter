package org.churchpresenter.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reorder-drag hit-testing extracted from ScheduleTab's pointer loop. A wrong result drops a
 * card on the wrong row or fails to notice the delete zone, so the delete-zone boundary, the
 * row-span containment (including the shared-edge tie-break), the gaps, and the ends are all pinned.
 */
class ScheduleDragMathTest {

    // Three contiguous 50px rows: [0,50) [50,100) [100,150).
    private val rows = listOf(
        DragItemGeometry(index = 0, offset = 0, size = 50),
        DragItemGeometry(index = 1, offset = 50, size = 50),
        DragItemGeometry(index = 2, offset = 100, size = 50),
    )

    // ── delete zone ────────────────────────────────────────────────────────────

    @Test
    fun `reaching the bottom strip is over the delete zone and computes no target`() {
        val hit = dragDropTarget(cursorY = 950f, listHeightPx = 1000, deleteZonePx = 56f, visibleItems = rows)
        assertTrue(hit.overDeleteZone)
        assertNull(hit.targetIndex, "a delete drop needs no reorder target")
    }

    @Test
    fun `just above the delete zone is not over it`() =
        assertFalse(dragDropTarget(943f, listHeightPx = 1000, deleteZonePx = 56f, visibleItems = rows).overDeleteZone)

    @Test
    fun `a zero-height list disables the delete zone`() =
        assertFalse(dragDropTarget(950f, listHeightPx = 0, deleteZonePx = 56f, visibleItems = rows).overDeleteZone)

    // ── row targeting ───────────────────────────────────────────────────────────

    @Test
    fun `a cursor inside a row targets that row`() =
        assertEquals(1, dragDropTarget(70f, 1000, 56f, rows).targetIndex, "70px lands in row 1's [50,100) span")

    @Test
    fun `at a shared row boundary the earlier row wins`() =
        // 50px is the bottom edge of row 0 and the top edge of row 1; firstOrNull picks row 0.
        assertEquals(0, dragDropTarget(50f, 1000, 56f, rows).targetIndex)

    @Test
    fun `a cursor in a gap between rows targets nothing`() {
        val gapped = listOf(DragItemGeometry(0, 0, 40), DragItemGeometry(1, 60, 40))
        assertNull(dragDropTarget(50f, 1000, 56f, gapped).targetIndex, "50px is in the 40..60 gap")
    }

    @Test
    fun `a cursor above every row targets nothing`() {
        val below = listOf(DragItemGeometry(0, 100, 50), DragItemGeometry(1, 150, 50))
        assertNull(dragDropTarget(10f, 1000, 56f, below).targetIndex)
    }

    @Test
    fun `an empty visible list targets nothing`() =
        assertNull(dragDropTarget(70f, 1000, 56f, emptyList()).targetIndex)
}
