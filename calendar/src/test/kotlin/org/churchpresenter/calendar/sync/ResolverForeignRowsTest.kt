package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResolverForeignRowsTest {

    private val today = LocalDate.of(2026, 9, 20)
    private val now = Instant.parse("2026-09-20T12:00:00Z")
    private val joined = Resolver(emptyList(), emptyList(), today, now, keepForeignRows = true)

    private fun remote(vararg rows: RemoteRow) =
        RemoteService(id = "s1", date = "2026-09-27", startTime = "10:00", name = "Sunday", rows = rows.toList())

    private fun local(vararg items: ScheduleItem) =
        PlannedService(id = "s1", date = "2026-09-27", name = "Sunday", startTime = "10:00", items = items.toList())

    @Test
    fun `a ref this computer never had is kept as a flagged placeholder in its place`() {
        val resolved = joined.resolve(
            remote(RemoteRow.Section("h", "Welcome"), RemoteRow.Ref("m", "Intro video", RemoteKind.MEDIA)),
            null,
        )!!

        assertEquals(0, resolved.dropped)
        assertEquals(listOf("h", "m"), resolved.service.items.map { it.id })
        assertEquals(ScheduleItem.MinistryItem("m", "Intro video"), resolved.service.items[1])
        assertEquals(mapOf("m" to Resolver.UNRESOLVED_ELSEWHERE), resolved.unresolved)
        assertEquals(resolved.unresolved, resolved.service.unresolvedRows)
    }

    @Test
    fun `a placeholder for a ref with no title is named as a ministry row`() {
        val resolved = joined.resolve(remote(RemoteRow.Ref("m", "  ", RemoteKind.MEDIA)), null)!!

        assertEquals("Ministry", assertIs<ScheduleItem.MinistryItem>(resolved.service.items.single()).title)
    }

    @Test
    fun `a ref this computer can open resolves to its own item, unflagged`() {
        val video = ScheduleItem.MediaItem("m", "/videos/intro.mp4", "Intro video", "local")

        val resolved = joined.resolve(remote(RemoteRow.Ref("m", "Renamed", RemoteKind.MEDIA)), local(video))!!

        assertEquals(video, resolved.service.items.single())
        assertTrue(resolved.unresolved.isEmpty())
    }

    @Test
    fun `an earlier placeholder is rebuilt from the row as it now arrives`() {
        val earlier = ScheduleItem.MinistryItem("m", "Intro video")

        val resolved = joined.resolve(remote(RemoteRow.Ref("m", "Welcome video", RemoteKind.MEDIA)), local(earlier))!!

        assertEquals(ScheduleItem.MinistryItem("m", "Welcome video"), resolved.service.items.single())
        assertEquals(Resolver.UNRESOLVED_ELSEWHERE, resolved.unresolved["m"])
    }

    @Test
    fun `the main computer still drops a ref it never had`() {
        val main = Resolver(emptyList(), emptyList(), today, now)

        val resolved = main.resolve(remote(RemoteRow.Ref("m", "Intro video", RemoteKind.MEDIA)), null)!!

        assertTrue(resolved.service.items.isEmpty())
        assertEquals(1, resolved.dropped)
    }
}
