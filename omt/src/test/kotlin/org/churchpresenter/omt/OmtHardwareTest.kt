package org.churchpresenter.omt

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Semi-transparent red: alpha and a channel that a swapped byte order would turn blue. */
private const val HALF_RED = 0x80FF0000.toInt()

/** How red a received pixel has to be to have come from the frame that was sent. */
private const val REDDISH = 0x80

/** How transparent it has to be to prove the alpha flag crossed the wire. */
private const val SEMI_TRANSPARENT = 0xC0

/** Discovery: forty looks, each after one frame sent, which is well past DNS-SD's own latency. */
private const val DISCOVERY_ATTEMPTS = 40

/** Frames: each attempt waits [OmtReceiver.DEFAULT_TIMEOUT_MS], so this is a few seconds. */
private const val FRAME_ATTEMPTS = 60

private const val SIZE = 16

/**
 * An **opt-in manual harness** that binds the real `libomt`, rather than [FakeOmtLibrary]. It does
 * nothing unless explicitly asked for:
 *
 * ```
 * ./gradlew :omt:test -PomtHardware=true --tests '*OmtHardwareTest*'
 * ./gradlew :omt:test -PomtHardware=true -PomtLibrary=/dir/with/libomt --tests '*OmtHardwareTest*'
 * ```
 *
 * Without `-PomtLibrary` it binds the app's own bundled copy, where `:composeApp:fetchBundledOmt`
 * writes it — which is the copy that matters, since it is the one that ships.
 *
 * **Gated rather than self-skipping**, as `NdiHardwareTest` is: it loads a native library, starts the
 * library's discovery threads and **advertises a source every OMT receiver on the LAN can see**.
 *
 * **What it is for.** The fake proves the logic but cannot prove the binding: that `libomt` exports
 * the symbols [OmtLibC] declares, that [OmtMediaFrameStruct] matches `OMTMediaFrame` field for field,
 * and that `libomt` finds `libvmx` when loaded from a directory of the app's choosing — it is a .NET
 * NativeAOT library that resolves its codec by name at run time. Each of those fails silently or
 * not at all until a frame is actually encoded, decoded and read back, which is what the loopback
 * test does.
 */
class OmtHardwareTest {

    private val enabled = System.getProperty("churchpresenter.omtHardware") == "true"

    /** The library to bind, or null when this harness is switched off or nothing is there to bind. */
    private fun libraryPathOrSkip(): String? {
        if (!enabled) return null
        val configured = System.getProperty("churchpresenter.omtLibrary").orEmpty()
        val dir = configured.ifBlank { bundledDir() }
        return OmtRuntime.detect(customPath = dir).also { println("libomt: $it (looked in $dir)") }
    }

    /**
     * The library at [path], with its own log switched off. Left alone it writes one file per test
     * process into `~/.OMT/logs` — `C:\ProgramData\OMT\logs` on Windows — which a test has no
     * business leaving behind.
     */
    private fun load(path: String): JnaOmtLibrary =
        assertNotNull(JnaOmtLibrary.load(path), "libomt at $path did not bind").apply { setLoggingFilename(null) }

    /** `composeApp/src/jvmMain/appResources/<os>/omt`, relative to this module's directory. */
    private fun bundledDir(): String {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val osDir = when {
            os.contains("mac") -> "macos"
            os.contains("win") -> "windows"
            else -> "linux"
        }
        return File("../composeApp/src/jvmMain/appResources/$osDir/omt").absolutePath
    }

    @Test
    fun `a real sender takes a frame in every mode`() {
        val path = libraryPathOrSkip() ?: return
        val lib = load(path)
        for (mode in OmtOutputMode.entries) {
            val sender = OmtSender(lib, "ChurchPresenter Self Test ${mode.name}", mode, fps = 30,
                product = "ChurchPresenter", version = "test")
            assertTrue(sender.open(), "the library refused a sender in $mode")
            try {
                val argb = IntArray(SIZE * SIZE) { HALF_RED }
                repeat(3) { sender.send(argb, SIZE, SIZE) }
                println("$mode: advertised as '${sender.address()}', ${sender.receiverCount()} connection(s)")
                assertTrue(sender.address().isNotBlank(), "the sender reported no address")
                assertTrue(sender.receiverCount() >= 0, "a negative connection count means the ABI is wrong")
            } finally {
                sender.close()
            }
        }
    }

