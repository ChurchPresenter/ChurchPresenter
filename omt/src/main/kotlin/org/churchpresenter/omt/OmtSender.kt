package org.churchpresenter.omt

private const val DEFAULT_FRAME_RATE_D = 1_000
private const val FRAME_RATE_N_SCALE = 1_000

/**
 * One OMT source on the network.
 *
 * Owns the pixel conversion, the native handle and the reused byte buffer, so a caller hands it
 * packed ARGB — what every renderer in this app already has — and nothing else. Ignorant of
 * settings, Compose and what is being presented, as `NdiSender` is.
 *
 * **Not thread-safe for [send].** Each instance is driven by exactly one render pump, which is what
 * makes the reused buffer safe. [close] may come from another thread — a shutdown hook — and waits
 * for a frame already in flight rather than freeing the handle under it.
 */
class OmtSender(
    private val library: OmtLibrary,
    val name: String,
    val mode: OmtOutputMode,
    private val fps: Int,
    private val quality: OmtQuality = OmtQuality.DEFAULT,
    /** What receivers are told made this source; blank leaves the library's default. */
    private val product: String = "",
    private val version: String = "",
) {
    /** Serialises [send] against [close]; see `NdiSender.lifecycleLock` for the crash it prevents. */
    private val lifecycleLock = Any()

    @Volatile
    private var handle = 0L
    private var bytes = ByteArray(0)

    /** True between a successful [open] and a [close]. */
    val isOpen: Boolean get() = handle != 0L

    companion object {
        /**
         * The frame rate as a rational over 1000, so the common broadcast rates stay exact rather
         * than being rounded into a receiver's timing — the same scale `NdiSender` uses.
         */
        fun frameRateNumerator(fps: Int): Int = fps.coerceAtLeast(1) * FRAME_RATE_N_SCALE
    }

    /**
     * Puts the source on the network. False when the library refused, in which case nothing was
     * created and [send] does nothing.
     */
    fun open(): Boolean {
        if (handle != 0L) return true
        handle = library.sendCreate(name, quality)
        if (handle == 0L) return false
        if (product.isNotBlank()) library.sendSetSenderInformation(handle, product, product, version)
        return true
    }

    /** The name receivers see this source under, `HOSTNAME (name)`, or blank when not open. */
    fun address(): String = if (handle == 0L) "" else library.sendAddress(handle)

    /**
     * Sends one frame of packed ARGB. [argb] is only read, never kept — the conversion writes into
     * this sender's own buffer, grown once and reused for the life of the output.
     */
    fun send(argb: IntArray, width: Int, height: Int): Unit = synchronized(lifecycleLock) {
        if (handle == 0L) return@synchronized
        val needed = frameSizeBytes(width, height)
        if (needed <= 0) return@synchronized
        if (bytes.size != needed) bytes = ByteArray(needed)
        argbToBgra(argb, bytes, opaque = !mode.carriesAlpha)
        library.sendVideo(
            handle,
            OmtVideoFrame(bytes, width, height, mode.carriesAlpha, frameRateNumerator(fps), DEFAULT_FRAME_RATE_D),
        )
    }

    /**
     * How many receivers are watching. 0 when not open.
     *
     * The library counts connections, and a receiver that also takes audio holds two — so this is
     * the connection count, which is right for "is anybody watching" and an upper bound otherwise.
     */
    fun receiverCount(): Int = if (handle == 0L) 0 else library.connectionCount(handle)

    /**
     * Takes the source off the network.
     *
     * Called from a shutdown hook as well as on dispose, because a process that exits without this
     * leaves receivers holding the last frame they got.
     */
    fun close(): Unit = synchronized(lifecycleLock) {
        if (handle == 0L) return@synchronized
        library.sendDestroy(handle)
        handle = 0L
    }
}
