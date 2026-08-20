package org.churchpresenter.app.churchpresenter.data.settings

/**
 * The window size and position to persist when the app closes.
 *
 * Only a **floating** window has geometry worth remembering. A maximized or fullscreen window's
 * measured bounds are the screen's, not the user's choice, so storing them would overwrite the size
 * the window should return to when it is un-maximized — the user would maximize once and lose their
 * layout permanently. Instead the previous floating size is kept and the coordinates are cleared to
 * [NO_SAVED_POSITION].
 *
 * That sentinel is what the launch path checks (`windowX >= 0`) before restoring a position, so
 * clearing it means "open where the OS puts you" rather than "open at 0,0" — a real difference on a
 * multi-monitor setup, where 0,0 may be a screen that is no longer attached.
 */
// The six are the window's measured state as AWT reports it; a wrapper type for them would exist
// only to be unpacked again at the one call site.
@Suppress("LongParameterList")
fun AppSettings.withWindowGeometry(
    placement: String,
    isFloating: Boolean,
    width: Int,
    height: Int,
    x: Int,
    y: Int,
): AppSettings = copy(
    windowPlacement = placement,
    windowWidth = if (isFloating) width else windowWidth,
    windowHeight = if (isFloating) height else windowHeight,
    windowX = if (isFloating) x else NO_SAVED_POSITION,
    windowY = if (isFloating) y else NO_SAVED_POSITION,
)

/** `windowX`/`windowY` value meaning "no remembered position; let the OS place the window". */
const val NO_SAVED_POSITION = -1
