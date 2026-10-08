package org.churchpresenter.songchords

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What transposing must keep, over generated chords and lyric lines rather than the hand-picked
 * cases in [ChordTransposerTest]: a chord stays a chord, its roots land back on the same notes
 * when moved there and back, nothing but a root ever changes, and the words around the chords are
 * never touched.
 *
 * The seed is pinned, so every run checks the same inputs and a failure reproduces exactly.
 */
class ChordTransposerPropertyTest {

    private val roots = listOf(
        "C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb", "G", "G#", "Ab", "A", "A#", "Bb", "B",
    )
    private val root = Regex("[A-G][#b]?")

    private val chord = Arb.bind(
        Arb.element(roots),
        Arb.element(ChordTransposer.CHORD_QUALITIES),
        Arb.element(listOf("") + roots.map { "/$it" }),
    ) { r, quality, bass -> r + quality + bass }

    private val steps = Arb.int(-24..24)

    private val words = Arb.element("Amazing", "grace", "how", "sweet", "the", "sound", "Слава", "Ісусу", "", " ", "!")
    private val token = Arb.bind(Arb.int(0..3), chord, words) { kind, c, word ->
        when (kind) {
            0 -> "[$c]$word"
            1 -> "[Verse 1]"
            2 -> "[Chorus]"
            else -> word
        }
    }
    private val line = Arb.list(token, 0..8)

    private fun pitches(chord: String) = root.findAll(chord).map { ChordTransposer.pitchOf(it.value) }.toList()

    private fun shape(chord: String) = chord.replace(root, "#")

    @Test
    fun `a transposed chord is still a chord, with only its roots changed`() = runBlocking<Unit> {
        checkAll(CONFIG, chord, steps, Arb.boolean()) { c, n, flats ->
            val moved = ChordTransposer.transposeChord(c, n, flats)
            assertTrue(ChordTransposer.isChord(moved), "$c moved $n is $moved, not a chord")
            assertEquals(shape(c), shape(moved), "$c moved $n changed more than its roots: $moved")
        }
    }

    @Test
    fun `moving there and back lands every root on the note it started on`() = runBlocking<Unit> {
        checkAll(CONFIG, chord, steps, Arb.boolean(), Arb.boolean()) { c, n, there, back ->
            val returned = ChordTransposer.transposeChord(ChordTransposer.transposeChord(c, n, there), -n, back)
            assertEquals(pitches(c), pitches(returned), "$c moved $n and back is $returned")
        }
    }

    @Test
    fun `an octave either way is the same notes`() = runBlocking<Unit> {
        checkAll(CONFIG, chord, Arb.int(-3..3), Arb.boolean()) { c, octaves, flats ->
            assertEquals(pitches(c), pitches(ChordTransposer.transposeChord(c, octaves * SEMITONES, flats)))
        }
    }

    @Test
    fun `transposing a line never touches its words or headings`() = runBlocking<Unit> {
        checkAll(CONFIG, line, steps, Arb.boolean()) { tokens, n, flats ->
            val text = tokens.joinToString(" ")
            val moved = ChordTransposer.transposeText(text, n, flats)
            assertEquals(ChordTransposer.stripChords(text), ChordTransposer.stripChords(moved), "words moved in: $text")
            assertEquals(ChordTransposer.chordsIn(text, n, flats), ChordTransposer.chordsIn(moved), "chords in: $text")
        }
    }

    private companion object {
        const val SEMITONES = 12
        val CONFIG = PropTestConfig(seed = 20_261_008L, iterations = 500)
    }
}
