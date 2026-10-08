package org.churchpresenter.controlin

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OscTest {

    private fun oscString(text: String): ByteArray {
        val raw = text.toByteArray()
        return raw + ByteArray(4 - raw.size % 4)
    }

    @Test
    fun `a message with every argument kind survives encode and parse`() {
        val blob = byteArrayOf(1, 2, 3)
        val sent = OscMessage("/cp/test", listOf(7, 1.5f, "hello", 9L, 2.5, true, false, null, blob))
        val got = Osc.parse(Osc.encode(sent)).single()
        assertEquals("/cp/test", got.address)
        assertEquals(listOf<Any?>(7, 1.5f, "hello", 9L, 2.5, true, false, null), got.args.dropLast(1))
        assertContentEquals(blob, got.args.last() as ByteArray)
    }

    @Test
    fun `strings are padded to four bytes with their terminator`() {
        val packet = Osc.encode(OscMessage("/ab"))
        assertEquals(0, packet.size % 4)
        assertEquals("/ab\u0000,\u0000\u0000\u0000", String(packet))
    }

    @Test
    fun `a bundle yields every message in it, nested bundles included`() {
        val inner = Osc.encode(OscMessage("/inner"))
        val first = Osc.encode(OscMessage("/first", listOf(1)))
        val nested = ByteArrayOutputStream().apply {
            write(oscString("#bundle")); write(ByteArray(8))
            write(ByteBuffer.allocate(4).putInt(inner.size).array()); write(inner)
        }.toByteArray()
        val bundle = ByteArrayOutputStream().apply {
            write(oscString("#bundle")); write(ByteArray(8))
            write(ByteBuffer.allocate(4).putInt(first.size).array()); write(first)
            write(ByteBuffer.allocate(4).putInt(nested.size).array()); write(nested)
        }.toByteArray()
        assertEquals(listOf("/first", "/inner"), Osc.parse(bundle).map { it.address })
    }

    @Test
    fun `garbage is dropped, never thrown`() {
        assertTrue(Osc.parse(ByteArray(0)).isEmpty())
        assertTrue(Osc.parse(byteArrayOf(1, 2, 3)).isEmpty())
        assertTrue(Osc.parse("no slash\u0000\u0000\u0000\u0000".toByteArray()).isEmpty())
        assertTrue(Osc.parse(oscString("#bundle") + ByteArray(8) + byteArrayOf(0, 0, 9, 9)).isEmpty())
        assertTrue(Osc.parse(oscString("#nope")).isEmpty())
    }

    @Test
    fun `a message cut off in its arguments keeps the address with what was readable`() {
        val cut = oscString("/x") + oscString(",iq") + byteArrayOf(0, 0, 0, 5)
        val got = Osc.parse(cut).single()
        assertEquals("/x", got.address)
        assertEquals(listOf<Any?>(5), got.args)
    }

    @Test
    fun `a message with no type tags has no arguments`() {
        assertEquals(OscMessage("/old"), Osc.parse(oscString("/old")).single())
        assertEquals(OscMessage("/old"), Osc.parse(oscString("/old") + oscString("text")).single())
    }

    @Test
    fun `an argument reads as an int, else a float, else text, and blank is none`() {
        assertEquals(5, Osc.argumentOf(" 5 "))
        assertEquals(1.5f, Osc.argumentOf("1.5"))
        assertEquals("go", Osc.argumentOf("go"))
        assertNull(Osc.argumentOf("  "))
    }

    @Test
    fun `the first number argument is what a mapping sees as the value`() {
        assertEquals(3, Osc.firstNumber(listOf("x", 3, 4)))
        assertEquals(2, Osc.firstNumber(listOf(2.9f)))
        assertEquals(2, Osc.firstNumber(listOf(2.9)))
        assertEquals(ANY, Osc.firstNumber(listOf("x", null)))
        assertEquals(
            ControlEvent(TriggerKinds.OSC, address = "/a", value = 8),
            OscMessage("/a", listOf(8)).toEvent(),
        )
    }
}
