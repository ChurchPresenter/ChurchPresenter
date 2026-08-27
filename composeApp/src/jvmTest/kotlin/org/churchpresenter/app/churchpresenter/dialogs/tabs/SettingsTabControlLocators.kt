@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.input.ImeAction
import org.churchpresenter.app.churchpresenter.utils.FontCatalog
import org.churchpresenter.app.churchpresenter.utils.Utils
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locators for the styling controls the settings tabs share.
 *
 * These lived in `SongSettingsTabTestSupport` until that tab was rebuilt around a single set of
 * controls. They describe the shared composables — `TextStyleButtons`, `HorizontalAlignmentButtons`,
 * `PositionButtons`, `NumberSettingsTextField`, `FontSettingsDropdown` — rather than any one tab,
 * and the Stage Monitor, Source Properties and QA suites all use them.
 *
 * The ordinal maps that went with them did not survive: `StyleGroup`, `HAlignGroup`,
 * `PositionGroup`, `ShowDropdown` and `ModeRow` named a control's position among the eight
 * styled-text blocks the old Song tab repeated, and there are no repeats left to count.
 */

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/**
 * The horizontal-alignment icon buttons: the only `Role.Button` nodes on the tab carrying neither a
 * content description (the steppers and the position/vertical-alignment icons have one) nor text
 * (the two auto-fit buttons do).
 */
private val horizontalAlignButton =
    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
        SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription) and
        SemanticsMatcher.keyNotDefined(SemanticsProperties.Text)

/** Every `NumberSettingsTextField` on the tab — the stepper fields leave ImeAction at its default. */
internal fun ComposeUiTest.numberFields(): SemanticsNodeInteractionCollection =
    onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default))

/** Every `FontSettingsDropdown` on the tab — the only controls that call themselves a dropdown list. */
internal fun ComposeUiTest.fontFields(): SemanticsNodeInteractionCollection =
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))

/** The search box of the open font panel, which is the only labelled field inside it. */
internal fun ComposeUiTest.fontSearchBox(): SemanticsNodeInteraction =
    onNodeWithContentDescription(FONT_SEARCH_LABEL)

/** Every None / First Page / Every Page dropdown on the tab. */
internal fun ComposeUiTest.showDropdowns(): SemanticsNodeInteractionCollection =
    onAllNodes(hasClickAction() and (hasText("None") or hasText("First Page") or hasText("Every Page")))

internal fun ComposeUiTest.horizontalAlignButtons(): SemanticsNodeInteractionCollection =
    onAllNodes(horizontalAlignButton)

/** One button of one horizontal-alignment group — [which] is [HAlign.RIGHT]/`CENTER`/`LEFT`. */
internal fun ComposeUiTest.horizontalAlignButton(group: Int, which: Int): SemanticsNodeInteraction =
    horizontalAlignButtons()[group * 3 + which]

internal fun ComposeUiTest.positionButton(group: Int, above: Boolean): SemanticsNodeInteraction =
    onAllNodesWithContentDescription(if (above) "Above" else "Below")[group]

/** One B/I/U/S button — [label] is `"B"`, `"I"`, `"U"` or `"S"`. */
internal fun ComposeUiTest.styleButton(group: Int, label: String): SemanticsNodeInteraction =
    onAllNodes(hasClickAction() and hasText(label))[group]

/** One segmented button, e.g. `segmentedButton("1 Line", ModeRow.LOWER_THIRD)`. */
internal fun ComposeUiTest.segmentedButton(text: String, row: Int): SemanticsNodeInteraction =
    onAllNodesWithText(text)[row]

/** The "Auto" push-buttons next to the lyrics font sizes; only rendered with a PresenterManager. */
internal fun ComposeUiTest.autoFitButtons(): SemanticsNodeInteractionCollection =
    // Not toggleable: the auto-fit *checkboxes* beside these buttons carry the same "Auto" label,
    // and since they became LabeledCheckbox their row is clickable too, so text + click alone now
    // matches four nodes rather than two.
    onAllNodes(hasClickAction() and hasText("Auto") and !isToggleable())

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Retypes the number field currently displaying [showing], and asserts the field then displays what
 * was typed. That holds even for a value the field rejects: `NumberSettingsTextField` always shows
 * what you typed and only withholds the `onValueChange` callback when the value is out of range, so
 * the caller can assert the stored setting separately either way.
 */
