package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceMoveTest {

    private fun song(id: String) = ScheduleItem.SongItem(
        id = id, songNumber = 1, title = "Song", songbook = "", songId = "",
    )

    private fun cue(id: String, absoluteTime: String) = ScheduleItem.CueItem(
        id = id, action = CueAction.BLANK, absoluteTime = absoluteTime,
    )

    private fun service(start: String, items: List<ScheduleItem>, timing: Map<String, RowTiming>) =
        PlannedService(
            id = "svc",
            date = "2026-09-20",
            name = "Sunday",
            startTime = start,
            items = items,
            timing = timing,
        )

    @Test
    fun `a later start carries every pinned row with it`() {
        val moved = service(
            start = "10:30",
            items = listOf(song("a"), song("b")),
            timing = mapOf(
                "a" to RowTiming(startAt = "09:40", atEnd = RowEnd.NEXT),
                "b" to RowTiming(startAt = "09:55"),
            ),
        ).withStartMovedFrom("10:00")

        assertEquals("10:10", moved.timingOf("a").startAt, "minus twenty stays minus twenty")
        assertEquals("10:25", moved.timingOf("b").startAt)
        assertEquals(RowEnd.NEXT, moved.timingOf("a").atEnd, "nothing else about the row changes")
    }

    @Test
    fun `an earlier start moves them back`() {
        val moved = service(
            start = "09:00",
            items = listOf(song("a")),
            timing = mapOf("a" to RowTiming(startAt = "09:45")),
        ).withStartMovedFrom("10:00")

        assertEquals("08:45", moved.timingOf("a").startAt)
    }

    @Test
    fun `a pinned cue travels too`() {
        val moved = service(
            start = "11:00",
            items = listOf(cue("c", "09:50")),
            timing = emptyMap(),
        ).withStartMovedFrom("10:00")

        assertEquals("10:50", (moved.items.first() as ScheduleItem.CueItem).absoluteTime)
    }

    @Test
    fun `a cued row is left alone`() {
        val moved = service(
            start = "11:00",
            items = listOf(song("a")),
            timing = mapOf("a" to RowTiming(startAt = "", repeats = 0)),
        ).withStartMovedFrom("10:00")

        assertEquals("", moved.timingOf("a").startAt)
        assertEquals(0, moved.timingOf("a").repeats)
    }

    @Test
    fun `no change and unreadable times leave everything where it is`() {
        val service = service(
            start = "10:00",
            items = listOf(song("a")),
            timing = mapOf("a" to RowTiming(startAt = "09:45")),
        )

        assertEquals("09:45", service.withStartMovedFrom("10:00").timingOf("a").startAt)
        assertEquals("09:45", service.withStartMovedFrom("nonsense").timingOf("a").startAt)
    }

    @Test
    fun `a row pushed past midnight wraps rather than throwing`() {
        val moved = service(
            start = "23:40",
            items = listOf(song("a")),
            timing = mapOf("a" to RowTiming(startAt = "23:50")),
        ).withStartMovedFrom("23:00")

        assertEquals("00:30", moved.timingOf("a").startAt, "forty minutes on from 23:50")
    }

    @Test
    fun `a shift is the difference between two times of day`() {
        // The model holds times of day, not dates: moving a late service to just after midnight
        // is a large step *backwards* on the clock, and every pinned row takes the same step.
        val moved = service(
            start = "00:30",
            items = listOf(song("a")),
            timing = mapOf("a" to RowTiming(startAt = "23:50")),
        ).withStartMovedFrom("23:00")

        assertEquals("01:20", moved.timingOf("a").startAt)
    }
}
