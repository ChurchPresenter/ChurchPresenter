package org.churchpresenter.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The overflow arrows a scrollable tab strip places either side of itself.
 *
 * Each appears only when there is somewhere to go in its own direction — a strip that fits shows
 * neither, and the arrow at the end of a scroll disappears. That conditional is the whole widget:
 * an arrow that stays visible at the end of the strip is a control that does nothing when pressed.
 */
@OptIn(ExperimentalTestApi::class)
class TabStripArrowsTest {

    @Test
    fun `a strip that fits shows neither arrow`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val state = rememberScrollState()
                Row {
                    TabStripBackArrow(state)
                    Box(Modifier.width(400.dp)) {
                        Row(Modifier.horizontalScroll(state)) { Text("only tab") }
                    }
                    TabStripForwardArrow(state)
                }
            }
        }
        waitForIdle()
        assertEquals(
            0,
            onAllNodesWithTag(TAB_STRIP_ARROW_BACK_TAG).fetchSemanticsNodes(false).size +
                onAllNodesWithTag(TAB_STRIP_ARROW_FORWARD_TAG).fetchSemanticsNodes(false).size,
            "nothing overflows, so neither arrow has anywhere to go",
        )
    }

    @Test
    fun `at the start only the forward arrow is offered`() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            MaterialTheme {
                state = rememberScrollState()
                // Laid out side by side, not stacked: overlapping arrows would put one on top of
                // the other and a click by tag would land on whichever drew last.
                Row {
                    TabStripBackArrow(state)
                    Box(Modifier.width(200.dp)) {
                        Row(Modifier.horizontalScroll(state)) {
                            repeat(20) { Text("Tab $it", Modifier.width(100.dp)) }
                        }
                    }
                    TabStripForwardArrow(state)
                }
            }
        }
        waitForIdle()
        assertEquals(0, state.value, "the strip starts at the left")
        assertEquals(0, onAllNodesWithTag(TAB_STRIP_ARROW_BACK_TAG).fetchSemanticsNodes(false).size)
        onNodeWithTag(TAB_STRIP_ARROW_FORWARD_TAG).assertIsDisplayed()
    }

    @Test
    fun `pressing forward scrolls the strip along`() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            MaterialTheme {
                state = rememberScrollState()
                // Laid out side by side, not stacked: overlapping arrows would put one on top of
                // the other and a click by tag would land on whichever drew last.
                Row {
                    TabStripBackArrow(state)
                    Box(Modifier.width(200.dp)) {
                        Row(Modifier.horizontalScroll(state)) {
                            repeat(20) { Text("Tab $it", Modifier.width(100.dp)) }
                        }
                    }
                    TabStripForwardArrow(state)
                }
            }
        }
        waitForIdle()
        onNodeWithTag(TAB_STRIP_ARROW_FORWARD_TAG).performClick()
        waitForIdle()
        assertTrue(state.value > 0, "the arrow has to actually move the strip, was ${state.value}")
    }

    @Test
    fun `once scrolled the back arrow appears`() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            MaterialTheme {
                state = rememberScrollState()
                // Laid out side by side, not stacked: overlapping arrows would put one on top of
                // the other and a click by tag would land on whichever drew last.
                Row {
                    TabStripBackArrow(state)
                    Box(Modifier.width(200.dp)) {
                        Row(Modifier.horizontalScroll(state)) {
                            repeat(20) { Text("Tab $it", Modifier.width(100.dp)) }
                        }
                    }
                    TabStripForwardArrow(state)
                }
            }
        }
        waitForIdle()
        onNodeWithTag(TAB_STRIP_ARROW_FORWARD_TAG).performClick()
        waitForIdle()
        onNodeWithTag(TAB_STRIP_ARROW_BACK_TAG).assertIsDisplayed()
    }

    @Test
    fun `pressing back returns towards the start`() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            MaterialTheme {
                state = rememberScrollState()
                // Laid out side by side, not stacked: overlapping arrows would put one on top of
                // the other and a click by tag would land on whichever drew last.
                Row {
                    TabStripBackArrow(state)
                    Box(Modifier.width(200.dp)) {
                        Row(Modifier.horizontalScroll(state)) {
                            repeat(20) { Text("Tab $it", Modifier.width(100.dp)) }
                        }
                    }
                    TabStripForwardArrow(state)
                }
            }
        }
        waitForIdle()
        onNodeWithTag(TAB_STRIP_ARROW_FORWARD_TAG).performClick()
        waitForIdle()
        val advanced = state.value
        onNodeWithTag(TAB_STRIP_ARROW_BACK_TAG).performClick()
        waitForIdle()
        assertTrue(state.value < advanced, "back must undo some of the move, was ${state.value} of $advanced")
    }

    @Test
    fun `the forward arrow goes once the strip is at its end`() = runComposeUiTest {
        lateinit var state: ScrollState
        setContent {
            MaterialTheme {
                state = rememberScrollState()
                Row {
                    Box(Modifier.width(200.dp)) {
                        Row(Modifier.horizontalScroll(state)) {
                            repeat(20) { Text("Tab $it", Modifier.width(100.dp)) }
                        }
                    }
                    TabStripForwardArrow(state)
                }
            }
        }
        waitForIdle()
        repeat(30) {
            val n = onAllNodesWithTag(TAB_STRIP_ARROW_FORWARD_TAG).fetchSemanticsNodes(false).size
            if (n == 0) return@runComposeUiTest
            onNodeWithTag(TAB_STRIP_ARROW_FORWARD_TAG).performClick()
            waitForIdle()
        }
        assertEquals(
            0,
            onAllNodesWithTag(TAB_STRIP_ARROW_FORWARD_TAG).fetchSemanticsNodes(false).size,
            "at the far end there is nowhere forward to go, so the arrow must not linger",
        )
    }
}
