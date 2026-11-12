package org.churchpresenter.presentationengine

import org.apache.poi.sl.usermodel.PictureData
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RasterLayer
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A shape's rotation and flips on the layers a slide with builds is played from.
 *
 * The picture is four colored quarters, so which way up it was drawn reads off its corners.
 */
class RotatedShapeLayerTest {

    private val temp: File = Files.createTempDirectory("rotated-shape-test").toFile()
    private var decks = 0

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** Red top left, green top right, blue bottom left, yellow bottom right. */
    private fun quarters(): ByteArray {
        val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        UPRIGHT.zip(listOf(0 to 0, 100 to 0, 0 to 50, 100 to 50)).forEach { (color, at) ->
            graphics.color = color
            graphics.fillRect(at.first, at.second, 100, 50)
        }
        graphics.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /**
     * A slide with the picture turned by [turn]; the picture itself fades in when [pictureBuilds],
     * and otherwise stands still beside a box that does.
     */
    private fun deck(pictureBuilds: Boolean, turn: (XSLFPictureShape) -> Unit): File {
        val file = File(temp, "deck-${decks++}.pptx")
        XMLSlideShow().use { ppt ->
            ppt.pageSize = Dimension(720, 405)
            val slide = ppt.createSlide()
            val picture = slide.createPicture(ppt.addPicture(quarters(), PictureData.PictureType.PNG))
            picture.anchor = PICTURE
            turn(picture)
            val built = if (pictureBuilds) {
                picture.shapeId
            } else {
                slide.createTextBox().apply { anchor = Rectangle(20, 340, 200, 40); text = "Words" }.shapeId
            }
            Fixtures.addTiming(slide, listOf(Fixtures.TimingTarget(built.toLong())))
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    /** The colors at the picture's four corners, top left first, on the layer that holds it. */
    private fun cornersOnLayer(file: File, holdsPicture: (LayerSpec) -> Boolean): List<Color> {
        val deck = (PresentationLoader.load(file) as LoadResult.Success).deck
        return DeckRasterizer(deck, targetWidthPx = 720).use { rasterizer ->
            val layer: RasterLayer = rasterizer.rasterizeSlideLayers(0).first { holdsPicture(it.spec) }
            CORNERS.map { (x, y) -> Color(layer.image.getRGB(x - layer.offsetXPx, y - layer.offsetYPx)) }
        }
    }

    private fun assertTurns(expected: List<Color>, turn: (XSLFPictureShape) -> Unit) {
        assertEquals(expected, cornersOnLayer(deck(pictureBuilds = true, turn)) { it is LayerSpec.Shape },
            "on a layer of its own")
        assertEquals(expected, cornersOnLayer(deck(pictureBuilds = false, turn)) { it is LayerSpec.Background },
            "and in the band of shapes that stand still")
    }

    @Test
    fun `a picture that is not turned is drawn as it is`() = assertTurns(UPRIGHT) {}

    @Test
    fun `a picture rotated half way round is drawn upside down`() =
        assertTurns(UPRIGHT.reversed()) { it.rotation = HALF_TURN }

    @Test
    fun `a picture flipped top to bottom swaps its rows`() =
        assertTurns(listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN)) { it.flipVertical = true }

    @Test
    fun `a picture flipped side to side swaps its columns`() =
        assertTurns(listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE)) { it.flipHorizontal = true }

    private companion object {
        val UPRIGHT: List<Color> = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val PICTURE = Rectangle(160, 100, 400, 200)
        val CORNERS = listOf(200 to 130, 520 to 130, 200 to 270, 520 to 270)
        const val HALF_TURN = 180.0
    }
}
