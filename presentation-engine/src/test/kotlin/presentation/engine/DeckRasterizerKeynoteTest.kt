package presentation.engine

import presentation.engine.Fixtures.ProtoWriter
import presentation.engine.keynote.KnFields as F
import presentation.engine.model.Deck
import presentation.engine.model.Fidelity
import presentation.engine.model.LayerSpec
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Which source a Keynote deck actually renders from.
 *
 * The engine has three ways to put a Keynote slide on screen — the embedded preview PDF, the
 * per-slide thumbnails, and its own native render of the parsed scene — and [DeckRasterizer] picks
 * per *slide*, not per deck: a native deck with one unreadable slide renders that slide from its
 * static fallback and the rest natively. Picking wrong is not a crash, it is a slide that looks
 * subtly (or entirely) unlike the one before it, so each route is worth pinning by what it draws.
 */
class DeckRasterizerKeynoteTest {

    private val temp: File = Files.createTempDirectory("deck-rasterizer-keynote-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun reference(id: Long) = ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, id) }.toByteArray()

    private fun geometry(x: Float, y: Float, w: Float, h: Float) = ProtoWriter().apply {
        bytesField(
            F.GEOMETRY_POSITION,
            ProtoWriter().apply { floatField(F.POINT_X, x); floatField(F.POINT_Y, y) }.toByteArray(),
        )
        bytesField(
            F.GEOMETRY_SIZE,
            ProtoWriter().apply { floatField(F.SIZE_WIDTH, w); floatField(F.SIZE_HEIGHT, h) }.toByteArray(),
        )
    }.toByteArray()

    private fun color(r: Float, g: Float, b: Float) = ProtoWriter().apply {
        varintField(F.COLOR_MODEL, 1)
        floatField(F.COLOR_R, r)
        floatField(F.COLOR_G, g)
        floatField(F.COLOR_B, b)
        floatField(F.COLOR_A, 1f)
    }.toByteArray()

    private fun filledShape(styleId: Long) = ProtoWriter().apply {
        bytesField(
            F.SHAPE_SUPER,
            ProtoWriter().apply { bytesField(F.DRAWABLE_GEOMETRY, geometry(0f, 0f, 1000f, 1000f)) }.toByteArray(),
        )
        bytesField(F.SHAPE_STYLE, reference(styleId))
    }.toByteArray()

    private fun shapeStyle(fill: ByteArray) = ProtoWriter().apply {
        bytesField(
            F.SHAPE_STYLE_PROPERTIES,
            ProtoWriter().apply {
                bytesField(F.SHAPE_PROPS_FILL, ProtoWriter().apply { bytesField(F.FILL_COLOR, fill) }.toByteArray())
            }.toByteArray(),
        )
    }.toByteArray()

    private fun unknownDrawable() = ProtoWriter().apply { varintField(99, 1) }.toByteArray()

    /** PNG rather than JPEG: the assertions key on the exact colour, and JPEG shifts it. */
    private fun thumbnail(color: Color): ByteArray {
        val image = BufferedImage(80, 45, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { paint = color; fillRect(0, 0, 80, 45); dispose() }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    /**
     * A `.key` package directory: [objects] in its Index, one `st-<n>.jpg` per entry in
     * [thumbnailColors], and a real preview PDF of [previewPdfPages] pages when asked for.
     */
    private fun bundle(
        objects: List<Triple<Long, Int, ByteArray>> = emptyList(),
        thumbnailColors: List<Color> = emptyList(),
        previewPdfPages: Int? = null,
        name: String = "deck",
    ): File {
        val dir = Files.createTempDirectory(temp.toPath(), name).toFile()
        val key = File(dir, "fixture.key").apply { mkdirs() }
        File(key, "Index").mkdirs()
        if (objects.isNotEmpty()) File(key, "Index/Test.iwa").writeBytes(Fixtures.buildIwa(objects))
        thumbnailColors.forEachIndexed { index, color ->
            File(key, "Index/Slide-${index + 1}.iwa").writeBytes(ByteArray(4))
            File(key, "Data").mkdirs()
            File(key, "Data/st-${index + 1}.png").writeBytes(thumbnail(color))
        }
        if (previewPdfPages != null) {
            File(key, "QuickLook").mkdirs()
            File(key, "QuickLook/Preview.pdf")
                .writeBytes(Fixtures.createPdf(dir, pages = previewPdfPages, name = "src.pdf").readBytes())
        }
        return key
    }

    /** A parseable one-slide scene whose slide is a red full-bleed box. */
    private fun nativeObjects(gated: Boolean = false, slides: Int = 1): List<Triple<Long, Int, ByteArray>> {
        val objects = mutableListOf<Triple<Long, Int, ByteArray>>()
        val nodeIds = (0 until slides).map { 100L + it }
        objects += Triple(1L, F.TYPE_KN_DOCUMENT, ProtoWriter().apply { bytesField(2, reference(2L)) }.toByteArray())
        objects += Triple(
            2L,
            F.TYPE_KN_SHOW,
            ProtoWriter().apply {
                bytesField(
                    3,
                    ProtoWriter().apply { nodeIds.forEach { bytesField(2, reference(it)) } }.toByteArray(),
                )
                bytesField(
                    4,
                    ProtoWriter().apply {
                        floatField(F.SIZE_WIDTH, 1000f)
                        floatField(F.SIZE_HEIGHT, 1000f)
                    }.toByteArray(),
                )
            }.toByteArray(),
        )
        nodeIds.forEachIndexed { index, nodeId ->
            val slideId = 200L + index
            val drawableId = 300L + index
            objects += Triple(
                nodeId,
                F.TYPE_KN_SLIDE_NODE,
                ProtoWriter().apply { bytesField(2, reference(slideId)) }.toByteArray(),
            )
            objects += Triple(
                slideId,
                F.TYPE_KN_SLIDE,
                ProtoWriter().apply {
                    bytesField(F.SLIDE_OWNED_DRAWABLES, reference(drawableId))
                    bytesField(F.SLIDE_DRAWABLES_Z_ORDER, reference(drawableId))
                }.toByteArray(),
            )
            val unreadable = gated && index == slides - 1
            objects += if (unreadable) {
                Triple(drawableId, 9999, unknownDrawable())
            } else {
                Triple(drawableId, F.TYPE_TSD_SHAPE, filledShape(400L))
            }
        }
        objects += Triple(400L, F.TYPE_TSD_SHAPE_STYLE, shapeStyle(color(1f, 0f, 0f)))
        return objects
    }

    private fun load(file: File): Deck = assertIs<LoadResult.Success>(PresentationLoader.load(file)).deck

    private fun dominantColor(image: BufferedImage): Int {
        val counts = mutableMapOf<Int, Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y) and 0xFFFFFF
                counts[rgb] = (counts[rgb] ?: 0) + 1
            }
        }
        return counts.maxByOrNull { it.value }!!.key
    }

