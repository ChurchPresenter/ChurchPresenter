package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The timer field stepping and formatting on [AnnouncementsViewModel] — the spinner arrows next to
 * the H/M/S fields and the countdown display.
 *
 * Seconds step in fives and roll a full turn into the next minute (and minutes into the next hour);
 * the "specific time" target hour wraps around midnight rather than clamping. These are pure,
 * synchronous field mutations (no timer is actually running here), so they're driven directly and
 * read back through the public getters — no PresenterManager, no coroutines.
 */
class AnnouncementsViewModelSteppingTest {

    private val created = mutableListOf<AnnouncementsViewModel>()

    private fun vm() = AnnouncementsViewModel().also { created.add(it) }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    // ── formatTimer ─────────────────────────────────────────────────────────────

    @Test
    fun `under an hour formats as minutes and seconds`() {
        assertEquals("00:00", AnnouncementsViewModel.formatTimer(0))
        assertEquals("01:05", AnnouncementsViewModel.formatTimer(65))
        assertEquals("59:59", AnnouncementsViewModel.formatTimer(3599))
    }

    @Test
    fun `an hour or more adds the hours field`() {
        assertEquals("1:00:00", AnnouncementsViewModel.formatTimer(3600))
        assertEquals("1:01:01", AnnouncementsViewModel.formatTimer(3661))
    }

    // ── plain field setters and clamps ──────────────────────────────────────────

    @Test
    fun `minutes and seconds are clamped to 0-59`() {
        val vm = vm()
        vm.setTimerMinutes(70); assertEquals(59, vm.timerMinutes)
        vm.setTimerMinutes(-3); assertEquals(0, vm.timerMinutes)
        vm.setTimerSeconds(90); assertEquals(59, vm.timerSeconds)
    }

    @Test
    fun `a negative loop count is floored at zero`() {
        val vm = vm()
        vm.setLoopCount(-5); assertEquals(0, vm.loopCount)
        vm.setLoopCount(4); assertEquals(4, vm.loopCount)
    }

    // ── stepTimerSeconds: rounds to fives and rolls into minutes ─────────────────

    @Test
    fun `stepping seconds up snaps to the next multiple of five`() {
        val vm = vm()
        vm.setTimerSeconds(0); vm.stepTimerSeconds(1); assertEquals(5, vm.timerSeconds)
        vm.setTimerSeconds(2); vm.stepTimerSeconds(1); assertEquals(5, vm.timerSeconds, "an off-grid value rounds up to 5")
        vm.setTimerSeconds(5); vm.stepTimerSeconds(1); assertEquals(10, vm.timerSeconds)
    }

    @Test
    fun `stepping seconds up past 55 rolls the minute over`() {
        val vm = vm()
        vm.setTimerMinutes(3); vm.setTimerSeconds(55)

        vm.stepTimerSeconds(1)

        assertEquals(0, vm.timerSeconds)
        assertEquals(4, vm.timerMinutes, "the minute advances when the seconds wrap")
    }

    @Test
    fun `stepping seconds down snaps to the previous multiple of five`() {
        val vm = vm()
        vm.setTimerSeconds(7); vm.stepTimerSeconds(-1); assertEquals(5, vm.timerSeconds)
        vm.setTimerSeconds(5); vm.stepTimerSeconds(-1); assertEquals(0, vm.timerSeconds)
    }

    @Test
    fun `stepping seconds down from zero borrows a minute when there is one`() {
        val vm = vm()
        vm.setTimerMinutes(2); vm.setTimerSeconds(0)

        vm.stepTimerSeconds(-1)

        assertEquals(55, vm.timerSeconds)
        assertEquals(1, vm.timerMinutes, "a minute is borrowed for the wrap")
    }

    @Test
    fun `stepping seconds down from zero at zero total does nothing`() {
        val vm = vm() // 0:00:00

        vm.stepTimerSeconds(-1)

        assertEquals(0, vm.timerSeconds)
        assertEquals(0, vm.timerMinutes)
        assertEquals(0, vm.timerHours)
    }

    // ── stepTimerMinutes: rolls into hours ──────────────────────────────────────

    @Test
    fun `stepping minutes up past 59 rolls the hour over`() {
        val vm = vm()
        vm.setTimerMinutes(59)

        vm.stepTimerMinutes(1)

        assertEquals(0, vm.timerMinutes)
        assertEquals(1, vm.timerHours)
    }

    @Test
    fun `stepping minutes down from zero borrows an hour when there is one`() {
        val vm = vm()
        vm.setTimerHours(1); vm.setTimerMinutes(0)

        vm.stepTimerMinutes(-1)

        assertEquals(59, vm.timerMinutes)
        assertEquals(0, vm.timerHours)
    }

    @Test
    fun `stepping minutes down from zero with no hours stays at zero`() {
        val vm = vm()

        vm.stepTimerMinutes(-1)

        assertEquals(0, vm.timerMinutes)
        assertEquals(0, vm.timerHours)
    }

    // ── editing the duration carries the delta to the remaining time ─────────────

    @Test
    fun `editing the configured duration shifts the remaining preview by the same delta`() {
        val vm = vm() // TIMER_MODE_DURATION by default
        vm.setTimerMinutes(2) // total 120s -> remaining 120

        assertEquals(120, vm.timerRemaining)

        vm.setTimerMinutes(3) // +60s

        assertEquals(180, vm.timerRemaining, "lengthening the duration extends the countdown, not resets it")
    }

    // ── stepTargetHour: wraps around the clock ──────────────────────────────────

    @Test
    fun `the target hour wraps forward past midnight`() {
        val vm = vm()
        vm.setTargetHour(23)

        vm.stepTargetHour(1)

        assertEquals(0, vm.targetHour, "11 PM steps to midnight, not clamps at 23")
    }

    @Test
    fun `the target hour wraps backward past midnight`() {
        val vm = vm()
        vm.setTargetHour(0)

        vm.stepTargetHour(-1)

        assertEquals(23, vm.targetHour)
    }

    @Test
    fun `the target hour is clamped to 0-23 when set directly`() {
        val vm = vm()
        vm.setTargetHour(30); assertEquals(23, vm.targetHour)
        vm.setTargetHour(-1); assertEquals(0, vm.targetHour)
    }

    // ── setTimerMode: switching to duration recomputes the remaining ────────────

    @Test
    fun `switching to duration mode seeds the remaining from the configured fields`() {
        val vm = vm()
        vm.setTimerMinutes(5) // 300s configured
        vm.setTimerMode(Constants.TIMER_MODE_COUNT_UP) // leave duration mode

        vm.setTimerMode(Constants.TIMER_MODE_DURATION) // back to duration

        assertEquals(300, vm.timerRemaining, "returning to duration mode shows the full configured time")
    }
}
