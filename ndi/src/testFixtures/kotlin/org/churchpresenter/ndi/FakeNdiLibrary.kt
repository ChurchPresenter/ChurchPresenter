package org.churchpresenter.ndi

/**
 * A plain Kotlin stand-in for the native runtime — no mock, no NDI install, no network.
 *
 * Records what was created and what was sent, so a test asserts on the frames that reached the wire
 * rather than on the fact a method was called. Handles are sequential non-zero longs, matching what
 * the real library's opaque pointers behave like from this side.
 */
class FakeNdiLibrary(
    private val versionString: String = "NDI SDK 6.0.0",
    private val supportedCpu: Boolean = true,
    private val initializes: Boolean = true,
    /** Names for which sendCreate refuses, as the real runtime does when a name is already taken. */
    private val refuseNames: Set<String> = emptySet(),
    /** Sources for which recvCreate refuses, as the runtime does for one that has gone away. */
    private val refuseSources: Set<String> = emptySet(),
    /** Whether findCreate refuses, which is what a runtime with no network interface up does. */
    private val refuseFinder: Boolean = false,
) : NdiLibrary {

    data class SentFrame(
        val sender: Long,
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val format: NdiPixelFormat,
        val frameRateN: Int,
        val frameRateD: Int,
    ) {
        // Generated equals/hashCode would compare the ByteArray by identity; nothing in the suite
        // compares whole frames, so both are explicitly not supported rather than quietly wrong.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    // Synchronized, and it matters: a renderer sends from its own pump coroutine while the test
    // thread reads these to decide whether to stop waiting. Plain lists made that a
    // ConcurrentModificationException in whichever test happened to read mid-send.
    val created: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    val destroyed: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())
    val sent: MutableList<SentFrame> = java.util.Collections.synchronizedList(mutableListOf())
    var initializeCount = 0
        private set
    var destroyCount = 0
        private set
    var connections = 0

    /**
     * How many times the receiver count has been asked for.
     *
     * The renderer decides whether to render at all from this answer, so how often it asks is
     * behaviour worth asserting — and a test that waits for it to rise has a positive signal that
     * the pump reached its decision, rather than a pause hoping it did.
     *
     * Volatile: written on the renderer's pump coroutine, read from the test thread.
     */
    @Volatile
    var connectionQueries = 0
        private set

    private var nextHandle = 1L
    private val handleNames = java.util.concurrent.ConcurrentHashMap<Long, String>()

    /** The name the sender with this handle was created under. */
    fun nameOf(handle: Long): String? = handleNames[handle]

    /** Every frame sent to the sender created under [name]. */
    fun framesFor(name: String): List<SentFrame> =
        synchronized(sent) { sent.toList() }.filter { handleNames[it.sender] == name }

    override fun version(): String = versionString

    override fun isSupportedCpu(): Boolean = supportedCpu

    override fun initialize(): Boolean {
        initializeCount++
        return initializes
    }

    @Synchronized
    override fun sendCreate(name: String, groups: String, clockVideo: Boolean): Long {
        if (name in refuseNames) return 0L
        created += name
        val handle = nextHandle++
        handleNames[handle] = name
        return handle
    }

    override fun sendVideo(sender: Long, frame: NdiVideoFrame) {
        sent += SentFrame(
            sender, frame.bgra.copyOf(), frame.width, frame.height,
            frame.format, frame.frameRateN, frame.frameRateD,
        )
    }

    override fun connectionCount(sender: Long, timeoutMs: Int): Int {
        connectionQueries++
        return connections
    }

    override fun sendDestroy(sender: Long) {
        destroyed += sender
    }

    // ── The receive half ────────────────────────────────────────────

    /** What the fake network is advertising. Mutate it to make a source appear or disappear. */
    val discoverable: MutableList<NdiSourceInfo> =
        java.util.Collections.synchronizedList(mutableListOf())

    /** Every finder handle created, and every one destroyed. */
    val findersCreated: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())
    val findersDestroyed: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())

    /** The source each receiver handle was connected to, and the bandwidth it asked for. */
    val receivers: MutableMap<Long, ReceiverConnection> = java.util.concurrent.ConcurrentHashMap()
    val receiversDestroyed: MutableList<Long> = java.util.Collections.synchronizedList(mutableListOf())

    /** What [findSources] was last asked to wait, so a test can assert on discovery's blocking. */
    @Volatile
    var lastFindTimeoutMs: Int = -1
        private set

    /** How many times a capture has been asked for — a positive signal a loop is running. */
    @Volatile
    var captureCount = 0
        private set

    data class ReceiverConnection(
        val source: NdiSourceInfo,
        val bandwidth: NdiBandwidth,
        val receiverName: String,
    )

    private val incoming = java.util.concurrent.ConcurrentLinkedQueue<NdiVideoFrame>()

    /**
     * Queues one frame for the next [recvCaptureVideo] to return. An empty queue answers null,
     * which is the ordinary "nothing arrived yet" a real receiver gives.
     */
    fun offerFrame(frame: NdiVideoFrame) {
        incoming += frame
    }

    /** Queues a frame of one repeated colour, which is enough to assert the pixels came through. */
    fun offerSolidFrame(
        width: Int,
        height: Int,
        argb: Int,
        format: NdiPixelFormat = NdiPixelFormat.BGRA,
    ) {
        val bytes = ByteArray(frameSizeBytes(width, height))
        argbToNdiBytes(IntArray(width * height) { argb }, bytes, opaque = format == NdiPixelFormat.BGRX)
        offerFrame(NdiVideoFrame(bytes, width, height, format, 30_000, 1_000))
    }

    @Synchronized
    override fun findCreate(showLocalSources: Boolean, groups: String): Long {
        if (refuseFinder) return 0L
        val handle = nextHandle++
        findersCreated += handle
        return handle
    }

    override fun findSources(finder: Long, timeoutMs: Int): List<NdiSourceInfo> {
        lastFindTimeoutMs = timeoutMs
        return synchronized(discoverable) { discoverable.toList() }
    }

    override fun findDestroy(finder: Long) {
        findersDestroyed += finder
    }

    @Synchronized
    override fun recvCreate(source: NdiSourceInfo, bandwidth: NdiBandwidth, receiverName: String): Long {
        if (source.name in refuseSources) return 0L
        val handle = nextHandle++
        receivers[handle] = ReceiverConnection(source, bandwidth, receiverName)
        return handle
    }

    override fun recvCaptureVideo(receiver: Long, timeoutMs: Int): NdiVideoFrame? {
        captureCount++
        return incoming.poll()
    }

    override fun recvDestroy(receiver: Long) {
        receiversDestroyed += receiver
        receivers.remove(receiver)
    }

    override fun destroy() {
        destroyCount++
    }
}
