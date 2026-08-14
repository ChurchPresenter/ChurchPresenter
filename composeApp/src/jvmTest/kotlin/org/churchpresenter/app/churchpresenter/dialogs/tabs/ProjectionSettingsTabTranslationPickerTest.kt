@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ProjectionSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The per-output Bible translation picker in the Content Outputs dialog.
 *
 * Which translations an output shows is stored as [ScreenAssignment.bibleTranslations], a list of
 * positions in the configured stack, where **empty means all of them** — so that a translation added
 * later appears on every output rather than having to be ticked on each one. Most of what is tested
 * here follows from that one normalisation:
 *
 *  * it makes "none selected" unrepresentable as a selection, so showing none of them has to be
 *    stored as `bibleMode = SONG_LANG_OFF` instead, which is the same statement about the output.
 *    Before that, unticking the last box wrote an empty list that read straight back as *all* and
 *    every box silently re-ticked;
 *  * and it means a **position** is only meaningful against the stack it was stored for. One past the
 *    end of the current stack is ignored rather than counted, and a selection left with none of its
 *    positions surviving shows nothing rather than reading as the empty "all of them".
 *
 * Because clearing switches scripture off, both the trigger and the menu are mounted regardless of
 * whether scripture is on. Gate them on it — `expanded = dropdownOpen && showing` is all it takes —
 * and both halves of the second bug come back at once: Clear shuts the menu under the hand that just
 * pressed it, and because nothing cleared the flag that opened it, switching scripture back on later
 * pops the menu open again unprompted. That mutation is what `the menu does not reopen by itself after * Clear` was checked against; it fails on the first assertion, the one saying Clear left the menu
 * standing.
 *
 * This picker was rewritten (`567242f8`) after those fixes landed, which deleted the suite that
 * guarded them. These are the cases worth having back, restated for the current UI: the captions and
 * row structure are all different, and the on/off control now lives inside the menu rather than beside
 * the trigger.
 *
 * The picker's controls are addressed by test tag, not caption: see [TranslationPickerTags]. The
 * cleared trigger reads "None", which is also what an unassigned target-display dropdown reads, so
 * text was never a safe way to find it.
 */
class ProjectionSettingsTabTranslationPickerTest {

    private fun ComposeUiTest.openContentOutputs(row: Int = 0) {
        gridButton(Grid.contentOutputs(row)).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openPicker() {
        translationTrigger().performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.toggleTranslation(index: Int) {
        translationRow(index).performClick()
        waitForIdle()
    }

    /**
     * Closes the open translation menu the way an operator does: by clicking away from it.
     *
     * The click is aimed at the dialog's own "Quick Select" heading — inert text, chosen so that
     * nothing but the dismissal can happen. The menu's popup spans the whole window and takes the
     * press as an outside one, so the heading never actually receives it; a later click is needed to
     * reach anything under the menu. Escape does not work here: this popup does not handle it.
     */
    private fun ComposeUiTest.dismissPopup() {
        onNodeWithText("QUICK SELECT").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.assertMenuOpen() =
        translationRow(0).assertExists("the menu must still be open")

    private fun row0(get: () -> AppSettings): ScreenAssignment =
        get().projectionSettings.screenAssignments[0]

    @Test
    fun `unticking the last translation switches the output off`() = projectionTab(
        initial = threeTranslations(),
    ) { get ->
        openContentOutputs()
        openPicker()
        assertEquals(emptyList(), row0(get).bibleTranslations, "an untouched output shows all of them")

        toggleTranslation(0)
        toggleTranslation(1)
        assertEquals(listOf(2), row0(get).bibleTranslations, "the two unticked ones are gone from the selection")

        toggleTranslation(2)

        // Showing none of them is the same statement as an unticked cell. Storing it as an empty
        // selection instead is what used to read back as "all" and re-tick every box.
        assertEquals(Constants.SONG_LANG_OFF, row0(get).bibleMode)
        translationMaster().assertIsOff()
        onNodeWithText("0 of 3 translations enabled").assertExists()
    }

    @Test
    fun `the menu does not reopen by itself after Clear`() = projectionTab(
        initial = threeTranslations(),
    ) { get ->
        openContentOutputs()
        openPicker()

        onNodeWithText("Clear").performClick()
        waitForIdle()

        // Clearing is usually a step towards picking a couple, so the menu has to survive being the
        // thing that switched scripture off.
        assertEquals(Constants.SONG_LANG_OFF, row0(get).bibleMode)
        assertMenuOpen()

        dismissPopup()
        translationRow(0).assertDoesNotExist()

        // Switching scripture back on from outside the picker — the flag that opens the menu must
        // not be riding on whether scripture is on.
        onNodeWithText("Select All").performClick()
        waitForIdle()

        assertEquals(Constants.SONG_LANG_BOTH, row0(get).bibleMode)
        // The menu must not have opened by itself.
        translationRow(0).assertDoesNotExist()

        // ...and it still opens on demand.
        openPicker()
        translationMaster().assertIsOn()
    }

    @Test
    fun `a position left behind by a removed translation is not counted`() = projectionTab(
        // Position 7 names a translation that is not in this three-deep stack. Stack edits remap
        // selections now, so this is what reaches the picker from a settings file written before they
        // did, or a hand-edited one.
        initial = threeTranslations(
            ProjectionSettings(screenAssignments = listOf(ScreenAssignment(bibleTranslations = listOf(0, 7)))),
        ),
    ) { _ ->
        openContentOutputs()
        openPicker()

        // One position survives, so one translation is enabled — the count used to say two, while the
        // preview chip on the row behind it named only the one.
        onNodeWithText("1 of 3 translations enabled").assertExists()
        // And one of three reads as a partial selection, not as all of them.
        translationMaster().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate),
        )
    }

    @Test
    fun `a selection of nothing but removed translations shows none, not all`() = projectionTab(
        initial = threeTranslations(
            ProjectionSettings(screenAssignments = listOf(ScreenAssignment(bibleTranslations = listOf(7, 8)))),
        ),
    ) { _ ->
        openContentOutputs()
        openPicker()

        // Naming translations that have gone is not the same statement as the empty "all of them", so
        // it must not read as all: that would put three languages on an output narrowed to one.
        onNodeWithText("0 of 3 translations enabled").assertExists()
    }

    @Test
    fun `ticking a translation while the output is off switches it back on alone`() = projectionTab(
        initial = threeTranslations(
            ProjectionSettings(screenAssignments = listOf(ScreenAssignment(bibleMode = Constants.SONG_LANG_OFF))),
        ),
    ) { get ->
        openContentOutputs()
        openPicker()

        toggleTranslation(1)

        // Both halves in one event — two back-to-back callbacks would each read the same pre-click
        // assignment and the second would overwrite the first.
        assertEquals(Constants.SONG_LANG_BOTH, row0(get).bibleMode)
        assertEquals(listOf(1), row0(get).bibleTranslations, "only the one just ticked")
    }
}