    // ── The three routes ──────────────────────────────────────────────────────

    @Test
    fun `a thumbnail deck renders from its thumbnails`() {
        val deck = load(bundle(thumbnailColors = listOf(Color.GREEN, Color.BLUE), name = "thumbs"))
        assertEquals(2, deck.slideCount)

        DeckRasterizer(deck, targetWidthPx = 240).use { rasterizer ->
            assertEquals(
                Color.GREEN.rgb and 0xFFFFFF,
                dominantColor(rasterizer.renderFinalFrame(0)),
                "slide 1 has to come from st-1",
            )
            assertEquals(
                Color.BLUE.rgb and 0xFFFFFF,
                dominantColor(rasterizer.renderFinalFrame(1)),
                "and slide 2 from st-2 — swapping them is the failure this catches",
            )
        }
    }

    @Test
    fun `a preview-PDF deck renders its pages`() {
        val deck = load(bundle(previewPdfPages = 2, name = "preview"))
        assertEquals(2, deck.slideCount)

        DeckRasterizer(deck, targetWidthPx = 300).use { rasterizer ->
            val frame = rasterizer.renderFinalFrame(1)
            assertEquals(300, frame.width, "the render honours the requested width")
            assertTrue(frame.height > 0)
        }
    }

    @Test
    fun `a native deck renders its own scene rather than a picture of it`() {
        val deck = load(bundle(objects = nativeObjects(), name = "native"))
        assertEquals(Fidelity.NATIVE, deck.slides.single().fidelity)

        DeckRasterizer(deck, targetWidthPx = 200).use { rasterizer ->
            assertEquals(
                Color.RED.rgb and 0xFFFFFF,
                dominantColor(rasterizer.renderFinalFrame(0)),
                "the parsed red box is what should be drawn",
            )
        }
    }

