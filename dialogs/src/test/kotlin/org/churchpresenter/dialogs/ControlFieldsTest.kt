@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.controlin.ANY
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The show-control editors' own fields -- what a typed number and a picked option hand back -- and
 * the Go Live button.
 */
class ControlFieldsTest {

    private fun ComposeUiTest.intField(anyWhenBlank: Boolean, typed: String): List<Int> {
        val changes = mutableListOf<Int>()
        setContent {
            ChurchPresenterTheme(ThemeMode.DARK) {
                IntField(label = "Note", value = 5, anyWhenBlank = anyWhenBlank, onChange = { changes += it })
            }
        }
        onNode(hasSetTextAction()).performTextReplacement(typed)
        waitForIdle()
        return changes
    }

    @Test
    fun `a typed number keeps only its digits, up to six`() = runComposeUiTest {
        assertEquals(listOf(123_456), intField(anyWhenBlank = false, typed = "12a3456789"))
    }

    @Test
    fun `a field cleared is any where any is allowed`() = runComposeUiTest {
        assertEquals(listOf(ANY), intField(anyWhenBlank = true, typed = "x"))
    }

    @Test
    fun `a field cleared is zero where any is not allowed`() = runComposeUiTest {
        assertEquals(listOf(0), intField(anyWhenBlank = false, typed = ""))
    }

    @Test
    fun `any is shown as a blank field, a number as itself`() = runComposeUiTest {
        var value by mutableStateOf(ANY)
        setContent {
            ChurchPresenterTheme(ThemeMode.DARK) {
                IntField(label = "Note", value = value, anyWhenBlank = true, onChange = {})
            }
        }
        fun shown() = onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("", shown())
        value = 64
        waitForIdle()
        assertEquals("64", shown())
    }

    @Test
    fun `a picked option hands back its value, not its label`() = runComposeUiTest {
        val picked = mutableListOf<String>()
        setContent {
            ChurchPresenterTheme(ThemeMode.DARK) {
                ControlDropdown(
                    label = "Kind",
                    value = "note",
                    options = listOf("note" to "Note on", "cc" to "Control change"),
                    onPick = { picked += it },
                )
            }
        }
        onNodeWithTag(controlFieldTag("Kind")).performClick()
        waitForIdle()
        onAllNodes(hasText("Control change") and hasClickAction()).onLast().performClick()
        waitForIdle()

        assertEquals(listOf("cc"), picked)
    }

    @Test
    fun `an off live button is a disabled button in either theme, and an on one presses`() = runComposeUiTest {
        var presses = 0
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var enabled by mutableStateOf(false)
        setContent {
            ChurchPresenterTheme(mode) {
                LiveActionButton(label = "Go Live", enabled = enabled, onClick = { presses++ })
            }
        }
        onNodeWithText("Go Live").onParent().assertIsNotEnabled()
        mode = ThemeMode.DARK
        waitForIdle()
        onNodeWithText("Go Live").onParent().assertIsNotEnabled()
        enabled = true
        waitForIdle()
        onNodeWithText("Go Live").performClick()
        waitForIdle()
        assertEquals(1, presses)
    }
}
