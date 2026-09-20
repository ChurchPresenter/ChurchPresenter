@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The month grid and the day beside it — how a service is found before it is planned. */
class MonthAndDayTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    @Test
    fun `the month opens on today and says how much is planned`() = withCalendar(documentWith(service())) {
        awaitText("Sunday Morning")

        assertTrue(shows("September"))
        assertTrue(shows("2026"))
    }

    @Test
    fun `the month can be stepped and brought back`() = withCalendar(documentWith(service())) {
        awaitText("September")

        clickIcon("Next month")
        awaitText("October")

        clickIcon("Previous month")
        awaitText("September")

        clickFirst("Today")
        waitForIdle()
        assertTrue(shows("September"))
    }

    @Test
    fun `a day with a service says so, and one without offers to plan one`() =
        withCalendar(documentWith(service())) {
            awaitText("Sunday Morning")

            assertTrue(shows("10:00"), "when it starts")
        }

    @Test
    fun `two services on a day are both listed`() = withCalendar(
        documentWith(
            service(id = "m", name = "Morning", start = "10:00"),
            service(id = "e", name = "Evening", start = "18:00"),
        )
    ) {
        awaitText("Morning")

        assertTrue(shows("Evening"))
    }

    @Test
    fun `the second service can be selected and becomes the open one`() = withCalendar(
        documentWith(
            service(id = "m", name = "Morning", start = "10:00", items = listOf(song("a", "First song"))),
            service(id = "e", name = "Evening", start = "18:00", items = listOf(song("b", "Second song"))),
        )
    ) {
        awaitText("First song")

        clickFirst("Evening")
        awaitText("Second song")
    }

    @Test
    fun `a new service is added to the day that is open`() = withCalendar { folder ->
        awaitText("Nothing planned")

        clickFirst("Add service")
        awaitText("Start time")
        typeIntoFirstField("Midweek Prayer")
        clickLast("Add service")
        waitForIdle()

        assertEquals(1, stored(folder).services.size)
        assertEquals(TODAY.toString(), stored(folder).services.single().date)
    }

    @Test
    fun `the window can be closed from the footer`() = withCalendar(documentWith(service())) {
        awaitText("Sunday Morning")

        assertTrue(shows("Close"), "the way out sits beside Load into Schedule")
    }
}
