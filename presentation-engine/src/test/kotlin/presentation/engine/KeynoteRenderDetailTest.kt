package presentation.engine

import presentation.engine.Fixtures.ProtoWriter
import presentation.engine.keynote.KeynoteDeckParser
import presentation.engine.keynote.KeynoteScene
import presentation.engine.keynote.KeynoteSceneRasterizer
import presentation.engine.keynote.KnDrawable
import presentation.engine.keynote.KnFields as F
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rendering detail: rotation, outlines and strokes, embedded image assets, and the **zipped**
 * `.key` container.
 *
 * The zip is the half of the container story [KeynotePackageDirectoryTest] does not touch, and it
 * matters at render time rather than at parse time: assets have to come back out of the archive
 * before AWT can draw them, and an image that fails to extract leaves a hole in an otherwise fine
 * slide. Rotation is the other silent one — a shape drawn without its angle is in the right place,
 * the right size and the wrong orientation, which no bounds assertion notices.
 */
class KeynoteRenderDetailTest {

    private val temp: File = Files.createTempDirectory("keynote-render-detail-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private val slideW = 1000f
    private val slideH = 1000f

    // ── Graph builders ────────────────────────────────────────────────────────

    private fun reference(id: Long) = ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, id) }.toByteArray()

    private fun dataReference(id: Long) =
        ProtoWriter().apply { varintField(F.DATA_REFERENCE_IDENTIFIER, id) }.toByteArray()

    private fun geometry(x: Float, y: Float, w: Float, h: Float, angle: Float? = null) = ProtoWriter().apply {
        bytesField(
            F.GEOMETRY_POSITION,
            ProtoWriter().apply { floatField(F.POINT_X, x); floatField(F.POINT_Y, y) }.toByteArray(),
        )
        bytesField(
            F.GEOMETRY_SIZE,
            ProtoWriter().apply { floatField(F.SIZE_WIDTH, w); floatField(F.SIZE_HEIGHT, h) }.toByteArray(),
        )
        if (angle != null) floatField(F.GEOMETRY_ANGLE, angle)
    }.toByteArray()

    private fun drawableSuper(geometry: ByteArray) =
        ProtoWriter().apply { bytesField(F.DRAWABLE_GEOMETRY, geometry) }.toByteArray()

    private fun color(r: Float, g: Float, b: Float) = ProtoWriter().apply {
        varintField(F.COLOR_MODEL, 1)
        floatField(F.COLOR_R, r)
        floatField(F.COLOR_G, g)
        floatField(F.COLOR_B, b)
        floatField(F.COLOR_A, 1f)
    }.toByteArray()

    private fun style(fill: ByteArray? = null, strokeColor: ByteArray? = null, strokeWidth: Float? = null) =
        ProtoWriter().apply {
            bytesField(
                F.SHAPE_STYLE_PROPERTIES,
                ProtoWriter().apply {
                    if (fill != null) {
                        bytesField(
                            F.SHAPE_PROPS_FILL,
                            ProtoWriter().apply { bytesField(F.FILL_COLOR, fill) }.toByteArray(),
                        )
                    }
                    if (strokeColor != null || strokeWidth != null) {
                        bytesField(
                            F.SHAPE_PROPS_STROKE,
                            ProtoWriter().apply {
                                if (strokeColor != null) bytesField(F.STROKE_COLOR, strokeColor)
                                if (strokeWidth != null) floatField(F.STROKE_WIDTH, strokeWidth)
                            }.toByteArray(),
                        )
                    }
                }.toByteArray(),
            )
        }.toByteArray()

    private fun shape(styleId: Long?, geometry: ByteArray) = ProtoWriter().apply {
        bytesField(F.SHAPE_SUPER, drawableSuper(geometry))
        if (styleId != null) bytesField(F.SHAPE_STYLE, reference(styleId))
    }.toByteArray()

    private fun image(dataId: Long, geometry: ByteArray) = ProtoWriter().apply {
        bytesField(F.IMAGE_SUPER, drawableSuper(geometry))
        bytesField(F.IMAGE_DATA, dataReference(dataId))
    }.toByteArray()

    private fun packageMetadata(files: Map<Long, String>) = ProtoWriter().apply {
        files.forEach { (id, name) ->
            bytesField(
                F.PACKAGE_METADATA_DATAS,
                ProtoWriter().apply {
                    varintField(F.DATA_INFO_IDENTIFIER, id)
                    stringField(F.DATA_INFO_FILE_NAME, name)
                }.toByteArray(),
            )
        }
    }.toByteArray()

    private fun sceneObjects(drawables: List<Triple<Long, Int, ByteArray>>, extra: List<Triple<Long, Int, ByteArray>>) =
        listOf(
            Triple(1L, F.TYPE_KN_DOCUMENT, ProtoWriter().apply { bytesField(2, reference(2L)) }.toByteArray()),
            Triple(
                2L,
                F.TYPE_KN_SHOW,
                ProtoWriter().apply {
                    bytesField(3, ProtoWriter().apply { bytesField(2, reference(100L)) }.toByteArray())
                    bytesField(
                        4,
                        ProtoWriter().apply {
                            floatField(F.SIZE_WIDTH, slideW)
                            floatField(F.SIZE_HEIGHT, slideH)
                        }.toByteArray(),
                    )
                }.toByteArray(),
            ),
            Triple(100L, F.TYPE_KN_SLIDE_NODE, ProtoWriter().apply { bytesField(2, reference(200L)) }.toByteArray()),
            Triple(
                200L,
                F.TYPE_KN_SLIDE,
                ProtoWriter().apply {
                    drawables.forEach { bytesField(7, reference(it.first)) }
                    drawables.forEach { bytesField(42, reference(it.first)) }
                }.toByteArray(),
            ),
        ) + drawables + extra

    /** A `.key` **directory** bundle, with optional files under Data/. */
    private fun directoryScene(
        drawables: List<Triple<Long, Int, ByteArray>>,
        extra: List<Triple<Long, Int, ByteArray>> = emptyList(),
        assets: Map<String, ByteArray> = emptyMap(),
    ): KeynoteScene {
        val dir = Fixtures.writeKeynoteDir(
            Files.createTempDirectory(temp.toPath(), "dir").toFile(),
            sceneObjects(drawables, extra),
        )
        if (assets.isNotEmpty()) {
            File(dir, "Data").mkdirs()
            assets.forEach { (name, bytes) -> File(dir, "Data/$name").writeBytes(bytes) }
        }
        return assertNotNull(KeynoteDeckParser.parse(dir), "the directory fixture did not parse")
    }

    /** The same document written as a **zip** `.key` instead. */
    private fun zippedScene(
        drawables: List<Triple<Long, Int, ByteArray>>,
        extra: List<Triple<Long, Int, ByteArray>> = emptyList(),
        assets: Map<String, ByteArray> = emptyMap(),
    ): KeynoteScene {
        val file = File(Files.createTempDirectory(temp.toPath(), "zip").toFile(), "fixture.key")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Index/Test.iwa"))
            zip.write(Fixtures.buildIwa(sceneObjects(drawables, extra)))
            zip.closeEntry()
            assets.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry("Data/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return assertNotNull(KeynoteDeckParser.parse(file), "the zipped fixture did not parse")
    }

    private fun png(color: Color, w: Int = 40, h: Int = 40): ByteArray {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { paint = color; fillRect(0, 0, w, h); dispose() }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun render(scene: KeynoteScene, widthPx: Int = 200): BufferedImage =
        KeynoteSceneRasterizer(scene).use { it.renderFinalFrame(0, widthPx) }

    private fun colorsIn(image: BufferedImage): Set<Int> {
        val seen = mutableSetOf<Int>()
        for (y in 0 until image.height) for (x in 0 until image.width) seen += image.getRGB(x, y) and 0xFFFFFF
        return seen
    }

    // ── The zipped container ──────────────────────────────────────────────────

    @Test
    fun `a zipped document parses the same as the same document as a folder`() {
        val drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, geometry(0f, 0f, 100f, 100f))))
        val extra = listOf(Triple(400L, F.TYPE_TSD_SHAPE_STYLE, style(fill = color(1f, 0f, 0f))))

        val fromZip = zippedScene(drawables, extra)
        val fromDir = directoryScene(drawables, extra)

        assertEquals(fromDir.slides.size, fromZip.slides.size)
        assertEquals(
            assertIs<KnDrawable.Shape>(fromDir.slides.single().drawables.single().drawable).fill?.color,
            assertIs<KnDrawable.Shape>(fromZip.slides.single().drawables.single().drawable).fill?.color,
        )
    }

    @Test
    fun `an image inside a zipped document is extracted and drawn`() {
        val scene = zippedScene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(5L, geometry(0f, 0f, 1000f, 1000f)))),
            extra = listOf(Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(5L to "photo.png")))),
            assets = mapOf("photo.png" to png(Color.GREEN)),
        )
        val frame = render(scene)
        assertEquals(
            Color.GREEN.rgb and 0xFFFFFF,
            frame.getRGB(frame.width / 2, frame.height / 2) and 0xFFFFFF,
            "the image did not come out of the archive onto the slide",
        )
    }

    @Test
    fun `an image the archive does not actually hold leaves the slide otherwise intact`() {
        // The metadata names a file that is not in the zip: the draw is skipped, the render is not.
        val scene = zippedScene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(5L, geometry(0f, 0f, 1000f, 1000f)))),
            extra = listOf(Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(5L to "missing.png")))),
        )
        val frame = render(scene)
        assertTrue(frame.width > 0 && frame.height > 0, "a missing asset must not fail the render")
    }

    @Test
    fun `extracting the same asset twice hands back the same file`() {
        val scene = zippedScene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(5L, geometry(0f, 0f, 100f, 100f)))),
            extra = listOf(Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(5L to "photo.png")))),
            assets = mapOf("photo.png" to png(Color.BLUE)),
        )
        KeynoteSceneRasterizer(scene).use { rasterizer ->
            val first = assertNotNull(rasterizer.extractDataFile("photo.png"))
            val second = assertNotNull(rasterizer.extractDataFile("photo.png"))
            assertEquals(first, second, "extraction is cached rather than repeated per frame")
            assertTrue(first.isFile)
        }
    }

    @Test
    fun `an extracted asset is cleaned up when the rasterizer closes`() {
        val scene = zippedScene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(5L, geometry(0f, 0f, 100f, 100f)))),
            extra = listOf(Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(5L to "photo.png")))),
            assets = mapOf("photo.png" to png(Color.BLUE)),
        )
        val rasterizer = KeynoteSceneRasterizer(scene)
        val extracted = assertNotNull(rasterizer.extractDataFile("photo.png"))
        rasterizer.close()
        assertTrue(!extracted.exists(), "temp files must not outlive the rasterizer: $extracted")
    }

    @Test
    fun `an asset name the document does not contain extracts to nothing`() {
        val scene = zippedScene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(null, geometry(0f, 0f, 10f, 10f)))),
        )
        KeynoteSceneRasterizer(scene).use { assertNull(it.extractDataFile("nothing-here.mov")) }
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    @Test
    fun `an angle beyond a full turn is read as degrees rather than radians`() {
        val scene = directoryScene(
            drawables = listOf(
                Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, geometry(0f, 0f, 100f, 100f, angle = 90f))),
            ),
            extra = listOf(Triple(400L, F.TYPE_TSD_SHAPE_STYLE, style(fill = color(1f, 0f, 0f)))),
        )
        val drawable = assertIs<KnDrawable.Shape>(scene.slides.single().drawables.single().drawable)
        assertEquals(PI / 2, drawable.geometry.angle, 1e-6, "90 is a quarter turn in degrees, not 90 radians")
    }

    @Test
    fun `a small angle is taken as radians`() {
        val scene = directoryScene(
            drawables = listOf(
                Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, geometry(0f, 0f, 100f, 100f, angle = 1f))),
            ),
            extra = listOf(Triple(400L, F.TYPE_TSD_SHAPE_STYLE, style(fill = color(1f, 0f, 0f)))),
        )
        val drawable = assertIs<KnDrawable.Shape>(scene.slides.single().drawables.single().drawable)
        assertEquals(1.0, drawable.geometry.angle, 1e-6)
    }

    @Test
    fun `a rotated shape is drawn rotated`() {
        // A wide, short bar rotated a quarter turn has to end up tall and narrow on the slide.
        fun barFrame(angle: Float?): BufferedImage = render(
            directoryScene(
                drawables = listOf(
                    Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, geometry(250f, 450f, 500f, 100f, angle))),
                ),
                extra = listOf(Triple(400L, F.TYPE_TSD_SHAPE_STYLE, style(fill = color(1f, 0f, 0f)))),
            )
        )

        val (flatW, flatH) = inkExtent(barFrame(null))
        val (turnedW, turnedH) = inkExtent(barFrame(90f))
        assertTrue(flatW > flatH, "the unrotated bar is wide, got ${flatW}x$flatH")
        assertTrue(turnedH > turnedW, "the rotated bar has to be tall, got ${turnedW}x$turnedH")
    }

    /** Width and height of the red ink in [image], as a pair. */
    private fun inkExtent(image: BufferedImage): Pair<Int, Int> {
        val red = Color.RED.rgb and 0xFFFFFF
        val xs = mutableListOf<Int>()
        val ys = mutableListOf<Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) and 0xFFFFFF == red) {
                    xs += x
                    ys += y
                }
            }
        }
        if (xs.isEmpty()) return 0 to 0
        return (xs.max() - xs.min()) to (ys.max() - ys.min())
    }

    // ── Strokes ───────────────────────────────────────────────────────────────

    @Test
    fun `a stroked shape draws its outline as well as its fill`() {
        val stroked = directoryScene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, geometry(100f, 100f, 800f, 800f)))),
            extra = listOf(
                Triple(
                    400L,
                    F.TYPE_TSD_SHAPE_STYLE,
                    style(fill = color(1f, 1f, 1f), strokeColor = color(1f, 0f, 0f), strokeWidth = 20f),
                ),
            ),
        )
        val colors = colorsIn(render(stroked, widthPx = 300))
        assertTrue(Color.RED.rgb and 0xFFFFFF in colors, "the stroke colour never reached the frame: $colors")
        assertTrue(Color.WHITE.rgb and 0xFFFFFF in colors, "the fill colour is missing: $colors")
    }

    @Test
    fun `a shape with a stroke but no width still renders`() {
        val scene = directoryScene(
            drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(400L, geometry(10f, 10f, 100f, 100f)))),
            extra = listOf(
                Triple(400L, F.TYPE_TSD_SHAPE_STYLE, style(fill = color(0f, 0f, 1f), strokeColor = color(1f, 0f, 0f))),
            ),
        )
        assertTrue(render(scene).width > 0)
    }
}
