package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Typing a colour in by hand, which is the picker's third input alongside the square and the bar.
 *
 * The field has to keep the rest of the dialog in step — a typed hex moves the hue bar and the
 * saturation square, because otherwise confirming would report whatever the *sliders* last said and
 * silently discard what was typed. And it must not call a half-typed value an error: a field that
 * turns red the moment someone types `#` is telling them they are wrong when they are mid-way.
 */
@OptIn(ExperimentalTestApi::class)
class ColorPickerHexFieldTest {

    private fun dialog(initial: String = "#FF0000", onSelected: (String) -> Unit = {}) =
        @androidx.compose.runtime.Composable {
            MaterialTheme { ColorPickerDialog(initial, onDismiss = {}, onColorSelected = onSelected) }
        }

    @Test
    fun `a typed hex is what gets reported on OK`() = runComposeUiTest {
        var result: String? = null
        setContent(dialog(onSelected = { result = it }))
        onNode(hasSetTextAction()).performTextReplacement("#00FF00")
        waitForIdle()
        onNodeWithText("OK").performClick()
        assertEquals("#00FF00", result, "the typed value has to reach the caller, not the sliders' last value")
    }

    @Test
    fun `a typed hex drives the rest of the dialog`() = runComposeUiTest {
        var result: String? = null
        setContent(dialog(onSelected = { result = it }))
        onNode(hasSetTextAction()).performTextReplacement("#0000FF")
        waitForIdle()
        onNodeWithText("OK").performClick()
        // Blue is hue 240 at full saturation and value — proof the HSV state followed the text.
        assertEquals(cpColorToHex(cpHsvToColor(240f, 1f, 1f)), result)
    }

    @Test
    fun `an unparseable hex disables OK rather than committing something wrong`() = runComposeUiTest {
        setContent(dialog())
        onNode(hasSetTextAction()).performTextReplacement("nonsense")
        waitForIdle()
        onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun `an empty field is not an error, just not yet confirmable`() = runComposeUiTest {
        setContent(dialog())
        onNode(hasSetTextAction()).performTextReplacement("")
        waitForIdle()
        onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun `a lone hash is not an error either`() = runComposeUiTest {
        setContent(dialog())
        onNode(hasSetTextAction()).performTextReplacement("#")
        waitForIdle()
        // Mid-typing: nothing to confirm yet, but the field must not be shouting at the operator.
        onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun `correcting a bad hex re-enables OK`() = runComposeUiTest {
        setContent(dialog())
        onNode(hasSetTextAction()).performTextReplacement("zzzzzz")
        waitForIdle()
        onNodeWithText("OK").assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextReplacement("#123456")
        waitForIdle()
        onNodeWithText("OK").assertIsEnabled()
    }

    @Test
    fun `an eight-digit hex is accepted`() = runComposeUiTest {
        setContent(dialog())
        onNode(hasSetTextAction()).performTextReplacement("#80FF0000")
        waitForIdle()
        onNodeWithText("OK").assertIsEnabled()
    }

    @Test
    fun `an unreadable initial colour opens on white rather than failing`() = runComposeUiTest {
        var result: String? = null
        setContent(dialog(initial = "not-a-colour", onSelected = { result = it }))
        waitForIdle()
        onNodeWithText("OK").performClick()
        assertEquals(cpColorToHex(cpHsvToColor(0f, 0f, 1f)), result, "the documented fallback is white")
    }

    @Test
    fun `a corrupt entry in the recent colours falls back to white rather than throwing`() = runComposeUiTest {
        RecentColors.add("not-a-colour")
        var result: String? = null
        setContent(dialog(onSelected = { result = it }))
        waitForIdle()
        // The row renders the unreadable entry as white instead of failing the whole dialog: the
        // recents file is on disk and an older build could have written anything into it.
        onNodeWithText("OK").performClick()
        assertEquals("#FF0000", result, "the dialog still works around a bad recent entry")
    }

    @Test
    fun `a recent colour that does parse is offered as a swatch`() = runComposeUiTest {
        RecentColors.add("#123456")
        setContent(dialog())
        waitForIdle()
        onNodeWithTag("recentColor_#123456").assertIsDisplayed()
    }
}
