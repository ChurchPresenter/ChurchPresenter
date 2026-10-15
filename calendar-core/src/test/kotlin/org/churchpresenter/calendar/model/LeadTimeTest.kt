package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * How far ahead a service loads itself, and what is done with a number nobody should have typed.
 *
 * The lead is a setting, so it arrives from a file that a person can edit by hand: `0` would mean
 * "load it as it starts", which is a load nobody has time to look at, and a number in the thousands
 * would put the window before the service exists. [CalendarPreferences.autoLoadLead] is where both
 * are dealt with, once, rather than at each place the lead is read.
 */
class LeadTimeTest {

    private val today = LocalDate.of(2026, 9, 20)

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(today, LocalTime.of(hour, minute))

    private fun service(start: String = "10:00", items: List<ScheduleItem> = listOf(song("a"))) =
        PlannedService(
            id = "svc", date = today.toString(), name = "Sunday Morning", startTime = start, items = items,
        )

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song", "Hymns", "Hymns::1")

    // ── The typed value ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a bare number inside the range is the lead`() {
        assertEquals(1, parseLeadMinutes("1"))
        assertEquals(20, parseLeadMinutes("20"))
        assertEquals(120, parseLeadMinutes("120"))
    }

    @Test
    fun `spaces around it are still a number`() {
        assertEquals(15, parseLeadMinutes("  15 "))
    }

    /**
     * No unit is accepted, deliberately.
     *
     * The field draws a bare number because the word for "minutes" differs in each of the locales
     * this ships in — a parser that knew `min` would be a parser that only worked in English.
     */
    @Test
    fun `a number with a unit beside it is not a number`() {
        assertNull(parseLeadMinutes("10 min"))
        assertNull(parseLeadMinutes("10 хв"))
    }

    @Test
    fun `anything outside the range is refused rather than clamped`() {
        assertNull(parseLeadMinutes("0"), "loading as it starts is no lead at all")
        assertNull(parseLeadMinutes("121"))
        assertNull(parseLeadMinutes("-5"))
        assertNull(parseLeadMinutes(""))
        assertNull(parseLeadMinutes("soon"))
    }

    // ── The stored value ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a calendar that has never been asked loads five minutes ahead`() {
        assertEquals(AUTO_LOAD_LEAD_MINUTES, CalendarPreferences().autoLoadLead())
    }

    @Test
    fun `a hand-edited file cannot put the lead outside what the window offers`() {
        assertEquals(AUTO_LOAD_LEAD_MIN, CalendarPreferences(autoLoadLeadMinutes = 0).autoLoadLead())
        assertEquals(AUTO_LOAD_LEAD_MIN, CalendarPreferences(autoLoadLeadMinutes = -30).autoLoadLead())
        assertEquals(AUTO_LOAD_LEAD_MAX, CalendarPreferences(autoLoadLeadMinutes = 10_000).autoLoadLead())
    }

    @Test
    fun `a lead the window could have set is kept as it is`() {
        assertEquals(45, CalendarPreferences(autoLoadLeadMinutes = 45).autoLoadLead())
    }

    // ── What the lead does ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a longer lead opens the window earlier`() {
        val service = service()

        assertFalse(service.isDueToLoad(at(9, 40), lead = 5), "5 minutes ahead is not yet due at 9:40")
        assertTrue(service.isDueToLoad(at(9, 40), lead = 30), "half an hour ahead is")
    }

    @Test
    fun `the lead counts from the first row, not from the service's own start`() {
        val early = service(
            items = listOf(song("a"), song("b")),
        ).copy(timing = mapOf("a" to RowTiming(startAt = "09:30")))

        assertTrue(early.isDueToLoad(at(9, 25), lead = 5), "the pre-service loop is what begins at 9:30")
        assertFalse(service().isDueToLoad(at(9, 25), lead = 5), "a plan with no pinned row begins at 10:00")
    }

    @Test
    fun `the document picks the service due now, with the lead it was given`() {
        val morning = service(start = "10:00").copy(id = "morning")
        val evening = service(start = "18:00").copy(id = "evening")
        val calendar = CalendarDocument(services = listOf(morning, evening))

        assertNull(calendar.serviceToAutoLoad(at(9, 0), lead = 5), "too early for either")
        assertSame(morning, calendar.serviceToAutoLoad(at(9, 30), lead = 45), "a longer lead reaches the morning")
        assertSame(evening, calendar.serviceToAutoLoad(at(17, 50), lead = 15))
    }
}
