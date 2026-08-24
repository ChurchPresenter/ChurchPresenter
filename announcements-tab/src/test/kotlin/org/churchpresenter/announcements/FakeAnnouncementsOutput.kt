package org.churchpresenter.announcements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/**
 * A recording stand-in for whatever is driving the screens.
 *
 * **Everything the tab reads is Compose state.** The tab renders from these values, so a plain
 * `var` here would record the change and never redraw — eight tests failed exactly that way, all of
 * them asserting that a button had swapped or the display had cleared. The real adapter has the
 * same property by accident rather than design: its getters read a `State.value` during
 * composition, which registers the read.
 *
 * These suites used to build a real `PresenterManager` and assert against its state. That is the
 * app's object, not this module's, and it brought the whole presenter stack with it. The port makes
 * a plain fake enough: every member below is either what the tab asked for or a value the tab reads
 * back, so the assertions still describe behaviour rather than "a stub was called".
 *
 * The timer members are settable because the tab reads them to decide what to draw — a test that
 * needs a running countdown sets [tickerActive] and [timerRemainingSeconds] the way the real
 * outputs would have.
 */
internal class FakeAnnouncementsOutput : AnnouncementsOutput {

    var announcementText: String by mutableStateOf("")
        private set

    /** True once [goLive] has been called and nothing has cleared it since. */
    var announcementLiveNow: Boolean by mutableStateOf(false)
        private set

    var clearDisplayRequested: Boolean by mutableStateOf(false)
        private set

    var announcementTickerLive: Boolean by mutableStateOf(false)
        private set

    /** The screens currently held on the announcement. */
    val lockedScreens: MutableList<Int> = mutableStateListOf()

    /** What was last started, e.g. `"countdown 300"` — for asserting which timer the tab chose. */
    val timerStarts: MutableList<String> = mutableListOf()

    var pausedAt: Int? = null
        private set

    override var tickerActive: Boolean by mutableStateOf(false)
    override var timerExpired: Boolean by mutableStateOf(false)
    override var timerRunning: Boolean by mutableStateOf(false)
    override var timerRemainingSeconds: Int by mutableStateOf(0)

    override val announcementLive: Boolean get() = announcementLiveNow

    override fun setText(text: String) { announcementText = text }

    override fun goLive() { announcementLiveNow = true }

    override fun clear() {
        clearDisplayRequested = true
        announcementLiveNow = false
    }

    override fun isScreenLockedToAnnouncements(screenIndex: Int): Boolean = screenIndex in lockedScreens

    override fun setScreenLockedToAnnouncements(screenIndex: Int, locked: Boolean) {
        if (locked) lockedScreens += screenIndex else lockedScreens -= screenIndex
    }

    override var tickerLive: Boolean
        get() = announcementTickerLive
        set(value) { announcementTickerLive = value }

    override fun startClockDisplay(format: String) {
        timerStarts += "clock $format"
        tickerActive = true
    }

    override fun startSpecificTime(hour: Int, minute: Int, second: Int) {
        timerStarts += "specific %02d:%02d:%02d".format(hour, minute, second)
        tickerActive = true
    }

    override fun startCountUp(fromSeconds: Int) {
        timerStarts += "countUp $fromSeconds"
        tickerActive = true
        timerRunning = true
    }

    override fun startCountdown(seconds: Int, expiredText: String) {
        timerStarts += "countdown $seconds"
        tickerActive = true
        timerRunning = true
        timerRemainingSeconds = seconds
    }

    override fun pauseTimer(atSeconds: Int?) {
        pausedAt = atSeconds
        if (atSeconds != null) timerRemainingSeconds = atSeconds
        tickerActive = false
        timerRunning = false
    }
}
