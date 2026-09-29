package org.churchpresenter.omt

/**
 * Every native call this module makes, and the only place JNA is allowed to appear behind.
 *
 * The same seam `:ndi` has, for the same reason: [OmtSender], [OmtReceiver], [OmtDiscovery] and
 * their tests talk to this, and the suite passes a plain Kotlin fake rather than a mock. The real
 * implementation is [JnaOmtLibrary], where the single genuinely untestable line — the load itself —
 * lives.
 *
 * Handles are opaque `Long`s rather than JNA `Pointer`s so a pointer type never reaches a caller's
 * compile classpath or a fake.
 *
 * There is no `initialize` here, and that is the library's shape rather than a gap: `libomt` has no
 * runtime to bring up. Its discovery and logging threads start on first use and stop at [shutdown].
 */
@Suppress("TooManyFunctions")  // One function per native call: the count is the C API's, not a design choice.
interface OmtLibrary {
    /**
     * Where the library writes its own log, or null to write none.
     *
     * Left alone, `libomt` writes one to `~/.OMT/logs` for every process that loads it — a folder the
     * operator never asked for. The app points it into its own log directory instead. Must be the
     * first call made, because the library opens its log on first use.
     */
    fun setLoggingFilename(path: String?)

    /**
     * A discovery server to use instead of DNS-SD, as `omt://host:port`.
     *
     * The escape hatch for a network that blocks multicast, which is common on church networks run
     * by an IT department: sources are then found by asking the server rather than by listening.
     * Read by the library once, when its discovery starts — set it before anything discovers.
     */
    fun setDiscoveryServer(url: String)

    /**
     * Creates a sender the network will see as `HOSTNAME (name)`, encoding at [quality].
     *
     * Returns 0 when the library refused — a port range with nothing free, most often.
     */
    fun sendCreate(name: String, quality: OmtQuality): Long

    /** Tells receivers what made this source — shown in their source information. */
    fun sendSetSenderInformation(sender: Long, product: String, manufacturer: String, version: String)

    /** The name discovery advertises this sender under, `HOSTNAME (name)`. Blank if unknown. */
    fun sendAddress(sender: Long): String

    /** Puts one [frame] on the network. */
    fun sendVideo(sender: Long, frame: OmtVideoFrame)

    /**
     * How many connections the sender has.
     *
     * **Connections, not receivers.** A receiver takes one connection for video and metadata and a
     * second when it also wants audio — see [OmtSender.receiverCount], which is the number to show.
     */
    fun connectionCount(sender: Long): Int

    /** Removes the sender from the network. After this the handle is dead. */
    fun sendDestroy(sender: Long)

    /**
     * Every source discovery knows about right now, as the `HOSTNAME (name)` strings a receiver
     * connects by.
     *
     * Process-wide — `libomt` has no finder handle — and the array behind it is only valid until the
     * next call, so two callers at once would read each other's. [OmtDiscovery] is the one caller.
     */
    fun discoveryAddresses(): List<String>

    /**
     * Connects a receiver to [address] — a discovered name or an `omt://host:port` URL — asking for
     * video only, delivered as BGRA. [preview] asks the sender for its 1/8-size preview stream.
     * Returns 0 when the library refused.
     */
    fun recvCreate(address: String, preview: Boolean): Long

    /**
     * The next video frame on [receiver], or null when none arrived within [timeoutMs].
     *
     * The returned frame's [OmtVideoFrame.bgra] belongs to the library and is **valid only until the
     * next call for that receiver** — one buffer per handle, reused rather than reallocated at frame
     * rate. Nothing needs freeing: the library owns its frame and reuses it on the next call.
     */
    fun recvCaptureVideo(receiver: Long, timeoutMs: Int): OmtVideoFrame?

    /** Asks the sender for the preview stream or the full one, from the next frame on. */
    fun recvSetPreview(receiver: Long, preview: Boolean)

    /** Disconnects the receiver. After this the handle is dead. */
    fun recvDestroy(receiver: Long)

    /**
     * Stops the library's discovery and logging threads. Once per process, after every sender and
     * receiver is destroyed, and nothing here may be called afterwards.
     */
    fun shutdown()
}

/**
 * One video frame: on its way to the wire, or just off it.
 *
 * [bgra] holds at least `width * height * 4` bytes, packed rows, in BGRA order, and is a reused
 * buffer owned by whoever produced the frame: read it before returning, never retain it. Going out
 * that is the sender's buffer; coming back it is the library's. Deliberately not a `data class` —
 * equality over a buffer that is overwritten every frame would mean nothing.
 *
 * [alpha] is whether the fourth byte is transparency. False on the way out means the library encodes
 * the frame as BGRX; false on the way back means the sender sent none, and the byte is undefined.
 */
class OmtVideoFrame(
    val bgra: ByteArray,
    val width: Int,
    val height: Int,
    val alpha: Boolean,
    val frameRateN: Int,
    val frameRateD: Int,
)
