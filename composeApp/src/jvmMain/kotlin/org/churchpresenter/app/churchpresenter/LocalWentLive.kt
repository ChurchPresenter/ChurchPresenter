package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * Reports a row going on screen from a tab's own Go Live -- the Pictures folder, the deck, the
 * clip, the scene -- as the schedule row that identifies it.
 *
 * What `LiveDurationLog` times and what the automation engine yields to. `main.kt` provides it
 * around `MainDesktop`; the four tabs read it at the point they set the presenting mode, building
 * the same row their Add to Schedule and Save Preset buttons build. It travels this way, like
 * [LocalOpenCalendar], because nothing between `main.kt` and those go-live sites has any use for
 * it, and threading it as a parameter would re-key four baselined tab signatures for no gain. A
 * no-op where nothing provides it, which is every test that composes a tab alone.
 */
val LocalWentLive = staticCompositionLocalOf<(ScheduleItem) -> Unit> { {} }
