package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window

/**
 * Provides the main application [WindowState] to all descendant composables so that
 * dialogs can position themselves on the same screen as the main window.
 */
val LocalMainWindowState = compositionLocalOf<WindowState?> { null }

/**
 * Computes a [WindowPosition] that centres a dialog of the given size on the same
 * screen as the main application window.  Falls back to [WindowPosition.PlatformDefault]
 * when no main window state is available.
 */
fun centeredOnMainWindow(
    mainWindowState: WindowState?,
    dialogWidth: Dp,
    dialogHeight: Dp
): WindowPosition {
    val ws = mainWindowState ?: return WindowPosition.PlatformDefault
    val pos = ws.position
    val size = ws.size
    // If the position is not yet known (PlatformDefault / Aligned) fall back
    if (pos !is WindowPosition.Absolute) return WindowPosition.PlatformDefault
    val x = pos.x + (size.width - dialogWidth) / 2
    val y = pos.y + (size.height - dialogHeight) / 2
    return WindowPosition(x.coerceAtLeast(0.dp), y.coerceAtLeast(0.dp))
}

/** Space left around a dialog that had to be shrunk, so its edges and title bar stay grabbable. */
private val SCREEN_MARGIN = 48.dp

/**
 * The primary display's size, or `0x0` where there is no display to ask.
 *
 * Zero rather than an exception: the callers are sizing a window, and every one of them has a
 * declared size to fall back on. A headless JVM is the test suite, which never opens the windows
 * this feeds, so failing there would only mean failing tests for a value they do not use.
 */
fun primaryScreenSizeDp(): DpSize = try {
    val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration.bounds
    DpSize(bounds.width.dp, bounds.height.dp)
} catch (_: HeadlessException) {
    DpSize(0.dp, 0.dp)
}

/**
 * The size to open a dialog at when its preferred size may not fit the display.
 *
 * A dialog that declares more room than the screen has opens with its own edges off screen —
 * including, on most window managers, the bottom edge carrying its buttons, and there is no way to
 * drag a window by an edge that is not there. The settings dialog asks for 1400x900, which is wider
 * *and* taller than a 1366x768 laptop panel, so it has never opened fully visible on one.
 *
 * Shrinking is the whole behaviour: the dialog is resizable and its content scrolls, so a smaller
 * window costs a scroll, while an oversized one costs access to the controls. Sizes that already fit
 * are returned untouched, so nothing changes on a display with room.
 */
fun dialogSizeWithin(
    preferredWidth: Dp,
    preferredHeight: Dp,
    screenWidth: Dp,
    screenHeight: Dp,
): DpSize {
    // A screen smaller than the margin is not a real display — a stub value, or a probe that ran
    // before the device reported itself. Preferring the declared size there fails visibly rather
    // than resolving to something near zero.
    if (screenWidth <= SCREEN_MARGIN || screenHeight <= SCREEN_MARGIN) {
        return DpSize(preferredWidth, preferredHeight)
    }
    return DpSize(
        preferredWidth.coerceAtMost(screenWidth - SCREEN_MARGIN),
        preferredHeight.coerceAtMost(screenHeight - SCREEN_MARGIN),
    )
}

data class ScreenArea(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

fun usableScreenArea(near: Window?): ScreenArea? = try {
    val config = near?.graphicsConfiguration
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    areaInside(config.bounds, Toolkit.getDefaultToolkit().getScreenInsets(config))
} catch (_: HeadlessException) {
    null
}

internal fun areaInside(bounds: Rectangle, insets: Insets): ScreenArea = ScreenArea(
    x = (bounds.x + insets.left).dp,
    y = (bounds.y + insets.top).dp,
    width = (bounds.width - insets.left - insets.right).dp,
    height = (bounds.height - insets.top - insets.bottom).dp,
)

internal fun staysAboveMainWindow(active: Any?, mainWindow: Any?, own: Any?): Boolean =
    mainWindow != null && active != null && (active === mainWindow || active === own)
