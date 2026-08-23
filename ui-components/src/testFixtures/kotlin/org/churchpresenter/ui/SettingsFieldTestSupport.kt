@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.input.ImeAction

/**
 * Driving the settings fields this module owns, from any suite that puts them on screen.
 *
 * These reach the widgets through their semantics rather than through any one tab's layout, so a
 * settings tab in `:composeApp` and one in a feature module of its own both drive them the same
 * way. They were written for the song settings tab and used to live beside it; the tab-shaped
 * helpers still do.
 */

/** Every `NumberSettingsTextField` on screen — the stepper fields leave ImeAction at its default. */
fun ComposeUiTest.numberFields(): SemanticsNodeInteractionCollection =
    onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default))

/** Every `FontSettingsDropdown` on screen — the only controls that call themselves a dropdown list. */
fun ComposeUiTest.fontFields(): SemanticsNodeInteractionCollection =
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))

/** The search box of the open font panel, which is the only labelled field inside it. */
fun ComposeUiTest.fontSearchBox(): SemanticsNodeInteraction =
    onNodeWithContentDescription(FONT_SEARCH_LABEL)

/** One B/I/U/S button — [label] is `"B"`, `"I"`, `"U"` or `"S"`. */
fun ComposeUiTest.styleButton(group: Int, label: String): SemanticsNodeInteraction =
    onAllNodes(hasClickAction() and hasText(label))[group]

/** One segmented button, e.g. `segmentedButton("1 Line", row = 0)`. */
fun ComposeUiTest.segmentedButton(text: String, row: Int): SemanticsNodeInteraction =
    onAllNodesWithText(text)[row]

/**
 * Retypes the number field currently displaying [showing], and asserts the field then displays what
 * was typed. That holds even for a value the field rejects: `NumberSettingsTextField` always shows
 * what you typed and only withholds the `onValueChange` callback when the value is out of range, so
 * the caller can assert the stored setting separately either way.
 */
fun ComposeUiTest.retypeNumberField(showing: Int, to: Int) {
    onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(showing.toString()))
        .onFirstNode("no number field is showing $showing")
        .performTextReplacement(to.toString())
    waitForIdle()
    assertNumberFieldShows(to, "the field just retyped")
}

/** The pixels a node currently paints, for controls that publish no state to assert on. */
fun SemanticsNodeInteraction.renderedPixels(): IntArray = captureToImage().toPixelMap().buffer

/** Asserts some font dropdown on screen is displaying [family]. */
fun ComposeUiTest.assertFontFieldShows(family: String, what: String) {
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList) and hasText(family))
        .onFirstNode("$what must display $family")
        .assertExists("$what must display $family")
}

/** Asserts some number field on screen is displaying [value]. */
fun ComposeUiTest.assertNumberFieldShows(value: Int, what: String) {
    onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(value.toString()))
        .onFirstNode("$what must display $value")
        .assertExists("$what must display $value")
}

/**
 * Opens the font dropdown currently displaying [showing] and picks [to] from its panel.
 *
 * The panel lists every installed family, so the search box is what brings [to] into view — and [to]
 * must be a name no other family contains, or the row clicked here would be ambiguous. See
 * [uniquelyNamedFont].
 */
fun ComposeUiTest.pickFont(showing: String, to: String) {
    openFontPanel(showing)
    fontSearchBox().performTextInput(to)
    waitForIdle()
    // Not merely "clickable text carrying the name": the search box now holds that same name as its
    // own editable text, and a text field answers to a click too.
    onAllNodes(hasText(to) and hasClickAction() and !isEditable())
        .onFirstNode("the font panel should be offering $to")
        .performClick()
    waitForIdle()
    assertFontFieldShows(to, "the font dropdown just committed")
}

/**
 * Types [filter] into the panel of the font dropdown displaying [showing] without picking anything.
 *
 * The panel leaves the field alone until a row is chosen, so nothing reaches the settings — and the
 * panel is left open, which is what a caller asserting on the filtered list wants.
 */
fun ComposeUiTest.pickFontFilterOnly(showing: String, filter: String) {
    openFontPanel(showing)
    fontSearchBox().performTextInput(filter)
    waitForIdle()
}

/** Clicks open the font dropdown currently displaying [showing]. */
fun ComposeUiTest.openFontPanel(showing: String) {
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList) and hasText(showing))
        .onFirstNode("no font dropdown is showing $showing")
        .performScrollTo()
        .performClick()
    waitForIdle()
}

/**
 * An installed family whose name no other installed family contains, so that typing it into
 * `FontSettingsDropdown` filters the menu down to exactly one candidate — which is the only state
 * from which the dropdown commits on the IME action.
 */
fun uniquelyNamedFont(): String {
    // Offerable, not merely installed: the picker hides the system's own internal faces, and the
    // first uniquely-named family on a Mac is ".AppleSystemUIFont", which is exactly one of those.
    val fonts = Utils.getAvailableSystemFonts().filterNot { FontCatalog.isHidden(it) }
    return fonts.first { candidate -> fonts.count { it.contains(candidate, ignoreCase = true) } == 1 }
}

/** A font name no installed family matches, used to park a dropdown on a value only it holds. */
const val SENTINEL_FONT = "ZzUnusedTestFont"

/** The font panel's search box announces itself with its own placeholder. */
const val FONT_SEARCH_LABEL = "Search fonts…"

/** The first node of a collection, with a message naming what was being looked for when there is none. */
internal fun SemanticsNodeInteractionCollection.onFirstNode(message: String): SemanticsNodeInteraction {
    val count = fetchSemanticsNodes(atLeastOneRootRequired = false).size
    check(count > 0) { message }
    return get(0)
}
