package org.churchpresenter.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The QR encoder three surfaces share — the Q&A join code, the presentation remote and the canvas.
 *
 * It must produce a square image of the requested size for valid input, and fail soft rather than
 * throw on input the encoder rejects: every caller draws inside a composition, where an exception
 * takes the output window down.
 */
class QrCodeTest {

    @Test
    fun `a QR code is generated at the requested square size`() {
        val bitmap = assertNotNull(
            generateQRCodeBitmap("https://example.church/qa", 240),
            "a valid URL must produce a scannable code",
        )
        assertEquals(240, bitmap.width, "a QR code must be square at the requested size")
        assertEquals(240, bitmap.height)
    }

    @Test
    fun `an unencodable input fails soft instead of crashing the output`() {
        // The zxing encoder rejects an empty string; the output window must not take the exception.
        assertNull(generateQRCodeBitmap("", 240), "an empty payload must yield null, not throw")
    }

    @Test
    fun `a payload too long for the requested size fails soft too`() {
        // 4096 characters cannot fit a 21x21 module grid, which is the smallest a QR code has.
        assertNull(generateQRCodeBitmap("x".repeat(4096), 21), "an overlong payload must yield null")
    }

    @Test
    fun `the chosen colours are the ones drawn`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val bitmap = assertNotNull(
            generateQRCodeBitmap("https://example.church/qa", 240, foregroundArgb = red, backgroundArgb = blue),
        )
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        val distinct = pixels.toSet()
        assertEquals(setOf(red, blue), distinct, "a code is drawn in exactly the two colours it was given")
    }

    @Test
    fun `the code is drawn on the requested background, not left blank`() {
        val bitmap = assertNotNull(generateQRCodeBitmap("https://example.church/qa", 240))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        val dark = pixels.count { it == 0xFF000000.toInt() }
        assertTrue(dark > 0, "a code with no dark modules would not scan")
        assertTrue(dark < pixels.size, "and one with no light modules would not either")
    }
}
