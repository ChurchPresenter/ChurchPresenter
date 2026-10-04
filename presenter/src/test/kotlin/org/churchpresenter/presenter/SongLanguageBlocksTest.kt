package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import kotlin.test.Test
import kotlin.test.assertEquals

class SongLanguageBlocksTest {

    private fun modes(lookAhead: Boolean = false, line: Boolean = false, laLine: Boolean = false, index: Int = -1) =
        SongSlideModes(lookAheadEnabled = lookAhead, isLineMode = line, laIsLineMode = laLine, lineIndex = index)

    private val lines = listOf("one", "two", "three")
    private val next = listOf("four", "five")

    @Test
    fun `with no look-ahead there is nothing after the slide, even in line mode`() {
        assertEquals(emptyList(), lookAheadLinesFor(lines, emptyList(), modes(line = true, laLine = true, index = 0)))
    }

    @Test
    fun `in line mode the next line of this section comes before the next section`() {
        val both = modes(lookAhead = true, line = true, laLine = true, index = 0)
        assertEquals(listOf("two"), lookAheadLinesFor(lines, next, both))
        assertEquals(listOf("four"), lookAheadLinesFor(lines, next, both.copy(lineIndex = 2)))
        assertEquals(listOf("four"), lookAheadLinesFor(lines, next, both.copy(lineIndex = -1)))
    }

    @Test
    fun `a verse-mode look-ahead previews the whole next section`() {
        assertEquals(next, lookAheadLinesFor(lines, next, modes(lookAhead = true)))
        assertEquals(listOf("four"), lookAheadLinesFor(lines, next, modes(lookAhead = true, laLine = true)))
    }

    @Test
    fun `languages the song lacks are dropped, and with none left the primary is drawn`() {
        val section = LyricSection(lines = lines, translations = listOf(SectionTranslation(lines = emptyList())))
        val nextSection = LyricSection(lines = next)

        val blocks = songLanguageBlocks(section, nextSection, listOf(1, 3), modes(lookAhead = true))

        assertEquals(listOf(0), blocks.map { it.index })
        assertEquals(lines, blocks.single().lines)
        assertEquals(next, blocks.single().lookAheadLines)
    }

    @Test
    fun `each language gets its own line and its own look-ahead`() {
        val section =
            LyricSection(lines = lines, translations = listOf(SectionTranslation(lines = listOf("uno", "dos"))))
        val lineModes = modes(lookAhead = true, line = true, laLine = true, index = 0)
        val blocks = songLanguageBlocks(section, null, listOf(0, 1), lineModes)

        assertEquals(listOf(listOf("one"), listOf("uno")), blocks.map { it.lines })
        assertEquals(listOf(listOf("two"), listOf("dos")), blocks.map { it.lookAheadLines })
    }
}
