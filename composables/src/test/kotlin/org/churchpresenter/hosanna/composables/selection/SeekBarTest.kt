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
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import kotlin.test.Test
import kotlin.test.assertEquals

class SeekBarTest {

    private val bar = Modifier.width(214.dp)

    @Test
    fun `the times show either side and the bar names its position`() = runComposeUiTest {
        setContent { HosannaSurface { SeekBar(83.0, 214.0, {}, "Seek", bar) } }
        onNodeWithText("1:23").assertExists()
        onNodeWithText("3:34").assertExists()
        onNodeWithContentDescription("Seek")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1:23"))
    }

    @Test
    fun `hiding the times leaves only the bar`() = runComposeUiTest {
        setContent { HosannaSurface { SeekBar(83.0, 214.0, {}, "Seek", bar, showTimes = false) } }
        onNodeWithText("1:23").assertDoesNotExist()
        onNodeWithText("3:34").assertDoesNotExist()
    }

    @Test
    fun `clicking and dragging seek, finishing once per gesture`() = runComposeUiTest {
        var position by mutableStateOf(0.0)
        var finished = 0
        setContent {
            HosannaSurface {
                SeekBar(
                    position, 200.0, { position = it }, "Seek", bar,
                    showTimes = false, onSeekFinished = { finished++ },
                )
            }
        }
        val seek = onNodeWithContentDescription("Seek")
        seek.performMouseInput { click(center) }
        waitForIdle()
        assertEquals(100.0, position)
        seek.performMouseInput {
            moveTo(center)
            press()
            moveTo(Offset(width + 30f, centerY))
            release()
        }
        waitForIdle()
        assertEquals(200.0, position)
        assertEquals(2, finished)
    }

    @Test
    fun `hovering shows the time under the pointer`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { HosannaSurface { SeekBar(0.0, 200.0, {}, "Seek", bar, showTimes = false) } }
        onNodeWithContentDescription("Seek").performMouseInput { moveTo(center) }
        settle()
        onNodeWithText("1:40").assertExists()
        onNodeWithContentDescription("Seek").performMouseInput { moveTo(Offset(-50f, -50f)) }
        settle()
        onNodeWithText("1:40").assertDoesNotExist()
    }

    @Test
    fun `keys seek by steps and jump to the ends`() = runComposeUiTest {
        var position by mutableStateOf(60.0)
        setContent { HosannaSurface { SeekBar(position, 200.0, { position = it }, "Seek", bar) } }
        val seek = onNodeWithContentDescription("Seek")
        seek.requestFocus()
        seek.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(65.0, position)
        seek.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
        assertEquals(35.0, position)
        seek.performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals(200.0, position)
        seek.performKeyInput { pressKey(Key.A) }
        assertEquals(200.0, position)
    }

    @Test
    fun `screen readers can seek, clamped to the duration`() = runComposeUiTest {
        var position by mutableStateOf(0.0)
        setContent { HosannaSurface { SeekBar(position, 200.0, { position = it }, "Seek", bar) } }
        onNodeWithContentDescription("Seek").performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
        assertEquals(200.0, position)
    }

    @Test
    fun `a disabled seek bar ignores everything`() = runComposeUiTest {
        var seeks = 0
        setContent { HosannaSurface { SeekBar(30.0, 200.0, { seeks++ }, "Seek", bar, enabled = false) } }
        val seek = onNodeWithContentDescription("Seek").assertIsNotEnabled()
        seek.performMouseInput { click(center) }
        seek.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(0, seeks)
        seek.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
    }
}
