package org.churchpresenter.ndi

/** What a receiver asks the sender to send it. */
enum class NdiBandwidth {
    /** Full resolution, full frame rate — what a source going to the screen wants. */
    HIGHEST,

    /**
     * A low-resolution proxy the sender generates for free.
     *
     * Worth offering rather than hiding: a canvas layer that ends up as a corner inset does not
     * need 1080p, and on a congested LAN the difference between a proxy and a full stream is the
     * difference between a service that runs and one that stutters.
     */
    LOWEST,
    ;

    companion object {
        /** The [NdiBandwidth] a "low bandwidth" toggle in the UI maps onto. */
        fun of(low: Boolean): NdiBandwidth = if (low) LOWEST else HIGHEST
    }
}

/**
 * One frame that came off the network, as packed ARGB.
 *
 * [pixels] is the receiver's own reused buffer and is **only valid until the next [NdiReceiver
 * .receive] call on that receiver** — read it, or copy it, before asking for another frame. It is
 * reused for the same reason the sender's is: at 1080p a fresh array per frame is 8.3 MB of garbage
 * thirty times a second.
 *
 * [pixels] may be longer than `width * height` when a smaller frame follows a larger one; read
 * exactly `width * height` from it and ignore the tail. Deliberately not a `data class` — equality
 * over a buffer that is overwritten every frame would mean nothing.
 */
class NdiFrame(val pixels: IntArray, val width: Int, val height: Int)

/**
 * One NDI source being received: the connection, the pixel conversion and the reused buffer.
 *
 * The mirror of [NdiSender], and deliberately the same shape — the caller hands it nothing and gets
 * packed ARGB back, which is what every renderer in this app already draws. It knows nothing about
 * settings, Compose or what the frame is for.
 *
 * **Not thread-safe.** Each instance is driven by exactly one capture loop; that is what makes the
 * reused buffer safe, exactly as one pump per sender does on the way out.
 *
 * Audio and metadata are asked for and discarded by the runtime: this is a video path, and a
 * receiver that accepted audio it never drained would grow the SDK's own queue for the life of the
 * service.
 */
class NdiReceiver(
    private val library: NdiLibrary,
    val source: NdiSourceInfo,
    private val bandwidth: NdiBandwidth = NdiBandwidth.HIGHEST,
    private val receiverName: String = "",
) {
    private var handle = 0L
    private var pixels = IntArray(0)

    /** True between a successful [open] and a [close]. */
    val isOpen: Boolean get() = handle != 0L

    /** Connects to [source]. False means the runtime refused; [receive] then always returns null. */
    fun open(): Boolean {
        if (handle != 0L) return true
        if (!source.isValid) return false
        handle = library.recvCreate(source, bandwidth, receiverName)
        return handle != 0L
    }

    /**
     * The next video frame, or null when none arrived within [timeoutMs].
     *
     * Null is the ordinary answer, not a fault: a source that is connecting, paused, or simply
     * slower than the caller's poll returns nothing and the caller keeps showing what it has.
     */
    fun receive(timeoutMs: Int = DEFAULT_TIMEOUT_MS): NdiFrame? {
        if (handle == 0L) return null
        val frame = library.recvCaptureVideo(handle, timeoutMs) ?: return null
        val count = frame.width * frame.height
        if (count <= 0) return null
        if (pixels.size < count) pixels = IntArray(count)
        ndiBytesToArgb(frame.bgra, pixels, count, opaque = frame.format != NdiPixelFormat.BGRA)
        return NdiFrame(pixels, frame.width, frame.height)
    }

    /** Disconnects. The sender stops paying for this receiver as soon as it does. */
    fun close() {
        if (handle == 0L) return
        library.recvDestroy(handle)
        handle = 0L
    }

    companion object {
        /**
         * How long [receive] waits for a frame by default.
         *
         * Long enough that a 30fps source answers on the first ask rather than being polled in a
         * spin, short enough that a capture loop notices its own cancellation promptly.
         */
        const val DEFAULT_TIMEOUT_MS = 100
    }
}
