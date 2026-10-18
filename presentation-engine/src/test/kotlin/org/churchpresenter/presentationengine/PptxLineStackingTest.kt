package org.churchpresenter.presentationengine

import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.DrawTextShape
import org.apache.poi.sl.draw.Drawable
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextBox
import org.churchpresenter.presentationengine.pptx.SlideDrawFactory
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Text lines stack the way PowerPoint stacks them: 1.2 times the font size apart at single
 * spacing, whatever the font, and the same distance whether a line starts a new paragraph or not.
 *
 * POI took the line height from the font's metrics (1.15 for Arial) and added an extra descent at
 * every paragraph break, so a lyrics slide — one paragraph per line — came out a fifth taller than
 * on the screen it was made on and spilled out of its box. The heights here are POI's own
 * `getTextHeight`, which is also what places middle- and bottom-anchored text.
 */
class PptxLineStackingTest {

    private val size = 20.0

    private fun withTextBox(build: (XSLFTextBox) -> Unit, check: (XSLFTextBox) -> Unit) {
        XMLSlideShow().use { ppt ->
            val box = ppt.createSlide().createTextBox()
            box.anchor = Rectangle(0, 0, 600, 400)
            box.clearText()
            build(box)
            check(box)
        }
    }

    private fun XSLFTextBox.addLines(vararg texts: String) {
        for (text in texts) {
            val run = addNewTextParagraph().addNewTextRun()
            run.setText(text)
            run.fontSize = size
            run.fontFamily = "Arial"
        }
    }

    private fun textHeight(box: XSLFTextBox, factory: DrawFactory?): Double {
        val graphics: Graphics2D = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            if (factory != null) graphics.setRenderingHint(Drawable.DRAW_FACTORY, factory)
            val drawable = DrawFactory.getInstance(graphics).getDrawable(box) as DrawTextShape
            return drawable.getTextHeight(graphics)
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun `three one-line paragraphs are three line heights tall`() {
        withTextBox({ it.addLines("First line", "Second line", "Third line") }) { box ->
            assertEquals(3 * 1.2 * size, textHeight(box, SlideDrawFactory), 0.5)
        }
    }

    @Test
    fun `POI's own stacking is taller, which is what the fix corrects`() {
        withTextBox({ it.addLines("First line", "Second line", "Third line") }) { box ->
            val poi = textHeight(box, null)
            assertTrue(
                poi > 3 * 1.2 * size + 1,
                "the fixture must reproduce POI's extra space, otherwise this test proves nothing (got $poi)",
            )
        }
    }

    @Test
    fun `a line break inside a paragraph spaces lines like a new paragraph does`() {
        val asParagraphs = mutableListOf<Double>()
        withTextBox({ it.addLines("First line", "Second line") }) { asParagraphs += textHeight(it, SlideDrawFactory) }
        withTextBox({ box ->
            val paragraph = box.addNewTextParagraph()
            paragraph.addNewTextRun().apply { setText("First line"); fontSize = size; fontFamily = "Arial" }
            paragraph.addLineBreak()
            paragraph.addNewTextRun().apply { setText("Second line"); fontSize = size; fontFamily = "Arial" }
        }) { asParagraphs += textHeight(it, SlideDrawFactory) }

        assertEquals(asParagraphs[0], asParagraphs[1], 0.5)
    }

    @Test
    fun `space before a paragraph is added on top of the line height`() {
        withTextBox({ box ->
            box.addLines("First line", "Second line")
            box.textParagraphs[1].spaceBefore = -12.0 // OOXML: negative is absolute points
        }) { box ->
            assertEquals(2 * 1.2 * size + 12, textHeight(box, SlideDrawFactory), 0.5)
        }
    }

    @Test
    fun `space before given as a percentage is that share of the line height`() {
        withTextBox({ box ->
            box.addLines("First line", "Second line")
            box.textParagraphs[1].spaceBefore = 50.0 // OOXML: positive is a percentage
        }) { box ->
            assertEquals(2.5 * 1.2 * size, textHeight(box, SlideDrawFactory), 0.5)
        }
    }

    @Test
    fun `a paragraph opening with a line break starts with an empty line`() {
        withTextBox({ box ->
            val paragraph = box.addNewTextParagraph()
            // The break carries its own size, POI's 18pt default unless set.
            paragraph.addLineBreak().fontSize = size
            paragraph.addNewTextRun().apply { setText("After the break"); fontSize = size; fontFamily = "Arial" }
        }) { box ->
            assertEquals(2 * 1.2 * size, textHeight(box, SlideDrawFactory), 0.5)
        }
    }

    @Test
    fun `text in a freeform shape stacks like text in a text box`() {
        XMLSlideShow().use { ppt ->
            val shape = ppt.createSlide().createFreeform()
            shape.path = java.awt.geom.Path2D.Double(Rectangle(0, 0, 600, 400))
            shape.anchor = Rectangle(0, 0, 600, 400)
            shape.clearText()
            for (text in listOf("First line", "Second line")) {
                val run = shape.addNewTextParagraph().addNewTextRun()
                run.setText(text)
                run.fontSize = size
                run.fontFamily = "Arial"
            }
            val graphics: Graphics2D = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
            try {
                graphics.setRenderingHint(Drawable.DRAW_FACTORY, SlideDrawFactory)
                val drawable = DrawFactory.getInstance(graphics).getDrawable(shape) as DrawTextShape
                assertEquals(2 * 1.2 * size, drawable.getTextHeight(graphics), 0.5)
            } finally {
                graphics.dispose()
            }
        }
    }
}
