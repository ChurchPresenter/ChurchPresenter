package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.sharedui.models.Presenting

/**
 * The overlays up over the slide -- lower thirds and captions ([OVERLAY_MODES]) --
 * and what was put live most recently. Part of [PresenterManager].
 */
interface LiveOverlays {
    /** The overlay content up over the slide. Going live with slide content, or clearing, takes it down. */
    val overlays: State<Set<Presenting>>

    /**
     * Whatever was put live most recently, slide or overlay, or [Presenting.NONE] once everything is
     * cleared -- what OBS scene switching follows.
     */
    val lastLive: State<Presenting>

    /** Whether anything at all is on screen. */
    val anythingLive: Boolean

    /** Whether [mode] is on screen, on the slide layers or as an overlay. */
    fun isLive(mode: Presenting): Boolean

    /**
     * The live mode as a tab for the overlay [overlay] sees it: [overlay] while it is up, the slide's
     * mode otherwise -- for the tabs that only ever ask "is my content live".
     */
    fun modeAsSeenBy(overlay: Presenting): State<Presenting>

    /** Takes the overlay [mode] down, leaving the slide and any other overlay up. */
    fun clearOverlay(mode: Presenting)

    /**
     * An overlay that ended on its own -- a lower third's run or its animation finishing. With
     * [clearsDisplay] (the default setting, as it always was) the whole display clears; otherwise
     * only the overlay comes down and the slide under it stays.
     */
    fun overlayFinished(mode: Presenting, clearsDisplay: Boolean)
}

internal class LiveOverlaysState(private val context: PresenterContext) : LiveOverlays {
    override val overlays: State<Set<Presenting>> = context.overlays
    override val lastLive: State<Presenting> = context.lastLive

    override val anythingLive: Boolean
        get() = context.presentingMode.value != Presenting.NONE || context.overlays.value.isNotEmpty()

    override fun isLive(mode: Presenting): Boolean =
        mode != Presenting.NONE && (mode == context.presentingMode.value || mode in context.overlays.value)

    override fun modeAsSeenBy(overlay: Presenting): State<Presenting> =
        derivedStateOf { if (overlay in context.overlays.value) overlay else context.presentingMode.value }

    /** Puts [mode] up over the slide, leaving the slide as it is. */
    internal fun showOverlay(mode: Presenting) {
        if (mode !in context.overlays.value) {
            CrashReporter.breadcrumb("Overlay: ${mode.name}", category = "presenter")
        }
        context.overlays.value = context.overlays.value + mode
        context.lastLive.value = mode
        context.clearDisplayRequested.value = false
        context.notify(mode)
    }

    override fun clearOverlay(mode: Presenting) {
        if (mode !in context.overlays.value) return
        context.overlays.value = context.overlays.value - mode
        // The most recent of what is still up: the newest overlay left, else the slide.
        if (context.lastLive.value == mode) {
            context.lastLive.value = context.overlays.value.lastOrNull() ?: context.presentingMode.value
        }
        context.notify(context.presentingMode.value)
    }

    override fun overlayFinished(mode: Presenting, clearsDisplay: Boolean) {
        if (!isLive(mode)) return
        if (clearsDisplay) context.requestClearDisplay() else clearOverlay(mode)
    }
}
