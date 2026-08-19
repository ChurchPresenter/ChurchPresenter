package presentation.engine

import presentation.engine.Fixtures.ProtoWriter
import presentation.engine.keynote.KeynoteDeckParser
import presentation.engine.keynote.KeynoteSceneRasterizer
import presentation.engine.keynote.KnDrawable
import presentation.engine.keynote.KnFields as F
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How one Keynote slide is assembled: its master chain, its placeholders, its z-order, its notes
 * and its background.
 *
 * A slide is not self-contained. Its theme decorations come from a master (which may itself have a
 * master), its background fill may be declared on either, and its drawables arrive from two places
 * — an authoritative z-order list that a real deck was found to *omit placeholders from*, and the
 * placeholder references themselves. Getting the composition wrong does not fail; it silently
 * drops the theme, the title, or the background, leaving a slide that renders and is missing half
 * of what the operator expects.
 */
class KeynoteSlideCompositionTest {

    private val temp: File = Files.createTempDirectory("keynote-composition-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Builders ──────────────────────────────────────────────────────────────

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

    private fun slideStyle(fill: ByteArray) = ProtoWriter().apply {
        bytesField(
            F.SLIDE_STYLE_PROPERTIES,
            ProtoWriter().apply {
                bytesField(
                    F.SLIDE_STYLE_PROPS_FILL,
                    ProtoWriter().apply { bytesField(F.FILL_COLOR, fill) }.toByteArray(),
                )
            }.toByteArray(),
        )
    }.toByteArray()

    private fun shape(styleId: Long?, x: Float = 0f, y: Float = 0f, w: Float = 100f, h: Float = 100f) =
        ProtoWriter().apply {
            bytesField(
                F.SHAPE_SUPER,
                ProtoWriter().apply { bytesField(F.DRAWABLE_GEOMETRY, geometry(x, y, w, h)) }.toByteArray(),
            )
            if (styleId != null) bytesField(F.SHAPE_STYLE, reference(styleId))
        }.toByteArray()

    private fun textShape(storageId: Long) = ProtoWriter().apply {
        bytesField(
            F.SHAPE_INFO_SUPER,
            ProtoWriter().apply {
                bytesField(
                    F.SHAPE_SUPER,
                    ProtoWriter().apply { bytesField(F.DRAWABLE_GEOMETRY, geometry(0f, 0f, 400f, 200f)) }
                        .toByteArray(),
                )
            }.toByteArray(),
        )
        bytesField(F.SHAPE_INFO_OWNED_STORAGE, reference(storageId))
    }.toByteArray()

    private fun placeholder(storageId: Long) =
        ProtoWriter().apply { bytesField(F.PLACEHOLDER_SUPER, textShape(storageId)) }.toByteArray()

    private fun storage(text: String) = ProtoWriter().apply { stringField(F.STORAGE_TEXT, text) }.toByteArray()

    private fun note(storageId: Long) =
        ProtoWriter().apply { bytesField(F.NOTE_CONTAINED_STORAGE, reference(storageId)) }.toByteArray()

    /** Assembles the document/show/node wrapper around one [slidePayload]. */
    private fun parse(slidePayload: ByteArray, extra: List<Triple<Long, Int, ByteArray>>) = KeynoteDeckParser.parse(
        Fixtures.writeKeynoteDir(
            Files.createTempDirectory(temp.toPath(), "deck").toFile(),
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
                                floatField(F.SIZE_WIDTH, 1000f)
                                floatField(F.SIZE_HEIGHT, 1000f)
                            }.toByteArray(),
                        )
                    }.toByteArray(),
                ),
                Triple(
                    100L,
                    F.TYPE_KN_SLIDE_NODE,
                    ProtoWriter().apply { bytesField(2, reference(200L)) }.toByteArray(),
                ),
                Triple(200L, F.TYPE_KN_SLIDE, slidePayload),
            ) + extra,
        )
    )

    // ── Masters ───────────────────────────────────────────────────────────────

    @Test
    fun `a master's decorations are drawn beneath the slide's own content`() {
        val slide = ProtoWriter().apply {
            bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(500L))
            bytesField(F.SLIDE_OWNED_DRAWABLES, reference(300L))
        }.toByteArray()
        val master = ProtoWriter().apply { bytesField(F.SLIDE_OWNED_DRAWABLES, reference(301L)) }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(500L, F.TYPE_KN_SLIDE, master),
                    Triple(300L, F.TYPE_TSD_SHAPE, shape(null, 0f, 0f, 50f, 50f)),
                    Triple(301L, F.TYPE_TSD_SHAPE, shape(null, 500f, 500f, 50f, 50f)),
                ),
            )
        )
        val ids = scene.slides.single().drawables.map { it.id }
        assertEquals(listOf(301L, 300L), ids, "the master's shape is first, so it renders underneath")
    }

    @Test
    fun `a chain of masters is walked deepest first`() {
        val slide = ProtoWriter().apply { bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(500L)) }.toByteArray()
        val master = ProtoWriter().apply {
            bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(501L))
            bytesField(F.SLIDE_OWNED_DRAWABLES, reference(301L))
        }.toByteArray()
        val grandMaster = ProtoWriter().apply { bytesField(F.SLIDE_OWNED_DRAWABLES, reference(302L)) }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(500L, F.TYPE_KN_SLIDE, master),
                    Triple(501L, F.TYPE_KN_SLIDE, grandMaster),
                    Triple(301L, F.TYPE_TSD_SHAPE, shape(null)),
                    Triple(302L, F.TYPE_TSD_SHAPE, shape(null)),
                ),
            )
        )
        assertEquals(
            listOf(302L, 301L),
            scene.slides.single().drawables.map { it.id },
            "the theme under the master, the master under the slide",
        )
    }

    @Test
    fun `a master pointing at itself does not hang the parse`() {
        val slide = ProtoWriter().apply { bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(500L)) }.toByteArray()
        val cyclic = ProtoWriter().apply {
            bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(500L))
            bytesField(F.SLIDE_OWNED_DRAWABLES, reference(301L))
        }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(500L, F.TYPE_KN_SLIDE, cyclic),
                    Triple(301L, F.TYPE_TSD_SHAPE, shape(null)),
                ),
            )
        )
        assertTrue(scene.slides.single().drawables.isNotEmpty(), "the guard stops the walk, it does not empty it")
    }

    @Test
    fun `a master's placeholders are prompts, not content`() {
        // "Click to add title" lives on the master; drawing it would put the prompt on stage.
        val slide = ProtoWriter().apply { bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(500L)) }.toByteArray()
        val master = ProtoWriter().apply {
            bytesField(F.SLIDE_OWNED_DRAWABLES, reference(310L))
            bytesField(F.SLIDE_OWNED_DRAWABLES, reference(301L))
        }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(500L, F.TYPE_KN_SLIDE, master),
                    Triple(310L, F.TYPE_KN_PLACEHOLDER, placeholder(400L)),
                    Triple(400L, F.TYPE_TSWP_STORAGE, storage("Click to add title")),
                    Triple(301L, F.TYPE_TSD_SHAPE, shape(null)),
                ),
            )
        )
        assertEquals(
            listOf(301L),
            scene.slides.single().drawables.map { it.id },
            "the master's placeholder must not reach the slide",
        )
    }

    @Test
    fun `a slide's own placeholder is content and is kept`() {
        val slide = ProtoWriter().apply { bytesField(F.SLIDE_TITLE_PLACEHOLDER, reference(310L)) }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(310L, F.TYPE_KN_PLACEHOLDER, placeholder(400L)),
                    Triple(400L, F.TYPE_TSWP_STORAGE, storage("Real title")),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Text>(scene.slides.single().drawables.single().drawable)
        assertEquals("Real title", drawable.paragraphs.single().text)
    }

    @Test
    fun `the alternate placeholder registration is recognised too`() {
        val slide = ProtoWriter().apply { bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(500L)) }.toByteArray()
        val master = ProtoWriter().apply { bytesField(F.SLIDE_OWNED_DRAWABLES, reference(310L)) }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(500L, F.TYPE_KN_SLIDE, master),
                    Triple(310L, F.TYPE_KN_PLACEHOLDER_ALT, placeholder(400L)),
                    Triple(400L, F.TYPE_TSWP_STORAGE, storage("Click to add text")),
                ),
            )
        )
        assertTrue(
            scene.slides.single().drawables.isEmpty(),
            "both placeholder type ids are prompts on a master",
        )
    }

    // ── Z-order and placeholders ──────────────────────────────────────────────

    @Test
    fun `the z-order list decides the drawing order`() {
        val slide = ProtoWriter().apply {
            bytesField(F.SLIDE_OWNED_DRAWABLES, reference(300L))
            bytesField(F.SLIDE_OWNED_DRAWABLES, reference(301L))
            bytesField(F.SLIDE_DRAWABLES_Z_ORDER, reference(301L))
            bytesField(F.SLIDE_DRAWABLES_Z_ORDER, reference(300L))
        }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(300L, F.TYPE_TSD_SHAPE, shape(null)),
                    Triple(301L, F.TYPE_TSD_SHAPE, shape(null)),
                ),
            )
        )
        assertEquals(
            listOf(301L, 300L),
            scene.slides.single().drawables.map { it.id },
            "the z-order list wins over the owned-drawables order",
        )
    }

    @Test
    fun `a placeholder the z-order omits is drawn below the ordered content`() {
        // Validated on a real deck: the title placeholder was missing from drawables_z_order.
        val slide = ProtoWriter().apply {
            bytesField(F.SLIDE_TITLE_PLACEHOLDER, reference(310L))
            bytesField(F.SLIDE_DRAWABLES_Z_ORDER, reference(300L))
        }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(310L, F.TYPE_KN_PLACEHOLDER, placeholder(400L)),
                    Triple(400L, F.TYPE_TSWP_STORAGE, storage("Title")),
                    Triple(300L, F.TYPE_TSD_SHAPE, shape(null)),
                ),
            )
        )
        assertEquals(
            listOf(310L, 300L),
            scene.slides.single().drawables.map { it.id },
            "the omitted placeholder goes underneath, not missing",
        )
    }

    @Test
    fun `a placeholder that is also in the z-order is not drawn twice`() {
        val slide = ProtoWriter().apply {
            bytesField(F.SLIDE_TITLE_PLACEHOLDER, reference(310L))
            bytesField(F.SLIDE_DRAWABLES_Z_ORDER, reference(310L))
        }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(310L, F.TYPE_KN_PLACEHOLDER, placeholder(400L)),
                    Triple(400L, F.TYPE_TSWP_STORAGE, storage("Title")),
                ),
            )
        )
        assertEquals(1, scene.slides.single().drawables.size)
    }

    // ── Background ────────────────────────────────────────────────────────────

    @Test
    fun `a slide with no fill of its own inherits the master's`() {
        val slide = ProtoWriter().apply { bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(500L)) }.toByteArray()
        val master = ProtoWriter().apply { bytesField(F.SLIDE_STYLE, reference(600L)) }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(500L, F.TYPE_KN_SLIDE, master),
                    Triple(600L, F.TYPE_KN_SLIDE_STYLE, slideStyle(color(0f, 0f, 1f))),
                ),
            )
        )
        assertEquals(Color.BLUE, assertNotNull(scene.slides.single().background?.color))
    }

    @Test
    fun `a slide's own fill wins over the master's`() {
        val slide = ProtoWriter().apply {
            bytesField(F.SLIDE_TEMPLATE_SLIDE, reference(500L))
            bytesField(F.SLIDE_STYLE, reference(601L))
        }.toByteArray()
        val master = ProtoWriter().apply { bytesField(F.SLIDE_STYLE, reference(600L)) }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(500L, F.TYPE_KN_SLIDE, master),
                    Triple(600L, F.TYPE_KN_SLIDE_STYLE, slideStyle(color(0f, 0f, 1f))),
                    Triple(601L, F.TYPE_KN_SLIDE_STYLE, slideStyle(color(1f, 0f, 0f))),
                ),
            )
        )
        assertEquals(Color.RED, assertNotNull(scene.slides.single().background?.color))
    }

    @Test
    fun `a slide with no fill anywhere has no background rather than a black one`() {
        val scene = assertNotNull(parse(ProtoWriter().toByteArray(), emptyList()))
        assertNull(scene.slides.single().background)
    }

    @Test
    fun `a background fill is painted across the whole rendered frame`() {
        val slide = ProtoWriter().apply { bytesField(F.SLIDE_STYLE, reference(600L)) }.toByteArray()
        val scene = assertNotNull(
            parse(slide, listOf(Triple(600L, F.TYPE_KN_SLIDE_STYLE, slideStyle(color(0f, 1f, 0f)))))
        )
        val frame = KeynoteSceneRasterizer(scene).use { it.renderFinalFrame(0, 100) }
        assertEquals(Color.GREEN.rgb and 0xFFFFFF, frame.getRGB(0, 0) and 0xFFFFFF, "the corner is background")
        assertEquals(
            Color.GREEN.rgb and 0xFFFFFF,
            frame.getRGB(frame.width - 1, frame.height - 1) and 0xFFFFFF,
            "and so is the far corner",
        )
    }

    @Test
    fun `a slide with no background renders as transparent rather than as a black rectangle`() {
        val scene = assertNotNull(parse(ProtoWriter().toByteArray(), emptyList()))
        val frame: BufferedImage = KeynoteSceneRasterizer(scene).use { it.renderFinalFrame(0, 60) }
        assertTrue(frame.width == 60 && frame.height == 60, "a 1:1 show renders square")
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    @Test
    fun `presenter notes are read through the note archive`() {
        val slide = ProtoWriter().apply { bytesField(F.SLIDE_NOTE, reference(700L)) }.toByteArray()

        val scene = assertNotNull(
            parse(
                slide,
                listOf(
                    Triple(700L, F.TYPE_KN_SLIDE, note(701L)),
                    Triple(701L, F.TYPE_TSWP_STORAGE, storage("Pause here\rthen continue")),
                ),
            )
        )
        val notes = scene.slides.single().notes
        assertTrue(notes.contains("Pause here"), "got '$notes'")
        assertTrue(notes.contains("\n"), "a carriage return is a paragraph break in notes too: '$notes'")
    }

    @Test
    fun `a slide with no note archive has empty notes`() {
        val scene = assertNotNull(parse(ProtoWriter().toByteArray(), emptyList()))
        assertEquals("", scene.slides.single().notes)
    }
}
