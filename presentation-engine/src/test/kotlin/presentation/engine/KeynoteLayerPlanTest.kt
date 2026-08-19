package presentation.engine

import presentation.engine.keynote.KeynoteBuildMapper
import presentation.engine.keynote.KeynoteLayerPlanner
import presentation.engine.keynote.KeynoteScene
import presentation.engine.keynote.KeynoteSceneRasterizer
import presentation.engine.keynote.KnDrawable
import presentation.engine.keynote.KnFill
import presentation.engine.keynote.KnGeometry
import presentation.engine.keynote.KnParagraph
import presentation.engine.keynote.KnPlacedDrawable
import presentation.engine.keynote.KnSlide
import presentation.engine.model.EffectInterval
import presentation.engine.model.EffectSpec
import presentation.engine.model.LayerSpec
import presentation.engine.model.Step
import presentation.engine.model.Timeline
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Splitting a parsed Keynote slide into animatable layers, and drawing those layers one at a time.
 *
 * The planner's job is to give exactly the drawables a build targets their own layer and flatten
 * everything else into bands, because a layer is what can be animated and a band is what cannot.
 * Two rules carry the weight: a movie is always its own layer even on a slide with no builds at all
 * (the app has to find it to drive playback), and a by-paragraph build fans one text box into one
 * layer per paragraph — the same layer kind the PPTX planner emits, so everything downstream is
 * shared rather than reinvented.
 *
 * Scenes are built as models: the planner takes a parsed slide, so parsing one first would only
 * test the parser.
 */
class KeynoteLayerPlanTest {

    private val slideW = 1000.0
    private val slideH = 600.0

    private fun geometry(x: Double, y: Double, w: Double, h: Double) =
        KnGeometry(x, y, w, h, 0.0, hFlip = false, vFlip = false)

    private fun box(x: Double = 100.0, y: Double = 100.0, w: Double = 200.0, h: Double = 100.0) =
        KnDrawable.Shape(geometry(x, y, w, h), null, KnFill(color = Color.RED), null, 0.0, 1.0)

    private fun textBox(vararg lines: String, x: Double = 100.0, y: Double = 100.0) = KnDrawable.Text(
        geometry = geometry(x, y, 600.0, 300.0),
        shape = KnDrawable.Shape(geometry(x, y, 600.0, 300.0), null, KnFill(color = Color.WHITE), null, 0.0, 1.0),
        paragraphs = lines.map { KnParagraph(it, null, 30.0, false, false, Color.BLACK, 0) },
    )

    private fun movie(x: Double = 0.0, y: Double = 0.0) =
        KnDrawable.Movie(geometry(x, y, 400.0, 225.0), videoFile = "clip.mov", posterFile = null)

    private fun slide(
        drawables: List<KnPlacedDrawable>,
        builtIds: Set<Long> = emptySet(),
        paragraphBuiltIds: Set<Long> = emptySet(),
        timeline: Timeline? = null,
    ) = KnSlide(
        index = 0,
        background = KnFill(color = Color.WHITE),
        drawables = drawables,
        notes = "",
        timeline = timeline,
        builtDrawableIds = builtIds,
        paragraphBuiltDrawableIds = paragraphBuiltIds,
        transition = null,
        gateReason = null,
    )

    private fun timelineFor(vararg layerIds: String) = Timeline(
        layerIds.map { id ->
            Step(listOf(EffectInterval(id, EffectSpec.Fade(EffectSpec.Role.ENTRANCE), 0, 500)))
        }
    )

    private fun plan(slide: KnSlide) = KeynoteLayerPlanner.plan(slide, slideW, slideH)

    private fun sceneOf(slide: KnSlide) = KeynoteScene(File("in-memory.key"), slideW, slideH, listOf(slide))

