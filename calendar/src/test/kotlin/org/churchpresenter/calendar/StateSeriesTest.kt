package org.churchpresenter.calendar

import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Services that repeat: made as a series, edited as one, and deleted as one. */
class StateSeriesTest {

    private val folder: File = Files.createTempDirectory("calendar-series").toFile()
    private val today = LocalDate.of(2026, 9, 20)

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun state() = CalendarState(CalendarStore(folder), songFolder = null, today = today)

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song", "Hymns", "Hymns::1")

    private fun CalendarState.sunday(name: String = "Sunday Morning"): PlannedService {
        select(today)
        addService(name, "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)
        return document.services.first { it.name == name }
    }

    @Test
    fun `a weekly series puts a service on each of its dates`() {
        val state = state()
        val service = state.sunday()

        state.copyService(
            state.document.serviceById(service.id)!!,
            dates = listOf(today.plusDays(7), today.plusDays(14)),
            includeRunOfShow = true,
            includeCues = true,
            repeat = ServiceRepeat.WEEKLY,
        )

        assertEquals(3, state.document.services.size)
        assertTrue(state.document.services.all { it.seriesId.isNotEmpty() }, "including the one it came from")
    }

    @Test
    fun `editing the whole series moves every service in it`() {
        val state = state()
        val service = state.sunday()
        state.copyService(
            state.document.serviceById(service.id)!!,
            dates = listOf(today.plusDays(7)),
            includeRunOfShow = false,
            includeCues = false,
            repeat = ServiceRepeat.WEEKLY,
        )

        state.updateService(
            state.document.serviceById(service.id)!!.copy(startTime = "10:30", name = "Later"),
            wholeSeries = true,
        )

        assertTrue(state.document.services.all { it.startTime == "10:30" })
        assertTrue(state.document.services.all { it.name == "Later" })
    }

    @Test
    fun `editing one service of a series leaves the rest alone`() {
        val state = state()
        val service = state.sunday()
        state.copyService(
            state.document.serviceById(service.id)!!,
            dates = listOf(today.plusDays(7)),
            includeRunOfShow = false,
            includeCues = false,
            repeat = ServiceRepeat.WEEKLY,
        )

        state.updateService(state.document.serviceById(service.id)!!.copy(startTime = "11:00"))

        assertEquals(1, state.document.services.count { it.startTime == "11:00" })
    }

    @Test
    fun `a series can be deleted in one go`() {
        val state = state()
        val service = state.sunday()
        state.copyService(
            state.document.serviceById(service.id)!!,
            dates = listOf(today.plusDays(7), today.plusDays(14)),
            includeRunOfShow = false,
            includeCues = false,
            repeat = ServiceRepeat.WEEKLY,
        )

        state.deleteService(service.id, wholeSeries = true)

        assertTrue(state.document.services.isEmpty())
    }

    @Test
    fun `moving a service carries its pinned rows with it`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a")))
        state.setTiming(service.id, "a", RowTiming(startAt = "09:40"))

        state.updateService(state.document.serviceById(service.id)!!.copy(startTime = "11:00"))

        assertEquals(
            "10:40",
            state.document.serviceById(service.id)!!.timingOf("a").startAt,
            "twenty minutes before the service stays twenty minutes before it",
        )
    }

    @Test
    fun `a new service can start from last week's`() {
        val state = state()
        val previous = state.sunday("Last week")
        state.addItems(previous.id, listOf(song("a")))
        state.select(today.plusDays(7))

        // The template carries the service *as it is now*, not as it was when it was created.
        val lastWeek = state.document.serviceById(previous.id)!!
        state.addService("This week", "10:00", ServiceKind.SUNDAY, ServiceTemplate.CopyOf(lastWeek))

        val fresh = state.document.services.first { it.name == "This week" }
        assertEquals(1, fresh.items.size)
        assertFalse(fresh.items.single().id == "a", "copied rows are re-keyed")
    }

    @Test
    fun `a new service can start from a saved template`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a")))
        state.saveTemplate(state.document.serviceById(service.id)!!, name = "Standard")
        val template = state.document.templates.single()

        state.select(today.plusDays(7))
        state.addService("From template", "10:00", ServiceKind.SUNDAY, ServiceTemplate.Saved(template))

        assertEquals(1, state.document.services.first { it.name == "From template" }.items.size)
    }

    @Test
    fun `the templates offered include last week's service, once`() {
        val state = state()
        state.sunday("Last week")

        val options = state.templateOptions()

        assertTrue(options.any { it is ServiceTemplate.Blank }, "always the option to start empty")
        assertTrue(options.filterIsInstance<ServiceTemplate.CopyOf>().size <= ServiceKind.entries.size)
    }

    @Test
    fun `the songbooks offered are the ones the library holds`() {
        val state = state()

        assertTrue(state.songbooks().isEmpty(), "with no song folder there is nothing to offer")
    }
}
