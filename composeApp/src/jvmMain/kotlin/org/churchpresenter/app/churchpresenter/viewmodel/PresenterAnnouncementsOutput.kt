package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import org.churchpresenter.announcements.AnnouncementsOutput
import org.churchpresenter.sharedui.models.Presenting

/**
 * [PresenterManager] as the `:announcements` tab sees it: every call goes straight through, the
 * timer included, which keeps ticking on the manager whichever tab is open.
 *
 * A separate class rather than `PresenterManager : AnnouncementsOutput`, for the same reason as
 * [PresenterSlidesOutput]: the manager's own declaration stays untouched.
 */
class PresenterAnnouncementsOutput(private val manager: PresenterManager) : AnnouncementsOutput {
    override val presentingMode: State<Presenting> by lazy { manager.modeAsSeenBy(Presenting.ANNOUNCEMENTS) }
    override val screenLocks: State<Map<Int, Presenting>> get() = manager.screenLocks
    override val timerRemainingSeconds: State<Int> get() = manager.timerRemainingSeconds
    override val timerRunning: State<Boolean> get() = manager.timerRunning
    override val announcementTickerActive: State<Boolean> get() = manager.announcementTickerActive
    override val announcementTimerExpired: State<Boolean> get() = manager.announcementTimerExpired

    override fun setPresentingMode(mode: Presenting) = manager.setPresentingMode(mode)
    override fun requestClearDisplay() = manager.requestClearDisplay()
    override fun setScreenLock(screenIndex: Int, mode: Presenting?) = manager.setScreenLock(screenIndex, mode)
    override fun setAnnouncementText(text: String) = manager.setAnnouncementText(text)
    override fun setAnnouncementTickerLive(live: Boolean) = manager.setAnnouncementTickerLive(live)

    override fun startAnnouncementCountdown(remainingSeconds: Int, expiredText: String) =
        manager.startAnnouncementCountdown(remainingSeconds, expiredText)

    override fun startAnnouncementCountUp(initialElapsedSeconds: Int) =
        manager.startAnnouncementCountUp(initialElapsedSeconds)

    override fun startAnnouncementSpecificTime(targetHour: Int, targetMinute: Int, targetSecond: Int) =
        manager.startAnnouncementSpecificTime(targetHour, targetMinute, targetSecond)

    override fun startAnnouncementClockDisplay(formatPattern: String) =
        manager.startAnnouncementClockDisplay(formatPattern)

    override fun pauseAnnouncementTimer(remainingSeconds: Int?) = manager.pauseAnnouncementTimer(remainingSeconds)
}
