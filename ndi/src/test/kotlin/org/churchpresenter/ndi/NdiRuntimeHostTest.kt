package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LIB_PATH = "/opt/ndi/libndi.so.6"

class NdiRuntimeHostTest {

    private fun host(
        library: NdiLibrary? = FakeNdiLibrary(),
        path: String? = LIB_PATH,
    ) = NdiRuntimeHost(locate = { path }, loader = { library })

    @Test
    fun `nothing installed reports NotInstalled and never tries to load`() {
        var loadAttempts = 0
        val h = NdiRuntimeHost(locate = { null }, loader = { loadAttempts++; null })
        assertEquals(NdiRuntimeStatus.NotInstalled, h.start())
        assertEquals(0, loadAttempts)
    }

    @Test
    fun `a library that will not load reports LoadFailed with the path it tried`() {
        val status = host(library = null).start()
        assertEquals(NdiRuntimeStatus.LoadFailed(LIB_PATH), status)
    }

    @Test
    fun `an unsupported cpu is reported as such rather than as missing`() {
        val status = host(FakeNdiLibrary(supportedCpu = false)).start()
        assertEquals(NdiRuntimeStatus.UnsupportedCpu, status)
    }

    @Test
    fun `the cpu is checked before the runtime is initialized`() {
        val lib = FakeNdiLibrary(supportedCpu = false)
        host(lib).start()
        assertEquals(0, lib.initializeCount)
    }

    @Test
    fun `a runtime that declines to initialize is a load failure`() {
        assertEquals(NdiRuntimeStatus.LoadFailed(LIB_PATH), host(FakeNdiLibrary(initializes = false)).start())
    }

    @Test
    fun `a working runtime reports Ready with its version and path`() {
        val status = host(FakeNdiLibrary(versionString = "NDI SDK 6.1.1")).start()
        assertEquals(NdiRuntimeStatus.Ready("NDI SDK 6.1.1", LIB_PATH), status)
        assertTrue(status.isReady)
    }

    @Test
    fun `only Ready is ready`() {
        assertFalse(NdiRuntimeStatus.NotInstalled.isReady)
        assertFalse(NdiRuntimeStatus.UnsupportedCpu.isReady)
        assertFalse(NdiRuntimeStatus.LoadFailed(LIB_PATH).isReady)
    }

    @Test
    fun `starting twice does not initialize the runtime twice`() {
        val lib = FakeNdiLibrary()
        val h = host(lib)
        h.start()
        h.start()
        assertEquals(1, lib.initializeCount)
    }

    @Test
    fun `a failed start can be retried after the operator installs the runtime`() {
        // What the settings card's "look again" does: nothing was left latched by the first miss.
        var installed = false
        val lib = FakeNdiLibrary()
        val h = NdiRuntimeHost(locate = { if (installed) LIB_PATH else null }, loader = { lib })
        assertEquals(NdiRuntimeStatus.NotInstalled, h.start())
        installed = true
        assertTrue(h.start().isReady)
    }

    @Test
    fun `the custom path is passed through to discovery`() {
        var seen: String? = null
        NdiRuntimeHost(locate = { seen = it; null }, loader = { null }).start("/home/op/ndi")
        assertEquals("/home/op/ndi", seen)
    }

    @Test
    fun `no sender can be created before a successful start`() {
        assertNull(host().createSender("Stage", NdiOutputMode.ALPHA, 30))
    }

    @Test
    fun `no sender can be created when the runtime failed to load`() {
        val h = host(library = null)
        h.start()
        assertNull(h.createSender("Stage", NdiOutputMode.ALPHA, 30))
    }

    @Test
    fun `a sender over a started runtime carries its name and mode`() {
        val h = host()
        h.start()
        val sender = assertNotNull(h.createSender("Stage", NdiOutputMode.FILL_AND_KEY, 30))
        assertEquals("Stage", sender.name)
        assertEquals(NdiOutputMode.FILL_AND_KEY, sender.mode)
        assertEquals("Stage Key", sender.keyName)
    }

    @Test
    fun `shutdown takes the runtime down and refuses further senders`() {
        val lib = FakeNdiLibrary()
        val h = host(lib)
        h.start()
        h.shutdown()
        assertEquals(1, lib.destroyCount)
        assertNull(h.createSender("Stage", NdiOutputMode.ALPHA, 30))
        assertFalse(h.status.isReady)
    }

    @Test
    fun `shutdown before start is harmless`() {
        val lib = FakeNdiLibrary()
        NdiRuntimeHost(locate = { LIB_PATH }, loader = { lib }).shutdown()
        assertEquals(0, lib.destroyCount)
    }

    @Test
    fun `the runtime can be started again after a shutdown`() {
        val lib = FakeNdiLibrary()
        val h = host(lib)
        h.start()
        h.shutdown()
        assertTrue(h.start().isReady)
        assertEquals(2, lib.initializeCount)
    }
}
