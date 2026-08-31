package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An **opt-in manual harness** that binds the real NDI Runtime, rather than [FakeNdiLibrary]. It is
 * not part of the ordinary suite and does nothing unless explicitly asked for:
 *
 * ```
 * ./gradlew :ndi:test -PndiHardware=true --tests '*NdiHardwareTest*'
 * ```
 *
 * **Why it is gated rather than merely self-skipping.** Everything below has effects a routine
 * `check` must not have: it loads a 30 MB native library, brings up the runtime's own threads, and
 * — the part that leaves the machine — **advertises a source that every NDI receiver on the LAN can
 * discover**. On a machine mid-service that is a stray source appearing in the operator's list.
 * It also costs far more than the ~1s per-test bar the suite is held to.
 *
 * **What it is actually for.** The rest of this module is tested against a fake, which proves the
 * logic but cannot prove the *binding*: that `libndi` exports the flat C symbols [NdiLibC] declares,
 * that [NdiSendCreateStruct], [NdiVideoFrameStruct], [NdiFindCreateStruct], [NdiSourceStruct] and
 * [NdiRecvCreateStruct] match the SDK's ABI field-for-field, and that the FourCC values are the
 * ones a receiver accepts. Those are the assumptions that fail silently —
 * a wrong struct layout is not a compile error and not an exception, it is a wrong picture — and
 * this is the only thing that can check them.
 *
 * It deliberately does **not** assert that a receiver saw anything: that needs a second machine, or
 * NDI Studio Monitor running beside it. What it proves is that every call this app makes reaches the
 * runtime and comes back sane.
 */
private const val HALF_RED = 0x80FF0000.toInt().toInt()

/** How red a received pixel has to be to have come from the frame that was sent. */
private const val REDDISH = 0x80

/** Discovery: sixteen looks of a quarter second each, which is well past mDNS's own latency. */
private const val DISCOVERY_ATTEMPTS = 16
private const val DISCOVERY_STEP_MS = 250

/** Frames: each attempt waits [NdiReceiver.DEFAULT_TIMEOUT_MS], so this is a few seconds. */
private const val FRAME_ATTEMPTS = 50

class NdiHardwareTest {

    private val enabled = System.getProperty("churchpresenter.ndiHardware") == "true"

    /**
     * The installed runtime's path, or null when this harness is switched off or the machine has no
     * runtime — in which case every test below returns without touching anything.
     *
     * A plain early return rather than a JUnit `Assume`: this module's suite runs on `kotlin.test`
     * alone, and adding JUnit to its classpath for one skip would be a dependency the other 87
     * tests do not need.
     */
    private fun runtimePathOrSkip(): String? = if (!enabled) null else NdiRuntime.detect()

    @Test
    fun `discovery finds the installed runtime`() {
        val path = runtimePathOrSkip() ?: return
        println("NDI runtime: $path")
        assertTrue(path.isNotBlank())
    }

    @Test
    fun `the runtime loads and reports a version`() {
        val path = runtimePathOrSkip() ?: return
        // The one line the rest of the suite cannot reach: binding a real shared library.
        val lib = assertNotNull(JnaNdiLibrary.load(path), "libndi at $path did not bind")
        assertTrue(lib.isSupportedCpu(), "this CPU does not meet NDI's requirements")
        val version = lib.version()
        println("NDI version: $version")
        assertTrue(version.isNotBlank(), "the runtime reported no version")
        assertTrue(lib.initialize(), "the runtime declined to initialize")
        lib.destroy()
    }

    /**
     * The whole send path against the real ABI: create a source, push a frame of known pixels
     * through the native buffer, ask how many receivers are watching, and take it down again.
     *
     * A wrong `@Structure.FieldOrder` shows up here as a crash or a nonsense connection count,
     * which is precisely what the fake cannot tell us.
     */
    @Test
    fun `a real sender takes a frame in every mode`() {
        val path = runtimePathOrSkip() ?: return
        val lib = assertNotNull(JnaNdiLibrary.load(path))
        assertTrue(lib.initialize())
        try {
            for (mode in NdiOutputMode.entries) {
                val sender = NdiSender(lib, "ChurchPresenter Self Test ${mode.name}", mode, fps = 30)
                assertTrue(sender.open(), "the runtime refused a sender in $mode")
                try {
                    // 16x16 of half-transparent red, the case alpha mode exists for.
                    val argb = IntArray(16 * 16) { 0x80FF0000.toInt() }
                    repeat(3) { sender.send(argb, 16, 16) }
                    val receivers = sender.connectionCount()
                    println("$mode: sent 3 frames, $receivers receiver(s)")
                    assertTrue(receivers >= 0, "a negative receiver count means the ABI is wrong")
                } finally {
                    sender.close()
                }
            }
        } finally {
            lib.destroy()
        }
    }

