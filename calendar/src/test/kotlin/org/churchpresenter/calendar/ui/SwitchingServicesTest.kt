@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.withTimesLaidOut
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two services on one day, and the run of show switched between them in the same window.
 *
 * Every action the open service's pane offers is a lambda over that service. Switching services
 * recomposes the pane rather than rebuilding it, so an action that kept the first service it was
 * built with would quietly change the wrong service -- these act after a switch and check which
 * one changed on disk.
 */
class SwitchingServicesTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private val morning = service(id = "am", name = "Morning", start = "09:00")
    private val evening = service(
        id = "pm",
        name = "Evening",
        start = "18:00",
        items = listOf(song("b", "Be Thou My Vision"), heading("h2", "Response")),
        planned = mapOf("b" to 240),
    )

    @Test
    fun `a row removed after switching services leaves the first service alone`() =
        withCalendar(documentWith(morning, evening)) { folder ->
            awaitText("Amazing Grace")
            clickFirst("Evening")
            awaitText("Be Thou My Vision")

            clickIcon("Remove from the run of show")

            val saved = stored(folder)
            assertEquals(listOf("h2"), saved.serviceById("pm")!!.items.map { it.id })
            assertEquals(listOf("h", "a"), saved.serviceById("am")!!.items.map { it.id })

            clickFirst("Morning")
            awaitText("Amazing Grace")
            clickIcon("Remove from the run of show")
            assertEquals(listOf("a"), stored(folder).serviceById("am")!!.items.map { it.id }, "and back again")
        }

    @Test
    fun `arming follows the service that is open`() = withCalendar(documentWith(morning, evening)) { folder ->
        awaitText("Amazing Grace")
        val before = stored(folder).serviceById("am")!!.armed
        clickFirst("Evening")
        awaitText("Be Thou My Vision")

        clickIcon("Arm or disarm every cue")

        val saved = stored(folder)
        assertEquals(!before, saved.serviceById("pm")!!.armed)
        assertEquals(before, saved.serviceById("am")!!.armed)
    }

    @Test
    fun `loading after a switch loads the service now open, at its own start`() {
        val loads = mutableListOf<Pair<List<ScheduleItem>, String?>>()
        val host = CalendarHost(loadIntoSchedule = { items, _, _, _, start -> loads += items to start })
        withCalendar(documentWith(morning, evening), host = host) {
            awaitText("Amazing Grace")
            clickFirst("Evening")
            awaitText("Be Thou My Vision")

            clickFirst("Load into Schedule")

            val (items, start) = loads.single()
            assertEquals("18:00", start)
            assertEquals(listOf("b", "h2"), items.map { it.id })
        }
    }

    @Test
    fun `laying out times after a switch times the service now open`() =
        withCalendar(documentWith(morning, evening)) { folder ->
            awaitText("Amazing Grace")
            clickFirst("Evening")
            awaitText("Be Thou My Vision")

            clickIcon("Time every row from the first")

            val saved = stored(folder)
            assertEquals(morning.timing, saved.serviceById("am")!!.timing, "the morning is untouched")
            val laidOut = saved.serviceById("pm")!!
            assertEquals(evening.withTimesLaidOut().timing, laidOut.timing, "the evening, from its own start")
        }
}
