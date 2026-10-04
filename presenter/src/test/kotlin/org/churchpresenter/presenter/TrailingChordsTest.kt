package org.churchpresenter.presenter

import org.churchpresenter.songchords.ChordSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrailingChordsTest {

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
}
