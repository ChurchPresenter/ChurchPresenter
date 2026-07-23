package org.churchpresenter.app.churchpresenter.server

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Encoding a lower-third frame for the ATEM media pool.
 *
 * Two independent steps feed the upload: ARGB pixels become 10-bit YUVA 4:2:2 (two 32-bit words per
 * pixel pair, so exactly four bytes per pixel), then that buffer is RLE-compressed in 8-byte blocks.
 * The compressor's contract is what the upload path relies on: it must NEVER produce more bytes than
 * it was given (the FTDa chunker sizes buffers on that promise), a flat/transparent frame — which is
 * what a lower third mostly is — must collapse to a tiny run, and a frame with no repeats must come
 * back byte-for-byte so nothing is silently dropped on the wire.
 *
 * Values are asserted as structural invariants (lengths, the run header, exact round-trips of
 * distinct data), never as reproduced YUV arithmetic — the conversion math is the thing under test,
 * not something to re-derive in the assertion.
 */
class AtemFrameEncoderTest {

    private fun ByteArray.longAt(byteOffset: Int): Long = ByteBuffer.wrap(this).getLong(byteOffset)

    /** A YUVA buffer of [blockCount] identical 8-byte blocks, each carrying [value]. */
    private fun uniformBlocks(blockCount: Int, value: Long): ByteArray {
        val buf = ByteBuffer.allocate(blockCount * 8)
        repeat(blockCount) { buf.putLong(value) }
        return buf.array()
    }

    /** A YUVA buffer whose every 8-byte block differs, so nothing is compressible. */
    private fun distinctBlocks(blockCount: Int): ByteArray {
        val buf = ByteBuffer.allocate(blockCount * 8)
        repeat(blockCount) { buf.putLong(0x0102030405060700L + it) }
        return buf.array()
    }

    // ── argbToYuv422 ────────────────────────────────────────────────────────────

    @Test
    fun `every pixel becomes exactly four bytes`() {
        // 4x2 = 8 pixels -> 32 bytes; the media pool's FTSD size field is this raw length.
        val out = AtemFrameEncoder.argbToYuv422(4, 2, IntArray(8) { 0xFF204060.toInt() })
        assertEquals(8 * 4, out.size, "raw YUVA length must be width*height*4 or the upload size is wrong")
    }

    @Test
    fun `encodeFrame reports the pre-RLE length as the raw size`() {
        val frame = AtemFrameEncoder.encodeFrame(4, 2, IntArray(8) { 0x00000000 })
        assertEquals(8 * 4, frame.rawLen, "rawLen is the FTSD size field and must be the uncompressed length")
    }

    @Test
    fun `a mismatched pixel buffer is rejected rather than encoded wrong`() {
        // 2x2 claims 4 pixels; handing over 3 must fail loudly, not read past the array or ship a short frame.
        assertFailsWith<IllegalArgumentException> {
            AtemFrameEncoder.encodeFrame(2, 2, IntArray(3))
        }
    }

    @Test
    fun `conversion is deterministic`() {
        val pixels = IntArray(8) { 0xFF3399CC.toInt() }
        val a = AtemFrameEncoder.argbToYuv422(4, 2, pixels)
        val b = AtemFrameEncoder.argbToYuv422(4, 2, pixels)
        assertTrue(a.contentEquals(b), "same pixels must encode identically every time")
    }

    // ── encodeRLE ───────────────────────────────────────────────────────────────

    @Test
    fun `a flat frame collapses to a single run`() {
        // 8 identical blocks -> [header][count=8][block] = 24 bytes. This is why a transparent
        // lower third uploads in a fraction of the bandwidth of its raw size.
        val out = AtemFrameEncoder.encodeRLE(uniformBlocks(8, 0x1122334455667788L))
        assertEquals(24, out.size, "a run of identical blocks must compress to header+count+block")
        assertEquals(AtemFrameEncoder.RLE_HEADER, out.longAt(0), "a run must open with the RLE header marker")
        assertEquals(8L, out.longAt(8), "the run count must be the number of identical blocks")
        assertEquals(0x1122334455667788L, out.longAt(16), "the run must carry the repeated block verbatim")
    }

    @Test
    fun `data with no repeats is returned byte-for-byte`() {
        // The decoder on the ATEM re-expands this; if the encoder dropped a non-repeating block the
        // frame would be corrupt. All-distinct input must survive unchanged.
        val input = distinctBlocks(4)
        val out = AtemFrameEncoder.encodeRLE(input)
        assertTrue(out.contentEquals(input), "incompressible data must pass through untouched")
    }

    @Test
    fun `output is never larger than the input`() {
        // The chunker sizes its send buffer on this guarantee; a growing compressor would overrun it.
        val mixed = ByteBuffer.allocate(6 * 8).apply {
            putLong(0xAAL); putLong(0xAAL); putLong(0xAAL); putLong(0xAAL)  // a run
            putLong(0xBBL); putLong(0xCCL)                                   // then two singletons
        }.array()
        val out = AtemFrameEncoder.encodeRLE(mixed)
        assertTrue(out.size <= mixed.size, "RLE output must never exceed its input length")
    }
}
