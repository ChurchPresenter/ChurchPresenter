package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.Fixtures.ProtoWriter
import org.churchpresenter.presentationengine.model.DeckFormat
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.LayerSpec
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The **native** Keynote path end to end: a `.key` whose IWA graph parses into a scene becomes a
 * deck of per-shape layers, not a flat picture per slide.
 *
 * [KeynoteDeckParserTest] stops at the scene and [KeynoteParagraphBuildTest] drives the planner
 * with hand-built models; nothing joined the two through [PresentationLoader], which is where the
 * decisions that matter live: which slides are gated to a static fallback, whether a static source
 * is aligned well enough to *be* that fallback, and whether a deck that gates everything is better
 * off going down the plain static cascade instead. Those choices are invisible in a scene test and
 * decide what an operator actually sees.
 *
 * The object-graph shapes here are the ones [KeynoteDeckParserTest] documents from a real deck
 * dumped with `dumpKeynote`.
 */
class KeynoteNativeDeckTest {

    private val temp: File = Files.createTempDirectory("keynote-native-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Graph builders ────────────────────────────────────────────────────────

    private fun reference(id: Long): ByteArray = ProtoWriter().apply { varintField(1, id) }.toByteArray()

    private fun document(showId: Long): ByteArray =
        ProtoWriter().apply { bytesField(2, reference(showId)) }.toByteArray()

    private fun show(width: Float, height: Float, nodeIds: List<Long>): ByteArray {
        val size = ProtoWriter().apply { floatField(1, width); floatField(2, height) }.toByteArray()
        val tree = ProtoWriter().apply { nodeIds.forEach { bytesField(2, reference(it)) } }.toByteArray()
        return ProtoWriter().apply { bytesField(3, tree); bytesField(4, size) }.toByteArray()
    }

    private fun slideNode(slideId: Long): ByteArray =
        ProtoWriter().apply { bytesField(2, reference(slideId)) }.toByteArray()

    private fun slideWithDrawables(drawableIds: List<Long>): ByteArray =
        ProtoWriter().apply {
            drawableIds.forEach { bytesField(7, reference(it)) }
            drawableIds.forEach { bytesField(42, reference(it)) }
        }.toByteArray()

    private fun geometry(x: Float, y: Float, w: Float, h: Float): ByteArray {
        val point = ProtoWriter().apply { floatField(1, x); floatField(2, y) }.toByteArray()
        val size = ProtoWriter().apply { floatField(1, w); floatField(2, h) }.toByteArray()
        return ProtoWriter().apply { bytesField(1, point); bytesField(2, size) }.toByteArray()
    }

    private fun textShape(x: Float, y: Float, w: Float, h: Float, storageId: Long): ByteArray {
        val drawableArchive = ProtoWriter().apply { bytesField(1, geometry(x, y, w, h)) }.toByteArray()
        val shapeArchive = ProtoWriter().apply { bytesField(1, drawableArchive) }.toByteArray()
        return ProtoWriter().apply {
            bytesField(1, shapeArchive)
            bytesField(4, reference(storageId))
        }.toByteArray()
    }

    private fun storage(vararg text: String): ByteArray =
        ProtoWriter().apply { text.forEach { stringField(3, it) } }.toByteArray()

    /** An object of a type the parser does not recognize — enough to gate its slide. */
    private fun unknownDrawable(): ByteArray = ProtoWriter().apply { varintField(99, 1) }.toByteArray()

    private fun jpeg(): ByteArray {
        val image = BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { paint = Color.DARK_GRAY; fillRect(0, 0, 64, 36); dispose() }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    /**
     * Writes a package-form `.key` holding [objects], plus one `Data/st-<n>.jpg` per slide when
     * [thumbnailsPerSlide] and, when [previewPdfPages] is set, a real `QuickLook/Preview.pdf`.
     */
    private fun bundle(
        objects: List<Triple<Long, Int, ByteArray>>,
        slideIwaIds: List<Long> = listOf(1L),
        thumbnailsPerSlide: Int = 0,
        previewPdfPages: Int? = null,
        name: String = "native",
    ): File {
        val dir = Files.createTempDirectory(temp.toPath(), name).toFile()
        val key = File(dir, "fixture.key").apply { mkdirs() }
        File(key, "Index").mkdirs()
        File(key, "Index/Test.iwa").writeBytes(Fixtures.buildIwa(objects))
        for (id in slideIwaIds) File(key, "Index/Slide-$id.iwa").writeBytes(ByteArray(4))
        if (thumbnailsPerSlide > 0) {
            File(key, "Data").mkdirs()
            repeat(thumbnailsPerSlide) { File(key, "Data/st-${it + 1}.jpg").writeBytes(jpeg()) }
        }
        if (previewPdfPages != null) {
            File(key, "QuickLook").mkdirs()
            val pdf = Fixtures.createPdf(dir, pages = previewPdfPages, name = "preview.pdf")
            File(key, "QuickLook/Preview.pdf").writeBytes(pdf.readBytes())
        }
        return key
    }

    /** One 1920×1080 slide carrying a single text shape. */
    private fun oneTextSlide() = listOf(
        Triple(1L, 1, document(2L)),
        Triple(2L, 2, show(1920f, 1080f, listOf(100L))),
        Triple(100L, 4, slideNode(200L)),
        Triple(200L, 5, slideWithDrawables(listOf(300L))),
        Triple(300L, 2011, textShape(100f, 120f, 800f, 200f, storageId = 400L)),
        Triple(400L, 2001, storage("Grace and peace")),
    )

    // ── Native decks ──────────────────────────────────────────────────────────

    @Test
    fun `a parseable deck keeps the scene's own geometry and renders natively`() {
        val result = assertIs<LoadResult.Success>(PresentationLoader.load(bundle(oneTextSlide())))
        val deck = result.deck

        assertEquals(DeckFormat.KEYNOTE, deck.format)
        assertEquals(1920.0, deck.slideWidthPt, 1e-6, "the size comes from the show, not a default")
        assertEquals(1080.0, deck.slideHeightPt, 1e-6)
        assertEquals(1, deck.slideCount)
        assertEquals(Fidelity.NATIVE, deck.slides.single().fidelity, "a parseable slide is not a picture")
        assertTrue(deck.warnings.isEmpty(), "nothing degraded, so nothing to report: ${deck.warnings}")
    }

    @Test
    fun `a slide with nothing to animate is drawn as one native layer covering the slide`() {
        // Per-shape layers exist to be animated independently. With no build on the slide there is
        // no timeline to remap them onto, so the planner's layers are collapsed back into a single
        // full-slide composite — still NATIVE (the engine renders it), just not split up.
        val result = assertIs<LoadResult.Success>(PresentationLoader.load(bundle(oneTextSlide())))
        val slide = result.deck.slides.single()

        val layer = assertIs<LayerSpec.StaticComposite>(slide.layers.single())
        assertEquals(0.0, layer.boundsPt.x, 1e-6)
        assertEquals(0.0, layer.boundsPt.y, 1e-6)
        assertEquals(1920.0, layer.boundsPt.w, 1e-6, "the composite spans the whole slide")
        assertEquals(1080.0, layer.boundsPt.h, 1e-6)
        assertEquals(null, slide.timeline, "no build, no timeline")
    }

    // ── Gating ────────────────────────────────────────────────────────────────

    @Test
    fun `a slide the parser cannot fully read is gated to a fallback and reported`() {
        val objects = listOf(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L, 101L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slideWithDrawables(listOf(300L))),
            Triple(300L, 2011, textShape(10f, 10f, 100f, 50f, storageId = 400L)),
            Triple(400L, 2001, storage("readable")),
            Triple(101L, 4, slideNode(201L)),
            Triple(201L, 5, slideWithDrawables(listOf(301L))),
            Triple(301L, 9999, unknownDrawable()),
        )
        val key = bundle(objects, slideIwaIds = listOf(1L, 2L), thumbnailsPerSlide = 2)

        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(key)).deck
        assertEquals(2, deck.slideCount)
        assertEquals(Fidelity.NATIVE, deck.slides[0].fidelity)
        assertEquals(Fidelity.STATIC_FALLBACK, deck.slides[1].fidelity, "the unreadable slide is gated")
        assertTrue(
            deck.slides[1].layers.single() is LayerSpec.StaticComposite,
            "a gated slide is drawn as one picture",
        )
        assertTrue(
            deck.warnings.any { it.contains("Slide 2") },
            "the operator has to be told which slide degraded, got ${deck.warnings}",
        )
    }