    /**
     * The whole receive path against the real ABI, in one loop back through the network stack: put
     * a source up, discover it, connect to it, and read back the pixels that were sent.
     *
     * This is the only thing that can catch a wrong field order in the three receive structs. A
     * receiver built over a mislaid `NDIlib_source_t` connects to nothing and simply never gets a
     * frame — no crash, no exception — which is indistinguishable from a quiet network until
     * something actually asserts on the picture, as this does.
     *
     * Discovery is given several seconds because mDNS answers arrive over the first few of them.
     * A machine with no network interface up may still see nothing; the failure message says which
     * step it got to, because "no frame" and "no source" have completely different causes.
     */
    @Test
    fun `a real receiver reads back what a real sender put on the network`() {
        val path = runtimePathOrSkip() ?: return
        val lib = assertNotNull(JnaNdiLibrary.load(path))
        assertTrue(lib.initialize())
        val name = "ChurchPresenter Loopback Test"
        val sender = NdiSender(lib, name, NdiOutputMode.ALPHA, fps = 30)
        val finder = NdiFinder(lib)
        try {
            assertTrue(sender.open(), "the runtime refused the sender")
            assertTrue(finder.open(), "the runtime refused a finder")

            // Keep sending while discovery runs: a receiver joins mid-stream and wants a frame
            // soon after it connects, exactly as it would from a camera.
            val argb = IntArray(16 * 16) { HALF_RED }
            val found = pollFor(DISCOVERY_ATTEMPTS) {
                sender.send(argb, 16, 16)
                // Matched by containment, not equality: a sender called X is advertised to the
                // network as "MACHINE (X)", and that full string is the name a receiver connects by.
                finder.sources(DISCOVERY_STEP_MS).find { name in it.name }
            }
            println("discovered: $found")
            val source = assertNotNull(found, "the sender never appeared in discovery")

            val receiver = assertNotNull(lib.let { NdiReceiver(it, source, receiverName = "Self Test") })
            assertTrue(receiver.open(), "the runtime refused a receiver for $source")
            try {
                val frame = pollFor(FRAME_ATTEMPTS) {
                    sender.send(argb, 16, 16)
                    receiver.receive()
                }
                val picture = assertNotNull(frame, "connected to $source but no frame ever arrived")
                println("received ${picture.width}x${picture.height}")
                assertEquals(16, picture.width)
                assertEquals(16, picture.height)
                // Not asserted exactly: NDI's own encode is lossy, so the pixel that comes back is
                // near the one that went out, not equal to it. What matters is that it is red-ish
                // rather than the black a wrong channel order or a dropped frame would give.
                val red = (picture.pixels[0] shr RED_SHIFT) and BYTE_MASK
                assertTrue(red > REDDISH, "expected a red frame, got ${picture.pixels[0].toUInt().toString(16)}")
            } finally {
                receiver.close()
            }
        } finally {
            finder.close()
            sender.close()
            lib.destroy()
        }
    }

    /** Retries [attempt] until it answers, up to [times]. Ends on the answer, never on the clock. */
    private fun <T> pollFor(times: Int, attempt: () -> T?): T? {
        repeat(times) {
            attempt()?.let { found -> return found }
        }
        return null
    }

    @Test
    fun `the runtime host reports Ready against a real install`() {
        runtimePathOrSkip() ?: return
        val host = NdiRuntimeHost()
        val status = host.start()
        println("status: $status")
        try {
            assertTrue(status.isReady, "expected Ready, got $status")
            val ready = status as NdiRuntimeStatus.Ready
            assertTrue(ready.version.isNotBlank())
            assertEquals(NdiRuntime.detect(), ready.path)
            assertNotNull(host.createSender("ChurchPresenter Host Test", NdiOutputMode.ALPHA, 30))
        } finally {
            host.shutdown()
        }
    }
}
