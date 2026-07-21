package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The play/pause/reset buttons under the countdown, and what they do to the live output.
 *
 * The timer itself ticks on [PresenterManager] rather than here, so that a countdown survives the
 * operator switching away from the Announcements tab. That split is the subtle part: this view
 * model only decides *which* of the manager's four tickers to start, and whether a click means
 * start, resume or pause — and the running-state flag to check differs by mode (Duration and
 * Count-up set `timerRunning`; Specific Time and Clock Display never do, and must be judged by
 * `announcementTickerActive` instead). Reading the wrong flag makes the button stop responding
 * mid-service.
 *
 * Nothing here waits on a tick: every flag the assertions read is set synchronously by the start
 * call, before its coroutine does any work.
 */
class AnnouncementsTimerControlTest {

    private val created = mutableListOf<AnnouncementsViewModel>()

    @AfterTest
    fun disposeAll() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    private fun vm(): AnnouncementsViewModel = AnnouncementsViewModel().also { created.add(it) }

    /** A view model in [mode] with a five-minute duration configured. */
    private fun vm(mode: String, minutes: Int = 5): AnnouncementsViewModel = vm().apply {
        setTimerMode(mode)
        setTimerMinutes(minutes)
    }

    private fun pm() = PresenterManager()

    // ── Duration countdown ──────────────────────────────────────────────────────

    @Test
    fun `starting a duration countdown runs it for the configured time`() {
        val vm = vm(Constants.TIMER_MODE_DURATION)
        val pm = pm()

        vm.startPauseTimer(pm)

        assertTrue(pm.timerRunning.value)
        assertTrue(pm.announcementTickerActive.value)
        assertTrue(
            pm.timerRemainingSeconds.value in 299..300,
            // The ticker recomputes from the wall clock, so its first tick reads 299 if the epoch
            // second rolls over between the start call and that tick. Pinning 300 exactly would be
            // asserting on timing.
            "expected roughly the configured 5 minutes, got ${pm.timerRemainingSeconds.value}"
        )
    }

    @Test
    fun `clicking again pauses the countdown where it stands`() {
        val vm = vm(Constants.TIMER_MODE_DURATION)
        val pm = pm()
        vm.startPauseTimer(pm)

        vm.startPauseTimer(pm)

        assertFalse(pm.timerRunning.value)
        assertFalse(pm.announcementTickerActive.value)
        assertTrue(
            pm.timerRemainingSeconds.value in 299..300,
            "pausing must keep the remaining time, not clear it; got ${pm.timerRemainingSeconds.value}"
        )
    }

    @Test
    fun `resuming continues from the remaining time, not the configured duration`() {
        val vm = vm(Constants.TIMER_MODE_DURATION)
        val pm = pm()
        // Paused with 42s left, with no ticker running. Starting a real countdown first and then
        // pausing it would race: pauseAnnouncementTimer cancels the ticker and then pins the value,
        // but cancellation only takes effect at the ticker's next suspension point, so its first
        // iteration can write the full duration back afterwards.
        pm.pauseAnnouncementTimer(42)

        vm.startPauseTimer(pm)

        assertTrue(
            pm.timerRemainingSeconds.value in 41..42,
            "resume must not restart the countdown from the top; got ${pm.timerRemainingSeconds.value}"
        )
        assertTrue(pm.timerRunning.value)
    }

    @Test
    fun `a countdown of zero does not start`() {
        val vm = vm(Constants.TIMER_MODE_DURATION, minutes = 0)
        val pm = pm()

        vm.startPauseTimer(pm)

        assertFalse(pm.timerRunning.value, "an empty timer would go straight to the expired message")
        assertFalse(pm.announcementTickerActive.value)
    }

    // ── Count-up stopwatch ──────────────────────────────────────────────────────

