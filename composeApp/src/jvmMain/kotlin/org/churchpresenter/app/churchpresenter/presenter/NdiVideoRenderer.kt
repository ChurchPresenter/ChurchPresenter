package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.ndi.NdiOutputMode
import org.churchpresenter.ndi.NdiSender
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

/**
 * Puts one output's live content on the network as an NDI source.
 *
 * The same off-screen render every other virtual output in this app uses — [ComposeScenePump]
 * driving [OffscreenOutputContent], so an NDI receiver sees pixel-for-pixel what a Browser Source
 * and a projector window see, with no second implementation to drift.
 *
 * What differs from a Browser Source is what happens to the pixels afterwards, and it is the
 * opposite trade. A Browser Source encodes only what changed, so a static slide costs one encode;
 * NDI sends every frame regardless, because a receiver expects a continuous stream and an NDI
 * source that stops sending reads as a dead one. That is why [shouldSend] gates on the output being
 * switched on rather than on anyone watching: NDI's own answer to "is anyone watching" is the
 * connection count, and a source with no receivers still has to keep announcing itself.
 *
 * In [NdiOutputMode.ALPHA] the content is drawn with transparent blanking, so the frame carries
 * genuine per-pixel alpha and OBS receives a keyed layer directly. In the fill modes it is drawn
 * with its configured background and flattened opaque, and [NdiOutputMode.FILL_AND_KEY] puts the
 * key on the network as a second source — see [NdiSender].
 */
class NdiVideoRenderer(
    private val sender: NdiSender,
    context: OffscreenOutputContext,
    private val screenAssignmentState: State<ScreenAssignment>,
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    fps: Int = DEFAULT_FPS,
) {
    private val pump = ComposeScenePump(
        width = width,
        height = height,
        fps = fps,
        shouldRender = { shouldSend(screenAssignmentState.value.ndiEnabled, sender.isOpen) },
    ) {
        OffscreenOutputContent(context, transparentBlanking = sender.mode == NdiOutputMode.ALPHA)
    }

    companion object {
        internal const val DEFAULT_WIDTH = 1920
        internal const val DEFAULT_HEIGHT = 1080
        internal const val DEFAULT_FPS = 30

        /**
         * Whether this tick is worth rendering.
         *
         * Deliberately *not* gated on the receiver count, unlike the Browser Source's equivalent: a
         * receiver tuning in expects a picture immediately, and NDI's discovery already means an
         * unwatched source costs only the frames it sends. What it is gated on is the operator's own
         * switch, and on the sender actually being on the network — rendering into a sender that
         * failed to open is pure waste.
         */
        internal fun shouldSend(enabled: Boolean, senderOpen: Boolean): Boolean = enabled && senderOpen

        /** The stored `ndiMode` string as the behaviour it names, defaulting to alpha. */
        fun modeOf(assignment: ScreenAssignment): NdiOutputMode = when (assignment.ndiMode) {
            Constants.NDI_MODE_FILL -> NdiOutputMode.FILL
            Constants.NDI_MODE_FILL_AND_KEY -> NdiOutputMode.FILL_AND_KEY
            else -> NdiOutputMode.ALPHA
        }

        /** The stored mode for a [NdiOutputMode], for writing an operator's choice back to settings. */
        fun storedModeOf(mode: NdiOutputMode): String = when (mode) {
            NdiOutputMode.FILL -> Constants.NDI_MODE_FILL
            NdiOutputMode.FILL_AND_KEY -> Constants.NDI_MODE_FILL_AND_KEY
            NdiOutputMode.ALPHA -> Constants.NDI_MODE_ALPHA
        }
    }

    /** How many receivers are watching, for the settings card to show. */
    fun connectionCount(): Int = sender.connectionCount()

    /**
     * Opens the sender and starts rendering. Does nothing if the runtime refused to create the
     * sender — there is no point rendering frames with nowhere to put them.
     */
    fun start(scope: CoroutineScope) {
        if (!sender.open()) return
        pump.start(scope) { argb, w, h, _ -> sender.send(argb, w, h) }
    }

    /**
     * Stops rendering and takes the source off the network.
     *
     * Both halves matter: leaving the sender open holds the last frame in front of every receiver,
     * so an operator who removes an output would keep seeing it.
     */
    fun stop() {
        pump.stop()
        sender.close()
    }
}
