package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow

/** The window a dialog asks for: its title, where and how big, and what closing it does. */
data class DialogFrameSpec(
    val title: String,
    val state: DialogState,
    val onClose: () -> Unit,
    val resizable: Boolean = true,
    val alwaysOnTop: Boolean = false,
)

/**
 * Opens the window a [DialogFrameSpec] describes and draws a dialog's content in it.
 *
 * Every dialog takes one, defaulted to [appDialogFrame]: the app always gets a real window, and a
 * test passes one that draws the content in place, since a headless test cannot open a window.
 */
typealias DialogFrame = @Composable (spec: DialogFrameSpec, content: @Composable () -> Unit) -> Unit

/** The app's dialogs: real `DialogWindow`s. */
val appDialogFrame: DialogFrame = { spec, content ->
    DialogWindow(
        onCloseRequest = spec.onClose,
        state = spec.state,
        title = spec.title,
        resizable = spec.resizable,
        alwaysOnTop = spec.alwaysOnTop,
    ) { content() }
}
