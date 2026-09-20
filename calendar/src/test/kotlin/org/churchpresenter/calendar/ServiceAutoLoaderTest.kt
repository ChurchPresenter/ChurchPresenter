package org.churchpresenter.calendar

import kotlinx.coroutines.test.runTest
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceAutoLoaderTest {

    private val today = LocalDate.of(2026, 9, 20)

    /** The Schedule tab, as far as the loader can see it: rows in, rows back out. */
    private class FakeSchedule {
        var rows: List<ScheduleItem> = emptyList()
        var loads = 0

        /** Deaf, the way the real one is before the Schedule tab has first composed. */
        var accepting = true

        fun host(): CalendarHost = CalendarHost(
            loadIntoSchedule = { items, _, replace, _, _ ->
                loads++
                if (!accepting) return@CalendarHost
                rows = if (replace) items else rows + items
            },
            currentSchedule = { rows },
        )
    }

    private fun song(id: String) = ScheduleItem.SongItem(
        id = id, songNumber = 1, title = "Song", songbook = "", songId = "",
    )

    private fun service(id: String, start: String, rows: List<ScheduleItem> = listOf(song("$id-1"))) =
        PlannedService(
            id = id,
            date = "2026-09-20",
            name = "Service $id",
            startTime = start,
            items = rows,
            plannedSeconds = rows.associate { it.id to 600 },
        )

    private fun document(vararg services: PlannedService, on: Boolean = true) = CalendarDocument(
        preferences = CalendarPreferences(autoLoadService = on),
        services = services.toList(),
    )

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(today, LocalTime.of(hour, minute))

    private fun loader(
        document: CalendarDocument,
        schedule: FakeSchedule,
        now: () -> LocalDateTime,
    ) = ServiceAutoLoader(document = { document }, host = schedule.host(), now = now)

    @Test
    fun `loads the service that is about to start`() = runTest {
        val schedule = FakeSchedule()
        val morning = service("m", "10:00")
        var clock = at(9, 50)
        val loader = loader(document(morning), schedule) { clock }

        loader.tick()
        assertEquals(0, schedule.loads, "ten minutes out is too early")

        clock = at(9, 56)
        loader.tick()
        assertEquals(1, schedule.loads)
        assertEquals(listOf("m-1"), schedule.rows.map { it.id })
    }

    @Test
    fun `last week's service is replaced, but rows built by hand are kept and the service goes under them`() = runTest {
        val lastWeek = service("old", "10:00")
        val today = service("svc", "10:00")
        val schedule = FakeSchedule().apply { rows = lastWeek.items }

        loader(document(lastWeek, today), schedule, { at(9, 56) }).tick()
        assertEquals(today.items, schedule.rows, "a planned leftover is replaced")

        val byHand = song("built-by-hand")
        val fresh = FakeSchedule().apply { rows = listOf(byHand) }
        loader(document(today), fresh, { at(9, 56) }).tick()
        assertEquals(listOf(byHand) + today.items, fresh.rows, "the operator's row stays, the service follows it")
    }

    @Test
    fun `off unless the preference is on`() = runTest {
        val schedule = FakeSchedule()
        val loader = loader(document(service("m", "10:00"), on = false), schedule) { at(9, 56) }

        loader.tick()

        assertEquals(0, schedule.loads)
    }

    @Test
    fun `loads a plan once, however many ticks`() = runTest {
        val schedule = FakeSchedule()
        val loader = loader(document(service("m", "10:00")), schedule) { at(9, 56) }

        repeat(5) { loader.tick() }

        assertEquals(1, schedule.loads)
    }

    @Test
    fun `a schedule cleared by the operator stays cleared`() = runTest {
        val schedule = FakeSchedule()
        val loader = loader(document(service("m", "10:00")), schedule) { at(9, 56) }
        loader.tick()
        loader.tick() // the tick that records it as landed

        schedule.rows = emptyList()
        repeat(3) { loader.tick() }

        assertEquals(1, schedule.loads, "putting it back would undo a deliberate clear")
        assertTrue(schedule.rows.isEmpty())
    }

    @Test
    fun `a load that went nowhere is tried again`() = runTest {
        val schedule = FakeSchedule()
        schedule.accepting = false
        val loader = loader(document(service("m", "10:00")), schedule) { at(9, 56) }

        loader.tick()
        assertTrue(schedule.rows.isEmpty(), "the Schedule tab was not listening yet")

        schedule.accepting = true
        loader.tick()

        assertEquals(2, schedule.loads)
        assertEquals(listOf("m-1"), schedule.rows.map { it.id })
    }

    @Test
    fun `an edited plan is a different plan and loads`() = runTest {
        val schedule = FakeSchedule()
        val first = service("m", "10:00")
        var document = document(first)
        var clock = at(9, 56)
        val loader = ServiceAutoLoader(
            document = { document },
            host = schedule.host(),
            now = { clock },
        )
        loader.tick()
        loader.tick()
        assertEquals(1, schedule.loads)

        document = document(first.copy(items = first.items + song("m-2")))
        clock = at(9, 57)
        loader.tick()

        assertEquals(2, schedule.loads)
        assertEquals(listOf("m-1", "m-2"), schedule.rows.map { it.id })
    }

    @Test
    fun `both of a day's services load, each at its own time`() = runTest {
        val schedule = FakeSchedule()
        val morning = service("m", "10:00")
        val evening = service("e", "18:00")
        var clock = at(9, 56)
        val loader = loader(document(morning, evening), schedule) { clock }

        loader.tick()
        assertEquals(listOf("m-1"), schedule.rows.map { it.id })

        clock = at(13, 0)
        loader.tick()
        assertEquals(1, schedule.loads, "nothing is due between the two")

        clock = at(17, 56)
        loader.tick()
        assertEquals(2, schedule.loads)
        assertEquals(listOf("e-1"), schedule.rows.map { it.id })
    }

    @Test
    fun `a morning service cannot come back after the evening one`() = runTest {
        val schedule = FakeSchedule()
        // Ten hours of planned content, so the morning's window is still open in the evening.
        val morning = service("m", "10:00").copy(plannedSeconds = mapOf("m-1" to 36_000))
        val evening = service("e", "18:00")
        var clock = at(9, 56)
        val loader = loader(document(morning, evening), schedule) { clock }
        loader.tick()
        loader.tick()

        clock = at(17, 56)
        loader.tick()
        loader.tick()
        assertEquals(listOf("e-1"), schedule.rows.map { it.id })

        // The evening service is over; the morning's window has not closed yet.
        clock = at(19, 0)
        repeat(3) { loader.tick() }

        assertEquals(2, schedule.loads, "the morning has had its turn")
        assertEquals(listOf("e-1"), schedule.rows.map { it.id })
    }
}
