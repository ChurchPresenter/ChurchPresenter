package org.churchpresenter.app.churchpresenter.utils

import kotlinx.serialization.Serializable

private const val HOURS_PER_DAY = 24L
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60
private const val MILLIS_PER_SECOND = 1000

/**
 * How often the automatic startup check is allowed to run. Manual "Check for Updates…"
 * always runs regardless of this setting — it only gates the silent background check.
 */
@Serializable
enum class UpdateCheckInterval(private val days: Int?) {
    EVERY_LAUNCH(0),
    WEEKLY(7),
    MONTHLY(30),
    EVERY_2_MONTHS(60),
    EVERY_3_MONTHS(90),
    EVERY_6_MONTHS(180),
    NEVER(null);

    fun isDueSince(lastCheckedAtMillis: Long): Boolean {
        val intervalDays = days ?: return false
        if (intervalDays == 0) return true
        val elapsedMillis = System.currentTimeMillis() - lastCheckedAtMillis
        return elapsedMillis >= intervalDays * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
    }
}
