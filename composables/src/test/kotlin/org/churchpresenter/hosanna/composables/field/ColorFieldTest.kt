@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.churchpresenter.hosanna.composables.model.ColorFieldLabels
import org.churchpresenter.hosanna.composables.testing.HosannaSurface

class ColorFieldTest {

    private val labels =
        ColorFieldLabels("Pick a color", "None", "Opacity", "Theme", "Recent", "Use a color like #1F5BB0")
    private val theme = listOf(Color(0xFF1F5BB0), Color(0xFF0F7F86))

    private fun SemanticsNodeInteraction.mouseClick() = performMouseInput { click(center) }

    private fun ComposeUiTest.twoFields(
        start: Color?,
        allowNone: Boolean = false,
        allowOpacity: Boolean = false,
    ): () -> Color? {
        var color by mutableStateOf(start)
        setContent {
            HosannaSurface {
                Column {
                    ColorField(
                        color, { color = it }, "Text", labels,
                        allowNone = allowNone, allowOpacity = allowOpacity, themeColors = theme,
                    )
                    ColorField(Color.Black, {}, "Other", labels)
                }
            }
        }
        return { color }
    }

    private fun ComposeUiTest.firstInput() = onAllNodes(hasSetTextAction())[0]

    private fun ComposeUiTest.leaveField() = onAllNodes(hasSetTextAction())[1].requestFocus()

    @Test
    fun `the field shows the label, hex and opacity`() = runComposeUiTest {
        twoFields(Color(0xB31B1F24), allowOpacity = true)
        onNodeWithText("TEXT").assertExists()
        onNode(hasText("#1B1F24")).assertExists()
        onNodeWithText("70%").assertExists()
    }

    @Test
    fun `typing a valid color applies it as you type`() = runComposeUiTest {
        val color = twoFields(Color(0xFF1F5BB0))
        firstInput().performTextReplacement("#c43030")
        waitForIdle()
        assertEquals(Color(0xFFC43030), color())
    }

    @Test
    fun `leaving with an invalid value shows the error and keeps the color`() = runComposeUiTest {
        val color = twoFields(Color(0xFF1F5BB0))
        firstInput().requestFocus()
        firstInput().performTextReplacement("blue-ish")
        leaveField()
        waitForIdle()
        onNodeWithText("Use a color like #1F5BB0").assertExists()
        assertEquals(Color(0xFF1F5BB0), color())
    }

    @Test
    fun `clearing an optional color sets None`() = runComposeUiTest {
        val color = twoFields(Color(0xFF1F5BB0), allowNone = true)
        firstInput().requestFocus()
        firstInput().performTextReplacement("")
        leaveField()
        waitForIdle()
        assertNull(color())
        onNodeWithText("Use a color like #1F5BB0").assertDoesNotExist()
    }

    @Test
    fun `the picker applies a theme swatch and Escape puts the old color back`() = runComposeUiTest {
        val color = twoFields(Color(0xFFC43030))
        onAllNodesWithContentDescription("Pick a color")[1].mouseClick()
        waitForIdle()
        onNodeWithText("Theme").assertExists()
        onNodeWithContentDescription("#0F7F86").mouseClick()
        waitForIdle()
        assertEquals(Color(0xFF0F7F86), color())
        onNodeWithText("Theme").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(Color(0xFFC43030), color())
        onNodeWithText("Theme").assertDoesNotExist()
    }

    @Test
    fun `clicking the swatch opens the picker, and typing hex there applies`() = runComposeUiTest {
        val color = twoFields(Color(0xFFC43030))
        onAllNodesWithContentDescription("Pick a color")[0].mouseClick()
        waitForIdle()
        val inputs = onAllNodes(hasSetTextAction())
        inputs[inputs.fetchSemanticsNodes().size - 1].performTextReplacement("#14855A")
        waitForIdle()
        assertEquals(Color(0xFF14855A), color())
    }

    @Test
    fun `a disabled field cannot be opened`() = runComposeUiTest {
        setContent { HosannaSurface { ColorField(Color.Red, {}, "Off", labels, enabled = false) } }
        onAllNodesWithContentDescription("Pick a color")[1].assertIsNotEnabled().mouseClick()
        waitForIdle()
        onNodeWithText("Theme").assertDoesNotExist()
    }
}
