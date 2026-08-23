package org.churchpresenter.core.models.statistics

/**
 * An inclusive epoch-millis range, as the statistics range queries expect.
 *
 * What a [StatisticsPeriod] resolves to; `:statistics` owns the resolution, which needs a
 * caller-supplied `today` and the earliest event on record.
 */
data class DateRange(val fromMs: Long, val toMs: Long)
