package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.window.WindowPlacement

/**
 * How the main window's placement is stored in settings and read back at launch.
 *
 * Lives in `utils/` rather than beside [org.churchpresenter.settings.AppSettings]
 * because [WindowPlacement] is a Compose type and `data/settings` is deliberately Compose-free.
 */

/** The persisted form of [placement]. Exhaustive on purpose: adding a placement fails to compile
 *  here rather than silently writing a string the reader below does not understand. */
fun windowPlacementToSettings(placement: WindowPlacement): String = when (placement) {
    WindowPlacement.Floating -> FLOATING
    WindowPlacement.Fullscreen -> FULLSCREEN
    WindowPlacement.Maximized -> MAXIMIZED
}

/**
 * The placement a saved string means, defaulting to [WindowPlacement.Maximized].
 *
 * Maximized is the right fallback for anything unrecognised: it is the shipped default, and it is the
 * only one guaranteed to put the window somewhere visible. Restoring an unknown value as Floating
 * would pair with saved coordinates that may no longer be on any attached display.
 */
fun windowPlacementFromSettings(saved: String): WindowPlacement = when (saved) {
    FLOATING -> WindowPlacement.Floating
    FULLSCREEN -> WindowPlacement.Fullscreen
    else -> WindowPlacement.Maximized
}

private const val FLOATING = "floating"
private const val FULLSCREEN = "fullscreen"
private const val MAXIMIZED = "maximized"
