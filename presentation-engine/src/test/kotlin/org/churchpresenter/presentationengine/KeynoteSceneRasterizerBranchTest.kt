package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.keynote.KeynoteScene
import org.churchpresenter.presentationengine.keynote.KeynoteSceneRasterizer
import org.churchpresenter.presentationengine.keynote.KnDrawable
import org.churchpresenter.presentationengine.keynote.KnFill
import org.churchpresenter.presentationengine.keynote.KnGeometry
import org.churchpresenter.presentationengine.keynote.KnPlacedDrawable
import org.churchpresenter.presentationengine.keynote.KnSlide
import java.awt.Color
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The drawing paths the parsed-deck suites never reach: a background image, a group's children, a
 * shape with its own outline, a translucent shape and an image fill clipped to its outline.
 *
 * Every sample is taken from a flat area of solid colour, far from any edge, so nothing here depends
 * on how a platform antialiases.
 */
class KeynoteSceneRasterizerBranchTest {

    private lateinit var bundle: File

    @BeforeTest
    fun setUp() {
        bundle = Files.createTempDirectory("cp-kn-branches").toFile().resolve("deck.key")
        File(bundle, "Data").mkdirs()
        solid(Color.BLUE).let { ImageIO.write(it, "png", File(bundle, "Data/bg.png")) }
        solid(Color.GREEN).let { ImageIO.write(it, "png", File(bundle, "Data/fill.png")) }
    }

    @AfterTest
    fun tearDown() {
        bundle.parentFile.deleteRecursively()
    }

    private fun solid(color: Color) = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().run {
            this.color = color
            fillRect(0, 0, 8, 8)
            dispose()
        }
    }

    private fun geometry(x: Double, y: Double, w: Double, h: Double) =
        KnGeometry(x, y, w, h, angle = 0.0, hFlip = false, vFlip = false)

    private fun shape(geometry: KnGeometry, fill: KnFill, path: Path2D.Double? = null, opacity: Double = 1.0) =
        KnDrawable.Shape(geometry, path, fill, strokeColor = null, strokeWidthPt = 0.0, opacity = opacity)

    private fun render(vararg drawables: KnDrawable): BufferedImage {
        val slide = KnSlide(
            index = 0,
            background = KnFill(imageFile = "bg.png"),
            drawables = drawables.mapIndexed { index, drawable -> KnPlacedDrawable(index.toLong(), drawable) },
            notes = "",
            timeline = null,
            builtDrawableIds = emptySet(),
            paragraphBuiltDrawableIds = emptySet(),
            transition = null,
            gateReason = null,
        )
        val scene = KeynoteScene(bundle, slideWidthPt = 100.0, slideHeightPt = 100.0, slides = listOf(slide))
        return KeynoteSceneRasterizer(scene).use { it.renderFinalFrame(0, targetWidthPx = 100) }
    }

    private fun BufferedImage.colorAt(x: Int, y: Int) = Color(getRGB(x, y), true)

    @Test
    fun `a background image fills the slide`() {
        val frame = render()
        assertEquals(Color.BLUE, frame.colorAt(10, 90))
    }

    @Test
    fun `a group draws its children, and a translucent one lets the background through`() {
        val child = shape(geometry(0.0, 0.0, 20.0, 20.0), KnFill(color = Color.RED), opacity = 0.5)
        val group = KnDrawable.Group(geometry(50.0, 50.0, 40.0, 40.0), listOf(KnPlacedDrawable(9, child)))

        val pixel = render(group).colorAt(60, 60)

        assertTrue(pixel.red > 64 && pixel.blue > 64, "half red over blue, got $pixel")
        assertEquals(Color.BLUE, render(group).colorAt(80, 80), "outside the child, only the background")
    }

    @Test
    fun `an image fill is clipped to the shape's own outline`() {
        // A right triangle in the unit square: its top-left half is inside, its bottom-right is not.
        val triangle = Path2D.Double().apply {
            moveTo(0.0, 0.0)
            lineTo(1.0, 0.0)
            lineTo(0.0, 1.0)
            closePath()
        }
        val frame = render(shape(geometry(0.0, 0.0, 40.0, 40.0), KnFill(imageFile = "fill.png"), path = triangle))

        assertEquals(Color.GREEN, frame.colorAt(5, 5))
        assertEquals(Color.BLUE, frame.colorAt(35, 35))
    }
}
