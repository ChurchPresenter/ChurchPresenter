package org.churchpresenter.songchords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordSheetImporterTest {

    // ── Recognising a chord line ────────────────────────────────────────────────

    @Test
    fun `a row of chords is a chord line`() {
        assertTrue(ChordSheetImporter.isChordLine("Cm Bb Ab G"))
        assertTrue(ChordSheetImporter.isChordLine("      G       D/F#   Em"))
    }

    @Test
    fun `a line of words is not a chord line`() {
        assertFalse(ChordSheetImporter.isChordLine("Amazing grace how sweet the sound"))
        assertFalse(ChordSheetImporter.isChordLine(""))
    }

    @Test
    fun `a lone bare letter needs indenting to count as a chord`() {
        // "A" on its own is as likely to be a word; a positioned chord is always indented.
        assertFalse(ChordSheetImporter.isChordLine("A"))
        assertTrue(ChordSheetImporter.isChordLine("    A"))
        assertTrue(ChordSheetImporter.isChordLine("Am"))
    }

    // ── Merging a chord line into the words under it ────────────────────────────

    @Test
    fun `each chord lands on the column it was written over`() {
        val merged = ChordSheetImporter.merge(
            chordLine = "    G       C",
            lyricLine = "Amazing grace how",
        )
        assertEquals("Amaz[G]ing grac[C]e how", merged)
    }

    @Test
    fun `a chord written past the end of the words lands at the end`() {
        val merged = ChordSheetImporter.merge(
            chordLine = "                    Gsus G",
            lyricLine = "short line",
        )
        assertEquals("short line[Gsus][G]", merged)
    }

    @Test
    fun `a chord over the first column opens the line`() {
        assertEquals("[G]Amazing", ChordSheetImporter.merge("G", "Amazing"))
    }

    // ── Section headings ────────────────────────────────────────────────────────

    @Test
    fun `english headings map to the app's markers`() {
        assertEquals("[Verse 1]", ChordSheetImporter.sectionMarkerOf("Verse 1") { 1 })
        assertEquals("{Chorus}", ChordSheetImporter.sectionMarkerOf("Chorus:") { 1 })
        assertEquals("[Bridge]", ChordSheetImporter.sectionMarkerOf("Bridge") { 1 })
        assertEquals("[Intro]", ChordSheetImporter.sectionMarkerOf("Intro:") { 1 })
    }

    @Test
    fun `russian and ukrainian headings map to the same markers`() {
        assertEquals("[Intro]", ChordSheetImporter.sectionMarkerOf("Интро:") { 1 })
        assertEquals("[Verse 1]", ChordSheetImporter.sectionMarkerOf("1 куплет:") { 9 })
        assertEquals("{Chorus}", ChordSheetImporter.sectionMarkerOf("Припев:") { 1 })
        assertEquals("{Chorus}", ChordSheetImporter.sectionMarkerOf("Приспів:") { 1 })
        assertEquals("[Bridge]", ChordSheetImporter.sectionMarkerOf("Бридж:") { 1 })
    }

    @Test
    fun `a verse with no number takes the next one in sequence`() {
        assertEquals("[Verse 4]", ChordSheetImporter.sectionMarkerOf("куплет") { 4 })
    }

    @Test
    fun `pre-chorus is not mistaken for a chorus`() {
        assertEquals("[Pre-Chorus]", ChordSheetImporter.sectionMarkerOf("Pre-Chorus:") { 1 })
    }

    @Test
    fun `a marker already in the app's own form is kept`() {
        assertEquals("{Chorus}", ChordSheetImporter.sectionMarkerOf("{Chorus}") { 1 })
    }

    @Test
    fun `a line of words is not a heading`() {
        assertNull(ChordSheetImporter.sectionMarkerOf("Amazing grace how sweet the sound") { 1 })
        assertNull(ChordSheetImporter.sectionMarkerOf("") { 1 })
    }

    @Test
    fun `a long line naming a section is still not a heading`() {
        assertNull(
            ChordSheetImporter.sectionMarkerOf("and then the chorus rang out across the whole room") { 1 },
        )
    }

    // ── Converting a whole sheet ────────────────────────────────────────────────

    @Test
    fun `a sheet is recognised by having a positioned chord line`() {
        assertTrue(ChordSheetImporter.looksLikeChordSheet("   G   C\nsome words here"))
        assertFalse(ChordSheetImporter.looksLikeChordSheet("just some words\nand some more"))
    }

    @Test
    fun `a chord line with no words under it becomes bare markers`() {
        val out = ChordSheetImporter.convert("Intro:\nCm Bb Ab G")
        assertEquals("[Intro]\n[Cm] [Bb] [Ab] [G]", out)
    }

    @Test
    fun `headings, chords and words come out in the app's own format`() {
        val sheet = """
            Verse 1:
            G     C
            first line here
            second line here
        """.trimIndent()

        assertEquals(
            listOf("[Verse 1]", "[G]first [C]line here", "second line here"),
            ChordSheetImporter.convert(sheet).lines(),
        )
    }

    @Test
    fun `a source url is dropped`() {
        val out = ChordSheetImporter.convert("Verse 1:\nsome words\nhttps://example.com/12345")
        assertFalse(out.contains("http"), out)
    }

    @Test
    fun `a section with no chord line of its own is left as plain words`() {
        // Sheets expect the player to reuse the first chorus's chords; guessing them could be wrong.
        val out = ChordSheetImporter.convert("Chorus:\nno chords on this one")
        assertEquals(listOf("{Chorus}", "no chords on this one"), out.lines())
    }

    @Test
    fun `text that is not a sheet survives conversion unchanged`() {
        val plain = "[Verse 1]\nAmazing grace how sweet the sound"
        assertEquals(plain, ChordSheetImporter.convert(plain))
    }

    @Test
    fun `a blank line is neither a chord line nor a heading`() {
        assertFalse(ChordSheetImporter.isChordLine(""))
        assertFalse(ChordSheetImporter.isChordLine("    "))
        assertNull(ChordSheetImporter.sectionMarkerOf("   ") { 1 })
    }

    @Test
    fun `a chord line at the very end of the sheet has no words to merge with`() {
        val converted = ChordSheetImporter.convert(
            """
            Verse 1
            G       C
            Amazing grace
                D   G
            """.trimIndent()
        )

        // The trailing chord row has nothing under it, so it becomes bare markers rather than
        // reaching past the end of the sheet for words.
        assertTrue(converted.trimEnd().endsWith("[D] [G]"), converted)
    }

    @Test
    fun `a chord line followed by a blank line is not merged into it`() {
        val converted = ChordSheetImporter.convert(
            """
            G       C

            Amazing grace
            """.trimIndent()
        )

        assertTrue("[G] [C]" in converted, converted)
        assertTrue("Amazing grace" in converted, converted)
        assertFalse("[G]Amazing" in converted, "a blank line separates them: $converted")
    }

    @Test
    fun `a chord line immediately before a heading is not merged into the heading`() {
        val converted = ChordSheetImporter.convert(
            """
            G       C
            Chorus
            Amazing grace
            """.trimIndent()
        )

        assertTrue("[G] [C]" in converted, converted)
        assertTrue("{Chorus}" in converted, converted)
    }

    @Test
    fun `a heading at the very start does not open the song with a blank line`() {
        val converted = ChordSheetImporter.convert(
            """
            Verse 1
            Amazing grace
            """.trimIndent()
        )

        assertTrue(converted.startsWith("[Verse 1]"), "leading blank line: ${converted.take(20)}")
    }

    @Test
    fun `runs of blank lines between sections collapse to one`() {
        val converted = ChordSheetImporter.convert(
            """
            Verse 1
            Amazing grace



            Chorus
            How sweet the sound
            """.trimIndent()
        )

        assertFalse("\n\n\n" in converted, "blank runs were not collapsed: ${converted.replace("\n", "|")}")
        assertTrue("Amazing grace\n\n{Chorus}" in converted, converted.replace("\n", "|"))
    }
}