    private fun countInk(image: BufferedImage, background: Int = image.getRGB(0, 0)): Int {
        var count = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) != background) count++
            }
        }
        return count
    }

    // ── Nothing to plan ───────────────────────────────────────────────────────

    @Test
    fun `a slide with no builds and no movie has no layer plan at all`() {
        assertNull(
            plan(slide(listOf(KnPlacedDrawable(1L, box())))),
            "a still slide renders as one picture; layers would be pure overhead",
        )
    }

    @Test
    fun `a timeline with no built drawables is not enough to plan layers`() {
        val slide = slide(
            drawables = listOf(KnPlacedDrawable(1L, box())),
            builtIds = emptySet(),
            timeline = timelineFor("kn-1"),
        )
        assertNull(plan(slide))
    }

    // ── Bands and layers ──────────────────────────────────────────────────────

    @Test
    fun `only the built drawable gets its own layer, the rest flatten into bands`() {
        val slide = slide(
            drawables = listOf(
                KnPlacedDrawable(1L, box(x = 0.0)),
                KnPlacedDrawable(2L, box(x = 300.0)),
                KnPlacedDrawable(3L, box(x = 600.0)),
            ),
            builtIds = setOf(2L),
            timeline = timelineFor("kn-2"),
        )
        val layers = assertNotNull(plan(slide))
        assertEquals(
            listOf("Background", "Shape", "Background"),
            layers.map { it::class.simpleName },
            "the un-built drawables flatten around the built one",
        )
        assertEquals(listOf(0), assertIs<LayerSpec.Background>(layers.first()).shapeIndexes)
        assertEquals(listOf(2), assertIs<LayerSpec.Background>(layers.last()).shapeIndexes)
        assertEquals("kn-2", layers[1].id)
    }

    @Test
    fun `a slide whose only content is built still has a bottom band for its background`() {
        val slide = slide(
            drawables = listOf(KnPlacedDrawable(1L, box())),
            builtIds = setOf(1L),
            timeline = timelineFor("kn-1"),
        )
        val layers = assertNotNull(plan(slide))
        val band = assertIs<LayerSpec.Background>(layers.first())
        assertEquals(0, band.zIndex)
        assertTrue(band.shapeIndexes.isEmpty(), "the band carries the slide's background and nothing else")
    }

    @Test
    fun `a build targeting a drawable inside a group promotes the whole group`() {
        // The group is the top-level drawable; the build names a child. Animating the child alone
        // is impossible once it is inside a group, so the group is what becomes the layer.
        val child = KnPlacedDrawable(2L, box())
        val group = KnPlacedDrawable(1L, KnDrawable.Group(geometry(0.0, 0.0, 500.0, 400.0), listOf(child)))
        val slide = slide(drawables = listOf(group), builtIds = setOf(2L), timeline = timelineFor("kn-2"))

        val layers = assertNotNull(plan(slide))
        assertEquals("kn-1", layers.first { it is LayerSpec.Shape }.id, "the group, not the child")
    }

    // ── Movies ────────────────────────────────────────────────────────────────

    @Test
    fun `a movie is its own layer even with no builds on the slide`() {
        val slide = slide(drawables = listOf(KnPlacedDrawable(1L, box()), KnPlacedDrawable(2L, movie())))
        val layers = assertNotNull(plan(slide), "a movie alone is reason enough to plan layers")
        val media = assertIs<LayerSpec.Media>(layers.first { it is LayerSpec.Media })
        assertEquals("kn-2", media.id)
        assertEquals(400.0, media.contentRectPt.w, 1e-6, "the content rect is the movie's own geometry")
    }

    @Test
    fun `a media layer's padded bounds are at least its content`() {
        val slide = slide(drawables = listOf(KnPlacedDrawable(1L, movie(x = 50.0, y = 50.0))))
        val media = assertIs<LayerSpec.Media>(assertNotNull(plan(slide)).first { it is LayerSpec.Media })
        assertTrue(media.boundsPt.w >= media.contentRectPt.w, "padding never shrinks the video area")
        assertTrue(media.boundsPt.h >= media.contentRectPt.h)
    }

    // ── Paragraph builds ──────────────────────────────────────────────────────

    @Test
    fun `a by-paragraph build becomes one layer per paragraph`() {
        val slide = slide(
            drawables = listOf(KnPlacedDrawable(1L, textBox("Alpha", "Beta", "Gamma"))),
            builtIds = setOf(1L),
            paragraphBuiltIds = setOf(1L),
            timeline = timelineFor("kn-1-p0", "kn-1-p1", "kn-1-p2"),
        )
        val layers = assertNotNull(plan(slide))
        val paragraphs = layers.filterIsInstance<LayerSpec.ParagraphText>()
        assertEquals(3, paragraphs.size)
        assertEquals(listOf(0, 1, 2), paragraphs.map { it.paragraphIndex })
        assertEquals(
            (0 until 3).map { KeynoteBuildMapper.paragraphLayerIdFor(1L, it) },
            paragraphs.map { it.id },
        )
    }

    @Test
    fun `a single-paragraph box is one shape layer, not a paragraph layer`() {
        val slide = slide(
            drawables = listOf(KnPlacedDrawable(1L, textBox("Only one"))),
            builtIds = setOf(1L),
            paragraphBuiltIds = setOf(1L),
            timeline = timelineFor("kn-1"),
        )
        val layers = assertNotNull(plan(slide))
        assertTrue(layers.none { it is LayerSpec.ParagraphText }, "nothing to fan out")
        assertTrue(layers.any { it is LayerSpec.Shape })
    }

    // ── Rendering the planned layers ──────────────────────────────────────────

    @Test
    fun `each paragraph layer draws its own line`() {
        val slide = slide(
            drawables = listOf(KnPlacedDrawable(1L, textBox("Alpha", "Beta", "Gamma"))),
            builtIds = setOf(1L),
            paragraphBuiltIds = setOf(1L),
            timeline = timelineFor("kn-1-p0", "kn-1-p1", "kn-1-p2"),
        )
        val layers = assertNotNull(plan(slide)).filterIsInstance<LayerSpec.ParagraphText>()

        KeynoteSceneRasterizer(sceneOf(slide)).use { rasterizer ->
            val inkCounts = layers.map { spec ->
                val layer = rasterizer.rasterizeLayer(0, spec, targetWidthPx = 500)
                countInk(layer.image, background = 0)
            }
            assertTrue(inkCounts.all { it > 0 }, "every paragraph layer draws something: $inkCounts")
            // Paragraph 0 also paints the box's own fill, so it is heavier; the other two are
            // text only and should be within sight of each other.
            val textOnly = inkCounts.drop(1)
            assertTrue(
                textOnly.max() < textOnly.min() * 3,
                "one text layer appears to hold more than its own line: $inkCounts",
            )
        }
    }

    @Test
    fun `only the first paragraph layer carries the text box's own fill`() {
        val slide = slide(
            drawables = listOf(KnPlacedDrawable(1L, textBox("Alpha", "Beta"))),
            builtIds = setOf(1L),
            paragraphBuiltIds = setOf(1L),
            timeline = timelineFor("kn-1-p0", "kn-1-p1"),
        )
        val layers = assertNotNull(plan(slide)).filterIsInstance<LayerSpec.ParagraphText>()

        KeynoteSceneRasterizer(sceneOf(slide)).use { rasterizer ->
            val first = rasterizer.rasterizeLayer(0, layers[0], targetWidthPx = 400)
            val second = rasterizer.rasterizeLayer(0, layers[1], targetWidthPx = 400)
            assertTrue(
                countInk(first.image, background = 0) > countInk(second.image, background = 0),
                "the box fill is painted once, with paragraph 0 — otherwise it stacks on every layer",
            )
        }
    }

    @Test
    fun `a media layer renders and reports the video file it points at`() {
        val slide = slide(drawables = listOf(KnPlacedDrawable(1L, movie())))
        val media = assertIs<LayerSpec.Media>(assertNotNull(plan(slide)).first { it is LayerSpec.Media })

        KeynoteSceneRasterizer(sceneOf(slide)).use { rasterizer ->
            val layer = rasterizer.rasterizeLayer(0, media, targetWidthPx = 300)
            assertTrue(layer.image.width >= 1 && layer.image.height >= 1)
            assertNull(
                assertIs<LayerSpec.Media>(layer.spec).mediaFile,
                "the fixture's document holds no asset, so there is nothing to extract",
            )
        }
    }

    @Test
    fun `a band layer draws the drawables it flattened and the slide background`() {
        val slide = slide(
            drawables = listOf(
                KnPlacedDrawable(1L, box(x = 0.0, y = 0.0, w = 1000.0, h = 600.0)),
                KnPlacedDrawable(2L, box()),
            ),
            builtIds = setOf(2L),
            timeline = timelineFor("kn-2"),
        )
        val band = assertIs<LayerSpec.Background>(assertNotNull(plan(slide)).first())

        KeynoteSceneRasterizer(sceneOf(slide)).use { rasterizer ->
            val layer = rasterizer.rasterizeLayer(0, band, targetWidthPx = 200)
            assertEquals(
                Color.RED.rgb and 0xFFFFFF,
                layer.image.getRGB(100, 60) and 0xFFFFFF,
                "the flattened full-bleed box is what the band shows",
            )
        }
    }
}
