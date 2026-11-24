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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class RangeSliderTest {

    private val width = 216.dp

    @Test
    fun `dragging a thumb moves it and it cannot pass the other`() = runComposeUiTest {
        var range by mutableStateOf(20.0 to 70.0)
        var finished = 0
        setContent {
            HosannaSurface {
                RangeSlider(
                    range.first, range.second, { a, b -> range = a to b }, "Trim start", "Trim end",
                    Modifier.width(width), onRangeChangeFinished = { finished++ },
                )
            }
        }
        val step = with(density) { (width - 16.dp).toPx() } / 100f
        onNodeWithContentDescription("Trim start").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(step * 10, 0f))
            release()
        }
        waitForIdle()
        assertEquals(30.0 to 70.0, range)
        onNodeWithContentDescription("Trim end").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(-step * 60, 0f))
            release()
        }
        waitForIdle()
        assertEquals(30.0 to 30.0, range)
        assertEquals(2, finished)
        onNodeWithContentDescription("Trim end")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30"))
    }

    @Test
    fun `each thumb steps with its own keys and stops at the other`() = runComposeUiTest {
        var range by mutableStateOf(20.0 to 22.0)
        setContent {
            HosannaSurface {
                RangeSlider(
                    range.first, range.second, { a, b -> range = a to b }, "Start", "End", Modifier.width(width),
                    valueText = "${range.first.toInt()}–${range.second.toInt()}",
                )
            }
        }
        onNodeWithText("20–22").assertExists()
        val start = onNodeWithContentDescription("Start")
        start.requestFocus()
        start.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(21.0 to 22.0, range)
        start.performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals(22.0 to 22.0, range)
        start.performKeyInput { pressKey(Key.A) }
        assertEquals(22.0 to 22.0, range)
        val end = onNodeWithContentDescription("End")
        end.requestFocus()
        end.performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals(22.0 to 100.0, range)
        end.performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        assertEquals(22.0 to 22.0, range)
    }

    @Test
    fun `a disabled range slider ignores clicks and keys`() = runComposeUiTest {
        var changes = 0
        setContent {
            HosannaSurface {
                RangeSlider(20.0, 70.0, { _, _ -> changes++ }, "Start", "End", Modifier.width(width), enabled = false)
            }
        }
        val start = onNodeWithContentDescription("Start").assertIsNotEnabled()
        onRoot().performMouseInput { click(center) }
        start.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(0, changes)
        start.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
    }
}
