package presentation.engine

import presentation.engine.Fixtures.ProtoWriter
import presentation.engine.keynote.KeynoteDeckParser
import presentation.engine.keynote.KnDrawable
import presentation.engine.keynote.KnFields as F
import java.awt.Color
import java.awt.geom.PathIterator
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
 * The detail inside a Keynote shape: its outline, its grouping, and the character styling of its
 * text.
 *
 * All three resolve indirectly. An outline is a bezier path in its own natural coordinate space
 * that has to be normalized into the unit square before it can be scaled to the shape's geometry —
 * miss that and the outline is drawn at the wrong size, or off the slide entirely. A group is a
 * drawable that owns other drawables by reference. And character styling is an attribute *table*
 * keyed by character offset into the storage string, so a run's style is looked up rather than
 * carried: a table read even slightly wrong silently styles the wrong half of a sentence.
 */
class KeynoteShapeDetailTest {

    private val temp: File = Files.createTempDirectory("keynote-shape-detail-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Graph builders ────────────────────────────────────────────────────────

    private fun reference(id: Long) = ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, id) }.toByteArray()

    private fun geometry(x: Float = 0f, y: Float = 0f, w: Float = 400f, h: Float = 200f): ByteArray {
        val point = ProtoWriter().apply { floatField(F.POINT_X, x); floatField(F.POINT_Y, y) }.toByteArray()
        val size = ProtoWriter().apply { floatField(F.SIZE_WIDTH, w); floatField(F.SIZE_HEIGHT, h) }.toByteArray()
        return ProtoWriter().apply { bytesField(1, point); bytesField(2, size) }.toByteArray()
    }

    private fun point(x: Float, y: Float) =
        ProtoWriter().apply { floatField(F.POINT_X, x); floatField(F.POINT_Y, y) }.toByteArray()

    private fun pathElement(type: Int, vararg points: ByteArray) = ProtoWriter().apply {
        varintField(F.PATH_ELEMENT_TYPE, type.toLong())
        points.forEach { bytesField(F.PATH_ELEMENT_POINTS, it) }
    }.toByteArray()

    /** TSD.BezierPathSourceArchive: a natural size and the path drawn in that space. */
    private fun bezierPathSource(naturalW: Float, naturalH: Float, elements: List<ByteArray>) = ProtoWriter().apply {
        bytesField(
            F.PATHSOURCE_BEZIER,
            ProtoWriter().apply {
                bytesField(
                    F.BEZIER_PATH_NATURAL_SIZE,
                    ProtoWriter().apply {
                        floatField(F.SIZE_WIDTH, naturalW)
                        floatField(F.SIZE_HEIGHT, naturalH)
                    }.toByteArray(),
                )
                bytesField(
                    F.BEZIER_PATH_PATH,
                    ProtoWriter().apply { elements.forEach { bytesField(F.PATH_ELEMENTS, it) } }.toByteArray(),
                )
            }.toByteArray(),
        )
    }.toByteArray()

    private fun shape(pathSource: ByteArray? = null, geometry: ByteArray = geometry()) = ProtoWriter().apply {
        bytesField(F.SHAPE_SUPER, ProtoWriter().apply { bytesField(1, geometry) }.toByteArray())
        if (pathSource != null) bytesField(F.SHAPE_PATHSOURCE, pathSource)
    }.toByteArray()

    private fun group(childIds: List<Long>, geometry: ByteArray = geometry()) = ProtoWriter().apply {
        bytesField(F.GROUP_SUPER, ProtoWriter().apply { bytesField(1, geometry) }.toByteArray())
        childIds.forEach { bytesField(F.GROUP_CHILDREN, reference(it)) }
    }.toByteArray()

    private fun color(r: Float, g: Float, b: Float) = ProtoWriter().apply {
        varintField(F.COLOR_MODEL, 1)
        floatField(F.COLOR_R, r)
        floatField(F.COLOR_G, g)
        floatField(F.COLOR_B, b)
        floatField(F.COLOR_A, 1f)
    }.toByteArray()

    /** TSWP.CharacterStyleArchive, optionally inheriting from [parentId]. */
    private fun characterStyle(
        fontName: String? = null,
        fontSize: Float? = null,
        bold: Boolean? = null,
        italic: Boolean? = null,
        fontColor: ByteArray? = null,
        parentId: Long? = null,
    ) = ProtoWriter().apply {
        bytesField(
            F.CHARACTER_STYLE_PROPERTIES,
            ProtoWriter().apply {
                if (bold != null) varintField(F.CHAR_PROPS_BOLD, if (bold) 1 else 0)
                if (italic != null) varintField(F.CHAR_PROPS_ITALIC, if (italic) 1 else 0)
                if (fontSize != null) floatField(F.CHAR_PROPS_FONT_SIZE, fontSize)
                if (fontName != null) stringField(F.CHAR_PROPS_FONT_NAME, fontName)
                if (fontColor != null) bytesField(F.CHAR_PROPS_FONT_COLOR, fontColor)
            }.toByteArray(),
        )
        if (parentId != null) {
            bytesField(
                F.STYLE_SUPER,
                ProtoWriter().apply { bytesField(F.TSS_STYLE_PARENT, reference(parentId)) }.toByteArray(),
            )
        }
    }.toByteArray()

    /** TSWP.StorageArchive: the text, plus a character-style table keyed by character offset. */
    private fun storage(text: String, charRuns: List<Pair<Int, Long>> = emptyList()) = ProtoWriter().apply {
        stringField(F.STORAGE_TEXT, text)
        if (charRuns.isNotEmpty()) {
            bytesField(
                F.STORAGE_TABLE_CHAR_STYLE,
                ProtoWriter().apply {
                    charRuns.forEach { (charIndex, styleId) ->
                        bytesField(
                            F.ATTR_TABLE_ENTRIES,
                            ProtoWriter().apply {
                                varintField(F.ATTR_ENTRY_CHAR_INDEX, charIndex.toLong())
                                bytesField(F.ATTR_ENTRY_OBJECT, reference(styleId))
                            }.toByteArray(),
                        )
                    }
                }.toByteArray(),
            )
        }
    }.toByteArray()

    private fun textShape(storageId: Long, geometry: ByteArray = geometry()) = ProtoWriter().apply {
        bytesField(
            F.SHAPE_INFO_SUPER,
            ProtoWriter().apply {
                bytesField(F.SHAPE_SUPER, ProtoWriter().apply { bytesField(1, geometry) }.toByteArray())
            }.toByteArray(),
        )
        bytesField(F.SHAPE_INFO_OWNED_STORAGE, reference(storageId))
    }.toByteArray()

    private fun parseOne(
        drawables: List<Triple<Long, Int, ByteArray>>,
        extra: List<Triple<Long, Int, ByteArray>> = emptyList(),
    ) = assertNotNull(
        KeynoteDeckParser.parse(
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
                                    floatField(F.SIZE_WIDTH, 1920f)
                                    floatField(F.SIZE_HEIGHT, 1080f)
                                }.toByteArray(),
                            )
                        }.toByteArray(),
                    ),
                    Triple(
                        100L,
                        F.TYPE_KN_SLIDE_NODE,
                        ProtoWriter().apply { bytesField(2, reference(200L)) }.toByteArray(),
                    ),
                    Triple(
                        200L,
                        F.TYPE_KN_SLIDE,
                        ProtoWriter().apply {
                            drawables.forEach { bytesField(7, reference(it.first)) }
                            drawables.forEach { bytesField(42, reference(it.first)) }
                        }.toByteArray(),
                    ),
                ) + drawables + extra,
            )
        ),
        "the fixture scene did not parse",
    ).slides.single()

    private fun segments(path: java.awt.geom.Path2D.Double): List<Pair<Int, DoubleArray>> {
        val out = mutableListOf<Pair<Int, DoubleArray>>()
        val iterator = path.getPathIterator(null)
        while (!iterator.isDone) {
            val coordinates = DoubleArray(6)
            out += iterator.currentSegment(coordinates) to coordinates
            iterator.next()
        }
        return out
    }

    // ── Outlines ──────────────────────────────────────────────────────────────

    @Test
    fun `a bezier outline is normalized into the unit square`() {
        // The path is drawn in a 200×100 natural space; a point at (100, 50) is its centre and has
        // to come back as (0.5, 0.5), not as raw points.
        val slide = parseOne(
            listOf(
                Triple(
                    300L,
                    F.TYPE_TSD_SHAPE,
                    shape(
                        bezierPathSource(
                            200f, 100f,
                            listOf(
                                pathElement(1, point(0f, 0f)),
                                pathElement(2, point(100f, 50f)),
                                pathElement(2, point(200f, 100f)),
                            ),
                        )
                    ),
                )
            )
        )
        val outline = assertNotNull(assertIs<KnDrawable.Shape>(slide.drawables.single().drawable).path)
        val points = segments(outline)
        assertEquals(PathIterator.SEG_MOVETO, points[0].first)
        assertEquals(0.5, points[1].second[0], 1e-6, "x normalized against the natural width")
        assertEquals(0.5, points[1].second[1], 1e-6, "y normalized against the natural height")
        assertEquals(1.0, points[2].second[0], 1e-6)
    }

    @Test
    fun `quadratic and cubic segments survive as curves rather than as lines`() {
        val slide = parseOne(
            listOf(
                Triple(
                    300L,
                    F.TYPE_TSD_SHAPE,
                    shape(
                        bezierPathSource(
                            100f, 100f,
                            listOf(
                                pathElement(1, point(0f, 0f)),
                                pathElement(3, point(50f, 0f), point(100f, 50f)),
                                pathElement(4, point(100f, 75f), point(50f, 100f), point(0f, 100f)),
                                pathElement(5),
                            ),
                        )
                    ),
                )
            )
        )
        val outline = assertNotNull(assertIs<KnDrawable.Shape>(slide.drawables.single().drawable).path)
        val kinds = segments(outline).map { it.first }
        assertTrue(PathIterator.SEG_QUADTO in kinds, "the quad segment was flattened away: $kinds")
        assertTrue(PathIterator.SEG_CUBICTO in kinds, "the cubic segment was flattened away: $kinds")
        assertTrue(PathIterator.SEG_CLOSE in kinds, "the close was dropped: $kinds")
    }

    @Test
    fun `a curve missing its control points is skipped rather than half-drawn`() {
        val slide = parseOne(
            listOf(
                Triple(
                    300L,
                    F.TYPE_TSD_SHAPE,
                    shape(
                        bezierPathSource(
                            100f, 100f,
                            listOf(
                                pathElement(1, point(0f, 0f)),
                                pathElement(4, point(10f, 10f)),
                                pathElement(2, point(100f, 100f)),
                            ),
                        )
                    ),
                )
            )
        )
        val outline = assertNotNull(assertIs<KnDrawable.Shape>(slide.drawables.single().drawable).path)
        val kinds = segments(outline).map { it.first }
        assertTrue(PathIterator.SEG_CUBICTO !in kinds, "an incomplete curve must not be drawn: $kinds")
        assertTrue(PathIterator.SEG_LINETO in kinds, "the rest of the path still draws")
    }

    @Test
    fun `an element type the format does not define abandons the outline`() {
        val slide = parseOne(
            listOf(
                Triple(
                    300L,
                    F.TYPE_TSD_SHAPE,
                    shape(
                        bezierPathSource(
                            100f, 100f,
                            listOf(pathElement(1, point(0f, 0f)), pathElement(99, point(50f, 50f))),
                        )
                    ),
                )
            )
        )
        // No outline means "draw the shape as its rectangle" — the documented degrade.
        assertNull(assertIs<KnDrawable.Shape>(slide.drawables.single().drawable).path)
    }

    @Test
    fun `a natural size of zero does not divide the outline by nothing`() {
        val slide = parseOne(
            listOf(
                Triple(
                    300L,
                    F.TYPE_TSD_SHAPE,
                    shape(
                        bezierPathSource(
                            0f, 0f,
                            listOf(pathElement(1, point(0f, 0f)), pathElement(2, point(1f, 1f))),
                        )
                    ),
                )
            )
        )
        val outline = assertNotNull(assertIs<KnDrawable.Shape>(slide.drawables.single().drawable).path)
        assertTrue(
            segments(outline).all { it.second.all { value -> value.isFinite() } },
            "a degenerate natural size must not produce infinities",
        )
    }

    @Test
    fun `a shape whose path source is not a bezier falls back to its rectangle`() {
        // Scalar paths (rounded rectangles and friends) approximate to the plain rect.
        val scalarPathSource = ProtoWriter().apply { varintField(1, 1) }.toByteArray()
        val slide = parseOne(listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(scalarPathSource))))
        assertNull(assertIs<KnDrawable.Shape>(slide.drawables.single().drawable).path)
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    @Test
    fun `a group keeps its children, in order`() {
        val slide = parseOne(
            drawables = listOf(Triple(300L, F.TYPE_TSD_GROUP, group(listOf(301L, 302L)))),
            extra = listOf(
                Triple(301L, F.TYPE_TSD_SHAPE, shape(geometry = geometry(0f, 0f, 100f, 100f))),
                Triple(302L, F.TYPE_TSD_SHAPE, shape(geometry = geometry(200f, 0f, 100f, 100f))),
            ),
        )
        val parsed = assertIs<KnDrawable.Group>(slide.drawables.single().drawable)
        assertEquals(listOf(301L, 302L), parsed.children.map { it.id })
    }

    @Test
    fun `a group whose child cannot be read gates the slide but keeps the rest`() {
        val slide = parseOne(
            drawables = listOf(Triple(300L, F.TYPE_TSD_GROUP, group(listOf(301L, 302L)))),
            extra = listOf(
                Triple(301L, F.TYPE_TSD_SHAPE, shape()),
                Triple(302L, 9999, ProtoWriter().apply { varintField(99, 1) }.toByteArray()),
            ),
        )
        val parsed = assertIs<KnDrawable.Group>(slide.drawables.single().drawable)
        assertEquals(listOf(301L), parsed.children.map { it.id }, "the readable child survives")
        assertNotNull(slide.gateReason, "an unreadable child still gates the slide")
    }

    // ── Character styling ─────────────────────────────────────────────────────

    @Test
    fun `a character style gives its run its font, size, weight and color`() {
        val slide = parseOne(
            drawables = listOf(Triple(300L, F.TYPE_TSWP_SHAPE_INFO, textShape(storageId = 400L))),
            extra = listOf(
                Triple(400L, F.TYPE_TSWP_STORAGE, storage("Grace", charRuns = listOf(0 to 500L))),
                Triple(
                    500L,
                    F.TYPE_TSWP_CHARACTER_STYLE,
                    characterStyle(
                        fontName = "Helvetica",
                        fontSize = 42f,
                        bold = true,
                        italic = true,
                        fontColor = color(1f, 0f, 0f),
                    ),
                ),
            ),
        )
        val paragraph = assertIs<KnDrawable.Text>(slide.drawables.single().drawable).paragraphs.single()
        assertEquals("Helvetica", paragraph.fontFamily)
        assertEquals(42.0, paragraph.fontSizePt, 1e-6)
        assertTrue(paragraph.bold)
        assertTrue(paragraph.italic)
        assertEquals(Color.RED, paragraph.color)
    }

    @Test
    fun `a style inherits what its parent declares and overrides the rest`() {
        val slide = parseOne(
            drawables = listOf(Triple(300L, F.TYPE_TSWP_SHAPE_INFO, textShape(storageId = 400L))),
            extra = listOf(
                Triple(400L, F.TYPE_TSWP_STORAGE, storage("Peace", charRuns = listOf(0 to 500L))),
                Triple(
                    500L,
                    F.TYPE_TSWP_CHARACTER_STYLE,
                    characterStyle(fontSize = 18f, parentId = 501L),
                ),
                Triple(
                    501L,
                    F.TYPE_TSWP_CHARACTER_STYLE,
                    characterStyle(fontName = "Georgia", fontColor = color(0f, 0f, 1f)),
                ),
            ),
        )
        val paragraph = assertIs<KnDrawable.Text>(slide.drawables.single().drawable).paragraphs.single()
        assertEquals(18.0, paragraph.fontSizePt, 1e-6, "the child's own size wins")
        assertEquals("Georgia", paragraph.fontFamily, "the font comes from the parent")
        assertEquals(Color.BLUE, paragraph.color, "so does the color")
    }

    @Test
    fun `each paragraph takes the style of the run it starts in`() {
        // Two paragraphs, the second starting at character 6 with its own style — the table is
        // keyed by character offset into the whole string, so this is where an off-by-one shows.
        val slide = parseOne(
            drawables = listOf(Triple(300L, F.TYPE_TSWP_SHAPE_INFO, textShape(storageId = 400L))),
            extra = listOf(
                Triple(400L, F.TYPE_TSWP_STORAGE, storage("Grace\rPeace", charRuns = listOf(0 to 500L, 6 to 501L))),
                Triple(500L, F.TYPE_TSWP_CHARACTER_STYLE, characterStyle(fontSize = 20f)),
                Triple(501L, F.TYPE_TSWP_CHARACTER_STYLE, characterStyle(fontSize = 60f)),
            ),
        )
        val paragraphs = assertIs<KnDrawable.Text>(slide.drawables.single().drawable).paragraphs
        assertEquals(listOf("Grace", "Peace"), paragraphs.map { it.text }, "a CR is a paragraph break")
        assertEquals(20.0, paragraphs[0].fontSizePt, 1e-6)
        assertEquals(60.0, paragraphs[1].fontSizePt, 1e-6, "the second run's size, not the first's")
    }

    @Test
    fun `text with no styling at all still parses`() {
        val slide = parseOne(
            drawables = listOf(Triple(300L, F.TYPE_TSWP_SHAPE_INFO, textShape(storageId = 400L))),
            extra = listOf(Triple(400L, F.TYPE_TSWP_STORAGE, storage("Plain"))),
        )
        val paragraph = assertIs<KnDrawable.Text>(slide.drawables.single().drawable).paragraphs.single()
        assertEquals("Plain", paragraph.text)
        assertTrue(paragraph.fontSizePt > 0.0, "an unstyled run still needs a usable size")
    }
}
