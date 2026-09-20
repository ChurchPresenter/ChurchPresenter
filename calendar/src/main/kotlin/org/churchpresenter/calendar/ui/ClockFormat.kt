package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_timing_minus
import org.churchpresenter.calendar.generated.resources.calendar_timing_on_time
import org.churchpresenter.calendar.generated.resources.calendar_timing_plus
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.localeUses24HourClock
import org.churchpresenter.calendar.model.parseStoredTime
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the window writes a time as `18:30` or `6:30 PM` — `CalendarPreferences.use24HourClock`,
 * provided once at the root of [CalendarApp].
 *
 * A composition local rather than a parameter because a time is drawn in every pane and every
 * sheet, and none of them decides anything from it: they format with it, the way they take their
 * colors from the theme. Format through `clockText`; never `service.startTime` directly.
 */
val LocalUse24HourClock: ProvidableCompositionLocal<Boolean> = compositionLocalOf { localeUses24HourClock() }

/** The run-of-show time column: wide enough for `10:00 AM` when that is what it holds. */
@Composable
@ReadOnlyComposable
fun rowTimeColumnWidth(): Dp =
    if (LocalUse24HourClock.current) CalendarMetrics.rowTimeColumn else CalendarMetrics.rowTimeColumnWide

/**
 * A pinned start said the way it was chosen: `−15`, `On time`, `+5`.
 *
 * A row's own clock column already gives the wall-clock time, so printing it on the row's chip as
 * well said the same thing twice and left the offset -- which is what a pre-service sequence is
 * planned in, and what has to stay right when the service moves -- nowhere on the row. The clock
 * time is a hover away. Falls back to the clock when either time is unreadable.
 */
@Composable
fun startOffsetLabel(startAt: String, serviceStartTime: String): String {
    val pinned = parseStoredTime(startAt)
    val start = parseStoredTime(serviceStartTime)
    if (pinned == null || start == null) return clockText(startAt, LocalUse24HourClock.current)
    val minutes = (pinned.toSecondOfDay() - start.toSecondOfDay()) / SECONDS_PER_MINUTE
    return when {
        minutes == 0 -> stringResource(Res.string.calendar_timing_on_time)
        minutes < 0 -> stringResource(Res.string.calendar_timing_minus, -minutes)
        else -> stringResource(Res.string.calendar_timing_plus, minutes)
    }
}

private const val SECONDS_PER_MINUTE = 60
