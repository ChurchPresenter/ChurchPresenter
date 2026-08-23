@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsExactly

/**
 * The timer half of the Announcements tab — the countdown a church puts up before a service starts.
 *
 * Its four modes look alike on screen but behave differently, and the differences are what these
 * pin: only two of them count from a starting point (so only those can be reset), only two of them
 * ever expire (so only those take an expiry message), and the play button previews while Go Live is
 * what actually marks the ticker live.
 *
 * Nothing here waits for a tick. The tab renders the timer from state the view model already holds,
 * so the starting value, the mode switches and the buttons are all assertable on the frame after
 * the click; a test that waited for a second to pass would be asserting on the clock rather than on
 * the tab. What a running ticker does over time belongs to `PresenterManagerAnnouncementTimerTest`.
 *
 * See `AnnouncementsTabTestSupport.kt` for the harness.
 */
class AnnouncementsTabTimerTest {

    // ── The modes ───────────────────────────────────────────────────────────────

    @Test
    fun `the timer starts on countdown, showing the configured time`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerMinutes = 5, timerSeconds = 30),
        ) { _, _ ->
            assertTrue(showsExactly("05:30"), "the countdown's starting value: ${renderedText()}")
        }

    @Test
    fun `an hour-long countdown is shown with its hours`() =
        announcementsTab(
            initial = AnnouncementsSettings(timerHours = 1, timerMinutes = 2, timerSeconds = 3),
        ) { _, _ ->
            assertTrue(showsExactly("1:02:03"), "hours are not zero-padded: ${renderedText()}")
        }

    @Test
    fun `each mode can be chosen and is remembered`() = announcementsTab { _, reports ->
        clickLabel(AnnouncementLabel.CLOCK_MODE)
        assertEquals(Constants.TIMER_MODE_CLOCK, reports.settings?.timerMode)

        clickLabel(AnnouncementLabel.CLOCK_DISPLAY_MODE)
        assertEquals(Constants.TIMER_MODE_CLOCK_DISPLAY, reports.settings?.timerMode)

        clickLabel(AnnouncementLabel.DURATION_MODE)
        assertEquals(
            Constants.TIMER_MODE_COUNT_UP,
            reports.settings?.timerMode,
            "the control labelled Duration is the count-up mode",
        )
    }

    @Test
    fun `only the modes that count from a starting point can be reset`() = announcementsTab { _, _ ->
        assertTrue(hasAnnButton(AnnouncementLabel.RESET), "a countdown can be reset")

        clickLabel(AnnouncementLabel.CLOCK_MODE)
        assertFalse(
            hasAnnButton(AnnouncementLabel.RESET),
            "a specific time tracks the wall clock — there is nothing to reset it to",
        )

        clickLabel(AnnouncementLabel.CLOCK_DISPLAY_MODE)
        assertFalse(hasAnnButton(AnnouncementLabel.RESET), "and neither does a live clock")

        clickLabel(AnnouncementLabel.DURATION_MODE)
        assertTrue(hasAnnButton(AnnouncementLabel.RESET), "but a count-up can be reset")
    }

    @Test
    fun `only the modes that can expire offer an expiry message`() = announcementsTab { _, _ ->
        assertTrue(showsExactly(AnnouncementLabel.EXPIRED_HINT), "a countdown reaches an end")

        clickLabel(AnnouncementLabel.CLOCK_DISPLAY_MODE)
        assertFalse(
            showsExactly(AnnouncementLabel.EXPIRED_HINT),
            "a live clock never expires, so it takes no message",
        )

        clickLabel(AnnouncementLabel.CLOCK_MODE)
        assertTrue(showsExactly(AnnouncementLabel.EXPIRED_HINT), "a specific time does reach an end")
    }

    // ── Running it ──────────────────────────────────────────────────────────────

    @Test
    fun `the start button becomes a pause button while it runs`() =
        announcementsTab(initial = AnnouncementsSettings(timerMinutes = 5)) { _, _ ->
            assertTrue(hasAnnButton(AnnouncementLabel.START), "stopped to begin with")

            annButton(AnnouncementLabel.START).performClick()
            waitForIdle()

            assertTrue(hasAnnButton(AnnouncementLabel.PAUSE), "the same button now offers to pause")
            assertFalse(hasAnnButton(AnnouncementLabel.START))
        }

    @Test
    fun `starting the timer is a preview — it does not put it on screen`() =
        announcementsTab(initial = AnnouncementsSettings(timerMinutes = 5)) { presenter, _ ->
            // The operator sets a countdown running to check it before the service without the
            // congregation seeing it; only Go Live is allowed to mark the ticker live.
            annButton(AnnouncementLabel.START).performClick()
            waitForIdle()

            assertFalse(presenter.announcementTickerLive.value, "still preview-only")
            assertFalse(presenter.presentingMode.value == Presenting.ANNOUNCEMENTS)
        }

    @Test
    fun `pausing stops it again`() =
        announcementsTab(initial = AnnouncementsSettings(timerMinutes = 5)) { _, _ ->
            annButton(AnnouncementLabel.START).performClick()
            waitForIdle()
            annButton(AnnouncementLabel.PAUSE).performClick()
            waitForIdle()

            assertTrue(hasAnnButton(AnnouncementLabel.START), "back to offering a start")
        }

    @Test
    fun `taking the timer live marks the ticker live and shows it`() =
        announcementsTab(initial = AnnouncementsSettings(timerMinutes = 5)) { presenter, _ ->
            timerButton(AnnouncementLabel.GO_LIVE).performClick()
            waitForIdle()

            assertTrue(presenter.announcementTickerLive.value, "the ticker is live now")
            assertEquals(Presenting.ANNOUNCEMENTS, presenter.presentingMode.value)
            assertEquals(
                "05:00",
                presenter.announcementText.value,
                "and what went on screen is the timer, not the announcement text",
            )
        }

    @Test
    fun `switching mode stops a running timer rather than leaving it ticking`() =
        announcementsTab(initial = AnnouncementsSettings(timerMinutes = 5)) { presenter, _ ->
            annButton(AnnouncementLabel.START).performClick()
            waitForIdle()
            assertTrue(hasAnnButton(AnnouncementLabel.PAUSE), "running to begin with")

            clickLabel(AnnouncementLabel.CLOCK_MODE)

            // A ticker left running would be counting for a mode that is no longer selected.
            assertTrue(hasAnnButton(AnnouncementLabel.START), "it was stopped")
            assertFalse(presenter.announcementTickerLive.value)
        }

    // ── Scheduling a timer ──────────────────────────────────────────────────────

    @Test
    fun `a timer can be scheduled even with no announcement text`() =
        announcementsTab(initial = AnnouncementsSettings(timerMinutes = 10)) { _, reports ->
            // The text-side button stays shut without text; the timer's does not, because the
            // countdown is the content.
            timerButton(AnnouncementLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            val item = reports.scheduled.single()
            assertEquals(10, item.timerMinutes, "the countdown is carried whole")
            assertEquals(Constants.TIMER_MODE_DURATION, item.timerMode)
        }

    @Test
    fun `a countdown of zero with no text is not worth scheduling`() = announcementsTab { _, _ ->
        // Nothing set at all: an item that shows 00:00 and no words is never what was meant.
        timerButton(AnnouncementLabel.ADD_TO_SCHEDULE).assertIsNotEnabled()
    }

    @Test
    fun `a clock is worth scheduling even with nothing typed and no countdown set`() =
        announcementsTab { _, reports ->
            clickLabel(AnnouncementLabel.CLOCK_DISPLAY_MODE)
            timerButton(AnnouncementLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            assertEquals(
                Constants.TIMER_MODE_CLOCK_DISPLAY,
                reports.scheduled.single().timerMode,
                "a clock has content of its own",
            )
        }

    @Test
    fun `the expiry message is kept against the timer`() = announcementsTab { _, reports ->
        expiredTextField().performTextReplacement("We are starting")
        waitForIdle()

        assertEquals("We are starting", reports.settings?.timerExpiredText)
    }
}
