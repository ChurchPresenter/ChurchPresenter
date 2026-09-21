@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Copying a service: to one date, or as a series, and what travels with it. */
class CopyOptionsTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun openCopy() = documentWith(service())

    @Test
    fun `the sheet offers the dates and the repeats`() = withCalendar(openCopy()) {
        awaitText("Amazing Grace")
        clickIcon("Copy this service")
        awaitText("Paste on")

        assertTrue(shows("Next week"))
        assertTrue(shows("In 2 weeks"))
        assertTrue(shows("Next month"))
        assertTrue(shows("Tomorrow"))
        assertTrue(shows("Weekly"))
        assertTrue(shows("Monthly"))
    }

    @Test
    fun `a weekly series creates as many services as asked for`() = withCalendar(openCopy()) { folder ->
        awaitText("Amazing Grace")
        clickIcon("Copy this service")
        awaitText("Paste on")

        clickFirst("Weekly")
        waitForIdle()
        clickLast("Create")
        waitUntil("the sheet closed") { !shows("Paste on") }

        assertTrue(stored(folder).services.size > 1, "a series is more than the one it came from")
    }

    @Test
    fun `a copy lands on the day that was picked`() = withCalendar(openCopy()) { folder ->
        awaitText("Amazing Grace")
        clickIcon("Copy this service")
        awaitText("Paste on")

        clickFirst("Tomorrow")
        waitForIdle()
        clickLast("Paste")
        waitUntil("the sheet closed") { !shows("Paste on") }

        val copies = stored(folder).services.filter { it.date != TODAY.toString() }
        assertEquals(1, copies.size, "one copy, on the day that was picked")
        assertEquals(TODAY.plusDays(1).toString(), copies.single().date)
    }

    @Test
    fun `the sheet counts what pressing the button will do`() = withCalendar(
        documentWith(
            service(),
            service(id = "next", name = "Next week", date = TODAY.plusDays(7)),
        )
    ) {
        awaitText("Amazing Grace")
        clickIcon("Copy this service")
        awaitText("Paste on")

        clickFirst("Tomorrow")
        waitForIdle()

        assertTrue(shows("Paste"), "one date is a paste, not a series")
    }

    @Test
    fun `the template sheet says what it will keep`() = withCalendar(openCopy()) {
        awaitText("Amazing Grace")
        clickIcon("Save this run of show")
        awaitText("Template")

        assertTrue(shows("Sections"), "the headings")
        assertTrue(shows("Items"), "the rows")
        assertTrue(shows("Automation cues"), "and the cues, each its own choice")
    }
}
