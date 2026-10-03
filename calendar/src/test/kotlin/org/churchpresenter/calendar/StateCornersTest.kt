package org.churchpresenter.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where the selection goes when the service under it goes, and the rows a correction or a typed
 * length leaves alone -- the cases [StateGuardsTest] does not reach.
 */
class StateCornersTest {

    private val folder: File = Files.createTempDirectory("calendar-corners").toFile()
    private val today = LocalDate.of(2026, 9, 20)

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song $id", "Hymns", "Hymns::1")

    private fun planned(id: String, start: String, vararg items: ScheduleItem) = PlannedService(
        id = id, date = today.toString(), name = "Service $id", startTime = start, items = items.toList(),
    )

    private suspend fun stateWith(vararg services: PlannedService): CalendarState {
        CalendarStore(folder).save(CalendarDocument(services = services.toList()))
        return CalendarState(CalendarStore(folder), songFolder = null, today = today).also {
            it.loadAsync(Dispatchers.Unconfined)
            it.select(today)
        }
    }

    @Test
    fun `deleting the open service opens the next one that day, or none`() = runTest {
        val state = stateWith(planned("am", "09:00"), planned("pm", "18:00"))
        state.selectService("pm")

        state.deleteService("pm")
        assertEquals("am", state.selectedServiceId)

        state.deleteService("am")
        assertNull(state.selectedServiceId)
    }

    @Test
    fun `deleting another service leaves the open one open`() = runTest {
        val state = stateWith(planned("am", "09:00"), planned("pm", "18:00"))
        state.selectService("pm")
        state.deleteService("am")
        assertEquals("pm", state.selectedServiceId)
    }

    @Test
    fun `a correction to a row that is not there, or in a service that is not there, changes nothing`() = runTest {
        val state = stateWith(planned("am", "09:00", song("a")))
        val before = state.document

        state.updateItem("gone", song("a"))
        state.updateItem("am", song("not-a-row"))

        assertEquals(before, state.document)
    }

    @Test
    fun `a length typed on one row leaves another row's timer alone`() = runTest {
        val timer = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMinutes = 5)
        val state = stateWith(planned("am", "09:00", timer, song("a")))

        state.setPlannedSeconds("am", "a", 120)
        state.setPlannedSeconds("am", "t", null)

        val saved = state.document.serviceById("am")!!
        assertEquals(timer, saved.items.first(), "neither a length for another row nor a cleared one resets it")
        assertEquals(mapOf("a" to 120), saved.plannedSeconds)
    }

    @Test
    fun `a length typed on a clock timer leaves the timer as it was`() = runTest {
        val clock = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMode = TimerModes.CLOCK)
        val state = stateWith(planned("am", "09:00", clock))

        state.setPlannedSeconds("am", "t", 300)

        val saved = state.document.serviceById("am")!!
        assertEquals(clock, saved.items.single(), "a clock timer counts to a time, not for a length")
        assertEquals(mapOf("t" to 300), saved.plannedSeconds)
    }

    @Test
    fun `a merge that removes the open service opens what is left of the day`() = runTest {
        val state = stateWith(planned("am", "09:00"), planned("pm", "18:00"))
        state.selectService("pm")

        // Another machine deleted the evening service and saved.
        CalendarStore(folder).save(state.document.withoutService("pm"))
        state.reloadMerging(Dispatchers.Unconfined)

        assertEquals("am", state.selectedServiceId)
    }
}
