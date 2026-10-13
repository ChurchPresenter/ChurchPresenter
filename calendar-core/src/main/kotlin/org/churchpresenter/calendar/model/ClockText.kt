package org.churchpresenter.calendar.model

import java.time.LocalTime
import java.time.chrono.Chronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The clock format — `18:30` or `6:30 PM` — as a time is shown and as one is typed.
 *
 * Storage is untouched by any of this: a time is written to `calendar.json` as `HH:mm` whatever
 * the setting, through [storedTime], so flipping the format rewrites nothing.
 */

private const val NOON = 12
private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val CLOCK_TEXT = Regex("(\\d{1,2}):(\\d{2})\\s*(\\S*)")
private val AM_MARKERS = setOf("am", "a.m.", "a")
private val PM_MARKERS = setOf("pm", "p.m.", "p")

/**
 * Whether [locale] writes a clock time without AM/PM — what the calendar's clock format starts
 * as. The same test the app's own clocks make, from the JDK's short time pattern.
 */
fun localeUses24HourClock(locale: Locale = Locale.getDefault()): Boolean {
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        null, FormatStyle.SHORT, Chronology.ofLocale(locale), locale
    )
    return !pattern.contains('h')
}

/** [time] as the calendar shows it: `18:30`, or `6:30 PM` when [use24Hour] is false. */
fun clockText(time: LocalTime, use24Hour: Boolean, locale: Locale = Locale.getDefault()): String =
    if (use24Hour) time.format(HH_MM) else time.format(DateTimeFormatter.ofPattern("h:mm a", locale))

/** A stored `HH:mm` as the calendar shows it, or the text itself when it is not a time. */
fun clockText(stored: String, use24Hour: Boolean, locale: Locale = Locale.getDefault()): String =
    parseStoredTime(stored)?.let { clockText(it, use24Hour, locale) } ?: stored

/**
 * What was typed into a time field: `18:30`, `6:30 PM`, `6:30pm`, or the same with [locale]'s own
 * AM/PM markers. Null when it is none of those. Both forms are accepted whatever the format
 * setting, so a time can be typed the way it is shown and the way it is stored.
 */
fun parseClockText(text: String, locale: Locale = Locale.getDefault()): LocalTime? {
    val match = CLOCK_TEXT.matchEntire(text.trim()) ?: return null
    val (hourText, minuteText, marker) = match.destructured
    if (marker.isEmpty()) return parseStoredTime("$hourText:$minuteText")
    val hour = hourText.toInt()
    val isPm = when (marker.lowercase()) {
        in AM_MARKERS, marker(LocalTime.MIDNIGHT, locale) -> false
        in PM_MARKERS, marker(LocalTime.NOON, locale) -> true
        else -> null
    }
    return if (isPm == null || hour !in 1..NOON) {
        null
    } else {
        runCatching { LocalTime.of((hour % NOON) + if (isPm) NOON else 0, minuteText.toInt()) }.getOrNull()
    }
}

/** [locale]'s AM or PM marker, lower-cased for comparison. */
private fun marker(time: LocalTime, locale: Locale): String =
    time.format(DateTimeFormatter.ofPattern("a", locale)).lowercase()
