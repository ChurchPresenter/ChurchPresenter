package org.churchpresenter.presentationengine

import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.DrawTextShape
import org.apache.poi.sl.draw.Drawable
import org.apache.poi.sl.usermodel.AutoNumberingScheme
import org.apache.poi.sl.usermodel.TextParagraph
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextBox
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.churchpresenter.presentationengine.pptx.SlideDrawFactory
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.font.TextLayout
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.font.TextAttribute
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lines break where PowerPoint breaks them.
 *
 * Java's line breaker counts the spaces that end a line into its width, so a line whose words fit
 * the box exactly lost its last word to the next line; PowerPoint lets those spaces hang past the
 * edge. The line count is read from the box's text height — one line is 1.2 times the size.
 */
class PptxLineBreakingTest {

    private val size = 20.0
    private val lineHeight = 1.2 * size

    private fun graphics(): Graphics2D =
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().also {
            it.setRenderingHint(Drawable.DRAW_FACTORY, SlideDrawFactory)
        }

    /** The width [text] draws at in the box's font, measured the way the box will measure it. */
    private fun visibleWidth(text: String): Double {
        val graphics = graphics()
        try {
            val font = Font(Font.SANS_SERIF, Font.PLAIN, size.toInt())
            return TextLayout(text, font, graphics.fontRenderContext).visibleAdvance.toDouble()
        } finally {
            graphics.dispose()
        }
    }

    private fun withBox(width: Double, text: String, style: (XSLFTextRun) -> Unit = {}, check: (XSLFTextBox) -> Unit) {
        XMLSlideShow().use { ppt ->
            val box = ppt.createSlide().createTextBox()
            box.anchor = Rectangle2D.Double(0.0, 0.0, width, 400.0)
            box.leftInset = 0.0
            box.rightInset = 0.0
            box.clearText()
            val run = box.addNewTextParagraph().addNewTextRun()
            run.setText(text)
            run.fontSize = size
            run.fontFamily = Font.SANS_SERIF
            style(run)
            check(box)
        }
    }

