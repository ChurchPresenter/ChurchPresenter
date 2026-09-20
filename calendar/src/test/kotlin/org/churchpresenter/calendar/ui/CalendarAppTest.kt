@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window as a whole: the month, the day, the run of show and what the footer does.
 *
 * Driven through the real [CalendarApp] against a real store folder, because the panes only say
 * anything true together — a service selected in the month decides what the day lists, which
 * decides what the run of show draws.
 */
class CalendarAppTest {

    @Test
    fun `opens on the month, the day and the run of show`() = withCalendar(documentWith(service())) {
        awaitText("Sunday Morning")

        assertTrue(shows("September"), "the month it is showing")
        assertTrue(shows("Amazing Grace"), "the run of show's rows")
        assertTrue(shows("WORKSHOP") || shows("Worship") || shows("WORSHIP"), "and its section headings")
    }

    @Test
    fun `a day with nothing planned offers to plan something`() = withCalendar {
        awaitText("Nothing planned")
    }

    @Test
    fun `the run of show shows each row's planned length`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")

        assertTrue(shows("5:00"), "the planned five minutes")
    }

    @Test
    fun `a pinned row says when it starts`() = withCalendar(
        documentWith(
            service(timing = mapOf("a" to RowTiming(startAt = "09:45"))),
        )
    ) {
        awaitText("Amazing Grace")

        // The row's chip reads the offset it was chosen as: a quarter of an hour before 10:00.
        assertTrue(shows("15"), "the offset from the service start")
    }

    @Test
    fun `the footer loads the service into the schedule`() {
        val loaded = mutableListOf<List<ScheduleItem>>()
        val host = CalendarHost(
            loadIntoSchedule = { items, _, _, _, _ -> loaded += items },
            currentSchedule = { emptyList() },
        )

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")
            clickText("Load into Schedule")

            assertEquals(1, loaded.size)
            assertEquals(listOf("h", "a"), loaded.single().map { it.id })
        }
    }

    @Test
    fun `the settings dialog opens on its tabs`() = withCalendar(documentWith(service())) {
        awaitText("Sunday Morning")

        clickFirst("Calendar settings")
        awaitText("Sections")

        assertTrue(shows("Templates"))
        assertTrue(shows("Presets"))
        assertTrue(shows("Defaults"))
    }

    @Test
    fun `the add-item sheet opens on the songs tab`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")

        clickFirst("Add song, verse or section")
        awaitText("Songs")

        assertTrue(shows("Bible"), "and the other places a row can come from")
        assertTrue(shows("Presets"))
    }

    @Test
    fun `a service can be added to the open day`() = withCalendar {
        awaitText("Nothing planned")

        clickFirst("Add service")
        awaitText("Add service")
    }
}
