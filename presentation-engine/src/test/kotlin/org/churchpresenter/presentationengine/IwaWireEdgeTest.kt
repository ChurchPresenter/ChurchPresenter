package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.Fixtures.ProtoWriter
import org.churchpresenter.presentationengine.keynote.IwaChunkReader
import org.churchpresenter.presentationengine.keynote.IwaMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IwaWireEdgeTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `present fields are listed and absent ones read as empty`() {
        val bytes = ProtoWriter().apply { varintField(3, 1); floatField(4, 2f) }.toByteArray()
        val message = assertNotNull(IwaMessage.parse(bytes))
        assertTrue(message.has(3))
        assertFalse(message.has(9))
        assertEquals(setOf(3, 4), message.fieldNumbers())
        assertTrue(message.varints(9).isEmpty())
        assertTrue(message.strings(9).isEmpty())
        assertTrue(message.messages(9).isEmpty())
        assertNull(message.double(9))
        assertNull(message.double(3), "a varint is not a double")
        assertEquals(2.0, message.double(4))
    }

    @Test
    fun `repeated varints read unpacked, skip other wire types and stop at a truncated packed value`() {
        val message = assertNotNull(
            IwaMessage.parse(
                ProtoWriter().apply {
                    varintField(1, 5)
                    varintField(1, 6)
                    fixed32Field(1, 7)
                    bytesField(1, bytes(9, 0x80))
                }.toByteArray()
            )
        )
        assertEquals(listOf(5L, 6L, 9L), message.varints(1))
    }

    @Test
    fun `malformed wire data parses to nothing`() {
        assertNull(IwaMessage.parse(bytes(0x00, 0x01)), "field number zero")
        assertNull(IwaMessage.parse(bytes(0x09, 1, 2, 3)), "a fixed64 cut short")
        assertNull(IwaMessage.parse(bytes(0x0D, 1, 2)), "a fixed32 cut short")
        assertNull(IwaMessage.parse(bytes(0x12, 5, 1)), "a length past the end")
        assertNull(IwaMessage.parse(bytes(0x0B, 1)), "a group wire type")
        assertNull(IwaMessage.parse(bytes(0x08, 0x80)), "a varint value cut short")
        assertNull(IwaMessage.parse(bytes(0x80)), "a tag cut short")
    }

    @Test
    fun `a varint longer than sixty-four bits is rejected`() {
        assertNull(IwaMessage.readVarint(ByteArray(11) { if (it < 10) 0x80.toByte() else 0 }, 0))
        assertEquals(300L to 2, IwaMessage.readVarint(bytes(0xAC, 0x02), 0))
    }

    private fun chunk(type: Int, payload: ByteArray): ByteArray =
        bytes(type, payload.size and 0xFF, (payload.size shr 8) and 0xFF, (payload.size shr 16) and 0xFF) + payload

    private fun archive(
        identifier: Long?,
        vararg messages: Pair<Int?, ByteArray>,
        declaredLengths: List<Long>? = null,
    ): ByteArray {
        val info = ProtoWriter().apply {
            identifier?.let { varintField(1, it) }
            messages.forEachIndexed { i, (type, body) ->
                bytesField(2, ProtoWriter().apply {
                    type?.let { varintField(1, it.toLong()) }
                    varintField(3, declaredLengths?.get(i) ?: body.size.toLong())
                }.toByteArray())
            }
        }.toByteArray()
        return ProtoWriter().apply { writeVarint(info.size.toLong()) }.toByteArray() + info +
            messages.fold(ByteArray(0)) { acc, (_, body) -> acc + body }
    }

    @Test
    fun `an archive without identifier or type defaults both to zero and keeps only its first message`() {
        val first = ProtoWriter().apply { varintField(1, 1) }.toByteArray()
        val second = ProtoWriter().apply { varintField(1, 2) }.toByteArray()
        val objects = IwaChunkReader.readObjects(chunk(1, archive(null, null to first, 4 to second)))
        val only = objects.single()
        assertEquals(0L, only.identifier)
        assertEquals(0, only.type)
        assertEquals(1L, IwaMessage.parse(only.payload)?.varint(1))
    }

    @Test
    fun `an archive with no messages is skipped and reading carries on`() {
        val body = ProtoWriter().apply { varintField(1, 3) }.toByteArray()
        val objects = IwaChunkReader.readObjects(chunk(1, archive(7L) + archive(8L, 2 to body)))
        assertEquals(listOf(8L), objects.map { it.identifier })
    }

    private fun idsAfterGood(body: ByteArray, tail: ByteArray) =
        IwaChunkReader.readObjects(chunk(1, archive(8L, 2 to body) + tail)).map { it.identifier }

    @Test
    fun `a corrupt archive ends the read but keeps what came before it`() {
        val body = ProtoWriter().apply { varintField(1, 3) }.toByteArray()
        val overlong = archive(9L, 2 to body, declaredLengths = listOf(500L))
        val zeroInfo = bytes(0)
        assertEquals(listOf(8L), idsAfterGood(body, overlong))
        assertEquals(listOf(8L), idsAfterGood(body, zeroInfo))
        assertEquals(listOf(8L), idsAfterGood(body, bytes(0x80)))
    }

    @Test
    fun `an unknown chunk type or a chunk longer than the file yields nothing`() {
        val body = archive(8L, 2 to ProtoWriter().apply { varintField(1, 3) }.toByteArray())
        assertTrue(IwaChunkReader.readObjects(chunk(2, body)).isEmpty())
        assertTrue(IwaChunkReader.readObjects(chunk(1, body).copyOfRange(0, 6)).isEmpty())
    }
}
