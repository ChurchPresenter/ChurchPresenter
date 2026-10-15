package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The branches of the model the screens rarely take: bad input, the odd shapes, the fallbacks. */
class ModelEdgesTest {

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song $id", "", "")

    private fun heading(id: String) = ScheduleItem.LabelItem(id, "Worship", "#FFFFFF", "#5B9DF5")

    private fun cue(id: String, offset: Int = 0, at: String = "") =
        ScheduleItem.CueItem(id = id, action = CueAction.BLANK, offsetMinutes = offset, absoluteTime = at)

    private fun service(
        start: String = "10:00",
        items: List<ScheduleItem> = emptyList(),
        planned: Map<String, Int> = emptyMap(),
        timing: Map<String, RowTiming> = emptyMap(),
    ) = PlannedService(
        id = "s", date = "2026-09-20", name = "Sunday", startTime = start,
        items = items, plannedSeconds = planned, timing = timing,
    )

    // ── References ──────────────────────────────────────────────────────────────

    @Test
    fun `a whole chapter, a verse and a range each read as typed`() {
        assertEquals("John 3", parseReference("John 3")!!.display)
        assertEquals("John 3:16", parseReference("John 3:16")!!.display)
        assertEquals("John 3:16-18", parseReference("John 3:16-18")!!.display)
        assertEquals("1 Corinthians 13", parseReference("1  Corinthians 13")!!.display, "spaces collapse")
    }

    @Test
    fun `a range written backwards is a typo, not a range`() {
        val parsed = parseReference("John 3:16-12")!!

        assertEquals(16, parsed.firstVerse)
        assertEquals(16, parsed.lastVerse)
        assertEquals("", parsed.verseRange)
    }

    @Test
    fun `a typed reference becomes a row that names the book rather than knowing it`() {
        val row = parseReference("Пс 23:1-6")!!.toScheduleItem()

        assertEquals(0, row.bookId)
        assertEquals("Пс", row.bookName)
        assertEquals(23, row.chapter)
        assertEquals(1, row.verseNumber)
        assertEquals("1-6", row.verseRange)
        assertEquals("Пс 23:1-6", row.displayText)
    }

    @Test
    fun `a verse picked from the grids carries its book id`() {
        val one = bibleVerseItem(bookId = 43, bookName = "John", chapter = 3, first = 16)
        val run = bibleVerseItem(bookId = 43, bookName = "John", chapter = 3, first = 18, last = 16)

        assertEquals("John 3:16", one.displayText)
        assertEquals("", one.verseRange)
        assertEquals(43, one.bookId)
        assertEquals("John 3:16-18", run.displayText, "picked in either order")
        assertEquals(16, run.verseNumber)
    }

    // ── Recurrence ──────────────────────────────────────────────────────────────

