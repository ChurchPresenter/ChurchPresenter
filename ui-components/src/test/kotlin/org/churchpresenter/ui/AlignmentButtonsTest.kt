package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The three icon-button groups the text editors align content with.
 *
 * Each group is a row of buttons that all look alike and carry no text, so what is asserted here is
 * position: the groups are documented as rendering right-to-left (`HorizontalAlignmentButtons` puts
 * Right first), and a caller wiring the values in the reading order they expect would silently get
 * the opposite alignment. Clicking by index is the only thing that pins it.
 */
@OptIn(ExperimentalTestApi::class)
class AlignmentButtonsTest {

    private fun clicks(index: Int, content: @androidx.compose.runtime.Composable ((String) -> Unit) -> Unit): String? {
        var picked: String? = null
        runComposeUiTest {
            setContent { MaterialTheme { content { picked = it } } }
            onAllNodes(hasClickAction())[index].performClick()
        }
        return picked
    }

    @Test
    fun `horizontal buttons run right, centre, left`() {
        val order = (0..2).map { i ->
            clicks(i) { onChange ->
                HorizontalAlignmentButtons("", onChange, leftValue = "L", centerValue = "C", rightValue = "R")
            }
        }
        assertEquals(listOf("R", "C", "L"), order, "the row is drawn right-to-left")
    }

    @Test
    fun `vertical buttons run bottom, middle, top`() {
        val order = (0..2).map { i ->
            clicks(i) { onChange ->
                VerticalAlignmentButtons("", onChange, topValue = "T", middleValue = "M", bottomValue = "B")
            }
        }
        assertEquals(listOf("B", "M", "T"), order, "the row is drawn bottom-to-top")
    }

    @Test
    fun `position buttons run above then below`() {
        val order = (0..1).map { i ->
            clicks(i) { onChange -> PositionButtons("", onChange, aboveValue = "A", belowValue = "B") }
        }
        assertEquals(listOf("A", "B"), order)
    }

    @Test
    fun `the selected value is still clickable, so re-picking it is not swallowed`() {
        assertEquals(
            "R",
            clicks(0) { onChange ->
                HorizontalAlignmentButtons("R", onChange, leftValue = "L", centerValue = "C", rightValue = "R")
            },
        )
    }

    @Test
    fun `a group renders with no selection matching any of its values`() {
        var picked: String? = null
        runComposeUiTest {
            setContent {
                MaterialTheme { HorizontalAlignmentButtons("nothing", { picked = it }, "L", "C", "R") }
            }
            waitForIdle()
        }
        assertNull(picked, "drawing an unmatched selection must not fire a change on its own")
    }
}
