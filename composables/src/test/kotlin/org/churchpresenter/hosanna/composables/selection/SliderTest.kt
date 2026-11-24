@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.selection

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class SliderTest {

    private val width = Modifier.width(216.dp)

    @Test
    fun `clicking and dragging set the value and finish once`() = runComposeUiTest {
        var value by mutableStateOf(0.0)
        var finished = 0
        setContent {
            HosannaSurface { Slider(value, { value = it }, "Opacity", width, onValueChangeFinished = { finished++ }) }
        }
        val slider = onNodeWithContentDescription("Opacity")
        slider.performMouseInput { click(center) }
        waitForIdle()
        assertEquals(50.0, value)
        assertEquals(1, finished)
        slider.performMouseInput {
            moveTo(Offset(width / 2f, centerY))
            press()
            moveTo(Offset(width.toFloat(), centerY))
            moveTo(Offset(width + 40f, centerY))
            release()
        }
        waitForIdle()
        assertEquals(100.0, value)
        assertEquals(2, finished)
    }

    @Test
    fun `arrows step, Shift steps by ten, Home and End jump`() = runComposeUiTest {
        var value by mutableStateOf(40.0)
        setContent { HosannaSurface { Slider(value, { value = it }, "Volume", width) } }
        val slider = onNodeWithContentDescription("Volume")
        slider.requestFocus()
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(41.0, value)
        slider.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
        assertEquals(31.0, value)
        slider.performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals(100.0, value)
        slider.performKeyInput { pressKey(Key.MoveHome) }
        assertEquals(0.0, value)
        slider.performKeyInput { pressKey(Key.A) }
        assertEquals(0.0, value)
    }

    @Test
    fun `a stepped slider snaps to its steps`() = runComposeUiTest {
        var value by mutableStateOf(0.0)
        setContent { HosannaSurface { Slider(value, { value = it }, "Stars", width, range = NumberRange(0.0, 5.0)) } }
        onNodeWithContentDescription("Stars").performMouseInput { click(Offset(width * 0.56f, centerY)) }
        waitForIdle()
        assertEquals(3.0, value)
    }

    @Test
    fun `a centered slider snaps to zero and resets on double click or Delete`() = runComposeUiTest {
        var value by mutableStateOf(4.0)
        setContent {
            HosannaSurface {
                Slider(value, { value = it }, "Transpose", width, range = NumberRange(-6.0, 6.0), centered = true)
            }
        }
        val slider = onNodeWithContentDescription("Transpose")
        onNodeWithText("+4").assertDoesNotExist()
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "+4"))
        slider.performMouseInput { doubleClick(Offset(width * 0.9f, centerY)) }
        waitForIdle()
        assertEquals(0.0, value)
        value = 3.0
        slider.requestFocus()
        slider.performKeyInput { pressKey(Key.Delete) }
        assertEquals(0.0, value)
        slider.performMouseInput { click(Offset(width * 0.51f, centerY)) }
        waitForIdle()
        assertEquals(0.0, value)
    }

    @Test
    fun `the value text shows and screen readers can set the value`() = runComposeUiTest {
        var value by mutableStateOf(60.0)
        setContent {
            HosannaSurface { Slider(value, { value = it }, "Opacity", width, valueText = "${value.toInt()}%") }
        }
        onNodeWithText("60%").assertExists()
        onNodeWithContentDescription("Opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(25.4f) }
        waitForIdle()
        assertEquals(25.0, value)
        onNodeWithText("25%").assertExists()
    }

    @Test
    fun `a disabled slider ignores clicks, keys and set progress`() = runComposeUiTest {
        var changes = 0
        setContent { HosannaSurface { Slider(30.0, { changes++ }, "Locked", width, enabled = false) } }
        val slider = onNodeWithContentDescription("Locked").assertIsNotEnabled()
        slider.performMouseInput { click(center) }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(0, changes)
        slider.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
    }
}
