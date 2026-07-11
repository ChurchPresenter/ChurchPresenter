package presentation.engine

import io.airlift.compress.snappy.SnappyCompressor
import org.junit.jupiter.api.Test
import presentation.engine.keynote.IwaChunkReader
import presentation.engine.keynote.IwaMessage
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Round-trip tests for the hand-rolled protobuf wire reader and the IWA chunk container —
 * the foundations the reverse-engineered Keynote parser stands on.
 */
class KeynoteIwaTest {

    // ── Minimal protobuf writer (test-side only) ──────────────────────────────

    private class ProtoWriter {
        val out = ByteArrayOutputStream()

        fun varintField(field: Int, value: Long) {
            writeVarint((field.toLong() shl 3) or 0L)
            writeVarint(value)
        }

        fun bytesField(field: Int, data: ByteArray) {
            writeVarint((field.toLong() shl 3) or 2L)
            writeVarint(data.size.toLong())
            out.write(data)
        }

        fun fixed32Field(field: Int, bits: Int) {
            writeVarint((field.toLong() shl 3) or 5L)
            for (i in 0 until 4) out.write((bits shr (8 * i)) and 0xFF)
        }

        fun floatField(field: Int, value: Float) = fixed32Field(field, java.lang.Float.floatToIntBits(value))

        fun writeVarint(value: Long) {
            var v = value
            while (true) {
                if (v and 0x7F.inv().toLong() == 0L) {
                    out.write(v.toInt())
                    return
                }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }

        fun toByteArray(): ByteArray = out.toByteArray()
    }

    @Test
    fun `proto reader round-trips varints strings floats and nested messages`() {
        val nested = ProtoWriter().apply { varintField(1, 42) }.toByteArray()
        val writer = ProtoWriter().apply {
            varintField(1, 1234567890123L)
            bytesField(2, "héllo".toByteArray(Charsets.UTF_8))
            floatField(3, 3.5f)
            bytesField(4, nested)
            varintField(5, 1)
        }
        val message = assertNotNull(IwaMessage.parse(writer.toByteArray()))
        assertEquals(1234567890123L, message.varint(1))
        assertEquals("héllo", message.string(2))
        assertEquals(3.5f, message.float(3))
        assertEquals(42L, assertNotNull(message.message(4)).varint(1))
        assertEquals(true, message.bool(5))
        assertEquals(null, message.varint(99), "unknown fields must read as absent")
    }

    @Test
    fun `truncated input returns null instead of throwing`() {
        val writer = ProtoWriter().apply { bytesField(2, ByteArray(100)) }
        val bytes = writer.toByteArray()
        assertEquals(null, IwaMessage.parse(bytes.copyOfRange(0, bytes.size - 10)))
    }

    // ── IWA chunk container ───────────────────────────────────────────────────

    private fun buildIwa(objects: List<Triple<Long, Int, ByteArray>>, compressed: Boolean): ByteArray {
        val stream = ByteArrayOutputStream()
        for ((identifier, type, payload) in objects) {
            val messageInfo = ProtoWriter().apply {
                varintField(1, type.toLong())      // MessageInfo.type
                varintField(3, payload.size.toLong()) // MessageInfo.length
            }.toByteArray()
            val archiveInfo = ProtoWriter().apply {
                varintField(1, identifier)          // ArchiveInfo.identifier
                bytesField(2, messageInfo)          // ArchiveInfo.message_infos
            }.toByteArray()
            val lengthPrefix = ProtoWriter().apply { writeVarint(archiveInfo.size.toLong()) }.toByteArray()
            stream.write(lengthPrefix)
            stream.write(archiveInfo)
            stream.write(payload)
        }
        val raw = stream.toByteArray()
        val body: ByteArray
        val chunkType: Int
        if (compressed) {
            val compressor = SnappyCompressor()
            val buffer = ByteArray(compressor.maxCompressedLength(raw.size))
            val written = compressor.compress(raw, 0, raw.size, buffer, 0, buffer.size)
            body = buffer.copyOf(written)
            chunkType = 0
        } else {
            body = raw
            chunkType = 1
        }
        val file = ByteArrayOutputStream()
        file.write(chunkType)
        file.write(body.size and 0xFF)
        file.write((body.size shr 8) and 0xFF)
        file.write((body.size shr 16) and 0xFF)
        file.write(body)
        return file.toByteArray()
    }

    @Test
    fun `chunk reader round-trips snappy-compressed archives`() {
        val payloadA = ProtoWriter().apply { varintField(2, 7) }.toByteArray()
        val payloadB = ProtoWriter().apply { bytesField(3, "slide".toByteArray()) }.toByteArray()
        val iwa = buildIwa(listOf(Triple(11L, 1, payloadA), Triple(12L, 5, payloadB)), compressed = true)

        val objects = IwaChunkReader.readObjects(iwa)
        assertEquals(2, objects.size)
        assertEquals(11L, objects[0].identifier)
        assertEquals(1, objects[0].type)
        assertEquals(7L, assertNotNull(IwaMessage.parse(objects[0].payload)).varint(2))
        assertEquals(12L, objects[1].identifier)
        assertEquals(5, objects[1].type)
        assertEquals("slide", assertNotNull(IwaMessage.parse(objects[1].payload)).string(3))
    }

    @Test
    fun `chunk reader accepts uncompressed chunks`() {
        val payload = ProtoWriter().apply { varintField(1, 99) }.toByteArray()
        val iwa = buildIwa(listOf(Triple(5L, 2, payload)), compressed = false)
        val objects = IwaChunkReader.readObjects(iwa)
        assertEquals(1, objects.size)
        assertEquals(99L, assertNotNull(IwaMessage.parse(objects[0].payload)).varint(1))
    }

    @Test
    fun `garbage input yields no objects and no exception`() {
        assertTrue(IwaChunkReader.readObjects(ByteArray(64) { (it * 37).toByte() }).isEmpty())
    }

    @Test
    fun `packed varints decode`() {
        // Packed repeated field: length-delimited blob of varints.
        val packed = ProtoWriter().apply {
            writeVarint(3)
            writeVarint(200)
            writeVarint(1)
        }.toByteArray()
        val writer = ProtoWriter().apply { bytesField(6, packed) }
        val message = assertNotNull(IwaMessage.parse(writer.toByteArray()))
        assertEquals(listOf(3L, 200L, 1L), message.varints(6))
    }
}
