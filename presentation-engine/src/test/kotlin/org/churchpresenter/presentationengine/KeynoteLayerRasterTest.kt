package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.Fixtures.ProtoWriter
import org.churchpresenter.presentationengine.keynote.KeynoteDeckParser
import org.churchpresenter.presentationengine.keynote.KeynoteSceneRasterizer
import org.churchpresenter.presentationengine.keynote.KnFields as F
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RectPt
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rendering **one layer at a time** out of a Keynote scene.
 *
 * The whole-frame render is what an unanimated slide shows; per-layer rendering is what makes a
 * build possible, and it is a different code path with its own arithmetic. Each layer comes back
 * as a transparent image the size of its own bounds plus the offset it belongs at, so two mistakes
 * are possible and neither shows up in a full-frame test: a layer that paints the *other* shapes
 * into its own image (they would appear twice, and would not animate), and a layer whose
 * offset/size arithmetic is off (the shape jumps when the animation starts).
 */
class KeynoteLayerRasterTest {

    private val temp: File = Files.createTempDirectory("keynote-layer-raster-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private val slideW = 1920.0
    private val slideH = 1080.0

    // ── Fixture graph (provenance: KeynoteDeckParserTest) ─────────────────────

    private fun reference(id: Long) = ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, id) }.toByteArray()

    private fun geometry(x: Float, y: Float, w: Float, h: Float): ByteArray {
        val point = ProtoWriter().apply { floatField(1, x); floatField(2, y) }.toByteArray()
        val size = ProtoWriter().apply { floatField(1, w); floatField(2, h) }.toByteArray()
        return ProtoWriter().apply { bytesField(1, point); bytesField(2, size) }.toByteArray()
    }

    private fun color(r: Float, g: Float, b: Float) = ProtoWriter().apply {
        varintField(F.COLOR_MODEL, 1)
        floatField(F.COLOR_R, r)
        floatField(F.COLOR_G, g)
        floatField(F.COLOR_B, b)
        floatField(F.COLOR_A, 1f)
    }.toByteArray()

    private fun filledStyle(rgb: ByteArray) = ProtoWriter().apply {
        bytesField(
            F.SHAPE_STYLE_PROPERTIES,
            ProtoWriter().apply {
                bytesField(F.SHAPE_PROPS_FILL, ProtoWriter().apply { bytesField(F.FILL_COLOR, rgb) }.toByteArray())
            }.toByteArray(),
        )
    }.toByteArray()

    private fun shape(styleId: Long, x: Float, y: Float, w: Float, h: Float) = ProtoWriter().apply {
        bytesField(F.SHAPE_SUPER, ProtoWriter().apply { bytesField(1, geometry(x, y, w, h)) }.toByteArray())
        bytesField(F.SHAPE_STYLE, reference(styleId))
    }.toByteArray()

    private fun slideStyle(rgb: ByteArray) = ProtoWriter().apply {
        bytesField(
            F.SLIDE_STYLE_PROPERTIES,
            ProtoWriter().apply {
                bytesField(
                    F.SLIDE_STYLE_PROPS_FILL,
                    ProtoWriter().apply { bytesField(F.FILL_COLOR, rgb) }.toByteArray(),
                )
            }.toByteArray(),
        )
    }.toByteArray()

    /** Builds a one-slide scene from [drawables], optionally with a slide background style. */
    private fun scene(
        drawables: List<Triple<Long, Int, ByteArray>>,
        extra: List<Triple<Long, Int, ByteArray>> = emptyList(),
        backgroundStyleId: Long? = null,
    ): org.churchpresenter.presentationengine.keynote.KeynoteScene {
        val size = ProtoWriter().apply {
            floatField(1, slideW.toFloat())
            floatField(2, slideH.toFloat())
        }.toByteArray()
        val tree = ProtoWriter().apply { bytesField(2, reference(100L)) }.toByteArray()
        val slidePayload = ProtoWriter().apply {
            if (backgroundStyleId != null) bytesField(F.SLIDE_STYLE, reference(backgroundStyleId))
            drawables.forEach { bytesField(7, reference(it.first)) }
            drawables.forEach { bytesField(42, reference(it.first)) }
        }.toByteArray()
        val objects = listOf(
            Triple(1L, F.TYPE_KN_DOCUMENT, ProtoWriter().apply { bytesField(2, reference(2L)) }.toByteArray()),
            Triple(
                2L,
                F.TYPE_KN_SHOW,
                ProtoWriter().apply { bytesField(3, tree); bytesField(4, size) }.toByteArray(),
            ),
            Triple(100L, F.TYPE_KN_SLIDE_NODE, ProtoWriter().apply { bytesField(2, reference(200L)) }.toByteArray()),
            Triple(200L, F.TYPE_KN_SLIDE, slidePayload),
        ) + drawables + extra
        val dir = Fixtures.writeKeynoteDir(Files.createTempDirectory(temp.toPath(), "scene").toFile(), objects)
        return assertNotNull(KeynoteDeckParser.parse(dir), "the fixture scene did not parse")
    }

    /** A red box at (100,100) 400×200 and a green one at (800,600) 300×150. */
    private fun twoBoxScene() = scene(
        drawables = listOf(
            Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, 100f, 100f, 400f, 200f)),
            Triple(301L, F.TYPE_TSD_SHAPE, shape(401L, 800f, 600f, 300f, 150f)),
        ),
        extra = listOf(
            Triple(400L, F.TYPE_TSD_SHAPE_STYLE, filledStyle(color(1f, 0f, 0f))),
            Triple(401L, F.TYPE_TSD_SHAPE_STYLE, filledStyle(color(0f, 1f, 0f))),
        ),
    )

    private fun opaquePixels(image: BufferedImage): Int {
        var count = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 != 0) count++
            }
        }
        return count
    }

    // ── Shape layers ──────────────────────────────────────────────────────────

    @Test
    fun `a shape layer is the size of its own bounds and offset to where it belongs`() {
        KeynoteSceneRasterizer(twoBoxScene()).use { rasterizer ->
            val spec = LayerSpec.Shape(
                id = "kn-301",
                zIndex = 1,
                boundsPt = RectPt(800.0, 600.0, 300.0, 150.0),
                shapeIndex = 1,
                initiallyVisible = true,
            )
            // Half scale: 1920pt of slide rendered 960px wide.
            val layer = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 960)

            assertEquals(400, layer.offsetXPx, "800pt at half scale")
            assertEquals(300, layer.offsetYPx, "600pt at half scale")
            assertEquals(150, layer.image.width, "300pt wide at half scale")
            assertEquals(75, layer.image.height, "150pt tall at half scale")
        }
    }

    @Test
    fun `a shape layer paints only its own shape`() {
        KeynoteSceneRasterizer(twoBoxScene()).use { rasterizer ->
            val spec = LayerSpec.Shape("kn-300", 1, RectPt(100.0, 100.0, 400.0, 200.0), 0, true)
            val layer = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 1920)

            val centre = layer.image.getRGB(layer.image.width / 2, layer.image.height / 2)
            assertEquals(Color.RED.rgb, centre, "the layer's own shape is drawn")
            assertEquals(
                layer.image.width * layer.image.height,
                opaquePixels(layer.image),
                "a full-bleed box fills its layer; anything less means the other shape stole space",
            )
        }
    }

    @Test
    fun `layers of the two shapes do not overlap in the frame`() {
        KeynoteSceneRasterizer(twoBoxScene()).use { rasterizer ->
            val first = rasterizer.rasterizeLayer(
                0,
                LayerSpec.Shape("kn-300", 1, RectPt(100.0, 100.0, 400.0, 200.0), 0, true),
                targetWidthPx = 1920,
            )
            val second = rasterizer.rasterizeLayer(
                0,
                LayerSpec.Shape("kn-301", 2, RectPt(800.0, 600.0, 300.0, 150.0), 1, true),
                targetWidthPx = 1920,
            )
            assertTrue(
                first.offsetXPx + first.image.width <= second.offsetXPx ||
                    first.offsetYPx + first.image.height <= second.offsetYPx,
                "each layer covers only its own shape's rectangle",
            )
        }
    }

    // ── Background layers ─────────────────────────────────────────────────────

    @Test
    fun `the bottom background layer covers the whole slide and carries its fill`() {
        val withBackground = scene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, 100f, 100f, 200f, 100f))),
            extra = listOf(
                Triple(400L, F.TYPE_TSD_SHAPE_STYLE, filledStyle(color(1f, 0f, 0f))),
                Triple(500L, F.TYPE_KN_SLIDE_STYLE, slideStyle(color(0f, 0f, 1f))),
            ),
            backgroundStyleId = 500L,
        )
        KeynoteSceneRasterizer(withBackground).use { rasterizer ->
            val spec = LayerSpec.Background("bg", 0, RectPt(0.0, 0.0, slideW, slideH), shapeIndexes = emptyList())
            val layer = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 960)

            assertEquals(0, layer.offsetXPx)
            assertEquals(960, layer.image.width)
            assertEquals(540, layer.image.height)
            assertEquals(Color.BLUE.rgb, layer.image.getRGB(10, 10), "the slide's own fill is the backdrop")
        }
    }

    @Test
    fun `a background band above the bottom one draws its shapes without the slide fill`() {
        val withBackground = scene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, 0f, 0f, 1920f, 1080f))),
            extra = listOf(
                Triple(400L, F.TYPE_TSD_SHAPE_STYLE, filledStyle(color(1f, 0f, 0f))),
                Triple(500L, F.TYPE_KN_SLIDE_STYLE, slideStyle(color(0f, 0f, 1f))),
            ),
            backgroundStyleId = 500L,
        )
        KeynoteSceneRasterizer(withBackground).use { rasterizer ->
            val spec = LayerSpec.Background("bg-1", 1, RectPt(0.0, 0.0, slideW, slideH), shapeIndexes = listOf(0))
            val layer = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 480)

            assertEquals(
                Color.RED.rgb,
                layer.image.getRGB(10, 10),
                "a band above the bottom paints its own shapes, and the slide fill is not repainted under them",
            )
        }
    }

    // ── Scaling ───────────────────────────────────────────────────────────────

    @Test
    fun `a layer scales with the requested width`() {
        KeynoteSceneRasterizer(twoBoxScene()).use { rasterizer ->
            val spec = LayerSpec.Shape("kn-300", 1, RectPt(100.0, 100.0, 400.0, 200.0), 0, true)
            val small = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 480)
            val large = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 1920)

            assertEquals(100, small.image.width, "400pt at quarter scale")
            assertEquals(400, large.image.width, "400pt at full scale")
            assertEquals(25, small.offsetXPx)
            assertEquals(100, large.offsetXPx)
        }
    }

    @Test
    fun `a degenerate layer still produces an image rather than a zero-sized one`() {
        KeynoteSceneRasterizer(twoBoxScene()).use { rasterizer ->
            val spec = LayerSpec.Shape("kn-300", 1, RectPt(100.0, 100.0, 0.0, 0.0), 0, true)
            val layer = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 960)

            assertTrue(layer.image.width >= 1 && layer.image.height >= 1, "an empty rect must not crash the render")
        }
    }

    // ── Layer kinds the Keynote path does not produce ─────────────────────────

    @Test
    fun `a layer kind this path never plans is rejected loudly`() {
        KeynoteSceneRasterizer(twoBoxScene()).use { rasterizer ->
            val spec = LayerSpec.StaticComposite("flat", 0, RectPt(0.0, 0.0, slideW, slideH))
            assertFailsWith<IllegalArgumentException> { rasterizer.rasterizeLayer(0, spec, targetWidthPx = 960) }
        }
    }

    // ── Media assets ──────────────────────────────────────────────────────────

    @Test
    fun `a media layer extracts the movie asset it points at`() {
        // The video has to become a real file on disk for the player to open; a layer that renders
        // but hands back no file is a poster frame that never starts playing.
        val movieBytes = "not really a movie, but a real file".toByteArray()
        val posterBytes = ByteArrayOutputStream().also { out ->
            val image = BufferedImage(16, 9, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { paint = Color.WHITE; fillRect(0, 0, 16, 9); dispose() }
            ImageIO.write(image, "png", out)
        }.toByteArray()

        val movie = ProtoWriter().apply {
            bytesField(F.MOVIE_SUPER, ProtoWriter().apply { bytesField(1, geometry(0f, 0f, 640f, 360f)) }.toByteArray())
            bytesField(F.MOVIE_DATA, ProtoWriter().apply { varintField(F.DATA_REFERENCE_IDENTIFIER, 5L) }.toByteArray())
            bytesField(
                F.MOVIE_POSTER,
                ProtoWriter().apply { varintField(F.DATA_REFERENCE_IDENTIFIER, 6L) }.toByteArray(),
            )
        }.toByteArray()
        val metadata = ProtoWriter().apply {
            for ((id, name) in listOf(5L to "clip.mov", 6L to "poster.png")) {
                bytesField(
                    F.PACKAGE_METADATA_DATAS,
                    ProtoWriter().apply {
                        varintField(F.DATA_INFO_IDENTIFIER, id)
                        stringField(F.DATA_INFO_FILE_NAME, name)
                    }.toByteArray(),
                )
            }
        }.toByteArray()

        val movieScene = scene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_MOVIE, movie)),
            extra = listOf(Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, metadata)),
        )
        // The bundle the scene was written into needs the assets themselves alongside its Index.
        val bundle = movieScene.file
        File(bundle, "Data").mkdirs()
        File(bundle, "Data/clip.mov").writeBytes(movieBytes)
        File(bundle, "Data/poster.png").writeBytes(posterBytes)

        KeynoteSceneRasterizer(movieScene).use { rasterizer ->
            val spec = LayerSpec.Media(
                id = "kn-300",
                zIndex = 1,
                boundsPt = RectPt(0.0, 0.0, 640.0, 360.0),
                shapeIndex = 0,
                contentRectPt = RectPt(0.0, 0.0, 640.0, 360.0),
                mediaFile = null,
            )
            val layer = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 960)

            val extracted = assertNotNull(
                (layer.spec as LayerSpec.Media).mediaFile,
                "the movie asset was not extracted",
            )
            assertTrue(extracted.isFile, "the extracted path has to exist: $extracted")
            assertContentEqualsBytes(movieBytes, extracted.readBytes())
        }
    }

    @Test
    fun `an asset the package does not hold extracts to nothing rather than failing`() {
        KeynoteSceneRasterizer(twoBoxScene()).use { rasterizer ->
            assertNull(rasterizer.extractDataFile("absent.mov"))
        }
    }

    private fun assertContentEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size, "extracted file has the wrong length")
        assertTrue(expected.contentEquals(actual), "extracted file has different content")
    }
}
