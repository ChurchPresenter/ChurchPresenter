package org.churchpresenter.omt

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val HALF_RED = 0x80FF0000.toInt()

class OmtSenderTest {

    @Test
    fun `a refused sender is not open and sends nothing`() {
        val lib = FakeOmtLibrary(refuseNames = setOf("Taken"))
        val sender = OmtSender(lib, "Taken", OmtOutputMode.ALPHA, 30)
        assertFalse(sender.open())
        assertFalse(sender.isOpen)
        sender.send(IntArray(1) { HALF_RED }, 1, 1)
        assertTrue(lib.sent.isEmpty())
        assertEquals(0, sender.receiverCount())
        assertEquals("", sender.address())
    }

    @Test
    fun `opening twice creates one source, told what made it`() {
        val lib = FakeOmtLibrary()
        val sender = OmtSender(lib, "Lyrics", OmtOutputMode.ALPHA, 30, OmtQuality.MEDIUM, "ChurchPresenter", "2.0")
        assertTrue(sender.open())
        assertTrue(sender.open())
        assertEquals(listOf("Lyrics"), lib.created)
        assertEquals(listOf(OmtQuality.MEDIUM), lib.qualities)
        assertEquals(listOf(Triple("ChurchPresenter", "ChurchPresenter", "2.0")), lib.senderInformation)
        assertEquals("FAKEHOST (Lyrics)", sender.address())
    }

    @Test
    fun `no product leaves the library's own sender information`() {
        val lib = FakeOmtLibrary()
        OmtSender(lib, "Lyrics", OmtOutputMode.ALPHA, 30).open()
        assertTrue(lib.senderInformation.isEmpty())
    }

    @Test
    fun `alpha mode sends transparency, flagged`() {
        val lib = FakeOmtLibrary()
        val sender = OmtSender(lib, "Lower", OmtOutputMode.ALPHA, 30).apply { open() }
        sender.send(intArrayOf(HALF_RED), 1, 1)
        val frame = lib.sent.single()
        assertTrue(frame.alpha)
        assertContentEquals(byteArrayOf(0, 0, 0xFF.toByte(), 0x80.toByte()), frame.bytes)
        assertEquals(30_000, frame.frameRateN)
        assertEquals(1_000, frame.frameRateD)
    }

    @Test
    fun `fill mode flattens alpha and says so`() {
        val lib = FakeOmtLibrary()
        val sender = OmtSender(lib, "Fill", OmtOutputMode.FILL, 25).apply { open() }
        sender.send(intArrayOf(HALF_RED), 1, 1)
        val frame = lib.sent.single()
        assertFalse(frame.alpha)
        assertEquals(0xFF.toByte(), frame.bytes[3])
        assertEquals(25_000, frame.frameRateN)
    }

    @Test
    fun `a blank frame is fully transparent in alpha mode and black in fill mode`() {
        val alphaLib = FakeOmtLibrary()
        OmtSender(alphaLib, "a", OmtOutputMode.ALPHA, 30).apply { open() }.sendBlank(2, 1)
        assertTrue(alphaLib.sent.single().bytes.all { it == 0.toByte() }, "every byte zero, alpha included")

        val fillLib = FakeOmtLibrary()
        OmtSender(fillLib, "f", OmtOutputMode.FILL, 30).apply { open() }.sendBlank(1, 1)
        assertContentEquals(byteArrayOf(0, 0, 0, 0xFF.toByte()), fillLib.sent.single().bytes)
    }

    @Test
    fun `a blank frame of no size is not sent`() {
        val lib = FakeOmtLibrary()
        OmtSender(lib, "x", OmtOutputMode.ALPHA, 30).apply { open() }.sendBlank(0, 10)
        assertTrue(lib.sent.isEmpty())
    }

    @Test
    fun `a zero-sized frame is not sent`() {
        val lib = FakeOmtLibrary()
        OmtSender(lib, "x", OmtOutputMode.ALPHA, 30).apply { open() }.send(IntArray(0), 0, 0)
        assertTrue(lib.sent.isEmpty())
    }

    @Test
    fun `a size change regrows the buffer`() {
        val lib = FakeOmtLibrary()
        val sender = OmtSender(lib, "x", OmtOutputMode.ALPHA, 30).apply { open() }
        sender.send(IntArray(1), 1, 1)
        sender.send(IntArray(4), 2, 2)
        assertEquals(listOf(4, 16), lib.sent.map { it.bytes.size })
    }

    @Test
    fun `the receiver count is the library's connection count`() {
        val lib = FakeOmtLibrary().apply { connections = 2 }
        val sender = OmtSender(lib, "x", OmtOutputMode.ALPHA, 30).apply { open() }
        assertEquals(2, sender.receiverCount())
    }

    @Test
    fun `closing takes the source off the network once`() {
        val lib = FakeOmtLibrary()
        val sender = OmtSender(lib, "x", OmtOutputMode.ALPHA, 30).apply { open() }
        sender.close()
        sender.close()
        assertEquals(1, lib.destroyed.size)
        assertFalse(sender.isOpen)
    }

    @Test
    fun `the frame rate is kept exact over a thousand, and never zero`() {
        assertEquals(30_000, OmtSender.frameRateNumerator(30))
        assertEquals(1_000, OmtSender.frameRateNumerator(0))
    }
}
