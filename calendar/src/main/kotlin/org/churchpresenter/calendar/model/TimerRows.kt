package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.time.LocalTime

/*
 * What the run of show can do to a timer row -- a timer is an `AnnouncementItem` with `isTimer`,
 * the same row the Announcements tab adds, so the Schedule tab and the outputs already know how to
 * run it. Timers come into a run of show as presets; the picker has no tab that makes one.
 */

/** How long from [now] to [target], counting on to tomorrow rather than going negative. */
fun secondsUntil(now: LocalTime, target: LocalTime): Int {
    val seconds = target.toSecondOfDay() - now.toSecondOfDay()
    return if (seconds < 0) seconds + SECONDS_PER_DAY else seconds
}

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
private const val SECONDS_PER_DAY = 86_400
