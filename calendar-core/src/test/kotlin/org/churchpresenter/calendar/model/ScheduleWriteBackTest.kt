package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScheduleWriteBackTest {

    private fun song(id: String) = ScheduleItem.SongItem(
        id = id, songNumber = 1, title = "Song $id", songbook = "", songId = "",
    )

    private fun offScreen(id: String) = ScheduleItem.MinistryItem(id = id, title = "Prayer $id")

    private fun service(id: String, vararg rows: ScheduleItem) = PlannedService(
        id = id,
        date = "2026-09-20",
        name = "Service $id",
        startTime = "10:00",
        items = rows.toList(),
        plannedSeconds = rows.associate { it.id to 300 },
    )

    @Test
    fun `the Schedule is the service with the most of its rows there`() {
        val morning = service("m", song("m1"), song("m2"))
        val evening = service("e", song("e1"))
        val calendar = CalendarDocument(services = listOf(evening, morning))

        assertSame(morning, calendar.serviceInSchedule(listOf(song("e1"), song("m1"), song("m2"))))
        assertNull(calendar.serviceInSchedule(listOf(song("by-hand"))))
        assertNull(calendar.serviceInSchedule(emptyList()))
    }

    @Test
    fun `a service is in the Schedule when every row it puts there is, off-screen rows aside`() {
        val service = service("s", offScreen("p"), song("a"))

        assertTrue(service.isInSchedule(listOf(song("a"), song("extra"))))
        assertFalse(service.isInSchedule(listOf(song("extra"))))
    }

    @Test
    fun `rows added, removed and moved in the Schedule become the service's`() {
        val service = service("s", song("a"), song("b"), song("c")).copy(
            timing = mapOf("b" to RowTiming(runSeconds = 90)),
        )

        val saved = service.withScheduleRows(listOf(song("c"), song("new"), song("a")))

        assertEquals(listOf("c", "new", "a"), saved.items.map { it.id })
        assertEquals(setOf("a", "c"), saved.plannedSeconds.keys, "a removed row's length goes with it")
        assertTrue(saved.timing.isEmpty(), "and so does its timing")
    }

    @Test
    fun `off-screen rows stay before the row they preceded, or at the end when it has gone`() {
        val service = service("s", offScreen("p1"), song("a"), offScreen("p2"), song("b"), offScreen("end"))

        assertEquals(
            listOf("p1", "a", "p2", "b", "end"),
            service.withScheduleRows(listOf(song("a"), song("b"))).items.map { it.id },
            "the Schedule as loaded changes nothing",
        )
        assertEquals(
            listOf("p2", "b", "new", "p1", "end"),
            service.withScheduleRows(listOf(song("b"), song("new"))).items.map { it.id },
        )
    }

    @Test
    fun `a cue keeps its planned time, not the clock time it was pinned to in the Schedule`() {
        val planned = ScheduleItem.CueItem(id = "cue", action = CueAction.BLANK, offsetMinutes = 15)
        val service = service("s", planned, song("a"))

        val saved = service.withScheduleRows(service.rowsForSchedule())

        assertEquals(planned, saved.items.first())
    }
}
