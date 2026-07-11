package presentation.engine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import presentation.engine.model.Deck
import presentation.engine.model.Direction
import presentation.engine.model.EffectInterval
import presentation.engine.model.EffectSpec
import presentation.engine.model.LayerSpec
import presentation.engine.model.RectPt
import presentation.engine.model.Step
import presentation.engine.model.Timeline
import presentation.engine.timeline.TimelineEvaluator
import java.awt.Color
import java.awt.Rectangle
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimelineTest {

    @TempDir
    lateinit var tempDir: File

    /** One animated rect + a 3-paragraph text box built by paragraph → 4 click steps. */
    private fun createAnimatedDeck(): Deck {
        val file = File(tempDir, "timeline.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val rect = slide.createAutoShape()
            rect.anchor = Rectangle(300, 40, 200, 120)
            rect.fillColor = Color(0x22, 0x88, 0x22)
            val textBox = slide.createTextBox()
            textBox.anchor = Rectangle(40, 220, 460, 180)
            textBox.text = "First"
            textBox.addNewTextParagraph().addNewTextRun().setText("Second")
            textBox.addNewTextParagraph().addNewTextRun().setText("Third")
            Fixtures.addTiming(
                slide,
                listOf(
                    Fixtures.TimingTarget(rect.shapeId.toLong()),
                    Fixtures.TimingTarget(textBox.shapeId.toLong(), paragraphs = listOf(0, 1, 2))
                )
            )
            file.outputStream().use { ppt.write(it) }
        }
        return assertIs<LoadResult.Success>(PresentationLoader.load(file)).deck
    }

    @Test
    fun `timeline compiles one step per click target`() {
        val deck = createAnimatedDeck()
        val slide = deck.slides[0]
        val timeline = assertNotNull(slide.timeline, "animated slide must carry a timeline")
        assertEquals(4, timeline.stepCount, "1 rect + 3 paragraphs = 4 click steps")

        val step0 = timeline.steps[0]
        assertEquals(1, step0.intervals.size)
        val interval = step0.intervals[0]
        assertEquals("shape-0", interval.layerId)
        assertIs<EffectSpec.Fade>(interval.effect)
        assertEquals(EffectSpec.Role.ENTRANCE, interval.effect.role)
        assertEquals(500L, interval.durMs)

        // Paragraph steps target the paragraph layers in order.
        assertEquals("shape-1-para-0", timeline.steps[1].intervals[0].layerId)
        assertEquals("shape-1-para-1", timeline.steps[2].intervals[0].layerId)
        assertEquals("shape-1-para-2", timeline.steps[3].intervals[0].layerId)
    }

    @Test
    fun `entrance targets start hidden`() {
        val deck = createAnimatedDeck()
        val layers = deck.slides[0].layers
        val byId = layers.associateBy { it.id }
        assertFalse(assertNotNull(byId["shape-0"]).initiallyVisible)
        assertFalse(assertNotNull(byId["shape-1-para-0"]).initiallyVisible)
        assertTrue(assertNotNull(byId["band-0"]).initiallyVisible)
    }

    @Test
    fun `evaluator ramps alpha and settles`() {
        val deck = createAnimatedDeck()
        val slide = deck.slides[0]
        val bounds = slide.layers.associate { it.id to it.boundsPt }
        val hidden = slide.layers.filter { !it.initiallyVisible }.map { it.id }.toSet()
        val evaluator = TimelineEvaluator(
            timeline = assertNotNull(slide.timeline),
            slideWidthPt = deck.slideWidthPt,
            slideHeightPt = deck.slideHeightPt,
            layerBounds = bounds,
            initiallyHiddenLayerIds = hidden
        )

        // Step 0 at t=0: the rect is at alpha 0; paragraph layers are still resting-hidden.
        val start = evaluator.evaluate(0, 0)
        assertEquals(0.0, assertNotNull(start.layerStates["shape-0"]).alpha, 1e-9)
        assertFalse(assertNotNull(start.layerStates["shape-1-para-0"]).visible)
        assertFalse(start.status.settled)

        // Mid-fade.
        val mid = evaluator.evaluate(0, 250)
        assertEquals(0.5, assertNotNull(mid.layerStates["shape-0"]).alpha, 0.01)

        // Past the duration: settled, held at full alpha.
        val done = evaluator.evaluate(0, 600)
        assertTrue(done.status.settled)
        assertEquals(1.0, assertNotNull(done.layerStates["shape-0"]).alpha, 1e-9)

        // Step 2: rect and paragraph 0 rest visible (their entrances completed in earlier steps).
        val later = evaluator.evaluate(2, 0)
        assertTrue(assertNotNull(later.layerStates["shape-0"]).visible)
        assertEquals(1.0, assertNotNull(later.layerStates["shape-1-para-0"]).alpha, 1e-9)
        assertFalse(assertNotNull(later.layerStates["shape-1-para-2"]).visible)

        // After the final step everything is visible.
        assertTrue(evaluator.finalVisibility().values.all { it })
    }

    @Test
    fun `evaluator handles fly offsets`() {
        // Direct evaluator math test — no file needed.
        val timeline = Timeline(
            steps = listOf(
                Step(
                    intervals = listOf(
                        EffectInterval(
                            layerId = "L",
                            effect = EffectSpec.Fly(EffectSpec.Role.ENTRANCE, Direction.UP),
                            beginMs = 0,
                            durMs = 1000
                        )
                    )
                )
            )
        )
        val evaluator = TimelineEvaluator(
            timeline, 720.0, 405.0,
            layerBounds = mapOf("L" to RectPt(100.0, 100.0, 200.0, 50.0)),
            initiallyHiddenLayerIds = setOf("L")
        )
        // Entering moving UP → starts below the slide: offset +(slideH - y) = 305.
        val start = assertNotNull(evaluator.evaluate(0, 0).layerStates["L"])
        assertEquals(305.0, start.translateYPt, 1e-6)
        val mid = assertNotNull(evaluator.evaluate(0, 500).layerStates["L"])
        assertEquals(152.5, mid.translateYPt, 1e-6)
        val end = assertNotNull(evaluator.evaluate(0, 1000).layerStates["L"])
        assertEquals(0.0, end.translateYPt, 1e-6)
    }

    @Test
    fun `slide without timing has null timeline and static composite`() {
        val plain = Fixtures.createPptx(tempDir, slides = listOf("Text" to ""), name = "plain2.pptx")
        val deck = assertIs<LoadResult.Success>(PresentationLoader.load(plain)).deck
        assertEquals(null, deck.slides[0].timeline)
        assertIs<LayerSpec.StaticComposite>(deck.slides[0].layers[0])
    }
}
