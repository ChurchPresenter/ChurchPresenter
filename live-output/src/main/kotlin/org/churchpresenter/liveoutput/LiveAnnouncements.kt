package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.announcements.AnnouncementsViewModel
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val TICK_INTERVAL_MS = 1000L
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_DAY = 86400

/**
 * The announcement text, and the timer or clock that can drive it. Part of [PresenterManager].
 *
 * The timer ticks here -- not in the Announcements tab's view model -- so switching to another tab
 * (to present a Bible verse, say) does not kill the coroutine and freeze the countdown. The Stage
 * Monitor and the live output both observe [timerRemainingSeconds] and [timerRunning] directly.
 */
interface LiveAnnouncements {
    val announcementText: State<String>

    /** What the outputs draw, faded in by [announcementTransitionAlpha]. */
    val displayedAnnouncementText: State<String>
    val announcementTransitionAlpha: State<Float>
    val timerRemainingSeconds: State<Int>
    val timerRunning: State<Boolean>

    /**
     * Unlike [timerRunning] (only ever true for Duration and Count-Up, the two modes with a pause),
     * true whenever ANY of the four ticker modes is pushing its value into [announcementText] every
     * second -- Specific Time and Clock Display included. Switching to plain announcement text must
     * check this, not [timerRunning], or the still-running ticker overwrites the text a second later.
     */
    val announcementTickerActive: State<Boolean>

    /**
     * True only once the operator has pushed the ticker live via Go Live or Send to Stage Monitor --
     * NOT merely because it is running. The tab's play/pause button gates on this, so pressing it
     * while only previewing never touches the screen. Deliberately not cleared by
     * [pauseAnnouncementTimer]: once live, pausing freezes the value on screen and play resumes
     * pushing it, without a separate "stop timer on presenter" control.
     */
    val announcementTickerLive: State<Boolean>

    /**
     * True once a Duration countdown has run out -- the signal the Announcements tab shows the
     * expired message by, since the tab may have been recreated since the countdown finished.
     */
    val announcementTimerExpired: State<Boolean>

    fun setAnnouncementText(text: String)
    fun setDisplayedAnnouncementText(text: String)
    fun setAnnouncementTransitionAlpha(alpha: Float)
    fun setAnnouncementTickerLive(live: Boolean)

    /** Duration/Countdown — ticks down from [remainingSeconds] and shows [expiredText] on reaching zero. */
    fun startAnnouncementCountdown(remainingSeconds: Int, expiredText: String)

    /** Count-up — an open-ended stopwatch starting from [initialElapsedSeconds]. */
    fun startAnnouncementCountUp(initialElapsedSeconds: Int)

    /** Specific Time — always-on countdown to the next occurrence of [targetHour]:[targetMinute]:[targetSecond]. */
    fun startAnnouncementSpecificTime(targetHour: Int, targetMinute: Int, targetSecond: Int)

    /** Clock Display — always-on live wall clock formatted with [formatPattern]. */
    fun startAnnouncementClockDisplay(formatPattern: String)

    /**
     * Starts (or resumes, if already ticking) the given timer/clock config and marks it live,
     * pushing a value immediately instead of waiting for the ticker's next natural tick. Must work
     * regardless of whether the Announcements tab has ever been composed this session — e.g.
     * presenting a Timer schedule item straight from the Schedule tab, before Announcements has
     * ever been opened.
     */
    fun goLiveAnnouncementTimer(
        item: ScheduleItem.AnnouncementItem,
        timerExpiredText: String = item.timerExpiredText,
    )

    /**
     * Pauses/stops whichever announcement ticker is active, optionally pinning the mirrored remaining
     * value (e.g. on Reset).
     */
    fun pauseAnnouncementTimer(remainingSeconds: Int? = null)
}

internal class LiveAnnouncementsState(private val context: PresenterContext) : LiveAnnouncements {

    private val _announcementText = mutableStateOf("")
    override val announcementText: State<String> = _announcementText

    private val _displayedAnnouncementText = mutableStateOf("")
    override val displayedAnnouncementText: State<String> = _displayedAnnouncementText

