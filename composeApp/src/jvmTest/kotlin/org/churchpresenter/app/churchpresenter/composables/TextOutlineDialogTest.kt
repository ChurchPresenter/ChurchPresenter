@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import org.churchpresenter.core.models.text.TextOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dialog behind the outline button's caret: the switch, the colour and the width.
 *
 * Field captions are matched upper-case — `ColorPickerField` and `NumberSettingsTextField` both
 * draw `label.uppercase()` inside the box, as does the dialog's own heading.
 */
class TextOutlineDialogTest {

    private val showLabel = "Show outline"

    /** Opens the dialog on [initial] and hands the block back whatever it last reported. */
    private fun dialog(
        initial: TextOutline = TextOutline(),
        block: ComposeUiTest.(get: () -> TextOutline, dismissed: () -> Int) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        var dismissals = 0
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                TextOutlineDialog(
                    outline = state,
                    onChange = { state = it; current = it },
                    onDismiss = { dismissals++ },
                )
            }
        }
        // What the dialog has *committed*: edits sit in its draft until Apply or OK, so the
        // reader presses Apply first -- a no-op when nothing changed.
        block({
            onNodeWithText("Apply").performClick()
            waitForIdle()
            current
        }, { dismissals })
    }

    /** Retypes the width field, which is the dialog's only number. */
    private fun ComposeUiTest.retypeWidth(showing: Int, to: Int) {
        onNode(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(showing.toString()))
            .performTextReplacement(to.toString())
        waitForIdle()
    }

    private val stroked = TextOutline(enabled = true, color = "#123456", width = 7)

    @Test
    fun `the dialog offers the switch, the colour and the width`() = dialog(stroked) { _, _ ->
        onNodeWithText("OUTLINE").assertExists("the heading")
        onNode(isToggleable() and hasText(showLabel)).assertExists("the switch")
        onNodeWithText("COLOR").assertExists()
        onNodeWithText("WIDTH").assertExists()
    }

    @Test
    fun `ticking the switch turns the stroke on and keeps its look`() = dialog(TextOutline(width = 7)) { get, _ ->
        onNode(isToggleable() and hasText(showLabel)).performClick()
        waitForIdle()
        assertTrue(get().enabled, "the switch must turn the stroke on")
        assertEquals(7, get().width, "and leave the width it found alone")
    }

    @Test
    fun `unticking it turns the stroke off and keeps the colour and width`() = dialog(stroked) { get, _ ->
        onNode(isToggleable() and hasText(showLabel)).performClick()
        waitForIdle()
        val off = get()
        assertFalse(off.enabled)
        assertEquals("#123456", off.color, "a stroke turned off keeps the colour it had")
        assertEquals(7, off.width)
    }

    @Test
    fun `retyping the width reports it`() = dialog(stroked) { get, _ ->
        retypeWidth(7, 22)
        assertEquals(22, get().width)
    }

    @Test
    fun `a width past the range is not reported`() = dialog(stroked) { get, _ ->
        retypeWidth(7, 90)
        assertEquals(7, get().width, "40 is as thick as the field goes")
    }

    @Test
    fun `a width of zero is not reported`() = dialog(stroked) { get, _ ->
        retypeWidth(7, 0)
        assertEquals(7, get().width, "an invisible stroke is the switch's job, not the width's")
    }

    @Test
    fun `the close button dismisses`() = dialog(stroked) { _, dismissed ->
        onNodeWithContentDescription("Close").performClick()
        waitForIdle()
        assertEquals(1, dismissed(), "the close button must dismiss exactly once")
    }

    @Test
    fun `the switch is offered while the stroke is off`() = dialog { _, _ ->
        onNode(isToggleable() and hasText(showLabel)).assertExists("an operator must be able to switch it on here")
        onNodeWithText("WIDTH").assertExists("and see what it will draw with")
    }
}
