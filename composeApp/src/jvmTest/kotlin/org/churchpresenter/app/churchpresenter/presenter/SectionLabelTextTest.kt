package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the section label says, which is where #613's brackets came from.
 *
 * They were never drawn by the presenter: `LyricSection.header` is the header line exactly as the
 * song file wrote it -- `[Verse 1]`, `{Chorus}` -- and the label drew that string as it stood. The
 * settings preview had always shown a bracket-free "Verse 1" because it sets `labelName` instead,
 * which the presenter never read, so the two disagreed and the preview was the one telling the truth.
 */
class SectionLabelTextTest {

    private val on = SongSectionLabel(enabled = true)

    private fun section(header: String?, type: String = "verse") =
        LyricSection(header = header, type = type)

    @Test
    fun `square brackets are stripped`() {
        assertEquals("Verse 1", sectionLabelText(section("[Verse 1]"), on, isTitleSlide = false))
    }

    @Test
    fun `curly brackets are stripped, which is what a chorus is written with`() {
        assertEquals("Chorus", sectionLabelText(section("{Chorus}"), on, isTitleSlide = false))
    }

    @Test
    fun `a header with no brackets is left exactly as it was`() {
        assertEquals("Bridge 2", sectionLabelText(section("Bridge 2"), on, isTitleSlide = false))
    }

    @Test
    fun `whitespace inside the brackets goes too`() {
        assertEquals("Verse 3", sectionLabelText(section("[  Verse 3  ]"), on, isTitleSlide = false))
    }

    @Test
    fun `a song with no header falls back to its humanized type`() {
        // Nothing to strip here, which is why the fallback was never the part that looked wrong.
        assertEquals("Chorus", sectionLabelText(section(header = null, type = "chorus"), on, isTitleSlide = false))
    }

    @Test
    fun `a blank header falls back the same way`() {
        assertEquals("Verse", sectionLabelText(section("   ", type = "verse"), on, isTitleSlide = false))
    }

    @Test
    fun `switched off there is nothing to draw`() {
        assertNull(sectionLabelText(section("[Verse 1]"), SongSectionLabel(enabled = false), isTitleSlide = false))
    }

    @Test
    fun `the title slide has no section to label`() {
        assertNull(sectionLabelText(section("[Verse 1]"), on, isTitleSlide = true))
    }

    @Test
    fun `a header that is nothing but brackets draws nothing rather than an empty label`() {
        assertNull(sectionLabelText(section("[]", type = ""), on, isTitleSlide = false))
    }

    @Test
    fun `a title-slide section is still labelled when asked for as a lyric slide`() {
        // `isTitleSlide` is the caller's decision, not the section's type: `SongPresenter` passes the
        // flag it already computed, and the fallback must not start reading types of its own.
        val titleType = section(header = null, type = Constants.SECTION_TYPE_TITLE_SLIDE)
        assertEquals(
            Constants.SECTION_TYPE_TITLE_SLIDE.replaceFirstChar { it.uppercase() },
            sectionLabelText(titleType, on, isTitleSlide = false),
        )
    }
}
