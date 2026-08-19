package presentation.engine

import org.junit.jupiter.api.Test
import presentation.engine.Fixtures.ProtoWriter
import presentation.engine.keynote.IwaChunkReader
import presentation.engine.keynote.IwaMessage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Round-trip tests for the hand-rolled protobuf wire reader and the IWA chunk container —
 * the foundations the reverse-engineered Keynote parser stands on.
 */
class KeynoteIwaTest {

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

    private fun buildIwa(objects: List<Triple<Long, Int, ByteArray>>, compressed: Boolean): ByteArray =
        Fixtures.buildIwa(objects, compressed)

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
