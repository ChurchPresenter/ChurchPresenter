package org.churchpresenter.announcements

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.settings.utils.Constants
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The countdown's configured length, set outright or stepped with the arrows. */
interface AnnouncementsDurationActions {
    var timerHours: Int
    var timerMinutes: Int
    var timerSeconds: Int
    fun stepTimerHours(delta: Int)
    fun stepTimerMinutes(delta: Int)
    fun stepTimerSeconds(delta: Int)
}

/** The Specific Time a countdown runs to, set outright or stepped with the arrows. */
interface AnnouncementsTargetActions {
    var targetHour: Int
    var targetMinute: Int
    var targetSecond: Int
    fun stepTargetHour(delta: Int)
    fun stepTargetMinute(delta: Int)
    fun stepTargetSecond(delta: Int)
}

/** Which kind of timer it is, and the clock's format. */
interface AnnouncementsClockActions {
    var timerMode: String
    var liveClockFormat: String
}

/** Starting, pausing and resetting the timer, which ticks on the output. */
interface AnnouncementsTimerActions {
    /** Starts, pauses, or resumes the live timer/clock for the current timer mode. */
    fun startPauseTimer(output: AnnouncementsOutput?)
    fun pauseTimer(output: AnnouncementsOutput?)
    fun resetTimer(output: AnnouncementsOutput?)
}

internal class AnnouncementsDuration(private val state: AnnouncementsState) : AnnouncementsDurationActions {

    /**
     * Applies a change to the configured H/M/S duration fields and carries the *delta* over to
     * the live remaining time, instead of recomputing remaining from the raw fields. That way,
     * editing hours/minutes/seconds while paused extends or shortens the countdown instead of
     * snapping it back to the full configured duration. (Duration actually ticks on the output once
     * started — see startPauseTimer — so this only affects the local, not-yet-live preview; editing
     * the fields while a countdown is already live doesn't nudge it.)
     */
    private inline fun applyDurationDelta(mutate: () -> Unit) {
        val oldTotal = state.totalSeconds()
        mutate()
        val delta = state.totalSeconds() - oldTotal
        if (delta == 0) return
        if (state.timerMode != Constants.TIMER_MODE_DURATION) return
        state.timerRemaining = (state.timerRemaining + delta).coerceAtLeast(0)
    }

    override var timerHours: Int
        get() = state.timerHours
        set(value) = applyDurationDelta { state.timerHours = value.coerceAtLeast(0) }

    override var timerMinutes: Int
        get() = state.timerMinutes
        set(value) = applyDurationDelta { state.timerMinutes = value.coerceIn(0, MAX_MINUTE) }

    override var timerSeconds: Int
        get() = state.timerSeconds
        set(value) = applyDurationDelta { state.timerSeconds = value.coerceIn(0, MAX_SECOND) }

    override fun stepTimerHours(delta: Int) = applyDurationDelta {
        state.timerHours = (state.timerHours + delta).coerceAtLeast(0)
    }

    override fun stepTimerMinutes(delta: Int) = applyDurationDelta {
        val cur = state.timerMinutes
        if (delta > 0) {
            if (cur >= MAX_MINUTE) {
                state.timerMinutes = 0
                state.timerHours += 1
            } else {
                state.timerMinutes = cur + 1
            }
        } else if (cur > 0) {
            state.timerMinutes = cur - 1
        } else if (state.timerHours > 0) {
            state.timerMinutes = MAX_MINUTE
            state.timerHours -= 1
        }
    }

    override fun stepTimerSeconds(delta: Int) = applyDurationDelta {
        val cur = state.timerSeconds
        if (delta > 0) {
            if (cur >= LAST_SECOND_STEP) {
                state.timerSeconds = 0
                if (state.timerMinutes >= MAX_MINUTE) {
                    state.timerMinutes = 0
                    state.timerHours += 1
                } else {
                    state.timerMinutes += 1
                }
            } else {
                state.timerSeconds = (cur / SECOND_STEP + 1) * SECOND_STEP
            }
        } else if (cur > 0) {
            state.timerSeconds = ((cur - 1) / SECOND_STEP) * SECOND_STEP
        } else if (state.timerHours > 0 || state.timerMinutes > 0) {
            state.timerSeconds = LAST_SECOND_STEP
            if (state.timerMinutes <= 0) {
                state.timerMinutes = MAX_MINUTE
                state.timerHours -= 1
            } else {
                state.timerMinutes -= 1
            }
        }
    }
}

