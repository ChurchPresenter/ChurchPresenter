package org.churchpresenter.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the window is showing, and the only thing that writes the store.
 *
 * Driven against a real folder: every mutation here commits, and "saved immediately" is a claim
 * about a file rather than about a field.
 */
class CalendarStateTest {

    private val folder: File = Files.createTempDirectory("calendar-state").toFile()
    private val today = LocalDate.of(2026, 9, 20)

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun state() = CalendarState(CalendarStore(folder), songFolder = null, today = today)

    private fun song(id: String, title: String = "Song") =
        ScheduleItem.SongItem(id = id, songNumber = 1, title = title, songbook = "Hymns", songId = "Hymns::1")

    private fun CalendarState.addSunday(name: String = "Sunday Morning"): PlannedService {
        select(today)
        addService(name, "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)
        return document.services.first { it.name == name }
    }

    // ── Services ────────────────────────────────────────────────────────────────

    @Test
    fun `a service is added to the selected day and written out`() {
        val state = state()

        val service = state.addSunday()

        assertEquals(today.toString(), service.date)
        assertTrue(CalendarStore(folder).load().document.services.any { it.id == service.id }, "saved")
    }

    @Test
    fun `the day's services and the selected one follow the selection`() {
        val state = state()
        val service = state.addSunday()

        assertEquals(listOf(service.id), state.servicesOnSelectedDate.map { it.id })
        assertEquals(service.id, state.selectedService?.id, "the only service is the selected one")

        state.select(today.plusDays(1))

        assertTrue(state.servicesOnSelectedDate.isEmpty())
        assertNull(state.selectedService)
    }

    @Test
    fun `a renamed service keeps its rows`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a")))

        state.updateService(state.document.serviceById(service.id)!!.copy(name = "Evening"))

        val updated = state.document.serviceById(service.id)!!
        assertEquals("Evening", updated.name)
        assertEquals(listOf("a"), updated.items.map { it.id })
    }

    @Test
    fun `deleting a service clears the selection`() {
        val state = state()
        val service = state.addSunday()

        state.deleteService(service.id)

        assertTrue(state.document.services.isEmpty())
        assertNull(state.selectedService)
    }

    @Test
    fun `a service copied to other dates is re-keyed`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a")))
        state.setPlannedSeconds(service.id, "a", 300)

        state.copyService(
            state.document.serviceById(service.id)!!,
            dates = listOf(today.plusDays(7)),
            includeRunOfShow = true,
            includeCues = false,
            repeat = ServiceRepeat.NONE,
        )

