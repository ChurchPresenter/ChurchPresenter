package presentation.engine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal
import org.apache.xmlbeans.XmlObject
import presentation.engine.pptx.PowerPointDeckSupport
import presentation.engine.pptx.stripUnsupportedHighlights
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import javax.xml.namespace.QName
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A text highlight written as anything other than `<a:srgbClr>` used to take the whole slide down.
 *
 * POI reads the highlight as `getHighlight().getSrgbClr()` and hands the result to
 * `DrawPaint.applyColorTransform` unchecked, so a theme-coloured highlight — `<a:schemeClr>`, which
 * is what PowerPoint writes when the highlight comes from the theme palette — throws
 * `NullPointerException: … because "rgbCol" is null` from inside `DrawTextParagraph.breakText`.
 * That is thrown while measuring text, i.e. before anything is painted, so the slide renders as
 * nothing at all rather than as a slide missing one highlight.
 *
 * The fixture is built with POI and read back from disk so the XML is what a real deck carries,
 * and the render test drives the same `DrawFactory` path `DeckRasterizer.renderPowerPointSlide`
 * uses — pinning the crash itself, not a proxy for it.
 */
class PptxHighlightSanitizerTest {

    private val temp: File = Files.createTempDirectory("highlight-test").toFile()
    private val dml = "http://schemas.openxmlformats.org/drawingml/2006/main"

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** A one-slide deck whose only run carries a highlight of [colourElement] (e.g. `schemeClr`). */
    private fun deckWithHighlight(colourElement: String, colourValue: String): File {
        val file = File(temp, "highlight-${System.nanoTime()}.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val box = slide.createTextBox()
            box.anchor = Rectangle(50, 50, 400, 100)
            val run: XSLFTextRun = box.addNewTextParagraph().addNewTextRun()
            run.setText("Highlighted")
            // The run POI just built already carries an rPr (it holds `lang`); a second one would be
            // invalid and simply ignored by every typed reader, including the one under test.
            val runProperties = (run.xmlObject as CTRegularTextRun).let { it.rPr ?: it.addNewRPr() }
            val highlight = runProperties.addNewHighlight()
            if (colourElement == "srgbClr") {
                highlight.addNewSrgbClr().setVal(colourValue.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            } else {
                highlight.addNewSchemeClr().setVal(STSchemeColorVal.Enum.forString(colourValue))
            }
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    private fun highlightCount(xml: XmlObject): Int {
        var count = 0
        val cursor = xml.newCursor()
        try {
            while (cursor.toNextToken() != org.apache.xmlbeans.XmlCursor.TokenType.NONE) {
                if (cursor.isStart && cursor.name == QName(dml, "highlight")) count++
            }
        } finally {
            cursor.dispose()
        }
        return count
    }

    @Test
    fun `a scheme-coloured highlight is removed`() {
        XMLSlideShow(deckWithHighlight("schemeClr", "accent1").inputStream()).use { ppt ->
            assertEquals(1, highlightCount(ppt.slides[0].xmlObject), "the fixture must carry the highlight")

            assertEquals(1, stripUnsupportedHighlights(ppt), "the unreadable highlight is taken out")
            assertEquals(0, highlightCount(ppt.slides[0].xmlObject))
        }
    }

    @Test
    fun `an rgb highlight is left alone`() {
        XMLSlideShow(deckWithHighlight("srgbClr", "FFFF00").inputStream()).use { ppt ->
            assertEquals(0, stripUnsupportedHighlights(ppt), "POI reads this form, so it stays")
            assertEquals(1, highlightCount(ppt.slides[0].xmlObject))
        }
    }

    @Test
    fun `a deck with a scheme-coloured highlight renders instead of throwing`() {
        val file = deckWithHighlight("schemeClr", "accent1")

        // Unsanitized: the render throws, which is the reported crash.
        XMLSlideShow(file.inputStream()).use { ppt ->
            val failure = runCatching { renderFirstSlide(ppt) }.exceptionOrNull()
            assertTrue(
                failure is NullPointerException,
                "the fixture must reproduce the POI crash, otherwise this test proves nothing " +
                    "(got ${failure ?: "no failure"})",
            )
        }

        // Through the loader every render path goes through, it renders.
        PowerPointDeckSupport.open(file).use { show ->
            val image = renderFirstSlide(show as XMLSlideShow)
            assertTrue(image.width > 0 && image.height > 0)
        }
    }

    /** The same POI drawing path `DeckRasterizer.renderPowerPointSlide` takes. */
    private fun renderFirstSlide(ppt: XMLSlideShow): java.awt.image.BufferedImage {
        val size = ppt.pageSize
        val image = java.awt.image.BufferedImage(size.width, size.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            org.apache.poi.sl.draw.DrawFactory.getInstance(graphics)
                .getDrawable(ppt.slides[0])
                .draw(graphics)
        } finally {
            graphics.dispose()
        }
        return image
    }
}
