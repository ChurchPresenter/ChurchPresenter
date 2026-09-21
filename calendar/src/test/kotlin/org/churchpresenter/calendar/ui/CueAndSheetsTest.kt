@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cue rows in the run of show, and the two sheets a whole service goes through. */
class CueAndSheetsTest {

    private fun cue(id: String = "c", action: String = CueAction.BLANK, at: String = "09:45") =
        ScheduleItem.CueItem(id = id, action = action, absoluteTime = at, label = "Blank the screen")

    private fun serviceWithCue() = service(
        items = listOf(heading("h"), song("a", "Amazing Grace"), cue()),
        timing = mapOf("a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT)),
    )

    @Test
    fun `a cue is a row of the run of show, with its time and what it does`() =
        withCalendar(documentWith(serviceWithCue())) {
            awaitText("Amazing Grace")

            assertTrue(shows("Blank the screen"), "its label")
            assertTrue(shows("09:45") || shows("9:45"), "and when it fires")
        }

    @Test
    fun `a cue can be fired by hand`() {
        var blanked = 0
        val host = CalendarHost(blankOutputs = { blanked++ })

        withCalendar(documentWith(serviceWithCue()), host = host) {
            awaitText("Blank the screen")

            clickIcon("Fire this cue now")

            assertEquals(1, blanked, "the same path the engine fires through")
        }
    }

    @Test
    fun `a cue can be skipped for today without deleting it`() =
        withCalendar(documentWith(serviceWithCue())) {
            awaitText("Blank the screen")

            clickIcon("Skip this cue")
            waitForIdle()

            assertTrue(shows("Blank the screen"), "still planned, just not firing")
        }

    @Test
    fun `the arm switch says whether anything will fire`() =
        withCalendar(documentWith(serviceWithCue())) {
            awaitText("Amazing Grace")

            assertTrue(shows("Armed"))
        }

    @Test
    fun `the header says what the service will do on its own`() =
        withCalendar(documentWith(serviceWithCue())) {
            awaitText("Amazing Grace")

            assertTrue(shows("auto start") || shows("auto starts"), "how many rows run themselves")
        }

    @Test
    fun `laying out the times is one press`() =
        withCalendar(documentWith(serviceWithCue())) {
            awaitText("Amazing Grace")

            clickIcon("Time every row from the first")
            waitForIdle()

            assertTrue(shows("Amazing Grace"), "the run of show survives being timed")
        }

    @Test
    fun `the copy sheet offers what to carry over and where to put it`() =
        withCalendar(documentWith(serviceWithCue())) {
            awaitText("Amazing Grace")

            clickIcon("Copy this service")
            awaitText("Copy")

            assertTrue(shows("Run of show"))
            assertTrue(shows("Automation cues"), "the cues are a choice of their own")
        }

    @Test
    fun `the template sheet offers the same choices, for keeping`() =
        withCalendar(documentWith(serviceWithCue())) {
            awaitText("Amazing Grace")

            clickIcon("Save this run of show")
            awaitText("Template")

            assertTrue(shows("Automation cues"))
        }

    @Test
    fun `the clock chip steps the service's own time`() =
        withCalendar(documentWith(serviceWithCue())) {
            awaitText("Amazing Grace")

            clickIcon("Run clock")
            waitForIdle()

            // A stepped clock is a preview, and offers its way back -- the × beside the time.
            assertTrue(
                onAllNodesWithContentDescription("Back to the real time", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
                "stepping the clock offers a way back to the real one",
            )
        }
}
