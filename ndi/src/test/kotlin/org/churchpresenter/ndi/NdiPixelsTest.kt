package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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
}
