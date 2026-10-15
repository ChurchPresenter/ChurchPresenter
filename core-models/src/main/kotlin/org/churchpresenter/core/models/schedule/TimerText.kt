package org.churchpresenter.core.models.schedule

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/**
 * A timer's [seconds] as the tab and the output both show them: `MM:SS` under an hour, `H:MM:SS`
 * from an hour up, the hours unpadded.
 */
fun formatTimer(seconds: Int): String {
    val h = seconds / SECONDS_PER_HOUR
    val m = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = seconds % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
