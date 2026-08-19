package presentation.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import presentation.engine.model.DeckFormat
import presentation.engine.model.DeckLoadError
import presentation.engine.model.Fidelity
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StaticPipelineTest {

    @TempDir
    lateinit var tempDir: File

    private fun assertNonBlank(image: BufferedImage) {
        val samples = mutableSetOf<Int>()
        for (y in 0 until image.height step (image.height / 16).coerceAtLeast(1)) {
            for (x in 0 until image.width step (image.width / 16).coerceAtLeast(1)) {
                samples.add(image.getRGB(x, y))
            }
        }
        assertTrue(samples.size > 1, "Rendered frame is a single flat color — render produced nothing")
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Test
    fun `pdf loads and renders at target width`() {
        val pdf = Fixtures.createPdf(tempDir, pages = 3)
        val result = PresentationLoader.load(pdf)
        val deck = assertIs<LoadResult.Success>(result).deck
        assertEquals(DeckFormat.PDF, deck.format)
        assertEquals(3, deck.slideCount)
        assertEquals(Fidelity.NATIVE, deck.slides[0].fidelity)

        DeckRasterizer(deck, targetWidthPx = 960).use { rasterizer ->
            val frame = rasterizer.renderFinalFrame(0)
            assertEquals(960, frame.width)
            assertNonBlank(frame)
        }
    }

    @Test
    fun `empty pdf reports EMPTY_DOCUMENT`() {
        val pdf = Fixtures.createPdf(tempDir, pages = 0)
        val result = PresentationLoader.load(pdf)
        assertEquals(DeckLoadError.EMPTY_DOCUMENT, assertIs<LoadResult.Failure>(result).error)
    }

    // ── PPTX ──────────────────────────────────────────────────────────────────

    @Test
    fun `pptx loads with notes and renders ARGB at target width`() {
        val pptx = Fixtures.createPptx(
            tempDir,
            slides = listOf("Welcome" to "greet everyone", "Announcements" to "")
        )
        val result = PresentationLoader.load(pptx)
        val deck = assertIs<LoadResult.Success>(result).deck
        assertEquals(DeckFormat.PPTX, deck.format)
        assertEquals(2, deck.slideCount)
        assertEquals("greet everyone", deck.slides[0].notes)
        assertEquals("", deck.slides[1].notes)

        DeckRasterizer(deck, targetWidthPx = 1280).use { rasterizer ->
            val frame = rasterizer.renderFinalFrame(0)
            assertEquals(1280, frame.width)
            assertEquals(BufferedImage.TYPE_INT_ARGB, frame.type)
            assertNonBlank(frame)
        }
    }

    @Test
    fun `unsupported extension reports UNSUPPORTED_FORMAT`() {
        val file = File(tempDir, "deck.txt").apply { writeText("not a deck") }
        val result = PresentationLoader.load(file)
        assertEquals(DeckLoadError.UNSUPPORTED_FORMAT, assertIs<LoadResult.Failure>(result).error)
    }

    @Test
    fun `corrupt pptx reports failure instead of throwing`() {
        val file = File(tempDir, "broken.pptx").apply { writeBytes(ByteArray(64) { 7 }) }
        assertIs<LoadResult.Failure>(PresentationLoader.load(file))
    }

    // ── Keynote static ────────────────────────────────────────────────────────

    @Test
    fun `keynote with embedded preview pdf uses it`() {
        val previewPdf = Fixtures.createPdf(tempDir, pages = 4, name = "preview_src.pdf").readBytes()
        val key = Fixtures.createKeynoteZip(
            tempDir,
            previewPdf = previewPdf,
            thumbnails = mapOf("st-aaa-1.jpg" to Fixtures.jpegBytes(320, 180, Color.RED)),
            slideIwaIds = listOf(11L)
        )
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(key)).deck
        assertEquals(DeckFormat.KEYNOTE, deck.format)
        assertEquals(4, deck.slideCount)
        assertEquals(Fidelity.STATIC_FALLBACK, deck.slides[0].fidelity)

        DeckRasterizer(deck, targetWidthPx = 800).use { rasterizer ->
            val frame = rasterizer.renderFinalFrame(2)
            assertEquals(800, frame.width)
            assertNonBlank(frame)
        }
    }

    @Test
    fun `keynote without preview pdf falls back to ordered thumbnails`() {
        // Presentation order (zip order of Slide-<id>.iwa): 30, 10, 20.
        // st- trailing ids sorted (100,200,300) rank-match sorted iwa ids (10,20,30):
        // slide 1 (id 30 → rank 2) = st-…-300 (blue), slide 2 (id 10 → rank 0) = st-…-100 (red).
        val key = Fixtures.createKeynoteZip(
            tempDir,
            previewPdf = null,
            thumbnails = mapOf(
                "st-uuid-100.jpg" to Fixtures.jpegBytes(320, 180, Color.RED),
                "st-uuid-200.jpg" to Fixtures.jpegBytes(320, 180, Color.GREEN),
                "st-uuid-300.jpg" to Fixtures.jpegBytes(320, 180, Color.BLUE)
            ),
            slideIwaIds = listOf(30L, 10L, 20L)
        )
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(key)).deck
        assertEquals(3, deck.slideCount)

        DeckRasterizer(deck).use { rasterizer ->
            val first = rasterizer.renderFinalFrame(0)
            val second = rasterizer.renderFinalFrame(1)
            val third = rasterizer.renderFinalFrame(2)
            assertTrue(Color(first.getRGB(160, 90)).blue > 200, "slide 1 should be the blue thumbnail")
            assertTrue(Color(second.getRGB(160, 90)).red > 200, "slide 2 should be the red thumbnail")
            assertTrue(Color(third.getRGB(160, 90)).green > 200, "slide 3 should be the green thumbnail")
        }
    }

    @Test
    fun `keynote with nothing usable reports EMPTY_DOCUMENT`() {
        val key = Fixtures.createKeynoteZip(tempDir, previewPdf = null, thumbnails = emptyMap(), slideIwaIds = emptyList())
        val result = PresentationLoader.load(key)
        assertEquals(DeckLoadError.EMPTY_DOCUMENT, assertIs<LoadResult.Failure>(result).error)
    }
}
