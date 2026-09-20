package org.churchpresenter.calendar.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val HH_MM_PARTS = 2
private const val WEEK_LENGTH = 7
private const val GRID_WEEKS = 6

/** The date form used everywhere a date is stored, and the only one [CalendarDocument] understands. */
private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** Wall-clock time text, the way a service start is stored. */
private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** [date] as it is stored, `2026-09-20`. */
fun storedDate(date: LocalDate): String = date.format(ISO_DATE)

/** A stored date back to a [LocalDate], or null if the text is not one. */
fun parseStoredDate(text: String): LocalDate? = runCatching { LocalDate.parse(text, ISO_DATE) }.getOrNull()

/** A stored `HH:mm` back to a [LocalTime], or null. Accepts `9:05` as well as `09:05`. */
fun parseStoredTime(text: String): LocalTime? {
    val parts = text.trim().split(':').map { it.toIntOrNull() }
    if (parts.size != HH_MM_PARTS || parts.any { it == null }) return null
    // LocalTime.of rejects 25:00 and 10:61 by throwing, which is the validation this wants.
    return runCatching { LocalTime.of(parts[0]!!, parts[1]!!) }.getOrNull()
}

/** [time] as it is stored, always zero-padded. */
fun storedTime(time: LocalTime): String = time.format(HH_MM)

/**
 * The day the week starts on in [locale] — Monday across most of Europe, Sunday in the US and much
 * of Asia.
 *
 * From the JDK's own locale data rather than a constant. The app ships in 34 languages, and a
 * planner that draws a Sunday service in the last column for half its users has the week wrong.
 */
fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek =
    WeekFields.of(locale).firstDayOfWeek

/**
 * The six weeks a month grid draws, starting on [firstDayOfWeek].
 *
 * Always six weeks, so the grid does not change height between months — a five-week month drawn
 * shorter than a six-week one makes everything below it jump as you page through the year.
 */
fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek = firstDayOfWeek()): List<LocalDate> {
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - firstDayOfWeek.value) + WEEK_LENGTH) % WEEK_LENGTH
    val start = first.minusDays(lead.toLong())
    return (0 until WEEK_LENGTH * GRID_WEEKS).map { start.plusDays(it.toLong()) }
}

/** The weekday headings for a grid starting on [firstDayOfWeek], in grid order. */
fun weekdayOrder(firstDayOfWeek: DayOfWeek = firstDayOfWeek()): List<DayOfWeek> =
    (0 until WEEK_LENGTH).map { DayOfWeek.of(((firstDayOfWeek.value - 1 + it) % WEEK_LENGTH) + 1) }

/**
 * Month and weekday names, from the JDK's locale data rather than from this module's `strings.xml`.
 *
 * Worth stating because the obvious alternative is twelve month strings and seven weekday strings
 * per locale, which is what the CCLI report window did (`ccli_month_*`, translated 34 times over).
 * `getDisplayName` already has all of them, correctly cased and correctly abbreviated for the
 * locale, and it cannot fall out of step with a translation nobody updated.
 */
fun monthName(month: Month, locale: Locale = Locale.getDefault()): String =
    month.getDisplayName(TextStyle.FULL, locale)

/** The short weekday heading over a grid column, e.g. `Mon`. */
fun weekdayName(day: DayOfWeek, locale: Locale = Locale.getDefault()): String =
    day.getDisplayName(TextStyle.SHORT, locale)

/** `September 2026`, the month grid's heading. */
fun monthHeading(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    "${monthName(month.month, locale)} ${month.year}"
