package org.churchpresenter.sharedui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ValueDialogTest {

    private class Outcome {
        var confirmed: Int? = null
        var dismissed = 0
    }

    private val spec = ValueDialogSpec(min = 1, max = 30, step = 1, presets = listOf(3, 5, 10, 15), unit = "s")

    private fun ComposeUiTest.dialog(initial: Int = 5, spec: ValueDialogSpec = this@ValueDialogTest.spec): Outcome {
        val outcome = Outcome()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                ValueDialog(
                    title = "Auto-scroll interval",
                    hint = "How long each slide stays before advancing.",
                    icon = rememberVectorPainter(Icons.Default.Timer),
                    accent = Color.Blue,
                    initial = initial,
                    spec = spec,
                    onConfirm = { outcome.confirmed = it },
                    onDismiss = { outcome.dismissed++ },
                )
            }
        }
        waitForIdle()
        return outcome
    }

    private fun ComposeUiTest.field() = onNode(hasSetTextAction())

    private fun ComposeUiTest.value(): String =
        field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.has(text: String) =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.ok() {
        onNodeWithText("OK").performClick()
        waitForIdle()
    }

    @Test
    fun `it shows the title, the hint, the value, the unit and every preset`() = runComposeUiTest {
        dialog()

        assertTrue(has("Auto-scroll interval"))
        assertTrue(has("How long each slide stays before advancing."))
        assertEquals("5", value())
        listOf("3 s", "5 s", "10 s", "15 s").forEach { assertTrue(has(it), it) }
        assertTrue(has("↵ OK · Esc Cancel"))
    }

    @Test
    fun `OK confirms the value that was typed`() = runComposeUiTest {
        val outcome = dialog()
        field().performTextReplacement("12")
        ok()

        assertEquals(12, outcome.confirmed)
    }

    @Test
    fun `a preset replaces the value`() = runComposeUiTest {
        val outcome = dialog()
        onNodeWithText("10 s").performClick()
        ok()

        assertEquals(10, outcome.confirmed)
    }

    @Test
    fun `plus and minus step the value by the spec's step`() = runComposeUiTest {
        val outcome = dialog(initial = 500, spec = ValueDialogSpec(100, 2000, 50, listOf(250), "ms"))
        onNodeWithContentDescription("Increase").performClick()
        onNodeWithContentDescription("Increase").performClick()
        onNodeWithContentDescription("Decrease").performClick()
        ok()

        assertEquals(550, outcome.confirmed)
    }

    @Test
    fun `stepping stops at the ends of the range`() = runComposeUiTest {
        val outcome = dialog(initial = 30)
        onNodeWithContentDescription("Increase").performClick()
        waitForIdle()
        assertEquals("30", value())

        field().performTextReplacement("1")
        onNodeWithContentDescription("Decrease").performClick()
        ok()
        assertEquals(1, outcome.confirmed)
    }

    @Test
    fun `a value out of range is refused with a message, not clamped`() = runComposeUiTest {
        val outcome = dialog()
        field().performTextReplacement("300")
        waitForIdle()

        assertTrue(has("Enter a number from 1 to 30 s."))
        onNodeWithText("OK").assertIsNotEnabled()
        ok()
        assertNull(outcome.confirmed)
        assertEquals(0, outcome.dismissed)
    }

    @Test
    fun `stepping from an out of range value brings it back into range`() = runComposeUiTest {
        dialog()
        field().performTextReplacement("300")
        onNodeWithContentDescription("Decrease").performClick()
        waitForIdle()

        assertEquals("30", value())
        assertFalse(has("Enter a number from 1 to 30 s."))
    }

    @Test
    fun `only digits are kept, and no more than five`() = runComposeUiTest {
        dialog()
        field().performTextReplacement("1a2b")
        waitForIdle()
        assertEquals("12", value())

        field().performTextReplacement("1234567")
        waitForIdle()
        assertEquals("12345", value())
    }

    @Test
    fun `an empty field cannot be confirmed`() = runComposeUiTest {
        val outcome = dialog()
        field().performTextReplacement("")
        ok()

        assertNull(outcome.confirmed)
        assertTrue(has("Enter a number from 1 to 30 s."))
    }

    @Test
    fun `Cancel dismisses without confirming`() = runComposeUiTest {
        val outcome = dialog()
        field().performTextReplacement("12")
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        assertNull(outcome.confirmed)
        assertEquals(1, outcome.dismissed)
    }

    @Test
    fun `Enter confirms and Escape dismisses`() = runComposeUiTest {
        val outcome = dialog()
        field().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(5, outcome.confirmed)

        field().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, outcome.dismissed)
    }

    @Test
    fun `the arrow keys step the value`() = runComposeUiTest {
        dialog()
        field().performKeyInput { pressKey(Key.DirectionUp) }
        field().performKeyInput { pressKey(Key.DirectionUp) }
        field().performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()

        assertEquals("6", value())
    }

    @Test
    fun `Enter on an out of range value does nothing`() = runComposeUiTest {
        val outcome = dialog()
        field().performTextReplacement("0")
        field().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertNull(outcome.confirmed)
    }

    @Test
    fun `it draws in the dark theme too`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                ValueDialog(
                    title = "Transition duration",
                    hint = "How long the transition between slides takes.",
                    icon = rememberVectorPainter(Icons.Default.Timer),
                    accent = Color.Green,
                    initial = 500,
                    spec = ValueDialogSpec(100, 2000, 50, listOf(250, 500), "ms"),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        waitForIdle()

        assertTrue(has("Transition duration"))
        assertTrue(has("500 ms"))
    }
}
