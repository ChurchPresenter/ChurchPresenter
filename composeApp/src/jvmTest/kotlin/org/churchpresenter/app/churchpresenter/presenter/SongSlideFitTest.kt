package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which part of a song the "each slide" auto-fit measures (#658).
 *
 * The song-wide fit measures `sectionsForFit` whole; slide by slide it must measure exactly the one
 * entry of it that stands for the slide on screen -- or, in line mode without look-ahead, the one line
 * -- and leave room for the end-of-song marker only on the song's last slide.
 */
class SongSlideFitTest {

    private val verse = LyricSection(header = "[Verse 1]", type = "verse", lines = listOf("v1 a", "v1 b"))
    private val chorus = LyricSection(
        header = "[Chorus]",
        type = "chorus",
        lines = listOf("c a", "c b"),
        translations = listOf(SectionTranslation(lines = listOf("c a 2", "c b 2"))),
    )
    private val song = listOf(verse, chorus)

    private fun fit(
        sectionsForFit: List<LyricSection> = song,
        section: LyricSection,
        displaySectionIndex: Int = song.indexOf(section),
        displayLineIndex: Int = -1,
        isLineMode: Boolean = false,
        lookAheadEnabled: Boolean = false,
    ) = slideFitSections(
        sectionsForFit = sectionsForFit,
        allLyricSections = song,
        slide = SlidePosition(section, displaySectionIndex, displayLineIndex),
        isLineMode = isLineMode,
        lookAheadEnabled = lookAheadEnabled,
    )

    @Test
    fun `verse mode measures only the section on screen`() {
        val first = fit(section = verse)
        assertEquals(listOf(verse), first.sections)
        assertFalse(first.isLast, "the first of two sections carries no end marker")

        val last = fit(section = chorus)
        assertEquals(listOf(chorus), last.sections)
        assertTrue(last.isLast, "the song's last section leaves room for the end marker")
    }

    @Test
    fun `verse mode with look-ahead measures the section together with the next`() {
        val joined = verse.copy(lines = verse.lines + chorus.lines)
        val result = fit(sectionsForFit = listOf(joined, chorus), section = verse, lookAheadEnabled = true)
        assertEquals(listOf(joined), result.sections)
    }

    @Test
    fun `line mode with look-ahead picks the line pair by its place in the whole song`() {
        // One entry per line of the song, each paired with the line after it.
        val pairs = listOf("v1 a", "v1 b", "c a", "c b").mapIndexed { i, line ->
            LyricSection(lines = listOf(line, "next $i"))
        }
        val result = fit(
            sectionsForFit = pairs,
            section = chorus,
            displayLineIndex = 1,
            isLineMode = true,
            lookAheadEnabled = true,
        )
        assertEquals(listOf(pairs[3]), result.sections, "the chorus's second line is the song's fourth")
        assertTrue(result.isLast)
    }

    @Test
    fun `line mode without look-ahead measures just the line on screen, in every language`() {
        val result = fit(section = chorus, displayLineIndex = 0, isLineMode = true)
        val only = result.sections.single()
        assertEquals(listOf("c a"), only.lines)
        assertEquals(listOf("c a 2"), only.translations.single().lines)
        assertFalse(result.isLast, "not the last line of the song")

        assertTrue(fit(section = chorus, displayLineIndex = 1, isLineMode = true).isLast)
    }

    @Test
    fun `a stale index still finds the section by what it is`() {
        val result = fit(section = chorus, displaySectionIndex = 0)
        assertEquals(listOf(chorus), result.sections)
    }

    @Test
    fun `a section the song does not list is measured on its own`() {
        val loose = LyricSection(header = "[Tag]", type = "tag", lines = listOf("loose"))
        val result = fit(section = loose, displaySectionIndex = -1)
        assertEquals(listOf(loose), result.sections)
        assertFalse(result.isLast)
    }
}
