package org.churchpresenter.omt

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val BUNDLE = File("bundle").absolutePath
private val BUNDLED_LIB = File(BUNDLE, "libomt.dylib").path

class OmtRuntimeHostTest {

    private fun host(
        path: String? = BUNDLED_LIB,
        library: OmtLibrary? = FakeOmtLibrary(),
    ): OmtRuntimeHost = OmtRuntimeHost(locate = { _, _ -> path }, loader = { library })

    @Test
    fun `nothing found is not installed, and nothing can be created`() {
        val host = host(path = null)
        assertEquals(OmtRuntimeStatus.NotInstalled, host.start())
        assertFalse(host.status.isReady)
        assertNull(host.createSender("x", OmtOutputMode.ALPHA, 30))
        assertNull(host.createReceiver("x"))
        assertNull(host.discovery)
    }

    @Test
    fun `a library that will not load is a failure naming its path`() {
        val status = host(library = null).start()
        assertEquals(OmtRuntimeStatus.LoadFailed(BUNDLED_LIB), status)
        assertFalse(status.isReady)
    }

    @Test
    fun `a loaded library is ready, and says whether it is the bundled copy`() {
        assertEquals(OmtRuntimeStatus.Ready(BUNDLED_LIB, bundled = true), host().start(bundledDir = BUNDLE))
        val elsewhere = host(path = File("/opt/omt/libomt.dylib").path).start(bundledDir = BUNDLE)
        assertEquals(false, assertIs<OmtRuntimeStatus.Ready>(elsewhere).bundled)
        assertEquals(false, assertIs<OmtRuntimeStatus.Ready>(host().start()).bundled, "no bundled dir at all")
    }

    @Test
    fun `the log is pointed where asked, or switched off`() {
        val lib = FakeOmtLibrary()
        host(library = lib).start(logFile = "/logs/omt.log")
        assertEquals("/logs/omt.log", lib.loggingFilename)
        val quiet = FakeOmtLibrary()
        host(library = quiet).start()
        assertNull(quiet.loggingFilename)
    }

    @Test
    fun `a discovery server is set before anything discovers, and a blank one is left alone`() {
        val lib = FakeOmtLibrary()
        host(library = lib).start(discoveryServer = "omt://server:6400")
        assertEquals("omt://server:6400", lib.discoveryServer)
        val blank = FakeOmtLibrary()
        host(library = blank).start(discoveryServer = "  ")
        assertNull(blank.discoveryServer, "a blank setting must not override OMT's own settings.xml")
    }

    @Test
    fun `starting again once loaded changes nothing`() {
        var loads = 0
        val lib = FakeOmtLibrary()
        val host = OmtRuntimeHost(locate = { _, _ -> BUNDLED_LIB }, loader = { loads++; lib })
        val first = host.start()
        assertSame(first, host.start(discoveryServer = "omt://late:1"))
        assertEquals(1, loads)
        assertNull(lib.discoveryServer)
    }

    @Test
    fun `a ready host hands out senders, receivers and its discovery`() {
        val lib = FakeOmtLibrary()
        val host = host(library = lib)
        host.start()
        val sender = assertNotNull(host.createSender("Out", OmtOutputMode.FILL, 25, OmtQuality.LOW, "P", "1"))
        assertEquals("Out", sender.name)
        assertEquals(OmtOutputMode.FILL, sender.mode)
        assertTrue(sender.open())
        assertEquals(listOf(OmtQuality.LOW), lib.qualities)
        assertEquals("omt://h:1", assertNotNull(host.createReceiver("omt://h:1", preview = true)).address)
        assertNotNull(host.discovery)
    }

    @Test
    fun `shutdown stops the library and forgets it`() {
        val lib = FakeOmtLibrary()
        val host = host(library = lib)
        host.start()
        host.shutdown()
        assertEquals(1, lib.shutdownCount)
        assertEquals(OmtRuntimeStatus.NotInstalled, host.status)
        assertNull(host.createSender("x", OmtOutputMode.ALPHA, 30))
        host.shutdown()
        assertEquals(1, lib.shutdownCount, "a second shutdown has nothing to stop")
    }
}
