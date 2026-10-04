package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.ElementOffset
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTranslationElement
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongStyleAccessTest {

    @Test
    fun `each slide keeps its own chunk on each output`() {
        val cases = listOf(
            SongStyleElement.LYRICS to SongStyleTarget.FULL_SCREEN,
            SongStyleElement.LYRICS to SongStyleTarget.LOWER_THIRD,
            SongStyleElement.LOOK_AHEAD to SongStyleTarget.FULL_SCREEN,
            SongStyleElement.NEXT_SECTION to SongStyleTarget.LOWER_THIRD,
        )
        cases.forEach { (element, target) ->
            val line = SongSettings().withChunk(element, target, Constants.SONG_DISPLAY_MODE_LINE)
            val verse = SongSettings().withChunk(element, target, Constants.SONG_DISPLAY_MODE_VERSE)
            assertEquals(Constants.SONG_DISPLAY_MODE_LINE, line.chunkFor(element, target), "$element $target")
            assertEquals(Constants.SONG_DISPLAY_MODE_VERSE, verse.chunkFor(element, target), "$element $target")
        }
    }

    @Test
    fun `each title-slide element keeps its own offset per output`() {
        val elements = listOf(
            SongStyleElement.TITLE, SongStyleElement.AUTHOR, SongStyleElement.COMPOSER,
            SongStyleElement.CCLI, SongStyleElement.TEMPO,
        )
        val offset = ElementOffset(xPercent = 20, yPercent = 80)
        elements.forEach { element ->
            val song = SongSettings().withTitleSlideOffset(element, SongStyleTarget.LOWER_THIRD, offset)
            assertEquals(offset, song.titleSlideOffset(element, SongStyleTarget.LOWER_THIRD), "$element")
            assertNull(song.titleSlideOffset(element, SongStyleTarget.FULL_SCREEN), "$element full screen")
        }
    }

    @Test
    fun `an element the title slide does not position has nowhere to keep an offset`() {
        val song = SongSettings()
        val unchanged = song.withTitleSlideOffset(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN, ElementOffset())
        assertSame(song, unchanged)
        assertNull(song.titleSlideOffset(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN))
    }

    @Test
    fun `every per-language element maps back to the element it styles`() {
        SongTranslationElement.entries.forEach { element ->
            assertEquals(element, element.styleElement.translationElement)
        }
    }

    @Test
    fun `a section label reads the header without its brackets, or the section's type`() {
        val on = SongSectionLabel(enabled = true)
        assertEquals("Verse 1", sectionLabelText(LyricSection(header = "[Verse 1]"), on, isTitleSlide = false))
        assertEquals("Chorus", sectionLabelText(LyricSection(header = "{Chorus}"), on, isTitleSlide = false))
        assertEquals("Bridge", sectionLabelText(LyricSection(header = "  ", type = "bridge"), on, isTitleSlide = false))
        assertNull(sectionLabelText(LyricSection(header = "[]"), on, isTitleSlide = false))
        assertNull(sectionLabelText(LyricSection(header = "[Verse 1]"), on, isTitleSlide = true))
        assertNull(sectionLabelText(LyricSection(header = "[Verse 1]"), SongSectionLabel(), isTitleSlide = false))
    }

    @Test
    fun `room is kept for the longest label above the lyrics, and none below them or when off`() {
        val sections = listOf(LyricSection(header = "[Verse 1]"), LyricSection(header = "[Pre-Chorus]"))
        val on = SongSectionLabel(enabled = true)
        assertEquals("Pre-Chorus", sectionLabelToReserve(on, sections, isLowerThird = false))
        assertNull(sectionLabelToReserve(SongSectionLabel(), sections, isLowerThird = false))
        assertNull(
            sectionLabelToReserve(on.copy(lowerThirdPosition = Constants.BELOW_VERSE), sections, isLowerThird = true),
        )
        assertNull(sectionLabelToReserve(on, listOf(LyricSection(header = "[]")), isLowerThird = false))
    }

    @Test
    fun `two pages are the same only when header, slide, type and lines all match`() {
        val page = LyricSection(header = "[Verse 1]", slideIndex = 0, type = "verse", lines = listOf("a"))
        assertTrue(page.isSamePageAs(page.copy(title = "another title")))
        assertFalse(page.isSamePageAs(page.copy(header = "[Verse 2]")))
        assertFalse(page.isSamePageAs(page.copy(slideIndex = 1)))
        assertFalse(page.isSamePageAs(page.copy(type = "chorus")))
        assertFalse(page.isSamePageAs(page.copy(lines = listOf("b"))))
    }
}
