package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which of a song's languages an output draws, from its mode and its explicit picks. */
class SongLanguageSelectionTest {

    private fun pick(mode: String, picks: List<Int> = emptyList(), available: Int = 4) =
        songLanguageSelection(mode, picks, available)

    @Test
    fun `an output that is off, or a song with no languages, draws nothing`() {
        assertEquals(emptyList(), pick(Constants.SONG_LANG_OFF))
        assertEquals(emptyList(), pick(Constants.SONG_LANG_BOTH, available = 0))
    }

    @Test
    fun `each single-language mode names its own position`() {
        assertEquals(listOf(0), pick(Constants.SONG_LANG_PRIMARY))
        assertEquals(listOf(1), pick(Constants.SONG_LANG_SECONDARY))
        assertEquals(listOf(2), pick(Constants.SONG_LANG_THIRD))
        assertEquals(listOf(3), pick(Constants.SONG_LANG_FOURTH))
    }

    @Test
    fun `both means every language the song has`() {
        assertEquals(listOf(0, 1, 2, 3), pick(Constants.SONG_LANG_BOTH))
        assertEquals(listOf(0, 1), pick(Constants.SONG_LANG_BOTH, available = 2))
    }

    @Test
    fun `explicit picks win over the mode`() {
        assertEquals(listOf(0, 2), pick(Constants.SONG_LANG_SECONDARY, picks = listOf(0, 2)))
    }

    @Test
    fun `positions the song does not have are dropped and duplicates collapse, in the order picked`() {
        assertEquals(listOf(1, 0), pick(Constants.SONG_LANG_BOTH, picks = listOf(1, 0, 1, 3), available = 2))
    }

    @Test
    fun `a language the song was never translated into falls back to the primary`() {
        assertEquals(listOf(0), pick(Constants.SONG_LANG_THIRD, available = 1))
        assertEquals(listOf(0), pick(Constants.SONG_LANG_FOURTH, available = 2))
    }

    @Test
    fun `a profile resolves through the same rule`() {
        val profile = OutputProfile(songMode = Constants.SONG_LANG_THIRD)
        assertEquals(listOf(2), profile.songLanguages(available = 4))
        assertEquals(
            listOf(1, 3),
            profile.copy(songTranslations = listOf(1, 3)).songLanguages(available = 4),
        )
    }
}
