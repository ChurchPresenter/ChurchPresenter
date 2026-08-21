package org.churchpresenter.converter.song

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The section splitting and naming every format without a slide list goes through.
 *
 * The whole risk here is one-sided: mistaking a sung line for a heading *deletes* that line from
 * the song, while mistaking a heading for a lyric only leaves a stray word on a slide. So the
 * negative cases — a verse opening with the word "Verse", a line ending in a colon, a long line
 * that starts like a label — are the ones that matter.
 */
class LyricBlocksTest {

    // ── headingOf: what may be removed from the lyrics ────────────────────────

    @Test
    fun `a bracketed line is a heading whatever it says`() {
        assertEquals("Chorus", LyricBlocks.headingOf("[Chorus]"))
        assertEquals("Chorus", LyricBlocks.headingOf("{Chorus}"))
        assertEquals("Antiphon of the day", LyricBlocks.headingOf("[Antiphon of the day]"))
    }

    @Test
    fun `a bare name with no lyric beside it is a heading`() {
        assertEquals("Verse 2", LyricBlocks.headingOf("Verse 2"))
        assertEquals("PRE-CHORUS", LyricBlocks.headingOf("PRE-CHORUS"))
        assertEquals("Припев", LyricBlocks.headingOf("Припев"))
    }

    @Test
    fun `a hand-written heading ending in a colon is one`() {
        assertEquals("Soloist", LyricBlocks.headingOf("Soloist:"))
        assertEquals("Men only", LyricBlocks.headingOf("Men only:"))
    }

    @Test
    fun `a sung line ending in a colon is not a heading`() {
        assertNull(LyricBlocks.headingOf("This is my Father's world:"))
        assertNull(LyricBlocks.headingOf("And he said, come:"))
    }

    @Test
    fun `a long line ending in a colon is a lyric, not a name`() {
        assertNull(LyricBlocks.headingOf("Everyone who is thirsty come to the water and drink:"))
    }

    @Test
    fun `too many words to be a name is not a heading`() {
        assertNull(LyricBlocks.headingOf("all of you sing this:"))
    }

    @Test
    fun `a line that is only brackets or blank is no heading at all`() {
        assertNull(LyricBlocks.headingOf(""))
        assertNull(LyricBlocks.headingOf("   "))
        assertNull(LyricBlocks.headingOf("[]"))
        assertNull(LyricBlocks.headingOf("[ ]"))
    }

    @Test
    fun `a sung line that merely opens with a section word keeps its place`() {
        assertNull(LyricBlocks.headingOf("Verse of the Lord be with you"))
        assertNull(LyricBlocks.headingOf("End of the day"))
    }

    @Test
    fun `an unclosed bracket is read as the lyric it is`() {
        assertNull(LyricBlocks.headingOf("[not closed"))
        assertNull(LyricBlocks.headingOf("not opened]"))
    }

    // ── isLabel: what may name a section ──────────────────────────────────────

    @Test
    fun `a bracketed line names a section however long it is`() {
        assertTrue(LyricBlocks.isLabel("[The very long name of a section nobody would abbreviate]"))
        assertTrue(LyricBlocks.isLabel("{Chorus}"))
    }

    @Test
    fun `a short line opening with a section word names one`() {
        assertTrue(LyricBlocks.isLabel("Verse 1"))
        assertTrue(LyricBlocks.isLabel("VERSE1"))
        assertTrue(LyricBlocks.isLabel("Куплет 2"))
    }

    @Test
    fun `a line too long to be a name is a lyric`() {
        assertFalse(LyricBlocks.isLabel("Verse of the Lord be with you always"))
    }

    @Test
    fun `an empty line names nothing`() {
        assertFalse(LyricBlocks.isLabel(""))
        assertFalse(LyricBlocks.isLabel("   "))
        assertFalse(LyricBlocks.isLabel("[]"))
    }

    @Test
    fun `a line that is not a section word is a lyric`() {
        assertFalse(LyricBlocks.isLabel("Amazing grace"))
    }

    // ── labels: naming the sections a format left unnamed ─────────────────────

