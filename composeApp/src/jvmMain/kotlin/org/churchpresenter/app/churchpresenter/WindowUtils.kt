package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState

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

