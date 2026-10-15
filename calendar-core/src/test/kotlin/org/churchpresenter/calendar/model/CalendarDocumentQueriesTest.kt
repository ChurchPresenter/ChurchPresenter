package org.churchpresenter.calendar.model

import org.churchpresenter.calendar.model.RowSamples.song
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the month grid and the run of show ask a calendar, and what a new one starts with. */
class CalendarDocumentQueriesTest {

    private fun service(id: String, date: String, start: String) =
        PlannedService(id = id, date = date, name = id, startTime = start)

    @Test
    fun `a day's services are listed in the order they start`() {
        val document = CalendarDocument(
            services = listOf(
                service("evening", "2026-09-20", "18:30"),
                service("other-day", "2026-09-21", "09:00"),
                service("morning", "2026-09-20", "10:00"),
            ),
        )

        assertEquals(listOf("morning", "evening"), document.servicesOn("2026-09-20").map { it.id })
        assertEquals(setOf("2026-09-20", "2026-09-21"), document.plannedDates())
        assertTrue(document.servicesOn("2026-09-22").isEmpty())
    }

    @Test
    fun `a service on its own is a Sunday service in no series`() {
        val alone = service("svc", "2026-09-20", "10:00")

        assertEquals(ServiceKind.SUNDAY.id, alone.kind)
        assertFalse(alone.isInSeries())
        assertEquals("", alone.repeat)
        assertTrue(alone.copy(seriesId = "weekly").isInSeries())
    }

    @Test
    fun `a saved template's cues are its cue rows`() {
        val cue = ScheduleItem.CueItem("c", CueAction.BLANK)
        val template = SavedTemplate(id = "t", name = "Sunday", startTime = "10:00", items = listOf(song(), cue))

        assertEquals(listOf(cue), template.cueRows())
        assertEquals(ServiceKind.SUNDAY.id, template.kind)
        assertTrue(template.plannedSeconds.isEmpty() && template.timing.isEmpty())
    }

    @Test
    fun `a new calendar starts at ten, armed, with nothing loading itself`() {
        val preferences = CalendarPreferences()

        assertEquals("10:00", preferences.defaultStartTime)
        assertTrue(preferences.armByDefault)
        assertFalse(preferences.autoLoadService)
        assertTrue(preferences.sections.isNotEmpty())
        assertTrue(preferences.defaultItemSeconds in 1 until preferences.defaultSermonSeconds)
        assertTrue(preferences.autoLoadLeadMinutes > 0)
    }

    @Test
    fun `a cue projects once, on the minute its service starts, unless told otherwise`() {
        val cue = ServiceCue(id = "c")

        assertEquals(0, cue.offsetMinutes)
        assertEquals("", cue.absoluteTime)
        assertEquals(CueAction.PROJECT, cue.action)
        assertTrue(cue.enabled)
        assertEquals(1, cue.plays)
        assertEquals("", cue.label)
        assertEquals(null, cue.payload)
    }
}
