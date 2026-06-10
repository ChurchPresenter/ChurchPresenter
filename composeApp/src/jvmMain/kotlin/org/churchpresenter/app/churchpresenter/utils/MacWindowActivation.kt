package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.FrameWindowScope
import kotlinx.coroutines.delay
import java.awt.Desktop

/**
 * Works around the macOS screen-menu-bar staying greyed out when this window
 * replaces another one during startup (splash → main): AppKit sometimes fails
 * to re-activate the app, leaving all menus disabled until refocus. Explicitly
 * bring the window to front and ask AppKit to activate the app once.
 */
@Composable
fun FrameWindowScope.MacMenuBarActivationFix() {
    if (!System.getProperty("os.name", "").lowercase().contains("mac")) return
    LaunchedEffect(Unit) {
        delay(300)   // let AppKit settle after the previous window closed
        try {
            window.toFront()
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                desktop.requestForeground(true)
            }
        } catch (_: Exception) {
            // best-effort; never break startup
        }
    }
}
