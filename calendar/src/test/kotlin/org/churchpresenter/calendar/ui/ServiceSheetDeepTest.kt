@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.SavedTemplate
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceRepeat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Add / Edit service sheet, field by field.
 *
 * Every one of these is a state the sheet draws only under a condition — a time that does not
 * parse, a service that belongs to a series, a calendar with templates to start from — and each
 * condition is invisible from any other test: the sheet opens on a blank service and shows none
 * of them.
 */
class ServiceSheetDeepTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    @Test
    fun `a time that is not a time says what one looks like`() = withCalendar {
        clickFirst("Add service")
        awaitText("New service")

        typeIntoFirstField("Evening")
        // The time field is the second; typing nonsense into it is what shows the hint.
        val fields = onAllNodes(hasSetTextAction())
        fields[1].performTextInput("half past")
        waitForIdle()

        assertTrue(shows("Enter a time such as"), "and the sheet says what will be accepted")
    }

    @Test
    fun `a service is added under the type that was picked`() = withCalendar { folder ->
        clickFirst("Add service")
        awaitText("New service")
        typeIntoFirstField("Prayer meeting")

        clickInSheet("Midweek", anchor = "New service")
        clickInSheet("Add service", anchor = "New service")

        val service = stored(folder).services.single()
        assertEquals("Prayer meeting", service.name)
        assertEquals(ServiceKind.MIDWEEK.id, service.kind)
    }

    @Test
    fun `a special event is a type of its own`() = withCalendar { folder ->
        clickFirst("Add service")
        awaitText("New service")
        typeIntoFirstField("Baptism")

        clickInSheet("Special", anchor = "New service")
        clickInSheet("Add service", anchor = "New service")

        assertEquals(ServiceKind.SPECIAL.id, stored(folder).services.single().kind)
    }

    @Test
    fun `a new service can start from a saved template, which fills the fields in`() {
        val template = SavedTemplate(
            id = "t1",
            name = "Sunday Morning",
            startTime = "09:30",
            kind = ServiceKind.SUNDAY.id,
            items = listOf(song("a", "Amazing Grace")),
        )

        withCalendar(CalendarDocument(templates = listOf(template))) { folder ->
            clickFirst("Add service")
            awaitText("Start from")

            assertTrue(shows("Blank service"), "and the option to start from nothing")
            clickFirst("Template ·")
            waitForIdle()
            clickInSheet("Add service", anchor = "New service")

            val service = stored(folder).services.single()
            assertEquals("Sunday Morning", service.name, "the template's own name")
            assertEquals("09:30", service.startTime)
            assertEquals(1, service.items.size, "and its run of show")
        }
    }

    @Test
    fun `a new service can start from an earlier one`() =
        withCalendar(documentWith(service(date = TODAY.minusDays(7), name = "Last Sunday"))) { folder ->
            // The day the window opens on is empty; last Sunday's service is what there is to copy.
            clickFirst("Add service")
            awaitText("Start from")

            clickFirst("Last Sunday")
            waitForIdle()
            clickInSheet("Add service", anchor = "New service")

            val added = stored(folder).services.first { it.date == TODAY.toString() }
            assertEquals("Last Sunday", added.name)
            assertEquals(2, added.items.size)
            assertTrue(
                added.items.none { row -> row.id in stored(folder).services.first().items.map { it.id } },
                "the copy is re-keyed, or editing one would edit the other",
            )
        }

    @Test
    fun `starting from blank leaves what was typed alone`() =
        withCalendar(documentWith(service(date = TODAY.minusDays(7), name = "Last Sunday"))) { folder ->
            clickFirst("Add service")
            awaitText("Start from")
            typeIntoFirstField("Youth night")

            clickFirst("Blank service")
            waitForIdle()
            clickInSheet("Add service", anchor = "New service")

            val added = stored(folder).services.first { it.date == TODAY.toString() }
            assertEquals("Youth night", added.name)
            assertTrue(added.items.isEmpty())
        }

    @Test
    fun `a service in a series is edited for one occurrence or for all of them`() {
        val first = service(id = "a", name = "Sunday Morning").copy(
            seriesId = "series-1", repeat = ServiceRepeat.WEEKLY.id,
        )
        val second = service(id = "b", name = "Sunday Morning", date = TODAY.plusDays(7)).copy(
            seriesId = "series-1", repeat = ServiceRepeat.WEEKLY.id,
        )

        withCalendar(documentWith(first, second)) {
            awaitText("Sunday Morning")
            clickIcon("Edit service")
            awaitText("Applies to")

            assertTrue(shows("This service only"))
            assertTrue(shows("All in series"))
            assertTrue(shows("2 services in this series"), "how far a change would reach")
        }
    }

    @Test
    fun `a service that is not in a series is edited on its own, with nothing to choose`() =
        withCalendar(documentWith(service())) {
            awaitText("Sunday Morning")
            clickIcon("Edit service")
            awaitText("Edit service")

            assertTrue(!shows("Applies to"))
            assertTrue(!shows("Start from"), "and nothing to start from — it already exists")
        }
}
