package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.ndi.NdiOutputMode
import org.churchpresenter.ndi.NdiSender
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Puts one output's live content on the network as an NDI source.
 *
 * The same off-screen render every other virtual output in this app uses — [ComposeScenePump]
 * driving [OffscreenOutputContent], so an NDI receiver sees pixel-for-pixel what a Browser Source
 * and a projector window see, with no second implementation to drift.
 *
 * What differs from a Browser Source is what happens to the pixels afterwards. A Browser Source
 * encodes only what changed, so a static slide costs one encode; NDI sends every frame regardless,
 * because a receiver expects a continuous stream. So while there is a receiver this output has a
 * floor its neighbour does not — and while there is not, [shouldSend] parks it entirely, exactly as
 * the Browser Source parks on its subscriber count.
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
    /**
     * Called the first time a receiver is found watching this output, so the usage ping can count
     * the services where NDI was genuinely consumed rather than merely switched on.
     *
     * A defaulted constructor parameter so a test passes its own lambda and never reaches the
     * process-wide store or `user.home`.
     */
    private val onReceiverSeen: () -> Unit = { UsageEvents.recordOncePerRun(UsageEvent.NDI_OUTPUT) },
) {
    private val pump = ComposeScenePump(
        width = width,
        height = height,
        fps = fps,
        shouldRender = { shouldRenderTick() },
    ) {
        OffscreenOutputContent(context, transparentBlanking = sender.mode == NdiOutputMode.ALPHA)
    }

    companion object {
        internal const val DEFAULT_WIDTH = 1920
        internal const val DEFAULT_HEIGHT = 1080
        internal const val DEFAULT_FPS = 30

        /**
         * How long a receiver count is trusted before the runtime is asked again.
         *
         * Deliberately just under [ComposeScenePump.IDLE_POLL_MS], so a parked output asks on every
         * one of its idle ticks rather than skipping one to timing jitter and taking two of them to
         * notice a receiver. While rendering it caps the ask at five a second instead of one per
         * frame.
         */
        internal const val RECEIVER_POLL_MS = 200L

        /**
         * Whether this tick is worth rendering.
         *
         * Gated on the receiver count, which an earlier version of this class deliberately did not
         * do — the reasoning being that a source still has to announce itself. It does, but
         * announcing is the discovery threads' job and has nothing to do with the video stream: a
         * source with no frames flowing is still listed in OBS, and [NdiSender.connectionCount]
         * still answers, because a receiver arrives on the sender's own accept thread. Rendering
         * for nobody is not what keeps a source alive; it is just 1080p30 thrown away, which is
         * what a reported machine was doing for an entire service.
         *
         * The cost of gating is that a receiver tuning in waits for the pump's next idle tick — at
         * most [ComposeScenePump.IDLE_POLL_MS] — for its first picture.
         */
        internal fun shouldSend(enabled: Boolean, senderOpen: Boolean, receivers: Int): Boolean =
            enabled && senderOpen && receivers > 0

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

    private var polled = false
    private var lastPollMs = 0L
    private var receivers = 0
    private var receiversSeen = false

    /**
     * The receiver count, asked of the runtime at most once per [RECEIVER_POLL_MS] and remembered
     * in between, and the first receiver reported as usage on the way past.
     *
     * One place asks, because both things that want the answer — whether to render at all, and
     * whether NDI was genuinely consumed this service — want the same answer at the same moment.
     * Splitting them meant two native calls on two different schedules and two ideas of "seen".
     *
     * Takes [nowMs] rather than reading a clock, so a test drives the whole schedule directly
     * against a fake library: no Compose, no wait, and no dependence on how long anything took.
     * Called only from the pump's own coroutine, which is what makes the unguarded state safe —
     * the same single-driver contract [NdiSender] is written to.
     */
    internal fun refreshReceivers(nowMs: Long): Int {
        if (polled && nowMs - lastPollMs < RECEIVER_POLL_MS) return receivers
        polled = true
        lastPollMs = nowMs
        receivers = sender.connectionCount()
        if (receivers > 0 && !receiversSeen) {
            receiversSeen = true
            onReceiverSeen()
        }
        return receivers
    }

    /**
     * Whether the pump should render this tick — the switch, the sender, and someone to send to.
     *
     * The runtime is only asked about receivers once the first two hold: an output the operator has
     * switched off, or whose sender never opened, is not going to render whatever the answer is.
     */
    private fun shouldRenderTick(): Boolean {
        val enabled = screenAssignmentState.value.ndiEnabled
        val open = sender.isOpen
        val watching = if (enabled && open) refreshReceivers(System.nanoTime() / NANOS_PER_MILLI) else 0
        return shouldSend(enabled, open, watching)
    }

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
