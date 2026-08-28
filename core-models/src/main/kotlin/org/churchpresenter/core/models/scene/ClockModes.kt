package org.churchpresenter.core.models.scene

/**
 * What a canvas [SceneSource.ClockSource] shows. These strings are written into saved scenes, so
 * [CLOCK] and [COUNTDOWN] keep the spelling they had before the other two existed.
 *
 * Deliberately not [org.churchpresenter.core.models.schedule.TimerModes], which the announcements
 * timer uses: that vocabulary spells the wall clock "clock_display" and spells counting down to a
 * time of day "clock", so the same word would mean two different things.
 */
object ClockModes {
    /** The wall clock, in [SceneSource.ClockSource.timeFormat]. */
    const val CLOCK = "clock"

    /** Counts down the configured duration, driven by its own start/pause/reset. */
    const val COUNTDOWN = "countdown"

    /** A stopwatch: counts up from zero, with nothing to configure. */
    const val COUNT_UP = "count_up"

    /** Counts down to a time of day, rolling to tomorrow once that time has passed. */
    const val TARGET_TIME = "target_time"
}
