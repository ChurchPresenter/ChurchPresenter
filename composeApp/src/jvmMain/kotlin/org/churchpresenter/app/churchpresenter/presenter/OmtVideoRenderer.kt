package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.omt.OmtOutputMode
import org.churchpresenter.omt.OmtQuality
import org.churchpresenter.omt.OmtSender
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Puts one output's live content on the network as an OMT source.
 *
 * [NdiVideoRenderer]'s twin, over the same off-screen render — [ComposeScenePump] driving
 * [OffscreenOutputContent] — so an OMT receiver sees pixel for pixel what a projector, a Browser
 * Source and an NDI receiver see. It parks while nobody is connected for the reason that class
 * gives: announcing a source is the library's discovery threads' job, not the video stream's.
 *
 * In [OmtOutputMode.ALPHA] the content is drawn with transparent blanking and each frame is flagged
 * as carrying alpha, so OBS receives a keyed layer directly. In [OmtOutputMode.FILL] it is drawn
 * with its configured background and flattened opaque.
 */
class OmtVideoRenderer(
    private val sender: OmtSender,
    context: OffscreenOutputContext,
    private val screenAssignmentState: State<ScreenAssignment>,
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    fps: Int = DEFAULT_FPS,
    /**
     * Called the first time a receiver is found watching this output. A defaulted constructor
     * parameter so a test passes its own lambda and never reaches the process-wide store.
     */
    private val onReceiverSeen: () -> Unit = { UsageEvents.recordOncePerRun(UsageEvent.OMT_OUTPUT) },
) {
    private val pump = ComposeScenePump(
        width = width,
        height = height,
        fps = fps,
        shouldRender = { shouldRenderTick() },
    ) {
        OffscreenOutputContent(context, transparentBlanking = sender.mode == OmtOutputMode.ALPHA)
    }

    companion object {
        internal const val DEFAULT_WIDTH = 1920
        internal const val DEFAULT_HEIGHT = 1080
        internal const val DEFAULT_FPS = 30

        /** How long a receiver count is trusted before the library is asked again — see NDI's. */
        internal const val RECEIVER_POLL_MS = NdiVideoRenderer.RECEIVER_POLL_MS

        /** Whether this tick is worth rendering: switched on, open, and someone to send to. */
        internal fun shouldSend(enabled: Boolean, senderOpen: Boolean, receivers: Int): Boolean =
            enabled && senderOpen && receivers > 0

        /** The stored `omtMode` string as the behaviour it names, defaulting to alpha. */
        fun modeOf(assignment: ScreenAssignment): OmtOutputMode = when (assignment.omtMode) {
            Constants.OMT_MODE_FILL -> OmtOutputMode.FILL
            else -> OmtOutputMode.ALPHA
        }

        /** The stored mode for an [OmtOutputMode], for writing an operator's choice back to settings. */
        fun storedModeOf(mode: OmtOutputMode): String = when (mode) {
            OmtOutputMode.FILL -> Constants.OMT_MODE_FILL
            OmtOutputMode.ALPHA -> Constants.OMT_MODE_ALPHA
        }

        /** The stored `omtQuality` string as the quality it names, defaulting to [OmtQuality.DEFAULT]. */
        fun qualityOf(assignment: ScreenAssignment): OmtQuality = when (assignment.omtQuality) {
            Constants.OMT_QUALITY_LOW -> OmtQuality.LOW
            Constants.OMT_QUALITY_MEDIUM -> OmtQuality.MEDIUM
            Constants.OMT_QUALITY_HIGH -> OmtQuality.HIGH
            else -> OmtQuality.DEFAULT
        }

        /** The stored quality for an [OmtQuality]. */
        fun storedQualityOf(quality: OmtQuality): String = when (quality) {
            OmtQuality.LOW -> Constants.OMT_QUALITY_LOW
            OmtQuality.MEDIUM -> Constants.OMT_QUALITY_MEDIUM
            OmtQuality.HIGH -> Constants.OMT_QUALITY_HIGH
            OmtQuality.DEFAULT -> Constants.OMT_QUALITY_DEFAULT
        }
    }

    /** How many receivers are watching, for the settings card to show. */
    fun receiverCount(): Int = sender.receiverCount()

    /** The name receivers find this output under, `HOSTNAME (name)`, for the settings card to show. */
    fun address(): String = sender.address()

    private var polled = false
    private var lastPollMs = 0L
    private var receivers = 0
    private var receiversSeen = false

    /**
     * The receiver count, asked of the library at most once per [RECEIVER_POLL_MS], with the first
     * receiver reported as usage on the way past — the same single asker [NdiVideoRenderer] has, for
     * the same reason. Takes [nowMs] so a test drives the schedule without a clock.
     */
    internal fun refreshReceivers(nowMs: Long): Int {
        if (polled && nowMs - lastPollMs < RECEIVER_POLL_MS) return receivers
        polled = true
        lastPollMs = nowMs
        receivers = sender.receiverCount()
        if (receivers > 0 && !receiversSeen) {
            receiversSeen = true
            onReceiverSeen()
        }
        return receivers
    }

    private fun shouldRenderTick(): Boolean {
        val enabled = screenAssignmentState.value.omtEnabled
        val open = sender.isOpen
        val watching = if (enabled && open) refreshReceivers(System.nanoTime() / NANOS_PER_MILLI) else 0
        return shouldSend(enabled, open, watching)
    }

    /** Opens the sender and starts rendering; does nothing if the library refused the sender. */
    fun start(scope: CoroutineScope) {
        if (!sender.open()) return
        pump.start(scope) { argb, w, h, _ -> sender.send(argb, w, h) }
    }

    /** Stops rendering and takes the source off the network, so no receiver is left on a frozen frame. */
    fun stop() {
        pump.stop()
        sender.close()
    }
}
