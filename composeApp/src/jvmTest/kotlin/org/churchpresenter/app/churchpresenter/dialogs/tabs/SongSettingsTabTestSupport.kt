@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings

/**
 * Harness and locators shared by the `SongSettingsTab` test classes.
 *
 * The tab keeps **one** set of styling controls and points them with two segmented switches — Full
 * Screen / Lower Third, and the five element tabs — so a test names the combination the operator
 * would pick rather than counting occurrences of a repeated control. That is what [pointAt] is for.
 *
 * Locators work off what the production tree already publishes: the text on the switches and
 * controls, `testTag`s on the two title-slide checkboxes, and content descriptions on the position
 * and vertical-alignment icons. Nothing here asks the tab to be changed for testability.
 *
 * Two production quirks shape them. `SlimSlider`'s track publishes no semantics at all, so a slider
 * can only be driven by injecting a click at a computed coordinate. And the horizontal-alignment
 * icons pass `contentDescription = null`, unlike their vertical-alignment and position siblings, so
 * they are found by position under their own caption.
 *
 * ## Coverage removed with the old tab, not replaced
 *
 * The suites this replaced held 178 tests against a tab of four scrolling columns that rendered the
 * same eight styled-text blocks over and over. Most of them located a control by its ordinal among
 * those repeats — `StyleGroup.LT_LOOK_AHEAD_NEXT`, "one colour field per styled text block" — and
 * there are no repeats left to count. They were deleted rather than repaired because they drive UI
 * that no longer exists.
 *
 * What is genuinely thinner as a result, and worth restoring:
 *
 *  * **The label inventory.** One test asserted every string the tab draws, and its exact count.
 *    Nothing here replaces it, so a stray or duplicated label is no longer caught.
 *  * **Recomposition.** The old suite checked the tab survived a recomposition that changed none of
 *    its inputs, and that a settings change re-rendered around the control that caused it.
 *  * **Per-block independence at the pixel level.** `SongSettingsTabRenderingTest` painted each
 *    block's buttons and asserted a change to one left the others alone. The lens tests
 *    ([SongElementStyleTest]) now assert that at the settings level, which is where the bug would
 *    be, but not that the *painting* follows.
 */
@OptIn(ExperimentalTestApi::class)
internal fun songTab(
    initial: AppSettings = AppSettings(),
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            SongSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
            )
        }
    }
    block { current }
}

/** The label each element tab carries, which is how a test names the element it wants. */
internal val SongStyleElement.tabLabel: String
    get() = when (this) {
        SongStyleElement.NUMBER -> "Number"
        SongStyleElement.TITLE -> "Title"
        SongStyleElement.LYRICS -> "Lyrics"
        SongStyleElement.LOOK_AHEAD -> "Look Ahead"
        SongStyleElement.NEXT_SECTION -> "Next Section"
    }

/** The label each output switch carries. */
internal val SongStyleTarget.switchLabel: String
    get() = if (isLowerThird) "Lower Third" else "Full Screen"

/**
 * Points the one control set at [element] on [target].
 *
 * The output first: switching it rebuilds the panel, so choosing the element afterwards is what
 * leaves the tab where the test wants it.
 */
internal fun ComposeUiTest.pointAt(element: SongStyleElement, target: SongStyleTarget) {
    onNodeWithText(target.switchLabel).performClick()
    waitForIdle()
    onNodeWithText(element.tabLabel).performClick()
    waitForIdle()
}

/** Every element on every output, which is what a per-profile test walks. */
internal val songProfiles: List<Pair<SongStyleElement, SongStyleTarget>> =
    SongStyleElement.entries.flatMap { element -> SongStyleTarget.entries.map { element to it } }