    private val _announcementTransitionAlpha = mutableStateOf(1f)
    override val announcementTransitionAlpha: State<Float> = _announcementTransitionAlpha

    private val _timerRemainingSeconds = mutableStateOf(0)
    override val timerRemainingSeconds: State<Int> = _timerRemainingSeconds

    private val _timerRunning = mutableStateOf(false)
    override val timerRunning: State<Boolean> = _timerRunning

    private val _announcementTickerActive = mutableStateOf(false)
    override val announcementTickerActive: State<Boolean> = _announcementTickerActive

    private val _announcementTickerLive = mutableStateOf(false)
    override val announcementTickerLive: State<Boolean> = _announcementTickerLive

    private val _announcementTimerExpired = mutableStateOf(false)
    override val announcementTimerExpired: State<Boolean> = _announcementTimerExpired

    private var announcementTickerJob: Job? = null

    // Which ticker is current. `cancel()` only lands at the ticker's next `delay`, so a tick that had
    // already read the clock used to write its value after a pause or a Reset and undo it (#711).
    // Every start and every pause takes a new generation under [announcementTickerLock]; a ticker
    // writes only while its own is still current, under the same lock, so once a start or pause has
    // returned no older tick can land.
    private val announcementTickerLock = Any()
    private var announcementTickerGeneration = 0L

    override fun setAnnouncementText(text: String) {
        _announcementText.value = text
        context.notify(Presenting.ANNOUNCEMENTS)
    }

    override fun setDisplayedAnnouncementText(text: String) {
        _displayedAnnouncementText.value = text
    }

    override fun setAnnouncementTransitionAlpha(alpha: Float) {
        _announcementTransitionAlpha.value = alpha
    }

    override fun setAnnouncementTickerLive(live: Boolean) {
        _announcementTickerLive.value = live
    }

    /** Stops the current ticker and returns the generation the next one runs under. */
    private fun nextAnnouncementTicker(): Long = synchronized(announcementTickerLock) {
        announcementTickerJob?.cancel()
        ++announcementTickerGeneration
    }

    /** Runs [write] if [generation] is still the current ticker; false when a newer one replaced it. */
    private inline fun whileCurrentTicker(generation: Long, write: () -> Unit): Boolean =
        synchronized(announcementTickerLock) {
            if (generation != announcementTickerGeneration) return false
            write()
            true
        }

    override fun startAnnouncementCountdown(remainingSeconds: Int, expiredText: String) {
        val generation = nextAnnouncementTicker()
        if (remainingSeconds <= 0) return
        val endEpochSecond = Instant.now().epochSecond + remainingSeconds
        _timerRemainingSeconds.value = remainingSeconds
        _timerRunning.value = true
        _announcementTickerActive.value = true
        _announcementTimerExpired.value = false
        announcementTickerJob = context.scope.launch {
            while (true) {
                val remaining = (endEpochSecond - Instant.now().epochSecond).toInt()
                if (remaining <= 0) break
                val current = whileCurrentTicker(generation) {
                    _timerRemainingSeconds.value = remaining
                    pushAnnouncementTextIfLive(AnnouncementsViewModel.formatTimer(remaining))
                }
                if (!current) return@launch
                delay(TICK_INTERVAL_MS)
            }
            whileCurrentTicker(generation) {
                _timerRemainingSeconds.value = 0
                _timerRunning.value = false
                _announcementTickerActive.value = false
                _announcementTimerExpired.value = true
                pushAnnouncementTextIfLive(expiredText)
                // Only where the timer is still what is on screen. A countdown row set to advance at
                // its end hands on at the very second it reaches zero, and this used to drag the
                // output straight back to the expired message -- the next item appeared for an
                // instant and then vanished. Same condition the push above is guarded by.
                if (announcementIsLive()) context.setPresentingMode(Presenting.ANNOUNCEMENTS)
            }
        }
    }

