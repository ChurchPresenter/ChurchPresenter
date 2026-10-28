package org.churchpresenter.app.churchpresenter.remote

import androidx.compose.ui.graphics.Color
import org.churchpresenter.server.DrawingFrame
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A page from a phone's canvas, decoded for the outputs, and how the operator's prompt names it. */
class DrawingPageTest {

    private fun png(width: Int, height: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        return assertNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes
    }

    @Test
    fun `a page decodes to its picture with its paper around it`() {
        val page = assertNotNull(decodeDrawingPage(DrawingFrame("ipad", png(4, 3), "#FFFFFF", "Sketch")))

        assertEquals(4, page.bitmap.width)
        assertEquals(3, page.bitmap.height)
        assertEquals(Color.White, page.paper)
    }

    @Test
    fun `bytes that only look like a picture are not a page`() {
        val signatureOnly = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

        assertNull(decodeDrawingPage(DrawingFrame("ipad", signatureOnly, null, "")))
    }

    @Test
    fun `the paper is read from #RRGGBB`() {
        assertEquals(Color(0xFF1E293B), paperColour("#1E293B"))
        assertEquals(Color(0xFF1E293B), paperColour("1E293B"))
    }

    @Test
    fun `no paper, or one that is not a color, is black like every other empty output`() {
        assertEquals(Color.Black, paperColour(null))
        assertEquals(Color.Black, paperColour("#FFF"))
        assertEquals(Color.Black, paperColour("#GGGGGG"))
    }

    @Test
    fun `the prompt names the drawing, or calls it a drawing when it has no title`() {
        assertEquals("Psalm 23" to "Drawing", drawingEventLabel("  Psalm 23 "))
        assertEquals("Drawing" to "Drawing", drawingEventLabel("   "))
    }
}
