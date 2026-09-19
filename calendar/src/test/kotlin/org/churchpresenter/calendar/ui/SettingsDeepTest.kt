@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.SavedTemplate
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings dialog: what every service inherits, and the two lists it keeps.
 *
 * Everything here is written straight to `calendar.json` (or `presets.json`), so each test ends at
 * the file. The defaults matter more than they look: they are what a *new* service and an
 * unmeasured row start from, so a value that fails to save is a wrong plan every week after.
 */
class SettingsDeepTest {

    /** A line only the dialog draws — the window behind it has a "Calendar settings" button too. */
    private val DIALOG = "Applies to every service"

    private fun stored(folder: File) = CalendarStore(folder).load().document
    private fun preferences(folder: File) = stored(folder).preferences

    private fun ComposeUiTest.openSettings(tab: String = "Sections") {
        awaitText("Sunday Morning")
        clickFirst("Calendar settings")
        awaitText("Sections")
        if (tab != "Sections") clickInSheetContaining(tab, anchor = DIALOG)
    }

    // ── Sections ────────────────────────────────────────────────────────────────────────────────

    /**
     * A section renamed in place is renamed in the file.
     *
     * The *add* row is not driven here: it is the last row of a scrolling list, and reaching it in
     * a test means scrolling a list whose length is the number of sections — `StateGuardsTest`
     * covers what adding does, including that a duplicate is refused, at the state it writes to.
     */
    @Test
    fun `a section renamed in place is renamed in the file`() = withCalendar(documentWith(service())) { folder ->
        openSettings()

        clearFieldAt(0)
        typeIntoFieldAt(index = 0, text = "Gathering")
        commitByLeavingField(focus = 1)

        assertTrue(preferences(folder).sections.any { it.name == "Gathering" }, "renamed")
        assertTrue(preferences(folder).sections.none { it.name == "Pre-Service" }, "and the old name is gone")
    }

    @Test
    fun `the sections a calendar starts with are the ones an order of service has`() =
        withCalendar(documentWith(service())) {
            openSettings()

            listOf("Pre-Service", "Worship", "Word", "Response", "Communion", "Closing")
                .forEach { assertTrue(showsInSheet(it, anchor = DIALOG), "$it is missing") }
        }

    @Test
    fun `a section can be removed`() = withCalendar(documentWith(service())) { folder ->
        val before = preferences(folder).sections.size
        openSettings()

        clickIcon("Remove section")

        assertEquals(before - 1, preferences(folder).sections.size)
    }

    // ── Defaults ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the clock format is a setting, and every time follows it`() =
        withCalendar(documentWith(service())) { folder ->
            openSettings(tab = "Defaults")

            clickInSheetContaining("24-hour", anchor = DIALOG)

            assertTrue(preferences(folder).use24HourClock)
            assertTrue(showsInSheet("10:00", anchor = DIALOG), "and the default start is redrawn")
        }

    @Test
    fun `a default length is typed in place`() = withCalendar(documentWith(service())) { folder ->
        openSettings(tab = "Defaults")

        // The item length is the second field of the tab: the start time is the first.
        clearFieldAt(index = 1)
        typeIntoFieldAt(index = 1, text = "5:30")
        commitByLeavingField()

        assertEquals(330, preferences(folder).defaultItemSeconds)
    }

    @Test
    fun `a default that cannot be read is put back as it was`() = withCalendar(documentWith(service())) { folder ->
        val before = preferences(folder).defaultItemSeconds
        openSettings(tab = "Defaults")

        clearFieldAt(index = 1)
        typeIntoFieldAt(index = 1, text = "soon")
        commitByLeavingField()

        assertEquals(before, preferences(folder).defaultItemSeconds, "a length that is not a length is refused")
    }

    @Test
    fun `auto-load is off until it is asked for, and then says how far ahead`() =
        withCalendar(documentWith(service())) { folder ->
            openSettings(tab = "Defaults")
            assertTrue(!preferences(folder).autoLoadService)

            toggleSwitch()

            assertTrue(preferences(folder).autoLoadService)
            assertTrue(showsInSheet("How far ahead", anchor = DIALOG), "the lead comes with it")
        }

    @Test
    fun `the lead is typed in minutes`() = withCalendar(
        documentWith(service()).copy(preferences = CalendarPreferences(autoLoadService = true))
    ) { folder ->
        openSettings(tab = "Defaults")
        awaitText("How far ahead")

        clearLastField()
        typeIntoLastField("20")
        commitByLeavingField()

        assertEquals(20, preferences(folder).autoLoadLeadMinutes)
    }

    @Test
    fun `a lead outside what the window offers is refused`() = withCalendar(
        documentWith(service())
            .copy(preferences = CalendarPreferences(autoLoadService = true, autoLoadLeadMinutes = 10))
    ) { folder ->
        openSettings(tab = "Defaults")
        awaitText("How far ahead")

        clearLastField()
        typeIntoLastField("999")
        commitByLeavingField()

        assertEquals(10, preferences(folder).autoLoadLeadMinutes, "the old lead stands")
    }

    // ── The two lists ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a saved template is listed with what it holds, and can be deleted`() {
        val template = SavedTemplate(
            id = "t1", name = "Sunday Morning", startTime = "10:00", kind = ServiceKind.SUNDAY.id,
            items = listOf(song("a", "Amazing Grace"), heading("h", "Worship")),
        )
        withCalendar(documentWith(service()).copy(templates = listOf(template))) { folder ->
            openSettings(tab = "Templates")

            assertTrue(showsInSheet("Sunday Morning", anchor = DIALOG))
            assertTrue(showsInSheet("items", anchor = DIALOG), "and how many rows it carries")

            clickIcon("Delete template")

            assertTrue(stored(folder).templates.isEmpty())
        }
    }

    @Test
    fun `a preset saved by a tab is listed here, and can be deleted`() =
        withCalendar(documentWith(service())) { folder ->
            seedPresets(
                folder,
                ItemPreset("p1", "Welcome loop", ScheduleItem.PictureItem("i", "/pics", "Welcome", 12)),
            )
            openSettings(tab = "Presets")
            awaitText("Welcome loop")

            clickIcon("Delete preset")

            assertTrue(PresetStore(folder).load().presets.isEmpty())
        }
}
