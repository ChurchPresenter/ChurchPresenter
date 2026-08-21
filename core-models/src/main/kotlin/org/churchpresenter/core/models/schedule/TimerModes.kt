package org.churchpresenter.core.models.schedule

object TimerModes {
    const val DURATION = "duration"
    const val CLOCK = "clock"

    /** Open-ended stopwatch: counts up from zero, no h:m:s configuration. */
    const val COUNT_UP = "count_up"

    /** Just displays the current wall-clock time, continuously, in a user-selectable format. */
    const val CLOCK_DISPLAY = "clock_display"
}
