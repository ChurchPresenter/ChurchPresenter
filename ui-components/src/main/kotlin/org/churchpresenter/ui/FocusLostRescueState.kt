package org.churchpresenter.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import java.awt.event.WindowEvent

private const val FOCUS_SETTLE_MS = 300L
private const val FOCUS_WAIT_TIMEOUT_MS = 1500
private const val FOCUS_POLL_INTERVAL_MS = 50

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
class FocusLostRescueState constructor(
    private val hostWindow: AwtWindow?,
    private val focusRequester: FocusRequester,
    private val scope: CoroutineScope,
) {
    var tabHasFocus by mutableStateOf(false)
    var windowFocused by mutableStateOf(true)
    var active by mutableStateOf(true)
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
            delay(FOCUS_SETTLE_MS)
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
    fun restoreAwtFocusOwner() {
        scope.launch {
            val w = hostWindow ?: return@launch
            var waitedMs = 0
            while (!w.isFocused && waitedMs < FOCUS_WAIT_TIMEOUT_MS) {
                delay(FOCUS_POLL_INTERVAL_MS.toLong())
                waitedMs += FOCUS_POLL_INTERVAL_MS
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
