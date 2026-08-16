package org.churchpresenter.app.churchpresenter.server

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Chunk boundaries and transfer-failure wording — the two pure pieces of the media-pool upload.
 *
 * A chunk that ends inside an RLE block makes the switcher decode garbage, so the length is pulled
 * back to the start of the block; and an FTDE the transfer cannot retry past has to say which of
 * the two very different causes it was, because "clip pool full" is fixed by shortening the clip
 * and "rejected" is not.
 */
class AtemTransferChunkingTest {

    private val atem = AtemClient("127.0.0.1")

    /** 64 bytes of payload with [AtemFrameEncoder.RLE_HEADER] written at each of [headerAt]. */
    private fun payload(vararg headerAt: Int): ByteBuffer =
        ByteBuffer.allocate(64).also { buf -> headerAt.forEach { buf.putLong(it, AtemFrameEncoder.RLE_HEADER) } }

    @Test
    fun `a chunk that ends clear of any RLE block keeps its full length`() {
        assertEquals(24, atem.chunkLengthAt(payload(), dataSize = 64, bytesSent = 0, chunkSize = 24))
    }

    @Test
    fun `a chunk ending one word into an RLE block stops before the header`() {
        // Header at 16 = one 8-byte word before the chunk end at 24.
        assertEquals(16, atem.chunkLengthAt(payload(16), dataSize = 64, bytesSent = 0, chunkSize = 24))
    }

    @Test
    fun `a chunk ending two words into an RLE block stops before the header`() {
        // Header at 8 = two 8-byte words before the chunk end at 24.
        assertEquals(8, atem.chunkLengthAt(payload(8), dataSize = 64, bytesSent = 0, chunkSize = 24))
    }

    @Test
    fun `the final chunk is never shortened, header or not`() {
        // Ends the data, so there is no following block to run into.
        assertEquals(16, atem.chunkLengthAt(payload(56), dataSize = 64, bytesSent = 48, chunkSize = 24))
    }

    @Test
    fun `a still rejected outright names the still and its error code`() {
        val message = atem.transferRejected(code = 5, name = "lower-third", frameIndex = 0, retries = 0).message
        assertEquals("ATEM rejected still (error code 5)", message)
    }

    @Test
    fun `a still the switcher stayed busy on reports the retry count`() {
        val message = atem.transferRejected(code = 1, name = "lower-third", frameIndex = 0, retries = 40).message
        assertEquals("ATEM stayed busy uploading still after 40 retries", message)
    }

    @Test
    fun `a clip frame past the first hints at the clip pool`() {
        val message = atem.transferRejected(code = 1, name = null, frameIndex = 7, retries = 40).message
        assertTrue(message!!.startsWith("ATEM stayed busy uploading clip frame 7 after 40 retries"), message)
        assertTrue(message.contains("clip pool capacity"), message)
    }

    @Test
    fun `the first clip frame failing is not blamed on the clip pool`() {
        // Nothing has been written yet, so capacity cannot be the cause — the hint would mislead.
        val message = atem.transferRejected(code = 9, name = null, frameIndex = 0, retries = 0).message
        assertEquals("ATEM rejected clip frame 0 (error code 9)", message)
        assertFalse(message!!.contains("clip pool"))
    }

    @Test
    fun `a packet far behind the ack is outside the window rather than wrapped`() {
        // 1 is more than half the id space before 0x7FFF: neither shortly before it nor wrapped
        // past it, so it is not covered.
        assertFalse(atem.isCoveredByAck(ackId = 0x7FFF, packetId = 1))
    }
}
