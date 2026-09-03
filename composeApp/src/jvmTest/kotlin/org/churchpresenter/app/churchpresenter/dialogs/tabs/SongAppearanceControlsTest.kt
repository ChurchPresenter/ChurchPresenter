package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * When the song number and the title appear -- the settings the tab's rewrite dropped.
 *
 * `SongSettingsLeftColumn` held the only UI for `showNumber`, `showNumberLowerThird`,
 * `titleDisplay`, `titleLowerThirdDisplay` and `songNumberBeforeTitle`, and the rewrite stopped
 * calling it without moving them. The file stayed in the tree and kept compiling, so nothing caught
 * it: the song number simply appeared on the lower third with nothing anywhere to switch it off.
 * These tests assert the controls exist *and* write, which is the part a rendering test would miss.
 */
@OptIn(ExperimentalTestApi::class)
class SongAppearanceControlsTest {

    private class Harness {
        var current = AppSettings(songSettings = SongSettings())
    }

    private fun ComposeUiTest.showTab(initial: AppSettings = AppSettings()): Harness {
        val harness = Harness().apply { current = initial }
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var settings by remember { mutableStateOf(initial) }
                SongSettingsTab(
                    settings = settings,
                    onSettingsChange = { transform ->
                        settings = transform(settings)
                        harness.current = settings
                    },
                )
            }
        }
        return harness
    }

    private fun ComposeUiTest.selectElement(label: String) = onNodeWithText(label).performClick()

    /**
     * One option of one element's Show control.
     *
     * Scoped by the control's own tag: "None" is also a text-transform option in the typography
     * grid below, so a bare text match finds two nodes.
     */
    private fun ComposeUiTest.showOption(element: String, option: String) =
        onNode(hasText(option) and hasAnyAncestor(hasTestTag("song_show_$element")))

    @Test
    fun `the song number can be switched off on the lower third`() = runComposeUiTest {
        val harness = showTab()

        onNodeWithText("Lower Third").performClick()
        selectElement("Number")
        showOption("number", "None").performClick()
        waitForIdle()

        assertEquals(Constants.NONE, harness.current.songSettings.showNumberLowerThird)
        assertEquals(
            Constants.FIRST_PAGE,
            harness.current.songSettings.showNumber,
            "and the full screen keeps its own answer",
        )
    }

    @Test
    fun `the song number can be switched off on the full screen`() = runComposeUiTest {
        val harness = showTab()

        selectElement("Number")
        showOption("number", "None").performClick()
        waitForIdle()

        assertEquals(Constants.NONE, harness.current.songSettings.showNumber)
        assertEquals(Constants.FIRST_PAGE, harness.current.songSettings.showNumberLowerThird)
    }

    @Test
    fun `the title can be set to every slide`() = runComposeUiTest {
        val harness = showTab()

        selectElement("Title")
        showOption("title", "Every Page").performClick()
        waitForIdle()

        assertEquals(Constants.EVERY_PAGE, harness.current.songSettings.titleDisplay)
    }

    @Test
    fun `the lyrics element has no show control, because it has nothing to answer`() = runComposeUiTest {
        showTab()

        selectElement("Lyrics")

        onNodeWithTag("song_show_lyrics").assertDoesNotExist()
        onNodeWithTag("song_show_number").assertDoesNotExist()
    }

    @Test
    fun `number-before-title is absent by default, because the two sit apart`() = runComposeUiTest {
        // The title goes above the verse, centred; the number below it, right-aligned -- which is
        // why the number an operator sees on the lower third is in the bottom-right corner. Their
        // order is not a question there, so the switch for it would have no effect.
        showTab()

        selectElement("Number")

        onNodeWithTag("song_songNumberBeforeTitle").assertDoesNotExist()
    }

    // ── The corner the number is pinned to ─────────────────────────────────────────────────────

    @Test
    fun `the corner dropdown writes the full screen's corner`() = runComposeUiTest {
        val harness = showTab()

        selectElement("Number")
        onNodeWithTag("song_number_corner").performClick()
        onNodeWithText("Top Left").performClick()
        waitForIdle()

        assertEquals(Constants.TOP_LEFT, harness.current.songSettings.songNumberCorner)
        assertEquals(
            SongSettings().songNumberLowerThirdCorner,
            harness.current.songSettings.songNumberLowerThirdCorner,
            "and the lower third keeps its own corner",
        )
    }

    @Test
    fun `the corner dropdown writes the lower third's corner`() = runComposeUiTest {
        val harness = showTab()

        onNodeWithText("Lower Third").performClick()
        selectElement("Number")
        onNodeWithTag("song_number_corner").performClick()
        onNodeWithText("Off").performClick()
        waitForIdle()

        assertEquals(Constants.NONE, harness.current.songSettings.songNumberLowerThirdCorner)
        assertEquals(
            SongSettings().songNumberCorner,
            harness.current.songSettings.songNumberCorner,
            "and the full screen keeps its own corner",
        )
    }

    @Test
    fun `the corner dropdown is the number's alone`() = runComposeUiTest {
        // Nothing else on a slide is pinned to a corner, and the title least of all -- it is the
        // element the number is being taken out of the row of.
        showTab()

        selectElement("Title")

        onNodeWithTag("song_number_corner").assertDoesNotExist()
    }

    @Test
    fun `number-before-title stays absent while a corner is set, even sharing a position`() =
        runComposeUiTest {
            val cornered = AppSettings(
                songSettings = SongSettings(
                    songNumberPosition = SongSettings().titlePosition,
                    songNumberHorizontalAlignment = SongSettings().titleHorizontalAlignment,
                    songNumberCorner = Constants.TOP_RIGHT,
                ),
            )
            showTab(cornered)

            selectElement("Number")

            onNodeWithTag("song_songNumberBeforeTitle").assertDoesNotExist()
        }

    @Test
    fun `number-before-title writes when the two do share a position`() = runComposeUiTest {
        val together = AppSettings(
            songSettings = SongSettings(
                songNumberPosition = SongSettings().titlePosition,
                songNumberHorizontalAlignment = SongSettings().titleHorizontalAlignment,
                // Off the corner it defaults to: a cornered number is drawn over the slide and
                // never in the title's row, so the ordering switch is absent while one is set.
                songNumberCorner = Constants.NONE,
            ),
        )
        val harness = showTab(together)

        selectElement("Number")
        onNodeWithTag("song_songNumberBeforeTitle").performClick()
        waitForIdle()

        assertEquals(
            !SongSettings().songNumberBeforeTitle,
            harness.current.songSettings.songNumberBeforeTitle,
        )
    }
}
