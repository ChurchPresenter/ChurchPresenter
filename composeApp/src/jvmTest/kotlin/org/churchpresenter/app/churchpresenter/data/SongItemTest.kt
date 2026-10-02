package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
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
