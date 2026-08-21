package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `isSongLineMode` decides whether arrow-key line navigation, the nav hint and per-line highlighting
 * are active. It was three identical inline OR-chains in SongsTab; the rule is "line mode if ANY of
 * the four output surfaces is in line mode", so each surface is checked independently here.
 */
class SongDisplayModeTest {

    private val verse = Constants.SONG_DISPLAY_MODE_VERSE
    private val line = Constants.SONG_DISPLAY_MODE_LINE

    private fun settings(
        fullscreen: String = verse,
        lowerThird: String = verse,
        lookAhead: String = verse,
        lowerThirdLookAhead: String = verse,
    ) = SongSettings(
        fullscreenDisplayMode = fullscreen,
        lowerThirdDisplayMode = lowerThird,
        lookAheadDisplayMode = lookAhead,
        lowerThirdLookAheadDisplayMode = lowerThirdLookAhead,
    )

    @Test
    fun `all surfaces in verse mode is not line mode`() =
        assertFalse(isSongLineMode(settings()))

    @Test
    fun `the fullscreen surface alone in line mode is line mode`() =
        assertTrue(isSongLineMode(settings(fullscreen = line)))

    @Test
    fun `the lower-third surface alone in line mode is line mode`() =
        assertTrue(isSongLineMode(settings(lowerThird = line)))

    @Test
    fun `the look-ahead surface alone in line mode is line mode`() =
        assertTrue(isSongLineMode(settings(lookAhead = line)))

    @Test
    fun `the lower-third look-ahead surface alone in line mode is line mode`() =
        assertTrue(isSongLineMode(settings(lowerThirdLookAhead = line)))
}
