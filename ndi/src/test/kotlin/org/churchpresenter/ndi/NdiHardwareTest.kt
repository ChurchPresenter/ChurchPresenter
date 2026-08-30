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
 * that [NdiSendCreateStruct] and [NdiVideoFrameStruct] match the SDK's ABI field-for-field, and that
 * the FourCC values are the ones a receiver accepts. Those are the assumptions that fail silently —
 * a wrong struct layout is not a compile error and not an exception, it is a wrong picture — and
 * this is the only thing that can check them.
 *
 * It deliberately does **not** assert that a receiver saw anything: that needs a second machine, or
 * NDI Studio Monitor running beside it. What it proves is that every call this app makes reaches the
 * runtime and comes back sane.
 */
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
