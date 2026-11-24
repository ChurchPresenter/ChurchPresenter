@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.hosanna.composables.model.TimeFieldActions
import org.churchpresenter.hosanna.composables.model.TimeFieldLabels
import org.churchpresenter.hosanna.composables.testing.HosannaSurface

class TimeFieldTest {

    private val labels = TimeFieldLabels("Hour", "Minute", "AM/PM", "AM", "PM")
    private val actions = TimeFieldActions("Now", "Done", "Later", "Earlier")

    private fun ComposeUiTest.field(
        start: Int,
        use24Hour: Boolean = false,
        enabled: Boolean = true,
        onChange: (Int) -> Unit = {},
    ): () -> Int {
        var minutes by mutableIntStateOf(start)
        setContent {
            HosannaSurface {
                TimeField(
                    minutes, { minutes = it; onChange(it) }, "From", labels, actions,
                    use24Hour = use24Hour, enabled = enabled, currentMinutes = { 615 },
                )
            }
        }
        return { minutes }
    }

    private fun ComposeUiTest.focused(): SemanticsNodeInteraction {
        val node = onNodeWithText("FROM")
        node.requestFocus()
        waitForIdle()
        return node
    }

    private fun SemanticsNodeInteraction.press(keys: List<Key>) =
        keys.forEach { key -> performKeyInput { pressKey(key) } }

    @Test
    fun `the field shows its label and the time`() = runComposeUiTest {
        field(1290)
        onNodeWithText("FROM").assertExists()
        onNodeWithText("9").assertExists()
        onNodeWithText("30").assertExists()
        onNodeWithText("PM").assertExists()
    }

    @Test
    fun `arrow keys step the active part and move between parts`() = runComposeUiTest {
        val minutes = field(480)
        val node = focused()
        node.press(listOf(Key.DirectionUp))
        waitForIdle()
        assertEquals(540, minutes())
        node.press(listOf(Key.DirectionRight, Key.DirectionUp))
        waitForIdle()
        assertEquals(545, minutes())
        node.press(listOf(Key.DirectionRight, Key.DirectionDown))
        waitForIdle()
        assertEquals(545 + 720, minutes())
    }

    @Test
    fun `typing digits and A or P sets the time`() = runComposeUiTest {
        val minutes = field(480)
        val node = focused()
        node.press(listOf(Key.Nine, Key.Three, Key.Zero, Key.P))
        waitForIdle()
        assertEquals(21 * 60 + 30, minutes())
        node.press(listOf(Key.A))
        waitForIdle()
        assertEquals(9 * 60 + 30, minutes())
    }

    @Test
    fun `a 24-hour field takes a two-digit hour`() = runComposeUiTest {
        val minutes = field(480, use24Hour = true)
        focused().press(listOf(Key.Two, Key.One, Key.Four, Key.Five))
        waitForIdle()
        assertEquals(21 * 60 + 45, minutes())
        onNodeWithText("AM").assertDoesNotExist()
    }

    @Test
    fun `Alt+Down opens the spinner, Escape closes it`() = runComposeUiTest {
        field(480)
        val node = focused()
        node.performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) } }
        waitForIdle()
        onNodeWithText("Done").assertExists()
        node.press(listOf(Key.Escape))
        waitForIdle()
        onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun `the spinner steps, picks, sets now and closes on Done`() = runComposeUiTest {
        val minutes = field(480)
        onNodeWithText("FROM").performMouseInput { click(center) }
        waitForIdle()
        onNodeWithText("9").performMouseInput { click(center) }
        waitForIdle()
        assertEquals(540, minutes())
        onNodeWithText("Now").performMouseInput { click(center) }
        waitForIdle()
        assertEquals(615, minutes())
        onNodeWithText("Done").performMouseInput { click(center) }
        waitForIdle()
        onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun `the small arrows step the minutes`() = runComposeUiTest {
        val minutes = field(480)
        onNodeWithText("FROM").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithContentDescription("Later").performMouseInput { click(center) }
        waitForIdle()
        assertEquals(485, minutes())
        onNodeWithContentDescription("Earlier").performMouseInput { click(center) }
        waitForIdle()
        assertEquals(480, minutes())
    }

    @Test
    fun `a disabled field ignores keys and has no arrows`() = runComposeUiTest {
        val minutes = field(480, enabled = false)
        onNodeWithText("FROM").performMouseInput { click(center) }
        waitForIdle()
        onNodeWithContentDescription("Later").assertDoesNotExist()
        onNodeWithText("Done").assertDoesNotExist()
        assertEquals(480, minutes())
    }

    @Test
    fun `the next-day tag and the error show`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                TimeField(30, {}, "To", labels, actions, nextDayTag = "next day", error = "Enter a time like 9:30 AM")
            }
        }
        onNodeWithText("next day").assertExists()
        onNodeWithText("Enter a time like 9:30 AM").assertExists()
    }
}
