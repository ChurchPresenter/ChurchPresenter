package org.churchpresenter.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.jetbrains.compose.resources.painterResource

/** The window a tool asks for: its title, its opening size, and what closing it does. */
data class ToolWindowSpec(val title: String, val size: DpSize, val onClose: () -> Unit)

/**
 * Opens the window a [ToolWindowSpec] describes and draws a tool in it. The app's is
 * [appToolWindowFrame]; a test passes one that draws in place, since it cannot open a window.
 */
typealias ToolWindowFrame = @Composable (spec: ToolWindowSpec, content: @Composable () -> Unit) -> Unit

/** The app's tool windows: real windows with the app icon. */
val appToolWindowFrame: ToolWindowFrame = { spec, content ->
    Window(
        onCloseRequest = spec.onClose,
        title = spec.title,
        icon = painterResource(IconRes.drawable.ic_app_icon),
        state = rememberWindowState(width = spec.size.width, height = spec.size.height),
    ) { content() }
}
