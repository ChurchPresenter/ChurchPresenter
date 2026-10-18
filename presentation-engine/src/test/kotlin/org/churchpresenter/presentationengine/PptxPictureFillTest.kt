package org.churchpresenter.presentationengine

import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.Drawable
import org.apache.poi.sl.usermodel.PictureData.PictureType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFRelation
import org.apache.xmlbeans.XmlObject
import org.churchpresenter.presentationengine.pptx.PowerPointDeckSupport
import org.churchpresenter.presentationengine.pptx.SlideDrawFactory
import org.churchpresenter.presentationengine.pptx.addMissingFillRects
import org.openxmlformats.schemas.drawingml.x2006.main.CTStretchInfoProperties
import org.openxmlformats.schemas.drawingml.x2006.main.STShapeType
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlide
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Two picture fills POI draws differently from PowerPoint, both from a real sermon deck.
 *
 * A bare `<a:stretch/>` — the stock themes' background picture carries one — is read by POI as no
 * stretch, so the picture is tiled at its own size instead of filling the slide. And a picture
 * cropped to a shape (`<a:custGeom>` or a preset other than `rect`) is drawn over its whole
 * anchor, covering what the shape was cut away to show.
 *
 * The decks are built with POI and read back from disk so the XML is what a real deck carries.
 */
class PptxPictureFillTest {

    private val temp: File = Files.createTempDirectory("picture-fill-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** A 2×1 PNG: red on the left, blue on the right — which half shows says how it was placed. */
    private fun redBluePng(): ByteArray {
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, Color.RED.rgb)
        image.setRGB(1, 0, Color.BLUE.rgb)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun solidPng(colour: Color): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 4) for (y in 0 until 4) image.setRGB(x, y, colour.rgb)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /** A one-slide deck whose background is [redBluePng] under a bare `<a:stretch/>`. */
    private fun deckWithBareStretchBackground(): File {
        val file = File(temp, "background.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val picture = ppt.addPicture(redBluePng(), PictureType.PNG)
            val relationId = slide.addRelation(null, XSLFRelation.IMAGES, picture).relationship.id
            val bgPr = (slide.xmlObject as CTSlide).cSld.addNewBg().addNewBgPr()
            val fill = bgPr.addNewBlipFill()
            fill.addNewBlip().embed = relationId
            fill.addNewStretch()
            bgPr.addNewEffectLst()
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    /** A one-slide deck holding one red picture at [anchor], with preset geometry [geometry]. */
    private fun deckWithPicture(anchor: Rectangle, geometry: STShapeType.Enum): File {
        val file = File(temp, "picture-$geometry.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val shape = slide.createPicture(ppt.addPicture(solidPng(Color.RED), PictureType.PNG))
            shape.anchor = anchor
            (shape.xmlObject as CTPicture).spPr.prstGeom.prst = geometry
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    private fun render(ppt: XMLSlideShow, factory: DrawFactory? = null): BufferedImage {
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

    private fun BufferedImage.colourAt(x: Int, y: Int): Color = Color(getRGB(x, y), true)

    private fun stretchCount(xml: XmlObject, withFillRect: Boolean): Int =
        xml.selectPath("declare namespace a='http://schemas.openxmlformats.org/drawingml/2006/main' .//a:stretch")
            .count { (it as CTStretchInfoProperties).isSetFillRect == withFillRect }

    @Test
    fun `a bare stretch gains a fill rect once`() {
        XMLSlideShow(deckWithBareStretchBackground().inputStream()).use { ppt ->
            val slideXml = ppt.slides[0].xmlObject
            assertEquals(1, stretchCount(slideXml, withFillRect = false), "the fixture must carry a bare stretch")

            assertEquals(1, addMissingFillRects(ppt))
            assertEquals(0, stretchCount(slideXml, withFillRect = false))
            assertEquals(1, stretchCount(slideXml, withFillRect = true))

            assertEquals(0, addMissingFillRects(ppt), "a stretch that has its fill rect is left alone")
        }
    }

    @Test
    fun `a background picture under a bare stretch fills the slide`() {
        val file = deckWithBareStretchBackground()
        val rightHalf = { image: BufferedImage -> image.colourAt(image.width * 3 / 4, image.height / 2) }

        XMLSlideShow(file.inputStream()).use { ppt ->
            assertNotEquals(
                Color.BLUE,
                rightHalf(render(ppt)),
                "the fixture must reproduce POI's unstretched picture, otherwise this test proves nothing",
            )
        }

        PowerPointDeckSupport.open(file).use { show ->
            val image = render(show as XMLSlideShow)
            assertEquals(Color.RED, image.colourAt(image.width / 4, image.height / 2))
            assertEquals(Color.BLUE, rightHalf(image))
        }
    }

    @Test
    fun `a picture cropped to a shape is drawn only inside it`() {
        val anchor = Rectangle(100, 100, 200, 200)
        XMLSlideShow(deckWithPicture(anchor, STShapeType.ELLIPSE).inputStream()).use { ppt ->
            val corner = { image: BufferedImage -> image.colourAt(anchor.x + 5, anchor.y + 5) }
            assertEquals(Color.RED, corner(render(ppt)), "POI's own drawing fills the whole anchor")

            val image = render(ppt, SlideDrawFactory)
            assertNotEquals(Color.RED, corner(image), "the corner is outside the ellipse")
            assertEquals(Color.RED, image.colourAt(anchor.centerX.toInt(), anchor.centerY.toInt()))
        }
    }

    @Test
    fun `a rectangular picture is drawn over its whole anchor`() {
        val anchor = Rectangle(100, 100, 200, 200)
        XMLSlideShow(deckWithPicture(anchor, STShapeType.RECT).inputStream()).use { ppt ->
            val image = render(ppt, SlideDrawFactory)
            assertEquals(Color.RED, image.colourAt(anchor.x + 5, anchor.y + 5))
        }
    }
}
