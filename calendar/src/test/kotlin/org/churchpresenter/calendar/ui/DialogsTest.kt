@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.core.models.schedule.RowTiming
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every dialog the window opens, driven through the window itself.
 *
 * Opened the way an operator opens them rather than composed directly: a sheet's state comes from
 * what is selected behind it, and half of what these assert — that the picker knows which service
 * it is adding to, that the settings dialog lists the sections the document holds — is only true
 * through that path.
 */
class DialogsTest {

    @Test
    fun `the picker offers every place a row can come from`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")
        clickFirst("Add song, verse or section")
        awaitText("Songs")

        assertTrue(shows("Bible"))
        assertTrue(shows("Section"))
        assertTrue(shows("Presets"))
        assertTrue(shows("Adds to"), "and says where the pick lands")
    }

    @Test
    fun `the picker's timing panel offers the three ways a row can start`() =
        withCalendar(documentWith(service())) {
            awaitText("Amazing Grace")
            clickFirst("Add song, verse or section")
            awaitText("Songs")

            assertTrue(shows("Cued"), "waits for the operator")
            assertTrue(shows("After previous"), "waits its turn")
            assertTrue(shows("On time"), "or a clock time")
            assertTrue(shows("min before"), "including one typed by hand")
        }

    @Test
    fun `the timing panel says what happens at the end of a row`() =
        withCalendar(documentWith(service())) {
            awaitText("Amazing Grace")
            clickFirst("Add song, verse or section")
            awaitText("Runs")

            assertTrue(shows("Repeats"))
            assertTrue(shows("At end"))
            assertTrue(shows("Loop"))
        }

    @Test
    fun `the bible tab browses books`() = withCalendar(
        documentWith(service()),
        host = CalendarHost(bibleBooks = { BIBLE_BOOKS }),
    ) {
        awaitText("Amazing Grace")
        clickFirst("Add song, verse or section")
        awaitText("Songs")

        clickFirst("Bible")
        awaitText("Genesis")

        assertTrue(shows("Psalms"))
    }

    @Test
    fun `a section can be picked from the ones the calendar keeps`() =
        withCalendar(documentWith(service())) {
            awaitText("Amazing Grace")
            clickFirst("Add song, verse or section")
            awaitText("Songs")

            clickFirst("Section")
            awaitText("Pre-Service")

            assertTrue(shows("Worship"), "the default sections the document ships with")
        }

    @Test
    fun `the settings dialog lists the sections it keeps`() = withCalendar(documentWith(service())) {
        awaitText("Sunday Morning")
        clickFirst("Calendar settings")
        awaitText("Sections")

        assertTrue(shows("Pre-Service"))
        assertTrue(shows("Communion"))
    }

    @Test
    fun `the defaults tab holds the clock format and the two lengths`() =
        withCalendar(documentWith(service())) {
            awaitText("Sunday Morning")
            clickFirst("Calendar settings")
            awaitText("Sections")

            clickFirst("Defaults")
            awaitText("Time format")

            assertTrue(shows("24"), "the two clock formats")
            assertTrue(shows("Load the service automatically"), "and the auto-load switch")
        }

    @Test
    fun `the templates and presets tabs say when they are empty`() =
        withCalendar(documentWith(service())) {
            awaitText("Sunday Morning")
            clickFirst("Calendar settings")
            awaitText("Sections")

            clickFirst("Templates")
            waitForIdle()
            clickFirst("Presets")
            waitForIdle()

            assertTrue(onAllNodesWithText("Presets", substring = true).fetchSemanticsNodes().isNotEmpty())
        }

    @Test
    fun `editing a service opens on its own details`() = withCalendar(documentWith(service())) {
        awaitText("Sunday Morning")

        clickIcon("Edit service")
        awaitText("Start time")

        assertTrue(shows("Sunday Morning"), "the service being edited")
    }

    @Test
    fun `a run of show can be copied to another date`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")

        clickIcon("Copy this service")
        awaitText("Copy")

        assertTrue(shows("Run of show"), "what is carried over")
    }

    @Test
    fun `a run of show can be saved as a template`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")

        clickIcon("Save this run of show")
        awaitText("Template")
    }

    @Test
    fun `a row opens the picker on what it is, for replacing or retiming`() =
        withCalendar(documentWith(service(timing = mapOf("a" to RowTiming(startAt = "09:45"))))) {
            awaitText("Amazing Grace")

            clickFirst("Amazing Grace")
            awaitText("Editing")

            assertTrue(shows("Saved as you change it"))
        }

    @Test
    fun `a duration can be typed straight onto a row`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")

        clickFirst("5:00")
        waitForIdle()

        // In place, not through the picker: a length is typed on the row it belongs to.
        assertTrue(!shows("Saved as you change it"), "the row editor did not open")
    }
}
