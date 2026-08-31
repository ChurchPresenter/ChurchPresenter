package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NdiPixelsTest {

    @Test
    fun `a row is four bytes per pixel`() {
        assertEquals(1920 * 4, lineStrideBytes(1920))
        assertEquals(0, lineStrideBytes(0))
    }

    @Test
    fun `a 1080p frame is just over eight megabytes`() {
        assertEquals(1920 * 1080 * 4, frameSizeBytes(1920, 1080))
    }

    @Test
    fun `argb is rewritten as bgra, not merely copied`() {
        // Distinct values per channel, so a wrong order cannot pass by coincidence.
        val out = ByteArray(4)
        argbToNdiBytes(intArrayOf(0x11223344), out, opaque = false)
        assertContentEquals(byteArrayOf(0x44, 0x33, 0x22, 0x11), out)
    }

    @Test
    fun `opaque forces the alpha byte regardless of the source pixel`() {
        val out = ByteArray(4)
        argbToNdiBytes(intArrayOf(0x00223344), out, opaque = true)
        assertEquals(0xFF.toByte(), out[3])
        // and leaves the colour channels alone
        assertContentEquals(byteArrayOf(0x44, 0x33, 0x22, 0xFF.toByte()), out)
    }

    @Test
    fun `every pixel of a multi-pixel frame is converted`() {
        val out = ByteArray(8)
        argbToNdiBytes(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), out, opaque = false)
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            out,
        )
    }

    @Test
    fun `a buffer larger than the frame is written into from the start and left otherwise alone`() {
        // This is the reuse case: the sender grows its buffer once and keeps it.
        val out = ByteArray(8) { 0x7F }
        argbToNdiBytes(intArrayOf(0x11223344), out, opaque = false)
        assertContentEquals(byteArrayOf(0x44, 0x33, 0x22, 0x11, 0x7F, 0x7F, 0x7F, 0x7F), out)
    }

    @Test
    fun `the fourcc codes are the SDK's own values`() {
        // 'B','G','R','A' little-endian packed. Wrong here and a receiver rejects every frame.
        assertEquals(0x41524742, NdiPixelFormat.BGRA.fourCc)
        assertEquals(0x58524742, NdiPixelFormat.BGRX.fourCc)
    }

    @Test
    fun `only alpha mode asks for a format with an alpha channel`() {
        assertEquals(NdiPixelFormat.BGRA, NdiOutputMode.ALPHA.pixelFormat)
        assertEquals(NdiPixelFormat.BGRX, NdiOutputMode.FILL.pixelFormat)
        assertEquals(NdiPixelFormat.BGRX, NdiOutputMode.FILL_AND_KEY.pixelFormat)
    }

    @Test
    fun `only fill-and-key puts a second sender on the network`() {
        assertEquals(listOf(false, false, true), NdiOutputMode.entries.map { it.hasKeySender })
    }

    @Test
    fun `a frame survives the round trip out to NDI and back`() {
        val original = intArrayOf(0xFF102030.toInt(), 0x80FFFFFF.toInt(), 0x00000000, 0xFF7F0000.toInt())
        val bytes = ByteArray(frameSizeBytes(original.size, 1))
        val back = IntArray(original.size)

        argbToNdiBytes(original, bytes, opaque = false)
        ndiBytesToArgb(bytes, back, original.size, opaque = false)

        assertContentEquals(original, back)
    }

    @Test
    fun `an opaque read ignores the fourth byte, whatever it happens to hold`() {
        val original = intArrayOf(0x00102030, 0x80FFFFFF.toInt())
        val bytes = ByteArray(frameSizeBytes(original.size, 1))
        val back = IntArray(original.size)

        argbToNdiBytes(original, bytes, opaque = false)
        ndiBytesToArgb(bytes, back, original.size, opaque = true)

        assertContentEquals(intArrayOf(0xFF102030.toInt(), 0xFFFFFFFF.toInt()), back)
    }

    @Test
    fun `only the pixels asked for are read, so an oversized buffer keeps its tail`() {
        val bytes = ByteArray(frameSizeBytes(4, 1))
        argbToNdiBytes(IntArray(4) { 0xFF112233.toInt() }, bytes, opaque = false)
        val out = IntArray(4) { -1 }

        ndiBytesToArgb(bytes, out, count = 2, opaque = false)

        assertContentEquals(intArrayOf(0xFF112233.toInt(), 0xFF112233.toInt(), -1, -1), out)
    }

    @Test
    fun `every format a receiver can be handed is recognised, and nothing else is`() {
        assertEquals(NdiPixelFormat.BGRA, NdiPixelFormat.ofFourCc(NdiPixelFormat.BGRA.fourCc))
        assertEquals(NdiPixelFormat.BGRX, NdiPixelFormat.ofFourCc(NdiPixelFormat.BGRX.fourCc))
        assertNull(NdiPixelFormat.ofFourCc(0x59565955))
    }
}
