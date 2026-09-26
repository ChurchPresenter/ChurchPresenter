@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.calendar.ScheduleServiceLink
import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The calendar's three notices under Add Files: the service about to load itself (and Load now,
 * which asks before touching a Schedule that holds anything), changes not yet saved back to the
 * service the Schedule came from, and a hand-built Schedule offered to the calendar.
 */
class ScheduleCalendarNoticesTest {

    private val sunday = UpcomingLoad(
        serviceId = "sunday",
        serviceName = "Sunday Morning",
        // Eighty minutes and a few seconds ahead, so "1 h 20 min" holds however long the test takes.
        loadAt = LocalDateTime.now().plusMinutes(80).plusSeconds(20),
    )

    private fun ComposeUiTest.shows(text: String): Boolean =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.click(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    @Test
    fun `the next service is announced with the time left`() =
        scheduleTab(upcomingServiceLoad = sunday) { _, _ ->
            assertTrue(shows("Loading Sunday Morning in 1 h 21 min"))
        }

    @Test
    fun `a service days ahead reads in days and hours`() =
        scheduleTab(
            upcomingServiceLoad = sunday.copy(loadAt = LocalDateTime.now().plusDays(2).plusHours(3).plusMinutes(1)),
        ) { _, _ ->
            assertTrue(shows("Loading Sunday Morning in 2 d 3 h"))
        }

    @Test
    fun `nothing is announced when nothing is due`() = scheduleTab { _, _ ->
        assertFalse(shows("Loading"))
        assertFalse(shows("Load now"))
    }

    @Test
    fun `load now with an empty Schedule loads without asking`() =
        scheduleTab(upcomingServiceLoad = sunday) { _, reports ->
            click("Load now")

            assertEquals(listOf(true), reports.loadNowChoices)
            assertFalse(shows("The Schedule already has"), "nothing to lose, nothing asked")
        }

    @Test
    fun `load now over a Schedule in use asks, and replaces when told to`() =
        scheduleTab(upcomingServiceLoad = sunday, seed = { seedService() }) { vm, reports ->
            click("Load now")
            // Section headings are not items, here as in the header.
            val items = vm.scheduleItems.count { it !is ScheduleItem.LabelItem }
            assertTrue(shows("The Schedule already has $items items."))

            click("Replace")

            assertEquals(listOf(true), reports.loadNowChoices)
            assertFalse(shows("The Schedule already has"), "the question is gone once answered")
        }

    @Test
    fun `load now can add to the end instead`() =
        scheduleTab(upcomingServiceLoad = sunday, seed = { seedService() }) { _, reports ->
            click("Load now")
            click("Add to the end")

            assertEquals(listOf(false), reports.loadNowChoices)
        }

    @Test
    fun `cancelling load now leaves the Schedule alone`() =
        scheduleTab(upcomingServiceLoad = sunday, seed = { seedService() }) { vm, reports ->
            val before = vm.scheduleItems.toList()
            click("Load now")
            click("Cancel")

            assertTrue(reports.loadNowChoices.isEmpty())
            assertEquals(before, vm.scheduleItems)
        }

    @Test
    fun `changes to a loaded service offer to be saved back`() = scheduleTab(
        scheduleService = ScheduleServiceLink("sunday", "Sunday Morning", hasChanges = true),
        seed = { seedService() },
    ) { _, reports ->
        assertTrue(shows("Changes not saved to Sunday Morning"))

        click("Save to calendar")

        assertEquals(1, reports.saveToCalendarRequests)
    }

    @Test
    fun `an unchanged service offers nothing to save`() = scheduleTab(
        scheduleService = ScheduleServiceLink("sunday", "Sunday Morning", hasChanges = false),
        seed = { seedService() },
        offerAddToCalendar = true,
    ) { _, _ ->
        assertFalse(shows("Save to calendar"))
        assertFalse(shows("Not in the calendar"), "it is in the calendar")
    }

    @Test
    fun `a Schedule built by hand is offered to the calendar`() =
        scheduleTab(seed = { seedService() }, offerAddToCalendar = true) { _, reports ->
            assertTrue(shows("Not in the calendar"))

            click("Add to calendar…")

            assertEquals(1, reports.addToCalendarRequests)
        }

    @Test
    fun `an empty Schedule, or one with no calendar to go to, is offered nothing`() {
        scheduleTab(offerAddToCalendar = true) { _, _ -> assertFalse(shows("Not in the calendar")) }
        scheduleTab(seed = { seedService() }) { _, _ -> assertFalse(shows("Not in the calendar")) }
    }
}