    @Test
    fun `starting the stopwatch runs it from where it was left`() {
        val vm = vm(Constants.TIMER_MODE_COUNT_UP)
        val pm = pm()
        pm.pauseAnnouncementTimer(75) // 1:15 already elapsed

        vm.startPauseTimer(pm)

        assertTrue(pm.timerRunning.value)
        assertTrue(pm.announcementTickerActive.value)
        assertTrue(
            pm.timerRemainingSeconds.value in 75..76,
            "expected the stopwatch to resume near 1:15, got ${pm.timerRemainingSeconds.value}"
        )
    }

    @Test
    fun `clicking again pauses the stopwatch`() {
        val vm = vm(Constants.TIMER_MODE_COUNT_UP)
        val pm = pm()
        vm.startPauseTimer(pm)

        vm.startPauseTimer(pm)

        assertFalse(pm.timerRunning.value)
        assertFalse(pm.announcementTickerActive.value)
    }

    @Test
    fun `a stopwatch starts from zero even with a duration configured`() {
        val vm = vm(Constants.TIMER_MODE_COUNT_UP, minutes = 5)
        val pm = pm()

        vm.startPauseTimer(pm)

        assertTrue(
            pm.timerRemainingSeconds.value in 0..1,
            "the H/M/S fields belong to the countdown, not the stopwatch; got ${pm.timerRemainingSeconds.value}"
        )
    }

    // ── Specific time ───────────────────────────────────────────────────────────

    @Test
    fun `starting a specific-time countdown runs its ticker`() {
        val vm = vm(Constants.TIMER_MODE_CLOCK)
        val pm = pm()

        vm.startPauseTimer(pm)

        assertTrue(pm.announcementTickerActive.value)
        assertFalse(
            pm.timerRunning.value,
            "specific time recomputes from the wall clock, so timerRunning stays false — the button must not read it"
        )
    }

    @Test
    fun `clicking again stops the specific-time ticker`() {
        val vm = vm(Constants.TIMER_MODE_CLOCK)
        val pm = pm()
        vm.startPauseTimer(pm)

        vm.startPauseTimer(pm)

        assertFalse(pm.announcementTickerActive.value, "judged by the ticker flag, since timerRunning is never set")
    }

    // ── Clock display ───────────────────────────────────────────────────────────

    @Test
    fun `starting the live clock runs its ticker`() {
        val vm = vm(Constants.TIMER_MODE_CLOCK_DISPLAY)
        val pm = pm()

        vm.startPauseTimer(pm)

        assertTrue(pm.announcementTickerActive.value)
        assertFalse(pm.timerRunning.value)
    }

    @Test
    fun `clicking again stops the live clock`() {
        val vm = vm(Constants.TIMER_MODE_CLOCK_DISPLAY)
        val pm = pm()
        vm.startPauseTimer(pm)

        vm.startPauseTimer(pm)

        assertFalse(pm.announcementTickerActive.value)
    }

    // ── No presenter ────────────────────────────────────────────────────────────

    @Test
    fun `the timer controls are inert with no presenter attached`() {
        val vm = vm(Constants.TIMER_MODE_DURATION)
        vm.startPauseTimer(null)
        vm.pauseTimer(null)
        vm.resetTimer(null)
    }

    // ── Explicit pause ──────────────────────────────────────────────────────────

    @Test
    fun `pausing stops whichever ticker is running`() {
        val vm = vm(Constants.TIMER_MODE_CLOCK_DISPLAY)
        val pm = pm()
        vm.startPauseTimer(pm)

        vm.pauseTimer(pm)

        assertFalse(
            pm.announcementTickerActive.value,
            "a live clock left ticking would overwrite plain announcement text within a second"
        )
    }

    @Test
    fun `pausing releases the timer's claim on the live slot`() {
        val vm = vm(Constants.TIMER_MODE_DURATION)
        val pm = pm()
        vm.startPauseTimer(pm)
        pm.setAnnouncementTickerLive(true)

        vm.pauseTimer(pm)

        assertFalse(
            pm.announcementTickerLive.value,
            "otherwise a later Resume click silently pushes the timer back over the text that took the slot"
        )
    }

    // ── Reset ───────────────────────────────────────────────────────────────────

