@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The list itself: what a row shows, and what can be done to one. */
class RunOfShowTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun rows() = listOf(
        heading("h", "Pre-Service"),
        song("a", "Amazing Grace"),
        ScheduleItem.MediaItem(id = "m", mediaUrl = "/clips/welcome.mp4", mediaTitle = "Welcome", mediaType = "local"),
        ScheduleItem.AnnouncementItem(
            id = "t", text = "", isTimer = true, timerMode = TimerModes.DURATION, timerMinutes = 5,
        ),
    )

    private fun fullService() = service(
        items = rows(),
        planned = mapOf("a" to 300, "m" to 90, "t" to 300),
        timing = mapOf(
            "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT),
            "m" to RowTiming(followsPrevious = true, atEnd = RowEnd.BLANK),
            "t" to RowTiming(repeats = 0),
        ),
    )

    @Test
    fun `every kind of row is drawn with what it is`() = withCalendar(documentWith(fullService())) {
        awaitText("Amazing Grace")

        assertTrue(shows("Welcome"), "a clip")
        assertTrue(shows("Timer"), "a timer")
        assertTrue(shows("Pre-Service"), "and the heading over them")
    }

    @Test
    fun `a row says when it starts and how long it runs`() = withCalendar(documentWith(fullService())) {
        awaitText("Amazing Grace")

        assertTrue(shows("5:00"), "its planned length")
        assertTrue(shows("1:30"), "and the clip's")
    }

    @Test
    fun `a row that waits its turn says so`() = withCalendar(documentWith(fullService())) {
        awaitText("Amazing Grace")

        assertTrue(shows("After previous"))
    }

    @Test
    fun `a looping row says it loops`() = withCalendar(documentWith(fullService())) {
        awaitText("Amazing Grace")

        assertTrue(shows("Loop"))
    }

    @Test
    fun `what a row does at its end is on the row`() = withCalendar(documentWith(fullService())) {
        awaitText("Amazing Grace")

        assertTrue(shows("Next"), "hands on")
        assertTrue(shows("Blank"), "or clears")
    }

    @Test
    fun `a row can be removed`() = withCalendar(documentWith(fullService())) { folder ->
        awaitText("Amazing Grace")

        clickIcon("Remove from the run of show")
        waitForIdle()

        assertTrue(stored(folder).services.single().items.size < 4, "one fewer than it started with")
    }

    @Test
    fun `a row can be moved`() = withCalendar(documentWith(fullService())) { folder ->
        awaitText("Amazing Grace")

        clickIcon("Move down")
        waitForIdle()

        assertEquals(
            listOf("h", "m", "a", "t"),
            stored(folder).services.single().items.map { it.id },
            "the first content row swapped with the one under it",
        )
    }

    @Test
    fun `a heading can be taken out without touching the rows under it`() =
        withCalendar(documentWith(fullService())) { folder ->
            awaitText("Pre-Service")

            // A heading is removed with the same control as any other row.
            clickIcon("Remove from the run of show")
            waitForIdle()

            val left = stored(folder).services.single().items
            assertTrue(left.none { it is ScheduleItem.LabelItem })
            assertTrue(left.any { it.id == "a" }, "the songs stay")
        }

    @Test
    fun `the footer says what the service will do on its own`() = withCalendar(documentWith(fullService())) {
        awaitText("Amazing Grace")

        assertTrue(shows("Sunday Morning"), "which service is loaded from here")
    }

    @Test
    fun `an empty run of show asks for something to be added`() = withCalendar(
        documentWith(service(items = emptyList(), planned = emptyMap()))
    ) {
        awaitText("Sunday Morning")

        assertTrue(shows("Add song, verse or section"), "an empty list still offers the one thing to do")
    }
}