    private fun lineCount(box: XSLFTextBox): Int {
        val graphics = graphics()
        try {
            val drawable = DrawFactory.getInstance(graphics).getDrawable(box) as DrawTextShape
            return Math.round(drawable.getTextHeight(graphics) / lineHeight).toInt()
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun `a line whose words fit keeps its last word though its trailing space does not fit`() {
        // POI's wrapping width is the box's plus one point; leave less than a space beyond that.
        val width = ceil(visibleWidth("mmm mmm")) + 0.5 - 1
        withBox(width, "mmm mmm mmm") { box ->
            assertEquals(2, lineCount(box), "\"mmm mmm\" fits; only its trailing space is past the edge")
        }
    }

    @Test
    fun `a line whose last word does not fit still wraps it`() {
        val width = visibleWidth("mmm mmm") - 4
        withBox(width, "mmm mmm mmm") { box ->
            assertEquals(3, lineCount(box))
        }
    }

    @Test
    fun `a word wider than the box is broken inside it`() {
        val width = visibleWidth("mmmmmmmm") * 0.6
        withBox(width, "mmmmmmmm") { box ->
            assertEquals(2, lineCount(box))
        }
    }

    @Test
    fun `an empty text box still measures one line`() {
        withBox(200.0, "") { box ->
            assertEquals(1, lineCount(box))
        }
    }

    private fun render(width: Double, text: String, style: (XSLFTextRun) -> Unit): BufferedImage {
        var image: BufferedImage? = null
        withBox(width, text, style) { box ->
            box.anchor = Rectangle(0, 0, width.toInt(), 60)
            val canvas = BufferedImage(width.toInt(), 60, BufferedImage.TYPE_INT_ARGB)
            val graphics = canvas.createGraphics()
            try {
                graphics.setRenderingHint(Drawable.DRAW_FACTORY, SlideDrawFactory)
                DrawFactory.getInstance(graphics).getDrawable(box).draw(graphics)
            } finally {
                graphics.dispose()
            }
            image = canvas
        }
        return image!!
    }

    private fun BufferedImage.pixels(): IntArray = getRGB(0, 0, width, height, null, 0, width)

    @Test
    fun `each run style still reaches the drawn text`() {
        val plain = render(300.0, "Styled", style = {}).pixels()
        val styles = mapOf<String, (XSLFTextRun) -> Unit>(
            "bold" to { it.isBold = true },
            "italic" to { it.isItalic = true },
            "underline" to { it.isUnderlined = true },
            "strikethrough" to { it.isStrikethrough = true },
            "superscript" to { it.isSuperscript = true },
            "subscript" to { it.isSubscript = true },
            "colour" to { it.setFontColor(Color.RED) },
            "highlight" to { run ->
                val properties = (run.xmlObject as CTRegularTextRun).let { it.rPr ?: it.addNewRPr() }
                properties.addNewHighlight().addNewSrgbClr().setVal(byteArrayOf(-1, -1, 0))
            },
        )
        for ((name, style) in styles) {
            val styled = render(300.0, "Styled", style).pixels()
            assertFalse(styled.contentEquals(plain), "$name changes nothing on the slide")
        }
    }

    @Test
    fun `a hyperlinked run renders like its text`() {
        XMLSlideShow().use { ppt ->
            val box = ppt.createSlide().createTextBox()
            box.anchor = Rectangle(0, 0, 300, 60)
            box.clearText()
            val run = box.addNewTextParagraph().addNewTextRun()
            run.setText("Linked")
            run.fontSize = size
            run.createHyperlink().address = "https://example.org"
            val graphics = graphics()
            try {
                val drawable = DrawFactory.getInstance(graphics).getDrawable(box) as DrawTextShape
                assertEquals(lineHeight, drawable.getTextHeight(graphics), 0.5)
            } finally {
                graphics.dispose()
            }
        }
    }

    /** The rightmost column of [image] that holds a drawn pixel. */
    private fun rightmostInk(image: BufferedImage): Int =
        (image.width - 1 downTo 0).firstOrNull { x ->
            (0 until image.height).any { y -> image.getRGB(x, y) ushr 24 != 0 }
        } ?: -1

    @Test
    fun `a justified line reaches the right edge of its box`() {
        val text = "mmm mmm mmm mmm mmm mmm"
        val width = 200.0
        val ragged = rightmostInk(render(width, text) {})
        val justified = rightmostInk(
            render(width, text) { run -> run.paragraph.textAlign = TextParagraph.TextAlign.JUSTIFY },
        )
        assertTrue(justified > ragged, "justified text stretches its first line (ragged $ragged, justified $justified)")
        assertTrue(justified >= width - 4, "its last glyph reaches the box edge (got $justified of $width)")
    }

    @Test
    fun `a character no font can show is still drawn, in the fallback font`() {
        // U+E000 is private use: no installed font has it, so the fallback range is taken.
        withBox(200.0, "a\uE000b") { box ->
            assertEquals(1, lineCount(box))
        }
        assertTrue(render(200.0, "a\uE000b") {}.pixels().any { it ushr 24 != 0 })
    }

    @Test
    fun `numbered paragraphs draw their numbers`() {
        fun numbered(on: Boolean): IntArray {
            var pixels = IntArray(0)
            XMLSlideShow().use { ppt ->
                val box = ppt.createSlide().createTextBox()
                box.anchor = Rectangle(0, 0, 300, 120)
                box.clearText()
                repeat(2) {
                    val paragraph = box.addNewTextParagraph()
                    if (on) {
                        paragraph.isBullet = true
                        paragraph.setBulletAutoNumber(AutoNumberingScheme.arabicPeriod, 1)
                        paragraph.leftMargin = 30.0
                        paragraph.indent = -30.0
                    }
                    paragraph.addNewTextRun().apply { setText("Item"); fontSize = size }
                }
                val canvas = BufferedImage(300, 120, BufferedImage.TYPE_INT_ARGB)
                val graphics = canvas.createGraphics()
                try {
                    graphics.setRenderingHint(Drawable.DRAW_FACTORY, SlideDrawFactory)
                    DrawFactory.getInstance(graphics).getDrawable(box).draw(graphics)
                } finally {
                    graphics.dispose()
                }
                pixels = canvas.pixels()
            }
            return pixels
        }
        assertFalse(numbered(on = true).contentEquals(numbered(on = false)))
    }

    @Test
    fun `a Calibri run is set to Calibri's widths, not the JVM's default face`() {
        // Calibri ships only with Office; the engine carries Carlito, drawn to its metrics. Either
        // way the line below measures as Carlito does — before the substitute reached the drawn
        // font, a missing Calibri was set in Dialog, wider, and the line wrapped.
        val text = "Grace and peace to you from God our Father"
        val graphics = graphics()
        val width = try {
            SlideFontRegistry.resolveFamily("Calibri") // registers the bundled fonts
            val font = Font(
                mapOf(
                    TextAttribute.FAMILY to "Carlito",
                    TextAttribute.SIZE to size.toFloat(),
                    TextAttribute.KERNING to TextAttribute.KERNING_ON,
                ),
            )
            TextLayout(text, font, graphics.fontRenderContext).visibleAdvance.toDouble()
        } finally {
            graphics.dispose()
        }
        withBox(width + 2, text, style = { it.fontFamily = "Calibri" }) { box ->
            val measuring = graphics()
            try {
                measuring.setRenderingHint(Drawable.FONT_HANDLER, SlideFontRegistry.drawFontManager)
                val drawable = DrawFactory.getInstance(measuring).getDrawable(box) as DrawTextShape
                assertEquals(lineHeight, drawable.getTextHeight(measuring), 0.5, "one line at Calibri's widths")
            } finally {
                measuring.dispose()
            }
        }
    }
}
