@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drag-to-reorder, over a real laid-out list.
 *
 * It can only be driven against real layout: every decision it makes reads
 * `LazyListState.layoutInfo` — where each row sits and how tall it is — so a fake list state would
 * be testing the test. The rows here are a fixed height, which is what makes "dragged past the row
 * below" a number rather than a guess.
 */
class ReorderStateTest {

    private val rowHeight = 40.dp

    /**
     * A list of [keys] with a reorder state over it, and [body] driven against both.
     *
     * The last key is never a drop target — the run of show ends with an "add" row, and dropping
     * onto it is the case that has to do nothing.
     */
    private fun withList(
        vararg keys: String,
        body: ComposeUiTest.(reorder: ReorderState, order: MutableList<String>, moves: List<Pair<Any, Any>>) -> Unit,
    ) {
        val order = mutableStateListOf(*keys)
        val moves = mutableListOf<Pair<Any, Any>>()
        lateinit var reorder: ReorderState
        runComposeUiTest {
            setContent {
                val listState = rememberLazyListState()
                reorder = rememberReorderState(
                    listState = listState,
                    isTarget = { it != "add" },
                    onMove = { from, to ->
                        moves += from to to
                        val fromIndex = order.indexOf(from)
                        val toIndex = order.indexOf(to)
                        if (fromIndex >= 0 && toIndex >= 0) order.add(toIndex, order.removeAt(fromIndex))
                    },
                )
                LazyColumn(state = listState, modifier = Modifier.height(rowHeight * 10)) {
                    items(order.toList(), key = { it }) { key ->
                        Box(Modifier.fillMaxWidth().height(rowHeight)) { Text(key) }
                    }
                }
            }
            waitForIdle()
            body(reorder, order, moves)
        }
    }

    @Test
    fun `a row dragged onto the one below swaps with it`() = withList("a", "b", "c", "add") { reorder, order, moves ->
        runOnIdle { reorder.start("a") }
        assertEquals("a", reorder.draggingKey)
        assertTrue(reorder.isDragging("a"))

        runOnIdle { reorder.drag(rowHeight.value) }
        waitForIdle()

        assertEquals(listOf("b", "a", "c", "add"), order.toList())
        assertEquals(listOf<Pair<Any, Any>>("a" to "b"), moves)
    }

    @Test
    fun `a nudge too small to reach the next row moves nothing`() =
        withList("a", "b", "c", "add") { reorder, order, moves ->
            runOnIdle { reorder.start("a") }
            runOnIdle { reorder.drag(2f) }
            waitForIdle()

            assertEquals(listOf("a", "b", "c", "add"), order.toList())
            assertTrue(moves.isEmpty())
            assertEquals(2f, reorder.translationFor("a"), "it still follows the pointer")
            assertEquals(0f, reorder.translationFor("b"), "and nothing else does")
        }

    @Test
    fun `the row keeps up with the pointer across a swap`() = withList("a", "b", "c", "add") { reorder, _, _ ->
        runOnIdle { reorder.start("a") }
        runOnIdle { reorder.drag(rowHeight.value) }
        waitForIdle()

        // Rebased, not reset: the row is where the pointer left it, not where the layout put it.
        // Without this it jumps a full row height on every swap.
        assertEquals(0f, reorder.translationFor("a"))
    }

    @Test
    fun `a row cannot be dropped on something that is not a target`() =
        withList("a", "add") { reorder, order, moves ->
            runOnIdle { reorder.start("a") }
            runOnIdle { reorder.drag(rowHeight.value) }
            waitForIdle()

            assertEquals(listOf("a", "add"), order.toList())
            assertTrue(moves.isEmpty(), "the add row is not a place to put a song")
        }

    @Test
    fun `dragging without having started does nothing`() = withList("a", "b", "add") { reorder, order, moves ->
        runOnIdle { reorder.drag(rowHeight.value * 2) }
        waitForIdle()

        assertNull(reorder.draggingKey)
        assertEquals(listOf("a", "b", "add"), order.toList())
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `a row that is not in the list cannot be picked up`() = withList("a", "add") { reorder, _, _ ->
        runOnIdle { reorder.start("gone") }

        assertNull(reorder.draggingKey, "there is nothing under the pointer to move")
    }

    @Test
    fun `letting go puts the row back in line`() = withList("a", "b", "add") { reorder, _, _ ->
        runOnIdle { reorder.start("a") }
        runOnIdle { reorder.drag(5f) }

        runOnIdle { reorder.end() }

        assertNull(reorder.draggingKey)
        assertEquals(0f, reorder.translationFor("a"))
        assertTrue(!reorder.isDragging("a"))
    }

    @Test
    fun `a row dragged the length of the list passes each row on the way`() =
        withList("a", "b", "c", "add") { reorder, order, moves ->
            runOnIdle { reorder.start("a") }
            runOnIdle { reorder.drag(rowHeight.value) }
            waitForIdle()
            runOnIdle { reorder.drag(rowHeight.value) }
            waitForIdle()

            assertEquals(listOf("b", "c", "a", "add"), order.toList())
            assertEquals(2, moves.size, "one swap per row crossed, not one jump at the end")
        }
}
