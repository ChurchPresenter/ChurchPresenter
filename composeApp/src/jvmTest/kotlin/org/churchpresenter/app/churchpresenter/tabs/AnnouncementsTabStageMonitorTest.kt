@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.data.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.data.settings.ProjectionSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sending the announcement (or the timer) to a dedicated Stage Monitor screen, locking it there
 * without disturbing whatever the main projection screen(s) are currently live with.
 *
 * Needs `AppSettings.projectionSettings` to actually assign a screen to Stage Monitor — with the
 * default single-screen setup nothing here is reachable at all, which is why none of the other
 * `AnnouncementsTab*Test` files exercise it. See `AnnouncementsTabTestSupport.kt` for the harness.
 */
class AnnouncementsTabStageMonitorTest {

    private val separateMainScreen = ProjectionSettings(
        screenAssignments = listOf(
            ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN),
            ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
        ),
    )

    private val onlyStageMonitorScreen = ProjectionSettings(
        screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)),
    )

    @Test
    fun `with no Stage Monitor screen configured, nothing offers to send there`() =
        announcementsTab { _, _ ->
            typeAnnouncement("Notices")

            assertFalse(hasAnnButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR))
        }

    @Test
    fun `with a separate main screen, sending locks only the Stage Monitor screen`() =
        announcementsTab(projectionSettings = separateMainScreen) { presenter, _ ->
            typeAnnouncement("Notices")

            annButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR).performClick()
            waitForIdle()

            assertEquals(Presenting.ANNOUNCEMENTS, presenter.screenLocks.value[1])
            assertNull(presenter.screenLocks.value[0], "the main screen must stay free")
            assertEquals("Notices", presenter.announcementText.value)
            assertTrue(hasAnnButton(AnnouncementLabel.HIDE_FROM_STAGE_MONITOR), "the button now offers to undo it")
        }

    @Test
    fun `hiding from the Stage Monitor releases its lock`() =
        announcementsTab(projectionSettings = separateMainScreen) { presenter, _ ->
            typeAnnouncement("Notices")
            annButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR).performClick()
            waitForIdle()

            annButton(AnnouncementLabel.HIDE_FROM_STAGE_MONITOR).performClick()
            waitForIdle()

            assertNull(presenter.screenLocks.value[1])
            assertTrue(hasAnnButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR), "back to offering to send")
        }

    @Test
    fun `when every screen is Stage Monitor, sending just goes live directly`() =
        announcementsTab(projectionSettings = onlyStageMonitorScreen) { presenter, _ ->
            typeAnnouncement("Notices")

            annButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR).performClick()
            waitForIdle()

            assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
            assertEquals("Notices", presenter.announcementText.value)
            assertTrue(
                presenter.screenLocks.value.isEmpty(),
                "there is nothing else to protect, so no per-screen lock is needed",
            )
        }

    @Test
    fun `hiding when every screen is Stage Monitor asks to clear the display`() =
        announcementsTab(projectionSettings = onlyStageMonitorScreen) { presenter, _ ->
            typeAnnouncement("Notices")
            annButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR).performClick()
            waitForIdle()

            annButton(AnnouncementLabel.HIDE_FROM_STAGE_MONITOR).performClick()
            waitForIdle()

            assertTrue(presenter.clearDisplayRequested.value)
        }

    @Test
    fun `sending a Specific Time to the Stage Monitor also starts it`() =
        announcementsTab(projectionSettings = separateMainScreen) { presenter, _ ->
            clickLabel(AnnouncementLabel.CLOCK_MODE)

            timerButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR).performClick()
            waitForIdle()

            assertEquals(Presenting.ANNOUNCEMENTS, presenter.screenLocks.value[1])
            assertTrue(presenter.announcementTickerLive.value, "Specific Time must start ticking, not just preview")
        }

    @Test
    fun `sending the timer to the Stage Monitor also marks its ticker live`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerMinutes = 5),
            projectionSettings = separateMainScreen,
        ) { presenter, _ ->
            timerButton(AnnouncementLabel.SEND_TO_STAGE_MONITOR).performClick()
            waitForIdle()

            assertEquals(Presenting.ANNOUNCEMENTS, presenter.screenLocks.value[1])
            assertTrue(presenter.announcementTickerLive.value)
            assertEquals("05:00", presenter.announcementText.value)
        }
}
