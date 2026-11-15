package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.ui.window.Window
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.churchpresenter.sharedui.composables.HideOutputWindowCursor
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.strings.generated.resources.output_menu_fullscreen
import org.churchpresenter.strings.generated.resources.output_menu_window
import org.churchpresenter.strings.generated.resources.output_menu_on_top
import org.churchpresenter.strings.generated.resources.output_menu_normal
import org.churchpresenter.strings.generated.resources.output_menu_hide
import org.churchpresenter.strings.generated.resources.output_menu_next
import org.churchpresenter.strings.generated.resources.output_menu_previous
import org.churchpresenter.strings.generated.resources.output_menu_black
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.strings.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OutputWindowWithMenu(spec: OutputWindowSpec, content: @Composable () -> Unit) {
    var framed by remember(spec.undecorated) { mutableStateOf(!spec.undecorated) }
    var onTop by remember(spec.alwaysOnTop) { mutableStateOf(spec.alwaysOnTop) }
    var hidden by remember(spec.visible, spec.state) { mutableStateOf(false) }
    var blackedOut by remember { mutableStateOf(false) }
    val toggleFullscreen = {
        framed = !framed
        spec.state.placement = if (framed) WindowPlacement.Floating else WindowPlacement.Fullscreen
        if (framed) spec.state.size = DpSize(OUTPUT_MENU_WINDOW_WIDTH.dp, OUTPUT_MENU_WINDOW_HEIGHT.dp)
    }
    val fullscreenLabel = stringResource(Res.string.output_menu_fullscreen)
    val windowLabel = stringResource(Res.string.output_menu_window)
    val topLabel = stringResource(Res.string.output_menu_on_top)
    val normalLabel = stringResource(Res.string.output_menu_normal)
    val hideLabel = stringResource(Res.string.output_menu_hide)
    val nextLabel = stringResource(Res.string.output_menu_next)
    val previousLabel = stringResource(Res.string.output_menu_previous)
    val blackLabel = stringResource(Res.string.output_menu_black)
    key(framed) {
        Window(
            visible = spec.visible && !hidden,
            title = spec.title,
            icon = painterResource(IconRes.drawable.ic_app_icon),
            onCloseRequest = spec.onClose,
            state = spec.state,
            undecorated = !framed,
            resizable = framed,
            alwaysOnTop = onTop,
            onPreviewKeyEvent = { event ->
                val action = outputShortcut(event.key)
                val modified = event.isCtrlPressed || event.isAltPressed
                if (event.type != KeyEventType.KeyDown || modified || action == null) {
                    false
                } else {
                    when (action) {
                        OutputShortcut.NEXT -> spec.onStep(true)
                        OutputShortcut.PREVIOUS -> spec.onStep(false)
                        OutputShortcut.FULLSCREEN -> toggleFullscreen()
                        OutputShortcut.HIDE -> hidden = true
                        OutputShortcut.ON_TOP -> onTop = !onTop
                        OutputShortcut.BLACKOUT -> blackedOut = !blackedOut
                    }
                    true
                }
            },
        ) {
            spec.hideCursor?.let { HideOutputWindowCursor(window, it) }
            // The output guard keeps this window's last still under it, while the guard is on.
            LocalOutputGuard.current?.let { guard -> GuardedOutputWindow(guard, window) }
            ContextMenuArea(items = {
                listOf(
                    ContextMenuItem(nextLabel + "  → / Space") { spec.onStep(true) },
                    ContextMenuItem(previousLabel + "  ← / PageUp") { spec.onStep(false) },
                    ContextMenuItem(blackLabel + "  B") { blackedOut = !blackedOut },
                    ContextMenuItem((if (framed) fullscreenLabel else windowLabel) + "  F11", toggleFullscreen),
                    ContextMenuItem((if (onTop) normalLabel else topLabel) + "  T") { onTop = !onTop },
                    ContextMenuItem(hideLabel + "  Esc") { hidden = true },
                )
            }) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    if (blackedOut) Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
    }
}

private const val OUTPUT_MENU_WINDOW_WIDTH = 960
private const val OUTPUT_MENU_WINDOW_HEIGHT = 540

internal fun OutputWindowHost.withOutputSteps(onStep: (Boolean) -> Unit): OutputWindowHost = { spec, content ->
    this(spec.copy(onStep = onStep), content)
}
