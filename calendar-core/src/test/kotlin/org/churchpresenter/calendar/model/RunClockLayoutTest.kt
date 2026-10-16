package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [withTimesLaidOut], [scheduleClocks] and [followsWithoutHandoff] — the three reckonings. */
class RunClockLayoutTest {

    private fun song(id: String) = ScheduleItem.SongItem(
        id = id, songNumber = 1, title = "Song", songbook = "", songId = "",
    )

    private fun heading(id: String) = ScheduleItem.LabelItem(
        id = id, text = "Worship", textColor = "#FFFFFF", backgroundColor = "#5B9DF5",
    )

    private fun service(
        items: List<ScheduleItem>,
        planned: Map<String, Int>,
        timing: Map<String, RowTiming>,
        start: String = "10:00",
    ) = PlannedService(
        id = "svc",
        date = "2026-09-20",
        name = "Sunday",
        startTime = start,
        items = items,
        plannedSeconds = planned,
        timing = timing,
    )

    @Test
    fun `lays every row out from the first pinned one`() {
        val laid = service(
            items = listOf(song("a"), song("b"), song("c")),
            planned = mapOf("a" to 300, "b" to 600, "c" to 60),
            timing = mapOf("a" to RowTiming(startAt = "09:40")),
        ).withTimesLaidOut()

        assertEquals("09:40", laid.timingOf("a").startAt, "the anchor keeps its own time")
        assertEquals("09:45", laid.timingOf("b").startAt)
        assertEquals("09:55", laid.timingOf("c").startAt)
    }

    @Test
    fun `a row played twice occupies twice its length`() {
        val laid = service(
            items = listOf(song("a"), song("b")),
            planned = mapOf("a" to 300),
            timing = mapOf("a" to RowTiming(startAt = "10:00", repeats = 2)),
        ).withTimesLaidOut()

        assertEquals("10:10", laid.timingOf("b").startAt)
    }

    @Test
    fun `it stops at the first row of unknown length`() {
        val laid = service(
            items = listOf(song("a"), song("b"), song("c")),
            planned = mapOf("a" to 300),
            timing = mapOf("a" to RowTiming(startAt = "10:00"), "c" to RowTiming(startAt = "11:11")),
        ).withTimesLaidOut()

        assertEquals("10:05", laid.timingOf("b").startAt, "the row itself is still placed")
        assertEquals("11:11", laid.timingOf("c").startAt, "everything after it is left alone")
    }

    @Test
    fun `a row waiting its turn is not pinned`() {
        val laid = service(
            items = listOf(song("a"), song("b"), song("c")),
            planned = mapOf("a" to 300, "b" to 300),
            timing = mapOf(
                "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT),
                "b" to RowTiming(followsPrevious = true),
            ),
        ).withTimesLaidOut()

        assertEquals("", laid.timingOf("b").startAt, "pinning it would take the hand-off away")
        assertTrue(laid.timingOf("b").followsPrevious)
        assertEquals("10:10", laid.timingOf("c").startAt, "its length still moves the clock on")
    }

    @Test
    fun `the schedule's own clock flows from the first pinned row`() {
        val clocks = scheduleClocks(
            items = listOf(song("a"), heading("h"), song("b"), song("c")),
            timing = mapOf(
                "a" to RowTiming(startAt = "10:00", runSeconds = 300),
                "b" to RowTiming(runSeconds = 600),
                "c" to RowTiming(runSeconds = 60),
            ),
        )

        assertEquals(LocalTime.of(10, 0), clocks["a"]?.time)
        assertEquals(LocalTime.of(10, 5), clocks["b"]?.time)
        assertEquals(LocalTime.of(10, 15), clocks["c"]?.time)
        assertEquals(null, clocks["h"], "a heading takes no time and gets no clock")
    }

    @Test
    fun `a row of unknown length makes every time after it an estimate`() {
        val clocks = scheduleClocks(
            items = listOf(song("a"), song("b"), song("c")),
            timing = mapOf("a" to RowTiming(startAt = "10:00"), "c" to RowTiming(runSeconds = 60)),
        )

        assertTrue(clocks.getValue("a").exact)
        assertEquals(false, clocks["b"]?.exact)
        assertEquals(LocalTime.of(10, 0), clocks["c"]?.time, "with nothing to add, the clock stands")
    }

