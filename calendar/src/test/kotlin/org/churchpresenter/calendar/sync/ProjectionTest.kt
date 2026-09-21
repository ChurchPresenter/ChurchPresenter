package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectionTest {

    private val today = LocalDate.of(2026, 9, 20)

    private val service = PlannedService(
        id = "svc",
        date = "2026-09-27",
        name = "Sunday Morning",
        startTime = "10:00",
        items = listOf(
            ScheduleItem.LabelItem("r1", "Worship", "#FFFFFF", "#5B9DF5"),
            ScheduleItem.SongItem("r2", 42, "Here I Am", "Hymnal", songId = "Hymnal::42"),
            ScheduleItem.BibleVerseItem("r3", "Psalms", 100, 1, "Make a joyful noise", verseRange = "1-5", bookId = 19),
            ScheduleItem.MinistryItem("r4", "Violin", "Jake"),
            ScheduleItem.PictureItem("r5", "/Users/x/Pictures/welcome", "Welcome loop", 24),
            ScheduleItem.WebsiteItem("r6", "https://example.org/secret", "Site"),
            ScheduleItem.CueItem("r7", action = "go_live"),
        ),
        plannedSeconds = mapOf("r2" to 300, "gone" to 99),
        timing = mapOf("r2" to RowTiming(startAt = "09:45"), "gone" to RowTiming(repeats = 0)),
    )

    @Test
    fun `phone kinds keep their content, everything else becomes a ref`() {
        val rows = Projection.service(service).rows

        assertEquals(7, rows.size)
        assertEquals(RemoteRow.Section("r1", "Worship", "#5B9DF5"), rows[0])
        assertEquals(RemoteRow.Song("r2", "Here I Am", "Hymnal::42", "Hymnal", "42"), rows[1])
        assertEquals(RemoteRow.Bible("r3", "Psalms 100:1-5", bookId = 19), rows[2])
        assertEquals(RemoteRow.Ministry("r4", "Violin", "Jake"), rows[3])
        assertEquals(RemoteRow.Ref("r5", "Welcome loop (24 images)", RemoteKind.PICTURES), rows[4])
        assertEquals(RemoteKind.WEBSITE, (rows[5] as RemoteRow.Ref).kind)
        assertEquals(RemoteKind.CUE, (rows[6] as RemoteRow.Ref).kind)
    }

    @Test
    fun `no path or url leaves the machine`() {
        val text = Projection.service(service).rows.joinToString { it.toString() }

        assertTrue("/Users" !in text)
        assertTrue("example.org" !in text)
    }

    @Test
    fun `timing and lengths travel only for rows that exist`() {
        val remote = Projection.service(service)

        assertEquals(setOf("r2"), remote.plannedSeconds.keys)
        assertEquals(setOf("r2"), remote.timing.keys)
        assertEquals(Projection.DESKTOP, remote.updatedBy)
    }

    @Test
    fun `only the retention window is pushed, newest first, capped`() {
        val old = service.copy(id = "old", date = today.minusDays(91).toString())
        val edge = service.copy(id = "edge", date = today.minusDays(90).toString())
        val many = (1..WireLimits.SERVICES_PER_PUSH + 5).map {
            service.copy(id = "s$it", date = today.plusDays(it.toLong()).toString())
        }

        val pushed = Projection.services(CalendarDocument(services = listOf(old, edge) + many), today)

        assertEquals(WireLimits.SERVICES_PER_PUSH, pushed.size)
        assertTrue(pushed.none { it.id == "old" })
        assertEquals("s${WireLimits.SERVICES_PER_PUSH + 5}", pushed.first().id)
    }

    @Test
    fun `tombstones and presets are projected by id and kind only`() {
        val document = CalendarDocument(deletedServices = mapOf("x" to "2026-09-01T00:00:00Z"))
        val presets = listOf(ItemPreset("p1", "Countdown", ScheduleItem.AnnouncementItem("a", "5:00", isTimer = true)))

        assertEquals(listOf(RemoteTombstone("x", "2026-09-01T00:00:00Z")), Projection.tombstones(document))
        assertEquals(listOf(RemotePreset("p1", "Countdown", RemoteKind.TIMER)), Projection.presets(presets))
    }
}
