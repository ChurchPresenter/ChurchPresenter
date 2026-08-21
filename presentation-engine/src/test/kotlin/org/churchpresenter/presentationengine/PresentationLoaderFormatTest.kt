package org.churchpresenter.presentationengine

import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.churchpresenter.presentationengine.model.DeckFormat
import org.churchpresenter.presentationengine.model.DeckLoadError
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.LayerSpec
import java.awt.Color
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What [PresentationLoader] does with each kind of file it is handed, including the ones it cannot
 * open.
 *
 * The engine's standing contract is that **load never throws**: every failure comes back as a
 * `LoadResult.Failure` with an error the app can put in front of an operator. A thrown exception
 * here is not a caught bug, it is a crash during a service, so the unhappy paths are worth as much
 * coverage as the happy one — a file that does not exist, a file whose bytes are not a deck at all,
 * an extension the engine does not handle, and a document with no slides in it.
 *
 * Legacy `.ppt` is the other half: the binary format exposes no usable animation data, so those
 * slides are static forever and must not pretend otherwise.
 */
class PresentationLoaderFormatTest {

    private val temp: File = Files.createTempDirectory("loader-format-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Failures ──────────────────────────────────────────────────────────────

    @Test
    fun `a file that does not exist fails rather than throwing`() {
        val result = assertIs<LoadResult.Failure>(PresentationLoader.load(File(temp, "absent.pptx")))
        assertEquals(DeckLoadError.PARSE_FAILED, result.error)
        assertTrue(result.detail?.contains("absent.pptx") == true, "the message names the file: ${result.detail}")
    }

    @Test
    fun `an extension the engine does not handle is reported as unsupported`() {
        val file = File(temp, "notes.txt").apply { writeText("not a deck") }
        val result = assertIs<LoadResult.Failure>(PresentationLoader.load(file))
        assertEquals(DeckLoadError.UNSUPPORTED_FORMAT, result.error)
        assertEquals("txt", result.detail, "the detail is the extension that was refused")
    }

    @Test
    fun `a file with the right extension and the wrong bytes fails to parse`() {
        for (name in listOf("broken.pptx", "broken.ppt", "broken.pdf")) {
            val file = File(temp, name).apply { writeText("this is not a presentation at all") }
            val result = assertIs<LoadResult.Failure>(PresentationLoader.load(file), "$name should fail")
            assertEquals(DeckLoadError.PARSE_FAILED, result.error, "$name")
        }
    }

    @Test
    fun `a deck with no slides is an empty document, not a parse failure`() {
        val file = File(temp, "empty.pptx")
        XMLSlideShow().use { ppt -> file.outputStream().use { ppt.write(it) } }

        val result = assertIs<LoadResult.Failure>(PresentationLoader.load(file))
        assertEquals(DeckLoadError.EMPTY_DOCUMENT, result.error, "the file is fine, it just has nothing in it")
    }

    @Test
    fun `a directory that is not a Keynote package fails rather than throwing`() {
        val dir = File(temp, "empty.key").apply { mkdirs() }
        val result = assertIs<LoadResult.Failure>(PresentationLoader.load(dir))
        assertEquals(DeckLoadError.EMPTY_DOCUMENT, result.error)
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Test
    fun `a PDF becomes one native slide per page, at the page's own size`() {
        val file = Fixtures.createPdf(temp, pages = 3)
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(file)).deck

        assertEquals(DeckFormat.PDF, deck.format)
        assertEquals(3, deck.slideCount)
        assertEquals(720.0, deck.slideWidthPt, 1.0)
        assertEquals(405.0, deck.slideHeightPt, 1.0)
        assertTrue(deck.slides.all { it.fidelity == Fidelity.NATIVE }, "the engine renders PDF pages itself")
        assertTrue(deck.slides.all { it.timeline == null }, "a PDF has no animation to compile")
        assertTrue(deck.slides.all { it.notes.isEmpty() }, "and no speaker notes")
    }

    @Test
    fun `every PDF page renders`() {
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(Fixtures.createPdf(temp, pages = 2))).deck
        DeckRasterizer(deck, targetWidthPx = 200).use { rasterizer ->
            for (index in 0 until deck.slideCount) {
                val frame = rasterizer.renderFinalFrame(index)
                assertEquals(200, frame.width, "page ${index + 1} honours the requested width")
            }
        }
    }

    // ── Legacy PowerPoint ─────────────────────────────────────────────────────

    private fun legacyPpt(slides: Int): File {
        val file = File(temp, "legacy.ppt")
        HSLFSlideShow().use { ppt ->
            repeat(slides) {
                val slide = ppt.createSlide()
                slide.createAutoShape().apply {
                    anchor = Rectangle(40, 40, 200, 120)
                    fillColor = Color(0x33, 0x66, 0x99)
                }
            }
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    @Test
    fun `a legacy ppt loads as static slides`() {
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(legacyPpt(slides = 2))).deck

        assertEquals(DeckFormat.PPT, deck.format)
        assertEquals(2, deck.slideCount)
        assertTrue(
            deck.slides.all { it.timeline == null },
            "the binary format exposes no usable animation data, so it must not pretend to have any",
        )
        assertTrue(
            deck.slides.all { it.layers.single() is LayerSpec.StaticComposite },
            "one flat layer per slide",
        )
    }

    @Test
    fun `a legacy ppt renders`() {
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(legacyPpt(slides = 1))).deck
        DeckRasterizer(deck, targetWidthPx = 320).use { rasterizer ->
            assertEquals(320, rasterizer.renderFinalFrame(0).width)
        }
    }

    // ── Timing that compiles to nothing ───────────────────────────────────────

    @Test
    fun `timing that targets a shape the slide does not have leaves the slide static`() {
        // Targets exist, so the planner produces layers, but nothing resolves to a real layer and
        // the compile comes back empty — the slide has to fall back to one flat composite rather
        // than keep a layer plan it cannot animate.
        val file = File(temp, "dangling.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            slide.createAutoShape().apply {
                anchor = Rectangle(40, 40, 200, 120)
                fillColor = Color.RED
            }
            Fixtures.addTiming(slide, listOf(Fixtures.TimingTarget(shapeId = 9999L)))
            file.outputStream().use { ppt.write(it) }
        }

        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(file)).deck
        val slide = deck.slides.single()
        assertNull(slide.timeline, "nothing compiled, so there is no timeline")
        assertIs<LayerSpec.StaticComposite>(slide.layers.single())
    }

    @Test
    fun `a multi-slide deck keeps its slides in order with their own notes`() {
        val file = Fixtures.createPptx(
            temp,
            listOf("First" to "note one", "Second" to "", "Third" to "note three"),
            name = "ordered.pptx",
        )
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(file)).deck

        assertEquals(3, deck.slideCount)
        assertEquals(listOf(0, 1, 2), deck.slides.map { it.index })
        assertTrue(deck.slides[0].notes.contains("note one"))
        assertEquals("", deck.slides[1].notes, "a slide with no notes has none, rather than the previous slide's")
        assertTrue(deck.slides[2].notes.contains("note three"))
    }
}
