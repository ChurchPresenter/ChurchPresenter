package org.churchpresenter.statistics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The rolling windows offered alongside the calendar years, in months. */
val ROLLING_MONTHS = listOf(3, 6, 12)

/**
 * A reporting period for the statistics views. Resolved against a caller-supplied `today` rather
 * than the clock, so screenshots and tests are deterministic.
 */
sealed interface StatisticsPeriod {
    /** Everything ever recorded. */
    data object AllTime : StatisticsPeriod

    /** A rolling window ending today, e.g. the last 3 months. */
    data class LastMonths(val months: Int) : StatisticsPeriod

    /** A single calendar year, January 1 to December 31. */
    data class Year(val year: Int) : StatisticsPeriod
}

/** An inclusive epoch-millis range, as the [StatisticsManager] range queries expect. */
data class DateRange(val fromMs: Long, val toMs: Long)

private const val LAST_HOUR = 23
private const val LAST_MINUTE = 59
private const val LAST_SECOND = 59
private const val DECEMBER = 12
private const val DECEMBER_LAST_DAY = 31

/** Midnight at the start of this date, in epoch millis. */
internal fun LocalDate.startOfDayMs(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 23:59:59 on this date, in epoch millis — the inclusive end of the day. */
internal fun LocalDate.endOfDayMs(): Long =
    atTime(LAST_HOUR, LAST_MINUTE, LAST_SECOND).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * The first and last day a period covers.
 *
 * The report's From/To pickers hold calendar dates while its queries take millis, so both come from
 * here — [resolve] is this function plus the day boundaries. Nothing computes a period twice.
 *
 * A period never runs past [today]: asking for the current year means January 1 to today, not to a
 * December that has not happened.
 *
 * @param today the date to measure rolling windows back from
 * @param earliestMs the oldest recorded event, from [StatisticsManager.getEarliestEventTime]; the
 *   lower bound of [StatisticsPeriod.AllTime]
 */
fun StatisticsPeriod.resolveDates(today: LocalDate, earliestMs: Long?): Pair<LocalDate, LocalDate> =
    when (this) {
        is StatisticsPeriod.AllTime -> {
            val from = earliestMs
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                ?.coerceAtMost(today)
                ?: LocalDate.of(today.year, 1, 1)
            from to today
        }
        is StatisticsPeriod.LastMonths -> today.minusMonths(months.toLong()) to today
        is StatisticsPeriod.Year -> LocalDate.of(year, 1, 1) to
            LocalDate.of(year, DECEMBER, DECEMBER_LAST_DAY).coerceAtMost(today)
    }

/** Turns a period into the inclusive millis range to query. */
internal fun StatisticsPeriod.resolve(today: LocalDate, earliestMs: Long?): DateRange {
    val (from, to) = resolveDates(today, earliestMs)
    return DateRange(from.startOfDayMs(), to.endOfDayMs())
}

/**
 * The calendar years that can hold data, newest first: from the year of the oldest event through
 * the current year. An empty log yields just the current year.
 */
fun availableYears(today: LocalDate, earliestMs: Long?): List<Int> {
    val firstYear = earliestMs
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }
        ?.coerceAtMost(today.year)
        ?: today.year
    return (firstYear..today.year).reversed().toList()
}
