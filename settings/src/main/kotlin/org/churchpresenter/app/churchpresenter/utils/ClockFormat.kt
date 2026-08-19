package org.churchpresenter.app.churchpresenter.utils

import java.time.chrono.Chronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * True if the system's default locale displays time in 24-hour format (no AM/PM).
 *
 * Lives here rather than beside the rest of [Utils] because a settings *default* asks it —
 * `AnnouncementsSettings.liveClockFormat` picks its pattern from the locale at construction — and
 * this module may not depend on the app.
 */
fun isSystemUsing24HourFormat(): Boolean {
    val locale = Locale.getDefault()
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        null, FormatStyle.SHORT, Chronology.ofLocale(locale), locale
    )
    return !pattern.contains('h')
}
