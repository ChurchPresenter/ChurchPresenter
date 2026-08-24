package org.churchpresenter.announcements

/**
 * Everything the Announcements feature needs from whatever is driving the screens.
 *
 * The tab and its view model between them reached thirteen members of `PresenterManager` — text,
 * live mode, stage-monitor locks and five kinds of timer. Threading that many callbacks through
 * both would have been unreadable, and taking the manager itself is what the repo's ViewModel rule
 * forbids. So the feature states what it needs and `:composeApp` supplies it: one adapter over
 * `PresenterManager` lives up there, and `Presenting` never crosses into this module.
 *
 * Every member is announcement-specific on purpose. Nothing here should ever grow a parameter that
 * names another feature — that would make this a second presenter manager rather than a port.
 */
interface AnnouncementsOutput {

    /** True while announcement text — not a timer — is what the audience is looking at. */
    val announcementLive: Boolean

    /** True while a countdown, count-up or clock is ticking on the outputs. */
    val tickerActive: Boolean

    /** Whether the ticker is treated as live, which drives the tab's own controls. */
    var tickerLive: Boolean

    /** True once a duration countdown has reached zero. */
    val timerExpired: Boolean

    /** True while a timer is running, as opposed to paused at a value. */
    val timerRunning: Boolean

    /** Where a running or paused timer has got to. */
    val timerRemainingSeconds: Int

    /** Puts [text] on the outputs without changing what mode they are in. */
    fun setText(text: String)

    /** Makes the announcement text what the audience sees. */
    fun goLive()

    /** Takes whatever is up back down. */
    fun clear()

    /** Whether the screen at [screenIndex] is held on the announcement. */
    fun isScreenLockedToAnnouncements(screenIndex: Int): Boolean

    /** Holds the screen at [screenIndex] on the announcement, or releases it. */
    fun setScreenLockedToAnnouncements(screenIndex: Int, locked: Boolean)

    fun startClockDisplay(format: String)
    fun startSpecificTime(hour: Int, minute: Int, second: Int)
    fun startCountUp(fromSeconds: Int)
    fun startCountdown(seconds: Int, expiredText: String)

    /** Stops the ticker, optionally parking it at [atSeconds]. */
    fun pauseTimer(atSeconds: Int? = null)
}
