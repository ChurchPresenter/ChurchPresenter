@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The outline split button beside the four faces: a toggle that strokes the glyphs, and a caret onto
 * the colour and width. Both halves publish their tooltip as a content description, which is how
 * each is found.
 */
class TextStyleButtonsOutlineTest {

    private val toggle = "Outline"
    private val caret = "Outline options"

    /** The style row with an outline attached, reporting whatever the control last stored. */
    private fun styleRow(
        initial: TextOutline = TextOutline(),
        block: ComposeUiTest.(get: () -> TextOutline) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                    outline = state,
                    onOutlineChange = { state = it; current = it },
                )
            }
        }
        block { current }
    }

    private val stroked = TextOutline(enabled = true, color = "#123456", width = 7)

    // ── When the control is offered at all ────────────────────────────────────

    @Test
    fun `a style row with no outline offers no outline control`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                )
            }
        }
        onNodeWithContentDescription(toggle).assertDoesNotExist()
        onNodeWithContentDescription(caret).assertDoesNotExist()
        onNodeWithText("B").assertExists("the four faces are still there")
    }

    @Test
    fun `passing an outline adds both halves of the split button`() = styleRow { _ ->
        onNodeWithContentDescription(toggle).assertExists()
        onNodeWithContentDescription(caret).assertExists()
    }

    // ── The toggle half ───────────────────────────────────────────────────────

    @Test
    fun `clicking the toggle strokes the glyphs`() = styleRow { get ->
        onNodeWithContentDescription(toggle).performClick()
        waitForIdle()
        assertTrue(get().enabled, "the toggle behaves like the four faces beside it")
    }

    @Test
    fun `clicking it again takes the stroke off and keeps its look`() = styleRow(stroked) { get ->
        onNodeWithContentDescription(toggle).performClick()
        waitForIdle()
        assertEquals(stroked.copy(enabled = false), get(), "only the flag moves")
    }

    @Test
    fun `the toggle does not open the dialog`() = styleRow(stroked) { _ ->
        onNodeWithContentDescription(toggle).performClick()
        waitForIdle()
        // Only the caret opens anything.
        onNodeWithText("WIDTH").assertDoesNotExist()
    }

    // ── The caret half ────────────────────────────────────────────────────────

    @Test
    fun `the caret opens the dialog on the outline the row is showing`() = styleRow(stroked) { _ ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNodeWithText("OUTLINE").assertExists("the dialog's heading")
        onNodeWithText("COLOR").assertExists()
        onNodeWithText("WIDTH").assertExists()
    }

    @Test
    fun `the caret opens the dialog even with the stroke off`() = styleRow { _ ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNode(isToggleable() and hasText("Show outline")).assertExists("the dialog opens in the state it is in")
    }

    @Test
    fun `switching it on in the dialog is reported by the row`() = styleRow { get ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNode(isToggleable() and hasText("Show outline")).performClick()
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertTrue(get().enabled, "what the dialog changes must reach the caller")
    }

    @Test
    fun `closing the dialog leaves the outline alone`() = styleRow(stroked) { get ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNodeWithContentDescription("Close").performClick()
        waitForIdle()
        assertEquals(stroked, get(), "opening and closing must change nothing")
        // And the dialog must be gone.
        onNodeWithText("WIDTH").assertDoesNotExist()
    }

    @Test
    fun `the outline and the backdrop are separate controls`() = runComposeUiTest {
        var outline = TextOutline()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(outline) }
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                    backdrop = TextBackdrop(),
                    onBackdropChange = {},
                    outline = state,
                    onOutlineChange = { state = it; outline = it },
                )
            }
        }
        onNodeWithContentDescription("Text backing").assertExists()
        onNodeWithContentDescription(toggle).performClick()
        waitForIdle()
        assertTrue(outline.enabled, "the outline's toggle must not be the backdrop's")
        assertEquals(TextOutline.DEFAULT_WIDTH, outline.width, "and it keeps the width it had")
    }
}
