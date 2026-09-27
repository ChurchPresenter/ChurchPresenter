@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two strip rows the other suites do not reach: the section label a song slide can carry, and
 * the signpost to the Lower Third animation a band draws behind its text.
 */
class ProfilesCustomizeStripExtrasTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun doc(
        mode: String = Constants.DISPLAY_MODE_FULLSCREEN,
        label: SongSectionLabel = SongSectionLabel(),
    ) = profileDocument(
        mode = mode,
        profile = OutputProfile(),
        song = SongSettings(layoutExtras = SongLayoutExtras(sectionLabel = label)),
    )

    // ── The section label ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the lyrics carry a section label row`() {
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithText("Section label").assertExists()
        }
    }

    @Test
    fun `switching the section label on writes it for this profile`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertFalse(get().song().layoutExtras.sectionLabel.enabled, "it ships off")

            toggleCheckbox("Section label")

            assertTrue(get().song().layoutExtras.sectionLabel.enabled)
        }
    }

    @Test
    fun `the section label's size is written for this profile`() {
        val label = SongSectionLabel(enabled = true, fullScreen = SongCreditStyle(fontType = "", fontSize = 27))
        profilesTab(doc(label = label)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_SECTION_LABEL)
            retypeNumberField(27, 33)

            assertEquals(33, get().song().layoutExtras.sectionLabel.fullScreen.fontSize)
            assertEquals(
                27,
                get().songSettings.layoutExtras.sectionLabel.fullScreen.fontSize,
                "the document's own value stays",
            )
        }
    }

    @Test
    fun `the section label's colour is written for this profile`() {
        val label = SongSectionLabel(enabled = true, fullScreen = SongCreditStyle(fontType = "", color = "#123456"))
        profilesTab(doc(label = label)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_SECTION_LABEL)
            recolor("#123456", "#ABCDEF")

            assertEquals("#ABCDEF", get().song().layoutExtras.sectionLabel.fullScreen.color)
        }
    }

    @Test
    fun `the section label has a chip of its own, with its switch`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_SECTION_LABEL)
            // Two switches, one each on the chip's rows and in Slides; the chip's comes first.
            val switches = onAllNodes(isToggleable() and hasText("Section label"))
            assertEquals(2, switches.fetchSemanticsNodes().size)
            switches[0].performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().song().layoutExtras.sectionLabel.enabled)
        }
    }

    @Test
    fun `the section label's position is written for the output being edited`() {
        profilesTab(doc(label = SongSectionLabel(enabled = true))) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_SECTION_LABEL)
            segment("Below lyrics").performScrollTo().performClick()
            waitForIdle()

            val label = get().song().layoutExtras.sectionLabel
            assertEquals(Constants.BELOW_LYRICS, label.position)
            assertEquals(Constants.ABOVE_LYRICS, label.lowerThirdPosition, "the band keeps its own")
        }
    }

    @Test
    fun `a band writes the section label's lower third position`() {
        profilesTab(doc(band, label = SongSectionLabel(enabled = true))) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_SECTION_LABEL)
            segment("Top").performScrollTo().performClick()
            waitForIdle()

            val label = get().song().layoutExtras.sectionLabel
            assertEquals(Constants.ABOVE_VERSE, label.lowerThirdPosition)
            assertEquals(Constants.ABOVE_LYRICS, label.position, "the full screen keeps its own")
        }
    }

    @Test
    fun `the next section can be held above the lyrics`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NEXT_SECTION)
            segment("Above lyrics").performScrollTo().performClick()
            waitForIdle()

            assertEquals(Constants.ABOVE_LYRICS, get().song().layoutExtras.nextSectionPosition.fullScreen)
        }
    }

    @Test
    fun `the title can be held above the lyrics`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            segment("Above lyrics").performScrollTo().performClick()
            waitForIdle()

            assertEquals(Constants.ABOVE_LYRICS, get().song().titlePosition)
        }
    }

    @Test
    fun `the lyrics have no position of their own`() {
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithText("Above lyrics").assertDoesNotExist()
        }
    }

    // ── The lower-third animation signpost ──────────────────────────────────────────────────────

    @Test
    fun `a band's Bible strip points at the lower third animation`() {
        profilesTab(doc(band)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            onNodeWithText("Band animation").assertExists()
        }
    }

    @Test
    fun `a full screen has no band animation to point at`() {
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            onNodeWithText("Band animation").assertDoesNotExist()
        }
    }



    // ── Long verses, and the crossfade ──────────────────────────────────────────────────────────

    /**
     * Splitting a long verse across two slides, and the word count that decides what "long" is.
     *
     * The count only appears once the split is on, which is the part worth pinning: a threshold
     * shown beside a switch that is off reads as a setting in force, and there is nothing on the
     * slide to say otherwise.
     */
    @Test
    fun `the long-verse word count appears only once splitting is on`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertFalse(get().bible().splitLongVerses, "it starts off")
            onNodeWithText("words").assertDoesNotExist()

            toggleCheckbox("Split long verses across two slides")

            assertTrue(get().bible().splitLongVerses)
            onNodeWithText("words").assertExists()
        }
    }

    @Test
    fun `the song strip stores a crossfade of its own`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            val before = get().song().crossfade
            toggleCheckbox("Crossfade between items")

            assertEquals(!before, get().song().crossfade)
        }
    }
}
