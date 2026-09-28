package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.omt.FakeOmtLibrary
import org.churchpresenter.omt.OmtReceiver
import org.churchpresenter.omt.OmtVideoFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ADDRESS = "BOOTH (Camera 1)"
private const val W = 8
private const val H = 4
private const val POLL_MS = 2L
private const val WAIT_MS = 4_000L

/** [W]x[H] of opaque red, as BGRA. */
private fun redFrame() = OmtVideoFrame(
    ByteArray(W * H * 4) { i -> if (i % 4 == 2 || i % 4 == 3) 0xFF.toByte() else 0 },
    W, H, alpha = true, frameRateN = 30_000, frameRateD = 1_000,
)

/**
 * The Canvas's OMT receive cache, driven over `:omt`'s [FakeOmtLibrary].
 *
 * Real [OmtReceiver]s over a fake library, so what is asserted is the picture a layer would have
 * drawn and the connections a sender would have seen. The capture loop itself is shared with NDI's
 * cache and is covered by both suites.
 */
class OmtFrameCacheTest {

    private fun source(address: String = ADDRESS, preview: Boolean = false) =
        SceneSource.OmtSource(id = "o1", name = "OMT", sourceAddress = address, preview = preview)

    private fun cache(lib: FakeOmtLibrary) = OmtFrameCache { address, preview -> OmtReceiver(lib, address, preview) }

    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(POLL_MS)
        }
    }

    @Test
    fun `acquiring connects to the configured address, asking for the stream the layer wants`() {
        val lib = FakeOmtLibrary()
        val cache = cache(lib)
        val flows = cache.acquire(source(preview = true))
        waitFor("the receiver to connect") { flows.connected.value }
        assertEquals(listOf(ADDRESS to true), lib.receiversCreated)
        cache.release(source(preview = true))
    }

    @Test
    fun `a frame on the wire reaches the layer at the size it was sent`() {
        val lib = FakeOmtLibrary().apply { incoming += redFrame() }
        val cache = cache(lib)
        val flows = cache.acquire(source())
        waitFor("a frame") { flows.frame.value != null }
        val image = assertNotNull(flows.frame.value).toAwtImage()
        assertEquals(W, image.width)
        assertEquals(H, image.height)
        assertEquals(0xFFFF0000.toInt(), image.getRGB(0, 0))
        cache.release(source())
    }

    @Test
    fun `two layers of one source share one connection, and the last to let go disconnects`() {
        val lib = FakeOmtLibrary()
        val cache = cache(lib)
        val first = cache.acquire(source())
        cache.acquire(source().copy(id = "o2"))
        waitFor("the receiver to connect") { first.connected.value }
        assertEquals(1, lib.receiversCreated.size)

        cache.release(source())
        assertTrue(cache.isConnected(source()), "one layer still draws it")
        cache.release(source())
        assertFalse(cache.isConnected(source()))
        waitFor("the receiver to close") { lib.receiversDestroyed.size == 1 }
        assertNull(first.frame.value)
    }

    @Test
    fun `the preview stream and the full one are two connections`() {
        val cache = cache(FakeOmtLibrary())
        assertTrue(cache.keyFor(source()) != cache.keyFor(source(preview = true)))
        assertEquals(cache.keyFor(source()), cache.keyFor(source().copy(id = "other", name = "Other")))
        assertTrue(cache.keyFor(source()) != cache.keyFor(source(address = "omt://elsewhere:6400")))
    }

    @Test
    fun `a source the library will not connect to reads as not connected`() {
        val lib = FakeOmtLibrary(refuseAddresses = setOf(ADDRESS))
        val cache = cache(lib)
        val flows = cache.acquire(source())
        waitFor("the attempt to finish") { cache.isConnected(source()) && lib.receiversCreated.isEmpty() }
        assertFalse(flows.connected.value)
        cache.release(source())
    }

    @Test
    fun `no library at all is a layer that draws nothing, not a crash`() {
        val cache = OmtFrameCache { _, _ -> null }
        val flows = cache.acquire(source())
        assertFalse(flows.connected.value)
        assertNull(flows.frame.value)
        cache.release(source())
    }

    @Test
    fun `a library that throws mid-service disconnects the layer and closes the receiver`() {
        val lib = FakeOmtLibrary()
        val cache = cache(lib)
        val flows = cache.acquire(source())
        waitFor("the receiver to connect") { flows.connected.value }
        lib.receiveFailure = IllegalStateException("gone")
        waitFor("the receiver to be closed") { lib.receiversDestroyed.isNotEmpty() }
        assertFalse(flows.connected.value)
        cache.release(source())
    }

    @Test
    fun `releasing something that was never acquired does nothing`() {
        cache(FakeOmtLibrary()).release(source())
    }
}
