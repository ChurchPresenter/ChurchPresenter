package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The announcement timer/clock state machine on [PresenterManager].
 *
 * Each `start…` sets the observable timer state synchronously and then launches a background
 * ticker; the tests assert the synchronous state the operator's UI binds to (running, active,
 * expired, remaining) and cancel the ticker in teardown so no coroutine outlives the test. The
 * tickers only push text to the live output when something is actually on the Announcements mode,
 * which nothing here is, so they have no visible side effect while running.
 */
class PresenterManagerAnnouncementTimerTest {

    private val managers = mutableListOf<PresenterManager>()

    private fun manager() = PresenterManager().also { managers.add(it) }

    @AfterTest
    fun stopTickers() {
        // Cancel every launched ticker so it cannot tick on into another test.
        managers.forEach { runCatching { it.pauseAnnouncementTimer() } }
        managers.clear()
    }

    // ── Countdown ────────────────────────────────────────────────────────────────

    @Test
    fun `a countdown starts running with its full time on the clock`() {
        val pm = manager()

        pm.startAnnouncementCountdown(remainingSeconds = 90, expiredText = "Time's up")

        assertEquals(90, pm.timerRemainingSeconds.value)
        assertTrue(pm.timerRunning.value)
        assertTrue(pm.announcementTickerActive.value)
        assertFalse(pm.announcementTimerExpired.value, "a fresh countdown has not expired yet")
    }

    @Test
    fun `a countdown of zero or less never starts`() {
        // Guards against a "0:00" schedule item or a finished timer being re-armed at zero.
        val pm = manager()

        pm.startAnnouncementCountdown(remainingSeconds = 0, expiredText = "done")

        assertFalse(pm.timerRunning.value, "there is nothing to count down")
        assertFalse(pm.announcementTickerActive.value)
    }

    // ── Count-up ─────────────────────────────────────────────────────────────────

    @Test
    fun `a count-up starts running from its initial elapsed time`() {
        val pm = manager()

        pm.startAnnouncementCountUp(initialElapsedSeconds = 30)

        assertEquals(30, pm.timerRemainingSeconds.value)
        assertTrue(pm.timerRunning.value)
        assertTrue(pm.announcementTickerActive.value)
    }

    // ── Always-on clock modes ────────────────────────────────────────────────────

    @Test
    fun `specific-time and clock-display are active but not a running countdown`() {
        // These are open-ended displays, not a countdown the operator can pause to a remaining
        // value, so they are "active" (a ticker is running) but not "running" (a timer).
        manager().let { pm ->
            pm.startAnnouncementSpecificTime(targetHour = 10, targetMinute = 30, targetSecond = 0)
            assertTrue(pm.announcementTickerActive.value)
            assertFalse(pm.timerRunning.value)
        }
        manager().let { pm ->
            pm.startAnnouncementClockDisplay(formatPattern = "HH:mm:ss")
            assertTrue(pm.announcementTickerActive.value)
            assertFalse(pm.timerRunning.value)
        }
    }

    // ── Pausing ──────────────────────────────────────────────────────────────────

    @Test
    fun `pausing stops the ticker and clears the running and expired flags`() {
        val pm = manager()
        pm.startAnnouncementCountdown(remainingSeconds = 60, expiredText = "done")

        pm.pauseAnnouncementTimer()

        assertFalse(pm.timerRunning.value)
        assertFalse(pm.announcementTickerActive.value)
        assertFalse(pm.announcementTimerExpired.value)
    }

    @Test
    fun `pausing with a pinned value mirrors that remaining time`() {
        // Reset pins the remaining value so a follower shows the reset time, not the last tick.
        val pm = manager()
        pm.startAnnouncementCountdown(remainingSeconds = 60, expiredText = "done")

        pm.pauseAnnouncementTimer(remainingSeconds = 300)

        assertEquals(300, pm.timerRemainingSeconds.value)
        assertFalse(pm.timerRunning.value)
    }

    // ── Going live ───────────────────────────────────────────────────────────────

    @Test
    fun `going live with a duration timer starts a countdown and marks it live`() {
        val pm = manager()

        pm.goLiveAnnouncementTimer(
            timerMode = Constants.TIMER_MODE_DURATION,
            timerHours = 0, timerMinutes = 1, timerSeconds = 30,
            targetHour = 0, targetMinute = 0, targetSecond = 0,
            liveClockFormat = "HH:mm", timerExpiredText = "Time's up",
        )

        assertTrue(pm.announcementTickerLive.value, "going live must mark the ticker live")
        assertEquals(90, pm.timerRemainingSeconds.value, "1m30s is 90 seconds on the clock")
        assertTrue(pm.timerRunning.value)
        assertTrue(pm.announcementText.value.isNotEmpty(), "an immediate value is pushed, not left blank until the first tick")
    }

    @Test
    fun `going live with a clock display marks it live and shows the wall clock`() {
        val pm = manager()

        pm.goLiveAnnouncementTimer(
            timerMode = Constants.TIMER_MODE_CLOCK_DISPLAY,
            timerHours = 0, timerMinutes = 0, timerSeconds = 0,
            targetHour = 0, targetMinute = 0, targetSecond = 0,
            liveClockFormat = "HH:mm", timerExpiredText = "",
        )

        assertTrue(pm.announcementTickerLive.value)
        assertTrue(pm.announcementTickerActive.value)
        assertTrue(pm.announcementText.value.isNotEmpty(), "the clock text is pushed immediately")
    }

    @Test
    fun `going live twice on a running count-up does not restart it`() {
        val pm = manager()
        pm.goLiveAnnouncementTimer(
            timerMode = Constants.TIMER_MODE_COUNT_UP,
            timerHours = 0, timerMinutes = 0, timerSeconds = 0,
            targetHour = 0, targetMinute = 0, targetSecond = 0,
            liveClockFormat = "HH:mm", timerExpiredText = "",
        )
        assertTrue(pm.announcementTickerActive.value)

        // A second go-live of the same running count-up must resume, not reset the elapsed time.
        pm.goLiveAnnouncementTimer(
            timerMode = Constants.TIMER_MODE_COUNT_UP,
            timerHours = 0, timerMinutes = 0, timerSeconds = 0,
            targetHour = 0, targetMinute = 0, targetSecond = 0,
            liveClockFormat = "HH:mm", timerExpiredText = "",
        )

        assertTrue(pm.announcementTickerLive.value)
        assertTrue(pm.announcementTickerActive.value)
    }
}
