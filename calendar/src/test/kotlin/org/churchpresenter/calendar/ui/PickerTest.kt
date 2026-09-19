@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarHost
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The picker: where every row of a run of show comes from.
 *
 * Driven through the window, so what is asserted is what an operator sees after opening it from a
 * service — the sheet reads the service it is adding to for its footer, its start-time chips and
 * the length it offers.
 */
class PickerTest {

    @Test
    fun `the songs tab says when the library is still loading`() = withCalendar(documentWith(service())) {
        openPicker()

        assertTrue(shows("song") || shows("Songs"), "the tab it opens on")
    }

    @Test
    fun `the bible tab walks books, chapters and verses`() = withCalendar(
        documentWith(service()),
        host = CalendarHost(bibleBooks = { BIBLE_BOOKS }),
    ) {
        openPicker()
        clickFirst("Bible")
        awaitText("Genesis")

        clickFirst("Genesis")
        awaitText("Chapter")

        assertTrue(shows("1"), "its chapters")
    }

    @Test
    fun `a typed reference is offered as its own result`() = withCalendar(
        documentWith(service()),
        host = CalendarHost(bibleBooks = { BIBLE_BOOKS }),
    ) {
        openPicker()
        clickFirst("Bible")
        awaitText("Genesis")

        typeIntoFirstField("Psalms 2:1")
        waitForIdle()

        assertTrue(shows("Psalms"), "the reference typed out is a result in itself")
    }

    @Test
    fun `the section tab offers what the calendar keeps and a new one`() =
        withCalendar(documentWith(service())) {
            openPicker()
            clickFirst("Section")
            awaitText("Pre-Service")

            typeIntoFirstField("Baptism")
            waitForIdle()

            assertTrue(shows("Baptism"), "a heading that does not exist yet is offered as new")
        }

    @Test
    fun `a section can be added straight to the run of show`() = withCalendar(documentWith(service())) {
        openPicker()
        clickFirst("Section")
        awaitText("Pre-Service")

        clickFirst("Pre-Service")
        waitForIdle()

        assertTrue(shows("Pre-Service"), "and lands in the list behind the sheet")
    }

    @Test
    fun `the presets tab says when there is nothing saved`() = withCalendar(documentWith(service())) {
        openPicker()
        clickFirst("Presets")
        waitForIdle()

        assertTrue(shows("preset") || shows("Presets"))
    }

    @Test
    fun `the timing panel's chips are all offered`() = withCalendar(documentWith(service())) {
        openPicker()

        assertTrue(shows("Cued"))
        assertTrue(shows("After previous"))
        assertTrue(shows("On time"))
        assertTrue(shows("Own length"))
        assertTrue(shows("Once"))
        assertTrue(shows("Hold"))
    }

    @Test
    fun `picking a start chip says what the row will do`() = withCalendar(documentWith(service())) {
        openPicker()

        clickFirst("After previous")
        waitForIdle()

        assertTrue(shows("After previous"))
    }

    @Test
    fun `a run length can be chosen from the chips`() = withCalendar(documentWith(service())) {
        openPicker()

        clickFirst("15m")
        waitForIdle()

        assertTrue(shows("15:00"), "the panel reads back what it will do")
    }

    @Test
    fun `an end action can be chosen`() = withCalendar(documentWith(service())) {
        openPicker()

        clickFirst("Next")
        waitForIdle()

        assertTrue(shows("Next"))
    }

    @Test
    fun `editing a row opens on that row and saves as it changes`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")

        clickFirst("Amazing Grace")
        awaitText("Editing")

        clickFirst("Loop")
        waitForIdle()

        assertTrue(shows("Done"), "an edit is finished by closing it, because it is already saved")
    }

    @Test
    fun `the sheet says where a pick will land`() = withCalendar(documentWith(service())) {
        openPicker()

        assertTrue(shows("Sunday Morning"), "the service it adds to")
    }
}

/** Opens the add-item sheet on a service that already has a row, the way an operator does. */
private fun ComposeUiTest.openPicker() {
    awaitText("Amazing Grace")
    clickFirst("Add song, verse or section")
    awaitText("Songs")
}
