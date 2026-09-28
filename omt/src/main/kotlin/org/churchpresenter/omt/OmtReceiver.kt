package org.churchpresenter.omt

/**
 * One frame that came off the network, as packed ARGB.
 *
 * [pixels] is the receiver's own reused buffer and is **only valid until the next
 * [OmtReceiver.receive] call** — read it, or copy it, before asking for another frame. It may be
 * longer than `width * height` when a smaller frame follows a larger one; read exactly that many.
 */
class OmtFrame(val pixels: IntArray, val width: Int, val height: Int)

/**
 * One OMT source being received: the connection, the pixel conversion and the reused buffer.
 *
 * The mirror of [OmtSender]: the caller hands it an address and gets packed ARGB back. Video only —
 * the receiver asks the library for video frames alone, so no audio queues up behind a caller that
 * would never drain it.
 *
 * **Not thread-safe.** Each instance is driven by exactly one capture loop.
 *
 * [address] is either the `HOSTNAME (name)` string discovery reports or an `omt://host:port` URL,
 * which is how a source on another subnet is reached without a discovery server.
 */
class OmtReceiver(
    private val library: OmtLibrary,
    val address: String,
    preview: Boolean = false,
) {
    private var handle = 0L
    private var pixels = IntArray(0)

    /** Whether the sender's 1/8-size preview stream is being asked for rather than the full one. */
    var preview: Boolean = preview
        private set

    /** True between a successful [open] and a [close]. */
    val isOpen: Boolean get() = handle != 0L

    /** Connects to [address]. False means the library refused; [receive] then always returns null. */
    fun open(): Boolean {
        if (handle != 0L) return true
        if (address.isBlank()) return false
        handle = library.recvCreate(address, preview)
        return handle != 0L
    }

    /**
     * The next video frame, or null when none arrived within [timeoutMs].
     *
     * Null is the ordinary answer, not a fault: a source that is connecting, paused, or simply slower
     * than the caller's poll returns nothing and the caller keeps showing what it has.
     */
    fun receive(timeoutMs: Int = DEFAULT_TIMEOUT_MS): OmtFrame? {
        if (handle == 0L) return null
        val frame = library.recvCaptureVideo(handle, timeoutMs) ?: return null
        val count = frame.width * frame.height
        if (count <= 0) return null
        if (pixels.size < count) pixels = IntArray(count)
        bgraToArgb(frame.bgra, pixels, count, opaque = !frame.alpha)
        return OmtFrame(pixels, frame.width, frame.height)
    }

    /** Switches between the preview stream and the full one without reconnecting. */
    fun setPreview(value: Boolean) {
        if (value == preview) return
        preview = value
        if (handle != 0L) library.recvSetPreview(handle, value)
    }

    /** Disconnects. The sender stops paying for this receiver as soon as it does. */
    fun close() {
        if (handle == 0L) return
        library.recvDestroy(handle)
        handle = 0L
    }

    companion object {
        /**
         * How long [receive] waits for a frame by default: long enough that a 30fps source answers on
         * the first ask, short enough that a capture loop notices its own cancellation promptly.
         */
        const val DEFAULT_TIMEOUT_MS = 100
    }
}
