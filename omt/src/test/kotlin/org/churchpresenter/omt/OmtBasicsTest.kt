package org.churchpresenter.omt

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Discovery, the pixel conversions and the format tables — the small pieces the rest stands on. */
class OmtBasicsTest {

    @Test
    fun `discovery drops blanks and repeats and sorts without regard to case`() {
        val lib = FakeOmtLibrary().apply { discovered = listOf("b (Two)", "", "A (One)", "b (Two)", "C (Three)") }
        assertEquals(listOf("A (One)", "b (Two)", "C (Three)"), OmtDiscovery(lib).sources())
        assertEquals(1, lib.discoveryLooks)
    }

    @Test
    fun `ARGB goes out as BGRA and comes back unchanged`() {
        val argb = intArrayOf(0x80FF4020.toInt(), 0x00010203)
        val bytes = ByteArray(8)
        argbToBgra(argb, bytes, opaque = false)
        assertContentEquals(byteArrayOf(0x20, 0x40, 0xFF.toByte(), 0x80.toByte(), 3, 2, 1, 0), bytes)
        val back = IntArray(2)
        bgraToArgb(bytes, back, 2, opaque = false)
        assertContentEquals(argb, back)
    }

    @Test
    fun `opaque conversion writes full alpha in both directions`() {
        val bytes = ByteArray(4)
        argbToBgra(intArrayOf(0x00112233), bytes, opaque = true)
        assertEquals(0xFF.toByte(), bytes[3])
        val back = IntArray(1)
        bgraToArgb(byteArrayOf(1, 2, 3, 0), back, 1, opaque = true)
        assertEquals(0xFF030201.toInt(), back[0])
    }

    @Test
    fun `stride and frame size are four bytes a pixel`() {
        assertEquals(7_680, lineStrideBytes(1_920))
        assertEquals(8_294_400, frameSizeBytes(1_920, 1_080))
    }

    @Test
    fun `the codec table knows the two RGB layouts and nothing else`() {
        assertEquals(OmtCodec.BGRA, OmtCodec.ofFourCc(0x41524742))
        assertEquals(OmtCodec.BGRX, OmtCodec.ofFourCc(0x58524742))
        assertNull(OmtCodec.ofFourCc(0x59565955), "UYVY is converted by the library before it gets here")
    }

    @Test
    fun `only alpha mode carries alpha`() {
        assertTrue(OmtOutputMode.ALPHA.carriesAlpha)
        assertFalse(OmtOutputMode.FILL.carriesAlpha)
    }

    @Test
    fun `the qualities are libomt's own numbers`() {
        assertEquals(listOf(0, 1, 50, 100), OmtQuality.entries.map { it.native })
    }
}
