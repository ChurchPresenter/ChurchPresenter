package org.churchpresenter.core.models.statistics

/**
 * A reporting period for the statistics views.
 *
 * Deliberately inert: resolving one into a [DateRange] needs a caller-supplied `today` (so
 * screenshots and tests are deterministic) and the earliest event on record, neither of which a
 * model can know. `:statistics` owns `resolveDates`/`resolve`/`availableYears` and the rolling
 * windows offered beside the calendar years.
 *
 * Not `@Serializable` and never persisted — this is what the report's pills are labelled with, not
 * anything that reaches disk.
 */
sealed interface StatisticsPeriod {
    /** Everything ever recorded. */
    data object AllTime : StatisticsPeriod

    /** A rolling window ending today, e.g. the last 3 months. */
    data class LastMonths(val months: Int) : StatisticsPeriod

    /** A single calendar year, January 1 to December 31. */
    data class Year(val year: Int) : StatisticsPeriod
}
