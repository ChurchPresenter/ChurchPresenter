package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Old files read forward, and what the run of show says about a cue against a clock. */
class MigrationAndStatusTest {

    private val today = LocalDate.of(2026, 9, 20)

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song", "", "")

    private fun cueRow(id: String, at: String = "09:45", enabled: Boolean = true) =
        ScheduleItem.CueItem(id = id, action = CueAction.BLANK, absoluteTime = at, enabled = enabled)

    private fun service(
        items: List<ScheduleItem> = listOf(song("a")),
        cues: List<ServiceCue> = emptyList(),
        armed: Boolean = true,
    ) = PlannedService(
        id = "svc", date = today.toString(), name = "Sunday", startTime = "10:00",
        items = items, cues = cues, armed = armed,
    )

    // ── The old shape ───────────────────────────────────────────────────────────

    @Test
    fun `cues written by an older version become rows`() {
        val document = CalendarDocument(
            services = listOf(
                service(cues = listOf(ServiceCue(id = "old", offsetMinutes = -15, label = "Countdown"))),
            ),
        )

        val migrated = document.withCuesAsRows()

        val service = migrated.services.single()
        assertTrue(service.cues.isEmpty(), "and are not written again in the old shape")
        assertEquals(1, service.cueRows().size)
        assertEquals("Countdown", service.cueRows().single().label)
    }

    @Test
    fun `a file that has no old cues is left as it is`() {
        val document = CalendarDocument(services = listOf(service(items = listOf(song("a"), cueRow("c")))))

        val migrated = document.withCuesAsRows()

        assertEquals(listOf("a", "c"), migrated.services.single().items.map { it.id })
    }

    @Test
    fun `an old cue is placed in the list where it fires`() {
        val document = CalendarDocument(
            services = listOf(
                service(
                    items = listOf(song("a")),
                    cues = listOf(ServiceCue(id = "old", offsetMinutes = -20)),
                ),
            ),
        )

        val rows = document.withCuesAsRows().services.single().items

        assertEquals("old", rows.first().id, "twenty minutes before the service comes before its rows")
    }

    // ── Status against a clock ──────────────────────────────────────────────────

    @Test
    fun `a cue that has passed is marked as fired, and the next one as next`() {
        val service = service(items = listOf(cueRow("early", "09:30"), cueRow("later", "10:30")))

        val statuses = service.cueStatuses(LocalTime.of(10, 0))

        assertTrue(statuses.getValue("early").fired)
        assertFalse(statuses.getValue("later").fired)
        assertTrue(statuses.getValue("later").isNext)
        assertEquals(30, statuses.getValue("later").minutesUntil)
    }

    @Test
    fun `with no clock nothing is fired or next`() {
        val statuses = service(items = listOf(cueRow("c"))).cueStatuses(null)

        assertTrue(statuses.isEmpty() || statuses.values.none { it.fired || it.isNext })
    }

    @Test
    fun `how many cues will fire depends on the arm switch and the ticks`() {
        val rows = listOf(cueRow("a"), cueRow("b", enabled = false))

        assertEquals(1, service(items = rows).activeCueCount(), "a skipped cue is not counted")
        assertEquals(0, service(items = rows, armed = false).activeCueCount(), "nor is any, disarmed")
    }

    @Test
    fun `a preview clock starts before the service, or from where it already is`() {
        assertEquals(LocalTime.of(9, 40), previewClockStart("10:00", showing = null))
        assertEquals(LocalTime.of(11, 0), previewClockStart("10:00", showing = LocalTime.of(11, 0)))
    }

    // ── What the engine is given ────────────────────────────────────────────────

    @Test
    fun `a cue is pinned to the clock as it goes into the schedule`() {
        val relative = ScheduleItem.CueItem(id = "c", action = CueAction.BLANK, offsetMinutes = -15)

        val rows = service(items = listOf(relative)).rowsForSchedule()

        assertEquals("09:45", (rows.single() as ScheduleItem.CueItem).absoluteTime)
    }

    @Test
    fun `only rows with something to say carry timing into the schedule`() {
        val service = PlannedService(
            id = "s", date = today.toString(), name = "x", startTime = "10:00",
            items = listOf(song("a"), song("b")),
            plannedSeconds = mapOf("b" to 300),
            timing = mapOf("a" to RowTiming.DEFAULT),
        )

        val timing = service.timingForSchedule()

        assertFalse("a" in timing, "a default entry says nothing a missing one would not")
        assertEquals(300, timing.getValue("b").runSeconds, "a length alone is worth carrying")
    }

    // ── The engine's own decision ───────────────────────────────────────────────

    @Test
    fun `a cue is due once, within its grace, and only while armed`() {
        val rows = listOf(cueRow("c", "10:00"))
        val at = LocalDateTime.of(today, LocalTime.of(10, 1))

        assertEquals(listOf("c"), dueCues(rows, armed = true, now = at, fired = emptySet()).map { it.id })
        assertTrue(dueCues(rows, armed = false, now = at, fired = emptySet()).isEmpty())
        assertTrue(dueCues(rows, armed = true, now = at, fired = setOf("c")).isEmpty())
        assertTrue(
            dueCues(rows, armed = true, now = LocalDateTime.of(today, LocalTime.of(10, 5)), fired = emptySet())
                .isEmpty(),
            "past its grace, a cue is let go rather than fired late",
        )
    }

    @Test
    fun `a skipped cue is never due`() {
        val at = LocalDateTime.of(today, LocalTime.of(10, 0))

        assertTrue(dueCues(listOf(cueRow("c", "10:00", enabled = false)), true, at, emptySet()).isEmpty())
    }

    @Test
    fun `the grace window can be widened by the caller`() {
        val rows = listOf(cueRow("c", "10:00"))
        val at = LocalDateTime.of(today, LocalTime.of(10, 5))

        assertEquals(
            listOf("c"),
            dueCues(rows, true, at, emptySet(), grace = Duration.ofMinutes(10)).map { it.id },
        )
    }

    @Test
    fun `a row with no time of its own is never due`() {
        val at = LocalDateTime.of(today, LocalTime.of(10, 0))

        assertTrue(dueRows(listOf(song("a")), emptyMap(), true, at, emptySet()).isEmpty())
    }

    @Test
    fun `due rows come in the order they fire`() {
        val rows = listOf(song("late"), song("early"))
        val timing = mapOf(
            "late" to RowTiming(startAt = "10:01"),
            "early" to RowTiming(startAt = "10:00"),
        )
        val at = LocalDateTime.of(today, LocalTime.of(10, 1))

        assertEquals(listOf("early", "late"), dueRows(rows, timing, true, at, emptySet()).map { it.id })
    }

    @Test
    fun `what comes next skips headings and cues`() {
        val rows = listOf(song("a"), ScheduleItem.LabelItem("h", "x", "#FFF", "#000"), cueRow("c"), song("b"))

        assertEquals("b", rows.nextContentRow("a")?.id)
        assertNull(rows.nextContentRow("b"), "nothing follows the last row")
        assertNull(rows.nextContentRow("missing"))
    }
}
