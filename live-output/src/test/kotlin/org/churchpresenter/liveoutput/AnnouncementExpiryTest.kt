@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput

import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An announcement's Show For seconds (#991): it comes down on its own once they run out, the way its
 * last loop ending does, and a new one sent live starts the time over.
 */
class AnnouncementExpiryTest {

    private fun settings(seconds: Int, clearsDisplay: Boolean = false): AppSettings {
        val base = AppSettings(announcementsSettings = AnnouncementsSettings(showForSeconds = seconds))
        return base.copy(projectionSettings = base.projectionSettings.copy(overlayEndClearsDisplay = clearsDisplay))
    }

    /** A song live with an announcement over it. */
    private fun songWithAnnouncement() = PresenterManager(showPresenterWindowInitially = false).apply {
        setPresentingMode(Presenting.LYRICS)
        setPresentingMode(Presenting.ANNOUNCEMENTS)
    }

    @Test
    fun `an announcement comes down once its seconds run out, leaving the slide`() = runComposeUiTest {
        val pm = songWithAnnouncement()
        mainClock.autoAdvance = false
        setContent { AnnouncementExpiry(pm, settings(seconds = 2)) }
        mainClock.advanceTimeBy(1_500)
        assertTrue(pm.isLive(Presenting.ANNOUNCEMENTS), "not yet")
        mainClock.advanceTimeBy(1_000)
        assertFalse(pm.isLive(Presenting.ANNOUNCEMENTS))
        assertEquals(Presenting.LYRICS, pm.slideContent.value, "only the announcement, as the System setting says")
    }

    @Test
    fun `with the System setting to clear the display, the whole display clears`() = runComposeUiTest {
        val pm = songWithAnnouncement()
        mainClock.autoAdvance = false
        setContent { AnnouncementExpiry(pm, settings(seconds = 1, clearsDisplay = true)) }
        mainClock.advanceTimeBy(1_500)
        assertTrue(pm.clearDisplayRequested.value)
    }

    @Test
    fun `zero keeps it up`() = runComposeUiTest {
        val pm = songWithAnnouncement()
        mainClock.autoAdvance = false
        setContent { AnnouncementExpiry(pm, settings(seconds = 0)) }
        mainClock.advanceTimeBy(60_000)
        assertTrue(pm.isLive(Presenting.ANNOUNCEMENTS))
    }

    @Test
    fun `a new announcement sent live starts the time over`() = runComposeUiTest {
        val pm = songWithAnnouncement()
        mainClock.autoAdvance = false
        setContent { AnnouncementExpiry(pm, settings(seconds = 2)) }
        mainClock.advanceTimeBy(1_500)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        mainClock.advanceTimeBy(1_000)
        assertTrue(pm.isLive(Presenting.ANNOUNCEMENTS), "resent at 1.5s, so it has 2s from there")
        mainClock.advanceTimeBy(1_500)
        assertFalse(pm.isLive(Presenting.ANNOUNCEMENTS))
    }

    @Test
    fun `one cleared first and put up again later gets its own time`() = runComposeUiTest {
        val pm = songWithAnnouncement()
        mainClock.autoAdvance = false
        setContent { AnnouncementExpiry(pm, settings(seconds = 2)) }
        mainClock.advanceTimeBy(1_000)
        pm.clearOverlay(Presenting.ANNOUNCEMENTS)
        mainClock.advanceTimeBy(500)
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        mainClock.advanceTimeBy(1_000)
        assertTrue(pm.isLive(Presenting.ANNOUNCEMENTS), "the first one's clock was cancelled with it")
    }
}
