package org.churchpresenter.ndi

/**
 * Every native call this module makes, and the only place JNA is allowed to appear behind.
 *
 * The point of the interface is that nothing above it knows a native library exists: [NdiSender],
 * [NdiReceiver] and their tests talk to this, and the suite passes a plain Kotlin fake rather than a mock. The real
 * implementation is [JnaNdiLibrary], where the single genuinely untestable line — the runtime load
 * itself — lives.
 *
 * Handles are opaque `Long`s rather than JNA `Pointer`s for the same reason: a pointer type in this
 * signature would put JNA in every caller's compile classpath and in every fake.
 */
@Suppress("TooManyFunctions")  // One function per native call: the count is the C API's, not a design choice.
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

    /**
     * Starts discovery, returning a finder handle, or 0 when the runtime refused.
     *
     * [showLocalSources] includes this machine's own senders, which is what makes a canvas layer
     * showing another of the app's own outputs possible. [groups] is blank for the default groups.
     */
    fun findCreate(showLocalSources: Boolean = true, groups: String = ""): Long

    /**
     * What [finder] knows about right now, after waiting up to [timeoutMs] for the list to change.
     *
     * Discovery is cumulative: the answer grows over the first seconds of a finder's life, so an
     * empty list from a young finder means "not yet", never "nobody is sending".
     */
    fun findSources(finder: Long, timeoutMs: Int = 0): List<NdiSourceInfo>

    /** Stops discovery. After this the handle is dead. */
    fun findDestroy(finder: Long)

    /**
     * Connects a receiver to [source], returning its handle, or 0 when the runtime refused.
     *
     * [receiverName] is what the *sender* sees in its own connection list — blank lets the runtime
     * name it — and [bandwidth] chooses between the full stream and the sender's free proxy.
     */
    fun recvCreate(
        source: NdiSourceInfo,
        bandwidth: NdiBandwidth = NdiBandwidth.HIGHEST,
        receiverName: String = "",
    ): Long

    /**
     * The next video frame on [receiver], or null when none arrived within [timeoutMs].
     *
     * The returned frame's [NdiVideoFrame.bgra] belongs to the library and is **valid only until
     * the next call for that receiver** — the same contract [sendVideo] states from the other
     * direction, and for the same reason: one buffer per handle, reused rather than reallocated at
     * frame rate. Audio and metadata frames are consumed and dropped, so nothing queues up behind a
     * caller that only wants pictures.
     */
    fun recvCaptureVideo(receiver: Long, timeoutMs: Int): NdiVideoFrame?

    /** Disconnects the receiver. After this the handle is dead. */
    fun recvDestroy(receiver: Long)

    /** Takes the runtime down. Nothing else here may be called afterwards. */
    fun destroy()
}

/**
 * One video frame: on its way to the wire, or just off it.
 *
 * The same type both directions, because it is the same seven facts either way — and having one
 * meant the receive path inherited the buffer-ownership rule below instead of inventing a second.
 *
 * A type rather than seven parameters on [NdiLibrary.sendVideo] — the three dimensions, the format
 * and the two halves of the frame rate all describe the same thing, and passing them separately
 * made a call site that could silently swap the rate's numerator and denominator.
 *
 * [bgra] holds at least `width * height * 4` bytes in [format]'s channel order and is a reused
 * buffer owned by whoever produced the frame: read it before returning, never retain it. Going out
 * that is the sender's buffer and the implementation is the borrower; coming back it is the
 * library's, and the caller is. Deliberately not
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
