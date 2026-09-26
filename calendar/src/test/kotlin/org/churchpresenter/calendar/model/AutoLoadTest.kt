package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoLoadTest {

    private val today = LocalDate.of(2026, 9, 20)

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(today, LocalTime.of(hour, minute))

    private fun song(id: String) = ScheduleItem.SongItem(
        id = id, songNumber = 1, title = "Song $id", songbook = "", songId = "",
    )

    private fun service(
        start: String,
        items: List<ScheduleItem> = listOf(song("a")),
        planned: Map<String, Int> = emptyMap(),
        timing: Map<String, RowTiming> = emptyMap(),
        date: LocalDate = today,
    ) = PlannedService(
        id = "svc-$start",
        date = storedDate(date),
        name = "Service $start",
        startTime = start,
        items = items,
        plannedSeconds = planned,
        timing = timing,
    )

    @Test
    fun `is due from five minutes before the first row until its run is over`() {
        val service = service("10:00", planned = mapOf("a" to 600))

        assertFalse(service.isDueToLoad(at(9, 54)), "six minutes out is too early")
        assertTrue(service.isDueToLoad(at(9, 55)), "the lead is exactly five minutes")
        assertTrue(service.isDueToLoad(at(10, 9)), "still running")
        assertFalse(service.isDueToLoad(at(10, 11)), "ten planned minutes are up")
    }

    @Test
    fun `a service with nothing estimated is assumed to run two hours`() {
        val service = service("10:00")

        assertTrue(service.isDueToLoad(at(11, 59)))
        assertFalse(service.isDueToLoad(at(12, 1)))
    }

    @Test
    fun `the first row's own start is what the lead counts back from`() {
        // The service is filed at 10:00 but its first row is pinned to 10:15, so it begins then.
        val service = service(
            start = "10:00",
            timing = mapOf("a" to RowTiming(startAt = "10:15")),
        )

        assertEquals(LocalTime.of(10, 15), service.firstRowStart())
        assertFalse(service.isDueToLoad(at(9, 56)), "five before the *service* is not the trigger")
        assertTrue(service.isDueToLoad(at(10, 10)))
    }

    @Test
    fun `falls back to the service start when no row carries one`() {
        assertEquals(LocalTime.of(18, 30), service("18:30").firstRowStart())
    }

    @Test
    fun `only today's services are considered`() {
        val document = CalendarDocument(
            services = listOf(service("10:00", date = today.minusDays(1))),
        )

        assertNull(document.serviceToAutoLoad(at(9, 58)))
    }

    @Test
    fun `where two overlap the later one wins`() {
        val morning = service("10:00", planned = mapOf("a" to 36_000))
        val evening = service("18:00")
        val document = CalendarDocument(services = listOf(morning, evening))

        assertEquals(morning.id, document.serviceToAutoLoad(at(10, 30))?.id)
        assertEquals(evening.id, document.serviceToAutoLoad(at(18, 30))?.id, "the evening has started")
    }

    @Test
    fun `nothing is due between services`() {
        val document = CalendarDocument(services = listOf(service("10:00"), service("18:00")))

        assertNull(document.serviceToAutoLoad(at(13, 0)))
    }

    @Test
    fun `an unreadable start time is never due`() {
        assertFalse(service("not a time").isDueToLoad(at(10, 0)))
    }
    @Test
    fun `the next load is the earliest service still ahead, today's first`() {
        val morning = service("10:00")
        val evening = service("18:00")
        val tomorrow = service("09:00", date = today.plusDays(1))
        val calendar = CalendarDocument(services = listOf(evening, tomorrow, morning))

        assertEquals(
            UpcomingLoad(morning.id, morning.name, at(9, 55)),
            calendar.nextAutoLoad(at(8, 0)),
        )
        assertEquals(evening.id, calendar.nextAutoLoad(at(9, 55))?.serviceId, "the morning's window has opened")
        assertEquals(at(17, 50), calendar.nextAutoLoad(at(12, 0), lead = 10)?.loadAt, "the lead moves it earlier")
        assertEquals(
            LocalDateTime.of(today.plusDays(1), LocalTime.of(8, 55)),
            calendar.nextAutoLoad(at(17, 55))?.loadAt,
            "nothing left today, so tomorrow's is next",
        )
    }

    @Test
    fun `a service days ahead is announced, so it can be loaded early to rehearse`() {
        val sunday = service("10:00", date = today.plusDays(4))
        val lastWeek = service("09:00", date = today.minusDays(3))

        assertEquals(
            LocalDateTime.of(today.plusDays(4), LocalTime.of(9, 55)),
            CalendarDocument(services = listOf(sunday, lastWeek)).nextAutoLoad(at(12, 0))?.loadAt,
        )
        assertNull(CalendarDocument(services = listOf(lastWeek)).nextAutoLoad(at(12, 0)), "the past is not ahead")
    }

    @Test
    fun `a skipped service is passed over for the next one`() {
        val morning = service("10:00")
        val evening = service("18:00")
        val calendar = CalendarDocument(services = listOf(morning, evening))

        assertEquals(evening.id, calendar.nextAutoLoad(at(8, 0)) { it.id == morning.id }?.serviceId)
    }
}
