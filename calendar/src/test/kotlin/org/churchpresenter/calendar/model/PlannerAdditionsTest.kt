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
        val single = typed.copy(chapter = 3, verseNumber = 16, verseRange = "")
        assertEquals("Псалтирь 3:16", single.withBook(19, "Псалтирь").displayText)
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
        val statuses = cueStatuses(
            listOf(cue, later), "10:00", armed = true, now = LocalTime.of(10, 10), skippedIds = setOf("c"),
        )
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

    // ── Off-screen rows ─────────────────────────────────────────────────────────────────────────

    private fun poem(id: String) = ScheduleItem.MinistryItem(id, "A poem", "Anna")

    @Test
    fun `an off-screen row is planned time the Schedule never receives`() {
        val service = PlannedService(
            "svc", "2026-09-20", "Sunday", "10:00",
            items = listOf(song("a"), poem("p"), song("b"), poem("q"), poem("r"), song("c")),
            plannedSeconds = mapOf("a" to 300, "p" to 180, "b" to 300, "q" to 60, "r" to 60),
        )
        assertEquals(listOf("a", "b", "c"), service.rowsForSchedule().map { it.id })

        val timing = service.timingForSchedule()
        assertEquals(0, timing.getValue("a").leadSeconds)
        assertEquals(180, timing.getValue("b").leadSeconds, "the poem's three minutes sit before b")
        assertEquals(120, timing.getValue("c").leadSeconds, "and two short slots add up before c")
        assertEquals(300, timing.getValue("b").runSeconds)

        // The calendar's own clock counts it as a row; the Schedule's clock counts it as a lead.
        val planned = runClocks(service)
        val loaded = scheduleClocks(service.rowsForSchedule(), timing, service.startTime)
        assertEquals(planned.getValue("b").time, loaded.getValue("b").time)
        assertEquals(planned.getValue("c").time, loaded.getValue("c").time)
        assertEquals(LocalTime.of(10, 8), loaded.getValue("b").time)
    }

    @Test
    fun `a pinned row is not moved by the lead before it`() {
        val rows = listOf(song("a"), song("b"))
        val timing = mapOf("b" to RowTiming(startAt = "10:30", leadSeconds = 600))
        assertEquals(LocalTime.of(10, 30), scheduleClocks(rows, timing, "10:00").getValue("b").time)
    }

    @Test
    fun `an off-screen row is content, has a look, and cannot be fired by a cue`() {
        val service = PlannedService("svc", "2026-09-20", "Sunday", "10:00", items = listOf(poem("p"), song("a")))
        assertEquals(2, service.contentItems().size)
        assertFalse(poem("p").isProjectableByCue())
        val copy = poem("p").withNewId()
        assertTrue(copy is ScheduleItem.MinistryItem && copy.id != "p" && copy.title == "A poem")
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
