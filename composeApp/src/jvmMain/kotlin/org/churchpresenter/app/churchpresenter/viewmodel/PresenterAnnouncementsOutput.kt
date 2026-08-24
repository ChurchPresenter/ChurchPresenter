package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.announcements.AnnouncementsOutput
import org.churchpresenter.app.churchpresenter.presenter.Presenting

/**
 * [PresenterManager] seen through the Announcements feature's own port.
 *
 * `:announcements` states what it needs of the outputs and knows nothing about `Presenting`,
 * screen locks or the manager itself; this is the one place that maps the two together. It holds
 * no state of its own — every member reads or calls straight through — so there is nothing here to
 * get out of step with the manager.
 */
class PresenterAnnouncementsOutput(private val manager: PresenterManager) : AnnouncementsOutput {

    override val announcementLive: Boolean
        get() = manager.presentingMode.value == Presenting.ANNOUNCEMENTS

    override val tickerActive: Boolean get() = manager.announcementTickerActive.value

    override val timerExpired: Boolean get() = manager.announcementTimerExpired.value

    override val timerRunning: Boolean get() = manager.timerRunning.value

    override val timerRemainingSeconds: Int get() = manager.timerRemainingSeconds.value

    override fun setText(text: String) = manager.setAnnouncementText(text)

    override fun goLive() = manager.setPresentingMode(Presenting.ANNOUNCEMENTS)

    override fun clear() = manager.requestClearDisplay()

    override fun isScreenLockedToAnnouncements(screenIndex: Int): Boolean =
        manager.screenLocks.value[screenIndex] == Presenting.ANNOUNCEMENTS

    override fun setScreenLockedToAnnouncements(screenIndex: Int, locked: Boolean) =
        manager.setScreenLock(screenIndex, if (locked) Presenting.ANNOUNCEMENTS else null)

    override var tickerLive: Boolean
        get() = manager.announcementTickerLive.value
        set(value) = manager.setAnnouncementTickerLive(value)

    override fun startClockDisplay(format: String) = manager.startAnnouncementClockDisplay(format)

    override fun startSpecificTime(hour: Int, minute: Int, second: Int) =
        manager.startAnnouncementSpecificTime(hour, minute, second)

    override fun startCountUp(fromSeconds: Int) = manager.startAnnouncementCountUp(fromSeconds)

    override fun startCountdown(seconds: Int, expiredText: String) =
        manager.startAnnouncementCountdown(seconds, expiredText)

    override fun pauseTimer(atSeconds: Int?) =
        if (atSeconds == null) manager.pauseAnnouncementTimer() else manager.pauseAnnouncementTimer(atSeconds)
}
