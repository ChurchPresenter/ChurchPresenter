package org.churchpresenter.atem

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    @Test
    fun `hd heights use the same four-bytes-per-pixel contract as sd`() {
        // height >= 720 switches the coefficients from BT.601 to BT.709; only the size contract is
        // asserted here, matching this file's determinism-over-arithmetic style.
        val out = AtemFrameEncoder.argbToYuv422(4, 720, IntArray(4 * 720) { 0xFF204060.toInt() })
        assertEquals(4 * 720 * 4, out.size)
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

    @Test
    fun `a run of exactly three identical blocks is left uncompressed`() {
        // The header only pays off from four repeats on: three copies (8*3=24B) cost the same as
        // the header+count+block record (also 24B), so the threshold is deliberately above three.
        val block = 0x99AABBCCDDEEFF00uL.toLong()
        val out = AtemFrameEncoder.encodeRLE(uniformBlocks(3, block))
        assertEquals(24, out.size)
        assertEquals(block, out.longAt(0))
        assertEquals(block, out.longAt(8))
        assertEquals(block, out.longAt(16))
    }

    @Test
    fun `a short run in the middle of the data is emitted as literal blocks`() {
        // The mirror of `a run of exactly three identical blocks`, but mid-stream rather than at
        // the end: a two-block run followed by something different takes the "emit the repeats
        // verbatim" path, not the header path. Below the threshold the output is the input.
        val a = 0x0101010101010101L
        val b = 0x0202020202020202L
        val input = ByteBuffer.allocate(3 * 8).apply { putLong(a); putLong(a); putLong(b) }.array()
        val out = AtemFrameEncoder.encodeRLE(input)
        assertTrue(out.contentEquals(input), "a run too short to compress must survive unchanged")
        assertNotEquals(AtemFrameEncoder.RLE_HEADER, out.longAt(0), "no header may be emitted for it")
    }

    @Test
    fun `a short run mid-stream still lets a later long run compress`() {
        // Both halves of the branch in one frame, which is what a real lower third looks like: a
        // couple of repeated edge blocks, then a long transparent stretch.
        val a = 0x0303030303030303L
        val b = 0x0404040404040404L
        val c = 0x0505050505050505L
        val input = ByteBuffer.allocate(8 * 8).apply {
            putLong(a); putLong(a)                                     // short run -> literals
            putLong(b)                                                 // breaks it
            putLong(c); putLong(c); putLong(c); putLong(c); putLong(c)  // long run -> header
        }.array()
        val out = AtemFrameEncoder.encodeRLE(input)
        assertEquals(a, out.longAt(0), "the short run is written out verbatim")
        assertEquals(a, out.longAt(8))
        assertEquals(b, out.longAt(16))
        assertEquals(AtemFrameEncoder.RLE_HEADER, out.longAt(24), "the long run still gets a header")
        assertEquals(5L, out.longAt(32))
        assertEquals(c, out.longAt(40))
        assertEquals(48, out.size)
    }

    @Test
    fun `pending literal blocks flush before a following run is emitted`() {
        // Two distinct blocks followed by a run of four: the literals must reach the output ahead
        // of the run's header, verbatim, not be swallowed by the run they happen to precede.
        val x = 0x1111111111111111L
        val y = 0x2222222222222222L
        val z = 0x3333333333333333L
        val buf = ByteBuffer.allocate(6 * 8).apply {
            putLong(x); putLong(y); putLong(z); putLong(z); putLong(z); putLong(z)
        }.array()

        val out = AtemFrameEncoder.encodeRLE(buf)

        assertEquals(40, out.size, "2 literal blocks (16B) + header+count+block (24B)")
        assertEquals(x, out.longAt(0))
        assertEquals(y, out.longAt(8))
        assertEquals(AtemFrameEncoder.RLE_HEADER, out.longAt(16))
        assertEquals(4L, out.longAt(24))
        assertEquals(z, out.longAt(32))
    }
}
