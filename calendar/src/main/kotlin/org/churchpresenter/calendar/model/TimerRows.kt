package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.util.UUID

/*
 * The timers the picker's Timer tab makes on the spot, and what the run of show can do to one --
 * a timer is an `AnnouncementItem` with `isTimer`, the same row the Announcements tab adds, so
 * the Schedule tab and the outputs already know how to run it.
 */

/** 15:00 -- the mockup's countdown, and about what a pre-service countdown is. */
const val DEFAULT_COUNTDOWN_SECONDS: Int = 900

/** A timer that counts down for [seconds] and then shows [expiredText]. */
fun countdownTimerItem(seconds: Int, expiredText: String = ""): ScheduleItem.AnnouncementItem =
    ScheduleItem.AnnouncementItem(
        id = UUID.randomUUID().toString(),
        text = "",
        isTimer = true,
        timerMode = TimerModes.DURATION,
        timerExpiredText = expiredText,
    ).withTimerSeconds(seconds)

/** A timer that counts up from zero until it is taken off. */
fun countUpTimerItem(): ScheduleItem.AnnouncementItem = ScheduleItem.AnnouncementItem(
    id = UUID.randomUUID().toString(),
    text = "",
    isTimer = true,
    timerMode = TimerModes.COUNT_UP,
)

/** Whether this row is a timer whose length is a duration -- the one kind a typed length can set. */
fun ScheduleItem.isDurationTimer(): Boolean =
    this is ScheduleItem.AnnouncementItem && isTimer && timerMode == TimerModes.DURATION

/** The same timer set to run for [seconds]. The display text is rebuilt, as `copy` would not. */
fun ScheduleItem.AnnouncementItem.withTimerSeconds(seconds: Int): ScheduleItem.AnnouncementItem {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    val fresh = ScheduleItem.AnnouncementItem(
        id = id,
        text = "",
        isTimer = true,
        timerHours = hours,
        timerMinutes = minutes,
        timerSeconds = secs,
    )
    return copy(timerHours = hours, timerMinutes = minutes, timerSeconds = secs, displayText = fresh.displayText)
}

/** How long a duration timer runs, in seconds; null for a timer of another kind. */
fun ScheduleItem.timerSeconds(): Int? {
    if (!isDurationTimer()) return null
    val timer = this as ScheduleItem.AnnouncementItem
    return timer.timerHours * SECONDS_PER_HOUR + timer.timerMinutes * SECONDS_PER_MINUTE + timer.timerSeconds
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
