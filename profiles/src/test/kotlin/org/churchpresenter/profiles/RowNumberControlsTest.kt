@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class RowNumberControlsTest {

    @Test
    fun `a typed number is stored only when it is a whole number in range`() = runComposeUiTest {
        var value by mutableIntStateOf(10)
        var writes = 0
        setContent {
            MaterialTheme {
                RowNumberField(
                    value = value,
                    onValueChange = { writes++; value = it },
                    range = -5..50,
                    unit = "px",
                    caption = "Top",
                    width = 80.dp,
                    testTag = "field",
                )
            }
        }
        onNodeWithText("Top").assertExists()
        onNodeWithText("px").assertExists()
        onNodeWithTag("field").performTextReplacement("2a5")
        waitForIdle()
        assertEquals(25, value, "letters are dropped as they are typed")
        onNodeWithTag("field").performTextReplacement("-3")
        waitForIdle()
        assertEquals(-3, value)
        onNodeWithTag("field").performTextReplacement("99")
        waitForIdle()
        assertEquals(-3, value, "out of range is shown, not stored")
        onNodeWithTag("field").performTextReplacement("-")
        waitForIdle()
        onNodeWithTag("field").performTextReplacement("")
        waitForIdle()
        assertEquals(-3, value)
        assertEquals(2, writes)
    }

    @Test
    fun `the stepper keys clamp to the range and stop at its ends`() = runComposeUiTest {
        var value by mutableIntStateOf(4)
        setContent {
            MaterialTheme {
                Column {
                    RowStepper(
                        value = value,
                        onValueChange = { value = it },
                        range = 0..10,
                        step = 4,
                        unit = "pt",
                        testTag = "s",
                    )
                }
            }
        }
        val plus = onAllNodes(hasContentDescription("Increment"))[0]
        val minus = onAllNodes(hasContentDescription("Decrement"))[0]
        repeat(2) {
            plus.performClick()
            waitForIdle()
        }
        assertEquals(10, value)
        plus.performClick()
        waitForIdle()
        assertEquals(10, value, "disabled at the top")
        repeat(3) {
            minus.performClick()
            waitForIdle()
        }
        assertEquals(0, value)
    }
}
