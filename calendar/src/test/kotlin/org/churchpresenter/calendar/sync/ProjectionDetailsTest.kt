package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What [ProjectionTest] leaves out: the edges of what the desktop puts on the wire. */
class ProjectionDetailsTest {

    @Test
    fun `a song with no number goes out with none rather than a zero`() {
        val row = Projection.row(ScheduleItem.SongItem("a", 0, "Unnumbered", "Hymns", "Hymns::0"))
        assertEquals("", assertIs<RemoteRow.Song>(row).number)
    }

    @Test
    fun `a deletion with an unreadable date or no version still goes out`() {
        val document = CalendarDocument(deletedServices = mapOf("gone" to "not a time"))
        val deletion = Projection.deletions(document).single()
        assertEquals("", deletion.date, "only the relay's retention reads the date")
        assertEquals(0L, deletion.version)
        assertTrue(deletion.deleted)
    }

    @Test
    fun `a songbook id keeps letters, digits and the id punctuation, and hashes the rest`() {
        val key = Projection.catalogKey("Songs of Praise v2.0_new-ed!")
        assertTrue(key.startsWith("Songs_of_Praise_v2.0_new-ed-"), key)
        assertTrue(Projection.catalogKey("Гимны").matches(Regex("[0-9a-f]{8}")), "a non-Latin name is its hash alone")
    }

    @Test
    fun `a book's songs are listed by number, the numberless after them`() {
        val songs = listOf("10", "B", "2", "A").map { SongItem(number = it, title = "Song $it", songbook = "Hymns") }
        val record = Projection.catalog(songs) { null }.values.single()
        assertEquals(listOf("2", "10", "A", "B"), record.songs.map { it.n })
    }
}
