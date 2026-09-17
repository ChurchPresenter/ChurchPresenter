package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which of a bilingual song's two titles a title slide draws, and which of them is marked as the
 * second language -- the flag that sends it to its own profile rather than the first title's.
 */
class SongTitleSlideSecondaryTitleTest {

    private val settings = SongSettings(titleSlideEnabled = true)

    private fun section(secondary: String = "О, благодать") = LyricSection(
        title = "Amazing Grace",
        secondaryTitle = secondary,
        songNumber = 0,
        lines = emptyList(),
        type = Constants.SECTION_TYPE_TITLE_SLIDE,
    )

    private fun titles(langDisplay: String, section: LyricSection = section()) =
        titleSlideLines(section, settings, langDisplay)
            .filter { it.element == SongStyleElement.TITLE }

    @Test
    fun `both languages draw both titles, the second marked as such`() {
        val lines = titles(Constants.SONG_LANG_BOTH)
        assertEquals(listOf("Amazing Grace", "О, благодать"), lines.map { it.text })
        assertFalse(lines[0].secondaryLanguage, "the first title is the first language's")
        assertTrue(lines[1].secondaryLanguage, "the second title takes the second title's profile")
    }

    @Test
    fun `the secondary language draws the second title, marked`() {
        val lines = titles(Constants.SONG_LANG_SECONDARY)
        assertEquals(listOf("О, благодать"), lines.map { it.text })
        assertTrue(lines[0].secondaryLanguage)
    }

    @Test
    fun `the primary language draws the first title, unmarked`() {
        val lines = titles(Constants.SONG_LANG_PRIMARY)
        assertEquals(listOf("Amazing Grace"), lines.map { it.text })
        assertFalse(lines[0].secondaryLanguage)
    }

    /**
     * A song with no second title shows the first under any language -- and it *is* the first title,
     * so it keeps the first title's look rather than being drawn in a profile meant for the other.
     */
    @Test
    fun `a song with one title is never marked as the second language`() {
        val lines = titles(Constants.SONG_LANG_SECONDARY, section(secondary = ""))
        assertEquals(listOf("Amazing Grace"), lines.map { it.text })
        assertFalse(lines[0].secondaryLanguage)
    }

    /** A second title identical to the first is not a second title. */
    @Test
    fun `a repeated title is drawn once`() {
        val lines = titles(Constants.SONG_LANG_BOTH, section(secondary = "Amazing Grace"))
        assertEquals(listOf("Amazing Grace"), lines.map { it.text })
        assertFalse(lines[0].secondaryLanguage)
    }
}
