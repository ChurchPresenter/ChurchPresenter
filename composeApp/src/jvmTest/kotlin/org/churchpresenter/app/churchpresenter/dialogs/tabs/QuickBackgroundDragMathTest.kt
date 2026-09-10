package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The Quick Background strip's reordering maths: where a dragged tile would land, how far the
 * tiles under the cursor slide to open the gap, and the list edit the drop finally makes.
 *
 * They are one decision split three ways, so the tests pair them: what [tileShift] opens a gap for
 * has to be the slot [dropIndexFor] names, and the slot has to be where [moved] puts the entry.
 */
class QuickBackgroundDragMathTest {

    private val stride = 100f

    // ── dropIndexFor ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a tile dropped where it started keeps its place`() {
        assertEquals(2, dropIndexFor(dragged = 2, offset = 0f, stridePx = stride, lastIndex = 5))
    }

    @Test
    fun `a drag of one full stride moves the tile one slot`() {
        assertEquals(3, dropIndexFor(dragged = 2, offset = stride, stridePx = stride, lastIndex = 5))
        assertEquals(1, dropIndexFor(dragged = 2, offset = -stride, stridePx = stride, lastIndex = 5))
    }

    @Test
    fun `half a stride rounds to the next slot, less than half stays put`() {
        assertEquals(3, dropIndexFor(dragged = 2, offset = stride / 2, stridePx = stride, lastIndex = 5))
        assertEquals(2, dropIndexFor(dragged = 2, offset = stride * 0.49f, stridePx = stride, lastIndex = 5))
    }

    @Test
    fun `dragging past either end lands on the end`() {
        assertEquals(5, dropIndexFor(dragged = 2, offset = stride * 20, stridePx = stride, lastIndex = 5))
        assertEquals(0, dropIndexFor(dragged = 2, offset = -stride * 20, stridePx = stride, lastIndex = 5))
    }

    @Test
    fun `nothing being dragged is answered with the same non-index`() {
        assertEquals(-1, dropIndexFor(dragged = -1, offset = stride * 3, stridePx = stride, lastIndex = 5))
    }

    @Test
    fun `an empty strip cannot be reordered`() {
        assertEquals(0, dropIndexFor(dragged = 0, offset = stride, stridePx = stride, lastIndex = -1))
    }

    @Test
    fun `a stride that has not been measured yet leaves the tile where it is`() {
        assertEquals(2, dropIndexFor(dragged = 2, offset = stride * 3, stridePx = 0f, lastIndex = 5))
        assertEquals(2, dropIndexFor(dragged = 2, offset = stride * 3, stridePx = -10f, lastIndex = 5))
    }

    // ── tileShift ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nothing moves while nothing is being dragged`() {
        (0..4).forEach { index ->
            assertEquals(0f, tileShift(index, dragged = -1, drop = 3, offset = 40f, stridePx = stride), "$index")
        }
    }

    @Test
    fun `the dragged tile follows the cursor exactly`() {
        assertEquals(37f, tileShift(index = 2, dragged = 2, drop = 2, offset = 37f, stridePx = stride))
    }

    @Test
    fun `dragging right slides the tiles it passes back by one stride`() {
        val shift = { i: Int -> tileShift(i, dragged = 1, drop = 3, offset = stride * 2, stridePx = stride) }
        assertEquals(0f, shift(0))
        assertEquals(-stride, shift(2))
        assertEquals(-stride, shift(3))
        assertEquals(0f, shift(4))
    }

    @Test
    fun `dragging left slides the tiles it passes forward by one stride`() {
        val shift = { i: Int -> tileShift(i, dragged = 3, drop = 1, offset = -stride * 2, stridePx = stride) }
        assertEquals(0f, shift(0))
        assertEquals(stride, shift(1))
        assertEquals(stride, shift(2))
        assertEquals(0f, shift(4))
    }

    @Test
    fun `a drag that has not yet reached another slot moves only itself`() {
        (0..4).filter { it != 2 }.forEach { index ->
            assertEquals(0f, tileShift(index, dragged = 2, drop = 2, offset = 10f, stridePx = stride), "$index")
        }
        assertEquals(10f, tileShift(2, dragged = 2, drop = 2, offset = 10f, stridePx = stride))
    }

    @Test
    fun `exactly one gap is opened, wherever the drop lands`() {
        listOf(0, 1, 2, 3, 4).forEach { drop ->
            val shifted = (0..4).count { tileShift(it, dragged = 2, drop = drop, 0f, stride) != 0f }
            assertEquals(
                if (drop == 2) 0 else kotlin.math.abs(drop - 2),
                shifted,
                "drop=$drop",
            )
        }
    }

    // ── moved ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an entry dragged right lands after the ones it passed`() {
        assertEquals(listOf("a", "c", "d", "b", "e"), listOf("a", "b", "c", "d", "e").moved(1, 3))
    }

    @Test
    fun `an entry dragged left lands before the ones it passed`() {
        assertEquals(listOf("a", "d", "b", "c", "e"), listOf("a", "b", "c", "d", "e").moved(3, 1))
    }

    @Test
    fun `moving an entry to its own place is the same list, untouched`() {
        val original = listOf("a", "b", "c")
        assertSame(original, original.moved(1, 1))
    }

    @Test
    fun `an index outside the list is a no-op rather than a crash`() {
        val original = listOf("a", "b", "c")
        assertSame(original, original.moved(-1, 1))
        assertSame(original, original.moved(1, 9))
        assertSame(original, original.moved(9, 1))
    }

    @Test
    fun `an empty list survives a move`() {
        assertEquals(emptyList(), emptyList<String>().moved(0, 0))
    }

    @Test
    fun `every entry survives the move`() {
        val original = ('a'..'g').map { it.toString() }
        val moved = original.moved(5, 0)
        assertEquals(original.toSet(), moved.toSet())
        assertEquals(original.size, moved.size)
        assertEquals("f", moved.first())
    }

    // ── the three together ────────────────────────────────────────────────────────────────────

    @Test
    fun `the gap the tiles open is the slot the drop actually uses`() {
        val tiles = listOf("a", "b", "c", "d", "e")
        val drop = dropIndexFor(dragged = 1, offset = stride * 2, stridePx = stride, lastIndex = tiles.lastIndex)
        assertEquals(3, drop)
        // Everything between the old slot and the new one has slid back by one, which is exactly
        // what moving "b" past them produces.
        assertEquals(-stride, tileShift(2, dragged = 1, drop = drop, offset = stride * 2, stridePx = stride))
        assertEquals(-stride, tileShift(3, dragged = 1, drop = drop, offset = stride * 2, stridePx = stride))
        assertEquals(listOf("a", "c", "d", "b", "e"), tiles.moved(1, drop))
    }
}
