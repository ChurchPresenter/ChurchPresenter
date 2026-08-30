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

    override fun destroy() {
        destroyCount++
    }
}
