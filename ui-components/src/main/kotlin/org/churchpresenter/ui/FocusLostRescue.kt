package org.churchpresenter.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.awt.Canvas
import java.awt.Component
import java.awt.Container
import java.awt.Window as AwtWindow
private const val RESCUE_ATTEMPTS = 10
private const val RESCUE_INTERVAL_MS = 100L


/**
 * Creates and drives the focus-lost rescue for one tab. [active] gates the banner and the
 * auto-heal (e.g. false while the tab has no keyboard-navigable content yet). The caller must
 * wire the tab root's `onFocusChanged { state.onFocusChanged(it.hasFocus) }` and render
 * [FocusLostBanner] where the warning should appear.
 */
@Composable
fun rememberFocusLostRescue(
    hostWindow: AwtWindow?,
    focusRequester: FocusRequester,
    active: Boolean = true,
): FocusLostRescueState {
    val scope = rememberCoroutineScope()
    val state = remember(hostWindow, focusRequester) {
        FocusLostRescueState(hostWindow, focusRequester, scope)
    }
    state.active = active
    state.windowFocused = LocalWindowInfo.current.isWindowFocused
    val requester by rememberUpdatedState(focusRequester)
    // macOS swallows the first click on an inactive window — the rescue banner's onClick
    // never fires from that click. Re-take keyboard focus whenever the window comes back to
    // the foreground with no focus owner inside the tab, so ANY activation click (banner,
    // content, title bar) or Cmd+Tab revives the keys immediately. AWT hands keyboard focus
    // back to the Compose panel asynchronously after activation — a single immediate request
    // is silently dropped (verified hands-on) — so retry briefly until the tab actually owns
    // focus again.
    LaunchedEffect(state.windowFocused) {
        if (state.windowFocused && !state.tabHasFocus && state.active) {
            state.restoreAwtFocusOwner()
            repeat(RESCUE_ATTEMPTS) {
                requester.requestFocus()
                delay(RESCUE_INTERVAL_MS)
                if (state.tabHasFocus) return@LaunchedEffect
            }
        }
    }
    return state
}

/** The rescue banner. Renders nothing while focus is healthy. */
@Composable
fun FocusLostBanner(state: FocusLostRescueState, text: String, modifier: Modifier = Modifier) {
    if (!state.bannerVisible) return
    Button(
        onClick = { state.rescue() },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // min-height, not fixed: in narrow panels the text wraps to two lines.
            .heightIn(min = 48.dp)
            // MUST stay non-focusable: a click on a focusable button takes focus, which
            // hides this very banner, which destroys the focused node, which clears focus,
            // which re-shows the banner — an infinite show/hide oscillation (observed live
            // via focus logging).
            .focusProperties { canFocus = false },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Root-modifier press hook: heals the AWT window-focus wedge on any press inside the tab
 *  (see [FocusLostRescueState.onPointerPress]). Attach to the same node as the tab's
 *  focusRequester/focusable chain. */
fun Modifier.focusRescuePressHook(state: FocusLostRescueState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) state.onPointerPress()
        }
    }
}

/** Deepest AWT Canvas under [c] — the Skiko/Compose render surface that must own AWT
 *  keyboard focus for key events to reach Compose at all. */
fun findAwtCanvas(c: Component): Component? = when (c) {
    is Canvas -> c
    is Container -> c.components.firstNotNullOfOrNull { findAwtCanvas(it) }
    else -> null
}