internal fun ComposeUiTest.retypeNumberField(showing: Int, to: Int) {
    onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(showing.toString()))
        .onFirstNode("no number field is showing $showing")
        .performTextReplacement(to.toString())
    waitForIdle()
    assertNumberFieldShows(to, "the field just retyped")
}

/**
 * Clicks one button of a mutually-exclusive group and proves the group repainted around it: the
 * button that held the selection must lose its styling, and a button that was unselected before and
 * after must not move at all. (The two unselected buttons are not compared with each other — they
 * carry different glyphs and differently rounded corners, so they never match pixel for pixel.)
 *
 * Deliberately asserts on the buttons that were *not* clicked. A clicked button also takes focus and
 * press indication, so its own pixels change even when the click re-selects what was already
 * selected — comparing it against itself would pass whether or not selection is drawn, which is
 * exactly the vacuous assertion this avoids. `SongSettingsTabRenderingTest` covers the clicked
 * button's own appearance, from fixtures, with no pointer anywhere near it.
 */
internal fun ComposeUiTest.selectAndAssertGroupRepaint(
    click: SemanticsNodeInteraction,
    losesSelection: SemanticsNodeInteraction,
    staysUnselected: SemanticsNodeInteraction,
    what: String,
) {
    click.performScrollTo()
    val loserBefore = losesSelection.renderedPixels()
    val bystanderBefore = staysUnselected.renderedPixels()
    click.performClick()
    waitForIdle()
    assertFalse(
        losesSelection.renderedPixels().contentEquals(loserBefore),
        "$what: the previously selected button must stop being painted as selected",
    )
    assertTrue(
        staysUnselected.renderedPixels().contentEquals(bystanderBefore),
        "$what: a button that was unselected throughout must not change",
    )
}

/** The pixels a node currently paints, for controls that publish no state to assert on. */
internal fun SemanticsNodeInteraction.renderedPixels(): IntArray = captureToImage().toPixelMap().buffer

/** Asserts some font dropdown on the tab is displaying [family]. */
internal fun ComposeUiTest.assertFontFieldShows(family: String, what: String) {
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList) and hasText(family))
        .onFirstNode("$what must display $family")
        .assertExists("$what must display $family")
}

/** Asserts some number field on the tab is displaying [value]. */
internal fun ComposeUiTest.assertNumberFieldShows(value: Int, what: String) {
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
internal fun ComposeUiTest.pickFont(showing: String, to: String) {
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
internal fun ComposeUiTest.pickFontFilterOnly(showing: String, filter: String) {
    openFontPanel(showing)
    fontSearchBox().performTextInput(filter)
    waitForIdle()
}

/** Clicks open the font dropdown currently displaying [showing]. */
internal fun ComposeUiTest.openFontPanel(showing: String) {
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList) and hasText(showing))
        .onFirstNode("no font dropdown is showing $showing")
        .performScrollTo()
        .performClick()
    waitForIdle()
}

/**
 * Opens the [group]-th show dropdown and picks [option] from its menu.
 *
 * The open menu's item and the field behind it both carry the option's text, so the item is picked
 * out by having *only* that text — the field always also carries its own "FULL SCREEN"/"LOWER THIRD"
 * caption.
 */
internal fun ComposeUiTest.chooseShowOption(group: Int, option: String) {
    showDropdowns()[group].performScrollTo().performClick()
    waitForIdle()
    onNode(hasTextExactly(option) and hasClickAction()).performClick()
    waitForIdle()
}

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/**
 * A font family whose name appears in no other installed family's name, so typing it into a
 * `FontSettingsDropdown` filters the menu down to exactly one candidate — which is the only state
 * from which the dropdown commits on the IME action.
 */
internal fun uniquelyNamedFont(): String {
    // Offerable, not merely installed: the picker hides the system's own internal faces, and the
    // first uniquely-named family on a Mac is ".AppleSystemUIFont", which is exactly one of those.
    val fonts = Utils.getAvailableSystemFonts().filterNot { FontCatalog.isHidden(it) }
    return fonts.first { candidate -> fonts.count { it.contains(candidate, ignoreCase = true) } == 1 }
}

/** A font name no installed family matches, used to park a dropdown on a value only it holds. */
internal const val SENTINEL_FONT = "ZzUnusedTestFont"

/** The font panel's search box announces itself with its own placeholder. */
internal const val FONT_SEARCH_LABEL = "Search fonts…"

private fun SemanticsNodeInteractionCollection.onFirstNode(message: String): SemanticsNodeInteraction {
    val count = fetchSemanticsNodes(atLeastOneRootRequired = false).size
    check(count > 0) { message }
    return get(0)
}
