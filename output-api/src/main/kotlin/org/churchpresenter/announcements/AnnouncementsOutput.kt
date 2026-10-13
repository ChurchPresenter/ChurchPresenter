package org.churchpresenter.announcements

import androidx.compose.runtime.State
import org.churchpresenter.sharedui.models.Presenting

/**
 * What the Announcements tab needs of the live output: what it is presenting and which screens are
 * locked, the announcement text, and the timer.
 *
 * The timer ticks on the output rather than in the tab, so a countdown keeps running while the
 * operator is on another tab; the tab only starts, pauses and reads it.
 */
interface AnnouncementsOutput {
    /** Whether announcements are on air. */
    val announcementsLive: Boolean
    val screenLocks: State<Map<Int, Presenting>>

    /** Seconds left on a countdown, or elapsed on a count-up. */
    val timerRemainingSeconds: State<Int>

    /** Whether a countdown or count-up is ticking. */
    val timerRunning: State<Boolean>

    /** Whether any timer mode is running, the clock and the specific time included. */
    val announcementTickerActive: State<Boolean>

    /** Whether a countdown has run out. */
    val announcementTimerExpired: State<Boolean>

    fun setPresentingMode(mode: Presenting)
    fun requestClearDisplay()
    fun setScreenLock(screenIndex: Int, mode: Presenting?)
    fun setAnnouncementText(text: String)

    /** Whether the running timer is on screen, rather than only in the tab's preview. */
    fun setAnnouncementTickerLive(live: Boolean)

    fun startAnnouncementCountdown(remainingSeconds: Int, expiredText: String)
    fun startAnnouncementCountUp(initialElapsedSeconds: Int)
    fun startAnnouncementSpecificTime(targetHour: Int, targetMinute: Int, targetSecond: Int)
    fun startAnnouncementClockDisplay(formatPattern: String)

    /** Stops whichever timer is running, leaving [remainingSeconds] showing when given. */
    fun pauseAnnouncementTimer(remainingSeconds: Int? = null)
}
