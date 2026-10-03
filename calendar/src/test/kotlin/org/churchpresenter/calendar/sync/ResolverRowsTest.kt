package org.churchpresenter.calendar.sync

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The rows a phone sends that [ResolverTest] does not: ones that cannot be kept, ones that arrive
 * with nothing in them, and a song whose library number is not a number. A phone's copy is never
 * trusted -- every row is rebuilt here, and one with nothing to say still reads as what it is.
 */
class ResolverRowsTest {

    private val resolver = Resolver(
        songs = listOf(SongItem(number = "A1", title = "Lettered Hymn", songbook = "Hymnal")),
        presets = emptyList(),
        today = LocalDate.of(2026, 9, 20),
    )

    private fun service(vararg rows: RemoteRow, seriesId: String = "", timing: Map<String, RowTiming> = emptyMap()) =
        resolver.resolve(
            RemoteService(
                id = "svc", date = "2026-09-27", startTime = "10:00", name = "Sunday",
                rows = rows.toList(), seriesId = seriesId, timing = timing,
            ),
            local = null,
        )!!

    @Test
    fun `a row with a malformed or repeated id is dropped and counted`() {
        val resolved = service(
            RemoteRow.Ministry("ok", "Welcome"),
            RemoteRow.Ministry("bad id", "Not an id"),
            RemoteRow.Ministry("ok", "The same id again"),
        )
        assertEquals(listOf("ok"), resolved.service.items.map { it.id })
        assertEquals(2, resolved.dropped)
    }

    @Test
    fun `a row sent with no title still says what kind of row it is`() {
        val items = service(
            RemoteRow.Section("s", ""),
            RemoteRow.Ministry("m", ""),
            RemoteRow.Song("u", ""),
            RemoteRow.Bible("b", ""),
            RemoteRow.Preset("p", "", presetId = "missing"),
        ).service.items

        assertEquals("Section", assertIs<ScheduleItem.LabelItem>(items[0]).text)
        assertEquals("Ministry", assertIs<ScheduleItem.MinistryItem>(items[1]).title)
        assertEquals("Song", assertIs<ScheduleItem.SongItem>(items[2]).title)
        assertEquals("Reference", assertIs<ScheduleItem.MinistryItem>(items[3]).title)
        assertEquals("Preset", assertIs<ScheduleItem.MinistryItem>(items[4]).title)
    }

    @Test
    fun `a library song numbered with letters keeps its title and has no number`() {
        val song = assertIs<ScheduleItem.SongItem>(service(RemoteRow.Song("a", "Lettered Hymn")).service.items.single())
        assertEquals("Lettered Hymn", song.title)
        assertEquals(0, song.songNumber)
    }

    @Test
    fun `a series id is kept only when it is a well-formed id`() {
        assertEquals("series-1", service(seriesId = "series-1").service.seriesId)
        assertEquals("", service(seriesId = "not an id").service.seriesId)
    }

    @Test
    fun `a row's timing is kept only when it says something`() {
        val timing = mapOf(
            "a" to RowTiming(runSeconds = 90),
            "b" to RowTiming(),
        )
        val kept = service(RemoteRow.Ministry("a", "Prayer"), RemoteRow.Ministry("b", "Offering"), timing = timing)
        assertEquals(setOf("a"), kept.service.timing.keys)
        assertEquals(90, kept.service.timing.getValue("a").runSeconds)
    }
}
