package org.churchpresenter.calendar.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import org.churchpresenter.calendar.model.localeUses24HourClock

/**
 * Whether the window writes a time as `18:30` or `6:30 PM` — `CalendarPreferences.use24HourClock`,
 * provided once at the root of [CalendarApp].
 *
 * A composition local rather than a parameter because a time is drawn in every pane and every
 * sheet, and none of them decides anything from it: they format with it, the way they take their
 * colors from the theme. Format through `clockText`; never `service.startTime` directly.
 */
val LocalUse24HourClock: ProvidableCompositionLocal<Boolean> = compositionLocalOf { localeUses24HourClock() }
