package org.churchpresenter.app.churchpresenter.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pure ATEM wire-format helpers: big-endian u16 read/write, the command envelope, the ack
 * window arithmetic, and the media-pool upload payloads. Every offset and length here is dictated
 * by the ATEM protocol — a byte in the wrong place, or a size field with the wrong endianness,
 * makes the switcher reject the upload or drops the wrong keyer. The socket I/O around these is not
 * exercised; the byte layout is.
 */
class AtemClientProtocolTest {

    private val atem = AtemClient("127.0.0.1")

    @Test
    fun `u16 reads two bytes big-endian`() {
        assertEquals(0x1234, atem.u16(byteArrayOf(0x12, 0x34), 0))
        assertEquals(0xFFFF, atem.u16(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), 0))
        assertEquals(0x00FF, atem.u16(byteArrayOf(0x00, 0xFF.toByte()), 0))
    }

    @Test
    fun `writeU16 and u16 are inverses across the full range`() {
        for (value in listOf(0, 1, 255, 256, 0x1234, 0x8000, 0xFFFF)) {
            val buf = ByteArray(2)
            atem.writeU16(buf, 0, value)
            assertEquals(value, atem.u16(buf, 0), "round-trip of $value")
        }
    }

    @Test
    fun `buildCommandBytes writes the length header, the 4-char name and the data`() {
        val cmd = atem.buildCommandBytes("CPgI", byteArrayOf(0x01, 0x02))
        assertEquals(10, cmd.size, "8-byte header + 2 data")
        assertEquals(10, atem.u16(cmd, 0), "bytes 0-1 are the total command length")
        assertEquals("CPgI", String(cmd, 4, 4, Charsets.US_ASCII))
        assertEquals(0x01, cmd[8].toInt())
        assertEquals(0x02, cmd[9].toInt())
    }

    @Test
    fun `buildCommandBytes truncates an over-long name to four bytes`() {
        val cmd = atem.buildCommandBytes("TOOLONG", ByteArray(0))
        assertEquals("TOOL", String(cmd, 4, 4, Charsets.US_ASCII))
    }

    @Test
    fun `isCoveredByAck treats an exact match as covered`() {
        assertTrue(atem.isCoveredByAck(ackId = 100, packetId = 100))
    }

    @Test
    fun `isCoveredByAck covers packets shortly before the ack but not shortly after`() {
        assertTrue(atem.isCoveredByAck(ackId = 100, packetId = 90), "90 was acked by 100")
        assertFalse(atem.isCoveredByAck(ackId = 100, packetId = 110), "110 comes after the ack")
    }

    @Test
    fun `isCoveredByAck handles the 15-bit id wraparound`() {
        assertTrue(atem.isCoveredByAck(ackId = 5, packetId = 0x7FFF))
    }

    @Test
    fun `buildLockPayload sets the store id and the lock flag`() {
        val locked = atem.buildLockPayload(storeId = 0x0102, locked = true)
        assertEquals(4, locked.size)
        assertEquals(0x0102, atem.u16(locked, 0))
        assertEquals(1, locked[2].toInt())

        assertEquals(0, atem.buildLockPayload(storeId = 1, locked = false)[2].toInt())
    }

    @Test
    fun `buildUploadRequestPayload lays out ids, a big-endian uint32 size, and write mode`() {
        val p = atem.buildUploadRequestPayload(
            transferId = 0x1111,
            storeId = 0x2222,
            frameIndex = 0x0003,
            size = 0x00ABCDEF,
        )
        assertEquals(16, p.size)
        assertEquals(0x1111, atem.u16(p, 0))
        assertEquals(0x2222, atem.u16(p, 2))
        assertEquals(0x0003, atem.u16(p, 6))
        assertEquals(0x00, p[8].toInt() and 0xFF)
        assertEquals(0xAB, p[9].toInt() and 0xFF)
        assertEquals(0xCD, p[10].toInt() and 0xFF)
        assertEquals(0xEF, p[11].toInt() and 0xFF)
        assertEquals(1, atem.u16(p, 12), "mode 1 = write")
    }

    @Test
    fun `buildFileDescriptionPayload places transferId, name and md5 at their offsets`() {
        val md5 = ByteArray(16) { (it + 1).toByte() }
        val p = atem.buildFileDescriptionPayload(transferId = 0x0007, name = "clip", md5 = md5)
        assertEquals(212, p.size)
        assertEquals(0x0007, atem.u16(p, 0))
        assertEquals("clip", String(p, 2, 4, Charsets.UTF_8))
        assertEquals(1, p[194].toInt(), "md5 starts at offset 194")
        assertEquals(16, p[209].toInt(), "and ends at 209")
    }

    @Test
    fun `buildFileDescriptionPayload tolerates a null name`() {
        val p = atem.buildFileDescriptionPayload(transferId = 1, name = null, md5 = ByteArray(16))
        assertEquals(212, p.size)
        assertEquals(0, p[2].toInt(), "no name bytes were written")
    }

    @Test
    fun `buildDataChunkPayload frames a slice of the source data with its length`() {
        val source = byteArrayOf(9, 8, 7, 6, 5)
        val p = atem.buildDataChunkPayload(transferId = 0x0042, data = source, offset = 1, length = 3)
        assertEquals(4 + 3, p.size)
        assertEquals(0x0042, atem.u16(p, 0))
        assertEquals(3, atem.u16(p, 2))
        assertEquals(listOf<Byte>(8, 7, 6), p.drop(4))
    }

    @Test
    fun `buildSetClipPayload sets the mask, clip index, name and frame count`() {
        val p = atem.buildSetClipPayload(clipIndex = 2, name = "Sermon", frames = 300)
        assertEquals(68, p.size)
        assertEquals(3, p[0].toInt(), "mask 3 = name + frames")
        assertEquals(2, p[1].toInt())
        assertEquals("Sermon", String(p, 2, 6, Charsets.UTF_8))
        assertEquals(300, atem.u16(p, 66))
    }

    @Test
    fun `writeU32 lays out four big-endian bytes`() {
        val buf = ByteArray(4)
        atem.writeU32(buf, 0, 0x01020304)
        assertEquals(listOf(0x01, 0x02, 0x03, 0x04), buf.map { it.toInt() and 0xFF })
    }

    @Test
    fun `writeU32 keeps the top bit rather than sign-extending it`() {
        val buf = ByteArray(4)
        atem.writeU32(buf, 0, -1)
        assertEquals(listOf(0xFF, 0xFF, 0xFF, 0xFF), buf.map { it.toInt() and 0xFF })
    }

    @Test
    fun `u16 reads the full unsigned range`() {
        assertEquals(0xFFFF, atem.u16(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), 0))
    }

    @Test
    fun `buildSetClipPayload truncates a name longer than its field`() {
        val p = atem.buildSetClipPayload(clipIndex = 0, name = "n".repeat(80), frames = 1)
        assertEquals(68, p.size)
        assertTrue(p.copyOfRange(2, 46).all { it.toInt() == 'n'.code })
        assertEquals(0, p[46].toInt())
        assertEquals(1, atem.u16(p, 66))
    }

    @Test
    fun `buildSetClipPayload accepts an empty name and no frames`() {
        val p = atem.buildSetClipPayload(clipIndex = 5, name = "", frames = 0)
        assertEquals(5, p[1].toInt())
        assertTrue(p.copyOfRange(2, 66).all { it.toInt() == 0 })
        assertEquals(0, atem.u16(p, 66))
    }

    @Test
    fun `buildSetClipPayload writes a non-ascii name as utf-8`() {
        val p = atem.buildSetClipPayload(clipIndex = 0, name = "Проповедь", frames = 10)
        assertEquals("Проповедь", String(p, 2, "Проповедь".toByteArray(Charsets.UTF_8).size, Charsets.UTF_8))
    }

    @Test
    fun `buildDataChunkPayload can frame an empty chunk`() {
        val p = atem.buildDataChunkPayload(transferId = 1, data = byteArrayOf(1, 2, 3), offset = 0, length = 0)
        assertEquals(4, p.size)
        assertEquals(0, atem.u16(p, 2))
    }

    @Test
    fun `buildUploadRequestPayload carries a size past the signed int range`() {
        val p = atem.buildUploadRequestPayload(transferId = 1, storeId = 0, frameIndex = 0, size = -1)
        assertEquals(listOf(0xFF, 0xFF, 0xFF, 0xFF), p.copyOfRange(8, 12).map { it.toInt() and 0xFF })
    }

    @Test
    fun `buildFileDescriptionPayload treats an empty name as no name`() {
        val p = atem.buildFileDescriptionPayload(transferId = 1, name = "", md5 = ByteArray(16) { 3 })
        assertTrue(p.copyOfRange(2, 66).all { it.toInt() == 0 })
        assertEquals(3, p[194].toInt())
    }

    @Test
    fun `isCoveredByAck rejects a packet just outside the window`() {
        assertFalse(atem.isCoveredByAck(ackId = 100, packetId = 101))
    }
}
