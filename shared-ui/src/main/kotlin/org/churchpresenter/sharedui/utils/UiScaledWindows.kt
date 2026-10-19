package org.churchpresenter.sharedui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.theme.LocalThemeCustomization

/**
 * The size an operator window declared, grown by the interface size and kept on the primary screen.
 *
 * A window's own size is in the display's units, outside the scale `ProvideUiScale`
 * applies to what is drawn in it — so at 150% a dialog that kept its declared size would draw its
 * content half as large again in the same room. At the default size this is the declared size,
 * untouched. Output windows never use it.
 */
@Composable
fun uiScaledWindowSize(width: Dp, height: Dp): DpSize {
    val scale = LocalThemeCustomization.current.uiScale
    return remember(width, height, scale) {
        if (scale == 1f) {
            DpSize(width, height)
        } else {
            val screen = primaryScreenSizeDp()
            dialogSizeWithin(width * scale, height * scale, screen.width, screen.height)
        }
    }
}

/** A dialog of the declared size, at the interface size, centred on the main window. */
@Composable
fun rememberCenteredDialogState(mainWindowState: WindowState?, width: Dp, height: Dp): DialogState {
    val size = uiScaledWindowSize(width, height)
    return rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, size.width, size.height),
        size = size,
    )
}
