package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.songchords.ChordSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongChartTest {

    // ── Grouping a song into sections ───────────────────────────────────────────

    @Test
    fun `a header starts a section and holds everything after it`() {
        val sections = buildPreviewSections("[Verse 1]\nline one\nline two\n{Chorus}\nchorus line")

        assertEquals(listOf("Verse 1", "Chorus"), sections.map { it.label })
        assertEquals(2, sections[0].lines.size)
        assertEquals(1, sections[1].lines.size)
    }

    @Test
    fun `blank lines separate nothing on their own`() {
        // A song written without blank lines between verses still reads as verses, and one written
        // with them does not gain extra sections from them.
        val packed = buildPreviewSections("[Verse 1]\na\n[Verse 2]\nb")
        val spaced = buildPreviewSections("[Verse 1]\n\na\n\n[Verse 2]\n\nb\n")

        assertEquals(packed.map { it.label }, spaced.map { it.label })
        assertEquals(2, spaced.size)
    }

    @Test
    fun `words before any header still make a section`() {
        val sections = buildPreviewSections("just a line")
        assertEquals(1, sections.size)
        assertEquals("", sections[0].label)
    }

    @Test
    fun `a section is coloured by what its name says it is`() {
        assertEquals(SongSectionKind.VERSE, sectionKindOf("Verse 2"))
        assertEquals(SongSectionKind.CHORUS, sectionKindOf("Chorus"))
        assertEquals(SongSectionKind.CHORUS, sectionKindOf("Refrain"))
        assertEquals(SongSectionKind.BRIDGE, sectionKindOf("Bridge"))
        assertEquals(SongSectionKind.TAG, sectionKindOf("Outro"))
        assertEquals(SongSectionKind.VERSE, sectionKindOf("Something Else"))
    }

    @Test
    fun `a section written in its own language is coloured the same as the English one`() {
        assertEquals(SongSectionKind.CHORUS, sectionKindOf("Припев"))
        assertEquals(SongSectionKind.CHORUS, sectionKindOf("Приспів 2"))
        assertEquals(SongSectionKind.BRIDGE, sectionKindOf("Мост"))
        assertEquals(SongSectionKind.TAG, sectionKindOf("Кода"))
        // A verse is the fallback, so it lands right whether or not its word is listed.
        assertEquals(SongSectionKind.VERSE, sectionKindOf("Куплет 1"))
        // Pre-chorus is not a chorus in either language — both read as a verse.
        assertEquals(SongSectionKind.VERSE, sectionKindOf("Pre-Chorus"))
        assertEquals(SongSectionKind.VERSE, sectionKindOf("Предприпев"))
    }

    // ── Counting ────────────────────────────────────────────────────────────────

    @Test
    fun `the counts describe the words, not the markup`() {
        val stats = songStatsOf(buildPreviewSections("[Verse 1]\n[G]one two\nthree\n{Chorus}\nfour"))

        assertEquals(2, stats.sections)
        assertEquals(3, stats.lines)
        assertEquals(4, stats.words, "the [G] is not a word")
    }

    @Test
    fun `an empty song counts nothing`() {
        assertEquals(SongStats(0, 0, 0), songStatsOf(buildPreviewSections("")))
    }

    // ── Trailing chords ─────────────────────────────────────────────────────────

    @Test
    fun `chords past the last word are gathered into one run`() {
        val collapsed = collapseTrailingChords(
            listOf(ChordSegment("", "some words"), ChordSegment("Ab", ""), ChordSegment("G", "")),
        )
        assertEquals(listOf(ChordSegment("", "some words"), ChordSegment("Ab G", "")), collapsed)
    }

    @Test
    fun `a line ending on a word is left exactly as it was`() {
        val segments = listOf(ChordSegment("G", "some"), ChordSegment("C", "words"))
        assertEquals(segments, collapseTrailingChords(segments))
    }

    @Test
    fun `a line of nothing but chords keeps every one of them`() {
        val collapsed = collapseTrailingChords(
            listOf(ChordSegment("Cm", " "), ChordSegment("Bb", " "), ChordSegment("G", "")),
        )
        assertTrue(collapsed.last().chord.contains("G"))
    }

    // ── The song info line ──────────────────────────────────────────────────────

    private fun info(section: LyricSection) =
        songInfoOf(section, keyLabel = "Key", capoLabel = "Capo", playLabel = "Play", bpmLabel = "BPM")

    @Test
    fun `the key is read off the chart`() {
        assertEquals("Key G", info(LyricSection(chordLines = listOf("[G]word [C]word"))))
    }

    @Test
    fun `a capo also says what to actually play`() {
        // Key G with a capo at 2 means F shapes — the shapes are what the player reads.
        assertEquals(
            "Key G  ·  Capo 2  ·  Play F",
            info(LyricSection(chordLines = listOf("[G]word"), capo = 2)),
        )
    }

    @Test
    fun `no capo means no shapes to mention`() {
        val line = info(LyricSection(chordLines = listOf("[G]word"), capo = 0))
        assertEquals("Key G", line)
    }

    @Test
    fun `a tempo is reported with or without chords`() {
        assertEquals("72 BPM", info(LyricSection(bpm = 72)))
        assertEquals("Key G  ·  72 BPM", info(LyricSection(chordLines = listOf("[G]word"), bpm = 72)))
    }

    @Test
    fun `a section with nothing to report says nothing at all`() {
        assertNull(info(LyricSection(lines = listOf("just words"))))
    }
}
