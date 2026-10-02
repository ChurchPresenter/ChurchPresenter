package org.churchpresenter.announcements

import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The countdown timer behind "service starts in...". Its H/M/S spinners carry, clamp and snap to
 * a 5-second grid, and editing a field while paused nudges the live remaining time by the *delta*
 * rather than resetting it -- all of which is easy to break and invisible until a service.
 *
 * No PresenterManager is needed: the start/pause/reset entry points take a nullable one, and the
 * field arithmetic tested here runs entirely locally.
 */
class AnnouncementsViewModelTest {

    private val created = mutableListOf<AnnouncementsViewModel>()

    @AfterTest
    fun disposeAll() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    private fun vm(): AnnouncementsViewModel = AnnouncementsViewModel().also { created.add(it) }

    /** Sets the duration fields directly, then reports the configured total in seconds. */
    private val AnnouncementsViewModel.totalSeconds: Int
        get() = timerHours * 3600 + timerMinutes * 60 + timerSeconds

    // ── Duration field clamping ─────────────────────────────────────────────────

    @Test
    fun `minutes and seconds are clamped to 0-59 and hours cannot go negative`() {
        val vm = vm()
        vm.timerMinutes = 99
        assertEquals(59, vm.timerMinutes)
        vm.timerMinutes = -5
        assertEquals(0, vm.timerMinutes)

        vm.timerSeconds = 120
        assertEquals(59, vm.timerSeconds)
        vm.timerSeconds = -1
        assertEquals(0, vm.timerSeconds)

        vm.timerHours = -3
        assertEquals(0, vm.timerHours, "a negative countdown makes no sense")
        vm.timerHours = 12
        assertEquals(12, vm.timerHours, "hours are deliberately uncapped")
    }

    @Test
    fun `loop count cannot go negative`() {
        val vm = vm()
        vm.loopCount = -4
        assertEquals(0, vm.loopCount)
        vm.loopCount = 3
        assertEquals(3, vm.loopCount)
    }

    // ── Spinner stepping ────────────────────────────────────────────────────────

    @Test
    fun `stepping minutes past 59 carries into hours`() {
        val vm = vm()
        vm.timerHours = 1
        vm.timerMinutes = 59
        vm.stepTimerMinutes(1)
        assertEquals(0, vm.timerMinutes)
        assertEquals(2, vm.timerHours, "59 -> 0 must carry an hour")
    }

    @Test
    fun `stepping minutes below zero borrows an hour, or stops at zero`() {
        val vm = vm()
        vm.timerHours = 1
        vm.timerMinutes = 0
        vm.stepTimerMinutes(-1)
        assertEquals(59, vm.timerMinutes)
        assertEquals(0, vm.timerHours)

        // Now at 0:00 -- there is nothing left to borrow from.
        vm.timerMinutes = 0
        vm.stepTimerMinutes(-1)
        assertEquals(0, vm.timerMinutes, "must not underflow past zero")
        assertEquals(0, vm.timerHours)
    }

    @Test
    fun `stepping seconds snaps to the nearest 5-second mark`() {
        val vm = vm()
        vm.timerSeconds = 0
        vm.stepTimerSeconds(1)
        assertEquals(5, vm.timerSeconds)

        vm.timerSeconds = 3 // off-grid value typed by hand
        vm.stepTimerSeconds(1)
        assertEquals(5, vm.timerSeconds, "stepping up from an off-grid value lands on the grid")

        vm.timerSeconds = 7
        vm.stepTimerSeconds(-1)
        assertEquals(5, vm.timerSeconds)

        vm.timerSeconds = 5
        vm.stepTimerSeconds(-1)
        assertEquals(0, vm.timerSeconds)
    }

    @Test
    fun `stepping seconds past 55 carries into minutes`() {
        val vm = vm()
        vm.timerMinutes = 2
        vm.timerSeconds = 55
        vm.stepTimerSeconds(1)
        assertEquals(0, vm.timerSeconds)
        assertEquals(3, vm.timerMinutes)
    }

