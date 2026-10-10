package org.churchpresenter.converter.song

import org.churchpresenter.converter.song.ProtoWriter.bytes
import org.churchpresenter.converter.song.ProtoWriter.concat
import org.churchpresenter.converter.song.ProtoWriter.key
import org.churchpresenter.converter.song.ProtoWriter.message
import org.churchpresenter.converter.song.ProtoWriter.number
import org.churchpresenter.converter.song.ProtoWriter.string
import org.churchpresenter.converter.song.ProtoWriter.varint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtoMessageTest {

    @Test
    fun `strings, numbers and nested messages are read back by field number`() {
        val parsed = ProtoMessage.parse(
            concat(string(1, "hello"), number(2, 300), message(3, string(1, "inner")), number(4, 1L shl 40)),
        )
        assertEquals("hello", parsed.string(1))
        assertEquals(300L, parsed.number(2))
        assertEquals("inner", parsed.message(3)?.string(1))
        assertEquals(1L shl 40, parsed.number(4))
    }

    @Test
    fun `a repeated field gives every occurrence in order`() {
        val parsed = ProtoMessage.parse(concat(message(5, string(1, "a")), message(5, string(1, "b"))))
        assertEquals(listOf("a", "b"), parsed.messages(5).map { it.string(1) })
    }

    @Test
    fun `absent fields come back null or empty`() {
        val parsed = ProtoMessage.parse(string(1, "only"))
        assertNull(parsed.string(9))
        assertNull(parsed.number(1))
        assertNull(parsed.message(9))
        assertTrue(parsed.bytes(9).isEmpty())
        assertNull(parsed.number(9))
    }

    @Test
    fun `fixed width fields are skipped without losing the fields after them`() {
        val parsed = ProtoMessage.parse(
            concat(key(1, 1), ByteArray(8), key(2, 5), ByteArray(4), string(3, "after")),
        )
        assertNull(parsed.number(1))
        assertNull(parsed.number(2))
        assertEquals("after", parsed.string(3))
    }

    @Test
    fun `an empty buffer is an empty message`() {
        val parsed = ProtoMessage.parse(ByteArray(0))
        assertNull(parsed.string(1))
    }

    @Test
    fun `field number zero is refused`() {
        val error = assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(number(0, 1)) }
        assertEquals("Field number 0 is not valid", error.message)
    }

    @Test
    fun `groups are refused`() {
        assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(key(1, 3)) }
        assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(key(1, 4)) }
    }

    @Test
    fun `an unknown wire type is refused`() {
        val error = assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(key(1, 6)) }
        assertEquals("Unknown wire type", error.message)
    }

    @Test
    fun `a truncated fixed width or length delimited field is refused`() {
        assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(key(1, 1) + ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(key(1, 5) + ByteArray(1)) }
        val truncated = bytes(1, "abcdef".toByteArray()).copyOf(4)
        val error = assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(truncated) }
        assertEquals("Truncated message", error.message)
    }

    @Test
    fun `a length that overflows is refused`() {
        val negative = key(1, 2) + varint(Int.MAX_VALUE.toLong())
        assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(negative) }
    }

    @Test
    fun `a varint cut off mid-number is refused`() {
        val cutOff = key(1, 0) + byteArrayOf(0x80.toByte())
        val error = assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(cutOff) }
        assertEquals("Truncated varint", error.message)
    }

    @Test
    fun `a varint longer than ten bytes is refused`() {
        val endless = key(1, 0) + ByteArray(11) { 0x80.toByte() }
        val error = assertFailsWith<IllegalArgumentException> { ProtoMessage.parse(endless) }
        assertEquals("Varint too long", error.message)
    }

    @Test
    fun `parseOrNull turns a malformed message into null and a nested malformed one is skipped`() {
        assertNull(ProtoMessage.parseOrNull(key(1, 6)))
        val outer = ProtoMessage.parseOrNull(concat(bytes(1, key(1, 6)), message(1, string(1, "ok"))))
        assertNotNull(outer)
        assertEquals(listOf("ok"), outer.messages(1).map { it.string(1) })
    }
}
