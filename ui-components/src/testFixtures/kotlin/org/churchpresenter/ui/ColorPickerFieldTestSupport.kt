@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.assertEquals

/**
 * Helpers for driving `ColorPickerField`, shared by the settings-tab test classes.
 *
 * The field is the same composable everywhere it appears: a clickable row showing its value as a
 * `#RRGGBB` string, which opens a dialog carrying a hex text box and OK/Cancel. Both the Song and
 * Background settings tabs render several of them, so these live here rather than in either tab's
 * own support file.
 *
 * Fields are found by the colour they display, so a test that drives one gives it a value in the
 * fixture that no other field on the tab holds.
 */

/** Every `ColorPickerField` on screen — each displays its stored value as a `#RRGGBB` string. */
fun ComposeUiTest.colorFields(): SemanticsNodeInteractionCollection =
    onAllNodes(hasClickAction() and hasText("#", substring = true))

/** Asserts some colour field on screen is displaying [hex], whatever case it was stored in. */
fun ComposeUiTest.assertColorFieldShows(hex: String, what: String) {
    onAllNodes(hasClickAction() and hasText(hex, ignoreCase = true))
        .firstOrFail("$what must display $hex")
        .assertExists("$what must display $hex")
}

/** Opens the colour field currently displaying [showingHex] and returns with its dialog up. */
fun ComposeUiTest.openColorField(showingHex: String) {
    onAllNodes(hasClickAction() and hasText(showingHex))
        .firstOrFail("no colour field is showing $showingHex")
        .performScrollTo()
        .performClick()
    waitForIdle()
}

/** In an open colour dialog: types [hex] and confirms. The hex box is the only editable "#" field. */
fun ComposeUiTest.confirmColorDialogWith(hex: String) {
    onAllNodes(hasSetTextAction() and hasText("#", substring = true))
        .firstOrFail("the colour dialog must offer a hex field")
        .performTextReplacement(hex)
    waitForIdle()
    onNodeWithText("OK").performClick()
    waitForIdle()
}

/** Opens the colour field showing [fromHex], types [toHex] and confirms — the whole round trip. */
fun ComposeUiTest.recolor(fromHex: String, toHex: String) {
    fun showingOldColour() = onAllNodes(hasClickAction() and hasText(fromHex, ignoreCase = true))
        .fetchSemanticsNodes(atLeastOneRootRequired = false).size
    val before = showingOldColour()
    openColorField(fromHex)
    confirmColorDialogWith(toHex)
    assertColorFieldShows(toHex, "the colour field just edited")
    // Counted rather than asserted absent: several controls share a default colour, and only the
    // one that was edited should have stopped showing it.
    assertEquals(before - 1, showingOldColour(), "one fewer field must show $fromHex after the edit")
}

private fun SemanticsNodeInteractionCollection.firstOrFail(message: String): SemanticsNodeInteraction {
    check(fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) { message }
    return get(0)
}
