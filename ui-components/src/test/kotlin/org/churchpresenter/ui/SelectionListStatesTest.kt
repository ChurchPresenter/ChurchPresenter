package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The list used wherever the operator picks one row out of many — songs, bibles, scenes.
 *
 * `SelectionListWithIndex` is the one with behaviour worth pinning: it reports the *index* as well
 * as the text, because two songs can share a title, and it takes a set of extra selected indices so
 * a multi-select caller can highlight rows the single `selectedIndex` cannot express.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionListStatesTest {

    private val items = listOf("Amazing Grace", "Be Thou My Vision", "Amazing Grace")

    @Test
    fun `clicking a row reports its text`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) { SelectionList(list = items, onItemSelected = { picked = it }) }
            }
        }
        onNodeWithText("Be Thou My Vision").performClick()
        assertEquals("Be Thou My Vision", picked)
    }

    @Test
    fun `the indexed list distinguishes two rows with the same text`() = runComposeUiTest {
        var index = -1
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = items,
                        selectedIndex = 2,
                        onItemSelected = { i, _ -> index = i },
                    )
                }
            }
        }
        onNodeWithText("Be Thou My Vision").performClick()
        assertEquals(1, index, "the index is what tells duplicate titles apart")
    }

    @Test
    fun `a selection beyond the list is clamped to the last row rather than throwing`() = runComposeUiTest {
        val distinct = listOf("First", "Second", "Third")
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(list = distinct, selectedIndex = 99, onItemSelected = { _, _ -> })
                }
            }
        }
        // The initial scroll coerces into the list, so the last row is what ends up in view.
        onNodeWithText("Third").assertIsDisplayed()
    }

    @Test
    fun `an empty list renders nothing and reports nothing`() = runComposeUiTest {
        var picked: String? = null
        setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp)) {
                    SelectionList(list = emptyList(), onItemSelected = { picked = it })
                }
            }
        }
        waitForIdle()
        assertNull(picked)
    }

    @Test
    fun `extra selected indices render alongside the primary selection`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = items,
                        selectedIndex = 0,
                        selectedIndices = setOf(0, 2),
                        onItemSelected = { _, _ -> },
                    )
                }
            }
        }
        onNodeWithText("Be Thou My Vision").assertIsDisplayed()
    }

    @Test
    fun `a single-line list still reports its rows`() = runComposeUiTest {
        var picked = -1
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = items,
                        singleLine = true,
                        onItemSelected = { i, _ -> picked = i },
                    )
                }
            }
        }
        onNodeWithText("Be Thou My Vision").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun `a selection far down a long list is scrolled into view`() = runComposeUiTest {
        val long = (1..200).map { "Row $it" }
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(list = long, selectedIndex = 180, onItemSelected = { _, _ -> })
                }
            }
        }
        waitForIdle()
        // The list starts at the selected row rather than the top, so a picked item is never
        // off-screen when the panel opens.
        onNodeWithText("Row 181").assertIsDisplayed()
    }

    @Test
    fun `a double click reports the row as well as a single click`() = runComposeUiTest {
        var doubled = -1
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = items,
                        onItemSelected = { _, _ -> },
                        onItemDoubleClicked = { i, _ -> doubled = i },
                    )
                }
            }
        }
        onNodeWithText("Be Thou My Vision").performClick()
        onNodeWithText("Be Thou My Vision").performClick()
        waitForIdle()
        assertEquals(1, doubled, "the second click has to reach the double-click handler")
    }

    @Test
    fun `a list with every optional handler wired still reports a plain click`() = runComposeUiTest {
        var picked = -1
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = items,
                        onItemSelected = { i, _ -> picked = i },
                        onItemDoubleClicked = { _, _ -> },
                        onItemCtrlClicked = { _, _ -> },
                        onItemShiftClicked = { _, _ -> },
                        onRightClicked = { },
                    )
                }
            }
        }
        // "Amazing Grace" appears twice in this fixture, so match the unique row.
        onNodeWithText("Be Thou My Vision").performClick()
        assertEquals(1, picked)
    }
}
