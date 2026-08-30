package org.churchpresenter.ndi

private const val DEFAULT_FRAME_RATE_D = 1_000
private const val FRAME_RATE_N_SCALE = 1_000

/**
 * One NDI source on the network: the fill, and the key beside it when the mode asks for one.
 *
 * Owns the pixel conversion, both native handles and the reused byte buffers, so a caller hands it
 * packed ARGB — what every renderer in this app already has — and nothing else. It is deliberately
 * ignorant of settings, of Compose and of what is being presented: it is given a frame and a name.
 *
 * **Not thread-safe.** Each instance is driven by exactly one render pump, which is what makes the
 * reused buffers safe. Two threads sending through one sender would tear a frame.
 *
 * [keyName] follows the convention hardware and software receivers already expect — the fill's name
 * with a " Key" suffix — so an operator picking the pair in OBS sees them adjacent and obviously
 * related, rather than as two unrelated sources.
 */
class NdiSender(
    private val library: NdiLibrary,
    val name: String,
    val mode: NdiOutputMode,
    private val fps: Int,
) {
    /** The name the key sender appears under, for [NdiOutputMode.FILL_AND_KEY]. */
    val keyName: String get() = keyNameFor(name)

    private var fillHandle = 0L
    private var keyHandle = 0L
    private var fillBytes = ByteArray(0)
    private var keyBytes = ByteArray(0)

    /**
     * The key signal's pixels, reused rather than reallocated. A fresh IntArray per frame would be
     * 8.3 MB of garbage 30 times a second at 1080p — the same allocation profile the Browser Source
     * renderer was explicitly fixed to stop paying.
     */
    private var keyPixels = IntArray(0)

    /** True between a successful [open] and a [close]. */
    val isOpen: Boolean get() = fillHandle != 0L

    companion object {
        /** The " Key" suffix convention, in one place because the UI shows this name too. */
        fun keyNameFor(name: String): String = "$name Key"

        /**
         * NDI states a frame rate as a rational. Scaling by 1000 keeps the common broadcast rates
         * exact — 30000/1000 for 30, and 30000/1001 would be 29.97 — rather than rounding them into
         * a receiver's timing.
         */
        fun frameRateNumerator(fps: Int): Int = fps.coerceAtLeast(1) * FRAME_RATE_N_SCALE

        /**
         * Turns fill pixels into a luminance key in place: white where the fill is opaque, black
         * where it is not, greys in between.
         *
         * Keys on **alpha**, not on brightness. That is the difference between this and the
         * DeckLink path's `convertToKeySignal`, which takes `max(r, g, b)` because an SDI fill
         * signal has no alpha channel left to read by the time it gets there. Here the alpha is
         * still present and is the actual answer, so black text on a transparent background keys
         * correctly instead of vanishing — which is exactly the lower-third case this feature is
         * for.
         */
        fun argbToLuminanceKey(argb: IntArray, out: IntArray) {
            for (i in argb.indices) {
                val alpha = (argb[i] ushr ALPHA_SHIFT) and BYTE_MASK
                out[i] = (BYTE_MASK shl ALPHA_SHIFT) or
                    (alpha shl RED_SHIFT) or (alpha shl GREEN_SHIFT) or alpha
            }
        }
    }

    /**
     * Puts the sender(s) on the network. Returns false when the runtime refused, in which case
     * nothing was created and [send] does nothing.
     */
    fun open(): Boolean {
        if (fillHandle != 0L) return true
        fillHandle = library.sendCreate(name)
        if (fillHandle == 0L) return false
        if (mode.hasKeySender) {
            keyHandle = library.sendCreate(keyName)
        }
        return true
    }

    /**
     * Sends one frame of packed ARGB.
     *
     * [argb] is the caller's buffer and is only read, never kept — the conversion writes into this
     * sender's own, which is grown once and then reused for the life of the output.
     */
    fun send(argb: IntArray, width: Int, height: Int) {
        if (fillHandle == 0L) return
        val needed = frameSizeBytes(width, height)
        if (fillBytes.size != needed) fillBytes = ByteArray(needed)
        argbToNdiBytes(argb, fillBytes, opaque = mode != NdiOutputMode.ALPHA)
        library.sendVideo(
            fillHandle,
            NdiVideoFrame(fillBytes, width, height, mode.pixelFormat, frameRateNumerator(fps), DEFAULT_FRAME_RATE_D),
        )
        if (keyHandle == 0L) return
        if (keyPixels.size != argb.size) keyPixels = IntArray(argb.size)
        argbToLuminanceKey(argb, keyPixels)
        if (keyBytes.size != needed) keyBytes = ByteArray(needed)
        argbToNdiBytes(keyPixels, keyBytes, opaque = true)
        library.sendVideo(
            keyHandle,
            NdiVideoFrame(keyBytes, width, height, NdiPixelFormat.BGRX, frameRateNumerator(fps), DEFAULT_FRAME_RATE_D),
        )
    }

    /** How many receivers are watching the fill. 0 when the sender is not open. */
    fun connectionCount(): Int = if (fillHandle == 0L) 0 else library.connectionCount(fillHandle)

    /**
     * Takes the sender(s) off the network.
     *
     * Called from a shutdown hook as well as on dispose, because a process that exits without this
     * leaves its source advertised — a receiver holds the last frame it got and shows a frozen
     * lower third rather than nothing.
     */
    fun close() {
        if (keyHandle != 0L) {
            library.sendDestroy(keyHandle)
            keyHandle = 0L
        }
        if (fillHandle != 0L) {
            library.sendDestroy(fillHandle)
            fillHandle = 0L
        }
    }
}
