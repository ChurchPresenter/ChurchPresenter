package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The smaller additions around the planner: default lengths, resolved books, the schedule's anchor, skipped cues. */
class PlannerAdditionsTest {

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song", "Hymns", songId = "Hymns::1")
    private fun deck(id: String) = ScheduleItem.PresentationItem(id, "/d.pptx", "d", 3, "pptx")

    @Test
    fun `a new row's default length depends on what it is`() {
        val prefs = CalendarPreferences(defaultItemSeconds = 270, defaultSermonSeconds = 1920)
        assertEquals(270, prefs.defaultLengthFor(song("s")))
        assertEquals(1920, prefs.defaultLengthFor(deck("d")))
        assertNull(prefs.defaultLengthFor(ScheduleItem.BibleVerseItem("v", "John", 3, 16, "")))
    }

    @Test
    fun `a typed reference settles its book and keeps its verses`() {
        val typed = ScheduleItem.BibleVerseItem("v", "Psalm", 91, 1, "", verseRange = "1-4")
        val settled = typed.withBook(19, "Псалтирь")
        assertEquals(19, settled.bookId)
        assertEquals("Псалтирь 91:1-4", settled.displayText)
        assertEquals("v", settled.id)
        assertEquals("Псалтирь 3:16", typed.copy(chapter = 3, verseNumber = 16, verseRange = "").withBook(19, "Псалтирь").displayText)
    }

    @Test
    fun `the last verse is the end of the range, or the verse itself`() {
        val one = ScheduleItem.BibleVerseItem("v", "John", 3, 16, "")
        assertEquals(16, one.lastVerse())
        assertEquals(18, one.copy(verseRange = "16-18").lastVerse())
        assertEquals(20, one.copy(verseRange = "16,18,20").lastVerse())
    }

    @Test
    fun `a loaded schedule reckons from the service start when nothing is pinned`() {
        val rows = listOf(song("a"), song("b"))
        val timing = mapOf("a" to RowTiming(runSeconds = 300))
        assertTrue(scheduleClocks(rows, timing).isEmpty())
        val clocks = scheduleClocks(rows, timing, startTime = "10:00")
        assertEquals(LocalTime.of(10, 0), clocks["a"]?.time)
        assertEquals(LocalTime.of(10, 5), clocks["b"]?.time)
        // A pinned row still wins over the start.
        val pinned = scheduleClocks(rows, timing + ("a" to RowTiming(startAt = "10:30", runSeconds = 300)), "10:00")
        assertEquals(LocalTime.of(10, 30), pinned["a"]?.time)
        assertTrue(scheduleClocks(rows, timing, startTime = "not a time").isEmpty())
    }

    @Test
    fun `a skipped cue is past its time but not fired`() {
        val cue = ScheduleItem.CueItem("c", CueAction.BLANK, offsetMinutes = 5)
        val later = ScheduleItem.CueItem("d", CueAction.BLANK, offsetMinutes = 30)
        val statuses = cueStatuses(listOf(cue, later), "10:00", armed = true, now = LocalTime.of(10, 10), skippedIds = setOf("c"))
        val skipped = statuses.getValue("c")
        assertTrue(skipped.skipped)
        assertFalse(skipped.fired)
        assertTrue(statuses.getValue("d").isNext)
        // Only a cue whose time has come can have been skipped.
        val early = cueStatuses(listOf(cue), "10:00", armed = true, now = LocalTime.of(10, 1), skippedIds = setOf("c"))
        assertFalse(early.getValue("c").skipped)
        assertTrue(cueStatuses(listOf(cue), "10:00", true, LocalTime.of(10, 10)).getValue("c").fired)
    }

    @Test
    fun `a planned length becomes the run length only where none was set`() {
        val service = PlannedService(
            "svc", "2026-09-20", "Sunday", "10:00",
            items = listOf(song("a"), song("b"), song("c")),
            plannedSeconds = mapOf("a" to 300, "b" to 300),
            timing = mapOf("b" to RowTiming(startAt = "10:05", runSeconds = 120)),
        )
        val timing = service.timingForSchedule()
        assertEquals(300, timing["a"]?.runSeconds)
        assertEquals(120, timing["b"]?.runSeconds, "a typed run length is not overwritten by the estimate")
        assertNull(timing["c"], "nothing to say about a row with neither")
    }

    @Test
    fun `the preview clock starts before the service, or where it already is`() {
        assertEquals(LocalTime.of(9, 40), previewClockStart("10:00", showing = null))
        assertEquals(LocalTime.of(11, 5), previewClockStart("10:00", showing = LocalTime.of(11, 5)))
        assertEquals(LocalTime.NOON, previewClockStart("", showing = null, fallback = LocalTime.NOON))
    }

    @Test
    fun `a clock typed with a meridian is read either way`() {
        val en = java.util.Locale.ENGLISH
        assertEquals(LocalTime.of(0, 30), parseClockText("12:30 am", en))
        assertEquals(LocalTime.of(12, 30), parseClockText("12:30 PM", en))
        assertEquals(LocalTime.of(18, 5), parseClockText("6:05pm", en))
        assertNull(parseClockText("half past", en))
    }

    @Test
    fun `the schedule may be replaced only while it holds planned rows`() {
        val planned = PlannedService("svc", "2026-09-20", "Sunday", "10:00", items = listOf(song("a")))
        val template = SavedTemplate("t", "Template", "10:00", items = listOf(song("t1")))
        val calendar = CalendarDocument(services = listOf(planned), templates = listOf(template))
        assertTrue(calendar.holdsOnlyPlannedRows(emptyList()))
        assertTrue(calendar.holdsOnlyPlannedRows(listOf(song("a"), song("t1"))))
        assertFalse(calendar.holdsOnlyPlannedRows(listOf(song("a"), song("built-by-hand"))))
    }
}
