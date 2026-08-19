package org.churchpresenter.app.churchpresenter.utils

import core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every predicate here answers "is this feature actually reaching the congregation right now",
 * which is always content AND output configuration together. Each test names which half it removes.
 */
class UsageDetectionTest {

    private fun song(
        secondary: List<String> = listOf("Vtoraya stroka"),
        lyrics: List<String> = listOf("Amazing grace how sweet the sound"),
    ) = SongItem(number = "1", title = "Amazing Grace", lyrics = lyrics, secondaryLyrics = secondary)

    private fun out(
        songMode: String = Constants.SONG_LANG_BOTH,
        bibleMode: String = Constants.SONG_LANG_BOTH,
        bibleTranslations: List<Int> = emptyList(),
        displayMode: String = Constants.DISPLAY_MODE_FULLSCREEN,
        targetDisplay: Int = 0,
    ) = ScreenAssignment(
        targetDisplay = targetDisplay,
        songMode = songMode,
        bibleMode = bibleMode,
        bibleTranslations = bibleTranslations,
        displayMode = displayMode,
    )

    // ── Bilingual songs ─────────────────────────────────────────────────────────

    @Test
    fun `a two-language song on an output showing both languages is dual language`() {
        assertTrue(isDualLanguagePresentation(song(), listOf(out(songMode = Constants.SONG_LANG_BOTH))))
        assertTrue(isDualLanguagePresentation(song(), listOf(out(songMode = Constants.SONG_LANG_SECONDARY))))
    }

    @Test
    fun `a song with no second language is never dual language, whatever the outputs say`() {
        assertFalse(isDualLanguagePresentation(song(secondary = emptyList()), listOf(out())))
    }

    @Test
    fun `a two-language song shown primary-only is not dual language`() {
        assertFalse(
            isDualLanguagePresentation(
                song(),
                listOf(out(songMode = Constants.SONG_LANG_PRIMARY), out(songMode = Constants.SONG_LANG_OFF)),
            )
        )
    }

    @Test
    fun `one output showing both is enough, and a switched-off one is not`() {
        assertTrue(
            isDualLanguagePresentation(
                song(),
                listOf(out(songMode = Constants.SONG_LANG_PRIMARY), out(songMode = Constants.SONG_LANG_BOTH)),
            )
        )
        assertFalse(
            isDualLanguagePresentation(song(), listOf(out(targetDisplay = Constants.KEY_TARGET_NONE)))
        )
        assertFalse(isDualLanguagePresentation(song(), emptyList()))
    }

    // ── Parallel Bible translations ─────────────────────────────────────────────

    @Test
    fun `an output showing the whole stack of two is multi translation`() {
        assertTrue(isMultiTranslationPresentation(2, listOf(out())))
    }

    @Test
    fun `a single-translation stack is never multi translation`() {
        assertFalse(isMultiTranslationPresentation(1, listOf(out())))
    }

    @Test
    fun `an output pinned to one translation of the stack is not multi translation`() {
        assertFalse(isMultiTranslationPresentation(3, listOf(out(bibleTranslations = listOf(0)))))
        assertTrue(isMultiTranslationPresentation(3, listOf(out(bibleTranslations = listOf(0, 2)))))
    }

    @Test
    fun `translations chosen past the end of the stack are ignored, not counted`() {
        // A settings file can name translations that have since been removed.
        assertFalse(isMultiTranslationPresentation(2, listOf(out(bibleTranslations = listOf(0, 5)))))
    }

    @Test
    fun `an output not showing the bible at all is not multi translation`() {
        assertFalse(isMultiTranslationPresentation(2, listOf(out(bibleMode = Constants.SONG_LANG_OFF))))
        assertFalse(isMultiTranslationPresentation(2, listOf(out(targetDisplay = Constants.KEY_TARGET_NONE))))
    }

    // ── Split screen ────────────────────────────────────────────────────────────

    @Test
    fun `one translation on one screen and another on the next is split screen`() {
        assertTrue(
            isSplitScreenBible(
                2,
                listOf(out(bibleTranslations = listOf(0)), out(bibleTranslations = listOf(1))),
            )
        )
    }

    @Test
    fun `outputs that all show the same thing are not split screen`() {
        assertFalse(isSplitScreenBible(2, listOf(out(), out())))
        assertFalse(
            isSplitScreenBible(
                2,
                listOf(out(bibleTranslations = listOf(1)), out(bibleTranslations = listOf(1))),
            )
        )
    }

