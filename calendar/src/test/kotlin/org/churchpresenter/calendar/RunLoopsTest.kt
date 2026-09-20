package org.churchpresenter.calendar

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The two loops the app starts once and leaves running, driven on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class RunLoopsTest {

    private val today = LocalDate.of(2026, 9, 20)

    private fun at(hour: Int, minute: Int, second: Int = 0, day: LocalDate = today) =
        LocalDateTime.of(day, LocalTime.of(hour, minute, second))

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song $id", "", "")

    private class Outputs {
        val done = mutableListOf<String>()
        var loads = 0
        var rows: List<ScheduleItem> = emptyList()

        fun host(): CalendarHost = CalendarHost(
            projectItem = { item, plays -> done += "project:${item.id}x$plays" },
            blankOutputs = { done += "blank" },
            loadIntoSchedule = { items, _, _, _, _ ->
                loads++
                rows = items
            },
            currentSchedule = { rows },
        )
    }

    // ── CueRunner ───────────────────────────────────────────────────────────────

    @Test
    fun `the engine keeps ticking and a row fires once however many ticks pass`() = runTest {
        val outputs = Outputs()
        val runner = CueRunner(
            items = { listOf(song("a")) },
            armed = { true },
            host = outputs.host(),
            timing = { mapOf("a" to RowTiming(startAt = "10:00")) },
            now = { at(10, 0, 30) },
        )

        val job = launch { runner.run(tickMillis = 1_000) }
        advanceTimeBy(3_500)
        job.cancel()

        assertEquals(listOf("project:ax1"), outputs.done)
    }

    @Test
    fun `an engine built with nothing but a host ticks against the clock and fires nothing`() {
        val outputs = Outputs()
        val runner = CueRunner(items = { emptyList() }, armed = { true }, host = outputs.host())

        runner.tick()

        assertTrue(outputs.done.isEmpty())
    }

    @Test
    fun `a cue row fires from the engine without reloading the schedule`() {
        val outputs = Outputs()
        val cue = ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, absoluteTime = "10:00")
        val runner = CueRunner(
            items = { listOf(song("a"), cue) }, armed = { true }, host = outputs.host(), now = { at(10, 0, 20) },
        )

        runner.tick()

        assertEquals(listOf("blank"), outputs.done)
        assertEquals(0, outputs.loads)
    }

    @Test
    fun `a new day lets the same row fire again`() {
        val outputs = Outputs()
        var now = at(10, 0, 30)
        val runner = CueRunner(
            items = { listOf(song("a")) },
            armed = { true },
            host = outputs.host(),
            timing = { mapOf("a" to RowTiming(startAt = "10:00")) },
            now = { now },
        )

        runner.tick()
        now = at(10, 0, 30, day = today.plusDays(1))
        runner.tick()

        assertEquals(listOf("project:ax1", "project:ax1"), outputs.done)
    }

    @Test
    fun `an end action this version does not know does nothing`() {
        val outputs = Outputs()
        var now = at(10, 0, 0)
        val runner = CueRunner(
            items = { listOf(song("a"), song("b")) },
            armed = { true },
            host = outputs.host(),
            timing = { mapOf("a" to RowTiming(startAt = "10:00", runSeconds = 10, atEnd = "someday")) },
            now = { now },
        )

        runner.tick()
        now = at(10, 0, 30)
        runner.tick()

        assertEquals(listOf("project:ax1"), outputs.done)
    }

    @Test
    fun `an item finishing when nothing is waiting on it changes nothing`() {
        val outputs = Outputs()
        val runner = CueRunner(items = { listOf(song("a")) }, armed = { true }, host = outputs.host())

        runner.liveItemFinished()

        assertTrue(outputs.done.isEmpty())
    }

    @Test
    fun `an item finishing after another row went live is ignored`() {
        val outputs = Outputs()
        var now = at(10, 0, 30)
        val runner = CueRunner(
            items = { listOf(song("a"), song("b"), song("c")) },
            armed = { true },
            host = outputs.host(),
            timing = {
                mapOf(
                    "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT),
                    "b" to RowTiming(startAt = "10:01"),
                )
            },
            now = { now },
        )
        runner.tick()
        now = at(10, 1, 30)
        runner.tick()

        runner.liveItemFinished()

        assertEquals(listOf("project:ax1", "project:bx1"), outputs.done)
    }

    // ── ServiceAutoLoader ───────────────────────────────────────────────────────

    private fun planned(id: String = "s", start: String = "10:00", rows: List<ScheduleItem> = listOf(song("$id-1"))) =
        PlannedService(
            id = id, date = today.toString(), name = "Service", startTime = start, items = rows,
            plannedSeconds = rows.associate { it.id to 600 },
        )

    private fun calendar(vararg services: PlannedService) = CalendarDocument(
        preferences = CalendarPreferences(autoLoadService = true),
        services = services.toList(),
    )

    @Test
    fun `the loader waits for the schedule to be ready, then loads and keeps checking`() = runTest {
        val outputs = Outputs()
        val loader = ServiceAutoLoader(document = { calendar(planned()) }, host = outputs.host(), now = { at(9, 57) })

        val job = launch { loader.run(tickMillis = 1_000, startupMillis = 100) }
        advanceTimeBy(50)
        assertEquals(0, outputs.loads, "not before the start-up lead")
        advanceTimeBy(3_000)
        job.cancel()

        assertEquals(1, outputs.loads, "loaded once, then seen to be there")
    }

    @Test
    fun `a tick that throws does not end the loop`() = runTest {
        val outputs = Outputs()
        var calls = 0
        val loader = ServiceAutoLoader(
            document = { if (calls++ == 0) error("folder not mounted") else calendar(planned()) },
            host = outputs.host(),
            now = { at(9, 57) },
        )

        val job = launch { loader.run(tickMillis = 1_000, startupMillis = 0) }
        advanceTimeBy(2_500)
        job.cancel()

        assertEquals(1, outputs.loads)
    }

    @Test
    fun `a loader built without a clock reads the real one`() = runTest {
        val outputs = Outputs()
        val loader = ServiceAutoLoader(document = { calendar() }, host = outputs.host())

        loader.tick()

        assertEquals(0, outputs.loads)
    }

    @Test
    fun `a service with no rows is never seen as loaded`() = runTest {
        val outputs = Outputs()
        val loader = ServiceAutoLoader(
            document = { calendar(planned(rows = emptyList())) }, host = outputs.host(), now = { at(9, 57) },
        )

        loader.tick()
        loader.tick()

        assertEquals(2, outputs.loads, "nothing of it can be found in the schedule, so it is asked for again")
    }
}
