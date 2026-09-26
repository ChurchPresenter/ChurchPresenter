@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.calendar.CalendarCloudSync
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarStore
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the window says about the app around it -- when it next syncs, and that services do not
 * load by themselves -- and the two ways a service changes place: moved to another day, or made
 * from what is in the app's Schedule tab.
 */
class SchedulingAdditionsTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun syncing(nextSyncAt: () -> Long?) = CalendarHost(
        cloudSync = CalendarCloudSync(enabled = { true }, setEnabled = {}, nextSyncAt = nextSyncAt),
    )

    // ── The auto-load note ──────────────────────────────────────────────────────────────────────

    @Test
    fun `with auto-load off the window says so, and Turn on switches it on`() =
        withCalendar(documentWith(service())) { folder ->
            awaitText("Services don’t load into the Schedule by themselves")

            // The last: the note's own sentence says "Turn on" too.
            clickLast("Turn on")

            assertTrue(stored(folder).preferences.autoLoadService)
            assertFalse(shows("Services don’t load into the Schedule by themselves"))
        }

    @Test
    fun `dismissing the note hides it and changes nothing`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Services don’t load into the Schedule by themselves")

        clickLast("Dismiss")

        assertFalse(shows("Services don’t load into the Schedule by themselves"))
        assertFalse(stored(folder).preferences.autoLoadService)
    }

    @Test
    fun `with auto-load on there is no note`() {
        val document = documentWith(service())
            .let { it.copy(preferences = it.preferences.copy(autoLoadService = true)) }
        withCalendar(document) {
            awaitText("Amazing Grace")
            assertFalse(shows("Services don’t load into the Schedule by themselves"))
        }
    }

    // ── The sync countdown ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the header counts down to the next sync`() =
        // Always 252 s ahead of whenever it is asked, so the text cannot tick while it is read.
        withCalendar(documentWith(service()), host = syncing { System.currentTimeMillis() + 252_000 }) {
            awaitText("Next sync in 4:12")
        }

    @Test
    fun `a sync that is due reads as syncing`() =
        withCalendar(documentWith(service()), host = syncing { System.currentTimeMillis() - 1_000 }) {
            awaitText("Syncing…")
        }

    @Test
    fun `no countdown without a sync scheduled, or with sync off`() {
        withCalendar(documentWith(service()), host = syncing { null }) {
            awaitText("Amazing Grace")
            assertFalse(shows("Next sync"))
        }
        val off = CalendarHost(
            cloudSync = CalendarCloudSync(enabled = { false }, setEnabled = {}, nextSyncAt = { 0L }),
        )
        withCalendar(documentWith(service()), host = off) {
            awaitText("Amazing Grace")
            assertFalse(shows("Next sync"))
            assertFalse(shows("Syncing"))
        }
    }

    // ── Moving a service to another day ─────────────────────────────────────────────────────────

    @Test
    fun `a service is moved to another day from its date field`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")
        clickIcon("Edit service")
        awaitText("Edit service")

        // The field reads the day in full, the way the reader's language writes it -- as does the day
        // heading behind the sheet, so the field is the last of the two.
        clickLast(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()).format(TODAY))
        // The picker is the last thing opened, so its "27" is the last one on screen.
        val days = onAllNodesWithText("27", substring = false)
        days[days.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
        clickInSheet("Save", anchor = "Edit service")

        assertEquals(TODAY.plusDays(7).toString(), stored(folder).services.single().date)
    }

    // ── A service made from the Schedule tab ────────────────────────────────────────────────────

    private val inTheSchedule = CalendarHost(
        currentSchedule = { listOf(song("hand-1", "Built by hand"), song("hand-2")) },
    )

    @Test
    fun `a new service can start from the Schedule tab, keeping its rows`() =
        withCalendar(host = inTheSchedule) { folder ->
            clickFirst("Add service")
            awaitText("New service")
            assertTrue(shows("The Schedule tab"))
            assertTrue(shows("2 rows, as they are there now"))

            clickInSheet("The Schedule tab", anchor = "New service")
            typeIntoFirstField("Built here")
            clickInSheet("Add service", anchor = "New service")

            val service = stored(folder).services.single()
            assertEquals(listOf("hand-1", "hand-2"), service.items.map { it.id }, "the Schedule is this service now")
        }

    @Test
    fun `the app can open the window straight onto a service from the Schedule`() =
        withCalendar(host = inTheSchedule, newServiceFromSchedule = 1) { folder ->
            awaitText("New service")

            typeIntoFirstField("Built here")
            clickInSheet("Add service", anchor = "New service")

            assertEquals(listOf("hand-1", "hand-2"), stored(folder).services.single().items.map { it.id })
        }

    @Test
    fun `an empty Schedule is not offered to start from`() = withCalendar {
        clickFirst("Add service")
        awaitText("New service")

        assertFalse(shows("The Schedule tab"))
    }
}
