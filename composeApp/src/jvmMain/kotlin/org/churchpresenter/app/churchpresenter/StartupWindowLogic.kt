package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.DisplayRect
import org.churchpresenter.settings.ResolvedMerge
import java.awt.Rectangle

/**
 * Where a merge of real displays opens its one window: across the whole picture -- or nowhere
 * while any of its displays is missing from [attached], since half a video wall is not a picture
 * anyone wants and the window would land across whatever took that display's place. It opens again
 * by itself when the display comes back, exactly as a single-display output does.
 */
internal fun mergedWindowBounds(merge: ResolvedMerge, attached: List<DisplayRect>): DisplayRect? {
    val desktop = merge.desktop ?: return null
    val members = merge.tiles.values.map { DisplayRect(desktop.x + it.x, desktop.y + it.y, it.width, it.height) }
    return desktop.takeIf { attached.containsAll(members) }
}

/**
 * Where a screen output's window goes: its own display ([single]) when it is merged with nothing;
 * nowhere for a merged display that is not the first of its merge, whose window covers it; and
 * across the whole picture for that first one -- see [mergedWindowBounds].
 */
internal fun screenWindowRect(
    merge: ResolvedMerge?,
    output: String,
    attached: List<DisplayRect>,
    single: () -> DisplayRect?,
): DisplayRect? = when {
    merge == null -> single()
    merge.host != output -> null
    else -> mergedWindowBounds(merge, attached)
}

/** A monitor's AWT bounds as the settings module's rectangle. */
internal fun Rectangle.asDisplayRect(): DisplayRect = DisplayRect(x, y, width, height)

/** The smallest the main window may open at, whatever was saved. */
internal const val MIN_MAIN_WINDOW_WIDTH = 800

internal const val MIN_MAIN_WINDOW_HEIGHT = 600

/**
 * The size the main window opens at.
 *
 * Only a floating window has its own size worth restoring; maximized and fullscreen take the
 * primary display's, so a saved size from a monitor since unplugged cannot strand the window at a
 * shape the current display cannot show.
 *
 * A restored floating size is clamped at both ends, because it is written back verbatim on exit and
 * nothing else checks it. Too large is the monitor case the paragraph above only covers for
 * maximized windows: a window sized on a 4K display and reopened on a laptop panel would otherwise
 * open with its own edges past the screen, and a window has to be draggable by an edge to be
 * recoverable. Too small is the same trap from the other side — the window can be dragged down to
 * nothing, and that nothing is what it reopens as.
 *
 * The lower bound wins ties: on a display smaller than [MIN_MAIN_WINDOW_WIDTH] the window is opened
 * too big for the screen rather than too small to use, since an oversized window can still be moved
 * and resized while a collapsed one cannot.
 */
internal fun startupWindowSize(
    isFloating: Boolean,
    savedWidth: Int,
    savedHeight: Int,
    primaryWidth: Int,
    primaryHeight: Int,
): Pair<Int, Int> {
    if (!isFloating) return primaryWidth to primaryHeight
    val width = savedWidth.coerceIn(MIN_MAIN_WINDOW_WIDTH, maxOf(primaryWidth, MIN_MAIN_WINDOW_WIDTH))
    val height = savedHeight.coerceIn(MIN_MAIN_WINDOW_HEIGHT, maxOf(primaryHeight, MIN_MAIN_WINDOW_HEIGHT))
    return width to height
}

/**
 * The position the main window opens at.
 *
 * The primary display's own origin is the fallback rather than 0,0 — on a multi-monitor desktop the
 * origin can belong to a different display, which would open the window on the wrong screen.
 */
internal fun startupWindowPosition(
    restoreGeometry: Boolean,
    savedX: Int,
    savedY: Int,
    primaryX: Int,
    primaryY: Int,
): Pair<Int, Int> = if (restoreGeometry) savedX to savedY else primaryX to primaryY
