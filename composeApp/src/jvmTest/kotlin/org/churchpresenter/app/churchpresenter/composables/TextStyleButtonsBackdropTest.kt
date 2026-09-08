@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.text.TextBackdrop
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The text-backing control at the end of the style row: a chip that flips the last look on and off,
 * and a caret onto the dialog behind it.
 *
 * Both halves publish their tooltip as a content description — the chip draws a letter and the
 * caret an arrow, so neither has a name of its own — which is how each is addressed here.
 *
 * The dialog the caret opens shares [SavedTextBackdrops], a process-wide singleton over a file
 * under the fake home, so it is reset around every test exactly as [TextBackdropDialogTest] does.
 */
class TextStyleButtonsBackdropTest {

    private val savedFile = File(System.getProperty("user.home"), ".churchpresenter/saved_backdrops.json")

    private val chip = "Text backing"
    private val caret = "Text backing options"

    @BeforeTest
    fun freshPresets() {
        savedFile.delete()
        SavedTextBackdrops.looks.clear()
    }

    @AfterTest
    fun cleanupPresets() {
        savedFile.delete()
        SavedTextBackdrops.looks.clear()
    }

    /** The style row with a backdrop attached, reporting whatever the control last stored. */
    private fun styleRow(
        initial: TextBackdrop = TextBackdrop(),
        block: ComposeUiTest.(get: () -> TextBackdrop) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                    backdrop = state,
                    onBackdropChange = { state = it; current = it },
                )
            }
        }
        block { current }
    }

    private val plate = TextBackdrop(
        lineBackground = true,
        lineBackgroundColor = "#7A1246",
        lineBackgroundOpacity = 88,
        border = true,
        borderColor = "#FFFFFF",
        borderWidth = 2,
        borderRadius = 14,
    )

    // ── When the control is offered at all ────────────────────────────────────

    @Test
    fun `a style row with no backdrop offers no backing control`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextStyleButtons(
                    bold = false, italic = false, underline = false, shadow = false,
                    onBoldChange = {}, onItalicChange = {}, onUnderlineChange = {}, onShadowChange = {},
                )
            }
        }
        onNodeWithContentDescription(chip).assertDoesNotExist()
        onNodeWithContentDescription(caret).assertDoesNotExist()
        onNodeWithText("B").assertExists("the four faces are still there")
    }

    @Test
    fun `passing a backdrop adds both halves of the split button`() = styleRow { _ ->
        onNodeWithContentDescription(chip).assertExists()
        onNodeWithContentDescription(caret).assertExists()
    }

    // ── The chip half ─────────────────────────────────────────────────────────

    @Test
    fun `clicking the chip on an unset backdrop turns a plain fill on`() = styleRow { get ->
        onNodeWithContentDescription(chip).performClick()
        waitForIdle()
        assertTrue(get().lineBackground, "the fallback look is a fill")
        assertFalse(get().border)
    }

    @Test
    fun `clicking the chip on a live backdrop turns it off`() = styleRow(plate) { get ->
        onNodeWithContentDescription(chip).performClick()
        waitForIdle()
        assertTrue(get().isEmpty, "the chip behaves like the four faces beside it")
    }

    @Test
    fun `turning a look off keeps everything but the two flags`() = styleRow(plate) { get ->
        onNodeWithContentDescription(chip).performClick()
        waitForIdle()
        assertEquals(plate.copy(lineBackground = false, border = false), get())
    }

    @Test
    fun `turning it back on returns the look it had`() = styleRow(plate) { get ->
        onNodeWithContentDescription(chip).performClick()
        waitForIdle()
        onNodeWithContentDescription(chip).performClick()
        waitForIdle()
        assertEquals(plate, get(), "the last style is remembered for as long as the panel is open")
    }

    @Test
    fun `a border-only look comes back as a border, not as a fill`() =
        styleRow(TextBackdrop(border = true, borderWidth = 6)) { get ->
            onNodeWithContentDescription(chip).performClick()
            waitForIdle()
            onNodeWithContentDescription(chip).performClick()
            waitForIdle()
            assertTrue(get().border, "the remembered mode is the one that was on")
            assertFalse(get().lineBackground)
        }

    @Test
    fun `the chip does not open the dialog`() = styleRow(plate) { _ ->
        onNodeWithContentDescription(chip).performClick()
        waitForIdle()
        // Only the caret opens anything.
        onNodeWithText("PRESETS").assertDoesNotExist()
    }

    // ── The caret half ────────────────────────────────────────────────────────

    @Test
    fun `the caret opens the dialog`() = styleRow(plate) { _ ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNodeWithText("PRESETS").assertExists()
        onNodeWithText("Both").assertExists("the dialog's own Style row")
    }

    @Test
    fun `the dialog opens on the look the row is showing`() = styleRow(plate) { _ ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNodeWithText("CORNER RADIUS").assertExists("Both draws the border's fields as well as the fill's")
    }

    @Test
    fun `the caret opens the dialog even on an unset backdrop`() = styleRow { _ ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNodeWithText("Off").assertExists("the dialog opens in the state it is in")
    }

    @Test
    fun `a look chosen in the dialog is reported by the row`() = styleRow { get ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNodeWithText("Border").performClick()
        waitForIdle()
        assertTrue(get().border, "what the dialog changes must reach the caller")
        assertFalse(get().lineBackground)
    }

    @Test
    fun `closing the dialog leaves the look alone`() = styleRow(plate) { get ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        onNodeWithContentDescription("Close").performClick()
        waitForIdle()
        assertEquals(plate, get(), "opening and closing must change nothing")
        // And the dialog must be gone.
        onNodeWithText("PRESETS").assertDoesNotExist()
    }

    @Test
    fun `a preset picked in the dialog reaches the row`() = styleRow(TextBackdrop(lineBackground = true)) { get ->
        onNodeWithContentDescription(caret).performClick()
        waitForIdle()
        // The built-ins in order: black bar, soft shade, thin outline, rounded plate.
        onAllNodesWithText("Aa")[0].performClick()
        waitForIdle()
        assertEquals(24, get().lineBackgroundHeight, "the black bar's own height")
        assertEquals(100, get().lineBackgroundOpacity)
    }
}
