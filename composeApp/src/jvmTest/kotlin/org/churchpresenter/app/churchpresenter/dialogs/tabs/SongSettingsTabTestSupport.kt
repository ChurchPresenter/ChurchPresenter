@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedPixels

/**
 * Harness and node locators shared by the `SongSettingsTab` test classes.
 *
 * The tab is the largest pure-View surface in the app (~1,450 lines) and it renders the *same*
 * widgets over and over — eight styled-text blocks each with a font-size field, a font dropdown, a
 * colour field, four style buttons and a shadow row. None of it is refactored for testability, so
 * every locator here works off what the production tree already publishes: `testTag`s on the
 * checkboxes, content descriptions on the stepper and position/vertical-alignment icons, and the
 * text the widgets actually display.
 *
 * Two locator styles are used, deliberately:
 *
 *  * **By value** — for text fields and colour fields. The fixture gives the field under test a
 *    value no other field on the tab holds (e.g. `songNumberFontSize = 111`), then finds it by that
 *    value. Reads like the UI, and is immune to controls being added or reordered around it.
 *  * **By ordinal** — for the button groups that publish neither a tag nor any text of their own
 *    (horizontal-alignment icons, B/I/U/S style buttons, the segmented mode rows). The index is the
 *    widget's position in composition order, named through the `*Group` objects below.
 *    `SongSettingsTabStructureTest` pins every one of those counts, so if a control is added or
 *    moved that test fails first and says so, instead of an ordinal test failing somewhere obscure.
 *
 * Two production quirks shape the locators. `SlimSlider`'s track publishes no semantics at all, so
 * the transition-duration slider can only be driven by injecting a click at a computed coordinate
 * (see the slider test). And the horizontal-alignment icons pass `contentDescription = null`, unlike
 * their vertical-alignment and position siblings, which is why they need the "a Button with neither
 * text nor a description" matcher below.
 *
 * Known coverage gaps — the four spots in `SongSettingsTab.kt` these tests deliberately do not
 * reach, all of them unreachable rather than untested:
 *
 *  * `else -> Constants.FIRST_PAGE` in each of the four show dropdowns' `onValueChange`. The
 *    dropdown's own `options` list holds exactly the three strings the `when` matches, so no click
 *    can produce a fourth value. (The mirror-image `else -> firstPageStr` on the *render* side is
 *    reachable — a settings file can hold anything — and is covered.)
 *  * `if (lyricsText.isBlank()) return@TextButton` in both auto-fit buttons. The button's `enabled`
 *    condition already requires a non-blank line, so the guard cannot fire from the UI;
 *    `the auto-fit buttons leave the size alone when the live section is blank` asserts that.
 *  * `count == 1` in `segmentedItemShape`. Every segmented row on this tab has two or three items.
 *  * The increment/decrement arrows on every stepper field. The fields here are driven by typing
 *    into them instead. (The arrows were once unclickable app-wide — the shared
 *    `NumberSettingsTextField` gave its 20.dp arrow column no room — which is why these tests type.
 *    That is fixed; `NumberSettingsTextFieldTest` covers the arrows and the range clamp behind them,
 *    and the Projection, Dictionary and Stage Monitor suites assert they are clickable on a real tab.)
 *  * The `when`-on-String jump tables compile to a hash switch followed by an `equals` check; the
 *    "hash matched but the string differs" arms need a hash-colliding value to reach.
 */
@OptIn(ExperimentalTestApi::class)
internal fun songTab(
    initial: AppSettings = AppSettings(),
    presenterManager: PresenterManager? = null,
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            SongSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
                presenterManager = presenterManager,
            )
        }
    }
    block { current }
}

// ── Ordinal maps ────────────────────────────────────────────────────────────────────────────────

/** Ordinal of each B/I/U/S button group, in composition order. */
internal object StyleGroup {
    const val TITLE_FULLSCREEN = 0
    const val TITLE_LOWER_THIRD = 1
    const val LYRICS_FULLSCREEN = 2
    const val LYRICS_LOWER_THIRD = 3
    const val LOOK_AHEAD = 4
    const val LOOK_AHEAD_NEXT = 5
    const val LT_LOOK_AHEAD = 6
    const val LT_LOOK_AHEAD_NEXT = 7
    const val COUNT = 8
}

/** Ordinal of each horizontal-alignment button group, in composition order. */
internal object HAlignGroup {
    const val SONG_NUMBER_FULLSCREEN = 0
    const val SONG_NUMBER_LOWER_THIRD = 1
    const val TITLE_FULLSCREEN = 2
    const val TITLE_LOWER_THIRD = 3
    const val LYRICS_FULLSCREEN = 4
    const val LYRICS_LOWER_THIRD = 5
    const val LOOK_AHEAD = 6
    const val LT_LOOK_AHEAD = 7
    const val COUNT = 8
}

/** Ordinal of each above/below position button pair, in composition order. */
internal object PositionGroup {
    const val SONG_NUMBER_FULLSCREEN = 0
    const val SONG_NUMBER_LOWER_THIRD = 1
    const val TITLE_FULLSCREEN = 2
    const val TITLE_LOWER_THIRD = 3
    const val COUNT = 4
}

/** Ordinal of each None/First Page/Every Page dropdown, in composition order. */
internal object ShowDropdown {
    const val NUMBER_FULLSCREEN = 0
    const val NUMBER_LOWER_THIRD = 1
    const val TITLE_FULLSCREEN = 2
    const val TITLE_LOWER_THIRD = 3
    const val COUNT = 4
}

/** Ordinal of each segmented display-mode / language row, in composition order. */
internal object ModeRow {
    const val FULLSCREEN = 0
    const val LOWER_THIRD = 1
    const val LOOK_AHEAD = 2
    const val LT_LOOK_AHEAD = 3
    const val COUNT = 4
}

/** Position of a button inside one horizontal-alignment group — the row is laid out right-first. */
internal object HAlign {
    const val RIGHT = 0
    const val CENTER = 1
    const val LEFT = 2
}

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

/** The "Auto" push-buttons next to the lyrics font sizes; only rendered with a PresenterManager. */
internal fun ComposeUiTest.autoFitButtons(): SemanticsNodeInteractionCollection =
    // Not toggleable: the auto-fit *checkboxes* beside these buttons carry the same "Auto" label,
    // and since they became LabeledCheckbox their row is clickable too, so text + click alone now
    // matches four nodes rather than two.
    onAllNodes(hasClickAction() and hasText("Auto") and !isToggleable())

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

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
