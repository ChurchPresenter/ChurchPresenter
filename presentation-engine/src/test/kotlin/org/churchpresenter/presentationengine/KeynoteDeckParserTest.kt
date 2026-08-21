package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.keynote.KeynoteDeckParser
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.presentationengine.keynote.KeynoteSceneRasterizer

/**
 * Walking a Keynote IWA object graph into a renderable scene:
 * `Document → Show → SlideTree → nodes → slides`.
 *
 * **The object graph these fixtures build was taken from a real Keynote deck**, dumped with the
 * module's own `dumpKeynote` tool, rather than from reading the parser. That distinction is the
 * point: a fixture derived from the code under test encodes the same assumptions as that code, so
 * a misread field number would be baked into both sides and the suite would pass while proving
 * nothing. The real deck confirmed the shape asserted here — `Document(type 1)` referencing
 * `Show(type 2)` at field 2, the show carrying its 1920×1080 size at field 4 and its slide tree at
 * field 3, each node (type 4) pointing at a slide (type 5) at field 2.
 *
 * The fixtures stay programmatic — no binary deck is committed, matching [Fixtures]' existing rule
 * for these formats.
 *
 * **Not covered here:** rendering. [KeynoteSceneRasterizer] draws real
 * drawables, and a drawable rich enough to rasterize needs far more of the graph than a parser test
 * should hand-build; a real deck with content is the honest way in, and none is committed.
 */
class KeynoteDeckParserTest {

