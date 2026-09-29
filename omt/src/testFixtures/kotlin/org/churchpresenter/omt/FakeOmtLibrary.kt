package org.churchpresenter.omt

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * A plain Kotlin stand-in for `libomt` — no mock, no library installed, no network.
 *
 * Records what was created and what was sent, so a test asserts on the frames that reached the wire
 * rather than on the fact that a method was called. Handles are sequential non-zero longs, as the
 * real library's opaque pointers look from this side.
 *
 * A fixture rather than a test class because `:composeApp`'s own OMT tests use it too — the same
 * reason `:ndi` publishes `FakeNdiLibrary`.
 */
class FakeOmtLibrary(
    /** Names for which sendCreate refuses, as the real library does with no port free. */
    private val refuseNames: Set<String> = emptySet(),
    /** Addresses for which recvCreate refuses. */
    private val refuseAddresses: Set<String> = emptySet(),
) : OmtLibrary {

    /** One frame as it reached the wire. Identity equality: nothing compares whole frames. */
    class SentFrame(
        val sender: Long,
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val alpha: Boolean,
        val frameRateN: Int,
        val frameRateD: Int,
    )

    // Synchronized: a renderer sends from its own pump coroutine while the test thread reads these.
    val created: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val qualities: MutableList<OmtQuality> = Collections.synchronizedList(mutableListOf())
    val destroyed: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    val sent: MutableList<SentFrame> = Collections.synchronizedList(mutableListOf())
    val senderInformation: MutableList<Triple<String, String, String>> =
        Collections.synchronizedList(mutableListOf())
    val receiversCreated: MutableList<Pair<String, Boolean>> = Collections.synchronizedList(mutableListOf())
    val receiversDestroyed: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    val previewChanges: MutableList<Pair<Long, Boolean>> = Collections.synchronizedList(mutableListOf())

    var loggingFilename: String? = "unset"
        private set
    var discoveryServer: String? = null
        private set
    var shutdownCount = 0
        private set

    /** What connectionCount answers. */
    @Volatile
    var connections = 0

    /**
     * How many times the connection count has been asked for — a positive signal that a renderer
     * reached its decision, rather than a pause hoping it did. Volatile: written on the pump.
     */
    @Volatile
    var connectionQueries = 0
        private set

    /** What discoveryAddresses answers, and how many times it has been asked. */
    @Volatile
    var discovered: List<String> = emptyList()
    @Volatile
    var discoveryLooks = 0
        private set

    /** Frames each receive hands back in order; empty answers null, as a quiet source does. */
    val incoming: MutableList<OmtVideoFrame> = Collections.synchronizedList(mutableListOf())

    /** When set, recvCaptureVideo throws this — what a library that has gone away looks like. */
    @Volatile
    var receiveFailure: RuntimeException? = null

    private var nextHandle = 1L
    private val handleNames = ConcurrentHashMap<Long, String>()

    /** The name the sender with this handle was created under. */
    fun nameOf(handle: Long): String? = handleNames[handle]

    /** Every frame sent to the sender created under [name]. */
    fun framesFor(name: String): List<SentFrame> =
        synchronized(sent) { sent.toList() }.filter { handleNames[it.sender] == name }

    override fun setLoggingFilename(path: String?) {
        loggingFilename = path
    }

    override fun setDiscoveryServer(url: String) {
        discoveryServer = url
    }

    @Synchronized
    override fun sendCreate(name: String, quality: OmtQuality): Long {
        if (name in refuseNames) return 0L
        created += name
        qualities += quality
        val handle = nextHandle++
        handleNames[handle] = name
        return handle
    }

    override fun sendSetSenderInformation(sender: Long, product: String, manufacturer: String, version: String) {
        senderInformation += Triple(product, manufacturer, version)
    }

    override fun sendAddress(sender: Long): String = handleNames[sender]?.let { "FAKEHOST ($it)" }.orEmpty()

    override fun sendVideo(sender: Long, frame: OmtVideoFrame) {
        sent += SentFrame(
            sender, frame.bgra.copyOf(), frame.width, frame.height, frame.alpha, frame.frameRateN, frame.frameRateD,
        )
    }

    override fun connectionCount(sender: Long): Int {
        connectionQueries++
        return connections
    }

    override fun sendDestroy(sender: Long) {
        destroyed += sender
    }

    override fun discoveryAddresses(): List<String> {
        discoveryLooks++
        return discovered
    }

    @Synchronized
    override fun recvCreate(address: String, preview: Boolean): Long {
        if (address in refuseAddresses) return 0L
        receiversCreated += address to preview
        return nextHandle++
    }

    override fun recvCaptureVideo(receiver: Long, timeoutMs: Int): OmtVideoFrame? {
        receiveFailure?.let { throw it }
        return synchronized(incoming) { if (incoming.isEmpty()) null else incoming.removeAt(0) }
    }

    override fun recvSetPreview(receiver: Long, preview: Boolean) {
        previewChanges += receiver to preview
    }

    override fun recvDestroy(receiver: Long) {
        receiversDestroyed += receiver
    }

    override fun shutdown() {
        shutdownCount++
    }
}
