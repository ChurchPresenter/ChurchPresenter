package org.churchpresenter.calendar.model

import java.time.LocalDate

/** The most a Copy can repeat — a year of weeks. */
const val MAX_REPEAT_COUNT: Int = 52

/** The Copy sheet's default when a repeat is chosen. */
const val DEFAULT_REPEAT_COUNT: Int = 4

/**
 * The [count] dates after [start] a service repeating by [repeat] falls on. Empty for
 * [ServiceRepeat.NONE]; [start] itself is never in the list.
 *
 * Monthly keeps the weekday ordinal — a third Sunday stays a third Sunday — because that is how
 * churches plan. A fifth Sunday in a month with no fifth Sunday lands on the month's last day
 * rather than being skipped, so "create 4" always creates 4; the preview shows the date so a
 * planner can see it and move it.
 */
fun recurrenceDates(start: LocalDate, repeat: ServiceRepeat, count: Int): List<LocalDate> {
    if (repeat == ServiceRepeat.NONE || count <= 0) return emptyList()
    return (1..count.coerceAtMost(MAX_REPEAT_COUNT)).map { n ->
        when (repeat) {
            ServiceRepeat.WEEKLY -> start.plusWeeks(n.toLong())
            ServiceRepeat.BIWEEKLY -> start.plusWeeks(2L * n)
            ServiceRepeat.MONTHLY -> sameWeekdayMonthsLater(start, n)
            ServiceRepeat.NONE -> start
        }
    }
}

private fun sameWeekdayMonthsLater(start: LocalDate, months: Int): LocalDate {
    val week = (start.dayOfMonth - 1) / DAYS_PER_WEEK
    val first = start.plusMonths(months.toLong()).withDayOfMonth(1)
    val offset = (start.dayOfWeek.value - first.dayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
    val day = 1 + offset + week * DAYS_PER_WEEK
    return first.withDayOfMonth(day.coerceAtMost(first.lengthOfMonth()))
}

/** Where a one-off Copy can be pasted, as the design's four chips. */
enum class CopyTarget {
    NEXT_WEEK,
    IN_TWO_WEEKS,
    NEXT_MONTH,
    TOMORROW;

    fun date(from: LocalDate): LocalDate = when (this) {
        NEXT_WEEK -> from.plusWeeks(1)
        IN_TWO_WEEKS -> from.plusWeeks(2)
        NEXT_MONTH -> sameWeekdayMonthsLater(from, 1)
        TOMORROW -> from.plusDays(1)
    }
}

private const val DAYS_PER_WEEK = 7
