package org.churchpresenter.canvas

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.canvas.DeckLinkManager.OutputInfo
import org.churchpresenter.diagnostics.Log
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** How long a closing output waits for its capture loop to finish the frame it is on. */
private const val CAPTURE_JOIN_TIMEOUT_MS = 1000L

/** The size an output is drawn at when the card cannot say its mode. */
private val FALLBACK_OUTPUT = OutputInfo(1920, 1080, 0, 0)

/**
 * DeckLink outputs, opened and closed on [worker] -- one thread, in the order they were asked for --
 * so the event thread an output composes on never waits on the card.
 *
 * One thread for every device keeps a reopen safe: an output turned off and on again, or moved to
 * another port and back, queues its open behind the close before it, so the old close can never
 * land on the new output.
 */
class DeckLinkOutputSessions internal constructor(
    private val bridge: DeckLinkBridge,
    private val worker: Executor,
    private val captureJoinTimeoutMs: Long = CAPTURE_JOIN_TIMEOUT_MS,
) {
    /** Starts opening [deviceIndex] and returns at once; [DeckLinkOutputSession.awaitOpened] says how it went. */
    fun open(deviceIndex: Int): DeckLinkOutputSession {
        val session = DeckLinkOutputSession(deviceIndex)
        worker.execute(session::openNow)
        return session
    }

    /** One opening of one device, from [open] to [close]. */
    inner class DeckLinkOutputSession internal constructor(val deviceIndex: Int) {
        private val opened = CompletableDeferred<OutputInfo?>()
        private val closeRequested = AtomicBoolean(false)

        /** Whether this session's frames reach the card; written and read under the device's lock. */
        private var live = false

        /** The output's mode once it is open, or null if it did not open or was closed first. */
        suspend fun awaitOpened(): OutputInfo? = opened.await()

        /** Sends a frame, unless this session has not opened or has started to close. */
        fun sendFrame(pixels: IntArray, width: Int, height: Int) = bridge.withOutputLock(deviceIndex) {
            if (live && bridge.isOutputActive(deviceIndex)) bridge.sendFrame(deviceIndex, pixels, width, height)
        }

        /**
         * Queues the close and returns at once. On the output thread: waits for [capture] to finish
         * the frame it is on, sends the device black, gives the card time to show it, closes it,
         * then calls [afterClose]. A second call does nothing.
         */
        fun close(capture: Job? = null, afterClose: () -> Unit = {}) {
            if (!closeRequested.compareAndSet(false, true)) return
            worker.execute { closeNow(capture, afterClose) }
        }

        internal fun openNow() {
            var info: OutputInfo? = null
            try {
                if (!closeRequested.get() && bridge.open(deviceIndex)) {
                    bridge.withOutputLock(deviceIndex) { live = true }
                    info = bridge.getOutputInfo(deviceIndex) ?: FALLBACK_OUTPUT
                }
            } finally {
                opened.complete(info)
            }
        }

        private fun closeNow(capture: Job?, afterClose: () -> Unit) {
            try {
                if (capture != null) {
                    val finished = runBlocking { withTimeoutOrNull(captureJoinTimeoutMs) { capture.join() } }
                    if (finished == null) Log.warn("DeckLink", "Device $deviceIndex: capture still running at close")
                }
                val wasLive = bridge.withOutputLock(deviceIndex) { live.also { live = false } }
                if (wasLive) bridge.blackOutAndClose(deviceIndex)
            } finally {
                afterClose()
            }
        }
    }
}
