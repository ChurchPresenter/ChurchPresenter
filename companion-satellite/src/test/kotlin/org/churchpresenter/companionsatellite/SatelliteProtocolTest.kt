package org.churchpresenter.companionsatellite

import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire helpers on their own: the read that tolerates a quiet socket a few times before calling
 * it dead, and the corners of the line format a well-behaved Companion never sends.
 */
class SatelliteProtocolTest {

    /** A reader whose first [quietReads] reads time out, as a socket with nothing to say does. */
    private class QuietReader(private var quietReads: Int, line: String) : BufferedReader(StringReader(line)) {
        override fun readLine(): String? {
            if (quietReads > 0) {
                quietReads--
                throw SocketTimeoutException("quiet")
            }
            return super.readLine()
        }
    }

    @Test
    fun `a socket that goes quiet briefly is waited out`() {
        assertEquals("PING", nextLine(QuietReader(quietReads = 2, line = "PING\n"), maxTimeouts = 3, timeoutMs = 10))
    }

    @Test
    fun `a socket quiet for every allowed read is a dead connection`() {
        val error = assertFailsWith<IOException> {
            nextLine(QuietReader(quietReads = 3, line = "PING\n"), maxTimeouts = 3, timeoutMs = 10)
        }
        assertEquals("No data received for 30ms — assuming dead connection", error.message)
    }

    @Test
    fun `a message with no device and a number argument is written bare`() {
        assertEquals("CHANGE-PAGE STEPS=2\n", encodeMessage("CHANGE-PAGE", null, linkedMapOf("STEPS" to 2)))
    }

    @Test
    fun `a button update whose bitmap or text is not base64 keeps the rest`() {
        val update = parseButtonUpdate(mapOf("CONTROLID" to "3", "BITMAP" to "%%%", "TEXT" to "%%%"), bitmapSize = 72)
        checkNotNull(update)
        assertEquals(3, update.index)
        assertNull(update.bitmapRgb, "a bitmap that will not decode is no bitmap, not a crash")
        assertEquals("", update.text)
    }

    @Test
    fun `runs of spaces between tokens add nothing`() {
        assertEquals(mapOf("A" to "1", "B" to "2"), parseLineParameters("A=1   B=2"))
    }

    @Test
    fun `a trailing backslash is kept as written`() {
        assertTrue(parseLineParameters("A=x\\").getValue("A").endsWith("\\"))
    }
}
