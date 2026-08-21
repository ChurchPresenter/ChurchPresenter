package org.churchpresenter.app.churchpresenter.presenter

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.presentationengine.model.Deck
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A static (never-animated) PDF deck has no timeline and no slide transitions, so it exercises
 * [PresentationPlayer]'s navigation, rasterization and frame-assembly logic without needing a real
 * animated PPTX/Keynote fixture — that path (advance/rewind/isAnimating/transitionOverlay against a
 * real timeline) needs a POI-built deck with `<p:timing>`, which composeApp's jvmTest has no fixture
 * for (the engine module's own `Fixtures.addTiming` lives in a separate Gradle test source set and
 * isn't on this classpath); left as a follow-up, not something this file reaches.
 */
class PresentationPlayerTest {

    private lateinit var dir: File
    private val players = mutableListOf<PresentationPlayer>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-presentation-player-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        players.forEach { runCatching { it.close() } }
        players.clear()
        dir.deleteRecursively()
    }

    private fun staticDeck(pageCount: Int, name: String = "deck.pdf"): Deck {
        val file = File(dir, name)
        PDDocument().use { doc ->
            repeat(pageCount) { doc.addPage(PDPage()) }
            doc.save(file)
        }
        return (PresentationLoader.load(file) as LoadResult.Success).deck
    }

    private fun player(pageCount: Int = 1): PresentationPlayer =
        PresentationPlayer(staticDeck(pageCount)).also { players.add(it) }

    private fun awaitFrame(player: PresentationPlayer, timeoutMs: Long = 5_000): PresentationFrame {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            player.frame(System.nanoTime())?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for a rasterized frame")
    }

    @Test
    fun `frame is null before any slide has been shown`() {
        val p = player()
        assertNull(p.frame(System.nanoTime()))
    }

    @Test
    fun `showSlide points currentSlideIndex at the requested slide`() {
        val p = player(pageCount = 2)
        p.showSlide(1)
        assertEquals(1, p.currentSlideIndex)
    }

    @Test
    fun `showSlide with an out-of-range index is a no-op`() {
        val p = player(pageCount = 2)
        p.showSlide(0)
        p.showSlide(99)
        assertEquals(0, p.currentSlideIndex)
        p.showSlide(-1)
        assertEquals(0, p.currentSlideIndex)
    }

    @Test
    fun `a rasterized static slide produces a frame with a visible layer and no transition`() {
        val p = player()
        p.showSlide(0)

        val frame = awaitFrame(p)

        assertEquals(0, frame.slideIndex)
        assertTrue(frame.frameWidthPx > 0)
        assertTrue(frame.frameHeightPx > 0)
        assertTrue(frame.layers.isNotEmpty(), "a static PDF page must place at least its background layer")
        assertNull(frame.transition, "a static deck never carries a deck-defined transition")
        assertEquals(0, frame.stepCount, "a static slide has no timeline, so no build steps")
    }

    @Test
    fun `advance and rewind are both false for a slide with no timeline`() {
        val p = player()
        p.showSlide(0)
        awaitFrame(p)

        assertFalse(p.advance(System.nanoTime()))
        assertFalse(p.rewind())
    }

    @Test
    fun `isAnimating is false once a static slide has finished loading`() {
        val p = player()
        p.showSlide(0)
        awaitFrame(p)

        assertFalse(p.isAnimating(System.nanoTime()))
    }

    @Test
    fun `isAnimating is false before the slide has finished loading`() {
        val p = player()
        p.showSlide(0)

        assertFalse(p.isAnimating(System.nanoTime()))
    }

    @Test
    fun `navigating to a second slide updates the frame's slideIndex`() {
        val p = player(pageCount = 2)
        p.showSlide(0)
        awaitFrame(p)

        p.showSlide(1)
        val frame = awaitFrame(p)

        assertEquals(1, frame.slideIndex)
    }

    @Test
    fun `re-showing the already-live slide is a safe no-op`() {
        val p = player(pageCount = 2)
        p.showSlide(0)
        awaitFrame(p)

        p.showSlide(0)
        val frame = awaitFrame(p)

        assertEquals(0, frame.slideIndex)
    }

    @Test
    fun `entering at the last step on an already-cached static slide lands on the pre-click state`() {
        val p = player(pageCount = 2)
        p.showSlide(0)
        awaitFrame(p)

        p.showSlide(0, enterAtLastStep = true)
        val frame = awaitFrame(p)

        assertEquals(0, frame.slideIndex)
        assertEquals(0, frame.stepCount, "a static slide has no timeline, so still no build steps")
    }

    @Test
    fun `entering at the last step before the slide has finished loading still resolves once it loads`() {
        val p = player(pageCount = 2)

        p.showSlide(1, enterAtLastStep = true)
        val frame = awaitFrame(p)

        assertEquals(1, frame.slideIndex)
    }

    @Test
    fun `navigating across many slides keeps only a window of the deck cached`() {
        val p = player(pageCount = 5)
        for (index in 0..4) {
            p.showSlide(index)
            val frame = awaitFrame(p)
            assertEquals(index, frame.slideIndex)
        }
    }

    @Test
    fun `close clears the cache so frame stops returning content`() {
        val p = player()
        p.showSlide(0)
        awaitFrame(p)

        p.close()

        assertNull(p.frame(System.nanoTime()))
    }

    @Test
    fun `close is safe to call more than once`() {
        val p = player()
        p.showSlide(0)
        awaitFrame(p)

        p.close()
        p.close()
    }

    @Test
    fun `a rasterization failure is reported and leaves frame returning null`() {
        val file = File(dir, "vanishing.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(file)
        }
        val deck = (PresentationLoader.load(file) as LoadResult.Success).deck
        file.delete()
        val p = PresentationPlayer(deck).also { players.add(it) }

        p.showSlide(0)

        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && p.loading.containsKey(0)) {
            Thread.sleep(10)
        }
        assertTrue(!p.loading.containsKey(0), "timed out waiting for the failed rasterization attempt to finish")
        assertNull(p.frame(System.nanoTime()), "a rasterization failure must never populate the slide cache")
    }

    @Test
    fun `the deck passed to the constructor is exposed unchanged`() {
        val deck = staticDeck(pageCount = 1)
        val p = PresentationPlayer(deck).also { players.add(it) }
        assertNotNull(p.deck)
        assertEquals(deck, p.deck)
    }
}
