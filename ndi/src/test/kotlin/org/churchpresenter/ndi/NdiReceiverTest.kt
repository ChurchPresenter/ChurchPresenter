package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val CAMERA = NdiSourceInfo("BOOTH (Camera 1)", "192.168.1.20:5961")
private const val OPAQUE_RED = 0xFFFF0000.toInt()
private const val HALF_GREEN = 0x8000FF00.toInt()
private const val OPAQUE_GREEN = 0xFF00FF00.toInt()
private const val WIDTH = 4
private const val HEIGHT = 2

class NdiReceiverTest {

    @Test
    fun `opening connects to the source it was given, at full bandwidth by default`() {
        val lib = FakeNdiLibrary()
        val receiver = NdiReceiver(lib, CAMERA)

        assertTrue(receiver.open())
        assertTrue(receiver.isOpen)
        val connection = lib.receivers.values.single()
        assertEquals(CAMERA, connection.source)
        assertEquals(NdiBandwidth.HIGHEST, connection.bandwidth)
    }

    @Test
    fun `the low bandwidth proxy is asked for when the caller wants it`() {
        val lib = FakeNdiLibrary()
        NdiReceiver(lib, CAMERA, NdiBandwidth.of(low = true), receiverName = "Canvas").open()

        val connection = lib.receivers.values.single()
        assertEquals(NdiBandwidth.LOWEST, connection.bandwidth)
        assertEquals("Canvas", connection.receiverName)
    }

    @Test
    fun `a blank source is refused without asking the runtime to connect to nothing`() {
        val lib = FakeNdiLibrary()
        val receiver = NdiReceiver(lib, NdiSourceInfo(""))

        assertFalse(receiver.open())
        assertFalse(receiver.isOpen)
        assertTrue(lib.receivers.isEmpty())
    }

    @Test
    fun `a source the runtime will not connect to leaves the receiver closed`() {
        val lib = FakeNdiLibrary(refuseSources = setOf(CAMERA.name))
        val receiver = NdiReceiver(lib, CAMERA)

        assertFalse(receiver.open())
        assertNull(receiver.receive())
    }

    @Test
    fun `opening twice connects once, so a redraw does not add a second receiver`() {
        val lib = FakeNdiLibrary()
        val receiver = NdiReceiver(lib, CAMERA)

        assertTrue(receiver.open())
        assertTrue(receiver.open())
        assertEquals(1, lib.receivers.size)
    }

    @Test
    fun `a receiver that was never opened returns no frames and asks for none`() {
        val lib = FakeNdiLibrary().apply { offerSolidFrame(WIDTH, HEIGHT, OPAQUE_RED) }

        assertNull(NdiReceiver(lib, CAMERA).receive())
        assertEquals(0, lib.captureCount)
    }

    @Test
    fun `nothing on the wire yet is null rather than an error`() {
        val lib = FakeNdiLibrary()
        val receiver = NdiReceiver(lib, CAMERA)
        receiver.open()

        assertNull(receiver.receive())
        assertEquals(1, lib.captureCount)
    }

    @Test
    fun `a BGRA frame arrives as packed ARGB with its alpha intact`() {
        val lib = FakeNdiLibrary().apply { offerSolidFrame(WIDTH, HEIGHT, HALF_GREEN) }
        val receiver = NdiReceiver(lib, CAMERA)
        receiver.open()

        val frame = assertNotNull(receiver.receive())
        assertEquals(WIDTH, frame.width)
        assertEquals(HEIGHT, frame.height)
        for (i in 0 until WIDTH * HEIGHT) {
            assertEquals(HALF_GREEN, frame.pixels[i], "pixel $i")
        }
    }

    @Test
    fun `a BGRX frame arrives fully opaque, whatever its fourth byte said`() {
        // The bytes on the wire carry alpha 0x80; BGRX means that byte is undefined, and a layer
        // drawn at the transparency it implies would be a camera feed that is half invisible.
        val bytes = ByteArray(frameSizeBytes(WIDTH, HEIGHT))
        argbToNdiBytes(IntArray(WIDTH * HEIGHT) { HALF_GREEN }, bytes, opaque = false)
        val lib = FakeNdiLibrary().apply {
            offerFrame(NdiVideoFrame(bytes, WIDTH, HEIGHT, NdiPixelFormat.BGRX, 30_000, 1_000))
        }
        val receiver = NdiReceiver(lib, CAMERA)
        receiver.open()

        val frame = assertNotNull(receiver.receive())
        assertEquals(OPAQUE_GREEN, frame.pixels[0])
    }

    @Test
    fun `the pixel buffer is reused across frames rather than reallocated per frame`() {
        val lib = FakeNdiLibrary().apply {
            offerSolidFrame(WIDTH, HEIGHT, OPAQUE_RED)
            offerSolidFrame(WIDTH, HEIGHT, HALF_GREEN)
        }
        val receiver = NdiReceiver(lib, CAMERA)
        receiver.open()

        val first = assertNotNull(receiver.receive())
        val second = assertNotNull(receiver.receive())
        assertSame(first.pixels, second.pixels, "one buffer, grown once — see NdiFrame")
        assertEquals(HALF_GREEN, second.pixels[0], "and the second frame's pixels are in it")
    }

    @Test
    fun `a smaller frame after a larger one keeps the buffer and reports its own size`() {
        val lib = FakeNdiLibrary().apply {
            offerSolidFrame(WIDTH, HEIGHT, OPAQUE_RED)
            offerSolidFrame(1, 1, HALF_GREEN)
        }
        val receiver = NdiReceiver(lib, CAMERA)
        receiver.open()

        val large = assertNotNull(receiver.receive())
        val small = assertNotNull(receiver.receive())
        assertSame(large.pixels, small.pixels)
        assertEquals(1, small.width)
        assertEquals(1, small.height)
        assertTrue(small.pixels.size >= WIDTH * HEIGHT, "the buffer keeps the larger frame's size")
    }

    @Test
    fun `a frame with no pixels in it is dropped rather than drawn`() {
        val lib = FakeNdiLibrary().apply {
            offerFrame(NdiVideoFrame(ByteArray(0), 0, 0, NdiPixelFormat.BGRA, 30_000, 1_000))
        }
        val receiver = NdiReceiver(lib, CAMERA)
        receiver.open()

        assertNull(receiver.receive())
    }

    @Test
    fun `closing disconnects, and the receiver can be reopened afterwards`() {
        val lib = FakeNdiLibrary()
        val receiver = NdiReceiver(lib, CAMERA)
        receiver.open()
        val handle = lib.receivers.keys.single()

        receiver.close()
        assertFalse(receiver.isOpen)
        assertEquals(listOf(handle), lib.receiversDestroyed)

        assertTrue(receiver.open())
        assertEquals(1, lib.receivers.size)
    }

    @Test
    fun `closing a receiver that was never opened does nothing`() {
        val lib = FakeNdiLibrary()
        NdiReceiver(lib, CAMERA).close()
        assertEquals(emptyList(), lib.receiversDestroyed)
    }
}
