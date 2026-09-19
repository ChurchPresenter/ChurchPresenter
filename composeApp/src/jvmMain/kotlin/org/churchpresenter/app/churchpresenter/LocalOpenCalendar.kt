package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the Calendar Manager window -- what the Help menu's entry does -- for the Schedule tab's
 * toolbar button.
 *
 * `main.kt` provides it around `MainDesktop`; `ScheduleTab` reads it. The window lives in `main.kt`
 * beside the Help menu that opens it, and nothing between there and the toolbar has any use for the
 * callback, so it travels the way `LocalApplySettings` does rather than as a parameter through
 * `MainDesktop`. A no-op where nothing provides it, which is every test that composes the tab alone.
 */
val LocalOpenCalendar = staticCompositionLocalOf<() -> Unit> { {} }
