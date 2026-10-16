package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The small pure pieces every screen reads a plan through. */
class ModelBasicsTest {

    // ── Durations ───────────────────────────────────────────────────────────────

    @Test
    fun `a duration reads back as it was written`() {
        assertEquals("4:30", formatDuration(270))
        assertEquals("1:02:30", formatDuration(3750))
        assertEquals("—", formatDuration(null), "nothing planned is not zero")
        assertEquals(270, parseDuration("4:30"))
        assertEquals(3750, parseDuration("1:02:30"))
        assertEquals(1920, parseDuration("32"), "a bare number is minutes")
        assertNull(parseDuration("half an hour"))
    }

    // ── Clock text ──────────────────────────────────────────────────────────────

    @Test
    fun `a stored time is drawn in the format the operator chose`() {
        assertEquals("18:30", clockText("18:30", use24Hour = true))
        assertEquals("6:30 PM", clockText("18:30", use24Hour = false, locale = Locale.US))
        assertEquals("nonsense", clockText("nonsense", use24Hour = true), "unreadable text is left as it is")
    }

    @Test
    fun `a typed time is accepted in either format`() {
        assertEquals(LocalTime.of(18, 30), parseClockText("18:30"))
        assertEquals(LocalTime.of(18, 30), parseClockText("6:30 PM"))
        assertEquals(LocalTime.of(9, 5), parseStoredTime("9:05"), "an unpadded hour still parses")
        assertNull(parseStoredTime("25:00"))
        assertEquals("09:05", storedTime(LocalTime.of(9, 5)), "storage is always padded")
    }

    // ── Dates and the grid ──────────────────────────────────────────────────────

    @Test
    fun `a date reads back as it was stored`() {
        assertEquals("2026-09-20", storedDate(LocalDate.of(2026, 9, 20)))
        assertEquals(LocalDate.of(2026, 9, 20), parseStoredDate("2026-09-20"))
        assertNull(parseStoredDate("20/09/2026"))
    }

    @Test
    fun `the month grid covers whole weeks and holds the month`() {
        val grid = monthGrid(YearMonth.of(2026, 9), DayOfWeek.MONDAY)

        assertEquals(0, grid.size % 7, "whole weeks")
        assertTrue(grid.contains(LocalDate.of(2026, 9, 20)), "the month itself")
        assertEquals(DayOfWeek.MONDAY, grid.first().dayOfWeek, "starting on the locale's first day")
    }

    // ── References ──────────────────────────────────────────────────────────────

    @Test
    fun `a typed reference becomes a verse row`() {
        val parsed = parseReference("John 3:16-17")

        assertEquals("John", parsed?.bookName)
        assertEquals(3, parsed?.chapter)
        assertEquals(16, parsed?.firstVerse)
        assertEquals(17, parsed?.lastVerse)
    }

    @Test
    fun `text that is not a reference is not one`() {
        assertNull(parseReference("Amazing Grace"))
    }

    // ── Cues ────────────────────────────────────────────────────────────────────

    @Test
    fun `a relative cue fires against the service start`() {
        val cue = ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, offsetMinutes = -15)

        assertEquals(LocalTime.of(9, 45), cueFireTime(cue, "10:00"))
        assertNull(cueFireTime(cue, null), "with no start there is no time, rather than a wrong one")
    }

    @Test
    fun `pinning a cue writes its time down`() {
        val cue = ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, offsetMinutes = -15)

        assertEquals("09:45", cue.pinnedTo("10:00").absoluteTime)
    }

    @Test
    fun `what a cue can show is what the host can put on screen`() {
        assertFalse(ScheduleItem.LabelItem("l", "x", "#FFF", "#000").isProjectableByCue())
        assertTrue(ScheduleItem.SongItem("s", 1, "Song", "", "").isProjectableByCue())
    }

    // ── Timers ──────────────────────────────────────────────────────────────────

    @Test
    fun `a duration timer's length can be read and set`() {
        val timer = ScheduleItem.AnnouncementItem(
            id = "t", text = "", isTimer = true, timerMode = TimerModes.DURATION,
            timerMinutes = 5,
        )

        assertEquals(300, timer.timerSeconds())
        assertEquals(90, timer.withTimerSeconds(90).timerSeconds())
        assertTrue(timer.isDurationTimer())
    }

    @Test
    fun `a clock timer has no duration to report`() {
        val clock = ScheduleItem.AnnouncementItem(
            id = "t", text = "", isTimer = true, timerMode = TimerModes.CLOCK,
        )

        assertNull(clock.timerSeconds())
        assertFalse(clock.isDurationTimer())
    }

    // ── Rows into the schedule ──────────────────────────────────────────────────

    @Test
    fun `a planned length becomes the run length the engine measures`() {
        val service = PlannedService(
            id = "s", date = "2026-09-20", name = "Sunday", startTime = "10:00",
            items = listOf(ScheduleItem.SongItem("a", 1, "Song", "", "")),
            plannedSeconds = mapOf("a" to 300),
            timing = mapOf("a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT)),
        )

        assertEquals(300, service.timingForSchedule().getValue("a").runSeconds)
    }

    @Test
    fun `a copied row is re-keyed so two services cannot share estimates`() {
        val row = ScheduleItem.SongItem("a", 1, "Song", "", "")

        assertTrue(row.withNewId().id != row.id)
    }
}
