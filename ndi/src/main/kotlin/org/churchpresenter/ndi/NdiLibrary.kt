package org.churchpresenter.ndi

/**
 * Every native call this module makes, and the only place JNA is allowed to appear behind.
 *
 * The point of the interface is that nothing above it knows a native library exists: [NdiSender]
 * and its tests talk to this, and the suite passes a plain Kotlin fake rather than a mock. The real
 * implementation is [JnaNdiLibrary], where the single genuinely untestable line — the runtime load
 * itself — lives.
 *
 * Handles are opaque `Long`s rather than JNA `Pointer`s for the same reason: a pointer type in this
 * signature would put JNA in every caller's compile classpath and in every fake.
 */
interface NdiLibrary {
    /** The runtime's own version string, for the settings card to show. */
    fun version(): String

    /**
     * Whether this CPU meets the runtime's requirements (it needs SSE4.2). False means NDI cannot
     * run here at all, which is a different thing to say to the operator than "not installed".
     */
    fun isSupportedCpu(): Boolean

    /** Brings the runtime up. False means it declined, and nothing else here may be called. */
    fun initialize(): Boolean

    /**
     * Creates a sender the network will see as [name], in [groups] (blank for the default groups).
     *
     * Returns 0 when the runtime refused. [clockVideo] makes the runtime pace `sendVideo` to the
     * frame rate the frames declare, which is what keeps a receiver's timing sane when the pump's
     * own cadence drifts.
     */
    fun sendCreate(name: String, groups: String = "", clockVideo: Boolean = true): Long

    /** Puts one [frame] on the network. */
    fun sendVideo(sender: Long, frame: NdiVideoFrame)

    /**
     * How many receivers are currently connected, waiting up to [timeoutMs] for the answer.
     *
     * This is what the settings card shows, and it is worth showing: an NDI source that nobody has
     * subscribed to looks identical to a broken one otherwise.
     */
    fun connectionCount(sender: Long, timeoutMs: Int = 0): Int

    /** Removes the sender from the network. After this the handle is dead. */
    fun sendDestroy(sender: Long)

    /** Takes the runtime down. Nothing else here may be called afterwards. */
    fun destroy()
}

/**
 * One video frame ready for the wire.
 *
 * A type rather than seven parameters on [NdiLibrary.sendVideo] — the three dimensions, the format
 * and the two halves of the frame rate all describe the same thing, and passing them separately
 * made a call site that could silently swap the rate's numerator and denominator.
 *
 * [bgra] holds `width * height * 4` bytes in [format]'s channel order, and is the sender's reused
 * buffer: an implementation must read it before returning and must not retain it. Deliberately not
 * a `data class` for that reason — equality over a buffer that is overwritten every frame would
 * mean nothing, and the generated `copy`/`toString` would never be called.
 */
class NdiVideoFrame(
    val bgra: ByteArray,
    val width: Int,
    val height: Int,
    val format: NdiPixelFormat,
    val frameRateN: Int,
    val frameRateD: Int,
)
