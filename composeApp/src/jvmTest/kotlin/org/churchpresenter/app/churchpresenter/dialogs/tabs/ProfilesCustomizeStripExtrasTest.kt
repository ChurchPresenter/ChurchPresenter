@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.OutputProfile
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
    fun `switching the section label on reveals its size and colour`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertFalse(get().song().layoutExtras.sectionLabel.enabled, "it ships off")

            toggleCheckbox("Section label")

            assertTrue(get().song().layoutExtras.sectionLabel.enabled)
            // The size and colour fields come with it; they are asserted by the values they show,
            // the captions beside them being shared with the typography panel above.
            assertNumberFieldShows(
                SongSectionLabel().fontSize,
                "the section label's size, now that it has one",
            )
        }
    }

    @Test
    fun `a section label that is off offers nothing to set`() {
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            // A size for a label nobody draws changes nothing, so the fields go with it.
            onNodeWithText("Font Size").assertDoesNotExist()
        }
    }

    @Test
    fun `the section label's size is written for this profile`() {
        profilesTab(doc(label = SongSectionLabel(enabled = true, fontSize = 27))) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(27, 33)

            assertEquals(33, get().song().layoutExtras.sectionLabel.fontSize)
            assertEquals(
                27,
                get().songSettings.layoutExtras.sectionLabel.fontSize,
                "the document's own value stays",
            )
        }
    }

    @Test
    fun `the section label's colour is written for this profile`() {
        profilesTab(doc(label = SongSectionLabel(enabled = true, color = "#123456"))) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            recolor("#123456", "#ABCDEF")

            assertEquals("#ABCDEF", get().song().layoutExtras.sectionLabel.color)
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
