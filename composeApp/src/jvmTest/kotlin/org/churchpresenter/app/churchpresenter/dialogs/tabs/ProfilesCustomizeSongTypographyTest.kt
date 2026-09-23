@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Song pane's second row of type controls: the ones that are not size, colour or B/I/U.
 *
 * They share one shape and one failure mode. Each is drawn only for the elements it applies to --
 * the chord colour on the lyrics alone, the above/below position on the number and the title alone
 * -- and each writes one field of a pair, full screen or band. A control wired to the wrong half of
 * the pair looks right in the pane and moves the other output; a control drawn for an element that
 * has no such field writes nothing at all and simply never takes effect. Both are invisible until
 * someone presents with them.
 *
 * `ProfilesCustomizeSongControlsTest` covers size, colour, alignment, case and the style quartet;
 * this is what is left over.
 */
class ProfilesCustomizeSongTypographyTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(
        mode = mode,
        profile = OutputProfile(songMode = Constants.SONG_LANG_PRIMARY),
        song = SongSettings(
            lyricsChordColor = "#4FD3E8",
            // Distinct, and neither at a default: the two sliders sit in one row and each is found
            // by the readout beside it, so a shared value makes them indistinguishable.
            lyricsLetterSpacing = 3,
            lyricsWordSpacing = 7,
            lyricsShadow = true,
            lyricsShadowColor = "#010203",
            lyricsShadowSize = 100,
            lyricsShadowOpacity = 90,
        ),
    )

    // ── Chords ──────────────────────────────────────────────────────────────────────────────────

    /**
     * The chord colour, which only the lyrics carry.
     *
     * The field and both writers were here long before the control was: chords drew in the default
     * cyan on every install because nothing in the interface could set it. So the interesting
     * assertion is simply that a click on it reaches the stored field at all.
     */
    @Test
    fun `the lyrics carry a chord colour, and it writes`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            recolor("#4FD3E8", "#FF8800")

            assertEquals("#FF8800", get().song().lyricsChordColor)
        }
    }

    @Test
    fun `no other element is offered one`() {
        // Chords are drawn over the lyrics and nowhere else, so the control on, say, the title
        // would be a colour the presenter never reads.
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)

            assertTrue(
                onAllNodesWithText("Chord Color").fetchSemanticsNodes().isEmpty(),
                "only the lyrics carry a chord colour",
            )
        }
    }

    // ── Above or below the lyrics ───────────────────────────────────────────────────────────────

    @Test
    fun `the title can be moved below the lyrics`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_TITLE)
            onAllNodesWithContentDescription("Below")[0].performScrollTo().performClick()
            waitForIdle()

            assertEquals(Constants.BELOW_VERSE, get().song().titlePosition)
        }
    }

    @Test
    fun `the lyrics themselves have nowhere to go`() {
        // Everything else is positioned relative to the lyrics, so the lyrics have no position of
        // their own -- and a control saying otherwise would be one that stores nothing.
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)

            assertTrue(
                onAllNodesWithContentDescription("Above").fetchSemanticsNodes().isEmpty(),
                "only the number and the title sit above or below",
            )
        }
    }

    // ── Spacing ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Letter and word spacing, which are two sliders in one row.
     *
     * They are `key`ed in the source because the position control above them appears and disappears
     * with the element, and a composable's identity is its call-site position: without the keys the
     * two sliders each inherit whichever one previously stood where they now stand, and the value
     * shown stops matching the value stored.
     */
    @Test
    fun `letter spacing and word spacing are stored apart`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            tapSliderTrack("Letter spacing", "3px", fraction = 0.9f)

            assertTrue(get().song().lyricsLetterSpacing > 3, "the letter-spacing slider must have moved it")
            assertEquals(7, get().song().lyricsWordSpacing, "the slider beside it must not have moved")
        }
    }

    @Test
    fun `word spacing moves on its own too`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            tapSliderTrack("Word spacing", "7px", fraction = 0.9f)

            assertTrue(get().song().lyricsWordSpacing > 7)
            assertEquals(3, get().song().lyricsLetterSpacing, "the slider beside it must not have moved")
        }
    }

    // ── Strikethrough, outline and the text backing ─────────────────────────────────────────────

    @Test
    fun `the S beside B I U is strikethrough, not shadow`() {
        // Shadow has a labelled checkbox of its own below, with three detail controls folded under
        // it; two buttons in one row both reading "S" could not be told apart, so this one is the
        // strikethrough.
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            styleButton(group = 0, label = "S").performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().song().lyricsStrikethrough)
        }
    }

    @Test
    fun `the outline button switches the stroke on`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertFalse(get().song().outlines.lyrics.enabled, "it starts off")

            onAllNodesWithContentDescription("Outline")[0].performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().song().outlines.lyrics.enabled)
        }
    }

    @Test
    fun `the text-backing button switches the backdrop on`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertFalse(get().song().lyricsBackdrop.lineBackground, "it starts off")

            onAllNodesWithContentDescription("Text backing")[0].performScrollTo().performClick()
            waitForIdle()

            // The button restores the last shape it drew rather than a fixed one, so what it turns
            // on is "a backing of some kind" -- fill, border or both.
            val backdrop = get().song().lyricsBackdrop
            assertTrue(backdrop.lineBackground || backdrop.border)
        }
    }

    // ── The shadow's own three controls ─────────────────────────────────────────────────────────

    @Test
    fun `a shadow that is on reveals its colour, size and opacity`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            recolor("#010203", "#FF0000")

            assertEquals("#FF0000", get().song().lyricsShadowColor)
            assertEquals(100, get().song().lyricsShadowSize, "the other two must not move with it")
            assertEquals(90, get().song().lyricsShadowOpacity)
        }
    }

    @Test
    fun `switching the shadow off folds its three controls away`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            shadowCheckbox(group = 0).performScrollTo().performClick()
            waitForIdle()

            assertFalse(get().song().lyricsShadow)
            assertEquals("#010203", get().song().lyricsShadowColor, "its settings outlive being switched off")
        }
    }

    // ── The band writes its own half of every pair ──────────────────────────────────────────────

    @Test
    fun `a band's lyrics store their own strikethrough and spacing`() {
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            styleButton(group = 0, label = "S").performScrollTo().performClick()
            waitForIdle()

            assertTrue(get().song().lyricsLowerThirdStrikethrough)
            assertFalse(get().song().lyricsStrikethrough, "the full screen's own must be untouched")
        }
    }
}
