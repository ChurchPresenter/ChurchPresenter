package org.churchpresenter.omt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** BGRA bytes for [count] pixels of blue 1, green 2, red 3 and alpha [alpha]. */
private fun bgra(count: Int, alpha: Int = 0x40) =
    ByteArray(count * 4) { i -> if (i % 4 == 3) alpha.toByte() else (i % 4 + 1).toByte() }

class OmtReceiverTest {

    @Test
    fun `no address never connects`() {
        val lib = FakeOmtLibrary()
        val receiver = OmtReceiver(lib, "  ")
        assertFalse(receiver.open())
        assertNull(receiver.receive())
        assertTrue(lib.receiversCreated.isEmpty())
    }

    @Test
    fun `a refused receiver is not open`() {
        val receiver = OmtReceiver(FakeOmtLibrary(refuseAddresses = setOf("gone")), "gone")
        assertFalse(receiver.open())
        assertFalse(receiver.isOpen)
    }

    @Test
    fun `opening twice connects once, asking for the preview stream when set`() {
        val lib = FakeOmtLibrary()
        val receiver = OmtReceiver(lib, "HOST (Cam)", preview = true)
        assertTrue(receiver.open())
        assertTrue(receiver.open())
        assertEquals(listOf("HOST (Cam)" to true), lib.receiversCreated)
    }

    @Test
    fun `a frame with alpha arrives as ARGB with its transparency`() {
        val lib = FakeOmtLibrary()
        lib.incoming += OmtVideoFrame(bgra(2), 2, 1, alpha = true, 30_000, 1_000)
        val receiver = OmtReceiver(lib, "x").apply { open() }
        val frame = assertNotNull(receiver.receive())
        assertEquals(2, frame.width)
        assertEquals(1, frame.height)
        assertEquals(0x40030201, frame.pixels[0])
    }

    @Test
    fun `a frame without alpha arrives opaque whatever its fourth byte says`() {
        val lib = FakeOmtLibrary()
        lib.incoming += OmtVideoFrame(bgra(1, alpha = 0), 1, 1, alpha = false, 30_000, 1_000)
        val frame = assertNotNull(OmtReceiver(lib, "x").apply { open() }.receive())
        assertEquals(0xFF030201.toInt(), frame.pixels[0])
    }

    @Test
    fun `a quiet source and an empty frame are both nothing`() {
        val lib = FakeOmtLibrary()
        val receiver = OmtReceiver(lib, "x").apply { open() }
        assertNull(receiver.receive())
        lib.incoming += OmtVideoFrame(ByteArray(0), 0, 0, true, 30_000, 1_000)
        assertNull(receiver.receive())
    }

    @Test
    fun `the pixel buffer grows for a larger frame and is kept for a smaller one`() {
        val lib = FakeOmtLibrary()
        lib.incoming += OmtVideoFrame(bgra(4), 2, 2, true, 30_000, 1_000)
        lib.incoming += OmtVideoFrame(bgra(1), 1, 1, true, 30_000, 1_000)
        val receiver = OmtReceiver(lib, "x").apply { open() }
        val big = assertNotNull(receiver.receive()).pixels
        assertSame(big, assertNotNull(receiver.receive()).pixels)
    }

    @Test
    fun `preview switches in place, and only when it changes`() {
        val lib = FakeOmtLibrary()
        val receiver = OmtReceiver(lib, "x")
        receiver.setPreview(true)
        assertTrue(receiver.preview)
        assertTrue(lib.previewChanges.isEmpty(), "not connected yet: remembered for the open")
        receiver.open()
        receiver.setPreview(true)
        receiver.setPreview(false)
        assertEquals(1, lib.previewChanges.size)
        assertFalse(lib.previewChanges.single().second)
    }

    @Test
    fun `closing disconnects once`() {
        val lib = FakeOmtLibrary()
        val receiver = OmtReceiver(lib, "x").apply { open() }
        receiver.close()
        receiver.close()
        assertEquals(1, lib.receiversDestroyed.size)
        assertNull(receiver.receive())
    }
}