    @Test
    fun `stepping seconds below zero borrows a minute`() {
        val vm = vm()
        vm.timerMinutes = 2
        vm.timerSeconds = 0
        vm.stepTimerSeconds(-1)
        assertEquals(55, vm.timerSeconds)
        assertEquals(1, vm.timerMinutes)
    }

    @Test
    fun `stepping seconds down at zero total stays at zero`() {
        val vm = vm()
        vm.timerHours = 0
        vm.timerMinutes = 0
        vm.timerSeconds = 0
        vm.stepTimerSeconds(-1)
        assertEquals(0, vm.totalSeconds, "there is nothing to borrow from at 0:00:00")
    }

    // ── Remaining-time delta behaviour ──────────────────────────────────────────

    @Test
    fun `editing the duration shifts the remaining time by the delta, not to the full duration`() {
        val vm = vm()
        vm.timerMode = Constants.TIMER_MODE_DURATION
        vm.timerMinutes = 5
        assertEquals(300, vm.timerRemaining, "setting 5 minutes from zero adds 300s")

        vm.timerMinutes = 6 // +60s
        assertEquals(360, vm.timerRemaining)

        vm.timerMinutes = 4 // -120s from 6
        assertEquals(240, vm.timerRemaining, "shrinking the duration must subtract, not reset")
    }

    @Test
    fun `remaining time never goes negative when the duration is cut`() {
        val vm = vm()
        vm.timerMode = Constants.TIMER_MODE_DURATION
        vm.timerMinutes = 1
        vm.timerHours = 5
        vm.timerHours = 0   // a large negative delta
        vm.timerMinutes = 0
        assertTrue(vm.timerRemaining >= 0, "remaining was ${vm.timerRemaining}")
    }

    @Test
    fun `a no-op edit leaves the remaining time alone`() {
        val vm = vm()
        vm.timerMode = Constants.TIMER_MODE_DURATION
        vm.timerMinutes = 5
        val before = vm.timerRemaining
        vm.timerMinutes = 5 // same value -> zero delta
        assertEquals(before, vm.timerRemaining)
    }

    // ── Target clock time ───────────────────────────────────────────────────────

    @Test
    fun `target time components are clamped to real clock ranges`() {
        val vm = vm()
        vm.targetHour = 30
        assertEquals(23, vm.targetHour)
        vm.targetHour = -1
        assertEquals(0, vm.targetHour)

        vm.targetMinute = 99
        assertEquals(59, vm.targetMinute)
        vm.targetSecond = 99
        assertEquals(59, vm.targetSecond)
    }

    @Test
    fun `stepping the target hour wraps around midnight in both directions`() {
        val vm = vm()
        vm.targetHour = 23
        vm.stepTargetHour(1)
        assertEquals(0, vm.targetHour, "23:00 + 1h should wrap to 00:00")

        vm.targetHour = 0
        vm.stepTargetHour(-1)
        assertEquals(23, vm.targetHour, "00:00 - 1h should wrap to 23:00")
    }

    /**
     * The minute spinner's hour carry has to wrap across midnight, like [stepTargetHour] does.
     *
     * This used to document the opposite as a known inconsistency: the carry went through
     * `setTargetHour`, which coerces into 0..23, so the minute wrapped while the hour stuck —
     * 23:59 stepped up to **23:00** instead of 00:00, and 00:00 stepped down to **00:59** instead of
     * 23:59. A countdown aimed at midnight is exactly the one a church sets, and it was 23 hours out.
     *
     * Both directions are asserted because they fail through different halves of the carry.
     */
    @Test
    fun `stepping the minute across midnight wraps the hour rather than clamping it`() {
        val vm = vm()
        vm.targetHour = 23
        vm.targetMinute = 59
        vm.stepTargetMinute(1)
        assertEquals(0, vm.targetMinute)
        assertEquals(0, vm.targetHour, "23:59 + 1min is 00:00, not 23:00")

        vm.targetHour = 0
        vm.targetMinute = 0
        vm.stepTargetMinute(-1)
        assertEquals(59, vm.targetMinute)
        assertEquals(23, vm.targetHour, "00:00 - 1min is 23:59, not 00:59")
    }

