package presentation.engine

import presentation.engine.keynote.KeynoteDeckParser
import presentation.engine.keynote.KeynoteSceneRasterizer
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drawing a parsed Keynote scene into a raster frame.
 *
 * This runs headless — `BufferedImage` + `Graphics2D` need no display, which is why the rasterizer
 * is testable at all despite Keynote rendering sounding display-bound.
 *
 * Assertions are invariants, never pixel comparisons: font rasterization differs across the three
 * target platforms, so "this pixel is #FF0000" would pass on one machine and fail on another. What
 * is asserted instead is the geometry contract (output size follows the requested width at the
 * scene's aspect ratio) and that ink lands inside the bounds a drawable declared and nowhere else —
 * which is what a misapplied scale or a wrong origin actually breaks.
 *
 * The fixture graph comes from a real deck dumped with `dumpKeynote`; see [KeynoteDeckParserTest].
 */
class KeynoteRasterizerTest {

    private val temp: File = Files.createTempDirectory("keynote-raster-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Fixture graph (see KeynoteDeckParserTest for provenance) ──────────────

    private fun reference(id: Long) = Fixtures.ProtoWriter().apply { varintField(1, id) }.toByteArray()

    private fun geometry(x: Float, y: Float, w: Float, h: Float): ByteArray {
        val point = Fixtures.ProtoWriter().apply { floatField(1, x); floatField(2, y) }.toByteArray()
        val size = Fixtures.ProtoWriter().apply { floatField(1, w); floatField(2, h) }.toByteArray()
        return Fixtures.ProtoWriter().apply { bytesField(1, point); bytesField(2, size) }.toByteArray()
    }

    /** ShapeInfo → ShapeArchive → DrawableArchive → Geometry, plus an optional text storage. */
    private fun textShape(x: Float, y: Float, w: Float, h: Float, storageId: Long?): ByteArray {
        val drawableArchive = Fixtures.ProtoWriter().apply { bytesField(1, geometry(x, y, w, h)) }.toByteArray()
        val shapeArchive = Fixtures.ProtoWriter().apply { bytesField(1, drawableArchive) }.toByteArray()
        return Fixtures.ProtoWriter().apply {
            bytesField(1, shapeArchive)
            if (storageId != null) bytesField(4, reference(storageId))
        }.toByteArray()
    }

    private fun sceneOf(vararg drawables: Triple<Long, Int, ByteArray>, width: Float = 1920f, height: Float = 1080f) : File {
        val ids = drawables.map { it.first }
        val size = Fixtures.ProtoWriter().apply { floatField(1, width); floatField(2, height) }.toByteArray()
        val tree = Fixtures.ProtoWriter().apply { bytesField(2, reference(100L)) }.toByteArray()
        val objects = listOf(
            Triple(1L, 1, Fixtures.ProtoWriter().apply { bytesField(2, reference(2L)) }.toByteArray()),
            Triple(2L, 2, Fixtures.ProtoWriter().apply { bytesField(3, tree); bytesField(4, size) }.toByteArray()),
            Triple(100L, 4, Fixtures.ProtoWriter().apply { bytesField(2, reference(200L)) }.toByteArray()),
            Triple(200L, 5, Fixtures.ProtoWriter().apply {
                ids.forEach { bytesField(7, reference(it)) }
                ids.forEach { bytesField(42, reference(it)) }
            }.toByteArray()),
        ) + drawables.toList()
        return Fixtures.writeKeynoteDir(Files.createTempDirectory(temp.toPath(), "deck").toFile(), objects)
    }

    private fun render(file: File, targetWidthPx: Int = 480): BufferedImage {
        val scene = assertNotNull(KeynoteDeckParser.parse(file), "the fixture parses")
        return KeynoteSceneRasterizer(scene).use { it.renderFinalFrame(0, targetWidthPx) }
    }

    private fun hasInk(image: BufferedImage, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        for (y in y0 until y1) for (x in x0 until x1) {
            if (image.getRGB(x, y) ushr 24 != 0) return true
        }
        return false
    }

    // ── Output geometry ───────────────────────────────────────────────────────

    @Test
    fun `the rendered frame matches the requested width at the scene's aspect ratio`() {
        val image = render(sceneOf(Triple(300L, 2011, textShape(0f, 0f, 100f, 100f, null))), targetWidthPx = 480)
        assertEquals(480, image.width)
        assertEquals(270, image.height, "1920x1080 scaled to 480 wide")
    }

    @Test
    fun `a non-16-by-9 scene keeps its own aspect ratio`() {
        val file = sceneOf(Triple(300L, 2011, textShape(0f, 0f, 10f, 10f, null)), width = 1024f, height = 768f)
        val image = render(file, targetWidthPx = 512)
        assertEquals(512, image.width)
        assertEquals(384, image.height)
    }

    @Test
    fun `a tiny target width still yields a valid image rather than a zero-sized one`() {
        val image = render(sceneOf(Triple(300L, 2011, textShape(0f, 0f, 10f, 10f, null))), targetWidthPx = 1)
        assertTrue(image.width >= 1 && image.height >= 1, "got ${image.width}x${image.height}")
    }

    @Test
    fun `rendering every slide of a deck succeeds`() {
        val file = sceneOf(Triple(300L, 2011, textShape(0f, 0f, 100f, 100f, 301L)), )
        val scene = assertNotNull(KeynoteDeckParser.parse(file))
        KeynoteSceneRasterizer(scene).use { rasterizer ->
            scene.slides.indices.forEach { i ->
                assertNotNull(rasterizer.renderFinalFrame(i, 320), "slide $i rendered")
            }
        }
    }

    // ── Where the ink lands ───────────────────────────────────────────────────

    @Test
    fun `a drawable's ink lands inside the bounds it declared`() {
        // 1920x1080 scene rendered 480 wide => scale 0.25. A shape at (800,400) sized 400x200
        // occupies (200,100)..(300,150) in the output.
        val file = sceneOf(
            Triple(300L, 2011, textShape(800f, 400f, 400f, 200f, 301L)),
            Triple(301L, 2001, Fixtures.ProtoWriter().apply { stringField(3, "Coverage") }.toByteArray()),
        )
        val image = render(file, targetWidthPx = 480)

        assertTrue(hasInk(image, 200, 100, 300, 150), "something was drawn where the shape sits")
        assertTrue(!hasInk(image, 0, 0, 150, 90), "and nothing spilled into the far corner")
    }

    @Test
    fun `moving a drawable moves its ink`() {
        // The strongest available check that the origin is applied rather than ignored: the same
        // shape at two positions must not produce identical output.
        fun frameFor(x: Float, y: Float): BufferedImage = render(
            sceneOf(
                Triple(300L, 2011, textShape(x, y, 400f, 200f, 301L)),
                Triple(301L, 2001, Fixtures.ProtoWriter().apply { stringField(3, "Coverage") }.toByteArray()),
            ),
            targetWidthPx = 480,
        )
        val left = frameFor(100f, 100f)
        val right = frameFor(1200f, 700f)

        assertTrue(hasInk(left, 25, 25, 150, 75), "the left-placed shape drew on the left")
        assertTrue(hasInk(right, 300, 175, 460, 240), "the right-placed shape drew on the right")
        assertTrue(!hasInk(right, 25, 25, 120, 60), "and not where the other one was")
    }

    @Test
    fun `an empty slide renders a frame rather than failing`() {
        val size = Fixtures.ProtoWriter().apply { floatField(1, 1920f); floatField(2, 1080f) }.toByteArray()
        val tree = Fixtures.ProtoWriter().apply { bytesField(2, reference(100L)) }.toByteArray()
        val file = Fixtures.writeKeynoteDir(
            Files.createTempDirectory(temp.toPath(), "empty").toFile(),
            listOf(
                Triple(1L, 1, Fixtures.ProtoWriter().apply { bytesField(2, reference(2L)) }.toByteArray()),
                Triple(2L, 2, Fixtures.ProtoWriter().apply { bytesField(3, tree); bytesField(4, size) }.toByteArray()),
                Triple(100L, 4, Fixtures.ProtoWriter().apply { bytesField(2, reference(200L)) }.toByteArray()),
                Triple(200L, 5, Fixtures.ProtoWriter().toByteArray()),
            ),
        )
        val image = render(file, targetWidthPx = 320)
        assertEquals(320, image.width, "a slide with no content is still a full frame")
    }

    @Test
    fun `several drawables all reach the frame`() {
        val file = sceneOf(
            Triple(300L, 2011, textShape(0f, 0f, 400f, 200f, 310L)),
            Triple(301L, 2011, textShape(1400f, 800f, 400f, 200f, 311L)),
            Triple(310L, 2001, Fixtures.ProtoWriter().apply { stringField(3, "First") }.toByteArray()),
            Triple(311L, 2001, Fixtures.ProtoWriter().apply { stringField(3, "Second") }.toByteArray()),
        )
        val image = render(file, targetWidthPx = 480)
        assertTrue(hasInk(image, 0, 0, 100, 50), "the first drawable is present")
        assertTrue(hasInk(image, 350, 200, 450, 250), "and so is the second")
    }

    @Test
    fun `the rasterizer can be closed twice without complaint`() {
        val scene = assertNotNull(
            KeynoteDeckParser.parse(sceneOf(Triple(300L, 2011, textShape(0f, 0f, 10f, 10f, null))))
        )
        val rasterizer = KeynoteSceneRasterizer(scene)
        rasterizer.renderFinalFrame(0, 160)
        rasterizer.close()
        rasterizer.close()
    }
}
