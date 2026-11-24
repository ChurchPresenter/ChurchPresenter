@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.hosanna.composables.defaults.HoldRepeatDefaults
import org.churchpresenter.hosanna.composables.model.NumberFieldActions
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.testing.HosannaSurface

class NumberFieldTest {

    private val actions = NumberFieldActions("Increase", "Decrease")
    private val photos = NumberRange(1.0, 200.0)

    private fun ComposeUiTest.numberField(
        start: Double,
        range: NumberRange = photos,
        enabled: Boolean = true,
    ): () -> Double {
        var value by mutableDoubleStateOf(start)
        setContent {
            HosannaSurface {
                Column {
                    NumberField(
                        value, { value = it }, "Photos", range, actions, Modifier.width(200.dp),
                        helper = "How many to show", rangeError = "Use 1 to 200", enabled = enabled,
                    )
                    UnitField(50.0, {}, "%", NumberRange(0.0, 100.0))
                }
            }
        }
        return { value }
    }

    private fun ComposeUiTest.input() = onAllNodes(hasSetTextAction())[0]
    private fun ComposeUiTest.leave() = onAllNodes(hasSetTextAction())[1].requestFocus()

    @Test
    fun `the arrows step and switch off at the limit`() = runComposeUiTest {
        val value = numberField(2.0)
        onNodeWithContentDescription("Increase").performMouseInput { click(center) }
        waitForIdle()
        assertEquals(3.0, value())
        onNodeWithContentDescription("Decrease").performMouseInput { click(center) }
        onNodeWithContentDescription("Decrease").performMouseInput { click(center) }
        waitForIdle()
        assertEquals(1.0, value())
        onNodeWithContentDescription("Decrease").assertIsNotEnabled()
        onNodeWithContentDescription("Increase").assertIsEnabled()
    }

    @Test
    fun `holding an arrow repeats`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val value = numberField(1.0)
        mainClock.advanceTimeByFrame()
        onNodeWithContentDescription("Increase").performMouseInput { moveTo(center); press() }
        mainClock.advanceTimeBy(HoldRepeatDefaults.DELAY_MILLIS + HoldRepeatDefaults.REPEAT_MILLIS * 3)
        onNodeWithContentDescription("Increase").performMouseInput { release() }
        mainClock.advanceTimeByFrame()
        assertTrue(value() >= 4.0, "held to ${value()}")
    }

    @Test
    fun `typing in range applies at once, out of range shows the error`() = runComposeUiTest {
        val value = numberField(50.0)
        input().performTextReplacement("120")
        waitForIdle()
        assertEquals(120.0, value())
        onNodeWithText("How many to show").assertExists()
        input().performTextReplacement("500")
        waitForIdle()
        assertEquals(120.0, value())
        onNodeWithText("Use 1 to 200").assertExists()
        onNodeWithText("How many to show").assertDoesNotExist()
    }

    @Test
    fun `leaving clamps an out-of-range value`() = runComposeUiTest {
        val value = numberField(50.0)
        input().requestFocus()
        input().performTextReplacement("500")
        leave()
        waitForIdle()
        assertEquals(200.0, value())
        onNodeWithText("Use 1 to 200").assertDoesNotExist()
        onNodeWithText("200").assertExists()
    }

    @Test
    fun `arrow keys step, shift steps ten`() = runComposeUiTest {
        val value = numberField(50.0)
        input().requestFocus()
        input().performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(51.0, value())
        input().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        waitForIdle()
        assertEquals(41.0, value())
    }

    @Test
    fun `the wheel steps only while focused`() = runComposeUiTest {
        val value = numberField(50.0)
        input().performMouseInput { moveTo(center); scroll(-1f) }
        waitForIdle()
        assertEquals(50.0, value())
        input().requestFocus()
        input().performMouseInput { moveTo(center); scroll(-1f) }
        waitForIdle()
        assertEquals(51.0, value())
    }

    @Test
    fun `a disabled field has no arrows and cannot be typed in`() = runComposeUiTest {
        numberField(5.0, enabled = false)
        onNodeWithContentDescription("Increase").assertDoesNotExist()
        onNodeWithText("5").assertIsNotEnabled()
    }
}
