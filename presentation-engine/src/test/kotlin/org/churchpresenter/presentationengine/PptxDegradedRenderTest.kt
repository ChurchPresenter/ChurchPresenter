package org.churchpresenter.presentationengine

import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFAutoShape
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import javax.xml.namespace.QName
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PptxDegradedRenderTest {

    private val temp: File = Files.createTempDirectory("pptx-degraded-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun deckWithBrokenShape(): File {
        val file = File(temp, "broken.pptx")
        XMLSlideShow().use { show ->
            show.pageSize = Dimension(960, 540)
            repeat(2) {
                val slide = show.createSlide()
                val good = slide.createAutoShape()
                good.shapeType = ShapeType.RECT
                good.anchor = Rectangle(10, 10, 100, 100)
                good.fillColor = Color.RED
                val broken = slide.createAutoShape()
                broken.shapeType = ShapeType.RECT
                broken.anchor = Rectangle(300, 300, 100, 100)
                breakGeometry(broken)
            }
            file.outputStream().use { show.write(it) }
        }
        return file
    }

    private fun breakGeometry(shape: XSLFAutoShape) {
        val cursor = shape.xmlObject.newCursor()
        try {
            cursor.toChild(QName("http://schemas.openxmlformats.org/presentationml/2006/main", "spPr"))
            cursor.toChild(QName("http://schemas.openxmlformats.org/drawingml/2006/main", "prstGeom"))
            cursor.setAttributeText(QName("prst"), "notAShape")
        } finally {
            cursor.close()
        }
    }

    @Test
    fun `a shape that cannot be drawn costs only itself and is reported once`() {
        val deck = (PresentationLoader.load(deckWithBrokenShape()) as LoadResult.Success).deck
        val reports = mutableListOf<SlideRenderDegradation>()
        DeckRasterizer(deck, targetWidthPx = 480, onDegraded = { reports += it }).use { rasterizer ->
            val first = rasterizer.renderFinalFrame(0)
            rasterizer.renderFinalFrame(1)
            assertEquals(Color.RED.rgb, first.getRGB(30, 30), "the healthy shape still drew")
        }
        val report = reports.single()
        assertEquals(0, report.slideIndex)
        assertEquals(2, report.shapesTotal)
        assertEquals(1, report.shapesSkipped)
        assertTrue(report.cause.isNotEmpty())
        assertEquals(listOf("XSLFAutoShape"), report.skippedShapes)
        assertTrue(report.failureOrigin.isNotEmpty())
        assertEquals("", report.recordLimit)
    }
}
