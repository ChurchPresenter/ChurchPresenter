@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The letter- and word-spacing sliders shared by the Song and Bible panes. Both run a -20..100
 * track, so halfway along is 40; see [tapSliderTrack] for why the far end is a pixel short.
 */
class ProjectionCustomizeSpacingTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun songOutput(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        songSettings = SongSettings(
            lyricsLetterSpacing = 3,
            lyricsWordSpacing = 7,
            lyricsLowerThirdLetterSpacing = 11,
            lyricsLowerThirdWordSpacing = 15,
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun bibleOutput(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(
                    fileName = "kjv.spb",
                    textLetterSpacing = 3,
                    textWordSpacing = 7,
                    lowerThirdTextLetterSpacing = 11,
                    lowerThirdTextWordSpacing = 15,
                ),
            ),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.storedSong(): SongSettings =
        assertNotNull(projectionSettings.screenAssignments[0].songOverride, "the output must have its own Songs")

    private fun AppSettings.storedTranslation(): BibleTranslationSettings =
        assertNotNull(projectionSettings.screenAssignments[0].bibleOverride, "the output must have its own Bible")
            .translationList()[0]

    private val letter = "Letter spacing"
    private val word = "Word spacing"

    // ── The Song pane ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `both spacing sliders show what the full screen has stored`() {
        projectionTab(songOutput()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            onNodeWithText("3px").assertExists("the letter spacing's readout")
            onNodeWithText("7px").assertExists("the word spacing's readout")
        }
    }

    @Test
    fun `the letter-spacing slider writes the full screen's lyrics`() {
        projectionTab(songOutput()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            tapSliderTrack(letter, "3px", fraction = 0.5f)

            val stored = get().storedSong()
            assertEquals(40, stored.lyricsLetterSpacing, "halfway along a -20..100 track")
            assertEquals(7, stored.lyricsWordSpacing, "the slider beside it must not move")
            assertEquals(11, stored.lyricsLowerThirdLetterSpacing, "nor the band's own")
        }
    }

    @Test
    fun `the word-spacing slider writes the full screen's lyrics`() {
        projectionTab(songOutput()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            tapSliderTrack(word, "7px", fraction = 0.5f)

            val stored = get().storedSong()
            assertEquals(40, stored.lyricsWordSpacing)
            assertEquals(3, stored.lyricsLetterSpacing, "the slider above it must not move")
            assertEquals(15, stored.lyricsLowerThirdWordSpacing, "nor the band's own")
        }
    }

    @Test
    fun `the letter-spacing slider writes the band's lyrics instead`() {
        projectionTab(songOutput(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            tapSliderTrack(letter, "11px", fraction = 0.5f)

            val stored = get().storedSong()
            assertEquals(40, stored.lyricsLowerThirdLetterSpacing)
            assertEquals(3, stored.lyricsLetterSpacing, "the full screen's own must be untouched")
        }
    }

    @Test
    fun `the word-spacing slider writes the band's lyrics instead`() {
        projectionTab(songOutput(band)) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            tapSliderTrack(word, "15px", fraction = 0.5f)

            val stored = get().storedSong()
            assertEquals(40, stored.lyricsLowerThirdWordSpacing)
            assertEquals(7, stored.lyricsWordSpacing, "the full screen's own must be untouched")
        }
    }

    @Test
    fun `spacing can be pulled negative, which is what the track's near end is for`() {
        projectionTab(songOutput()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            tapSliderTrack(letter, "3px", fraction = 0f)

            assertEquals(-20, get().storedSong().lyricsLetterSpacing, "letters may be tightened, not only opened")
        }
    }

    @Test
    fun `the readout follows the handle`() {
        projectionTab(songOutput()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            tapSliderTrack(letter, "3px", fraction = 0.5f)
            onNodeWithText("40px").assertExists("the readout must show what was stored")
        }
    }

    // ── The Bible pane ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the letter-spacing slider writes the full screen's verse text`() {
        projectionTab(bibleOutput()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            tapSliderTrack(letter, "3px", fraction = 0.5f)

            val stored = get().storedTranslation()
            assertEquals(40, stored.textLetterSpacing)
            assertEquals(11, stored.lowerThirdTextLetterSpacing, "the band's own must be untouched")
        }
    }

    @Test
    fun `the word-spacing slider writes the full screen's verse text`() {
        projectionTab(bibleOutput()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            tapSliderTrack(word, "7px", fraction = 0.5f)

            val stored = get().storedTranslation()
            assertEquals(40, stored.textWordSpacing)
            assertEquals(3, stored.textLetterSpacing, "the slider above it must not move")
        }
    }

    @Test
    fun `the letter-spacing slider writes the band's verse text instead`() {
        projectionTab(bibleOutput(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            tapSliderTrack(letter, "11px", fraction = 0.5f)

            val stored = get().storedTranslation()
            assertEquals(40, stored.lowerThirdTextLetterSpacing)
            assertEquals(3, stored.textLetterSpacing, "the full screen's own must be untouched")
        }
    }

    @Test
    fun `the word-spacing slider writes the band's verse text instead`() {
        projectionTab(bibleOutput(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            tapSliderTrack(word, "15px", fraction = 0.5f)

            val stored = get().storedTranslation()
            assertEquals(40, stored.lowerThirdTextWordSpacing)
            assertEquals(7, stored.textWordSpacing, "the full screen's own must be untouched")
        }
    }

    @Test
    fun `the case picker writes the band's verse text`() {
        projectionTab(bibleOutput(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            chooseSegment("AA")

            val stored = get().storedTranslation()
            assertEquals(Constants.TEXT_TRANSFORM_UPPERCASE, stored.lowerThirdTextTransform)
            assertEquals(
                Constants.TEXT_TRANSFORM_NONE,
                stored.textTransform,
                "the full screen's own case must be untouched",
            )
        }
    }
}
