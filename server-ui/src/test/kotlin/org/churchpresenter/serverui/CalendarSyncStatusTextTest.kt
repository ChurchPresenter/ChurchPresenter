@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.serverui

import androidx.compose.material3.Text
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.sync.SyncOutcome
import org.churchpresenter.server.CalendarSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sync status in words, as the card and the invite dialog both say it -- one sentence per
 * status that is not a successful sync, so an operator can tell "off" from "locked out" at a glance.
 */
class CalendarSyncStatusTextTest {

    private fun wordsFor(status: CalendarSyncStatus): String {
        var words = ""
        runComposeUiTest {
            setContent { Text(calendarSyncStatusText(status).also { words = it }) }
            waitForIdle()
        }
        return words
    }

    @Test
    fun `each status that is not a sync says what it is`() {
        assertEquals("Off", wordsFor(CalendarSyncStatus.Off))
        assertEquals("Syncing…", wordsFor(CalendarSyncStatus.Syncing))
        assertEquals(
            "Not registered yet — this computer registers with the relay the first time sync is on",
            wordsFor(CalendarSyncStatus.Unpaired),
        )
        val synced = wordsFor(CalendarSyncStatus.Synced("2026-09-20T10:30:00Z", SyncOutcome(1, 2, 0, 0)))
        assertTrue(synced.startsWith("Synced "), synced)
        assertEquals("Could not reach the relay: refused", wordsFor(CalendarSyncStatus.Failed("refused")))
        assertEquals(
            "The relay no longer accepts this computer. Pair again to continue.",
            wordsFor(CalendarSyncStatus.Unauthorized),
        )
        assertEquals(
            "Another computer is syncing this calendar. This one has stopped pushing until you unpair one of them.",
            wordsFor(CalendarSyncStatus.OtherDesktop("install-2")),
        )
    }

    @Test
    fun `a round that ran out of time at startup warns that changes may be missing`() {
        assertEquals(
            "The relay did not answer in time at startup; this week’s changes from phones may be missing",
            wordsFor(CalendarSyncStatus.TimedOut),
        )
    }
}
