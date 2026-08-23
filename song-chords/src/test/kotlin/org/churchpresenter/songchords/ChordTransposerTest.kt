package org.churchpresenter.songchords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordTransposerTest {

    // ── Telling a chord from a section name ─────────────────────────────────────

    @Test
    fun `plain triads, slashes, extensions and suspensions all read as chords`() {
        listOf("G", "Em", "C#m7", "Bbmaj7", "Asus4", "D/F#", "Gsus", "Ab", "F#dim").forEach {
            assertTrue(ChordTransposer.isChord(it), "$it should read as a chord")
        }
    }

    @Test
    fun `section names never read as chords`() {
        listOf("Verse 1", "Chorus", "Bridge", "Intro", "Tag", "Pre-Chorus", "", "Hello").forEach {
            assertFalse(ChordTransposer.isChord(it), "$it should not read as a chord")
        }
    }

    @Test
    fun `a whole line in brackets is a header`() {
        assertTrue(ChordTransposer.isSectionHeader("[Verse 1]"))
        assertTrue(ChordTransposer.isSectionHeader("{Chorus}"))
        assertTrue(ChordTransposer.isSectionHeader("  [Bridge]  "))
    }

    @Test
    fun `a line that is only a chord is not a header`() {
        // An instrumental writes its one chord on a line of its own.
        assertFalse(ChordTransposer.isSectionHeader("[Am]"))
    }

    @Test
    fun `a line of nothing but chords is not a header`() {
        // Opens with [ and closes with ], so the naive test called this a section named "Cm] [Bb...".
        assertFalse(ChordTransposer.isSectionHeader("[Cm] [Bb] [Ab] [G]"))
    }

    @Test
    fun `a line with words around a bracket is not a header`() {
        assertFalse(ChordTransposer.isSectionHeader("[G]Amazing grace"))
    }

    // ── Moving chords ───────────────────────────────────────────────────────────

    @Test
    fun `transposing up moves the root`() {
        assertEquals("A", ChordTransposer.transposeChord("G", 2))
        assertEquals("Gm", ChordTransposer.transposeChord("Em", 3))
    }

    @Test
    fun `both halves of a slash chord move`() {
        assertEquals("A/C#", ChordTransposer.transposeChord("G/B", 2))
    }

    @Test
    fun `the quality and extension are carried through untouched`() {
        assertEquals("C#m7", ChordTransposer.transposeChord("Bbm7", 3))
        assertEquals("Dsus4", ChordTransposer.transposeChord("Csus4", 2))
    }

    @Test
    fun `transposing wraps around the octave`() {
        assertEquals("C", ChordTransposer.transposeChord("B", 1))
        assertEquals("B", ChordTransposer.transposeChord("C", -1))
    }

    @Test
    fun `no movement leaves the chord exactly as written`() {
        assertEquals("Bb", ChordTransposer.transposeChord("Bb", 0))
    }

    @Test
    fun `something that is not a chord is returned untouched`() {
        assertEquals("Verse 1", ChordTransposer.transposeChord("Verse 1", 5))
    }

    // ── Spelling ────────────────────────────────────────────────────────────────

    @Test
    fun `a flat key spells its chords with flats`() {
        // Same pitch either way; only one of them is how a musician reads it.
        assertEquals("Ab", ChordTransposer.transposeChord("G", 1, flats = true))
        assertEquals("G#", ChordTransposer.transposeChord("G", 1, flats = false))
    }

    @Test
    fun `the keys written with flats are the ones asked for`() {
        listOf("F", "Bb", "Eb", "Ab", "Db", "Gb").forEach {
            assertTrue(ChordTransposer.prefersFlats(it), "$it is a flat key")
        }
        listOf("C", "G", "D", "A", "E", "B").forEach {
            assertFalse(ChordTransposer.prefersFlats(it), "$it is not a flat key")
        }
    }

    @Test
    fun `an enharmonic spelling still resolves to its pitch`() {
        assertEquals(ChordTransposer.pitchOf("C#"), ChordTransposer.pitchOf("Db"))
        assertNull(ChordTransposer.pitchOf("H"))
    }

    // ── Stripping ───────────────────────────────────────────────────────────────

    @Test
    fun `stripping removes chords and leaves the words`() {
        assertEquals(
            "Amazing grace how sweet the sound",
            ChordTransposer.stripChords("[G]Amazing grace how [G/B]sweet the [C]sound"),
        )
    }

    @Test
    fun `stripping leaves a section header alone`() {
        assertEquals("[Verse 1]", ChordTransposer.stripChords("[Verse 1]"))
    }

    @Test
    fun `a line of nothing but chords strips to nothing worth showing`() {
        assertTrue(ChordTransposer.stripChords("[Cm] [Bb] [Ab] [G]").isBlank())
    }

    @Test
    fun `a line without chords is left exactly as it was`() {
        val line = "Amazing grace how sweet the sound"
        assertEquals(line, ChordTransposer.stripChords(line))
        assertFalse(ChordTransposer.hasChords(line))
    }

    // ── Splitting a line ────────────────────────────────────────────────────────

    @Test
    fun `each chord takes the text up to the next one`() {
        val segments = ChordTransposer.parseLine("[G]Amazing grace how [C]sweet")
        assertEquals(
            listOf(ChordSegment("G", "Amazing grace how "), ChordSegment("C", "sweet")),
            segments,
        )
    }

    @Test
    fun `words before the first chord become a chordless run`() {
        val segments = ChordTransposer.parseLine("That [G]saved a wretch")
        assertEquals(ChordSegment("", "That "), segments.first())
        assertEquals(ChordSegment("G", "saved a wretch"), segments[1])
    }

    @Test
    fun `a chord written past the last word carries no text`() {
        val segments = ChordTransposer.parseLine("moря[Ab][G]")
        assertEquals(listOf("", "Ab", "G"), segments.map { it.chord })
        assertEquals("", segments.last().text)
    }

    @Test
    fun `a line naming no chord is one chordless run`() {
        assertEquals(
            listOf(ChordSegment("", "Amazing grace how sweet")),
            ChordTransposer.parseLine("Amazing grace how sweet"),
        )
    }

    @Test
    fun `a bracket that is not a chord stays in the words`() {
        val segments = ChordTransposer.parseLine("[G]Amazing [x2]grace [C]how sweet")
        assertEquals(listOf("G", "C"), segments.map { it.chord })
        assertEquals("Amazing [x2]grace ", segments.first().text)
    }

    @Test
    fun `splitting with chords off gives back the plain line`() {
        val segments = ChordTransposer.parseLine("[G]Amazing [C]grace", showChords = false)
        assertEquals(listOf(ChordSegment("", "Amazing grace")), segments)
    }

    @Test
    fun `splitting transposes on the way through`() {
        assertEquals(
            listOf("A", "D"),
            ChordTransposer.parseLine("[G]Amazing [C]grace", steps = 2).map { it.chord },
        )
    }

    // ── Key and palette ─────────────────────────────────────────────────────────

    @Test
    fun `the key is the first chord named`() {
        assertEquals("G", ChordTransposer.detectKey("[G]Amazing [C]grace"))
    }

    @Test
    fun `a song naming no chords falls back rather than failing`() {
        assertEquals("C", ChordTransposer.detectKey("Amazing grace"))
    }

    @Test
    fun `the palette is the seven chords of the key`() {
        assertEquals(
            listOf("G", "Am", "Bm", "C", "D", "Em", "F#dim"),
            ChordTransposer.diatonicChords("G"),
        )
    }

    @Test
    fun `a flat key's palette is spelled with flats`() {
        assertEquals(listOf("F", "Gm", "Am", "Bb", "C", "Dm", "Edim"), ChordTransposer.diatonicChords("F"))
    }

    @Test
    fun `a palette is only offered for a real key`() {
        assertEquals(emptyList(), ChordTransposer.diatonicChords("Verse"))
    }

    // ── The header helpers everything that reads a song file goes through ───────

    @Test
    fun `either kind of bracket opens a section`() {
        assertTrue(isHeaderLine("[Verse 1]"))
        assertTrue(isHeaderLine("{Chorus}"))
        assertTrue(isHeaderLine("   [Bridge]   "))
    }

    @Test
    fun `words and chord lines are not headers`() {
        assertFalse(isHeaderLine("Amazing grace how sweet the sound"))
        assertFalse(isHeaderLine("[Am]"))
        assertFalse(isHeaderLine("[Cm] [Bb] [Ab] [G]"))
        assertFalse(isHeaderLine("[Verse}"))
    }

    @Test
    fun `braces mean chorus and brackets do not`() {
        assertTrue(isChorusHeader("{Chorus}"))
        assertTrue(isChorusHeader("  {Chorus}  "))
        assertFalse(isChorusHeader("[Chorus]"))
    }

    @Test
    fun `a brace has to be closed to mean anything`() {
        assertFalse(isChorusHeader("{Chorus"))
        assertFalse(isChorusHeader("Chorus}"))
        assertFalse(isChorusHeader(""))
    }
}
