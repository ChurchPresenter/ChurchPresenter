@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class PositionPickerTest {

    private val label = "Position on screen"
    private fun name(position: ScreenPosition) = position.name

    private fun ComposeUiTest.picker(
        start: ScreenPosition,
        compact: Boolean = false,
        enabled: Boolean = true,
    ): () -> ScreenPosition {
        var position by mutableStateOf(start)
        setContent {
            HosannaSurface {
                PositionPicker(position, { position = it }, label, ::name, compact = compact, enabled = enabled)
            }
        }
        return { position }
    }

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithContentDescription(label).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `cells map to rows and columns and clamp at the edges`() {
        assertEquals(2, ScreenPosition.BottomCenter.row())
        assertEquals(1, ScreenPosition.BottomCenter.column())
        assertEquals(ScreenPosition.Center, positionAt(1, 1))
        assertEquals(ScreenPosition.TopLeft, positionAt(-1, -5))
        assertEquals(ScreenPosition.BottomRight, positionAt(9, 9))
        ScreenPosition.entries.forEach { assertEquals(it, positionAt(it.row(), it.column())) }
    }

    @Test
    fun `the label, the chosen name and the selected cell show`() = runComposeUiTest {
        picker(ScreenPosition.BottomCenter)
        onNodeWithText(label).assertExists()
        onNodeWithText("BottomCenter").assertExists()
        onNodeWithContentDescription("BottomCenter")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        onNodeWithContentDescription("Center").assertIsNotSelected()
    }

    @Test
    fun `clicking a cell picks it`() = runComposeUiTest {
        val position = picker(ScreenPosition.Center)
        onNodeWithContentDescription("TopRight").performMouseInput { click(center) }
        waitForIdle()
        assertEquals(ScreenPosition.TopRight, position())
        onNodeWithContentDescription("TopRight").assertIsSelected()
    }

    @Test
    fun `arrow keys move the selection and stop at the edges, Home and End jump`() = runComposeUiTest {
        val position = picker(ScreenPosition.Center)
        onNodeWithContentDescription(label).requestFocus()
        press(Key.DirectionUp)
        assertEquals(ScreenPosition.TopCenter, position())
        press(Key.DirectionUp)
        assertEquals(ScreenPosition.TopCenter, position())
        press(Key.DirectionLeft)
        assertEquals(ScreenPosition.TopLeft, position())
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        assertEquals(ScreenPosition.Center, position())
        press(Key.MoveEnd)
        assertEquals(ScreenPosition.BottomRight, position())
        press(Key.MoveHome)
        assertEquals(ScreenPosition.TopLeft, position())
    }

    @Test
    fun `the compact screen is half the size`() = runComposeUiTest {
        picker(ScreenPosition.Center, compact = true)
        onNodeWithContentDescription(label)
            .assertWidthIsEqualTo(PositionPickerDefaults.Compact.width)
            .assertHeightIsEqualTo(PositionPickerDefaults.Compact.height)
    }

    @Test
    fun `a disabled picker ignores clicks and keys`() = runComposeUiTest {
        val position = picker(ScreenPosition.Center, enabled = false)
        onNodeWithContentDescription("TopLeft").assertIsNotEnabled().performMouseInput { click(center) }
        waitForIdle()
        assertEquals(ScreenPosition.Center, position())
    }
}
