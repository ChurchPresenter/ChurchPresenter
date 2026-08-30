package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val OUTPUT_NAME = "Lower Third"
private const val FPS = 30

/** One pixel, ARGB, so a frame's bytes can be read off directly. */
private fun onePixel(argb: Int) = intArrayOf(argb)

class NdiSenderTest {

    private fun sender(
        library: NdiLibrary,
        mode: NdiOutputMode = NdiOutputMode.ALPHA,
    ) = NdiSender(library, OUTPUT_NAME, mode, FPS)

    @Test
    fun `open creates one sender under the configured name`() {
        val lib = FakeNdiLibrary()
        assertTrue(sender(lib).open())
        assertEquals(listOf(OUTPUT_NAME), lib.created)
    }

    @Test
    fun `fill and key mode creates a second sender under the key name`() {
        val lib = FakeNdiLibrary()
        sender(lib, NdiOutputMode.FILL_AND_KEY).open()
        assertEquals(listOf(OUTPUT_NAME, "$OUTPUT_NAME Key"), lib.created)
    }

    @Test
    fun `alpha and fill modes create no key sender`() {
        for (mode in listOf(NdiOutputMode.ALPHA, NdiOutputMode.FILL)) {
            val lib = FakeNdiLibrary()
            sender(lib, mode).open()
            assertEquals(listOf(OUTPUT_NAME), lib.created, "mode $mode")
        }
    }

    @Test
    fun `open reports failure and stays closed when the runtime refuses`() {
        val lib = FakeNdiLibrary(refuseNames = setOf(OUTPUT_NAME))
        val s = sender(lib)
        assertFalse(s.open())
        assertFalse(s.isOpen)
    }

    @Test
    fun `open is idempotent`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib)
        s.open()
        s.open()
        assertEquals(1, lib.created.size)
    }

    @Test
    fun `send before open puts nothing on the wire`() {
        val lib = FakeNdiLibrary()
        sender(lib).send(onePixel(-1), 1, 1)
        assertTrue(lib.sent.isEmpty())
    }

    @Test
    fun `alpha mode sends BGRA with the alpha channel preserved`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib, NdiOutputMode.ALPHA)
        s.open()
        // 50% alpha, pure red.
        s.send(onePixel(0x80FF0000.toInt()), 1, 1)
        val frame = lib.sent.single()
        assertEquals(NdiPixelFormat.BGRA, frame.format)
        // B, G, R, A
        assertContentEquals(byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0x80.toByte()), frame.bytes)
    }

    @Test
    fun `fill mode forces the alpha byte opaque and declares BGRX`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib, NdiOutputMode.FILL)
        s.open()
        s.send(onePixel(0x00FF0000), 1, 1)
        val frame = lib.sent.single()
        assertEquals(NdiPixelFormat.BGRX, frame.format)
        assertEquals(0xFF.toByte(), frame.bytes[3])
    }

    @Test
    fun `the key frame is a luminance ramp of the fill's alpha`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib, NdiOutputMode.FILL_AND_KEY)
        s.open()
        s.send(onePixel(0x80FF0000.toInt()), 1, 1)
        val key = lib.framesFor("$OUTPUT_NAME Key").single()
        // Grey at the fill's alpha level, in all three colour channels, fully opaque.
        assertContentEquals(
            byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0xFF.toByte()),
            key.bytes,
        )
    }

    @Test
    fun `the key signal reads alpha, so black-on-transparent keys instead of vanishing`() {
        // This is the whole reason the key is not DeckLink's max(r, g, b): opaque black text has no
        // luminance at all, and keyed on brightness it would be cut away exactly where it is drawn.
        val opaqueBlack = 0xFF000000.toInt()
        val out = IntArray(1)
        NdiSender.argbToLuminanceKey(intArrayOf(opaqueBlack), out)
        assertEquals(0xFFFFFFFF.toInt(), out[0])
    }

    @Test
    fun `a fully transparent pixel keys to black`() {
        val out = IntArray(1)
        NdiSender.argbToLuminanceKey(intArrayOf(0x00FFFFFF), out)
        assertEquals(0xFF000000.toInt(), out[0])
    }

    @Test
    fun `the fill of a fill-and-key pair is still sent`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib, NdiOutputMode.FILL_AND_KEY)
        s.open()
        s.send(onePixel(-1), 1, 1)
        assertEquals(1, lib.framesFor(OUTPUT_NAME).size)
        assertEquals(1, lib.framesFor("$OUTPUT_NAME Key").size)
    }

    @Test
    fun `the frame carries the configured rate as a rational`() {
        val lib = FakeNdiLibrary()
        val s = NdiSender(lib, OUTPUT_NAME, NdiOutputMode.ALPHA, fps = 60)
        s.open()
        s.send(onePixel(-1), 1, 1)
        val frame = lib.sent.single()
        assertEquals(60_000, frame.frameRateN)
        assertEquals(1_000, frame.frameRateD)
    }

    @Test
    fun `an fps of zero is clamped rather than sent as a zero rate`() {
        // A rate of 0/1000 is not a frame rate, and a receiver given one has nothing to pace on.
        assertEquals(1_000, NdiSender.frameRateNumerator(0))
        assertEquals(1_000, NdiSender.frameRateNumerator(-5))
    }

    @Test
    fun `the frame carries the pixel dimensions it was given`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib)
        s.open()
        s.send(IntArray(4 * 2), 4, 2)
        val frame = lib.sent.single()
        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertEquals(4 * 2 * 4, frame.bytes.size)
    }

    @Test
    fun `the pixel buffer is reused across frames of the same size`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib)
        s.open()
        repeat(3) { s.send(onePixel(it), 1, 1) }
        // Each recorded frame is a copy, so what this proves is that all three sends produced the
        // right bytes despite sharing one buffer — a stale buffer would repeat the first pixel.
        assertEquals(listOf<Byte>(0, 1, 2), lib.sent.map { it.bytes[0] })
    }

    @Test
    fun `a frame of a different size regrows the buffer rather than truncating`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib)
        s.open()
        s.send(IntArray(1), 1, 1)
        s.send(IntArray(64), 8, 8)
        assertEquals(64 * 4, lib.sent.last().bytes.size)
    }

    @Test
    fun `connectionCount is zero while closed and the runtime's answer once open`() {
        val lib = FakeNdiLibrary()
        lib.connections = 4
        val s = sender(lib)
        assertEquals(0, s.connectionCount())
        s.open()
        assertEquals(4, s.connectionCount())
    }

    @Test
    fun `close destroys both senders and leaves the output closed`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib, NdiOutputMode.FILL_AND_KEY)
        s.open()
        s.close()
        assertEquals(2, lib.destroyed.size)
        assertFalse(s.isOpen)
    }

    @Test
    fun `close on a sender that was never opened does nothing`() {
        val lib = FakeNdiLibrary()
        sender(lib).close()
        assertTrue(lib.destroyed.isEmpty())
    }

    @Test
    fun `a closed sender sends nothing and can be reopened`() {
        val lib = FakeNdiLibrary()
        val s = sender(lib)
        s.open()
        s.close()
        s.send(onePixel(-1), 1, 1)
        assertTrue(lib.sent.isEmpty())
        assertTrue(s.open())
        assertEquals(2, lib.created.size)
    }
}
