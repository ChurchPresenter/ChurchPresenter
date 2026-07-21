package org.churchpresenter.app.churchpresenter.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The identity a song is filed under.
 *
 * [SongItem.songId] is the key the statistics store counts against, the schedule matches on, and
 * Instance Link addresses a song by, so it has to stay stable across an edit to the song's own text
 * and stay distinct between songbooks. It is derived rather than stored, which is what makes it
 * worth pinning: nothing fails loudly if the derivation changes, but a year of play counts would
 * quietly split in two.
 */
class SongItemTest {

    private fun song(number: String = "1", title: String = "Amazing Grace", songbook: String = "Hymnal") =
        SongItem(number = number, title = title, songbook = songbook)

    @Test
    fun `a numbered song is filed under its songbook and number`() {
        assertEquals("Hymnal::1", song().songId)
    }

    @Test
    fun `the same number in two songbooks is two different songs`() {
        assertTrue(
            song(songbook = "Hymnal").songId != song(songbook = "Songs of Praise").songId,
            "song 1 means something different in each book",
        )
    }

    @Test
    fun `renaming a song does not change what it is filed under`() {
        assertEquals(
            song(title = "Amazing Grace").songId,
            song(title = "Amazing Grace (revised)").songId,
            "a corrected title must not split a song's play count in two",
        )
    }

    @Test
    fun `a song with no number is filed under its title instead`() {
        assertEquals("Hymnal::Amazing Grace", song(number = "").songId)
    }

    @Test
    fun `a number of nothing but spaces counts as no number`() {
        assertEquals("Hymnal::Amazing Grace", song(number = "   ").songId)
    }

    @Test
    fun `an unnumbered song is filed per songbook too`() {
        assertTrue(
            song(number = "", songbook = "Hymnal").songId != song(number = "", songbook = "Other").songId,
        )
    }

    @Test
    fun `a song from a loose file with no songbook still gets an id`() {
        assertEquals("::1", song(songbook = "").songId, "an id with an empty half is still stable and distinct")
    }

    @Test
    fun `numbers are compared as written, not as values`() {
        // "01" and "1" are different ids. Song numbers arrive as text from every format the app
        // reads, so this is worth knowing when two files disagree about padding.
        assertTrue(song(number = "01").songId != song(number = "1").songId)
    }
}

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
