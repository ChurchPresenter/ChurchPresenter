package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.churchpresenter.sharedui.models.Presenting

/**
 * What [PresenterManager]'s parts share: the live mode, the clear-display request, the per-screen
 * locks, the scope their background work runs on, and the two calls back into the manager.
 *
 * The parts are separate classes so that no one of them is the whole live state -- see
 * [PresenterManager] -- but a countdown still has to know whether an announcement is on screen, and
 * an animated slide whether it is visible, so these few things are held once, here, and handed to
 * each part that needs them.
 */
internal class PresenterContext {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** What the slide layers show -- see [PresenterManager.presentingMode]. */
    val presentingMode = mutableStateOf(Presenting.NONE)

    /** The overlay content up over the slide -- see [PresenterManager.overlays]. */
    val overlays = mutableStateOf<Set<Presenting>>(emptySet())

    /** Whatever was put live most recently, slide or overlay -- see [PresenterManager.lastLive]. */
    val lastLive = mutableStateOf(Presenting.NONE)
    val clearDisplayRequested = mutableStateOf(false)

    /** Per-screen lock: screen slot index -> locked mode; a missing entry follows [presentingMode]. */
    val screenLocks = mutableStateOf<Map<Int, Presenting>>(emptyMap())

    /** Reports a live-content change of [Presenting] type; set by the manager to its own broadcast. */
    var notify: (Presenting) -> Unit = {}

    /** Switches the live mode the way the manager does, for a countdown that runs out on screen. */
    var setPresentingMode: (Presenting) -> Unit = {}

    /** Clears the display the way the manager does, for an overlay that ends on its own. */
    var requestClearDisplay: () -> Unit = {}
}
