@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the window itself does, as opposed to what its panes and sheets do.
 *
 * These are the paths that reach the host — loading into the Schedule, exporting a PDF, firing a
 * cue by hand — and the ones that only exist when something has gone wrong, like the banner shown
 * after the calendar was read from a backup. Each is a branch of `CalendarApp` that nothing else
 * covers, because nothing else can: they are about the window, not about a row.
 */
class AppPathsDeepTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    // ── Loading into the Schedule ───────────────────────────────────────────────────────────────

    @Test
    fun `loading into an empty schedule asks nothing and just loads`() {
        val loaded = mutableListOf<Boolean>()
        val host = CalendarHost(
            loadIntoSchedule = { _, _, replace, _, _ -> loaded += replace },
            currentSchedule = { emptyList() },
        )

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")

            clickFirst("Load into Schedule")

            assertEquals(listOf(true), loaded, "nothing to lose, so nothing to confirm")
        }
    }

    @Test
    fun `loading over a schedule in use offers to replace it or add to it`() {
        val loaded = mutableListOf<Boolean>()
        val host = CalendarHost(
            loadIntoSchedule = { _, _, replace, _, _ -> loaded += replace },
            currentSchedule = { List(3) { song("live$it", "Already live") } },
        )

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")

            clickFirst("Load into Schedule")
            awaitText("Replace the schedule?")
            assertTrue(shows("3"), "and says how much would be discarded")

            clickFirst("Add to the end")

            assertEquals(listOf(false), loaded, "appending is the other answer")
        }
    }

    @Test
    fun `replacing is the other answer`() {
        val loaded = mutableListOf<Boolean>()
        val host = CalendarHost(
            loadIntoSchedule = { _, _, replace, _, _ -> loaded += replace },
            currentSchedule = { List(2) { song("live$it", "Already live") } },
        )

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")
            clickFirst("Load into Schedule")
            awaitText("Replace the schedule?")

            clickFirst("Replace and load")

            assertEquals(listOf(true), loaded)
        }
    }

    @Test
    fun `the rows handed over are pinned to the clock, with their timing beside them`() {
        var rows: List<ScheduleItem>? = null
        var timing: Map<String, RowTiming>? = null
        val host = CalendarHost(
            loadIntoSchedule = { items, rowTiming, _, _, _ -> rows = items; timing = rowTiming },
            currentSchedule = { emptyList() },
        )
        val plan = service(
            items = listOf(
                song("a", "Amazing Grace"),
                ScheduleItem.CueItem(id = "cue", action = CueAction.BLANK, offsetMinutes = -15),
            ),
            timing = mapOf("a" to RowTiming(startAt = "10:00")),
        )

        withCalendar(documentWith(plan), host = host) {
            awaitText("Amazing Grace")

            clickFirst("Load into Schedule")

            val cue = rows?.filterIsInstance<ScheduleItem.CueItem>()?.single()
            assertEquals("09:45", cue?.absoluteTime, "a relative cue is pinned as it is loaded")
            assertEquals("10:00", timing?.get("a")?.startAt, "and the timing goes with the rows")
        }
    }

    // ── Exporting ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a run of show can be exported as a pdf`() {
        val folder = Files.createTempDirectory("calendar-export").toFile()
        try {
            val target = File(folder, "run-of-show.pdf")
            val host = CalendarHost(chooseExportFile = { _, _ -> target })

            withCalendar(documentWith(service()), host = host) {
                awaitText("Amazing Grace")

                clickFirst("Export PDF")
                waitUntil("the file had been written") { target.isFile && target.length() > 0 }

                assertTrue(target.readBytes().take(4).toByteArray().decodeToString() == "%PDF", "a real PDF")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `an export nobody chose a file for writes nothing`() {
        var asked = false
        val host = CalendarHost(chooseExportFile = { _, _ -> asked = true; null })

        withCalendar(documentWith(service()), host = host) {
            awaitText("Amazing Grace")

            clickFirst("Export PDF")

            assertTrue(asked, "the chooser was opened")
        }
    }

    // ── Recovery ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a calendar read from a backup says so, and the note can be dismissed`() {
        val folder = Files.createTempDirectory("calendar-recovered").toFile()
        try {
            File(folder, "calendar.json").writeText("{ not json")
            File(folder, "calendar.json.bak1").writeText(
                kotlinx.serialization.json.Json { encodeDefaults = true }
                    .encodeToString(
                        org.churchpresenter.calendar.model.CalendarDocument.serializer(),
                        documentWith(service()),
                    )
            )

            withCalendarFolder(folder) {
                awaitText("Recovered from a backup")
                assertTrue(shows("kept beside it"), "and says the unreadable file was not thrown away")

                clickFirst("Dismiss")

                assertTrue(!shows("Recovered from a backup"), "once seen, it goes")
                assertTrue(shows("Amazing Grace"), "and the recovered plan is there")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    // ── Automation, from the window ─────────────────────────────────────────────────────────────

    @Test
    fun `a cue fired by hand reaches the host and raises the toast`() {
        val done = mutableListOf<String>()
        val host = CalendarHost(blankOutputs = { done += "blank" })
        val plan = service(
            items = listOf(ScheduleItem.CueItem(id = "cue", action = CueAction.BLANK, absoluteTime = "10:30")),
            planned = emptyMap(),
        )

        withCalendar(documentWith(plan), host = host) {
            awaitText("Sunday Morning")

            clickIcon("Fire this cue now")

            assertEquals(listOf("blank"), done)
            assertTrue(shows("Cue fired"), "and the window says it happened")
        }
    }

    @Test
    fun `arming is per service and is saved`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")
        assertTrue(stored(folder).services.single().armed, "a new service is armed")

        clickIcon("Arm or disarm every cue")

        assertTrue(!stored(folder).services.single().armed)
    }

    @Test
    fun `laying out the times pins every row from the first`() = withCalendar(
        documentWith(
            service(
                items = listOf(song("a", "Amazing Grace"), song("b", "Be Thou My Vision")),
                planned = mapOf("a" to 300, "b" to 240),
            )
        )
    ) { folder ->
        awaitText("Be Thou My Vision")

        clickIcon("Time every row from the first")

        val timing = stored(folder).services.single().timing
        assertEquals("10:00", timing["a"]?.startAt, "the first row keeps the service's own time")
        assertEquals("10:05", timing["b"]?.startAt, "and the next starts when it ends")
    }
}