        val copy = state.document.services.single { it.date == today.plusDays(7).toString() }
        assertEquals(1, copy.items.size)
        assertNotEquals("a", copy.items.single().id, "a copy shares no row ids with its source")
        assertEquals(300, copy.plannedSeconds.values.single(), "and its estimates travel with it")
    }

    @Test
    fun `a copy without the run of show is just the service`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a")))

        state.copyService(
            state.document.serviceById(service.id)!!,
            dates = listOf(today.plusDays(7)),
            includeRunOfShow = false,
            includeCues = false,
        )

        assertTrue(state.document.services.single { it.date != today.toString() }.items.isEmpty())
    }

    // ── Rows ────────────────────────────────────────────────────────────────────

    @Test
    fun `rows are added, moved and removed in place`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a"), song("b"), song("c")))

        state.moveItem(service.id, 0, 2)
        assertEquals(listOf("b", "c", "a"), state.document.serviceById(service.id)!!.items.map { it.id })

        state.removeItem(service.id, "c")
        assertEquals(listOf("b", "a"), state.document.serviceById(service.id)!!.items.map { it.id })
    }

    @Test
    fun `replacing a row drops the estimate that was measured for the old one`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a")))
        state.setPlannedSeconds(service.id, "a", 300)

        state.replaceItem(service.id, "a", listOf(song("b")))

        val updated = state.document.serviceById(service.id)!!
        assertEquals(listOf("b"), updated.items.map { it.id })
        assertTrue(updated.plannedSeconds.isEmpty(), "an estimate belongs to the row it was made for")
    }

    @Test
    fun `a planned length can be set and cleared`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a")))

        state.setPlannedSeconds(service.id, "a", 300)
        assertEquals(300, state.document.serviceById(service.id)!!.plannedSeconds["a"])

        state.setPlannedSeconds(service.id, "a", null)
        assertNull(state.document.serviceById(service.id)!!.plannedSeconds["a"])
    }

    @Test
    fun `timing is kept beside the row and dropped when it says nothing`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a")))

        state.setTiming(service.id, "a", RowTiming(startAt = "09:45", atEnd = RowEnd.NEXT))
        assertEquals("09:45", state.document.serviceById(service.id)!!.timingOf("a").startAt)

        state.setTiming(service.id, "a", RowTiming.DEFAULT)
        assertTrue(state.document.serviceById(service.id)!!.timing.isEmpty(), "a default says nothing")
    }

    @Test
    fun `laying out the times pins every row from the first`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a"), song("b")))
        state.setPlannedSeconds(service.id, "a", 300)
        state.setTiming(service.id, "a", RowTiming(startAt = "10:00"))

        state.layOutTimes(service.id)

        assertEquals("10:05", state.document.serviceById(service.id)!!.timingOf("b").startAt)
    }

    @Test
    fun `arming and skipping a cue touch only what they name`() {
        val state = state()
        val service = state.addSunday()
        val cue = ScheduleItem.CueItem(id = "c", action = "blank", absoluteTime = "10:30")
        state.addItems(service.id, listOf(song("a"), cue))

        state.setArmed(service.id, false)
        assertFalse(state.document.serviceById(service.id)!!.armed)

        state.setCueEnabled(service.id, "c", false)
        val rows = state.document.serviceById(service.id)!!.items
        assertFalse((rows.last() as ScheduleItem.CueItem).enabled)
        assertEquals(listOf("a", "c"), rows.map { it.id }, "ticking a cue is not moving it")
    }

    // ── Sections and preferences ────────────────────────────────────────────────

    @Test
    fun `a section can be added, recoloured, renamed and removed`() {
        val state = state()

        state.addSection("Baptism", "#123456")
        assertTrue(state.document.preferences.sections.any { it.name == "Baptism" })

        state.setSectionColor("Baptism", "#654321")
        assertEquals("#654321", state.document.preferences.sections.first { it.name == "Baptism" }.colorHex)

        state.renameSection("Baptism", "Testimony")
        assertTrue(state.document.preferences.sections.any { it.name == "Testimony" })

        state.removeSection("Testimony")
        assertFalse(state.document.preferences.sections.any { it.name == "Testimony" })
    }

    @Test
    fun `a preference change is written straight out`() {
        val state = state()

        state.updatePreferences(state.document.preferences.copy(autoLoadService = true))

        assertTrue(CalendarStore(folder).load().document.preferences.autoLoadService)
    }

    // ── Templates ───────────────────────────────────────────────────────────────

    @Test
    fun `a run of show can be saved as a template and offered back`() {
        val state = state()
        val service = state.addSunday()
        state.addItems(service.id, listOf(song("a")))

        state.saveTemplate(
            state.document.serviceById(service.id)!!,
            name = "Standard Sunday",
            includeSections = true,
            includeItems = true,
            includeCues = false,
        )

        assertTrue(state.document.templates.any { it.name == "Standard Sunday" })
        assertTrue(state.templateOptions().any { it is ServiceTemplate.Saved })

        state.deleteTemplate(state.document.templates.first().id)
        assertTrue(state.document.templates.isEmpty())
    }

    // ── The month ───────────────────────────────────────────────────────────────

    @Test
    fun `the visible month moves and comes back to today`() {
        val state = state()
        val start = state.visibleMonth

        state.showNextMonth()
        assertEquals(start.plusMonths(1), state.visibleMonth)

        state.showPreviousMonth()
        state.showPreviousMonth()
        assertEquals(start.minusMonths(1), state.visibleMonth)

        state.goToToday()
        assertEquals(start, state.visibleMonth)
        assertEquals(today, state.selectedDate)
    }

    @Test
    fun `the window knows which days hold services`() {
        val state = state()
        state.addSunday()

        assertTrue(state.hasServices(today))
        assertFalse(state.hasServices(today.plusDays(1)))
        assertEquals(1, state.servicesInVisibleMonth().size)
    }

    @Test
    fun `the most recent service before a day is what a new one can copy`() {
        val state = state()
        state.addSunday("Last week")
        state.select(today.plusDays(7))

        assertEquals("Last week", state.mostRecentServiceBefore()?.name)
    }

    @Test
    fun `a recovered calendar says so until the banner is dismissed`() = runTest {
        val store = CalendarStore(folder)
        store.save(
            store.load().document.copy(
                services = listOf(PlannedService(id = "s", date = today.toString(), name = "x", startTime = "10:00")),
            )
        )
        // A second save, so there is a backup to fall back to, then wreck the current file.
        store.save(store.load().document)
        store.file.writeText("{ not json")

        val state = state()
        state.loadAsync(Dispatchers.Unconfined)

        assertEquals(CalendarSource.RECOVERED, state.source, "the window must be able to say so")
        assertEquals("x", state.document.services.single().name)

        state.acknowledgeSource()
        assertEquals(CalendarSource.FILE, state.source, "dismissed, and nothing more to report")
    }
}