    @Test
    fun `a fully-gated deck with no aligned fallback drops to the plain static cascade`() {
        // Nothing renders natively and the thumbnails do not line up with the scene, so a native
        // deck would be a set of blank slides. The loader abandons the native path entirely and
        // lets the static cascade show the thumbnails it does have.
        val objects = listOf(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slideWithDrawables(listOf(300L))),
            Triple(300L, 9999, unknownDrawable()),
        )
        val key = bundle(objects, slideIwaIds = listOf(1L, 2L), thumbnailsPerSlide = 2, name = "all-gated")

        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(key)).deck
        assertEquals(2, deck.slideCount, "the cascade shows both thumbnails, not the scene's one slide")
        assertTrue(deck.slides.all { it.fidelity == Fidelity.STATIC_FALLBACK })
        // Static-cascade decks are sized from the thumbnail default, not from the scene.
        assertEquals(720.0, deck.slideWidthPt, 1.0, "the scene's 1920pt is not carried into a static deck")
    }

    @Test
    fun `a fully-gated deck keeps its native shell when a static source lines up`() {
        // Same document, but now one thumbnail for the one slide: the gated slide has something
        // real to show, so the deck stays on the native path with that source attached.
        val objects = listOf(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slideWithDrawables(listOf(300L))),
            Triple(300L, 9999, unknownDrawable()),
        )
        val key = bundle(objects, thumbnailsPerSlide = 1, name = "gated-aligned")

        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(key)).deck
        assertEquals(1, deck.slideCount)
        assertEquals(1920.0, deck.slideWidthPt, 1e-6, "the scene still decides the geometry")
        assertEquals(Fidelity.STATIC_FALLBACK, deck.slides.single().fidelity)
    }

    @Test
    fun `a misaligned static source is not used as the fallback`() {
        // Three thumbnails for two slides means they cannot be matched up; offering slide 3's
        // picture for slide 2 would be worse than having no fallback at all.
        val objects = listOf(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(1920f, 1080f, listOf(100L, 101L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slideWithDrawables(listOf(300L))),
            Triple(300L, 2011, textShape(10f, 10f, 100f, 50f, storageId = 400L)),
            Triple(400L, 2001, storage("readable")),
            Triple(101L, 4, slideNode(201L)),
            Triple(201L, 5, slideWithDrawables(listOf(301L))),
            Triple(301L, 2011, textShape(10f, 10f, 100f, 50f, storageId = 401L)),
            Triple(401L, 2001, storage("also readable")),
        )
        val key = bundle(objects, slideIwaIds = listOf(1L, 2L, 3L), thumbnailsPerSlide = 3, name = "misaligned")

        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(key)).deck
        assertEquals(2, deck.slideCount, "the scene decides the slide count, not the thumbnails")
        assertTrue(deck.slides.all { it.fidelity == Fidelity.NATIVE })
    }

    @Test
    fun `an embedded preview aligned with the scene is preferred as the fallback`() {
        val objects = oneTextSlide()
        val key = bundle(objects, thumbnailsPerSlide = 1, previewPdfPages = 1, name = "aligned-preview")

        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(key)).deck
        assertEquals(1, deck.slideCount)
        assertEquals(Fidelity.NATIVE, deck.slides.single().fidelity)
    }
}
