package org.churchpresenter.app.churchpresenter.models.songs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What identifies a song, and which edits to one move its file. */
class SongIdentityTest {

    @Test
    fun `a numbered song is identified by songbook and number`() {
        assertEquals("Hymnal::0001", SongItem("0001", "Amazing Grace", songbook = "Hymnal").songId)
    }

    @Test
    fun `a song with no number falls back to its title`() {
        assertEquals("Hymnal::Amazing Grace", SongItem("", "Amazing Grace", songbook = "Hymnal").songId)
        assertEquals("Hymnal::Amazing Grace", SongItem("   ", "Amazing Grace", songbook = "Hymnal").songId)
    }

    @Test
    fun `a song outside any songbook still has an id`() {
        assertEquals("::0001", SongItem("0001", "Loose").songId)
    }

    @Test
    fun `two songbooks can hold the same number without colliding`() {
        val hymnal = SongItem("0001", "One", songbook = "Hymnal")
        val kids = SongItem("0001", "Another", songbook = "Kids")

        assertTrue(hymnal.songId != kids.songId, "the songbook is what keeps the numbers apart")
    }

    @Test
    fun `only the fields that live outside the file move it`() {
        // The number is the file's name prefix and the songbook is its folder, so editing either is
        // a move; everything else is written inside the file and stays put.
        listOf(SongField.NUMBER, SongField.TITLE, SongField.SONGBOOK).forEach {
            assertTrue(it.movesFile, "$it decides where the file lives")
        }
        listOf(
            SongField.SECONDARY_TITLE,
            SongField.AUTHOR,
            SongField.COMPOSER,
            SongField.TUNE,
            SongField.CCLI,
        ).forEach {
            assertFalse(it.movesFile, "$it is written inside the file and must not move it")
        }
    }
}
