package org.churchpresenter.schedule

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a reorder drag points, over generated lists and cursor positions rather than the three
 * rows [ScheduleDragMathTest] pins: the delete zone wins exactly when the cursor is in it, a
 * target is always a row whose span holds the cursor -- the first such row -- and there is no
 * target only when no row holds it. The seed is pinned, so every run checks the same drags.
 */
class ScheduleDragMathPropertyTest {

    /** Visible rows top to bottom, each starting at or after the last one's end. */
    private val rows = Arb.list(Arb.int(0..80), 0..12).let { sizes ->
        Arb.bind(sizes, Arb.list(Arb.int(0..20), 12..12), Arb.int(0..30)) { s, gaps, first ->
            var offset = first
            s.mapIndexed { i, size ->
                DragItemGeometry(index = first + i, offset = offset, size = size).also { offset += size + gaps[i] }
            }
        }
    }

    @Test
    fun `a drag points at the first row under the cursor, unless it is over the delete zone`() = runBlocking<Unit> {
        checkAll(
            PropTestConfig(seed = 20_261_008L, iterations = 1_000),
            rows,
            Arb.float(-50f..1_200f),
            Arb.int(0..1_000),
            Arb.float(0f..120f),
        ) { visible, cursorY, listHeight, deleteZone ->
            val hit = dragDropTarget(cursorY, listHeight, deleteZone, visible)
            val inZone = listHeight > 0 && cursorY >= listHeight - deleteZone
            assertEquals(inZone, hit.overDeleteZone, "cursor $cursorY, list $listHeight, zone $deleteZone")
            if (inZone) {
                assertNull(hit.targetIndex, "a delete drop has no target")
            } else {
                val under = visible.filter { cursorY >= it.offset && cursorY <= it.offset + it.size }
                assertEquals(under.firstOrNull()?.index, hit.targetIndex, "cursor $cursorY over $visible")
                hit.targetIndex?.let { target -> assertTrue(under.any { it.index == target }) }
            }
        }
    }
}