    @Test
    fun `a service repeats weekly, fortnightly or on the same weekday of the month`() {
        val start = LocalDate.of(2026, 9, 20) // the third Sunday

        val weekly = recurrenceDates(start, ServiceRepeat.WEEKLY, 2)
        val fortnightly = recurrenceDates(start, ServiceRepeat.BIWEEKLY, 1)
        val monthly = recurrenceDates(start, ServiceRepeat.MONTHLY, 1)

        assertEquals(listOf(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 4)), weekly)
        assertEquals(listOf(LocalDate.of(2026, 10, 4)), fortnightly)
        assertEquals(listOf(LocalDate.of(2026, 10, 18)), monthly, "third Sunday")
    }

    @Test
    fun `no repeat, no count and too many are all bounded`() {
        val start = LocalDate.of(2026, 9, 20)

        assertTrue(recurrenceDates(start, ServiceRepeat.NONE, 4).isEmpty())
        assertTrue(recurrenceDates(start, ServiceRepeat.WEEKLY, 0).isEmpty())
        assertEquals(MAX_REPEAT_COUNT, recurrenceDates(start, ServiceRepeat.WEEKLY, 500).size)
    }

    @Test
    fun `a fifth Sunday lands on the last day of a month without one`() {
        val fifth = LocalDate.of(2026, 8, 30)

        assertEquals(LocalDate.of(2026, 9, 30), recurrenceDates(fifth, ServiceRepeat.MONTHLY, 1).single())
    }

    @Test
    fun `the copy chips each name a date`() {
        val from = LocalDate.of(2026, 9, 20)

        assertEquals(LocalDate.of(2026, 9, 27), CopyTarget.NEXT_WEEK.date(from))
        assertEquals(LocalDate.of(2026, 10, 4), CopyTarget.IN_TWO_WEEKS.date(from))
        assertEquals(LocalDate.of(2026, 10, 18), CopyTarget.NEXT_MONTH.date(from))
        assertEquals(LocalDate.of(2026, 9, 21), CopyTarget.TOMORROW.date(from))
    }

    // ── Cues and their times ────────────────────────────────────────────────────

    @Test
    fun `a template's old cues become rows on the way in`() {
        val template = SavedTemplate(
            id = "t", name = "Standard", startTime = "10:00", items = listOf(song("a")),
            cues = listOf(ServiceCue(id = "c", offsetMinutes = -5)),
        )

        val opened = CalendarDocument(templates = listOf(template)).withCuesAsRows().templates.single()

        assertTrue(opened.cues.isEmpty())
        assertEquals(listOf("c", "a"), opened.items.map { it.id }, "before the service, so before the first row")
    }

    @Test
    fun `a template with no old cues is left alone`() {
        val template = SavedTemplate(id = "t", name = "Standard", startTime = "10:00", items = listOf(song("a")))

        assertEquals(template, CalendarDocument(templates = listOf(template)).withCuesAsRows().templates.single())
    }

    @Test
    fun `a cue with no time to place it by goes last`() {
        val placed = service(start = "soon", items = listOf(song("a"))).withCue(cue("c", offset = -5))

        assertEquals(listOf("a", "c"), placed.items.map { it.id })
    }

    @Test
    fun `a cue is placed after headings and before the row it precedes`() {
        val placed = service(
            items = listOf(heading("h"), song("a"), song("b")),
            planned = mapOf("a" to 600, "b" to 600),
        ).withCue(cue("c", at = "10:05"))

        assertEquals(listOf("h", "a", "c", "b"), placed.items.map { it.id })
    }

    @Test
    fun `a countdown needs a start to count to`() {
        assertNull(countdownItem(null))
        assertNull(countdownItem("later"))
        val timer = countdownItem("10:30")!!
        assertEquals(TimerModes.CLOCK, timer.timerMode)
        assertEquals(10, timer.targetHour)
        assertEquals(30, timer.targetMinute)
    }

    @Test
    fun `an unarmed service has no cue that is next or fired`() {
        val armedOff = service(items = listOf(cue("c", at = "10:00"))).copy(armed = false)

        assertTrue(armedOff.cueStatuses(LocalTime.of(9, 0)).isEmpty())
        assertEquals(0, armedOff.activeCueCount())
    }

    @Test
    fun `a cue that cannot be timed has no status`() {
        val relative = service(start = "soon", items = listOf(cue("c", offset = -5)))

        assertTrue(relative.cueStatuses(LocalTime.of(9, 0)).isEmpty())
    }

    @Test
    fun `a cue in the schedule fires only when pinned, on time and within grace`() {
        val now = LocalDateTime.of(2026, 9, 20, 10, 0, 30)
        val rows = listOf(
            cue("early", at = "09:00"), cue("late", at = "10:05"), cue("loose", offset = -5), cue("due", at = "10:00"),
        )

        val due = dueCues(rows, armed = true, now = now, fired = emptySet())
        val lenient = dueCues(rows, true, now, emptySet(), grace = Duration.ofHours(2))

        assertEquals(listOf("due"), due.map { it.id })
        assertEquals(listOf("early", "due"), lenient.map { it.id })
    }

    @Test
    fun `a row whose start does not parse is never due`() {
        val now = LocalDateTime.of(2026, 9, 20, 10, 0, 30)
        val timing = mapOf(
            "a" to RowTiming(startAt = "ten"), "b" to RowTiming(startAt = "09:00"), "c" to RowTiming(startAt = "10:00"),
        )

        val due = dueRows(listOf(song("a"), song("b"), song("c")), timing, armed = true, now = now, fired = emptySet())

        assertEquals(listOf("c"), due.map { it.id })
    }

    @Test
    fun `what comes next of a row that is not there is nothing`() {
        assertNull(listOf(song("a"), song("b")).nextContentRow("zzz"))
        assertNull(listOf(song("a"), heading("h")).nextContentRow("a"))
    }

    // ── Clock text ──────────────────────────────────────────────────────────────

    @Test
    fun `a typed time with a marker nobody uses is not a time`() {
        assertNull(parseClockText("6:30 xm", Locale.US))
        assertNull(parseClockText("13:30 PM", Locale.US), "thirteen o'clock cannot be PM")
        assertNull(parseClockText("0:30 am", Locale.US))
        assertNull(parseClockText("6:75 pm", Locale.US), "seventy-five minutes")
        assertNull(parseClockText("half six"))
    }

    @Test
    fun `the short markers and the locale's own are all accepted`() {
        assertEquals(LocalTime.of(6, 30), parseClockText("6:30a", Locale.US))
        assertEquals(LocalTime.of(18, 30), parseClockText("6:30p", Locale.US))
        assertEquals(LocalTime.of(0, 15), parseClockText("12:15 a.m.", Locale.US), "twelve at night")
        assertEquals(LocalTime.of(12, 15), parseClockText("12:15 PM", Locale.US))
    }

    @Test
    fun `a locale decides whether the clock starts on 24 hours`() {
        assertTrue(localeUses24HourClock(Locale.GERMANY))
        assertFalse(localeUses24HourClock(Locale.US))
        assertEquals("6:05 AM", clockText(LocalTime.of(6, 5), use24Hour = false, locale = Locale.US))
    }

    // ── Durations ───────────────────────────────────────────────────────────────

    @Test
    fun `a duration with too many parts, a blank part or a negative part is not one`() {
        assertNull(parseDuration("1:02:03:04"))
        assertNull(parseDuration("4:"))
        assertNull(parseDuration("-4:30"))
        assertNull(parseDuration("   "))
        assertEquals(0, parseDuration("0"))
    }

    // ── Dates, months and the grid ──────────────────────────────────────────────

    @Test
    fun `month and weekday names come from the locale`() {
        assertEquals("September", monthName(Month.SEPTEMBER, Locale.US))
        assertEquals("September 2026", monthHeading(YearMonth.of(2026, 9), Locale.US))
        assertEquals("Mon", weekdayName(DayOfWeek.MONDAY, Locale.US))
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeek(Locale.US))
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeek(Locale.GERMANY))
    }

    @Test
    fun `a grid starting on Sunday puts Saturday last`() {
        val order = weekdayOrder(DayOfWeek.SUNDAY)

        assertEquals(DayOfWeek.SUNDAY, order.first())
        assertEquals(DayOfWeek.SATURDAY, order.last())
        assertEquals(DayOfWeek.SUNDAY, monthGrid(YearMonth.of(2026, 9), DayOfWeek.SUNDAY).first().dayOfWeek)
    }

    @Test
    fun `a time with the wrong number of parts is not a time`() {
        assertNull(parseStoredTime("10"))
        assertNull(parseStoredTime("10:00:00"))
        assertNull(parseStoredTime("ten:00"))
    }

    // ── Presets ─────────────────────────────────────────────────────────────────

    @Test
    fun `a stamp that is neither an instant nor a date-time is blank`() {
        assertEquals("", normalizedStamp("last Tuesday"))
        assertEquals("", normalizedStamp("   "))
        assertEquals("2026-09-20T10:00:00Z", normalizedStamp("2026-09-20T10:00:00Z"))
    }

    @Test
    fun `a preset saved again under its own id replaces itself`() {
        val first = ItemPreset("p", "Opener", song("a"), savedAt = "2026-09-01T00:00:00Z")
        val document = PresetDocument(presets = listOf(first))
            .withoutPreset("p")
            .withPreset(first.copy(name = "Opener 2"))

        assertEquals(listOf("Opener 2"), document.presets.map { it.name })
        assertTrue(document.deletedPresets.isEmpty(), "saving it again lifts the deletion")
    }

    @Test
    fun `a preset is named after what its item is`() {
        assertEquals("Holiday", suggestedPresetName(ScheduleItem.PictureItem("p", "/x", "Holiday", 3)))
        assertEquals("Deck", suggestedPresetName(ScheduleItem.PresentationItem("d", "/x", "Deck", 2, "pptx")))
        assertEquals("Clip", suggestedPresetName(ScheduleItem.MediaItem("m", "/x", "Clip", "local")))
        assertEquals("1 - Song a", suggestedPresetName(song("a")), "a song is named by its row text")
    }

    // ── Row identity ────────────────────────────────────────────────────────────

    @Test
    fun `rows copied without timing carry no timing`() {
        val copied = copiedRows(listOf(song("a")), mapOf("a" to 300))

        assertEquals(1, copied.items.size)
        assertEquals(300, copied.plannedSeconds.values.single())
        assertTrue(copied.timing.isEmpty())
    }

    @Test
    fun `a duplicated row keeps its timing as well as its estimate`() {
        val document = CalendarDocument(
            services = listOf(
                service(
                    items = listOf(song("a"), song("a")),
                    planned = mapOf("a" to 300),
                    timing = mapOf("a" to RowTiming(startAt = "10:00")),
                )
            )
        ).withUniqueRowIds()

        val fixed = document.services.single()
        val fresh = fixed.items.map { it.id }.single { it != "a" }
        assertEquals(300, fixed.plannedSeconds[fresh])
        assertEquals("10:00", fixed.timingOf(fresh).startAt)
    }

    // ── Paths ───────────────────────────────────────────────────────────────────

    @Test
    fun `a blank path and the folder itself are left as they are`() {
        val folder = File(System.getProperty("java.io.tmpdir"), "calendar-paths")

        val blank = ScheduleItem.PictureItem("p", "", "", 0).withPathsRelativeTo(folder)
        val self = ScheduleItem.PictureItem("p", folder.absolutePath, "", 0).withPathsRelativeTo(folder)

        assertEquals("", (blank as ScheduleItem.PictureItem).folderPath)
        assertEquals(folder.absolutePath, (self as ScheduleItem.PictureItem).folderPath)
    }

    // ── Timers ──────────────────────────────────────────────────────────────────

    @Test
    fun `seconds until a time already past count on to tomorrow`() {
        assertEquals(60, secondsUntil(LocalTime.of(10, 0), LocalTime.of(10, 1)))
        assertEquals(86_340, secondsUntil(LocalTime.of(10, 1), LocalTime.of(10, 0)))
    }

    @Test
    fun `only a duration timer has a length to read`() {
        val clock = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMode = TimerModes.CLOCK)

        assertFalse(clock.isDurationTimer())
        assertNull(clock.timerSeconds())
        assertNull(song("a").timerSeconds())
        assertFalse(song("a").canPlayRepeatedly())
        assertTrue(ScheduleItem.AnnouncementItem(id = "n", text = "Welcome").canPlayRepeatedly())
    }

    // ── Moving a service ────────────────────────────────────────────────────────

    @Test
    fun `moving a service leaves unpinned rows and unreadable times where they are`() {
        val moved = service(
            start = "11:00",
            items = listOf(cue("c", offset = -5), cue("odd", at = "soon")),
            timing = mapOf("a" to RowTiming(followsPrevious = true), "b" to RowTiming(startAt = "ten")),
        ).withStartMovedFrom("10:00")

        assertEquals("", moved.timingOf("a").startAt)
        assertEquals("ten", moved.timingOf("b").startAt)
        assertEquals("", (moved.items[0] as ScheduleItem.CueItem).absoluteTime)
        assertEquals("soon", (moved.items[1] as ScheduleItem.CueItem).absoluteTime)
    }

    // ── Clocks ──────────────────────────────────────────────────────────────────

    @Test
    fun `a service with no readable start and nothing pinned cannot be laid out`() {
        val unreadable = service(start = "soon", items = listOf(song("a")), planned = mapOf("a" to 300))

        assertEquals(unreadable, unreadable.withTimesLaidOut())
        assertTrue(runClocks(unreadable).isEmpty())
    }

    @Test
    fun `a pinned row waiting on nothing is not reported, and a cued first row is`() {
        val pinned = service(
            items = listOf(song("a")), timing = mapOf("a" to RowTiming(startAt = "10:00", followsPrevious = true)),
        )
        val waiting = service(
            items = listOf(heading("h"), song("a")), timing = mapOf("a" to RowTiming(followsPrevious = true)),
        )

        assertTrue(pinned.followsWithoutHandoff().isEmpty())
        assertEquals(setOf("a"), waiting.followsWithoutHandoff())
    }

    @Test
    fun `the schedule's clock flows through rows that carry no timing`() {
        val timing = mapOf("a" to RowTiming(startAt = "10:00", runSeconds = 60, repeats = 2, atEnd = RowEnd.NEXT))

        val clocks = scheduleClocks(listOf(song("a"), heading("h"), song("b")), timing)

        assertEquals(LocalTime.of(10, 2), clocks.getValue("b").time)
        assertTrue(clocks.getValue("b").exact)
        assertFalse(clocks.containsKey("h"))
    }

    // ── Auto-load ───────────────────────────────────────────────────────────────

    @Test
    fun `a service whose first row is pinned begins there, and one with no start is never due`() {
        val pinned = service(items = listOf(song("a")), timing = mapOf("a" to RowTiming(startAt = "10:15")))
        val lost = service(start = "soon")

        assertEquals(LocalTime.of(10, 15), pinned.firstRowStart())
        assertNull(lost.firstRowStart())
        assertFalse(lost.isDueToLoad(LocalDateTime.of(2026, 9, 20, 10, 0)))
    }

    // ── The document ────────────────────────────────────────────────────────────

    @Test
    fun `a deletion is stamped now unless told otherwise`() {
        val document = CalendarDocument(services = listOf(service())).withoutService("s")

        assertTrue(document.services.isEmpty())
        assertTrue(document.deletedServices.getValue("s").endsWith("Z"))
        assertTrue(document.servicesInSeries("").isEmpty())
    }

    @Test
    fun `a template saved under a name already used replaces it, by name or by id`() {
        val first = SavedTemplate(id = "t1", name = "Standard", startTime = "10:00")
        val document = CalendarDocument(templates = listOf(first))

        assertEquals(1, document.withTemplate(first.copy(id = "t2", name = "STANDARD")).templates.size)
        assertEquals(1, document.withTemplate(first.copy(name = "Other")).templates.size)
        assertNull(document.withoutTemplate("t1").templateById("t1"))
    }

    @Test
    fun `an unknown kind or repeat falls back rather than throwing`() {
        assertEquals(ServiceKind.SUNDAY, ServiceKind.from("brunch"))
        assertEquals(ServiceRepeat.NONE, ServiceRepeat.from("yearly"))
        assertEquals(AUTO_LOAD_LEAD_MAX, CalendarPreferences(autoLoadLeadMinutes = 999).autoLoadLead())
    }
}
