@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.hosanna.composables.defaults.HoldRepeatDefaults
import org.churchpresenter.hosanna.composables.model.TimerPreset
import org.churchpresenter.hosanna.composables.model.TimerSpinnerLabels
import org.churchpresenter.hosanna.composables.testing.HosannaSurface

class TimerSpinnerTest {

    private val labels = TimerSpinnerLabels("Hours", "Min", "Sec", "Increase", "Decrease")
    private val presets = listOf(TimerPreset("1 min", 60), TimerPreset("5 min", 300))
    private val hours = 0
    private val minutes = 1
    private val seconds = 2

    private fun ComposeUiTest.spinner(start: Int, enabled: Boolean = true): () -> Int {
        var value by mutableIntStateOf(start)
        setContent {
            HosannaSurface { TimerSpinner(value, { value = it }, labels, presets = presets, enabled = enabled) }
        }
        return { value }
    }

    private fun ComposeUiTest.plus(unit: Int) = onAllNodesWithContentDescription("Increase")[unit]
    private fun ComposeUiTest.minus(unit: Int) = onAllNodesWithContentDescription("Decrease")[unit]
    private fun SemanticsNodeInteraction.mouseClick() = performMouseInput { click(center) }

    @Test
    fun `plus and minus step one unit, and minus stops at zero`() = runComposeUiTest {
        val value = spinner(300)
        onNodeWithText("HOURS").assertExists()
        plus(seconds).mouseClick()
        waitForIdle()
        assertEquals(301, value())
        minus(minutes).mouseClick()
        waitForIdle()
        assertEquals(241, value())
        minus(hours).assertIsNotEnabled()
        minus(seconds).assertIsEnabled()
    }

    @Test
    fun `holding plus repeats, speeds up, and stops on release`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val value = spinner(0)
        mainClock.advanceTimeByFrame()
        plus(seconds).performMouseInput { moveTo(center); press() }
        mainClock.advanceTimeBy(HoldRepeatDefaults.DELAY_MILLIS + HoldRepeatDefaults.REPEAT_MILLIS * 2)
        val held = value()
        assertTrue(held >= 2, "held for ${held}s")
        mainClock.advanceTimeBy(HoldRepeatDefaults.FAST_AFTER_MILLIS)
        val fast = value()
        assertTrue(fast >= held + 5 * 3, "fast steps of five: $held → $fast")
        plus(seconds).performMouseInput { release() }
        mainClock.advanceTimeByFrame()
        val released = value()
        mainClock.advanceTimeBy(HoldRepeatDefaults.DELAY_MILLIS * 2)
        assertEquals(released, value())
    }

    @Test
    fun `the wheel steps the unit under it`() = runComposeUiTest {
        val value = spinner(300)
        onAllNodesWithText("05")[0].performMouseInput { moveTo(center); scroll(-1f) }
        waitForIdle()
        assertEquals(360, value())
    }

    @Test
    fun `typing a number sets it, caps it and Escape cancels`() = runComposeUiTest {
        val value = spinner(300)
        onAllNodesWithText("05")[0].mouseClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("75")
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(59 * 60, value())
        onAllNodesWithText("00")[0].mouseClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("12")
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(59 * 60, value())
    }

    @Test
    fun `arrow keys step while editing`() = runComposeUiTest {
        val value = spinner(300)
        onAllNodesWithText("05")[0].mouseClick()
        waitForIdle()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(420, value())
    }

    @Test
    fun `a preset sets the time and the matching one is on`() = runComposeUiTest {
        val value = spinner(300)
        onNodeWithText("5 min").assertIsOn()
        onNodeWithText("1 min").mouseClick()
        waitForIdle()
        assertEquals(60, value())
        onNodeWithText("1 min").assertIsOn()
    }

    @Test
    fun `a running timer locks every control`() = runComposeUiTest {
        val value = spinner(300, enabled = false)
        plus(seconds).assertIsNotEnabled()
        minus(minutes).assertIsNotEnabled()
        onNodeWithText("5 min").assertIsNotEnabled()
        onAllNodesWithText("05")[0].mouseClick()
        waitForIdle()
        onNode(hasSetTextAction()).assertDoesNotExist()
        assertEquals(300, value())
    }
}
