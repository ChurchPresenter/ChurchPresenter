package presentation.engine

import presentation.engine.model.Direction
import presentation.engine.model.EffectInterval
import presentation.engine.model.EffectSpec
import presentation.engine.model.FillMode
import presentation.engine.model.RectPt
import presentation.engine.model.RepeatSpec
import presentation.engine.model.Step
import presentation.engine.model.Timeline
import presentation.engine.timeline.TimelineEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sampling an animation timeline: `(step, elapsedMs) → per-layer state`.
 *
 * This is pure arithmetic with no clock of its own — the player asks it for a frame — so the
 * properties worth pinning are the ones a viewer would notice: a layer waiting for its turn is
 * not half-drawn, an entrance ends fully visible and an exit ends gone, `fill="remove"` snaps
 * back where `hold` does not, and a step reports itself settled exactly when its last interval
 * finishes so the player knows when to accept the next click.
 */
class TimelineEvaluatorTest {

    private val slideW = 960.0
    private val slideH = 540.0
    private val bounds = mapOf("a" to RectPt(100.0, 100.0, 200.0, 100.0), "b" to RectPt(400.0, 100.0, 200.0, 100.0))

    private fun evaluator(
        vararg steps: Step,
        hidden: Set<String> = setOf("a", "b"),
    ) = TimelineEvaluator(Timeline(steps.toList()), slideW, slideH, bounds, hidden)

    private fun entrance(
        layer: String = "a",
        effect: EffectSpec = EffectSpec.Fade(EffectSpec.Role.ENTRANCE),
        begin: Long = 0,
        dur: Long = 500,
        fill: FillMode = FillMode.HOLD,
        repeat: RepeatSpec = RepeatSpec.Once,
    ) = EffectInterval(layer, effect, begin, dur, repeat, fill)

    // ── Before anything runs ──────────────────────────────────────────────────

    @Test
    fun `layers awaiting an entrance start hidden`() {
        val e = evaluator(Step(listOf(entrance("a"))))
        val frame = e.initialFrame()
        assertEquals(false, frame.layerStates["a"]!!.visible, "an entrance target is hidden pre-click")
        assertTrue(frame.status.settled, "nothing is running yet")
    }

    @Test
    fun `a layer that is already on the slide starts visible`() {
        val e = evaluator(Step(listOf(entrance("a", EffectSpec.Pulse(EffectSpec.Role.EMPHASIS)))), hidden = emptySet())
        assertEquals(true, e.initialFrame().layerStates["a"]!!.visible)
    }

    // ── Progress through an interval ──────────────────────────────────────────

    @Test
    fun `a fade entrance runs from transparent to opaque`() {
        val e = evaluator(Step(listOf(entrance("a", EffectSpec.Fade(EffectSpec.Role.ENTRANCE), dur = 1000))))
        assertEquals(0.0, e.evaluate(0, 0).layerStates["a"]!!.alpha, 0.02)
        assertEquals(0.5, e.evaluate(0, 500).layerStates["a"]!!.alpha, 0.05)
        assertEquals(1.0, e.evaluate(0, 1000).layerStates["a"]!!.alpha, 0.02)
    }

    @Test
    fun `a fade exit runs the other way and ends hidden`() {
        val e = evaluator(
            Step(listOf(entrance("a", EffectSpec.Fade(EffectSpec.Role.EXIT), dur = 1000))),
            hidden = emptySet(),
        )
        assertTrue(e.evaluate(0, 0).layerStates["a"]!!.alpha > 0.9)
        val settled = e.evaluate(0, 1000).layerStates["a"]!!
        assertTrue(settled.alpha < 0.1 || !settled.visible, "the layer is gone once the exit completes")
    }

    @Test
    fun `an interval that has not begun leaves its layer at rest`() {
        val e = evaluator(Step(listOf(entrance("a", begin = 500, dur = 500))))
        assertEquals(false, e.evaluate(0, 0).layerStates["a"]!!.visible, "still waiting its turn, not half-drawn")
    }

    @Test
    fun `elapsed time past the end holds the settled state`() {
        val e = evaluator(Step(listOf(entrance("a", dur = 500))))
        val atEnd = e.evaluate(0, 500).layerStates["a"]!!
        val wayPast = e.evaluate(0, 60_000).layerStates["a"]!!
        assertEquals(atEnd.alpha, wayPast.alpha, 1e-9)
        assertEquals(atEnd.visible, wayPast.visible)
    }

    // ── Settling ──────────────────────────────────────────────────────────────

    @Test
    fun `a step settles when its last interval finishes`() {
        val e = evaluator(Step(listOf(entrance("a", begin = 0, dur = 400), entrance("b", begin = 300, dur = 400))))
        assertEquals(700, e.evaluate(0, 0).status.settleAtMs, "begin + duration of the latest interval")
        assertTrue(!e.evaluate(0, 699).status.settled)
        assertTrue(e.evaluate(0, 700).status.settled)
    }

    @Test
    fun `an empty step is settled immediately`() {
        val e = evaluator(Step(emptyList()))
        val status = e.evaluate(0, 0).status
        assertTrue(status.settled)
        assertEquals(0, status.settleAtMs)
    }