    @Test
    fun `an output showing everything and one pinned to a single translation is a split`() {
        assertTrue(isSplitScreenBible(2, listOf(out(), out(bibleTranslations = listOf(0)))))
    }

    @Test
    fun `an output with the bible switched off is an absence, not a disagreement`() {
        assertFalse(isSplitScreenBible(2, listOf(out(), out(bibleMode = Constants.SONG_LANG_OFF))))
        assertFalse(
            isSplitScreenBible(2, listOf(out(), out(targetDisplay = Constants.KEY_TARGET_NONE)))
        )
    }

    @Test
    fun `a single screen is never a split, however it is configured`() {
        assertFalse(isSplitScreenBible(3, listOf(out(bibleTranslations = listOf(0)))))
        assertFalse(isSplitScreenSong(listOf(out(songMode = Constants.SONG_LANG_PRIMARY))))
    }

    @Test
    fun `songs split when one screen shows the primary and another the secondary`() {
        assertTrue(
            isSplitScreenSong(
                listOf(out(songMode = Constants.SONG_LANG_PRIMARY), out(songMode = Constants.SONG_LANG_SECONDARY))
            )
        )
        assertFalse(
            isSplitScreenSong(
                listOf(out(songMode = Constants.SONG_LANG_BOTH), out(songMode = Constants.SONG_LANG_BOTH))
            )
        )
    }

    @Test
    fun `a screen with songs switched off does not make a split`() {
        assertFalse(
            isSplitScreenSong(
                listOf(out(songMode = Constants.SONG_LANG_BOTH), out(songMode = Constants.SONG_LANG_OFF))
            )
        )
    }

    // ── Chord charts ────────────────────────────────────────────────────────────

    // Chords are inline bracketed markers, not a separate chord line — see ChordTransposer.
    private val withChords = listOf("[G]Amazing [C]grace how [D]sweet the sound")

    @Test
    fun `a chord-carrying song on a stage monitor with chords on is a chord chart`() {
        assertTrue(
            isChordChartPresentation(
                song(lyrics = withChords),
                showChords = true,
                outputs = listOf(out(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)),
            )
        )
    }

    @Test
    fun `chords switched off means no chord chart, however the song is written`() {
        assertFalse(
            isChordChartPresentation(
                song(lyrics = withChords),
                showChords = false,
                outputs = listOf(out(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)),
            )
        )
    }

    @Test
    fun `a song with no chords is not a chord chart`() {
        assertFalse(
            isChordChartPresentation(
                song(),
                showChords = true,
                outputs = listOf(out(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)),
            )
        )
    }

    @Test
    fun `without a stage monitor there is nowhere for a chord chart to appear`() {
        assertFalse(
            isChordChartPresentation(
                song(lyrics = withChords),
                showChords = true,
                outputs = listOf(out(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
            )
        )
        assertFalse(
            isChordChartPresentation(
                song(lyrics = withChords),
                showChords = true,
                outputs = listOf(
                    out(
                        displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR,
                        targetDisplay = Constants.KEY_TARGET_NONE,
                    )
                ),
            )
        )
    }

    // ── Audience output ─────────────────────────────────────────────────────────

    @Test
    fun `a second display is what makes an output an audience output`() {
        assertTrue(hasAudienceOutput(listOf(out()), screenCount = 2, deckLinkDeviceCount = 0))
        assertFalse(
            hasAudienceOutput(listOf(out()), screenCount = 1, deckLinkDeviceCount = 0),
            "one screen is the operator's own — nothing the congregation can see",
        )
    }

    @Test
    fun `a decklink output counts when a device is fitted, and not when none is`() {
        val decklink = ScreenAssignment(targetDisplay = 0, targetType = Constants.TARGET_TYPE_DECKLINK)
        assertTrue(hasAudienceOutput(listOf(decklink), screenCount = 1, deckLinkDeviceCount = 1))
        assertFalse(hasAudienceOutput(listOf(decklink), screenCount = 1, deckLinkDeviceCount = 0))
    }

    @Test
    fun `an output switched off is not an audience output, however much hardware is attached`() {
        assertFalse(
            hasAudienceOutput(
                listOf(out(targetDisplay = Constants.KEY_TARGET_NONE)),
                screenCount = 3,
                deckLinkDeviceCount = 2,
            )
        )
        assertFalse(hasAudienceOutput(emptyList(), screenCount = 3, deckLinkDeviceCount = 2))
    }
}
