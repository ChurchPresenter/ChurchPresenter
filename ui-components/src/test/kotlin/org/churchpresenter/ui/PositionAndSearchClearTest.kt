package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two controls whose click handlers no test was reaching: the above/below pair, and the cross that
 * empties a search box.
 */
@OptIn(ExperimentalTestApi::class)
class PositionAndSearchClearTest {

    @Test
    fun `each position button reports its own value`() {
        val picked = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    PositionButtons(
                        selectedPosition = "above",
                        onPositionChange = { picked += it },
                        aboveValue = "above",
                        belowValue = "below",
                    )
                }
            }
            onNodeWithContentDescription("Above").performClick()
            onNodeWithContentDescription("Below").performClick()
        }
        assertEquals(listOf("above", "below"), picked, "each button reports the value it was given")
    }

    @Test
    fun `the search box cross empties it`() {
        var text = "amazing"
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SearchField(value = text, onValueChange = { text = it }, placeholder = "Search…")
                }
            }
            onNode(hasSetTextAction()).performTextInput("!")
            waitForIdle()
            onNodeWithContentDescription("Clear search").performClick()
            waitForIdle()
        }
        assertEquals("", text, "the cross clears the field rather than only hiding itself")
    }
}
