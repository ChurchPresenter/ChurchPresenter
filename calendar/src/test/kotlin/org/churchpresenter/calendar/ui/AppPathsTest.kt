@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CueFeed
import org.churchpresenter.calendar.FiredCue
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.nio.file.Files
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The window's own paths: exporting, loading over something, and saying what has fired. */
class AppPathsTest {

    @Test
    fun `the run of show can be exported as a pdf`() {
        val target = Files.createTempFile("run-of-show", ".pdf").toFile()
        try {
            val host = CalendarHost(chooseExportFile = { target }, pdfFont = { null })

            withCalendar(documentWith(service()), host = host) {
                awaitText("Amazing Grace")

                clickFirst("Export PDF")
                waitUntil("the export was written") { target.length() > 0 }

                assertTrue(target.length() > 0)
            }
        } finally {
            target.delete()
        }
    }

    @Test
    fun `an export nobody chose a file for writes nothing`() {
        var asked = false
        val host = CalendarHost(chooseExportFile = { asked = true; null })

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")

            clickFirst("Export PDF")
            waitForIdle()

            assertTrue(asked, "it asked; the operator said no, and that is the end of it")
        }
    }

    @Test
    fun `loading over a schedule that holds something asks first`() {
        val host = CalendarHost(
            currentSchedule = { listOf(ScheduleItem.SongItem("live", 1, "Already live", "", "")) },
        )

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")

            clickFirst("Load into Schedule")
            awaitText("Replace and load")

            assertTrue(shows("Add to the end"), "or add to what is there")
        }
    }

    @Test
    fun `replacing loads the service in place of what was there`() {
        val loads = mutableListOf<Boolean>()
        val host = CalendarHost(
            loadIntoSchedule = { _, _, replace, _, _ -> loads += replace },
            currentSchedule = { listOf(ScheduleItem.SongItem("live", 1, "Already live", "", "")) },
        )

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")
            clickFirst("Load into Schedule")
            awaitText("Replace and load")

            clickFirst("Replace and load")
            waitForIdle()

            assertEquals(listOf(true), loads)
        }
    }

    @Test
    fun `adding keeps what was already in the schedule`() {
        val loads = mutableListOf<Boolean>()
        val host = CalendarHost(
            loadIntoSchedule = { _, _, replace, _, _ -> loads += replace },
            currentSchedule = { listOf(ScheduleItem.SongItem("live", 1, "Already live", "", "")) },
        )

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")
            clickFirst("Load into Schedule")
            awaitText("Replace and load")

            clickLast("Add to the end")
            waitForIdle()

            assertEquals(listOf(false), loads)
        }
    }

    @Test
    fun `a fired cue is reported in the corner`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")

        CueFeed.post(
            FiredCue(
                row = ScheduleItem.CueItem(id = "c", action = "blank", label = "Blank the screen"),
                at = LocalTime.of(10, 5),
            )
        )
        waitForIdle()

        assertTrue(shows("Blank the screen") || shows("fired"), "the toast names what went off")
    }

    @Test
    fun `a recovered calendar says so`() {
        val folder = Files.createTempDirectory("calendar-recovered").toFile()
        try {
            val store = org.churchpresenter.calendar.CalendarStore(folder)
            store.save(documentWith(service()))
            store.save(documentWith(service()))
            store.file.writeText("{ not json")

            // Opened on the folder directly, because the banner is about what loading found.
            withCalendarFolder(folder) {
                awaitText("Amazing Grace")
                assertTrue(shows("Recovered from a backup"))
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a row pinned against the service start reads as an offset`() = withCalendar(
        documentWith(service(timing = mapOf("a" to RowTiming(startAt = "09:40"))))
    ) {
        awaitText("Amazing Grace")

        assertTrue(shows("20"), "twenty minutes before a ten o'clock service")
    }
}
