package org.churchpresenter.lottiegen.lottie

import java.awt.Font
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontStyleTest {

    @Test
    fun `a face is named for its weight and slant the way Lottie spells them`() {
        assertEquals("Regular", fontStyleName(bold = false, italic = false))
        assertEquals("Bold", fontStyleName(bold = true, italic = false))
        assertEquals("Italic", fontStyleName(bold = false, italic = true))
        assertEquals("Bold Italic", fontStyleName(bold = true, italic = true))
        val white = listOf(1.0, 1.0, 1.0)
        assertEquals("Arial-Bold Italic", TextRun("x", "Arial", 10.0, 700, white, "none", italic = true).fontName)
        assertEquals("Arial-Regular", TextRun("x", "Arial", 10.0, 400, white, "none").fontName)
    }

    @Test
    fun `the style name maps back to AWT's bits`() {
        assertEquals(Font.PLAIN, awtStyleOf("Regular"))
        assertEquals(Font.BOLD, awtStyleOf("Bold"))
        assertEquals(Font.ITALIC, awtStyleOf("Italic"))
        assertEquals(Font.BOLD or Font.ITALIC, awtStyleOf("Bold Italic"))
    }

    @Test
    fun `the measurer and the builder take the slant`() {
        val upright = TextMeasurer.measure("Amazing grace", "Arial", 40f, 400, "none")
        val slanted = TextMeasurer.measure("Amazing grace", "Arial", 40f, 400, "none", italic = true)
        assertTrue(upright.width > 0 && slanted.width > 0)
        val builder = LottieBuilder(100, 100)
        assertEquals("Arial-Italic", builder.addFont("Arial", 400, italic = true))
        assertEquals("Arial-Bold", builder.addFont("Arial", 700))
        assertEquals(2, builder.fonts.size)
        assertEquals("Arial-Italic", builder.addFont("Arial", 400, italic = true), "a face is declared once")
        assertEquals(2, builder.fonts.size)
    }
}
