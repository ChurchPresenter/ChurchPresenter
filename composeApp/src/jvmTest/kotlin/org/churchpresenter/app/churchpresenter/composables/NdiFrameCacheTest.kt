package org.churchpresenter.app.churchpresenter.composables

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ndi.FakeNdiLibrary
import org.churchpresenter.ndi.NdiBandwidth
import org.churchpresenter.ndi.NdiReceiver
import org.churchpresenter.ndi.NdiSourceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SOURCE_NAME = "BOOTH (Camera 1)"
private const val SOURCE_ADDRESS = "192.168.1.20:5961"
private const val W = 8
private const val H = 4
private const val RED = 0xFFFF0000.toInt()
private const val POLL_MS = 2L
private const val WAIT_MS = 4_000L

/**
 * The Canvas's NDI receive cache, driven over `:ndi`'s [FakeNdiLibrary].
 *
 * Real [NdiReceiver]s over a fake library rather than mocks, so what is asserted is the picture a
 * layer would have drawn and the connections a sender would have seen. No NDI Runtime is involved
 * and nothing touches the network.
 */
class NdiFrameCacheTest {

    private fun source(
        name: String = SOURCE_NAME,
        address: String = SOURCE_ADDRESS,
        lowBandwidth: Boolean = false,
    ) = SceneSource.NdiSource(
        id = "n1", name = "NDI", sourceName = name, sourceAddress = address, lowBandwidth = lowBandwidth,
    )

    private fun cache(lib: FakeNdiLibrary) = NdiFrameCache { info, bandwidth ->
        NdiReceiver(lib, info, bandwidth, receiverName = "test")
    }

    /** Ends on the condition itself; the deadline only fails the test. */
    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(POLL_MS)
        }
    }

    @Test
    fun `acquiring connects to the configured source and reports itself connected`() {
        val lib = FakeNdiLibrary()
        val cache = cache(lib)
        val layer = source()

        val flows = cache.acquire(layer)
        waitFor("the receiver to connect") { flows.connected.value }

        assertEquals(
            NdiSourceInfo(SOURCE_NAME, SOURCE_ADDRESS),
            lib.receivers.values.single().source,
        )
        cache.release(layer)
    }

    @Test
    fun `a frame on the wire reaches the layer at the size it was sent`() {
        val lib = FakeNdiLibrary().apply { offerSolidFrame(W, H, RED) }
        val cache = cache(lib)
        val layer = source()

        val flows = cache.acquire(layer)
        waitFor("a frame") { flows.frame.value != null }

        val image = assertNotNull(flows.frame.value)
        assertEquals(W, image.width)
        assertEquals(H, image.height)
        cache.release(layer)
    }

    @Test
    fun `the low bandwidth proxy is what a low bandwidth layer asks the sender for`() {
        val lib = FakeNdiLibrary()
        val cache = cache(lib)
        val layer = source(lowBandwidth = true)

        val flows = cache.acquire(layer)
        waitFor("the receiver to connect") { flows.connected.value }

        assertEquals(NdiBandwidth.LOWEST, lib.receivers.values.single().bandwidth)
        cache.release(layer)
    }

    @Test
    fun `two layers of one source share a single connection`() {
        val lib = FakeNdiLibrary()
        val cache = cache(lib)
        // The canvas preview and the presenter output are two composables drawing one source.
        val preview = source()
        val output = source().copy(id = "n2", transform = source().transform)

        cache.acquire(preview)
        cache.acquire(output)
        waitFor("the shared receiver") { lib.receivers.size == 1 }

        cache.release(preview)
        assertTrue(cache.isConnected(output), "the second layer is still drawing it")
        assertTrue(lib.receiversDestroyed.isEmpty(), "the sender must not see the stream dropped")

        cache.release(output)
        waitFor("the connection to close") { lib.receivers.isEmpty() }
    }

    @Test
    fun `two layers at different bandwidths are two connections, not one`() {
        val lib = FakeNdiLibrary()
        val cache = cache(lib)
        val full = source()
        val proxy = source(lowBandwidth = true)

        cache.acquire(full)
        cache.acquire(proxy)
        waitFor("both receivers") { lib.receivers.size == 2 }

        assertEquals(
            setOf(NdiBandwidth.HIGHEST, NdiBandwidth.LOWEST),
            lib.receivers.values.map { it.bandwidth }.toSet(),
        )
        cache.release(full)
        cache.release(proxy)
    }

    @Test
    fun `two different sources do not share a connection`() {
        val lib = FakeNdiLibrary()
        val cache = cache(lib)
        val camera = source()
        val graphics = source(name = "BOOTH (Graphics)", address = "")

        cache.acquire(camera)
        cache.acquire(graphics)
        waitFor("both receivers") { lib.receivers.size == 2 }

        assertEquals(
            setOf(SOURCE_NAME, "BOOTH (Graphics)"),
            lib.receivers.values.map { it.source.name }.toSet(),
        )
        cache.release(camera)
        cache.release(graphics)
    }

    @Test
    fun `releasing the last layer disconnects and forgets the frame`() {
        val lib = FakeNdiLibrary().apply { offerSolidFrame(W, H, RED) }
        val cache = cache(lib)
        val layer = source()

        val flows = cache.acquire(layer)
        waitFor("a frame") { flows.frame.value != null }

        cache.release(layer)

        assertNull(flows.frame.value, "a layer removed mid-service must not leave its last frame up")
        assertFalse(flows.connected.value)
        assertFalse(cache.isConnected(layer))
        waitFor("the receiver to be destroyed") { lib.receiversDestroyed.size == 1 }
    }

    @Test
    fun `releasing something that was never acquired does nothing`() {
        val lib = FakeNdiLibrary()
        val cache = cache(lib)

        cache.release(source())

        assertTrue(lib.receiversDestroyed.isEmpty())
    }

    @Test
    fun `a source the runtime will not connect to reads as not connected rather than hanging`() {
        val lib = FakeNdiLibrary(refuseSources = setOf(SOURCE_NAME))
        val cache = cache(lib)
        val layer = source()

        val flows = cache.acquire(layer)
        waitFor("the attempt to be given up on") { lib.receivers.isEmpty() && flows.frame.value == null }

        assertFalse(flows.connected.value)
        cache.release(layer)
    }

    @Test
    fun `no runtime at all is a layer that draws nothing, not a crash`() {
        val cache = NdiFrameCache { _, _ -> null }
        val layer = source()

        val flows = cache.acquire(layer)

        assertNull(flows.frame.value)
        assertFalse(flows.connected.value)
        cache.release(layer)
    }

    @Test
    fun `bandwidth is part of what makes two layers the same layer`() {
        val cache = NdiFrameCache { _, _ -> null }

        assertEquals(cache.keyFor(source()), cache.keyFor(source().copy(id = "other")))
        assertTrue(cache.keyFor(source()) != cache.keyFor(source(lowBandwidth = true)))
        assertTrue(cache.keyFor(source()) != cache.keyFor(source(name = "BOOTH (Graphics)")))
    }

    @Test
    fun `the source a layer resolves to carries both halves of its identity`() {
        val cache = NdiFrameCache { _, _ -> null }

        assertEquals(NdiSourceInfo(SOURCE_NAME, SOURCE_ADDRESS), cache.infoFor(source()))
    }
}
