@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Copying a service to other dates, and everything the settings dialog can change.
 *
 * Asserted against the store as well as the screen: both of these write `calendar.json` on every
 * change, and "it saved" is a claim about the file.
 */
class CopyAndSettingsTest {

    private fun ComposeUiTest.openSettings() {
        awaitText("Sunday Morning")
        clickFirst("Calendar settings")
        awaitText("Sections")
    }

    private fun stored(folder: File) = CalendarStore(folder).load().document

    @Test
    fun `the copy sheet offers the dates a service can go to`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")
        clickIcon("Copy this service")
        awaitText("Copy")

        assertTrue(shows("Run of show"), "what travels with it")
        assertTrue(shows("Repeat") || shows("Weekly"), "and how often")
    }

    @Test
    fun `a service can be copied to next week`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")
        clickIcon("Copy this service")
        awaitText("Copy")

        clickFirst("Next week")
        waitForIdle()
        clickLast("Paste")
        waitForIdle()
        // The sheet closes on the copy, which is the signal the calendar took it.
        waitUntil("the sheet closed") { !shows("Paste on") }

        assertEquals(2, stored(folder).services.size, "the service and its copy")
    }

    @Test
    fun `a template keeps a run of show for next time`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")
        clickIcon("Save this run of show")
        awaitText("Template")

        typeIntoLastField("Standard Sunday")
        clickFirst("Save template")
        waitForIdle()

        assertTrue(stored(folder).templates.any { it.name.contains("Standard") }, "saved to the file")
    }

    @Test
    fun `a section added in settings is kept`() = withCalendar(documentWith(service())) { folder ->
        openSettings()

        // The row is a dashed "add" button until it is pressed, and a field after it.
        clickFirst("Add a section name")
        waitForIdle()
        typeIntoLastField("Baptism")
        clickLast("Done")
        waitForIdle()

        assertTrue(
            stored(folder).preferences.sections.any { it.name == "Baptism" },
            "a section belongs to the calendar, not to one service",
        )
    }

    @Test
    fun `a section can be dropped into the open run of show`() = withCalendar(documentWith(service())) { folder ->
        openSettings()

        clickFirst("Insert")
        waitForIdle()

        assertTrue(stored(folder).services.single().items.size >= 3, "the heading landed in the service")
    }

    @Test
    fun `the clock format is a preference and it sticks`() = withCalendar(documentWith(service())) { folder ->
        openSettings()
        clickFirst("Defaults")
        awaitText("Time format")

        clickFirst("12")
        waitForIdle()

        assertEquals(false, stored(folder).preferences.use24HourClock)
    }

    @Test
    fun `the auto-load switch is a preference and it sticks`() = withCalendar(documentWith(service())) { folder ->
        openSettings()
        clickFirst("Defaults")
        awaitText("Load the service automatically")

        toggleSwitch()
        waitForIdle()

        assertTrue(stored(folder).preferences.autoLoadService, "off by default, on once asked for")
    }

    @Test
    fun `the arm-by-default preference is gone with the automation tab`() =
        withCalendar(documentWith(service())) {
            openSettings()

            assertTrue(!shows("Automation"), "cues are rows of the run of show, not a settings tab")
        }
}