    override fun startAnnouncementCountUp(initialElapsedSeconds: Int) {
        val generation = nextAnnouncementTicker()
        val startEpochSecond = Instant.now().epochSecond - initialElapsedSeconds
        _timerRemainingSeconds.value = initialElapsedSeconds
        _timerRunning.value = true
        _announcementTickerActive.value = true
        _announcementTimerExpired.value = false
        announcementTickerJob = context.scope.launch {
            while (true) {
                val elapsed = (Instant.now().epochSecond - startEpochSecond).toInt().coerceAtLeast(0)
                val current = whileCurrentTicker(generation) {
                    _timerRemainingSeconds.value = elapsed
                    pushAnnouncementTextIfLive(AnnouncementsViewModel.formatTimer(elapsed))
                }
                if (!current) return@launch
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    override fun startAnnouncementSpecificTime(targetHour: Int, targetMinute: Int, targetSecond: Int) {
        val generation = nextAnnouncementTicker()
        _timerRunning.value = false
        _announcementTickerActive.value = true
        announcementTickerJob = context.scope.launch {
            while (true) {
                val nowSec = LocalTime.now().toSecondOfDay()
                val targetSec = targetHour * SECONDS_PER_HOUR + targetMinute * SECONDS_PER_MINUTE + targetSecond
                val diff = targetSec - nowSec
                val remaining = if (diff > 0) diff else diff + SECONDS_PER_DAY
                val current = whileCurrentTicker(generation) {
                    _timerRemainingSeconds.value = remaining
                    pushAnnouncementTextIfLive(AnnouncementsViewModel.formatTimer(remaining))
                }
                if (!current) return@launch
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    override fun startAnnouncementClockDisplay(formatPattern: String) {
        val generation = nextAnnouncementTicker()
        _timerRunning.value = false
        _announcementTickerActive.value = true
        announcementTickerJob = context.scope.launch {
            while (true) {
                val text = LocalTime.now().format(DateTimeFormatter.ofPattern(formatPattern))
                if (!whileCurrentTicker(generation) { pushAnnouncementTextIfLive(text) }) return@launch
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    override fun goLiveAnnouncementTimer(item: ScheduleItem.AnnouncementItem, timerExpiredText: String) {
        val timerMode = item.timerMode
        val liveClockFormat = item.liveClockFormat
        val skipRestart = _announcementTickerActive.value &&
            (timerMode == Constants.TIMER_MODE_DURATION || timerMode == Constants.TIMER_MODE_COUNT_UP)
        if (!skipRestart) {
            when (timerMode) {
                Constants.TIMER_MODE_COUNT_UP -> startAnnouncementCountUp(0)
                Constants.TIMER_MODE_CLOCK ->
                    startAnnouncementSpecificTime(item.targetHour, item.targetMinute, item.targetSecond)
                Constants.TIMER_MODE_CLOCK_DISPLAY -> startAnnouncementClockDisplay(liveClockFormat)
                else -> startAnnouncementCountdown(
                    item.timerHours * SECONDS_PER_HOUR + item.timerMinutes * SECONDS_PER_MINUTE + item.timerSeconds,
                    timerExpiredText
                )
            }
        }
        _announcementTickerLive.value = true
        val liveText = if (timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY) {
            LocalTime.now().format(DateTimeFormatter.ofPattern(liveClockFormat))
        } else {
            AnnouncementsViewModel.formatTimer(_timerRemainingSeconds.value)
        }
        setAnnouncementText(liveText)
    }

    override fun pauseAnnouncementTimer(remainingSeconds: Int?) {
        synchronized(announcementTickerLock) {
            nextAnnouncementTicker()
            _timerRunning.value = false
            _announcementTickerActive.value = false
            _announcementTimerExpired.value = false
            if (remainingSeconds != null) _timerRemainingSeconds.value = remainingSeconds
        }
    }

    internal fun pushAnnouncementTextIfLive(text: String) {
        if (announcementIsLive()) setAnnouncementText(text)
    }

    /** Whether an announcement is what any output is showing -- globally, or on a locked screen. */
    private fun announcementIsLive(): Boolean {
        val anyScreenOnAnnouncements = Presenting.ANNOUNCEMENTS in context.overlays.value ||
            context.screenLocks.value.values.any { it == Presenting.ANNOUNCEMENTS }
        return anyScreenOnAnnouncements && _announcementTickerLive.value
    }
}