    @Test
    fun `an indefinitely repeating interval keeps animating without blocking the step`() {
        val e = evaluator(
            Step(
                listOf(
                    entrance(
                        "a", EffectSpec.Pulse(EffectSpec.Role.EMPHASIS),
                        dur = 300, repeat = RepeatSpec.Indefinite,
                    )
                )
            ),
            hidden = emptySet(),
        )
        val frame = e.evaluate(0, 10_000)
        assertTrue(frame.status.indefiniteActive, "it is still running")
        assertTrue(frame.status.settled, "but the operator can still click on")
    }

    // ── Fill modes ────────────────────────────────────────────────────────────

    @Test
    fun `a hold interval keeps its end state into the next step`() {
        val e = evaluator(
            Step(listOf(entrance("a", dur = 200, fill = FillMode.HOLD))),
            Step(listOf(entrance("b", dur = 200))),
        )
        assertEquals(true, e.evaluate(1, 0).layerStates["a"]!!.visible, "the entrance persists")
    }

    @Test
    fun `a remove interval leaves resting visibility unchanged`() {
        // fill="remove" snaps back, so an entrance with it does not leave the layer on screen.
        val e = evaluator(
            Step(listOf(entrance("a", dur = 200, fill = FillMode.REMOVE))),
            Step(listOf(entrance("b", dur = 200))),
        )
        assertEquals(false, e.evaluate(1, 0).layerStates["a"]!!.visible)
    }

    @Test
    fun `final visibility reports what remains on screen after every step`() {
        val e = evaluator(
            Step(listOf(entrance("a", EffectSpec.Fade(EffectSpec.Role.ENTRANCE), dur = 100))),
            Step(listOf(entrance("b", EffectSpec.Fade(EffectSpec.Role.ENTRANCE), dur = 100))),
            Step(listOf(entrance("a", EffectSpec.Fade(EffectSpec.Role.EXIT), dur = 100))),
        )
        val final = e.finalVisibility()
        assertEquals(false, final["a"], "a entered then left")
        assertEquals(true, final["b"], "b entered and stayed")
    }

    // ── Geometry-driven effects ───────────────────────────────────────────────

    @Test
    fun `a fly entrance starts offset and arrives at rest`() {
        val e = evaluator(
            Step(listOf(entrance("a", EffectSpec.Fly(EffectSpec.Role.ENTRANCE, Direction.RIGHT), dur = 500)))
        )
        val start = e.evaluate(0, 0).layerStates["a"]!!
        val end = e.evaluate(0, 500).layerStates["a"]!!
        assertTrue(start.translateXPt != 0.0, "it starts displaced")
        assertEquals(0.0, end.translateXPt, 1e-6, "and lands exactly at rest")
        assertEquals(0.0, end.translateYPt, 1e-6)
    }

    @Test
    fun `opposite fly directions displace opposite ways`() {
        fun startX(direction: Direction): Double {
            val e = evaluator(
                Step(listOf(entrance("a", EffectSpec.Fly(EffectSpec.Role.ENTRANCE, direction), dur = 500)))
            )
            return e.evaluate(0, 0).layerStates["a"]!!.translateXPt
        }
        assertTrue(startX(Direction.LEFT) * startX(Direction.RIGHT) < 0.0, "mirrored across the axis")
    }

    @Test
    fun `a zoom entrance grows to full size`() {
        val e = evaluator(
            Step(listOf(entrance("a", EffectSpec.Zoom(EffectSpec.Role.ENTRANCE, fromScale = 0.0), dur = 400)))
        )
        assertTrue(e.evaluate(0, 0).layerStates["a"]!!.scaleX < 0.1)
        assertEquals(1.0, e.evaluate(0, 400).layerStates["a"]!!.scaleX, 1e-6)
    }

    @Test
    fun `a spin ends back at its starting rotation`() {
        val e = evaluator(
            Step(listOf(entrance("a", EffectSpec.Spin(EffectSpec.Role.EMPHASIS), dur = 400))),
            hidden = emptySet(),
        )
        val end = e.evaluate(0, 400).layerStates["a"]!!.rotationDeg
        assertTrue(kotlin.math.abs(end % 360.0) < 1e-6, "full turns only, got $end")
    }

    @Test
    fun `a wipe reveals progressively rather than all at once`() {
        val e = evaluator(
            Step(listOf(entrance("a", EffectSpec.Wipe(EffectSpec.Role.ENTRANCE, Direction.RIGHT), dur = 400)))
        )
        val quarter = e.evaluate(0, 100).layerStates["a"]!!.clip
        val full = e.evaluate(0, 400).layerStates["a"]!!.clip
        assertTrue(quarter != null, "a clip is applied mid-wipe")
        assertTrue(full == null || full.right >= 0.999, "and it is fully open at the end: $full")
    }

    // ── Bounds ────────────────────────────────────────────────────────────────

    @Test
    fun `an out-of-range step index is rejected`() {
        val e = evaluator(Step(listOf(entrance("a"))))
        assertEquals(1, e.stepCount)
        val failed = runCatching { e.evaluate(5, 0) }.isFailure
        assertTrue(failed, "asking for a step that does not exist is a programming error, not a blank frame")
    }
}
