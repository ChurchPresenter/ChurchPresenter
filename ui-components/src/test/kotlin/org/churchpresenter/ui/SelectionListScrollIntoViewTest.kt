package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Following a selection that was changed from outside the list.
 *
 * A caller can move the selection without touching the list — the schedule selects a song, a remote
 * command selects a verse. When that lands on a row that is scrolled out of sight the list has to
 * bring it into view, or the operator sees an unchanged screen and believes nothing happened.
 *
 * It scrolls **only** when the row is not already visible: scrolling on every selection change
 * would jerk the list under the pointer on an ordinary click.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionListScrollIntoViewTest {

    private val rows = (1..200).map { "Row $it" }

    @Test
    fun `a selection moved off-screen is scrolled into view`() = runComposeUiTest {
        val selected = mutableIntStateOf(0)
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = rows,
                        selectedIndex = selected.intValue,
                        onItemSelected = { _, _ -> },
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Row 1").assertIsDisplayed()

        selected.intValue = 150
        waitForIdle()

        onNodeWithText("Row 151").assertIsDisplayed()
    }

    @Test
    fun `a selection already on screen does not move the list`() = runComposeUiTest {
        val selected = mutableIntStateOf(0)
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = rows,
                        selectedIndex = selected.intValue,
                        onItemSelected = { _, _ -> },
                    )
                }
            }
        }
        waitForIdle()
        selected.intValue = 1
        waitForIdle()
        onNodeWithText("Row 1").assertIsDisplayed()
        onNodeWithText("Row 2").assertIsDisplayed()
    }

    @Test
    fun `a selection outside the list is ignored rather than scrolled to`() = runComposeUiTest {
        val selected = mutableIntStateOf(0)
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = rows,
                        selectedIndex = selected.intValue,
                        onItemSelected = { _, _ -> },
                    )
                }
            }
        }
        waitForIdle()
        selected.intValue = 5000
        waitForIdle()
        onNodeWithText("Row 1").assertIsDisplayed()
    }

    @Test
    fun `a negative selection is ignored`() = runComposeUiTest {
        val selected = mutableIntStateOf(0)
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionListWithIndex(
                        list = rows,
                        selectedIndex = selected.intValue,
                        onItemSelected = { _, _ -> },
                    )
                }
            }
        }
        waitForIdle()
        selected.intValue = -1
        waitForIdle()
        onNodeWithText("Row 1").assertIsDisplayed()
    }

    @Test
    fun `the simple overload reports the item text`() = runComposeUiTest {
        var picked = ""
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp)) {
                    SelectionList(list = rows, selectedIndex = 0, onItemSelected = { picked = it })
                }
            }
        }
        waitForIdle()
        onNodeWithText("Row 3").performClick()
        assertEquals("Row 3", picked, "the item overload hands back the text, not the index")
    }
}