    /**
     * The seconds spinner reached the same defect one level deeper, and is a separate user action.
     *
     * `stepTargetSecond` already carried through `stepTargetMinute` — the correct pattern, and the
     * reason the fix was to make the minute carry match it rather than to invent a new convention.
     * But the hour it eventually reached was still clamped, so stepping seconds down from midnight
     * landed on hour 0 instead of 23.
     */
    @Test
    fun `stepping the second down from midnight carries all the way through the hour`() {
        val vm = vm()
        vm.targetHour = 0
        vm.targetMinute = 0
        vm.targetSecond = 0

        vm.stepTargetSecond(-1)

        assertEquals(23, vm.targetHour, "00:00:00 stepped down is 23:59:55")
        assertEquals(59, vm.targetMinute)
    }

    @Test
    fun `stepping the target minute carries normally away from the clock boundaries`() {
        val vm = vm()
        vm.targetHour = 10
        vm.targetMinute = 59
        vm.stepTargetMinute(1)
        assertEquals(0, vm.targetMinute)
        assertEquals(11, vm.targetHour, "away from midnight the carry works correctly")

        vm.targetMinute = 0
        vm.stepTargetMinute(-1)
        assertEquals(59, vm.targetMinute)
        assertEquals(10, vm.targetHour)
    }

    @Test
    fun `stepping the target second snaps to the 5-second grid and carries`() {
        val vm = vm()
        vm.targetHour = 10
        vm.targetMinute = 30
        vm.targetSecond = 0
        vm.stepTargetSecond(1)
        assertEquals(5, vm.targetSecond)

        vm.targetSecond = 55
        vm.stepTargetSecond(1)
        assertEquals(0, vm.targetSecond)
        assertEquals(31, vm.targetMinute, "seconds rolling over should advance the minute")
    }

    // ── Mode switching ──────────────────────────────────────────────────────────

    @Test
    fun `switching to clock mode computes a positive countdown to the target`() {
        val vm = vm()
        vm.targetHour = 12
        vm.targetMinute = 0
        vm.targetSecond = 0
        vm.timerMode = Constants.TIMER_MODE_CLOCK

        assertEquals(Constants.TIMER_MODE_CLOCK, vm.timerMode)
        assertTrue(
            vm.timerRemaining in 1..86_400,
            "a target time must always be somewhere in the next 24h, got ${vm.timerRemaining}",
        )
    }

    @Test
    fun `switching modes back and forth is stable`() {
        val vm = vm()
        vm.timerMode = Constants.TIMER_MODE_COUNT_UP
        assertEquals(Constants.TIMER_MODE_COUNT_UP, vm.timerMode)
        vm.timerMode = Constants.TIMER_MODE_DURATION
        assertEquals(Constants.TIMER_MODE_DURATION, vm.timerMode)
        vm.timerMode = Constants.TIMER_MODE_DURATION // repeated set is a no-op
        assertEquals(Constants.TIMER_MODE_DURATION, vm.timerMode)
    }

    // ── Settings round-trip ─────────────────────────────────────────────────────

    @Test
    fun `buildSettings round-trips through syncFromSettings`() {
        val vm = vm()
        vm.textColor = "#123456"
        vm.backgroundColor = "#654321"
        vm.fontSize = 72
        vm.bold = true
        vm.italic = true
        vm.horizontalAlignment = "center"
        vm.animationDuration = 1500
        vm.loopCount = 4
        vm.timerExpiredText = "We are starting!"

        val restored = vm()
        restored.syncFromSettings(vm.buildSettings())

        assertEquals("#123456", restored.textColor)
        assertEquals("#654321", restored.backgroundColor)
        assertEquals(72, restored.fontSize)
        assertTrue(restored.bold)
        assertTrue(restored.italic)
        assertEquals("center", restored.horizontalAlignment)
        assertEquals(1500, restored.animationDuration)
        assertEquals(4, restored.loopCount)
        assertEquals("We are starting!", restored.timerExpiredText)
    }
}
