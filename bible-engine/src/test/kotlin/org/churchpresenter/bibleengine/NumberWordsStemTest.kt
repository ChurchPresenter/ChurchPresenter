package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.detection.NumberWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberWordsStemTest {

    @Test
    fun `blank and empty tokens are not numbers`() {
        assertNull(NumberWords.parseToken(""))
        assertNull(NumberWords.parseToken("   "))
        assertEquals(0, NumberWords.matchedStemLength(""))
        assertEquals(0, NumberWords.matchedStemLength("   "))
    }

    @Test
    fun `a digit match reports no stem length`() {
        assertEquals(5, NumberWords.parseToken("5"))
        assertEquals(0, NumberWords.matchedStemLength("5"))
        assertEquals(0, NumberWords.matchedStemLength("3-я"))
    }

    @Test
    fun `a word match reports the stem it matched`() {
        val len = NumberWords.matchedStemLength("третий")

        assertTrue(len > 0, "expected a stem match for третий")
        assertTrue(len <= "третий".length)
    }

    @Test
    fun `a look-alike reports no stem length`() {
        assertEquals(0, NumberWords.matchedStemLength("сторона"))
        assertEquals(0, NumberWords.matchedStemLength("столько"))
    }

    @Test
    fun `an unrelated word reports no stem length`() {
        assertEquals(0, NumberWords.matchedStemLength("аллилуйя"))
    }

    @Test
    fun `stem length is case-insensitive and trims`() {
        assertEquals(NumberWords.matchedStemLength("третий"), NumberWords.matchedStemLength("  ТРЕТИЙ  "))
    }

    @Test
    fun `a sequence start outside the token list yields nothing`() {
        assertNull(NumberWords.parseSequence(listOf("три"), start = 5))
        assertNull(NumberWords.parseSequence(emptyList(), start = 0))
        assertNull(NumberWords.parseSequence(listOf("три"), start = -1))
    }

    @Test
    fun `a sequence starting on a non-number yields nothing`() {
        assertNull(NumberWords.parseSequence(listOf("слово", "три"), start = 0))
    }

    @Test
    fun `a digit token consumes exactly one position`() {
        assertEquals(37 to 1, NumberWords.parseSequence(listOf("37", "38"), start = 0))
    }

    @Test
    fun `a following digit token stops a word sequence`() {
        val result = NumberWords.parseSequence(listOf("сто", "5"), start = 0)

        assertEquals(1, result?.second, "the digit must not be folded into the word sequence")
    }

    @Test
    fun `a trailing non-number stops the sequence`() {
        val result = NumberWords.parseSequence(listOf("сто", "пятый", "слово"), start = 0)

        assertEquals(2, result?.second)
    }

    @Test
    fun `a sequence at the very end of the token list terminates`() {
        val result = NumberWords.parseSequence(listOf("сто", "пятый"), start = 0)

        assertEquals(105, result?.first)
        assertEquals(2, result?.second)
    }
}
