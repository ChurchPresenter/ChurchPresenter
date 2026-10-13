package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The corners of the plain model the other suites walk past: a time typed with the locale's own
 * AM/PM word, a loop with no service start to fill up to, a preset saved over another by name, a
 * row the Schedule added that the calendar never planned, and services that cannot be timed.
 */
class ModelCornersTest {

    private fun ministry(id: String) = ScheduleItem.MinistryItem(id = id, title = "Poem $id")
    private fun songRow(id: String) = ScheduleItem.SongItem(id = id, songNumber = 1, title = "Song $id", songbook = "H")
    private fun service(
        items: List<ScheduleItem>,
        start: String = "10:00",
        planned: Map<String, Int> = emptyMap(),
        timing: Map<String, RowTiming> = emptyMap(),
        date: String = "2026-09-27",
        id: String = "svc",
    ) = PlannedService(
        id = id, date = date, name = "Sunday", startTime = start,
        items = items, plannedSeconds = planned, timing = timing,
    )

    // ── Clock text ─────────────────────────────────────────────────────────────

    @Test
    fun `a time typed with the locale's own AM or PM word is read`() {
        val korean = Locale.KOREAN
        val am = LocalTime.of(0, 0).format(DateTimeFormatter.ofPattern("a", korean))
        val pm = LocalTime.NOON.format(DateTimeFormatter.ofPattern("a", korean))
        assertEquals(LocalTime.of(18, 30), parseClockText("6:30 $pm", korean))
        assertEquals(LocalTime.of(6, 30), parseClockText("6:30 $am", korean))
        assertNull(parseClockText("6:30 soon", korean), "a word that is neither")
    }

    // ── Run clock ──────────────────────────────────────────────────────────────

    @Test
    fun `a loop fills up to the service start only when there is a start, and only before it`() {
        val loop = RowTiming(repeats = 0)
        val nine = LocalTime.of(9, 0)
        val ten = LocalTime.of(10, 0)
        assertEquals(ten, clockAfter(nine, planned = null, timing = loop, serviceStart = ten))
        assertNull(clockAfter(nine, planned = null, timing = loop, serviceStart = null), "no start, no end to it")
        assertEquals(
            LocalTime.of(10, 5), clockAfter(ten, planned = 300, timing = loop, serviceStart = ten),
            "a loop at the start itself runs its one pass",
        )
    }

    @Test
    fun `the schedule's clock is exact again where a pre-service loop hands over to the start`() {
        val clocks = scheduleClocks(
            items = listOf(songRow("loop"), songRow("first")),
            timing = mapOf("loop" to RowTiming(startAt = "09:30", repeats = 0)),
            startTime = "10:00",
        )
        assertEquals(LocalTime.of(10, 0), clocks.getValue("first").time)
        assertTrue(clocks.getValue("first").exact)
    }

    // ── Timers ─────────────────────────────────────────────────────────────────

    @Test
    fun `only a duration timer is a duration timer`() {
        val plain = ScheduleItem.AnnouncementItem(id = "a", text = "Welcome")
        assertFalse(plain.isDurationTimer())
        assertFalse(plain.copy(isTimer = true, timerMode = TimerModes.CLOCK).isDurationTimer())
        assertTrue(plain.copy(isTimer = true, timerMode = TimerModes.DURATION).isDurationTimer())
        assertFalse(songRow("s").isDurationTimer())
    }

    // ── Presets ────────────────────────────────────────────────────────────────

    @Test
    fun `a preset saved under a name already kept replaces it, whatever the case`() {
        val first = ItemPreset("p1", "Opener", songRow("s1"))
        val doc = PresetDocument(presets = listOf(first))
        val renamed = doc.withPreset(ItemPreset("p1", "Renamed", songRow("s2")))
        assertEquals(listOf("Renamed"), renamed.presets.map { it.name }, "same id")
        val sameName = doc.withPreset(ItemPreset("p9", "OPENER", songRow("s3")))
        assertEquals(listOf("p9"), sameName.presets.map { it.id }, "same name")
        val another = doc.withPreset(ItemPreset("p2", "Closer", songRow("s4")))
        assertEquals(listOf("p1", "p2"), another.presets.map { it.id })
    }

