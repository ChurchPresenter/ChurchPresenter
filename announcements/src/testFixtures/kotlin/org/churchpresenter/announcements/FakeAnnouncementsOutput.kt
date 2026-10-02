package org.churchpresenter.announcements

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.sharedui.models.Presenting

/**
 * An [AnnouncementsOutput] that keeps what the tab puts on it, so a test asserts on the output the
 * way the app's own does.
 *
 * Every call makes the same state changes `PresenterManager` makes, apart from the ticking: a timer
 * started here runs, pauses and reports what it was given, but no second ever passes. The app's
 * `AnnouncementsTimerControlTest` drives the real ticker.
 */
class FakeAnnouncementsOutput : AnnouncementsOutput {
    override val presentingMode: MutableState<Presenting> = mutableStateOf(Presenting.NONE)
    override val screenLocks: MutableState<Map<Int, Presenting>> = mutableStateOf(emptyMap())
    override val timerRemainingSeconds: MutableState<Int> = mutableStateOf(0)
    override val timerRunning: MutableState<Boolean> = mutableStateOf(false)
    override val announcementTickerActive: MutableState<Boolean> = mutableStateOf(false)
    override val announcementTimerExpired: MutableState<Boolean> = mutableStateOf(false)

    val announcementText = mutableStateOf("")
    val announcementTickerLive = mutableStateOf(false)
    val clearDisplayRequested = mutableStateOf(false)

    /** Each timer started, in order: `countdown 300`, `countUp 5`, `specificTime 9:30:0`, `clock HH:mm`. */
    val started = mutableListOf<String>()

    override fun setPresentingMode(mode: Presenting) {
        presentingMode.value = mode
        if (mode != Presenting.NONE) clearDisplayRequested.value = false
    }

    override fun requestClearDisplay() {
        if (presentingMode.value != Presenting.NONE) clearDisplayRequested.value = true
    }

    override fun setScreenLock(screenIndex: Int, mode: Presenting?) {
        screenLocks.value =
            if (mode == null) screenLocks.value - screenIndex else screenLocks.value + (screenIndex to mode)
    }

    override fun setAnnouncementText(text: String) {
        announcementText.value = text
    }

    override fun setAnnouncementTickerLive(live: Boolean) {
        announcementTickerLive.value = live
    }

    override fun startAnnouncementCountdown(remainingSeconds: Int, expiredText: String) {
        started += "countdown $remainingSeconds"
        start(running = true, seconds = remainingSeconds)
    }

    override fun startAnnouncementCountUp(initialElapsedSeconds: Int) {
        started += "countUp $initialElapsedSeconds"
        start(running = true, seconds = initialElapsedSeconds)
    }

    override fun startAnnouncementSpecificTime(targetHour: Int, targetMinute: Int, targetSecond: Int) {
        started += "specificTime $targetHour:$targetMinute:$targetSecond"
        start(running = false, seconds = null)
    }

    override fun startAnnouncementClockDisplay(formatPattern: String) {
        started += "clock $formatPattern"
        start(running = false, seconds = null)
    }

    override fun pauseAnnouncementTimer(remainingSeconds: Int?) {
        timerRunning.value = false
        announcementTickerActive.value = false
        announcementTimerExpired.value = false
        if (remainingSeconds != null) timerRemainingSeconds.value = remainingSeconds
    }

    private fun start(running: Boolean, seconds: Int?) {
        if (seconds != null) timerRemainingSeconds.value = seconds
        timerRunning.value = running
        announcementTickerActive.value = true
        announcementTimerExpired.value = false
    }
}
