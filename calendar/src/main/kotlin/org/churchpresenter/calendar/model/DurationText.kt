package org.churchpresenter.calendar.model

/**
 * How a planned length is written and read back.
 *
 * Its own file rather than part of `CalendarTime.kt` because none of it is a date: these are
 * *durations*, typed into a run-of-show row, and they have their own rule about what a bare number
 * means.
 */

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR

/** How many colon-separated parts each accepted shape has. */
private const val MINUTES_ONLY = 1
private const val MINUTES_SECONDS = 2
private const val HOURS_MINUTES_SECONDS = 3

/**
 * `4:30` for 270 seconds, `1:02:30` for 3750, `—` for nothing planned.
 *
 * The em dash rather than `0:00` because an unplanned row and a row planned to take no time are
 * different things, and the run of show shows both.
 */
fun formatDuration(seconds: Int?): String {
    if (seconds == null) return "—"
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

/**
 * `4:30` back to 270 seconds, `32` to 32 minutes, `1:02:30` to 3750. Null if it is not a duration.
 *
 * A bare number is read as **minutes**, not seconds: someone typing a song length into a planner
 * types `4`, and meaning four seconds by it is not a thing anybody does.
 */
fun parseDuration(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(':')
    if (parts.any { it.isBlank() || it.toIntOrNull() == null || it.toInt() < 0 }) return null
    val numbers = parts.map { it.toInt() }
    return when (numbers.size) {
        MINUTES_ONLY -> numbers[0] * SECONDS_PER_MINUTE
        MINUTES_SECONDS -> numbers[0] * SECONDS_PER_MINUTE + numbers[1]
        HOURS_MINUTES_SECONDS ->
            numbers[0] * SECONDS_PER_HOUR + numbers[1] * SECONDS_PER_MINUTE + numbers[2]
        else -> null
    }
}
