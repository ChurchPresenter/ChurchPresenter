package org.churchpresenter.presentationengine

import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.Drawable
import org.apache.poi.sl.usermodel.PictureData.PictureType
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFRelation
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.churchpresenter.presentationengine.pptx.SlideDrawFactory
import org.openxmlformats.schemas.drawingml.x2006.main.CTGradientFillProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal
import org.openxmlformats.schemas.presentationml.x2006.main.CTConnector
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlide
import java.awt.Color
import java.awt.Rectangle
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PptxBackgroundFillAndLineGradientTest {

    private val navy = Color(0x00, 0x20, 0x60)
    private val under = Color(0x00, 0xC0, 0x00)
    private val stopA = Color(0, 0, 200)
    private val stopB = Color(200, 100, 0)
    private val halfway = Color(100, 50, 100)
    private val verticalLine = Rectangle(100, 50, 0, 400)
    private val shapeAnchor = Rectangle(100, 100, 200, 200)

    private fun rgb(color: Color) = byteArrayOf(color.red.toByte(), color.green.toByte(), color.blue.toByte())

    private fun XSLFSlide.navyBackground() {
        (xmlObject as CTSlide).cSld.addNewBg().addNewBgPr().apply {
            addNewSolidFill().addNewSrgbClr().`val` = rgb(navy)
            addNewEffectLst()
        }
    }

    private fun XMLSlideShow.redBlueBackground(slide: XSLFSlide) {
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, Color.RED.rgb)
        image.setRGB(1, 0, Color.BLUE.rgb)
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val relationId = slide.addRelation(null, XSLFRelation.IMAGES, addPicture(png, PictureType.PNG)).relationship.id
        (slide.xmlObject as CTSlide).cSld.addNewBg().addNewBgPr().apply {
            addNewBlipFill().apply {
                addNewBlip().embed = relationId
                addNewStretch().addNewFillRect()
            }
            addNewEffectLst()
        }
    }

    private fun XSLFSlide.greenShapeUnder(anchor: Rectangle) {
        createAutoShape().also {
            it.anchor = anchor
            it.setFillColor(under)
        }
    }

    /** What Designer writes: `useBgFill`, with a style whose fill POI falls back to. */
    private fun XSLFShape.fillFromBackground() {
        (xmlObject as CTShape).apply {
            useBgFill = true
            addNewStyle().addNewFillRef().apply {
                idx = 1
                addNewSchemeClr().`val` = STSchemeColorVal.ACCENT_1
            }
        }
    }

    private fun XMLSlideShow.reopened(): XMLSlideShow {
        val bytes = ByteArrayOutputStream().also { write(it) }.toByteArray()
        return XMLSlideShow(ByteArrayInputStream(bytes))
    }

    private fun deck(build: XMLSlideShow.(XSLFSlide) -> Unit): XMLSlideShow =
        XMLSlideShow().use { ppt ->
            ppt.build(ppt.createSlide())
            ppt.reopened()
        }

    private fun render(ppt: XMLSlideShow, factory: DrawFactory? = SlideDrawFactory): BufferedImage {
        val size = ppt.pageSize
        val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            if (factory != null) graphics.setRenderingHint(Drawable.DRAW_FACTORY, factory)
            DrawFactory.getInstance(graphics).getDrawable(ppt.slides[0]).draw(graphics)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun BufferedImage.colorAt(x: Int, y: Int) = Color(getRGB(x, y), true)

    private fun BufferedImage.centerOf(anchor: Rectangle) =
        colorAt(anchor.centerX.toInt(), anchor.centerY.toInt())

    private fun assertShowsBackgroundNotStyle(build: XMLSlideShow.(XSLFSlide) -> Unit) {
        deck { slide ->
            slide.navyBackground()
            build(slide)
        }.use { ppt ->
            assertNotEquals(navy, render(ppt, factory = null).centerOf(shapeAnchor), "POI must draw the style's fill")
            assertEquals(navy, render(ppt).centerOf(shapeAnchor))
        }
    }

    @Test
    fun `an auto shape filled from the background shows it, not its style's color`() =
        assertShowsBackgroundNotStyle { slide ->
            slide.createAutoShape().apply {
                anchor = shapeAnchor
                fillFromBackground()
            }
        }

    @Test
    fun `a text box filled from the background shows it, not its style's color`() =
        assertShowsBackgroundNotStyle { slide ->
            slide.createTextBox().apply {
                anchor = shapeAnchor
                fillFromBackground()
            }
        }

    @Test
    fun `a freeform filled from the background shows it, not its style's color`() =
        assertShowsBackgroundNotStyle { slide ->
            slide.createFreeform().apply {
                setPath(Path2D.Double(shapeAnchor))
                fillFromBackground()
            }
        }

    @Test
    fun `a shape filled from a picture background shows the part of it at its own place`() {
        val rightHalf = Rectangle(360, 0, 360, 540)
        deck { slide ->
            redBlueBackground(slide)
            slide.greenShapeUnder(rightHalf)
            slide.createAutoShape().apply {
                anchor = rightHalf
                fillFromBackground()
            }
        }.use { ppt ->
            val image = render(ppt)
            assertEquals(Color.BLUE, image.centerOf(rightHalf), "covers what is under it with the slide's right half")
            assertEquals(Color.RED, image.colorAt(180, 270))
        }
    }

    @Test
    fun `the background shows only inside the shape's outline`() {
        deck { slide ->
            slide.navyBackground()
            slide.greenShapeUnder(shapeAnchor)
            slide.createAutoShape().apply {
                anchor = shapeAnchor
                shapeType = ShapeType.ELLIPSE
                fillFromBackground()
            }
        }.use { ppt ->
            val image = render(ppt)
            val corner = image.colorAt(shapeAnchor.x + 5, shapeAnchor.y + 5)
            assertEquals(under, corner, "the corner is outside the ellipse")
            assertEquals(navy, image.centerOf(shapeAnchor))
        }
    }

    @Test
    fun `a shape filled from the background keeps its outline`() {
        deck { slide ->
            slide.navyBackground()
            slide.createAutoShape().apply {
                anchor = shapeAnchor
                lineColor = Color.YELLOW
                lineWidth = 10.0
                fillFromBackground()
            }
        }.use { ppt ->
            val image = render(ppt)
            assertEquals(Color.YELLOW, image.colorAt(shapeAnchor.x, shapeAnchor.centerY.toInt()))
            assertEquals(navy, image.centerOf(shapeAnchor))
        }
    }

    @Test
    fun `a shape with no filled geometry paints no background`() {
        deck { slide ->
            slide.navyBackground()
            slide.greenShapeUnder(shapeAnchor)
            slide.createAutoShape().apply {
                anchor = shapeAnchor
                shapeType = ShapeType.LINE
                fillFromBackground()
            }
        }.use { ppt ->
            assertEquals(under, render(ppt).colorAt(shapeAnchor.x + 5, shapeAnchor.maxY.toInt() - 5))
        }
    }

    @Test
    fun `a shape without the attribute keeps its own fill`() {
        deck { slide ->
            slide.navyBackground()
            slide.greenShapeUnder(shapeAnchor)
        }.use { ppt ->
            assertEquals(under, render(ppt).centerOf(shapeAnchor))
        }
    }

    private fun gradient(line: CTLineProperties, stops: List<Pair<Int, Color>>, angle: Int): CTGradientFillProperties =
        line.addNewGradFill().also { fill ->
            val list = fill.addNewGsLst()
            stops.forEach { (position, color) ->
                list.addNewGs().apply {
                    pos = position
                    addNewSrgbClr().`val` = rgb(color)
                }
            }
            fill.addNewLin().apply {
                ang = angle
                scaled = true
            }
        }

    private fun lineDeck(
        lineAnchor: Rectangle,
        stops: List<Pair<Int, Color>>,
        angle: Int = 16_200_000,
    ): XMLSlideShow = deck { slide ->
        slide.createConnector().apply {
            anchor = lineAnchor
            val line = (xmlObject as CTConnector).spPr.ln
            // createConnector sets a black line, and a solid fill outranks a gradient.
            line.unsetSolidFill()
            line.w = 127_000
            gradient(line, stops, angle)
        }
    }

    private fun verticalLineColors(stops: List<Pair<Int, Color>>): Set<Color> =
        lineDeck(verticalLine, stops).use { ppt ->
            val image = render(ppt)
            (verticalLine.y + 10 until verticalLine.y + verticalLine.height - 10)
                .map { image.colorAt(verticalLine.x, it) }
                .toSet()
        }

    @Test
    fun `a gradient on a vertical line is one flat color halfway along it`() {
        val stops = listOf(0 to stopA, 100_000 to stopB)
        assertEquals(setOf(halfway), verticalLineColors(stops))
        lineDeck(verticalLine, stops).use { ppt ->
            assertNotEquals(
                halfway,
                render(ppt, factory = null).centerOf(verticalLine),
                "POI must draw it differently, otherwise this test proves nothing",
            )
        }
    }

    @Test
    fun `halfway is measured between the stops either side of it`() {
        val stops = listOf(0 to Color.WHITE, 25_000 to stopA, 75_000 to stopB, 100_000 to Color.WHITE)
        assertEquals(setOf(halfway), verticalLineColors(stops))
    }

    @Test
    fun `stops that do not reach halfway give the nearest one`() {
        assertEquals(setOf(stopB), verticalLineColors(listOf(0 to stopA, 40_000 to stopB)))
        assertEquals(setOf(stopA), verticalLineColors(listOf(60_000 to stopA, 100_000 to stopB)))
    }

    @Test
    fun `a horizontal line keeps POI's own gradient`() {
        val horizontal = Rectangle(100, 200, 400, 0)
        lineDeck(horizontal, listOf(0 to stopA, 100_000 to stopB), angle = 0).use { ppt ->
            val ours = render(ppt)
            val pois = render(ppt, factory = null)
            val start = horizontal.x + 10
            val end = horizontal.x + horizontal.width - 10
            assertNotEquals(ours.colorAt(start, horizontal.y), ours.colorAt(end, horizontal.y), "it must be a gradient")
            for (x in listOf(start, horizontal.centerX.toInt(), end)) {
                assertEquals(pois.colorAt(x, horizontal.y), ours.colorAt(x, horizontal.y))
            }
        }
    }

    @Test
    fun `a gradient on a shape with width keeps running across it`() {
        deck { slide ->
            slide.createAutoShape().apply {
                anchor = shapeAnchor
                val fill = (xmlObject as CTShape).spPr.addNewGradFill()
                val list = fill.addNewGsLst()
                list.addNewGs().apply {
                    pos = 0
                    addNewSrgbClr().`val` = rgb(stopA)
                }
                list.addNewGs().apply {
                    pos = 100_000
                    addNewSrgbClr().`val` = rgb(stopB)
                }
                fill.addNewLin().ang = 0
            }
        }.use { ppt ->
            val image = render(ppt)
            val y = shapeAnchor.centerY.toInt()
            assertNotEquals(image.colorAt(shapeAnchor.x + 5, y), image.colorAt(shapeAnchor.maxX.toInt() - 5, y))
        }
    }
}