    @Test
    fun `with nothing pinned there is nothing to reckon from`() {
        val clocks = scheduleClocks(
            items = listOf(song("a")),
            timing = mapOf("a" to RowTiming(runSeconds = 300)),
        )

        assertTrue(clocks.isEmpty())
    }

    @Test
    fun `a row waiting on a turn nothing gives is reported`() {
        val stranded = service(
            items = listOf(song("a"), song("b"), song("c")),
            planned = emptyMap(),
            timing = mapOf(
                // `a` holds at its end, so `b` is never handed to -- and `b` holds too, so
                // neither is `c`. Both are reported: each is waiting on a row that never advances.
                "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.HOLD),
                "b" to RowTiming(followsPrevious = true),
                "c" to RowTiming(followsPrevious = true, atEnd = RowEnd.NEXT),
            ),
        ).followsWithoutHandoff()

        assertEquals(setOf("b", "c"), stranded)
    }

    @Test
    fun `a row whose predecessor advances is not reported`() {
        val stranded = service(
            items = listOf(song("a"), song("b")),
            planned = emptyMap(),
            timing = mapOf(
                "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT),
                "b" to RowTiming(followsPrevious = true),
            ),
        ).followsWithoutHandoff()

        assertTrue(stranded.isEmpty())
    }

    @Test
    fun `the first row of a service has nothing to follow`() {
        val stranded = service(
            items = listOf(song("a")),
            planned = emptyMap(),
            timing = mapOf("a" to RowTiming(followsPrevious = true)),
        ).followsWithoutHandoff()

        assertEquals(setOf("a"), stranded)
    }

    // ── A loop before the service fills the time until it starts (#653) ──────────────────────────

    /** The issue's plan: a welcome loop pinned 45 minutes early, one pass 1:11, then the prelude. */
    private fun welcomeLoop() = service(
        items = listOf(heading("pre"), song("loop"), song("prelude"), song("prayer")),
        planned = mapOf("loop" to 71, "prelude" to 300, "prayer" to 120),
        timing = mapOf("loop" to RowTiming(startAt = "08:05", repeats = 0)),
        start = "08:50",
    )

    @Test
    fun `the row after a loop that starts before the service is at the service's start`() {
        val clocks = runClocks(welcomeLoop())

        assertEquals(LocalTime.of(8, 5), clocks.getValue("loop").time)
        assertEquals(LocalTime.of(8, 50), clocks.getValue("prelude").time, "not one pass after the loop")
        assertEquals(LocalTime.of(8, 55), clocks.getValue("prayer").time)
        assertTrue(clocks.getValue("prelude").exact)
    }

    @Test
    fun `a loop before the service needs no measured length to end at the start`() {
        val service = welcomeLoop().let { it.copy(plannedSeconds = it.plannedSeconds - "loop") }

        val prelude = runClocks(service).getValue("prelude")

        assertEquals(LocalTime.of(8, 50), prelude.time)
        assertTrue(prelude.exact, "the service's start is known, whatever one pass lasts")
    }

    @Test
    fun `a loop inside the service still takes its stated length`() {
        val service = service(
            items = listOf(song("a"), song("loop"), song("b")),
            planned = mapOf("a" to 300, "loop" to 120, "b" to 60),
            timing = mapOf("loop" to RowTiming(repeats = 0)),
        )

        assertEquals(LocalTime.of(10, 7), runClocks(service).getValue("b").time)
    }

    @Test
    fun `the loaded schedule and the laid-out times agree with the calendar`() {
        val service = welcomeLoop()
        val schedule = scheduleClocks(
            service.items,
            mapOf(
                "loop" to RowTiming(startAt = "08:05", repeats = 0, runSeconds = 71),
                "prelude" to RowTiming(runSeconds = 300),
            ),
            startTime = service.startTime,
        )
        assertEquals(LocalTime.of(8, 50), schedule.getValue("prelude").time)

        assertEquals("08:50", service.withTimesLaidOut().timingOf("prelude").startAt)
    }
}
