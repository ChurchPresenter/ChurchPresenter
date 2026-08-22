package org.churchpresenter.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a Strong's number is read.
 *
 * The dictionary decides which alphabet an entry belongs to, and where it sorts, purely from the
 * text of its number — there is no separate field for either. Both are used on the load path that
 * builds the entry list, so a change here reorders the whole dictionary.
 */
class StrongsEntryTest {

    private fun entry(number: String) = StrongsEntry(
        number = number,
        word = "word",
        transliteration = "translit",
        pronunciation = "pron",
        definition = "definition",
    )

    @Test
    fun `an H number is hebrew and a G number is greek`() {
        assertTrue(entry("H430").isHebrew)
        assertFalse(entry("H430").isGreek)

        assertTrue(entry("G26").isGreek)
        assertFalse(entry("G26").isHebrew)
    }

    @Test
    fun `the number sorts by its digits, without the letter`() {
        assertEquals(430, entry("H430").numericValue)
        assertEquals(26, entry("G26").numericValue)
        assertEquals(7225, entry("H7225").numericValue)
    }

    @Test
    fun `the two alphabets share a number range`() {
        assertEquals(
            entry("H430").numericValue,
            entry("G430").numericValue,
            "sorting alone cannot separate the testaments, which is why the two lists are built " +
                "separately and then joined rather than sorted together",
        )
    }

    @Test
    fun `an entry with no number is neither alphabet and sorts first`() {
        val blank = entry("")

        assertFalse(blank.isHebrew)
        assertFalse(blank.isGreek)
        assertEquals(0, blank.numericValue)
    }

    @Test
    fun `a number that is not a number sorts first rather than throwing`() {
        assertEquals(0, entry("Gxyz").numericValue, "a malformed entry must not stop the dictionary loading")
    }

    /**
     * Documents CURRENT behaviour: the alphabet test is case-sensitive, so a lower-case prefix is
     * neither Greek nor Hebrew. The bundled data is upper-case throughout and the search box
     * lower-cases the *query* rather than the entry, so this is not reachable today — but it is
     * the assumption that makes the language filter work at all.
     */
    @Test
    fun `a lower-case prefix belongs to neither alphabet`() {
        assertFalse(entry("g26").isGreek)
        assertFalse(entry("h430").isHebrew)
        assertEquals(26, entry("g26").numericValue, "the digits are still read, whatever the prefix")
    }
}