    /**
     * The whole path through the real library and the network stack: put a source up, discover it,
     * connect, and read back the pixels that were sent — alpha included, since carrying a keyed lower
     * third is the point of an OMT output.
     */
    @Test
    fun `a real receiver reads back what a real sender put on the network`() {
        val path = libraryPathOrSkip() ?: return
        val lib = load(path)
        val name = "ChurchPresenter Loopback Test"
        val sender = OmtSender(lib, name, OmtOutputMode.ALPHA, fps = 30)
        val discovery = OmtDiscovery(lib)
        try {
            assertTrue(sender.open(), "the library refused the sender")
            val argb = IntArray(SIZE * SIZE) { HALF_RED }
            val found = pollFor(DISCOVERY_ATTEMPTS) {
                sender.send(argb, SIZE, SIZE)
                discovery.sources().find { name in it }
            }
            println("discovered: $found (own address '${sender.address()}')")
            // Discovery is the one step a locked-down network can fail on its own; the address the
            // sender reports for itself is what it would have found, so fall back to that.
            val address = found ?: sender.address()

            val receiver = OmtReceiver(lib, address)
            assertTrue(receiver.open(), "the library refused a receiver for $address")
            try {
                val frame = pollFor(FRAME_ATTEMPTS) {
                    sender.send(argb, SIZE, SIZE)
                    receiver.receive()
                }
                val picture = assertNotNull(frame, "connected to $address but no frame ever arrived")
                println("received ${picture.width}x${picture.height}: ${picture.pixels[0].toUInt().toString(16)}")
                println("sender connections while received: ${sender.receiverCount()}")
                assertEquals(SIZE, picture.width)
                assertEquals(SIZE, picture.height)
                val pixel = picture.pixels[0]
                val hex = pixel.toUInt().toString(16)
                assertTrue((pixel shr RED_SHIFT) and BYTE_MASK > REDDISH, "expected red, got $hex")
                assertTrue((pixel ushr ALPHA_SHIFT) < SEMI_TRANSPARENT, "alpha was lost: $hex")
            } finally {
                receiver.close()
            }
        } finally {
            sender.close()
        }
    }

    /**
     * The last thing a receiver gets from a source that is going away is the blank frame, not the
     * picture before it.
     *
     * The receiver-side half of what `OmtVideoRenderer.stop` relies on: OBS keeps drawing the last
     * frame it received, so the blank has to actually arrive before `omt_send_destroy` takes the
     * source down — sent and then lost in the teardown would leave the verse frozen exactly as
     * before. So this sends red until red arrives, then the blank, closes at once, and drains.
     */
    @Test
    fun `the blank frame sent before closing is the last thing a receiver gets`() {
        val path = libraryPathOrSkip() ?: return
        val lib = load(path)
        val name = "ChurchPresenter Blank On Close Test"
        val sender = OmtSender(lib, name, OmtOutputMode.ALPHA, fps = 30)
        assertTrue(sender.open())
        val receiver = OmtReceiver(lib, sender.address())
        try {
            assertTrue(receiver.open())
            val red = IntArray(SIZE * SIZE) { HALF_RED }
            assertNotNull(pollFor(FRAME_ATTEMPTS) { sender.send(red, SIZE, SIZE); receiver.receive() }, "no red frame")

            sender.sendBlank(SIZE, SIZE)
            sender.close()

            var last: Int? = null
            while (true) {
                val frame = receiver.receive() ?: break
                last = frame.pixels[0]
            }
            println("last pixel after close: ${last?.toUInt()?.toString(16)}")
            assertEquals(0, assertNotNull(last, "nothing arrived after the red frame"), "the blank frame never arrived")
        } finally {
            receiver.close()
            sender.close()
        }
    }

    /** Retries [attempt] until it answers, up to [times]. Ends on the answer, never on the clock. */
    private fun <T> pollFor(times: Int, attempt: () -> T?): T? {
        repeat(times) { attempt()?.let { return it } }
        return null
    }
}