internal class AnnouncementsTarget(private val state: AnnouncementsState) : AnnouncementsTargetActions {

    /** Applies a change to the target clock time and keeps the local preview in sync. */
    private inline fun applyTargetChange(mutate: () -> Unit) {
        mutate()
        if (state.timerMode != Constants.TIMER_MODE_CLOCK) return
        state.timerRemaining = state.secondsUntilTarget()
    }

    override var targetHour: Int
        get() = state.targetHour
        set(value) = applyTargetChange { state.targetHour = value.coerceIn(0, MAX_HOUR) }

    override var targetMinute: Int
        get() = state.targetMinute
        set(value) = applyTargetChange { state.targetMinute = value.coerceIn(0, MAX_MINUTE) }

    override var targetSecond: Int
        get() = state.targetSecond
        set(value) = applyTargetChange { state.targetSecond = value.coerceIn(0, MAX_SECOND) }

    override fun stepTargetHour(delta: Int) {
        // Wrap around midnight/noon instead of clamping, so continuing to step past 11 PM/AM flips correctly.
        targetHour = ((state.targetHour + delta) % HOURS_PER_DAY + HOURS_PER_DAY) % HOURS_PER_DAY
    }

    override fun stepTargetMinute(delta: Int) {
        val cur = state.targetMinute
        // The carry goes through stepTargetHour, not the targetHour setter: the latter coerces into
        // 0..23, which left the minute wrapping while the hour stuck (23:59 stepped up to 23:00,
        // 00:00 stepped down to 00:59). A countdown aimed at midnight was 23 hours out.
        if (delta > 0) {
            if (cur >= MAX_MINUTE) {
                state.targetMinute = 0
                stepTargetHour(1)
            } else {
                targetMinute = cur + 1
            }
        } else if (cur <= 0) {
            state.targetMinute = MAX_MINUTE
            stepTargetHour(-1)
        } else {
            targetMinute = cur - 1
        }
    }

    override fun stepTargetSecond(delta: Int) {
        val cur = state.targetSecond
        if (delta > 0) {
            if (cur >= LAST_SECOND_STEP) {
                state.targetSecond = 0
                stepTargetMinute(1)
            } else {
                targetSecond = (cur / SECOND_STEP + 1) * SECOND_STEP
            }
        } else if (cur <= 0) {
            state.targetSecond = LAST_SECOND_STEP
            stepTargetMinute(-1)
        } else {
            targetSecond = ((cur - 1) / SECOND_STEP) * SECOND_STEP
        }
    }
}

internal class AnnouncementsClock(
    private val state: AnnouncementsState,
    private val scope: CoroutineScope,
) : AnnouncementsClockActions {

    /** Ticks every second in clock mode while the timer is not running, keeping the display current. */
    private var clockPreviewJob: Job? = null

    /** Ticks every second while TIMER_MODE_CLOCK_DISPLAY is active, keeping liveClockText current. */
    private var liveClockJob: Job? = null

    override var liveClockFormat: String
        get() = state.liveClockFormat
        set(value) {
            state.liveClockFormat = value
            if (state.timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY) {
                state.liveClockText = LocalTime.now().format(DateTimeFormatter.ofPattern(value))
            }
        }

    override var timerMode: String
        get() = state.timerMode
        set(value) {
            if (state.timerMode == value) return
            state.timerMode = value
            stop()
            when (value) {
                // Nothing else to precompute — countUpElapsed already holds the right value.
                Constants.TIMER_MODE_COUNT_UP -> Unit
                Constants.TIMER_MODE_CLOCK_DISPLAY -> startLiveClockPreview()
                Constants.TIMER_MODE_CLOCK -> {
                    // Default the target to "now" instead of leaving a stale time from a previous session.
                    val now = LocalTime.now()
                    state.targetHour = now.hour
                    state.targetMinute = now.minute
                    state.targetSecond = now.second
                    state.timerRemaining = state.secondsUntilTarget()
                    startClockPreview()
                }
                else -> state.timerRemaining = state.totalSeconds()
            }
        }

    /** Starts whichever preview the mode the settings were loaded in needs. */
    fun startFor(mode: String) {
        if (mode == Constants.TIMER_MODE_CLOCK) startClockPreview()
        if (mode == Constants.TIMER_MODE_CLOCK_DISPLAY) startLiveClockPreview()
    }

    fun stop() {
        clockPreviewJob?.cancel()
        clockPreviewJob = null
        liveClockJob?.cancel()
        liveClockJob = null
    }

    private fun startClockPreview() {
        clockPreviewJob?.cancel()
        clockPreviewJob = scope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                if (state.timerMode == Constants.TIMER_MODE_CLOCK) {
                    state.timerRemaining = state.secondsUntilTarget()
                }
            }
        }
    }

    private fun startLiveClockPreview() {
        liveClockJob?.cancel()
        liveClockJob = scope.launch {
            while (true) {
                state.liveClockText = LocalTime.now().format(DateTimeFormatter.ofPattern(state.liveClockFormat))
                delay(TICK_INTERVAL_MS)
            }
        }
    }
}