    private val temp: File = Files.createTempDirectory("keynote-parser-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Graph builders, in the real deck's shape ──────────────────────────────

    private fun reference(id: Long): ByteArray =
        Fixtures.ProtoWriter().apply { varintField(1, id) }.toByteArray()

    /** KN.DocumentArchive: field 2 = TSP.Reference → show. */
    private fun document(showId: Long): ByteArray =
        Fixtures.ProtoWriter().apply { bytesField(2, reference(showId)) }.toByteArray()

    /**
     * KN.ShowArchive: field 4 = TSP.Size (1 = width, 2 = height, both float),
     * field 3 = KN.SlideTreeArchive whose field 2 is a repeated reference to slide nodes.
     */
    private fun show(width: Float, height: Float, nodeIds: List<Long>): ByteArray {
        val size = Fixtures.ProtoWriter().apply { floatField(1, width); floatField(2, height) }.toByteArray()
        val tree = Fixtures.ProtoWriter().apply { nodeIds.forEach { bytesField(2, reference(it)) } }.toByteArray()
        return Fixtures.ProtoWriter().apply { bytesField(3, tree); bytesField(4, size) }.toByteArray()
    }

    /** KN.SlideNodeArchive: field 1 = children, field 2 = slide, field 4 = isSkipped. */
    private fun slideNode(slideId: Long, children: List<Long> = emptyList(), skipped: Boolean = false): ByteArray =
        Fixtures.ProtoWriter().apply {
            children.forEach { bytesField(1, reference(it)) }
            bytesField(2, reference(slideId))
            if (skipped) varintField(4, 1)
        }.toByteArray()

    /** KN.SlideArchive — empty is enough for the traversal tests. */
    private fun slide(): ByteArray = Fixtures.ProtoWriter().toByteArray()

    /** KN.SlideArchive owning [drawableIds] (field 7), with field 42 as the authoritative z-order. */
    private fun slideWithDrawables(drawableIds: List<Long>): ByteArray =
        Fixtures.ProtoWriter().apply {
            drawableIds.forEach { bytesField(7, reference(it)) }
            drawableIds.forEach { bytesField(42, reference(it)) }
        }.toByteArray()

    /** TSD.GeometryArchive: field 1 = TSP.Point, field 2 = TSP.Size. */
    private fun geometry(x: Float, y: Float, w: Float, h: Float): ByteArray {
        val point = Fixtures.ProtoWriter().apply { floatField(1, x); floatField(2, y) }.toByteArray()
        val size = Fixtures.ProtoWriter().apply { floatField(1, w); floatField(2, h) }.toByteArray()
        return Fixtures.ProtoWriter().apply { bytesField(1, point); bytesField(2, size) }.toByteArray()
    }

    /**
     * TSWP.ShapeInfoArchive (type 2011): field 1 = TSD.ShapeArchive, field 4 = TSP.Reference →
     * TSWP.StorageArchive holding the text.
     *
     * Note the archive nests one level deeper than it first appears: the geometry hangs off
     * `ShapeArchive.super` (field 1, a TSD.DrawableArchive), not off the ShapeArchive itself.
     * Getting that wrong yields a shape at (0,0) sized 0×0 — silently invisible rather than a
     * parse failure, which is why these tests assert the coordinates rather than just the count.
     */
    private fun textShape(x: Float, y: Float, w: Float, h: Float, storageId: Long?): ByteArray {
        val drawableArchive = Fixtures.ProtoWriter().apply { bytesField(1, geometry(x, y, w, h)) }.toByteArray()
        val shapeArchive = Fixtures.ProtoWriter().apply { bytesField(1, drawableArchive) }.toByteArray()
        return Fixtures.ProtoWriter().apply {
            bytesField(1, shapeArchive)
            if (storageId != null) bytesField(4, reference(storageId))
        }.toByteArray()
    }

    /** TSWP.StorageArchive (type 2001): field 3 = repeated string. */
    private fun storage(vararg text: String): ByteArray =
        Fixtures.ProtoWriter().apply { text.forEach { stringField(3, it) } }.toByteArray()

    /** A one-slide deck whose slide owns the given drawable objects. */
    private fun deckWithDrawables(vararg drawables: Triple<Long, Int, ByteArray>): File {
        val ids = drawables.map { it.first }
        return deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slideWithDrawables(ids)),
            *drawables,
        )
    }

    /** Assembles a deck bundle from (id, type, payload) triples. */
    private fun deck(vararg objects: Triple<Long, Int, ByteArray>): File =
        Fixtures.writeKeynoteDir(Files.createTempDirectory(temp.toPath(), "deck").toFile(), objects.toList())

    /** A deck of [slideCount] slides, 1920×1080, as a real one is laid out. */
    private fun simpleDeck(slideCount: Int): File {
        val objects = mutableListOf<Triple<Long, Int, ByteArray>>()
        val nodeIds = (0 until slideCount).map { 100L + it }
        objects += Triple(1L, 1, document(2L))
        objects += Triple(2L, 2, show(1920f, 1080f, nodeIds))
        nodeIds.forEachIndexed { i, nodeId ->
            objects += Triple(nodeId, 4, slideNode(200L + i))
            objects += Triple(200L + i, 5, slide())
        }
        return deck(*objects.toTypedArray())
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `a deck parses into one scene slide per slide node`() {
        val scene = assertNotNull(KeynoteDeckParser.parse(simpleDeck(3)))
        assertEquals(3, scene.slides.size)
        assertEquals(listOf(0, 1, 2), scene.slides.map { it.index }, "slides are indexed in tree order")
    }

    @Test
    fun `the show's size becomes the scene's slide size`() {
        val scene = assertNotNull(KeynoteDeckParser.parse(simpleDeck(1)))
        assertEquals(1920.0, scene.slideWidthPt, 1e-6)
        assertEquals(1080.0, scene.slideHeightPt, 1e-6)
    }

    @Test
    fun `a non-standard slide size is carried through rather than assumed`() {
        val file = deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1024f, 768f, listOf(100L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slide()),
        )
        val scene = assertNotNull(KeynoteDeckParser.parse(file))
        assertEquals(1024.0, scene.slideWidthPt, 1e-6)
        assertEquals(768.0, scene.slideHeightPt, 1e-6)
    }

    // ── Tree traversal ────────────────────────────────────────────────────────

    @Test
    fun `child nodes are visited depth-first, after their parent`() {
        // Keynote groups slides under a parent; the presentation order is the flattened tree.
        val file = deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L, 101L))),
            Triple(100L, 4, slideNode(200L, children = listOf(110L))),
            Triple(110L, 4, slideNode(201L)),
            Triple(101L, 4, slideNode(202L)),
            Triple(200L, 5, slide()),
            Triple(201L, 5, slide()),
            Triple(202L, 5, slide()),
        )
        val scene = assertNotNull(KeynoteDeckParser.parse(file))
        assertEquals(3, scene.slides.size, "the child slide is included, not dropped")
    }

    @Test
    fun `a slide marked skipped is left out of the presentation`() {
        val file = deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L, 101L))),
            Triple(100L, 4, slideNode(200L, skipped = true)),
            Triple(101L, 4, slideNode(201L)),
            Triple(200L, 5, slide()),
            Triple(201L, 5, slide()),
        )
        val scene = assertNotNull(KeynoteDeckParser.parse(file))
        assertEquals(1, scene.slides.size, "a skipped slide is not presented")
    }

    @Test
    fun `a skipped parent still contributes its children`() {
        // Skipping a section header must not silently drop the slides under it.
        val file = deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L))),
            Triple(100L, 4, slideNode(200L, children = listOf(110L), skipped = true)),
            Triple(110L, 4, slideNode(201L)),
            Triple(200L, 5, slide()),
            Triple(201L, 5, slide()),
        )
        val scene = assertNotNull(KeynoteDeckParser.parse(file))
        assertEquals(1, scene.slides.size, "the child survives its parent being skipped")
    }

    @Test
    fun `a node pointing at a missing slide archive is gated, not crashed`() {
        val file = deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L))),
            Triple(100L, 4, slideNode(999L)),   // no object 999
        )
        val scene = assertNotNull(KeynoteDeckParser.parse(file))
        assertEquals(1, scene.slides.size)
        assertNotNull(scene.slides.single().gateReason, "an unreadable slide falls back rather than vanishing")
    }

    // ── Rejection ─────────────────────────────────────────────────────────────

    @Test
    fun `a deck with no document object is not a Keynote scene`() {
        assertNull(KeynoteDeckParser.parse(deck(Triple(2L, 2, show(1920f, 1080f, listOf(100L))))))
    }

    @Test
    fun `a document with no show reference yields nothing`() {
        assertNull(KeynoteDeckParser.parse(deck(Triple(1L, 1, Fixtures.ProtoWriter().toByteArray()))))
    }

    @Test
    fun `a show with no size yields nothing`() {
        val showNoSize = Fixtures.ProtoWriter().apply {
            bytesField(3, Fixtures.ProtoWriter().apply { bytesField(2, reference(100L)) }.toByteArray())
        }.toByteArray()
        assertNull(
            KeynoteDeckParser.parse(
                deck(
                    Triple(1L, 1, document(2L)),
                    Triple(2L, 2, showNoSize),
                    Triple(100L, 4, slideNode(200L)),
                    Triple(200L, 5, slide()),
                )
            )
        )
    }

    @Test
    fun `a zero-sized show yields nothing rather than a degenerate scene`() {
        // A 0-width slide would divide by zero downstream in the renderer.
        assertNull(
            KeynoteDeckParser.parse(
                deck(
                    Triple(1L, 1, document(2L)),
                    Triple(2L, 2, show(0f, 1080f, listOf(100L))),
                    Triple(100L, 4, slideNode(200L)),
                    Triple(200L, 5, slide()),
                )
            )
        )
    }

    @Test
    fun `a deck with no slides at all yields nothing`() {
        assertNull(
            KeynoteDeckParser.parse(
                deck(Triple(1L, 1, document(2L)), Triple(2L, 2, show(1920f, 1080f, emptyList())))
            )
        )
    }

    @Test
    fun `a deck whose every slide is skipped yields nothing`() {
        assertNull(
            KeynoteDeckParser.parse(
                deck(
                    Triple(1L, 1, document(2L)),
                    Triple(2L, 2, show(1920f, 1080f, listOf(100L))),
                    Triple(100L, 4, slideNode(200L, skipped = true)),
                    Triple(200L, 5, slide()),
                )
            )
        )
    }

    @Test
    fun `an unreadable file yields nothing rather than throwing`() {
        val notADeck = File(temp, "broken.key").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(KeynoteDeckParser.parse(notADeck))
        assertNull(KeynoteDeckParser.parse(File(temp, "does-not-exist.key")))
    }

    @Test
    fun `every parsed slide reports whether it can be rendered natively`() {
        val scene = assertNotNull(KeynoteDeckParser.parse(simpleDeck(2)))
        // An empty slide has nothing unsupported on it, so nothing should gate it.
        assertTrue(scene.slides.all { it.gateReason == null }, "gates: ${scene.slides.map { it.gateReason }}")
    }

    // ── Drawables ─────────────────────────────────────────────────────────────
    // A real deck's text boxes arrive as TSWP.ShapeInfoArchive (2011) wrapping a TSD.ShapeArchive
    // for geometry and referencing a TSWP.StorageArchive (2001) for the text — confirmed against a
    // real 12-slide deck via dumpKeynote.

    @Test
    fun `a text shape is parsed with its geometry`() {
        val file = deckWithDrawables(
            Triple(300L, 2011, textShape(x = 100f, y = 50f, w = 400f, h = 200f, storageId = 301L)),
            Triple(301L, 2001, storage("Submodule Coverage")),
        )
        val slide = assertNotNull(KeynoteDeckParser.parse(file)).slides.single()
        assertEquals(1, slide.drawables.size)

        val placed = slide.drawables.single()
        assertEquals(100.0, placed.drawable.geometry.x, 1e-6)
        assertEquals(50.0, placed.drawable.geometry.y, 1e-6)
        assertEquals(400.0, placed.drawable.geometry.w, 1e-6)
        assertEquals(200.0, placed.drawable.geometry.h, 1e-6)
    }

    @Test
    fun `a text shape carries the text from its storage archive`() {
        val file = deckWithDrawables(
            Triple(300L, 2011, textShape(0f, 0f, 400f, 200f, storageId = 301L)),
            Triple(301L, 2001, storage("Submodule Coverage")),
        )
        val drawable = assertNotNull(KeynoteDeckParser.parse(file)).slides.single().drawables.single().drawable
        assertTrue(
            drawable.toString().contains("Submodule Coverage"),
            "the storage text reaches the drawable: $drawable",
        )
    }

    @Test
    fun `several drawables on one slide are all kept, in order`() {
        val file = deckWithDrawables(
            Triple(300L, 2011, textShape(0f, 0f, 100f, 50f, storageId = 310L)),
            Triple(301L, 2011, textShape(200f, 0f, 100f, 50f, storageId = 311L)),
            Triple(302L, 2011, textShape(400f, 0f, 100f, 50f, storageId = 312L)),
            Triple(310L, 2001, storage("first")),
            Triple(311L, 2001, storage("second")),
            Triple(312L, 2001, storage("third")),
        )
        val slide = assertNotNull(KeynoteDeckParser.parse(file)).slides.single()
        assertEquals(3, slide.drawables.size)
        assertEquals(
            listOf(0.0, 200.0, 400.0),
            slide.drawables.map { it.drawable.geometry.x },
            "z-order is preserved as authored",
        )
    }

    @Test
    fun `a shape with no storage reference still parses rather than gating the slide`() {
        val file = deckWithDrawables(Triple(300L, 2011, textShape(10f, 10f, 100f, 100f, storageId = null)))
        val slide = assertNotNull(KeynoteDeckParser.parse(file)).slides.single()
        assertEquals(1, slide.drawables.size, "an empty text box is still a drawable")
    }

    @Test
    fun `a drawable whose geometry is absent falls back to zero rather than failing`() {
        val emptyShapeArchive = Fixtures.ProtoWriter().apply {
            bytesField(1, Fixtures.ProtoWriter().toByteArray())
        }.toByteArray()
        val noGeometry = Fixtures.ProtoWriter().apply {
            bytesField(1, emptyShapeArchive)
            bytesField(4, reference(301L))
        }.toByteArray()
        val file = deckWithDrawables(
            Triple(300L, 2011, noGeometry),
            Triple(301L, 2001, storage("text")),
        )
        val placed = assertNotNull(KeynoteDeckParser.parse(file)).slides.single().drawables.single()
        assertEquals(0.0, placed.drawable.geometry.x, 1e-6)
        assertEquals(0.0, placed.drawable.geometry.w, 1e-6)
    }

    @Test
    fun `a drawable of an unrecognised type gates the slide instead of being dropped silently`() {
        // The fidelity gate exists so an unrenderable slide falls back to its static image rather
        // than rendering as a blank or a partial slide.
        val file = deckWithDrawables(Triple(300L, 9999, Fixtures.ProtoWriter().toByteArray()))
        val slide = assertNotNull(KeynoteDeckParser.parse(file)).slides.single()
        assertNotNull(slide.gateReason, "an unknown drawable type is reported, not ignored")
    }

    @Test
    fun `a drawable reference pointing at nothing gates the slide`() {
        val file = deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slideWithDrawables(listOf(999L))),   // no object 999
        )
        val slide = assertNotNull(KeynoteDeckParser.parse(file)).slides.single()
        assertNotNull(slide.gateReason)
    }
}