    @Test
    fun `resetting a countdown puts the configured duration back`() {
        val vm = vm(Constants.TIMER_MODE_DURATION)
        val pm = pm()
        pm.pauseAnnouncementTimer(42) // paused with 42s left, no ticker running

        vm.resetTimer(pm)

        assertEquals(300, pm.timerRemainingSeconds.value)
        assertFalse(pm.timerRunning.value)
    }

    @Test
    fun `resetting the stopwatch puts it back to zero`() {
        val vm = vm(Constants.TIMER_MODE_COUNT_UP)
        val pm = pm()
        pm.pauseAnnouncementTimer(75) // paused at 1:15 elapsed, no ticker running

        vm.resetTimer(pm)

        assertEquals(0, pm.timerRemainingSeconds.value)
        assertFalse(pm.timerRunning.value)
    }

    @Test
    fun `resetting a specific-time countdown leaves it running`() {
        val vm = vm(Constants.TIMER_MODE_CLOCK)
        val pm = pm()
        vm.startPauseTimer(pm)

        vm.resetTimer(pm)

        assertTrue(
            pm.announcementTickerActive.value,
            "a target time always tracks the wall clock — there is nothing to reset it back to"
        )
    }

    // ── Going live ──────────────────────────────────────────────────────────────

    @Test
    fun `going live puts the announcement text on screen`() {
        val vm = vm()
        vm.setText("Welcome to the 10am service")
        val pm = pm()

        vm.goLive(pm) { }

        assertEquals("Welcome to the 10am service", pm.announcementText.value)
        assertEquals(Presenting.ANNOUNCEMENTS, pm.presentingMode.value)
    }

    @Test
    fun `going live stops a running timer from overwriting the text`() {
        val vm = vm(Constants.TIMER_MODE_CLOCK_DISPLAY)
        vm.setText("Welcome")
        val pm = pm()
        vm.startPauseTimer(pm)
        pm.setAnnouncementTickerLive(true)

        vm.goLive(pm) { }

        assertFalse(pm.announcementTickerActive.value, "the clock would replace the text on its next tick")
        assertFalse(pm.announcementTickerLive.value)
        assertEquals("Welcome", pm.announcementText.value)
    }

    @Test
    fun `going live persists what was on screen`() {
        val vm = vm()
        vm.setText("Welcome")
        vm.setFontSize(96)
        var saved = AppSettings()

        vm.goLive(pm()) { transform -> saved = transform(saved) }

        assertEquals("Welcome", saved.announcementsSettings.text, "the text must survive a restart mid-service")
        assertEquals(96, saved.announcementsSettings.fontSize)
    }

    @Test
    fun `saving hands over the current settings without going live`() {
        val vm = vm()
        vm.setText("Draft")
        var saved = AppSettings()
        val pm = pm()

        vm.saveToSettings { transform -> saved = transform(saved) }

        assertEquals("Draft", saved.announcementsSettings.text)
        assertEquals(Presenting.NONE, pm.presentingMode.value)
    }

    // ── Timer formatting ────────────────────────────────────────────────────────

    @Test
    fun `a timer under an hour is shown as minutes and seconds`() {
        assertEquals("00:00", AnnouncementsViewModel.formatTimer(0))
        assertEquals("00:09", AnnouncementsViewModel.formatTimer(9))
        assertEquals("00:59", AnnouncementsViewModel.formatTimer(59))
        assertEquals("01:00", AnnouncementsViewModel.formatTimer(60))
        assertEquals("05:30", AnnouncementsViewModel.formatTimer(330))
        assertEquals("59:59", AnnouncementsViewModel.formatTimer(3599))
    }

    @Test
    fun `an hour or more gains an hours field, unpadded`() {
        assertEquals("1:00:00", AnnouncementsViewModel.formatTimer(3600))
        assertEquals("1:01:01", AnnouncementsViewModel.formatTimer(3661))
        assertEquals("10:00:00", AnnouncementsViewModel.formatTimer(36_000))
        assertEquals("23:59:59", AnnouncementsViewModel.formatTimer(86_399))
    }
}