    // ── Into and out of the Schedule ───────────────────────────────────────────

    @Test
    fun `an off-screen row with no length adds no lead time`() {
        val timing = service(listOf(ministry("m"), songRow("s"))).timingForSchedule()
        assertEquals(0, timing["s"]?.leadSeconds ?: 0)
    }

    @Test
    fun `a cue the Schedule added is kept as it came, and an off-screen row before a removed row is kept`() {
        val planned = service(listOf(songRow("a"), ministry("m"), songRow("b")))
        val newCue = ScheduleItem.CueItem(id = "c", action = CueAction.PROJECT, absoluteTime = "10:30")
        val back = planned.withScheduleRows(listOf(songRow("a"), newCue))
        assertEquals(listOf("a", "c", "m"), back.items.map { it.id })
        assertSame(newCue, back.items[1])
    }

    @Test
    fun `an off-screen row that ends the service stays at the end, after rows the Schedule added`() {
        val planned = service(listOf(songRow("a"), ministry("benediction")))
        val back = planned.withScheduleRows(listOf(songRow("a"), songRow("new")))
        assertEquals(listOf("a", "new", "benediction"), back.items.map { it.id })
    }

    @Test
    fun `a duplicated row with a timing but no length keeps the timing`() {
        val doc = CalendarDocument(
            services = listOf(
                service(
                    listOf(songRow("x"), songRow("x")),
                    timing = mapOf("x" to RowTiming(runSeconds = 60)),
                ),
            ),
        ).withUniqueRowIds()
        val fixed = doc.services.single()
        assertEquals(2, fixed.items.map { it.id }.toSet().size)
        assertEquals(setOf(60), fixed.items.map { fixed.timing.getValue(it.id).runSeconds }.toSet())
        assertTrue(fixed.plannedSeconds.isEmpty())
    }

    @Test
    fun `a cue lands at the end when no row can be timed`() {
        val untimed = service(listOf(songRow("a"), songRow("b")), start = "")
        val cue = ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, absoluteTime = "10:30")
        assertEquals(listOf("a", "b", "c"), untimed.withCue(cue).items.map { it.id })
    }

    @Test
    fun `a service moved to an unreadable start is left alone`() {
        val timed = service(listOf(songRow("a")), start = "nope", timing = mapOf("a" to RowTiming(startAt = "10:00")))
        assertSame(timed, timed.withStartMovedFrom("10:00"))
    }

    // ── Deleting ───────────────────────────────────────────────────────────────

    @Test
    fun `deleting a service this calendar never held is still one edit past nothing`() {
        val doc = CalendarDocument().withoutService("ghost", Instant.parse("2026-09-20T10:00:00Z"))
        assertEquals(1L, doc.deletedVersions["ghost"])
    }

    // ── Due rows and auto-load ─────────────────────────────────────────────────

    @Test
    fun `a row is due only when its timing pins it to a time`() {
        val rows = listOf(songRow("pinned"), songRow("timed"), songRow("plain"))
        val due = dueRows(
            items = rows,
            timing = mapOf("pinned" to RowTiming(startAt = "10:00"), "timed" to RowTiming(runSeconds = 60)),
            armed = true,
            now = LocalDateTime.of(2026, 9, 27, 10, 0, 5),
            fired = emptySet(),
        )
        assertEquals(listOf("pinned"), due.map { it.id })
    }

    @Test
    fun `a service with an unreadable date or no start is never auto-loaded`() {
        val doc = CalendarDocument(
            services = listOf(
                service(listOf(songRow("a")), date = "someday", id = "bad-date"),
                service(emptyList(), start = "", id = "no-start"),
                service(listOf(songRow("b")), id = "ok"),
            ),
        )
        assertEquals("ok", doc.nextAutoLoad(LocalDateTime.of(2026, 9, 20, 8, 0))?.serviceId)
    }
}
