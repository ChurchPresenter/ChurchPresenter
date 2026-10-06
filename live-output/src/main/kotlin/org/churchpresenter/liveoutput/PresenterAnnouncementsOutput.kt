package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import org.churchpresenter.announcements.AnnouncementsOutput
import org.churchpresenter.sharedui.models.Presenting

/**
 * [PresenterManager] as the `:announcements` tab sees it: every call goes straight through, the
 * timer included, which keeps ticking on the manager whichever tab is open.
 *
 * Except that a text announcement goes through the [PreviewBus]: while preview mode is on it is
 * cued, and waits for Take. A timer's runs on air as before -- its ticker is live by the time its
 * text arrives, and it ticks on this manager.
 *
 * A separate class rather than `PresenterManager : AnnouncementsOutput`, for the same reason as
 * [PresenterSlidesOutput]: the manager's own declaration stays untouched.
 */
class PresenterAnnouncementsOutput(private val manager: PresenterManager) : AnnouncementsOutput {
    override val announcementsLive: Boolean get() = manager.isLive(Presenting.ANNOUNCEMENTS)
    override val screenLocks: State<Map<Int, Presenting>> get() = manager.screenLocks
    override val timerRemainingSeconds: State<Int> get() = manager.timerRemainingSeconds
    override val timerRunning: State<Boolean> get() = manager.timerRunning
    override val announcementTickerActive: State<Boolean> get() = manager.announcementTickerActive
    override val announcementTimerExpired: State<Boolean> get() = manager.announcementTimerExpired

    override fun setPresentingMode(mode: Presenting) = manager.previewBus.present(mode)
    override fun requestClearDisplay() = manager.requestClearDisplay()
    override fun setScreenLock(screenIndex: Int, mode: Presenting?) = manager.setScreenLock(screenIndex, mode)
    override fun setAnnouncementText(text: String) = cueOrSetAnnouncementText(manager, text)
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

/**
 * [text] as an announcement going up: cued on Preview while preview mode is on, unless a timer's
 * ticker is live on air -- then it is that timer's text, and goes straight to [manager].
 */
fun cueOrSetAnnouncementText(manager: PresenterManager, text: String) {
    val bus = manager.previewBus
    if (manager.announcementTickerLive.value || bus.forNewItem(Presenting.ANNOUNCEMENTS) === manager) {
        manager.setAnnouncementText(text)
    } else {
        bus.manager.setAnnouncementText(text)
        bus.manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
    }
}
