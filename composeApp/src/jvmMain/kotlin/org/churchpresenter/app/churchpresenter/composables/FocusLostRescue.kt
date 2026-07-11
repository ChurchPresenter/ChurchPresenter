package org.churchpresenter.app.churchpresenter.composables

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
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import java.awt.event.WindowEvent

/**
 * Focus-lost rescue for tabs whose keyboard shortcuts (arrow keys, clicker keys) only work
 * while something inside the tab holds keyboard focus. Detects BOTH in-window focus loss
 * (operator clicked another panel) and whole-WINDOW focus loss — Compose keeps the focused
 * node "focused" when the window deactivates, so onFocusChanged alone can't see the operator
 * switching away, but keys stop arriving all the same.
 *
 * Every piece of this machinery was verified hands-on on macOS; see the comments on each part
 * before "simplifying" anything.
 */
class FocusLostRescueState internal constructor(
    private val hostWindow: AwtWindow?,
    private val focusRequester: FocusRequester,
    private val scope: CoroutineScope,
) {
    internal var tabHasFocus by mutableStateOf(false)
    internal var windowFocused by mutableStateOf(true)
    internal var active by mutableStateOf(true)
    private var resyncJob: Job? = null

    /** True while the rescue banner should be shown: keys are (or may be) dead. */
    val bannerVisible: Boolean
        get() = active && (!tabHasFocus || !windowFocused)

    /** Wire this to the tab root's `onFocusChanged { onFocusChanged(it.hasFocus) }`. */
    fun onFocusChanged(hasFocus: Boolean) {
        tabHasFocus = hasFocus
    }

    /** The banner's click action: heal AWT if wedged, then re-take Compose focus. */
    fun rescue() {
        resyncWedgedWindowFocus()
        restoreAwtFocusOwner()
        focusRequester.requestFocus()
    }

    /**
     * Wire to a root `pointerInput` Press hook: any press landing in the tab while AWT still
     * believes the window is unfocused is proof of the wedge described below — heal it no
     * matter what was clicked. Deliberately does NOT touch Compose focus (a press on a text
     * field must keep its own focus).
     */
    fun onPointerPress() {
        resyncWedgedWindowFocus()
    }

    // AWT on macOS can miss a window re-activation entirely: the NSWindow is key again and
    // mouse events flow, but WINDOW_ACTIVATED/WINDOW_GAINED_FOCUS are never delivered
    // (observed live via a global AWT event tap). In that wedged state the
    // KeyboardFocusManager discards every key event, so the operator's keys stay dead no
    // matter where they click. Repost the missing activation events to resync.
    //
    // ⚠️ The resync must be DEFERRED and RE-CHECKED, never immediate: the click that
    // reactivates the window is delivered to Compose while AWT's real windowGainedFocus is
    // still in flight. Posting the synthetic events right away wins that race, and the
    // KeyboardFocusManager then discards the REAL activation event as a duplicate — before
    // its focus-owner restore runs — leaving every subsequent key press silently dropped
    // (user-reproduced: banner clears, arrow keys dead). Waiting and re-checking makes the
    // resync a no-op whenever AWT heals itself, which is every normal physical click.
    private fun resyncWedgedWindowFocus() {
        val w = hostWindow ?: return
        if (w.isFocused || resyncJob?.isActive == true) return
        resyncJob = scope.launch {
            delay(300)
            if (!w.isFocused) {
                val queue = Toolkit.getDefaultToolkit().systemEventQueue
                queue.postEvent(WindowEvent(w, WindowEvent.WINDOW_ACTIVATED))
                queue.postEvent(WindowEvent(w, WindowEvent.WINDOW_GAINED_FOCUS))
                // The window events alone don't regenerate the component-level FOCUS_GAINED,
                // so the KeyboardFocusManager still has no focus owner. Restore it explicitly
                // once the queue has processed the window events.
                EventQueue.invokeLater {
                    (w.mostRecentFocusOwner ?: w).requestFocusInWindow()
                }
            }
        }
    }

    // A mouse press on a component hands it AWT keyboard focus — that's why clicking any
    // content revives dead arrow keys while regaining focus via the banner can leave them
    // dead: the banner's press IS the activation click, and the real windowGainedFocus that
    // follows can fail to restore the component-level focus owner, so the
    // KeyboardFocusManager silently discards every key press. Do what a content click does,
    // explicitly: once the window is really AWT-focused, restore its last focus owner (the
    // Compose canvas). No-op when focus is already healthy; touches nothing else — no
    // content state, so e.g. live slide animations are never restarted by focus recovery.
    internal fun restoreAwtFocusOwner() {
        scope.launch {
            val w = hostWindow ?: return@launch
            var waitedMs = 0
            while (!w.isFocused && waitedMs < 1500) {
                delay(50)
                waitedMs += 50
            }
            if (w.isFocused) {
                EventQueue.invokeLater {
                    // Key events reach Compose only when the AWT focus owner is the Compose
                    // CANVAS — never the frame. mostRecentFocusOwner can be null after a
                    // failed activation restore, so locate the canvas in the component tree
                    // explicitly (what a physical click on tab content does natively).
                    val target = findAwtCanvas(w) ?: w.mostRecentFocusOwner ?: w
                    if (!target.requestFocusInWindow()) target.requestFocus()
                }
            }
        }
    }
}

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
            repeat(10) {
                requester.requestFocus()
                delay(100)
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
private fun findAwtCanvas(c: Component): Component? = when (c) {
    is Canvas -> c
    is Container -> c.components.firstNotNullOfOrNull { findAwtCanvas(it) }
    else -> null
}
