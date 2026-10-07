package org.churchpresenter.liveoutput

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.presentationengine.model.EffectInterval
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.model.Slide
import org.churchpresenter.presentationengine.model.SlideTransitionSpec
import org.churchpresenter.presentationengine.model.Step
import org.churchpresenter.presentationengine.model.Timeline
import org.churchpresenter.presentationengine.model.TransitionType
import org.churchpresenter.presentationengine.model.pdfDeck
import org.churchpresenter.slides.presenter.PresentationFrame
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

class PresentationPlayerTimelineTest {

    private lateinit var dir: File
    private val players = mutableListOf<PresentationPlayer>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-presentation-timeline-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        players.forEach { runCatching { it.close() } }
        dir.deleteRecursively()
    }

    private val bounds = RectPt(0.0, 0.0, 720.0, 405.0)

    private fun shape(id: String = "title", visible: Boolean = false) =
        LayerSpec.Shape(id = id, zIndex = 0, boundsPt = bounds, shapeIndex = 0, initiallyVisible = visible)

    private fun appear(layer: String, durMs: Long = 0) =
        Step(listOf(EffectInterval(layer, EffectSpec.Fade(), beginMs = 0, durMs = durMs)))

    private fun slide(
        index: Int,
        layers: List<LayerSpec> = listOf(shape()),
        timeline: Timeline? = null,
        transition: SlideTransitionSpec? = null,
    ) = Slide(
        index, notes = "", transition = transition, layers = layers, timeline = timeline, fidelity = Fidelity.NATIVE,
    )

    private fun deck(vararg slides: Slide): Deck {
        val file = File(dir, "deck.pdf")
        PDDocument().use { doc ->
            repeat(slides.size) { doc.addPage(PDPage()) }
            doc.save(file)
        }
        return pdfDeck(file, slides.toList())
    }

    private fun player(deck: Deck) = PresentationPlayer(deck, renderWidthPx = 160).also { players.add(it) }

    /** Rasterizing runs off this thread; the frame is the signal it finished. */
    private fun PresentationPlayer.awaitFrame(slide: Int, nowNanos: () -> Long = System::nanoTime): PresentationFrame {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            frame(nowNanos())?.takeIf { it.slideIndex == slide }?.let { return it }
            Thread.onSpinWait()
        }
        throw AssertionError("slide $slide never rasterized")
    }

    @Test
    fun `a two-step build reveals its layer one click at a time and takes it back the same way`() {
        val p = player(deck(slide(0, timeline = Timeline(listOf(appear("title"), appear("title"))))))
        p.showSlide(0)
        val unbuilt = p.awaitFrame(0)
        assertEquals(0 to 2, unbuilt.completedSteps to unbuilt.stepCount)
        assertTrue(unbuilt.layers.isEmpty(), "the layer starts hidden")

        val now = System.nanoTime()
        assertTrue(p.advance(now))
        val built = assertNotNull(p.frame(now + 1_000_000_000L))
        assertEquals(1, built.completedSteps)
        assertEquals(1, built.layers.size)
        assertTrue(p.advance(now))
        assertFalse(p.advance(now), "nothing left to build")

        assertTrue(p.rewind())
        assertTrue(p.rewind())
        assertFalse(p.rewind(), "already back to the unbuilt slide")
    }

    @Test
    fun `a step is animating until its effect has run its course`() {
        val p = player(deck(slide(0, timeline = Timeline(listOf(appear("title", durMs = 500))))))
        p.showSlide(0)
        p.awaitFrame(0)
        assertFalse(p.isAnimating(System.nanoTime()), "nothing runs before the first click")

        val start = System.nanoTime()
        p.advance(start)
        assertTrue(p.isAnimating(start + 100_000_000L))
        assertFalse(p.isAnimating(start + 2_000_000_000L))
    }

    @Test
    fun `a slide with no build has nothing to advance or animate`() {
        val p = player(deck(slide(0, layers = listOf(shape(visible = true)))))
        p.showSlide(0)
        val frame = p.awaitFrame(0)
        assertEquals(0, frame.stepCount)
        assertFalse(p.advance(System.nanoTime()))
        assertFalse(p.rewind())
        assertFalse(p.isAnimating(System.nanoTime()))
    }

    @Test
    fun `moving onto a slide with a transition plays it over the outgoing picture`() {
        val fade = SlideTransitionSpec(TransitionType.FADE, durationMs = 300)
        val p = player(
            deck(
                slide(0, layers = listOf(shape(visible = true))),
                slide(1, layers = listOf(shape(visible = true)), transition = fade),
            ),
        )
        p.showSlide(0)
        p.awaitFrame(0)
        p.awaitFrame(0)
        p.showSlide(1)
        val start = System.nanoTime()
        val incoming = p.awaitFrame(1) { start }
        assertNotNull(incoming.transition, "the fade is running")

        assertNull(assertNotNull(p.frame(start + 1_000_000_000L)).transition, "and it ends")
    }

    @Test
    fun `a transition of no type or no length is skipped`() {
        val none = SlideTransitionSpec(TransitionType.NONE, durationMs = 300)
        val instant = SlideTransitionSpec(TransitionType.FADE, durationMs = 0)
        val p = player(
            deck(
                slide(0, layers = listOf(shape(visible = true))),
                slide(1, layers = listOf(shape(visible = true)), transition = none),
                slide(2, layers = listOf(shape(visible = true)), transition = instant),
            ),
        )
        p.showSlide(0)
        p.awaitFrame(0)
        p.showSlide(1)
        assertNull(p.awaitFrame(1).transition)
        p.showSlide(2)
        assertNull(p.awaitFrame(2).transition)
    }

    @Test
    fun `stepping back onto a slide shows it fully built, loaded or not`() {
        val build = Timeline(listOf(appear("title"), appear("title")))
        val p = player(deck(slide(0, timeline = build), slide(1, timeline = build), slide(2, timeline = build)))
        p.showSlide(2, enterAtLastStep = true)
        val notYetLoaded = p.awaitFrame(2)
        assertEquals(2, notYetLoaded.completedSteps)

        p.showSlide(1, enterAtLastStep = true)
        val cached = p.awaitFrame(1)
        assertEquals(2, cached.completedSteps)
    }

    @Test
    fun `a slide stepped back onto is never drawn unbuilt while it loads`() {
        val build = Timeline(listOf(appear("title"), appear("title")))
        val deck = deck(slide(0, timeline = build), slide(1, timeline = build))
        repeat(STEP_BACK_ROUNDS) { round ->
            val p = player(deck)
            p.showSlide(1, enterAtLastStep = true)
            // Sampled from the moment the call returns: the first frame the slide gets is the
            // one the output shows, so it must already be built.
            val first = p.awaitFrame(1)
            assertEquals(2, first.completedSteps, "round $round")
            p.close()
            players.remove(p)
        }
    }

    @Test
    fun `a slide with a movie layer and no extracted file still draws its poster`() {
        val movie = LayerSpec.Media(
            id = "movie", zIndex = 0, boundsPt = bounds, shapeIndex = 0, contentRectPt = bounds, mediaFile = null,
        )
        val p = player(
            deck(
                slide(0, layers = listOf(movie), timeline = Timeline(listOf(appear("movie")))),
                slide(1, layers = listOf(shape(visible = true))),
            ),
        )
        p.showSlide(0)
        assertEquals(1, p.awaitFrame(0).layers.size)
        p.advance(System.nanoTime())
        assertNotNull(p.frame(System.nanoTime()))

        p.showSlide(1)
        assertEquals(1, p.awaitFrame(1).layers.size)
    }
}

private const val STEP_BACK_ROUNDS = 200
