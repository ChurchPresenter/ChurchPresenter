package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.presenter.Presenting
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
    fun `starting a timer while another runs cancels the previous ticker`() {
        // Each start cancels whatever ticker is already going, so switching timer type mid-service
        // never leaves two tickers writing to the same output. Chaining every mode exercises that
        // cancel on a non-null job for each entry point.
        val pm = manager()

        pm.startAnnouncementCountUp(0)
        pm.startAnnouncementCountdown(60, "done")            // cancels the count-up
        pm.startAnnouncementCountUp(0)                        // cancels the countdown
        pm.startAnnouncementSpecificTime(10, 0, 0)           // cancels the count-up
        pm.startAnnouncementClockDisplay("HH:mm")            // cancels the specific-time

        assertTrue(pm.announcementTickerActive.value, "the last-started ticker is the live one")
        assertFalse(pm.timerRunning.value, "a clock display is not a running countdown")
    }

    // ── Pushing timer text to the live output ───────────────────────────────────
    //
    // The ticker only writes to the announcement output when a screen is actually showing
    // announcements AND the timer has been sent live; otherwise it stays silent so a timer armed in
    // the background never overwrites whatever is on screen. goLive sets the live flag; pausing then
    // stops the ticker while leaving that flag set, so the gate can be exercised without a live tick.

    /** Arms the live flag the way going live does, then stops the ticker so nothing ticks under us. */
    private fun PresenterManager.armLiveTicker() {
        goLiveAnnouncementTimer(
            timerMode = Constants.TIMER_MODE_DURATION,
            timerHours = 0, timerMinutes = 5, timerSeconds = 0,
            targetHour = 0, targetMinute = 0, targetSecond = 0,
            liveClockFormat = "HH:mm", timerExpiredText = "",
        )
        pauseAnnouncementTimer()
    }

    @Test
    fun `timer text is not pushed when nothing is showing announcements`() {
        val pm = manager()
        pm.armLiveTicker() // live, but no screen is on announcements
        pm.setAnnouncementText("SENTINEL")

        pm.pushAnnouncementTextIfLive("TICK")

        assertEquals("SENTINEL", pm.announcementText.value, "a background timer must not overwrite the live output")
    }

    @Test
    fun `timer text is not pushed when announcements show but the timer is not live`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS) // showing announcements, but nothing armed live
        pm.setAnnouncementText("SENTINEL")

        pm.pushAnnouncementTextIfLive("TICK")

        assertEquals("SENTINEL", pm.announcementText.value, "with no timer sent live there is nothing to push")
    }

    @Test
    fun `timer text is not pushed when the only lock is on something other than announcements`() {
        val pm = manager()
        pm.armLiveTicker()
        pm.setScreenLock(screenIndex = 0, mode = Presenting.PRESENTATION) // a lock, but not announcements
        pm.setAnnouncementText("SENTINEL")

        pm.pushAnnouncementTextIfLive("TICK")

        assertEquals("SENTINEL", pm.announcementText.value, "a lock on other content does not open the announcement gate")
    }

    @Test
    fun `timer text is pushed while announcements are the live mode`() {
        val pm = manager()
        pm.armLiveTicker()
        pm.setPresentingMode(Presenting.ANNOUNCEMENTS)
        pm.setAnnouncementText("SENTINEL")

        pm.pushAnnouncementTextIfLive("00:42")

        assertEquals("00:42", pm.announcementText.value)
    }

    @Test
    fun `timer text is pushed when a screen lock pins announcements even off the live mode`() {
        // A locked output shows announcements regardless of the live mode, so the tick belongs there.
        val pm = manager()
        pm.armLiveTicker()
        pm.setScreenLock(screenIndex = 0, mode = Presenting.ANNOUNCEMENTS) // live mode stays NONE
        pm.setAnnouncementText("SENTINEL")

        pm.pushAnnouncementTextIfLive("00:41")

        assertEquals("00:41", pm.announcementText.value)
    }

    @Test
    fun `going live with a specific-time clock marks it live`() {
        val pm = manager()

        pm.goLiveAnnouncementTimer(
            timerMode = Constants.TIMER_MODE_CLOCK,
            timerHours = 0, timerMinutes = 0, timerSeconds = 0,
            targetHour = 10, targetMinute = 30, targetSecond = 0,
            liveClockFormat = "HH:mm", timerExpiredText = "",
        )

        assertTrue(pm.announcementTickerLive.value)
        assertTrue(pm.announcementTickerActive.value, "a specific-time countdown ticker is running")
        assertFalse(pm.timerRunning.value, "an always-on countdown is not a pausable timer")
    }

    @Test
    fun `going live twice on a running duration timer does not restart it`() {
        val pm = manager()
        pm.goLiveAnnouncementTimer(
            timerMode = Constants.TIMER_MODE_DURATION,
            timerHours = 0, timerMinutes = 2, timerSeconds = 0,
            targetHour = 0, targetMinute = 0, targetSecond = 0,
            liveClockFormat = "HH:mm", timerExpiredText = "",
        )
        assertTrue(pm.announcementTickerActive.value)

        // A second go-live of the same running duration timer resumes rather than resetting the clock.
        pm.goLiveAnnouncementTimer(
            timerMode = Constants.TIMER_MODE_DURATION,
            timerHours = 0, timerMinutes = 2, timerSeconds = 0,
            targetHour = 0, targetMinute = 0, targetSecond = 0,
            liveClockFormat = "HH:mm", timerExpiredText = "",
        )

        assertTrue(pm.announcementTickerLive.value)
        assertTrue(pm.announcementTickerActive.value)
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