    @Test
    fun `a song that names nothing has its sections numbered as verses`() {
        assertEquals(listOf("Verse 1", "Verse 2", "Verse 3"), LyricBlocks.labels(listOf(null, null, null)))
    }

    @Test
    fun `an unnamed slide after a named one continues that section`() {
        // A verse split over two slides must not read as two different verses.
        assertEquals(
            listOf("Verse 1", "Verse 1", "Chorus", "Chorus"),
            LyricBlocks.labels(listOf("v1", null, "c", null)),
        )
    }

    @Test
    fun `numbering skips the verse numbers the song already used`() {
        // Nothing precedes the first section, so it is numbered rather than continued -- and it
        // must not take the number the song gives its own verse further down.
        assertEquals(listOf("Verse 2", "Verse 1"), LyricBlocks.labels(listOf(null, "v1")))
    }

    @Test
    fun `a blank name is treated as no name at all`() {
        assertEquals(listOf("Verse 1", "Verse 2"), LyricBlocks.labels(listOf("", "   ")))
    }

    @Test
    fun `a lone chorus loses the number the source gave it`() {
        assertEquals(listOf("Verse 1", "Chorus"), LyricBlocks.labels(listOf("v1", "c1")))
    }

    @Test
    fun `two choruses keep their numbers`() {
        assertEquals(listOf("Chorus 1", "Chorus 2"), LyricBlocks.labels(listOf("c1", "c2")))
    }

    @Test
    fun `a single verse keeps its number, unlike every other section`() {
        assertEquals(listOf("Verse 1"), LyricBlocks.labels(listOf("v1")))
    }

    // ── split: one run of text into sections ──────────────────────────────────

    @Test
    fun `blank lines separate sections and a leading name becomes the label`() {
        val sections = LyricBlocks.split(
            """
            Verse 1
            Amazing grace how sweet the sound

            Chorus
            Praise the Lord
            """.trimIndent()
        )
        assertEquals(listOf("Verse 1", "Chorus"), sections.map { it.label })
        assertEquals(listOf("Amazing grace how sweet the sound"), sections.first().lines)
    }

    @Test
    fun `text with no names at all is numbered`() {
        val sections = LyricBlocks.split("Amazing grace\nhow sweet\n\nTwas grace\nthat taught")
        assertEquals(listOf("Verse 1", "Verse 2"), sections.map { it.label })
    }

    @Test
    fun `a name with nothing under it labels the block that follows`() {
        val sections = LyricBlocks.split("[Chorus]\n\nPraise the Lord\n\n")
        assertEquals(listOf("Verse 1"), sections.map { it.label })
        assertEquals(listOf("Praise the Lord"), sections.single().lines)
    }

    @Test
    fun `runs of blank lines do not produce empty sections`() {
        val sections = LyricBlocks.split("Amazing grace\n\n\n\n   \n\nTwas grace")
        assertEquals(2, sections.size)
    }

    @Test
    fun `text that is nothing but whitespace yields no sections`() {
        assertTrue(LyricBlocks.split("   \n\n  ").isEmpty())
    }

    // ── SectionLabel, the mapping underneath ──────────────────────────────────

    @Test
    fun `a marker that is only a number is a verse`() {
        assertEquals("Verse 2", SectionLabel.of("[2]"))
        assertEquals("Verse", SectionLabel.of(""))
        assertEquals("Verse", SectionLabel.of("[]"))
    }

    @Test
    fun `a name the mapping does not know is passed through`() {
        assertEquals("Antiphon", SectionLabel.of("Antiphon"))
        assertEquals("Antiphon 2", SectionLabel.of("Antiphon 2"))
    }

    @Test
    fun `both spellings of a section name reach the same label`() {
        assertEquals("Pre-Chorus", SectionLabel.of("p"))
        assertEquals("Pre-Chorus", SectionLabel.of("PreChorus"))
        assertEquals("Pre-Chorus 2", SectionLabel.of("pre chorus 2"))
        assertEquals("Ending", SectionLabel.of("END"))
    }
}
