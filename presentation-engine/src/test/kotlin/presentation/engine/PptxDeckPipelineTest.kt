package presentation.engine

import org.apache.poi.sl.usermodel.PictureData
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFRelation
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture
import presentation.engine.model.Deck
import presentation.engine.model.DeckFormat
import presentation.engine.model.Fidelity
import presentation.engine.model.LayerSpec
import java.awt.Color
import java.awt.Rectangle
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
 * A PPTX from file to rendered layers: what the planner decides, what the loader records, and what
 * the rasterizer draws for each layer.
 *
 * [LayeredRenderTest] proves the layers composite back into the full-slide render, which is the
 * fidelity check. What it does not cover is the *decisions* around that: which shapes stay
 * flattened in a background band, which start hidden because an entrance is waiting on them,
 * whether a shape inside a group counts as animated when the group is what the timing names, and
 * whether speaker notes survive the trip. Each of those changes what an operator sees on a click
 * or on the stage monitor, and none of them shows up in a pixel comparison of the settled slide.
 */
class PptxDeckPipelineTest {

    private val temp: File = Files.createTempDirectory("pptx-pipeline-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun loadDeck(file: File): Deck =
        assertIs<LoadResult.Success>(PresentationLoader.load(file)).deck

    /** A deck whose only slide has [notes] and a static box. */
    private fun deckWithNotes(notes: String): File {
        val file = File(temp, "notes.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            slide.createAutoShape().apply {
                anchor = Rectangle(40, 40, 200, 120)
                fillColor = Color(0x33, 0x66, 0x99)
            }
            val notesSlide = ppt.getNotesSlide(slide)
            val placeholder = notesSlide.placeholders.filterIsInstance<XSLFTextShape>()
                .firstOrNull { it.textType?.name?.contains("BODY") == true }
                ?: notesSlide.placeholders.getOrNull(1) as? XSLFTextShape
            placeholder?.text = notes
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    // ── What the loader records ───────────────────────────────────────────────

    @Test
    fun `speaker notes reach the deck`() {
        val deck = loadDeck(deckWithNotes("Read slowly, then pause."))
        assertEquals(DeckFormat.PPTX, deck.format)
        assertTrue(
            deck.slides.single().notes.contains("Read slowly"),
            "the stage monitor shows these, got '${deck.slides.single().notes}'",
        )
    }

    @Test
    fun `a slide with no animation is one static composite and no timeline`() {
        val deck = loadDeck(deckWithNotes(""))
        val slide = deck.slides.single()
        assertNull(slide.timeline, "nothing to animate means no timeline at all")
        assertIs<LayerSpec.StaticComposite>(slide.layers.single())
        assertEquals(Fidelity.NATIVE, slide.fidelity)
    }

    @Test
    fun `the deck's page size comes from the document, not from a default`() {
        val file = File(temp, "wide.pptx")
        XMLSlideShow().use { ppt ->
            ppt.pageSize = java.awt.Dimension(1440, 810)
            ppt.createSlide().createAutoShape().anchor = Rectangle(10, 10, 100, 100)
            file.outputStream().use { ppt.write(it) }
        }
        val deck = loadDeck(file)
        assertEquals(1440.0, deck.slideWidthPt, 1.0)
        assertEquals(810.0, deck.slideHeightPt, 1.0)
    }

    // ── What the planner decides ──────────────────────────────────────────────

    /**
     * Three shapes, only the middle one animated: the shapes below it flatten into the bottom
     * band, it becomes its own layer, and the shape above it starts a new band.
     */
    private fun bandedDeck(): File {
        val file = File(temp, "banded.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            slide.createAutoShape().apply {
                anchor = Rectangle(20, 20, 100, 100)
                fillColor = Color.RED
            }
            val animated = slide.createAutoShape().apply {
                anchor = Rectangle(200, 20, 100, 100)
                fillColor = Color.GREEN
            }
            slide.createAutoShape().apply {
                anchor = Rectangle(400, 20, 100, 100)
                fillColor = Color.BLUE
            }
            Fixtures.addTiming(slide, listOf(Fixtures.TimingTarget(animated.shapeId.toLong())))
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    @Test
    fun `an animated shape gets its own layer between two background bands`() {
        val slide = loadDeck(bandedDeck()).slides.single()
        val kinds = slide.layers.map { it::class.simpleName }
        assertEquals(
            listOf("Background", "Shape", "Background"),
            kinds,
            "the un-animated shapes flatten around the animated one, got $kinds",
        )
        val bottom = assertIs<LayerSpec.Background>(slide.layers.first())
        assertEquals(listOf(0), bottom.shapeIndexes, "the shape below the animated one is flattened in")
        val top = assertIs<LayerSpec.Background>(slide.layers.last())
        assertEquals(listOf(2), top.shapeIndexes, "and the one above it starts a new band")
    }

    @Test
    fun `a shape waiting on an entrance starts hidden`() {
        val slide = loadDeck(bandedDeck()).slides.single()
        val animated = assertIs<LayerSpec.Shape>(slide.layers[1])
        assertTrue(!animated.initiallyVisible, "an entrance target must not be on screen before its click")
        assertTrue(
            slide.layers.filterIsInstance<LayerSpec.Background>().all { it.initiallyVisible },
            "the bands are not animated, so they are visible from the start",
        )
    }

    @Test
    fun `a shape inside a group counts as animated when the timing names the group`() {
        // The timing targets the group's own id; the planner has to see the group as animated
        // rather than flattening it into a band, or the animation has nothing to move.
        val file = File(temp, "grouped.pptx")
        var groupId = 0L
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val group = slide.createGroup()
            group.anchor = Rectangle(50, 50, 300, 200)
            group.interiorAnchor = Rectangle(0, 0, 300, 200)
            group.createAutoShape().apply {
                anchor = Rectangle(0, 0, 100, 100)
                fillColor = Color.RED
            }
            group.createAutoShape().apply {
                anchor = Rectangle(150, 0, 100, 100)
                fillColor = Color.GREEN
            }
            groupId = group.shapeId.toLong()
            Fixtures.addTiming(slide, listOf(Fixtures.TimingTarget(groupId)))
            file.outputStream().use { ppt.write(it) }
        }
        val slide = loadDeck(file).slides.single()
        assertTrue(
            slide.layers.any { it is LayerSpec.Shape },
            "the group did not become its own animated layer, got ${slide.layers.map { it::class.simpleName }}",
        )
    }

    // ── Per-paragraph builds ──────────────────────────────────────────────────

    /** A three-paragraph text box built one paragraph per click. */
    private fun paragraphDeck(): File {
        val file = File(temp, "paragraphs.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val box = slide.createTextBox()
            box.anchor = Rectangle(40, 40, 600, 300)
            box.text = "Alpha"
            box.addNewTextParagraph().addNewTextRun().setText("Beta")
            box.addNewTextParagraph().addNewTextRun().setText("Gamma")
            Fixtures.addTiming(
                slide,
                listOf(Fixtures.TimingTarget(box.shapeId.toLong(), paragraphs = listOf(0, 1, 2))),
            )
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    @Test
    fun `each paragraph of a built text box becomes its own layer, in order`() {
        val slide = loadDeck(paragraphDeck()).slides.single()
        val paragraphs = slide.layers.filterIsInstance<LayerSpec.ParagraphText>()
        assertEquals(3, paragraphs.size, "one layer per paragraph")
        assertEquals(listOf(0, 1, 2), paragraphs.map { it.paragraphIndex })
        assertTrue(paragraphs.all { !it.initiallyVisible }, "each waits for its own click")
    }

    @Test
    fun `the timeline gives each paragraph its own click`() {
        val timeline = assertNotNull(loadDeck(paragraphDeck()).slides.single().timeline)
        assertEquals(3, timeline.stepCount, "three paragraphs, three clicks")
        val layerIds = timeline.steps.map { it.intervals.single().layerId }
        assertEquals(layerIds.distinct(), layerIds, "each step drives a different paragraph")
    }

    @Test
    fun `a paragraph layer draws its own line and not its neighbours`() {
        val deck = loadDeck(paragraphDeck())
        DeckRasterizer(deck, targetWidthPx = 960).use { rasterizer ->
            val layers = rasterizer.rasterizeSlideLayers(0)
            val paragraphLayers = layers.filter { it.spec is LayerSpec.ParagraphText }
            assertEquals(3, paragraphLayers.size)

            val inkCounts = paragraphLayers.map { layer -> countInk(layer.image) }
            assertTrue(inkCounts.all { it > 0 }, "every paragraph has to draw something, got $inkCounts")
            // Each line is roughly the same amount of ink; one layer holding all three would be a
            // multiple of the others.
            val largest = inkCounts.max()
            val smallest = inkCounts.min()
            assertTrue(
                largest < smallest * 3,
                "one layer appears to hold more than its own line: $inkCounts",
            )
        }
    }

    private fun countInk(image: BufferedImage): Int {
        var count = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 != 0) count++
            }
        }
        return count
    }

    // ── Rasterizing the deck ──────────────────────────────────────────────────

    @Test
    fun `the final frame of an animated slide is rendered with every build complete`() {
        val deck = loadDeck(paragraphDeck())
        DeckRasterizer(deck, targetWidthPx = 640).use { rasterizer ->
            val frame = rasterizer.renderFinalFrame(0)
            assertEquals(640, frame.width)
            assertTrue(countInk(frame) > 0, "the settled slide draws all three paragraphs")
        }
    }

    @Test
    fun `every layer of a slide is rasterized, each at its own offset`() {
        val deck = loadDeck(bandedDeck())
        DeckRasterizer(deck, targetWidthPx = 800).use { rasterizer ->
            val layers = rasterizer.rasterizeSlideLayers(0)
            assertEquals(deck.slides.single().layers.size, layers.size, "one raster per planned layer")
            assertTrue(layers.all { it.image.width >= 1 && it.image.height >= 1 })
            assertTrue(
                layers.any { it.offsetXPx > 0 || it.offsetYPx > 0 },
                "a shape layer that is not at the origin has to carry its offset",
            )
        }
    }

    @Test
    fun `rasterizing the same slide twice gives the same result`() {
        val deck = loadDeck(bandedDeck())
        DeckRasterizer(deck, targetWidthPx = 400).use { rasterizer ->
            val first = rasterizer.renderFinalFrame(0)
            val second = rasterizer.renderFinalFrame(0)
            assertEquals(first.width, second.width)
            assertEquals(countInk(first), countInk(second), "rendering must not depend on how often it has run")
        }
    }

    /** The same three-paragraph box, but bulleted — bullets are drawn outside the run's own fill. */
    private fun bulletedParagraphDeck(): File {
        val file = File(temp, "bulleted.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val box = slide.createTextBox()
            box.anchor = Rectangle(40, 40, 600, 300)
            box.text = "Alpha"
            box.addNewTextParagraph().addNewTextRun().setText("Beta")
            box.addNewTextParagraph().addNewTextRun().setText("Gamma")
            box.textParagraphs.forEach { paragraph ->
                paragraph.isBullet = true
                paragraph.bulletCharacter = "\u2022"
            }
            Fixtures.addTiming(
                slide,
                listOf(Fixtures.TimingTarget(box.shapeId.toLong(), paragraphs = listOf(0, 1, 2))),
            )
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    @Test
    fun `a bulleted paragraph layer draws its own bullet and hides the others`() {
        // The bullet glyph is not part of any run, so hiding a paragraph's runs is not enough — its
        // bullet has to be hidden as well or every bullet appears on every layer.
        val deck = loadDeck(bulletedParagraphDeck())
        DeckRasterizer(deck, targetWidthPx = 960).use { rasterizer ->
            val paragraphLayers = rasterizer.rasterizeSlideLayers(0).filter { it.spec is LayerSpec.ParagraphText }
            assertEquals(3, paragraphLayers.size)
            val inkCounts = paragraphLayers.map { countInk(it.image) }
            assertTrue(inkCounts.all { it > 0 }, "each layer draws its own line and bullet: $inkCounts")
            assertTrue(
                inkCounts.max() < inkCounts.min() * 3,
                "a layer carrying every bullet would be far heavier than the others: $inkCounts",
            )
        }
    }

    @Test
    fun `isolating a paragraph leaves the document's own XML unchanged`() {
        // The isolation is a reversible mutation of the live XML; if a restore is missed, the deck
        // on disk (and every later render) keeps a transparent run or a hidden bullet.
        val file = bulletedParagraphDeck()
        val before = file.readBytes().size
        val deck = loadDeck(file)
        DeckRasterizer(deck, targetWidthPx = 400).use { rasterizer ->
            rasterizer.rasterizeSlideLayers(0)
            val frame = rasterizer.renderFinalFrame(0)
            assertTrue(
                countInk(frame) > 0,
                "the settled slide still draws all three paragraphs after they were isolated one by one",
            )
        }
        assertEquals(before, file.readBytes().size, "the file on disk was rewritten")
    }

    // ── Embedded video ────────────────────────────────────────────────────────

    /**
     * A slide carrying a picture shape marked as a video, with its media part really in the
     * package — the shape PowerPoint writes for an embedded movie. POI has no high-level API for
     * it, so the `<a:videoFile>` element and its relationship are set by hand.
     */
    private fun videoDeck(): File {
        val file = File(temp, "video.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            slide.createAutoShape().apply {
                anchor = Rectangle(10, 10, 100, 100)
                fillColor = Color.RED
            }
            val media = ppt.addPicture(pngBytes(), PictureData.PictureType.PNG)
            val picture = slide.createPicture(media)
            picture.anchor = Rectangle(200, 100, 320, 180)
            val relationId = slide.addRelation(null, XSLFRelation.IMAGE_PNG, media).relationship.id
            (picture.xmlObject as CTPicture).nvPicPr.nvPr.addNewVideoFile().link = relationId
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    private fun pngBytes(): ByteArray {
        val image = BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { paint = Color.BLUE; fillRect(0, 0, 32, 18); dispose() }
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun `an embedded video becomes its own layer even with no animation on the slide`() {
        // The app drives live playback and has to be able to find the video; folded into a
        // background band it would render as a still poster and never play.
        val slide = loadDeck(videoDeck()).slides.single()
        val media = assertIs<LayerSpec.Media>(slide.layers.first { it is LayerSpec.Media })
        assertEquals(320.0, media.contentRectPt.w, 1.0, "the content rect is the shape's own anchor")
        assertEquals(180.0, media.contentRectPt.h, 1.0)
        assertTrue(media.boundsPt.w >= media.contentRectPt.w, "padding never shrinks the video area")
    }

    @Test
    fun `rasterizing a video layer extracts the media to a real file`() {
        val deck = loadDeck(videoDeck())
        DeckRasterizer(deck, targetWidthPx = 640).use { rasterizer ->
            val layer = rasterizer.rasterizeSlideLayers(0).first { it.spec is LayerSpec.Media }
            val extracted = assertNotNull(
                (layer.spec as LayerSpec.Media).mediaFile,
                "a video layer that hands back no file is a poster frame that never plays",
            )
            assertTrue(extracted.isFile, "the extracted path has to exist: $extracted")
            assertTrue(extracted.length() > 0, "and to hold the media bytes")
            assertEquals("png", extracted.extension, "the real extension is preserved for the decoder")
        }
    }

    @Test
    fun `the slide with a video still renders as a whole`() {
        val deck = loadDeck(videoDeck())
        DeckRasterizer(deck, targetWidthPx = 480).use { rasterizer ->
            val frame = rasterizer.renderFinalFrame(0)
            assertEquals(480, frame.width)
            assertTrue(countInk(frame) > 0, "the poster frame and the other shape are both drawn")
        }
    }
}
