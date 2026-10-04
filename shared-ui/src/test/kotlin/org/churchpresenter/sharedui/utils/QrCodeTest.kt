package org.churchpresenter.sharedui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The QR code the Q&A output and the remote dialogs show: a square image of the requested size for
 * valid input, and null rather than an exception for input ZXing rejects, so a bad URL never takes
 * down the screen drawing it.
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
    fun `an empty payload fails soft instead of crashing the output`() {
        assertNull(generateQRCodeBitmap("", 240), "an empty payload must yield null, not throw")
    }

    @Test
    fun `a size that cannot hold an image fails soft`() {
        assertNull(generateQRCodeBitmap("https://example.church/qa", 0), "a zero size must yield null")
        assertNull(generateQRCodeBitmap("https://example.church/qa", -5), "a negative size must yield null")
    }

    @Test
    fun `the code is drawn only in the two colours asked for`() {
        val fg = 0xFF112233.toInt()
        val bg = 0xFFEEDDCC.toInt()
        val pixels = assertNotNull(generateQRCodeBitmap("https://example.church", 64, fg, bg)).toPixelMap()
        val seen = buildSet {
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) add(pixels[x, y])
        }
        assertEquals(setOf(Color(fg), Color(bg)), seen, "only the foreground and background may be drawn")
    }

    @Test
    fun `the quiet zone is background and a finder pattern is foreground`() {
        val pixels = assertNotNull(generateQRCodeBitmap("https://example.church", 200)).toPixelMap()
        assertEquals(Color.White, pixels[0, 0], "the corner is the one-module margin, so background")
        val inFinder = (0 until pixels.width / 4).any { pixels[it, it] == Color.Black }
        assertTrue(inFinder, "the top-left finder pattern must put dark modules on the diagonal")
    }
}
