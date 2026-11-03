package org.churchpresenter.songs

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A song dragged onto the schedule is the same row Add to Schedule makes. */
class SongDragTest {

    @Test
    fun `a dragged song carries its number, title, songbook and id`() {
        val row = SongItem(number = "042", title = "Amazing Grace", songbook = "Hymnal")
            .toScheduleItem() as ScheduleItem.SongItem
        assertEquals(42, row.songNumber)
        assertEquals("Amazing Grace", row.title)
        assertEquals("Hymnal", row.songbook)
        assertEquals("Hymnal::042", row.songId, "the id the library knows the song by")
        assertTrue(row.id.isNotBlank())
    }

    @Test
    fun `a song with no number is numbered nought`() {
        val row = SongItem(number = "", title = "Untitled").toScheduleItem() as ScheduleItem.SongItem
        assertEquals(0, row.songNumber)
    }

    @Test
    fun `each drag is a new row`() {
        val song = SongItem(number = "1", title = "Doxology")
        assertNotEquals(song.toScheduleItem().id, song.toScheduleItem().id)
    }
}
