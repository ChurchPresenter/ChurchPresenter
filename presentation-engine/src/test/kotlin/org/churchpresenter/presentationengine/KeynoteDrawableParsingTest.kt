package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.Fixtures.ProtoWriter
import org.churchpresenter.presentationengine.keynote.KeynoteDeckParser
import org.churchpresenter.presentationengine.keynote.KnDrawable
import org.churchpresenter.presentationengine.keynote.KnFields as F
import java.awt.Color
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
 * The drawables on a Keynote slide: shapes and their fills, images, and movies.
 *
 * [KeynoteDeckParserTest] walks the document/show/slide traversal and the text path; this covers
 * what hangs off a slide once it is reached. Two things make it worth pinning separately. **Fills
 * resolve through a style chain**, so a solid color that fails to resolve leaves a shape drawn as
 * nothing rather than as an error. And **an asset the package cannot name gates its slide**: an
 * image whose data id is missing from the package metadata, a masked image, a movie without its
 * asset — each has to degrade the slide to a static picture with a stated reason, never render a
 * hole where the picture was.
 *
 * Field numbers come from `KnFields`, which is vendored from psobot/keynote-parser with citations.
 */
class KeynoteDrawableParsingTest {

    private val temp: File = Files.createTempDirectory("keynote-drawable-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Graph builders ────────────────────────────────────────────────────────

    private fun reference(id: Long) = ProtoWriter().apply { varintField(F.REFERENCE_IDENTIFIER, id) }.toByteArray()

    private fun dataReference(id: Long) =
        ProtoWriter().apply { varintField(F.DATA_REFERENCE_IDENTIFIER, id) }.toByteArray()

    private fun document(showId: Long) = ProtoWriter().apply { bytesField(2, reference(showId)) }.toByteArray()

    private fun show(nodeIds: List<Long>): ByteArray {
        val size = ProtoWriter().apply { floatField(1, 1920f); floatField(2, 1080f) }.toByteArray()
        val tree = ProtoWriter().apply { nodeIds.forEach { bytesField(2, reference(it)) } }.toByteArray()
        return ProtoWriter().apply { bytesField(3, tree); bytesField(4, size) }.toByteArray()
    }

    private fun slideNode(slideId: Long) = ProtoWriter().apply { bytesField(2, reference(slideId)) }.toByteArray()

    private fun slideWithDrawables(ids: List<Long>) = ProtoWriter().apply {
        ids.forEach { bytesField(7, reference(it)) }
        ids.forEach { bytesField(42, reference(it)) }
    }.toByteArray()

    private fun geometry(x: Float = 0f, y: Float = 0f, w: Float = 100f, h: Float = 50f): ByteArray {
        val point = ProtoWriter().apply { floatField(1, x); floatField(2, y) }.toByteArray()
        val size = ProtoWriter().apply { floatField(1, w); floatField(2, h) }.toByteArray()
        return ProtoWriter().apply { bytesField(1, point); bytesField(2, size) }.toByteArray()
    }

    /** TSP.Color in the rgb model, components 0..1. */
    private fun color(r: Float, g: Float, b: Float, a: Float = 1f) = ProtoWriter().apply {
        varintField(F.COLOR_MODEL, 1)
        floatField(F.COLOR_R, r)
        floatField(F.COLOR_G, g)
        floatField(F.COLOR_B, b)
        floatField(F.COLOR_A, a)
    }.toByteArray()

    private fun solidFill(rgb: ByteArray) = ProtoWriter().apply { bytesField(F.FILL_COLOR, rgb) }.toByteArray()

    private fun gradientFill(vararg stops: ByteArray) = ProtoWriter().apply {
        bytesField(
            F.FILL_GRADIENT,
            ProtoWriter().apply {
                stops.forEach { stop ->
                    bytesField(
                        F.GRADIENT_STOPS,
                        ProtoWriter().apply { bytesField(F.GRADIENT_STOP_COLOR, stop) }.toByteArray(),
                    )
                }
            }.toByteArray(),
        )
    }.toByteArray()

    private fun imageFill(dataId: Long) = ProtoWriter().apply {
        bytesField(
            F.FILL_IMAGE,
            ProtoWriter().apply { bytesField(F.IMAGE_FILL_DATA, dataReference(dataId)) }.toByteArray(),
        )
    }.toByteArray()

    /** TSD.ShapeStyleArchive: properties at field 11, fill/stroke/opacity inside it. */
    private fun shapeStyle(
        fill: ByteArray? = null,
        strokeColor: ByteArray? = null,
        strokeWidth: Float? = null,
        opacity: Float? = null,
    ) = ProtoWriter().apply {
        bytesField(
            F.SHAPE_STYLE_PROPERTIES,
            ProtoWriter().apply {
                if (fill != null) bytesField(F.SHAPE_PROPS_FILL, fill)
                if (strokeColor != null || strokeWidth != null) {
                    bytesField(
                        F.SHAPE_PROPS_STROKE,
                        ProtoWriter().apply {
                            if (strokeColor != null) bytesField(F.STROKE_COLOR, strokeColor)
                            if (strokeWidth != null) floatField(F.STROKE_WIDTH, strokeWidth)
                        }.toByteArray(),
                    )
                }
                if (opacity != null) floatField(F.SHAPE_PROPS_OPACITY, opacity)
            }.toByteArray(),
        )
    }.toByteArray()

    /** TSD.ShapeArchive: geometry through its drawable super, style by reference. */
    private fun shape(styleId: Long? = null, geometry: ByteArray = geometry()) = ProtoWriter().apply {
        bytesField(F.SHAPE_SUPER, ProtoWriter().apply { bytesField(1, geometry) }.toByteArray())
        if (styleId != null) bytesField(F.SHAPE_STYLE, reference(styleId))
    }.toByteArray()

    private fun image(dataId: Long?, maskId: Long? = null) = ProtoWriter().apply {
        bytesField(F.IMAGE_SUPER, ProtoWriter().apply { bytesField(1, geometry()) }.toByteArray())
        if (dataId != null) bytesField(F.IMAGE_DATA, dataReference(dataId))
        if (maskId != null) bytesField(F.IMAGE_MASK, reference(maskId))
    }.toByteArray()

    private fun movie(dataId: Long?, posterId: Long? = null) = ProtoWriter().apply {
        bytesField(F.MOVIE_SUPER, ProtoWriter().apply { bytesField(1, geometry()) }.toByteArray())
        if (dataId != null) bytesField(F.MOVIE_DATA, dataReference(dataId))
        if (posterId != null) bytesField(F.MOVIE_POSTER, dataReference(posterId))
    }.toByteArray()

    /** TSP.PackageMetadata — what names a data id, and so what makes an asset resolvable. */
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

    /** One slide holding [drawables], plus whatever supporting objects are given. */
    private fun parseSlide(
        drawables: List<Triple<Long, Int, ByteArray>>,
        extra: List<Triple<Long, Int, ByteArray>> = emptyList(),
    ) = KeynoteDeckParser.parse(
        Fixtures.writeKeynoteDir(
            Files.createTempDirectory(temp.toPath(), "deck").toFile(),
            listOf(
                Triple(1L, F.TYPE_KN_DOCUMENT, document(2L)),
                Triple(2L, F.TYPE_KN_SHOW, show(listOf(100L))),
                Triple(100L, F.TYPE_KN_SLIDE_NODE, slideNode(200L)),
                Triple(200L, F.TYPE_KN_SLIDE, slideWithDrawables(drawables.map { it.first })),
            ) + drawables + extra,
        )
    )

    // ── Shape fills ───────────────────────────────────────────────────────────

    @Test
    fun `a solid fill resolves through the shape's style`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(styleId = 400L))),
                extra = listOf(
                    Triple(400L, F.TYPE_TSD_SHAPE_STYLE, shapeStyle(fill = solidFill(color(1f, 0f, 0f)))),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Shape>(scene.slides.single().drawables.single().drawable)
        assertEquals(Color.RED, assertNotNull(drawable.fill?.color), "a fill that does not resolve draws nothing")
    }

    @Test
    fun `a gradient is approximated by its first stop`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(styleId = 400L))),
                extra = listOf(
                    Triple(
                        400L,
                        F.TYPE_TSD_SHAPE_STYLE,
                        shapeStyle(fill = gradientFill(color(0f, 0f, 1f), color(0f, 1f, 0f))),
                    ),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Shape>(scene.slides.single().drawables.single().drawable)
        assertEquals(Color.BLUE, assertNotNull(drawable.fill?.color), "the first stop stands in for the ramp")
    }

    @Test
    fun `an image fill keeps the file it points at`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(styleId = 400L))),
                extra = listOf(
                    Triple(400L, F.TYPE_TSD_SHAPE_STYLE, shapeStyle(fill = imageFill(dataId = 7L))),
                    Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(7L to "backdrop.png"))),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Shape>(scene.slides.single().drawables.single().drawable)
        assertEquals("backdrop.png", drawable.fill?.imageFile)
    }

    @Test
    fun `an image fill the package cannot name leaves the shape unfilled rather than broken`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(styleId = 400L))),
                extra = listOf(
                    Triple(400L, F.TYPE_TSD_SHAPE_STYLE, shapeStyle(fill = imageFill(dataId = 7L))),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Shape>(scene.slides.single().drawables.single().drawable)
        assertNull(drawable.fill, "an unresolvable image fill is no fill, not a gate")
    }

    @Test
    fun `stroke and opacity come off the same style`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape(styleId = 400L))),
                extra = listOf(
                    Triple(
                        400L,
                        F.TYPE_TSD_SHAPE_STYLE,
                        shapeStyle(strokeColor = color(0f, 0f, 0f), strokeWidth = 3f, opacity = 0.5f),
                    ),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Shape>(scene.slides.single().drawables.single().drawable)
        assertEquals(Color.BLACK, drawable.strokeColor)
        assertEquals(3.0, drawable.strokeWidthPt, 1e-6)
        assertEquals(0.5, drawable.opacity, 1e-6)
    }

    @Test
    fun `a shape with no style at all is still a shape`() {
        val scene = assertNotNull(
            parseSlide(drawables = listOf(Triple(300L, F.TYPE_TSD_SHAPE, shape())))
        )
        val drawable = assertIs<KnDrawable.Shape>(scene.slides.single().drawables.single().drawable)
        assertNull(drawable.fill)
        assertEquals(1.0, drawable.opacity, 1e-6, "no style means fully opaque, not invisible")
    }

    // ── Images ────────────────────────────────────────────────────────────────

    @Test
    fun `an image with a named, renderable asset parses`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(dataId = 5L))),
                extra = listOf(
                    Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(5L to "photo.jpg"))),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Image>(scene.slides.single().drawables.single().drawable)
        assertEquals("photo.jpg", drawable.dataFile)
    }

    @Test
    fun `an image in a format the renderer cannot draw gates its slide`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(dataId = 5L))),
                extra = listOf(
                    Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(5L to "artwork.heic"))),
                ),
            )
        )
        val slide = scene.slides.single()
        assertNotNull(slide.gateReason, "an undrawable image must gate rather than leave a hole")
        assertTrue(slide.gateReason!!.contains("heic"), "the reason names the format: ${slide.gateReason}")
    }

    @Test
    fun `a masked image gates its slide`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(dataId = 5L, maskId = 600L))),
                extra = listOf(
                    Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(5L to "photo.jpg"))),
                ),
            )
        )
        assertTrue(
            assertNotNull(scene.slides.single().gateReason).contains("mask"),
            "the reason names the mask: ${scene.slides.single().gateReason}",
        )
    }

    @Test
    fun `an image with no data reference gates its slide`() {
        val scene = assertNotNull(
            parseSlide(drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(dataId = null))))
        )
        assertNotNull(scene.slides.single().gateReason)
    }

    @Test
    fun `an image whose data id is not in the package metadata gates its slide`() {
        val scene = assertNotNull(
            parseSlide(drawables = listOf(Triple(300L, F.TYPE_TSD_IMAGE, image(dataId = 5L))))
        )
        assertTrue(assertNotNull(scene.slides.single().gateReason).contains("5"), "the reason names the id")
    }

    // ── Movies ────────────────────────────────────────────────────────────────

    @Test
    fun `a movie keeps both its asset and its poster`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_MOVIE, movie(dataId = 5L, posterId = 6L))),
                extra = listOf(
                    Triple(
                        900L,
                        F.TYPE_TSP_PACKAGE_METADATA,
                        packageMetadata(mapOf(5L to "sermon.mov", 6L to "poster.jpg")),
                    ),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Movie>(scene.slides.single().drawables.single().drawable)
        assertEquals("sermon.mov", drawable.videoFile)
        assertEquals("poster.jpg", drawable.posterFile)
    }

    @Test
    fun `a movie with no poster still plays`() {
        val scene = assertNotNull(
            parseSlide(
                drawables = listOf(Triple(300L, F.TYPE_TSD_MOVIE, movie(dataId = 5L))),
                extra = listOf(
                    Triple(900L, F.TYPE_TSP_PACKAGE_METADATA, packageMetadata(mapOf(5L to "sermon.mov"))),
                ),
            )
        )
        val drawable = assertIs<KnDrawable.Movie>(scene.slides.single().drawables.single().drawable)
        assertNull(drawable.posterFile)
        assertNull(scene.slides.single().gateReason, "a missing poster is not a reason to gate")
    }

    @Test
    fun `a movie whose asset cannot be resolved gates its slide`() {
        val scene = assertNotNull(
            parseSlide(drawables = listOf(Triple(300L, F.TYPE_TSD_MOVIE, movie(dataId = 5L))))
        )
        assertNotNull(scene.slides.single().gateReason)
    }
}