// Duration/Count-up/Specific-Time tick on the output (not here), so the countdown survives
// switching away from the Announcements tab — see AnnouncementsOutput's startAnnouncementCountdown/
// startAnnouncementCountUp/startAnnouncementSpecificTime. This part just tells it what to do; the UI
// reads its state back.
internal class AnnouncementsTimerControl(private val state: AnnouncementsState) : AnnouncementsTimerActions {

    override fun startPauseTimer(output: AnnouncementsOutput?) {
        if (output == null) return
        when (state.timerMode) {
            // Specific Time and Clock Display recompute from the wall clock, so pausing/resuming
            // just stops/restarts their ticker — announcementTickerActive (not timerRunning, which
            // never applies to these two modes) is the correct running-state check for both.
            Constants.TIMER_MODE_CLOCK_DISPLAY ->
                if (output.announcementTickerActive.value) output.pauseAnnouncementTimer()
                else output.startAnnouncementClockDisplay(state.liveClockFormat)
            Constants.TIMER_MODE_CLOCK ->
                if (output.announcementTickerActive.value) output.pauseAnnouncementTimer()
                else output.startAnnouncementSpecificTime(state.targetHour, state.targetMinute, state.targetSecond)
            Constants.TIMER_MODE_COUNT_UP ->
                if (output.timerRunning.value) output.pauseAnnouncementTimer(output.timerRemainingSeconds.value)
                else output.startAnnouncementCountUp(output.timerRemainingSeconds.value)
            else -> // TIMER_MODE_DURATION
                if (output.timerRunning.value) {
                    output.pauseAnnouncementTimer(output.timerRemainingSeconds.value)
                } else {
                    val remaining = output.timerRemainingSeconds.value.takeIf { it > 0 } ?: state.totalSeconds()
                    if (remaining > 0) output.startAnnouncementCountdown(remaining, state.timerExpiredText)
                }
        }
    }

    // Unconditional (not gated on timerRunning) — Specific Time and Clock Display keep their ticker
    // coroutine active while timerRunning stays false for them, and it must be stopped before
    // plain announcement text is pushed live or it silently overwrites the text within a second.
    // Also releases announcementTickerLive — text is taking over the live slot from the timer, so
    // a later plain Resume click on the timer's play/pause button must not silently go live again.
    override fun pauseTimer(output: AnnouncementsOutput?) {
        output?.pauseAnnouncementTimer(output.timerRemainingSeconds.value)
        output?.setAnnouncementTickerLive(false)
    }

    override fun resetTimer(output: AnnouncementsOutput?) {
        when (state.timerMode) {
            Constants.TIMER_MODE_COUNT_UP -> output?.pauseAnnouncementTimer(0)
            // Specific Time always auto-tracks the wall clock — nothing to reset back to.
            Constants.TIMER_MODE_CLOCK -> Unit
            else -> output?.pauseAnnouncementTimer(state.totalSeconds())
        }
    }
}
