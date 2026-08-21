@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Clicking a lower third in the schedule and landing on it in this tab.
 *
 * The schedule stores a *label*, not a path, so the tab has to resolve it back to a file on disk —
 * and the two do not always agree. A preset stored as "Welcome" has to match `Welcome.json`, and an
 * item saved on another machine, or before a rename, may match nothing at all.
 *
 * Getting this wrong during a service is quiet and expensive: the operator clicks the item they
 * queued, the tab shows whatever was already selected, and they go live with the wrong graphic. So
 * the miss case matters as much as the hit — what must NOT happen is landing on the wrong preset.
 *
 * See `LowerThirdTabTestSupport.kt` for the harness; the fixture folder holds "Welcome" and
 * "Speaker Name".
 */
class LowerThirdScheduleSelectionTest {

    private fun item(label: String, id: String = "preset-1") =
        ScheduleItem.LowerThirdItem(
            id = "sched-1",
            presetId = id,
            presetLabel = label,
            pauseAtFrame = false,
            pauseDurationMs = 0L,
        )

    @Test
    fun `an item naming a preset selects that preset`() =
        lowerThirdTab(selectedLowerThirdItem = item("Welcome")) { _ ->
            waitForIdle()

            // Asserted as the *disappearance of the empty prompt*, not the presence of the name:
            // "Welcome" is also a row in the preset list, so looking for it would pass whether or
            // not anything was selected.
            assertTrue(
                !showsContainingText(LowerThirdLabel.SELECT_PRESET),
                "the scheduled preset must actually be selected: ${renderedText()}",
            )
        }

    @Test
    fun `the label is matched without its file extension`() =
        lowerThirdTab(selectedLowerThirdItem = item("Speaker Name")) { _ ->
            waitForIdle()

            // On disk it is "Speaker Name.json"; the schedule stores the bare name.
            assertTrue(!showsContainingText(LowerThirdLabel.SELECT_PRESET), renderedText().toString())
        }

    @Test
    fun `an item matching no preset leaves the selection alone`() =
        lowerThirdTab(selectedLowerThirdItem = item("Deleted Since Last Sunday")) { _ ->
            waitForIdle()

            // The preset was removed or renamed after the service was built. Selecting nothing is
            // the honest outcome; selecting *something* would put an unrelated graphic one click
            // from going live.
            assertTrue(
                showsContainingText(LowerThirdLabel.SELECT_PRESET),
                "nothing must be selected: ${renderedText()}",
            )
        }

    @Test
    fun `the preset id is used when the label does not match`() =
        lowerThirdTab(selectedLowerThirdItem = item(label = "Renamed", id = "Welcome")) { _ ->
            waitForIdle()

            // Label first, id second — the fallback is what keeps an older schedule working after
            // someone renames a preset's label but not its file.
            assertTrue(!showsContainingText(LowerThirdLabel.SELECT_PRESET), renderedText().toString())
        }

    @Test
    fun `no scheduled item leaves the tab on its empty prompt`() = lowerThirdTab { _ ->
        assertTrue(
            showsContainingText(LowerThirdLabel.SELECT_PRESET),
            "with nothing scheduled the tab asks for a choice: ${renderedText()}",
        )
    }
}
