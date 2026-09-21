@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Copy sheet and the Template sheet — the two ways a run of show is reused.
 *
 * Both are built from the same parts (a scope, a set of include switches, a footer that says what
 * pressing the button will do), and both write through `CalendarState`, so each test ends at
 * `calendar.json` rather than at the sheet.
 */
class CopyAndTemplateDeepTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun ComposeUiTest.openCopy() {
        awaitText("Amazing Grace")
        clickIcon("Copy this service")
        awaitText("Paste on")
    }

    private fun ComposeUiTest.openTemplate() {
        awaitText("Amazing Grace")
        clickIcon("Save this run of show as a template")
        awaitText("Save as template")
    }

    /** Clicks a control of the open sheet by its exact label. */
    private fun ComposeUiTest.sheetButton(label: String, anchor: String) = clickInSheet(label, anchor)

    // ── Copy ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a service is pasted onto next week`() = withCalendar(documentWith(service())) { folder ->
        openCopy()

        sheetButton("Next week", anchor = "Paste on")
        clickLast("Paste")

        val copies = stored(folder).services
        assertEquals(2, copies.size)
        assertEquals(TODAY.plusWeeks(1).toString(), copies.last().date)
        assertTrue(copies.last().items.map { it.id }.none { it in copies.first().items.map { row -> row.id } },
            "the copy is re-keyed, or the two services would share their estimates")
    }

    @Test
    fun `each paste target is a date of its own`() = withCalendar(documentWith(service())) { folder ->
        openCopy()

        sheetButton("Tomorrow", anchor = "Paste on")
        clickLast("Paste")

        assertEquals(TODAY.plusDays(1).toString(), stored(folder).services.last().date)
    }

    @Test
    fun `a repeat turns the paste into a run of services`() = withCalendar(documentWith(service())) { folder ->
        openCopy()

        sheetButton("Weekly", anchor = "Paste on")
        clickLast("Create")

        val made = stored(folder).services
        assertEquals(5, made.size, "the four the sheet offers, plus the one copied from")
        assertTrue(made.all { it.seriesId == made.first().seriesId }, "and they are one series")
    }

    /**
     * The count is held inside what a copy may repeat, however it is typed.
     *
     * The field is controlled — its text is the count itself — so a digit typed beside the one
     * there reads as the two together, and `99`, `94` or `64` all mean "more than a year of
     * weeks". `MAX_REPEAT_COUNT` is what stops that becoming a year and a half of services.
     *
     * Typed rather than stepped: the ▲▼ buttons sit inside a tooltip, and driving one parks on the
     * tooltip's own coroutine — a single click cost ten minutes of wall clock. Both write through
     * the same `onChange`.
     */
    @Test
    fun `a typed count is pulled back to the most a copy may repeat`() =
        withCalendar(documentWith(service())) { folder ->
            openCopy()
            sheetButton("Weekly", anchor = "Paste on")

            typeIntoLastField("9")

            assertTrue(shows("52"), "a year of weeks, said before anything is created")
            clickLast("Create")
            assertEquals(53, stored(folder).services.size, "52 copies, plus the one copied from")
        }

    @Test
    fun `a copy can leave the run of show behind`() = withCalendar(documentWith(service())) { folder ->
        openCopy()

        // Off, so what is pasted is the service itself -- its name, time and type.
        sheetButton("Run of show", anchor = "Paste on")
        sheetButton("Next week", anchor = "Paste on")
        clickLast("Paste")

        val copy = stored(folder).services.last()
        assertTrue(copy.items.isEmpty(), "an empty plan on the right day")
        assertEquals("Sunday Morning", copy.name)
    }

    @Test
    fun `with nothing to copy the sheet says so`() = withCalendar(documentWith(withCue())) {
        openCopy()

        sheetButton("Run of show", anchor = "Paste on")
        sheetButton("Automation cues", anchor = "Paste on")

        assertTrue(shows("at least one thing"), "and asks for something to be picked")
    }

    @Test
    fun `a date that already holds a service is marked`() =
        withCalendar(documentWith(service(), service(id = "next", date = TODAY.plusWeeks(1)))) {
            openCopy()

            // The marker is on the repeat preview's rows, so a repeat is what brings it up.
            sheetButton("Weekly", anchor = "Paste on")

            assertTrue(shows("Has a service"), "so nobody plans a second one onto it by accident")
        }

    // ── Template ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a run of show is saved as a template under a typed name`() =
        withCalendar(documentWith(service())) { folder ->
            openTemplate()

            clearLastField()
            typeIntoLastField("Sunday Morning")
            clickLast("Save template")

            val template = stored(folder).templates.single()
            assertEquals("Sunday Morning", template.name)
            assertEquals("10:00", template.startTime)
            assertTrue(template.items.any { it is ScheduleItem.SongItem })
        }

    @Test
    fun `a template can keep the sections and leave the items`() =
        withCalendar(documentWith(service())) { folder ->
            openTemplate()

            sheetButton("Items", anchor = "Save as template")
            clearLastField()
            typeIntoLastField("Skeleton")
            clickLast("Save template")

            val template = stored(folder).templates.single()
            assertTrue(template.items.all { it is ScheduleItem.LabelItem }, "the headings, and nothing else")
        }

    @Test
    fun `with sections, items and cues all left out there is nothing to save`() =
        withCalendar(documentWith(service())) { folder ->
            openTemplate()

            sheetButton("Sections", anchor = "Save as template")
            sheetButton("Items", anchor = "Save as template")
            sheetButton("Automation cues", anchor = "Save as template")
            clickLast("Save template")

            assertTrue(stored(folder).templates.isEmpty(), "the button does nothing with nothing ticked")

            sheetButton("Automation cues", anchor = "Save as template")
            sheetButton("Sections", anchor = "Save as template")
            clickLast("Save template")
            assertTrue(stored(folder).templates.single().items.all { it is ScheduleItem.LabelItem })
        }

    @Test
    fun `saving over a name that exists says it will replace`() = withCalendar(documentWith(service())) { folder ->
        openTemplate()
        clickLast("Save template")

        openTemplate()
        assertTrue(shows("saving replaces it"), "the sheet warns before it does")

        clickLast("Save template")
        assertEquals(1, stored(folder).templates.size, "and it replaces rather than adding a second")
    }

    @Test
    fun `a template with no name cannot be saved`() = withCalendar(documentWith(service())) { folder ->
        openTemplate()

        clearLastField()
        clickLast("Save template")

        assertTrue(stored(folder).templates.isEmpty(), "a nameless template is not a template")
    }

    private companion object {
        /** The same service with a cue in it, so both of the sheet's include switches are drawn. */
        fun withCue() = service(
            items = listOf(
                heading("h", "Worship"),
                song("a", "Amazing Grace"),
                ScheduleItem.CueItem(id = "cue", action = "blank", absoluteTime = "10:30"),
            ),
        )
    }
}
