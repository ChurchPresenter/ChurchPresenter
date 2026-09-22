package org.churchpresenter.calendar.sync

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class EnvelopeTest {

    private val key = ByteArray(Envelope.KEY_BYTES) { (it * 3).toByte() }
    private val envelope = Envelope(key)
    private val plain = "Sunday Morning".toByteArray()

    @Test
    fun `seals and opens under the same key and ids`() {
        val box = envelope.seal(plain, "inst", "svc")

        assertContentEquals(plain, envelope.open(box, "inst", "svc"))
    }

    @Test
    fun `every seal uses a fresh nonce`() {
        assertNotEquals(envelope.seal(plain, "inst", "svc"), envelope.seal(plain, "inst", "svc"))
    }

    @Test
    fun `a different key does not open it`() {
        val box = envelope.seal(plain, "inst", "svc")

        assertNull(Envelope(ByteArray(Envelope.KEY_BYTES) { 7 }).open(box, "inst", "svc"))
    }

    @Test
    fun `a box moved to another service or instance does not open`() {
        val box = envelope.seal(plain, "inst", "svc")

        assertNull(envelope.open(box, "inst", "other-svc"))
        assertNull(envelope.open(box, "other-inst", "svc"))
    }

    @Test
    fun `a flipped byte does not open`() {
        val bytes = Base64.getDecoder().decode(envelope.seal(plain, "inst", "svc"))
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()

        assertNull(envelope.open(Base64.getEncoder().encodeToString(bytes), "inst", "svc"))
    }

    @Test
    fun `garbage never throws`() {
        assertNull(envelope.open("not base64!", "inst", "svc"))
        assertNull(envelope.open("", "inst", "svc"))
        assertNull(envelope.open(Base64.getEncoder().encodeToString(ByteArray(5)), "inst", "svc"))
    }

    @Test
    fun `wire form is nonce then ciphertext and tag`() {
        val bytes = Base64.getDecoder().decode(envelope.seal(plain, "inst", "svc"))

        assertEquals(12 + plain.size + 16, bytes.size)
    }

    @Test
    fun `a key of the wrong size is refused`() {
        assertFailsWith<IllegalArgumentException> { Envelope(ByteArray(16)) }
    }

    @Test
    fun `keys round trip through their url-safe encoding`() {
        val fresh = Envelope.newKey()

        assertEquals(Envelope.KEY_BYTES, fresh.size)
        assertContentEquals(fresh, Envelope.decodeKey(Envelope.encodeKey(fresh)))
        assertNull(Envelope.decodeKey("too-short"))
        assertNull(Envelope.decodeKey("***"))
    }
}
