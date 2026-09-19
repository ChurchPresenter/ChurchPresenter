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
}
