package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.sharedui.models.Presenting

/**
 * The overlays up over the slide -- captions, lower thirds and announcements ([OVERLAY_MODES]) --
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

    /** Takes the overlay [mode] down, leaving the slide and any other overlay up. */
    fun clearOverlay(mode: Presenting)

    /**
     * An overlay that ended on its own -- a lower third's run, an announcement's last loop. With
     * [clearsDisplay] (the default setting, as it always was) the whole display clears; otherwise
     * only the overlay comes down and the slide under it stays.
     */
    fun overlayFinished(mode: Presenting, clearsDisplay: Boolean)
}

internal class LiveOverlaysState(private val context: PresenterContext) : LiveOverlays {
    override val overlays: State<Set<Presenting>> = context.overlays
    override val lastLive: State<Presenting> = context.lastLive

    /** Puts [mode] up over the slide, leaving the slide as it is. */
    internal fun showOverlay(mode: Presenting) {
        if (mode !in context.overlays.value) {
            CrashReporter.breadcrumb("Overlay: ${mode.name}", category = "presenter")
        }
        // Re-added at the end, so the set's order stays the order they went up in.
        context.overlays.value = context.overlays.value - mode + mode
        context.lastLive.value = mode
        context.clearDisplayRequested.value = false
        context.notify(mode)
    }

    override fun clearOverlay(mode: Presenting) {
        if (mode !in context.overlays.value) return
        context.overlays.value = context.overlays.value - mode
        // The most recent of what is still up: the newest overlay left, else the slide.
        if (context.lastLive.value == mode) {
            context.lastLive.value = context.overlays.value.lastOrNull() ?: context.slideMode.value
        }
        context.notify(context.slideMode.value)
    }

    override fun overlayFinished(mode: Presenting, clearsDisplay: Boolean) {
        if (mode !in context.overlays.value) return
        if (clearsDisplay) context.requestClearDisplay() else clearOverlay(mode)
    }
}