    @Test
    fun `a gated slide in a native deck falls back to its aligned thumbnail`() {
        // Two slides, the second unreadable, and two thumbnails: slide 2 renders from st-2 while
        // slide 1 still renders natively.
        val deck = load(
            bundle(
                objects = nativeObjects(gated = true, slides = 2),
                thumbnailColors = listOf(Color.GREEN, Color.BLUE),
                name = "mixed",
            )
        )
        assertEquals(Fidelity.NATIVE, deck.slides[0].fidelity)
        assertEquals(Fidelity.STATIC_FALLBACK, deck.slides[1].fidelity)

        DeckRasterizer(deck, targetWidthPx = 200).use { rasterizer ->
            assertEquals(
                Color.RED.rgb and 0xFFFFFF,
                dominantColor(rasterizer.renderFinalFrame(0)),
                "the readable slide still renders natively",
            )
            assertEquals(
                Color.BLUE.rgb and 0xFFFFFF,
                dominantColor(rasterizer.renderFinalFrame(1)),
                "the gated slide comes from its own thumbnail, not from the first one",
            )
        }
    }

    @Test
    fun `a gated slide with no aligned fallback still renders what parsed`() {
        // One slide, unreadable, and thumbnails that do not line up: the deck stays native and
        // shows the parseable subset — partial beats blank.
        val deck = load(
            bundle(
                objects = nativeObjects(gated = true, slides = 1),
                thumbnailColors = listOf(Color.GREEN, Color.BLUE),
                name = "unaligned",
            )
        )
        DeckRasterizer(deck, targetWidthPx = 120).use { rasterizer ->
            val frame = rasterizer.renderFinalFrame(0)
            assertTrue(frame.width > 0 && frame.height > 0, "it renders rather than failing")
        }
    }

    // ── Layers ────────────────────────────────────────────────────────────────

    @Test
    fun `a Keynote slide with no layer plan comes back as one full-frame layer`() {
        val deck = load(bundle(thumbnailColors = listOf(Color.GREEN), name = "layers-static"))
        DeckRasterizer(deck, targetWidthPx = 240).use { rasterizer ->
            val layer = rasterizer.rasterizeSlideLayers(0).single()
            assertIs<LayerSpec.StaticComposite>(layer.spec)
            assertEquals(0, layer.offsetXPx)
            assertEquals(0, layer.offsetYPx)
            assertEquals(
                rasterizer.renderFinalFrame(0).width,
                layer.image.width,
                "the single layer is the whole frame",
            )
        }
    }

    @Test
    fun `a thumbnail is served at its own resolution rather than upscaled`() {
        // The target width drives what the engine *renders*; a thumbnail is a picture the document
        // already contains, and blowing it up would only add blur.
        val deck = load(bundle(thumbnailColors = listOf(Color.GREEN), name = "thumb-size"))
        DeckRasterizer(deck, targetWidthPx = 1920).use { rasterizer ->
            assertEquals(80, rasterizer.renderFinalFrame(0).width)
        }
    }

    @Test
    fun `compositing a Keynote deck's layers reproduces its final frame`() {
        val deck = load(bundle(objects = nativeObjects(), name = "composite"))
        DeckRasterizer(deck, targetWidthPx = 160).use { rasterizer ->
            val frame = rasterizer.renderFinalFrame(0)
            val layers = rasterizer.rasterizeSlideLayers(0)
            val canvas = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
            canvas.createGraphics().apply {
                layers.forEach { drawImage(it.image, it.offsetXPx, it.offsetYPx, null) }
                dispose()
            }
            assertEquals(dominantColor(frame), dominantColor(canvas), "the layers add up to the frame")
        }
    }

    // ── Bounds and lifecycle ──────────────────────────────────────────────────

    @Test
    fun `asking for a slide that does not exist is rejected`() {
        val deck = load(bundle(thumbnailColors = listOf(Color.GREEN), name = "bounds"))
        DeckRasterizer(deck, targetWidthPx = 100).use { rasterizer ->
            assertFailsWith<IllegalArgumentException> { rasterizer.renderFinalFrame(5) }
            assertFailsWith<IllegalArgumentException> { rasterizer.rasterizeSlideLayers(-1) }
        }
    }

    @Test
    fun `a native rasterizer is reused across slides and closes cleanly twice`() {
        val deck = load(bundle(objects = nativeObjects(slides = 2), name = "reuse"))
        val rasterizer = DeckRasterizer(deck, targetWidthPx = 100)
        rasterizer.renderFinalFrame(0)
        rasterizer.renderFinalFrame(1)
        rasterizer.close()
        rasterizer.close()
    }
}
