@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two mouse gestures the selection list handles beside a plain click.
 *
 * A double click opens the thing that was clicked — a song into the editor — and a right click is
 * how the context menu is asked for. Both are wired only by some callers, and the list has to fall
 * back to a plain selection for the ones that do not.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionListPointerPathsTest {

    @Test
    fun `a double click selects and then opens`() {
        val selected = mutableListOf<Int>()
        val opened = mutableListOf<Int>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SelectionListWithIndex(
                        list = listOf("One", "Two", "Three"),
                        onItemSelected = { i, _ -> selected += i },
                        onItemDoubleClicked = { i, _ -> opened += i },
                    )
                }
            }
            onNodeWithText("Two").performClick()
            onNodeWithText("Two").performClick()
            waitForIdle()
        }
        assertEquals(listOf(1), opened, "the second click inside the window opens the item")
        assertTrue(selected.contains(1), "and it selects it first, so the opened item is the live one")
    }

    @Test
    fun `a right click reports the row it landed on`() {
        val menued = mutableListOf<Int>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SelectionListWithIndex(
                        list = listOf("One", "Two", "Three"),
                        onItemSelected = { _, _ -> },
                        onRightClicked = { menued += it },
                    )
                }
            }
            onNodeWithText("Three").performMouseInput { rightClick() }
            waitForIdle()
        }
        assertEquals(listOf(2), menued)
    }

    /**
     * The Release half of the gesture is button-agnostic, so a right click selects the row as well
     * as opening the menu on it — which is what a context menu wants: it acts on the row under the
     * pointer, and that row is now the selected one.
     */
    @Test
    fun `a right click also selects the row it landed on`() {
        val selected = mutableListOf<Int>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SelectionListWithIndex(
                        list = listOf("One", "Two"),
                        onItemSelected = { i, _ -> selected += i },
                    )
                }
            }
            onNodeWithText("Two").performMouseInput { rightClick() }
            waitForIdle()
        }
        assertEquals(listOf(1), selected, "the row under a right click becomes the selected row")
    }

    @Test
    fun `the list keeps working after its contents change underneath it`() {
        val selected = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var shrunk by remember { mutableStateOf(false) }
                    Column {
                        TextButton(onClick = { shrunk = true }) { Text("shrink") }
                        SelectionListWithIndex(
                            list = if (shrunk) listOf("One") else listOf("One", "Two", "Three"),
                            selectedIndex = 0,
                            singleLine = true,
                            onItemSelected = { _, item -> selected += item },
                        )
                    }
                }
            }
            onNodeWithText("Three").performClick()
            waitForIdle()
            onNodeWithText("shrink").performClick()
            waitForIdle()
            onNodeWithText("One").performClick()
            waitForIdle()
        }
        assertEquals(listOf("Three", "One"), selected, "the rows that survive still report themselves")
    }
}
