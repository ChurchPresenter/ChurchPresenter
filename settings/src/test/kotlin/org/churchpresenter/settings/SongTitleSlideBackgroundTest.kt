package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the title slide is drawn over (#1016), one choice for each kind of output. */
class SongTitleSlideBackgroundTest {

    @Test
    fun `the song's background until a profile says otherwise`() {
        val song = SongSettings()

        assertEquals(Constants.TITLE_SLIDE_BACKGROUND_SONG, song.titleSlideBackground(lowerThird = false))
        assertEquals(Constants.TITLE_SLIDE_BACKGROUND_SONG, song.titleSlideBackground(lowerThird = true))
        assertFalse(song.titleSlideHidesBackground(lowerThird = false))
        assertFalse(song.titleSlideHidesBackground(lowerThird = true))
    }

    @Test
    fun `the full screen and the lower third each keep their own`() {
        val song = SongSettings().withTitleSlideBackground(lowerThird = false, Constants.TITLE_SLIDE_BACKGROUND_NONE)

        assertTrue(song.titleSlideHidesBackground(lowerThird = false))
        assertFalse(song.titleSlideHidesBackground(lowerThird = true), "the lower third is untouched")

        val both = song.withTitleSlideBackground(lowerThird = true, Constants.TITLE_SLIDE_BACKGROUND_NONE)
            .withTitleSlideBackground(lowerThird = false, Constants.TITLE_SLIDE_BACKGROUND_SONG)
        assertFalse(both.titleSlideHidesBackground(lowerThird = false))
        assertTrue(both.titleSlideHidesBackground(lowerThird = true))
    }

    @Test
    fun `they are saved and read back with the song settings`() {
        val song = SongSettings().withTitleSlideBackground(lowerThird = true, Constants.TITLE_SLIDE_BACKGROUND_NONE)
        val json = settingsJson.encodeToString(SongSettings.serializer(), song)

        assertEquals(song, settingsJson.decodeFromString(SongSettings.serializer(), json))
    }

    @Test
    fun `settings saved before the choice existed read back as the song's background`() {
        val old = settingsJson.decodeFromString(SongSettings.serializer(), """{"layoutExtras":{}}""")

        assertEquals(Constants.TITLE_SLIDE_BACKGROUND_SONG, old.titleSlideBackground(lowerThird = false))
        assertEquals(Constants.TITLE_SLIDE_BACKGROUND_SONG, old.titleSlideBackground(lowerThird = true))
    }
}
