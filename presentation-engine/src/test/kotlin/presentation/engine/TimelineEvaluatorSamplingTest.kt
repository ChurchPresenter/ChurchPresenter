package presentation.engine

import presentation.engine.model.Direction
import presentation.engine.model.EffectInterval
import presentation.engine.model.EffectSpec
import presentation.engine.model.FillMode
import presentation.engine.model.LayerProperty
import presentation.engine.model.LayerState
import presentation.engine.model.PropertyCurve
import presentation.engine.model.RectPt
import presentation.engine.model.RepeatSpec
import presentation.engine.model.RevealClip
import presentation.engine.model.Step
import presentation.engine.model.Timeline
import presentation.engine.timeline.TimelineEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What each effect actually samples to, halfway through and at its ends.
 *
 * [TimelineEvaluatorTest] pins the timeline's behavior — when a step settles, what rests visible.
 * This one goes the other way and pins the geometry every effect produces, because that is what a
 * viewer sees and none of it is checked by "the step finished": a split that reveals from an edge
 * instead of the middle, a wipe that uncovers the wrong side, a fly whose offscreen offset is
 * computed from the slide instead of the shape, and a custom curve that jumps rather than
 * interpolates all satisfy the timeline contract while looking wrong on the screen.
 */
class TimelineEvaluatorSamplingTest {

    private val slideW = 960.0
    private val slideH = 540.0
    private val bounds = mapOf("a" to RectPt(100.0, 120.0, 200.0, 100.0))

    private fun stateAt(
        effect: EffectSpec,
        elapsedMs: Long,
        durMs: Long = 1000,
        layer: String = "a",
        autoReverse: Boolean = false,
        repeat: RepeatSpec = RepeatSpec.Once,
        fill: FillMode = FillMode.HOLD,
    ): LayerState {
        val interval = EffectInterval(layer, effect, 0, durMs, repeat, fill, autoReverse)
        val evaluator = TimelineEvaluator(
            Timeline(listOf(Step(listOf(interval)))),
            slideW,
            slideH,
            bounds,
            initiallyHiddenLayerIds = setOf("a"),
        )
        return evaluator.evaluate(0, elapsedMs).layerStates.getValue(layer)
    }

    // ── Custom attribute curves ───────────────────────────────────────────────

    private fun custom(vararg curves: PropertyCurve) =
        EffectSpec.Custom(EffectSpec.Role.EMPHASIS, curves.toList())

    @Test
    fun `a custom curve interpolates linearly between its keyframes`() {
        val effect = custom(
            PropertyCurve(LayerProperty.TRANSLATE_X, listOf(0.0 to 0.0, 1.0 to 100.0))
        )
        assertEquals(0.0, stateAt(effect, 0).translateXPt, 1e-9)
        assertEquals(25.0, stateAt(effect, 250).translateXPt, 1e-9)
        assertEquals(60.0, stateAt(effect, 600).translateXPt, 1e-9)
    }

    @Test
    fun `a curve holds its first value before the first keyframe and its last after the last`() {
        // Keyframes covering only the middle of the interval: the value must not extrapolate out
        // past either end, or a shape jumps as the interval starts and again as it finishes.
        val effect = custom(
            PropertyCurve(LayerProperty.ROTATION, listOf(0.25 to 90.0, 0.75 to 180.0))
        )
        assertEquals(90.0, stateAt(effect, 0).rotationDeg, 1e-9)
        assertEquals(90.0, stateAt(effect, 100).rotationDeg, 1e-9)
        assertEquals(180.0, stateAt(effect, 800).rotationDeg, 1e-9)
        assertEquals(135.0, stateAt(effect, 500).rotationDeg, 1e-9)
    }

    @Test
    fun `two keyframes at the same time resolve to the first of them instead of dividing by zero`() {
        // A zero-width segment is what a source file gives when two keys share a time. The value
        // has to stay finite — the earlier key wins, which is arbitrary but not NaN.
        val effect = custom(
            PropertyCurve(LayerProperty.SCALE_X, listOf(0.0 to 1.0, 0.5 to 2.0, 0.5 to 3.0, 1.0 to 3.0))
        )
        val atStep = stateAt(effect, 500).scaleX
        assertTrue(atStep.isFinite(), "a zero-width segment must not produce NaN, got $atStep")
        assertEquals(2.0, atStep, 1e-9)
    }

    @Test
    fun `an empty curve leaves its channel alone rather than resetting it`() {
        val effect = custom(
            PropertyCurve(LayerProperty.ALPHA, emptyList()),
            PropertyCurve(LayerProperty.SCALE_Y, listOf(0.0 to 1.0, 1.0 to 2.0)),
        )
        val state = stateAt(effect, 1000)
        assertEquals(1.0, state.alpha, 1e-9)
        assertEquals(2.0, state.scaleY, 1e-9)
    }

    @Test
    fun `an alpha curve is clamped to the drawable range`() {
        val effect = custom(
            PropertyCurve(LayerProperty.ALPHA, listOf(0.0 to -2.0, 1.0 to 4.0))
        )
        assertEquals(0.0, stateAt(effect, 0).alpha, 1e-9)
        assertEquals(1.0, stateAt(effect, 1000).alpha, 1e-9)
    }

    @Test
    fun `every animatable channel can be driven by a curve`() {
        val effect = custom(
            PropertyCurve(LayerProperty.ALPHA, listOf(0.0 to 1.0, 1.0 to 0.5)),
            PropertyCurve(LayerProperty.TRANSLATE_X, listOf(0.0 to 0.0, 1.0 to 10.0)),
            PropertyCurve(LayerProperty.TRANSLATE_Y, listOf(0.0 to 0.0, 1.0 to 20.0)),
            PropertyCurve(LayerProperty.SCALE_X, listOf(0.0 to 1.0, 1.0 to 3.0)),
            PropertyCurve(LayerProperty.SCALE_Y, listOf(0.0 to 1.0, 1.0 to 4.0)),
            PropertyCurve(LayerProperty.ROTATION, listOf(0.0 to 0.0, 1.0 to 45.0)),
        )
        val end = stateAt(effect, 1000)
        assertEquals(0.5, end.alpha, 1e-9)
        assertEquals(10.0, end.translateXPt, 1e-9)
        assertEquals(20.0, end.translateYPt, 1e-9)
        assertEquals(3.0, end.scaleX, 1e-9)
        assertEquals(4.0, end.scaleY, 1e-9)
        assertEquals(45.0, end.rotationDeg, 1e-9)
    }

    // ── Split ─────────────────────────────────────────────────────────────────

    @Test
    fun `a horizontal split entrance opens from the middle outward`() {
        val effect = EffectSpec.Split(EffectSpec.Role.ENTRANCE, horizontal = true, outward = true)
        val start = assertNotNull(stateAt(effect, 0).clip)
        assertEquals(RevealClip(0.0, 0.5, 1.0, 0.5), start)

        val half = assertNotNull(stateAt(effect, 500).clip)
        assertEquals(0.25, half.top, 1e-9)
        assertEquals(0.75, half.bottom, 1e-9)
        assertEquals(0.0, half.left, 1e-9)
        assertEquals(1.0, half.right, 1e-9)

        val full = assertNotNull(stateAt(effect, 1000).clip)
        assertEquals(RevealClip(0.0, 0.0, 1.0, 1.0), full)
    }

    @Test
    fun `a vertical split splits the other axis`() {
        val effect = EffectSpec.Split(EffectSpec.Role.ENTRANCE, horizontal = false, outward = true)
        val half = assertNotNull(stateAt(effect, 500).clip)
        assertEquals(0.25, half.left, 1e-9)
        assertEquals(0.75, half.right, 1e-9)
        assertEquals(0.0, half.top, 1e-9)
        assertEquals(1.0, half.bottom, 1e-9)
    }

    @Test
    fun `a split exit closes rather than opens`() {
        val effect = EffectSpec.Split(EffectSpec.Role.EXIT, horizontal = true, outward = false)
        val early = assertNotNull(stateAt(effect, 100).clip)
        val late = assertNotNull(stateAt(effect, 900).clip)
        assertTrue(
            (late.bottom - late.top) < (early.bottom - early.top),
            "an exit split must shrink its reveal, got $early then $late",
        )
    }

    // ── Wipe ──────────────────────────────────────────────────────────────────

    @Test
    fun `each wipe direction uncovers from the matching edge`() {
        fun clipAtHalf(direction: Direction) =
            assertNotNull(stateAt(EffectSpec.Wipe(EffectSpec.Role.ENTRANCE, direction), 500).clip)

        assertEquals(RevealClip(0.0, 0.0, 1.0, 0.5), clipAtHalf(Direction.DOWN))
        assertEquals(RevealClip(0.0, 0.5, 1.0, 1.0), clipAtHalf(Direction.UP))
        assertEquals(RevealClip(0.0, 0.0, 0.5, 1.0), clipAtHalf(Direction.RIGHT))
        assertEquals(RevealClip(0.5, 0.0, 1.0, 1.0), clipAtHalf(Direction.LEFT))
    }

    @Test
    fun `a wipe with no meaningful direction still reveals top-down`() {
        // IN/OUT have no edge to wipe from; the engine's degrade rule is that something sensible
        // must still appear rather than nothing.
        val clip = assertNotNull(stateAt(EffectSpec.Wipe(EffectSpec.Role.ENTRANCE, Direction.IN), 500).clip)
        assertEquals(RevealClip(0.0, 0.0, 1.0, 0.5), clip)
    }

    @Test
    fun `a wipe exit covers back up`() {
        val effect = EffectSpec.Wipe(EffectSpec.Role.EXIT, Direction.DOWN)
        assertEquals(0.75, assertNotNull(stateAt(effect, 250).clip).bottom, 1e-9)
        assertEquals(0.25, assertNotNull(stateAt(effect, 750).clip).bottom, 1e-9)
    }

    // ── Fly ───────────────────────────────────────────────────────────────────

    @Test
    fun `a fly exit leaves along its own direction and clears the slide`() {
        // Sampled halfway, so the travel is half of the full offscreen offset. The shape sits at
        // y=120 and is 100 tall, so flying UP has to carry its bottom edge (220pt) past the top of
        // the slide — the offset comes from the shape, not from the slide height.
        val up = stateAt(EffectSpec.Fly(EffectSpec.Role.EXIT, Direction.UP), 500)
        assertEquals(-110.0, up.translateYPt, 1e-9)
        assertEquals(0.0, up.translateXPt, 1e-9)

        val down = stateAt(EffectSpec.Fly(EffectSpec.Role.EXIT, Direction.DOWN), 500)
        assertEquals((slideH - 120.0) / 2, down.translateYPt, 1e-9)

        val left = stateAt(EffectSpec.Fly(EffectSpec.Role.EXIT, Direction.LEFT), 500)
        assertEquals(-150.0, left.translateXPt, 1e-9)

        val right = stateAt(EffectSpec.Fly(EffectSpec.Role.EXIT, Direction.RIGHT), 500)
        assertEquals((slideW - 100.0) / 2, right.translateXPt, 1e-9)
    }

    @Test
    fun `a fly exit is simply gone once it finishes`() {
        assertEquals(LayerState.HIDDEN, stateAt(EffectSpec.Fly(EffectSpec.Role.EXIT, Direction.UP), 1000))
    }

    @Test
    fun `a fly in or out does not move the layer`() {
        val state = stateAt(EffectSpec.Fly(EffectSpec.Role.EXIT, Direction.IN), 500)
        assertEquals(0.0, state.translateXPt, 1e-9)
        assertEquals(0.0, state.translateYPt, 1e-9)
    }

    @Test
    fun `a layer with no recorded bounds flies the whole slide instead of nothing`() {
        val interval = EffectInterval(
            "unbounded",
            EffectSpec.Fly(EffectSpec.Role.EXIT, Direction.RIGHT),
            0,
            1000,
            RepeatSpec.Once,
            FillMode.HOLD,
        )
        val evaluator = TimelineEvaluator(
            Timeline(listOf(Step(listOf(interval)))),
            slideW,
            slideH,
            layerBounds = emptyMap(),
            initiallyHiddenLayerIds = emptySet(),
        )
        val state = evaluator.evaluate(0, 500).layerStates.getValue("unbounded")
        assertEquals(
            slideW / 2,
            state.translateXPt,
            1e-9,
            "with no bounds the layer is treated as the whole slide, so it flies a slide width",
        )
    }

    // ── Scale, spin and pulse ─────────────────────────────────────────────────

    @Test
    fun `a zoom exit shrinks toward its target scale`() {
        val effect = EffectSpec.Zoom(EffectSpec.Role.EXIT, fromScale = 0.0)
        assertEquals(1.0, stateAt(effect, 0).scaleX, 1e-9)
        assertEquals(0.5, stateAt(effect, 500).scaleX, 1e-9)
    }

    @Test
    fun `a layer scaled to nothing is not drawn at full opacity`() {
        val effect = EffectSpec.Zoom(EffectSpec.Role.ENTRANCE, fromScale = 0.0)
        assertEquals(0.0, stateAt(effect, 0).alpha, 1e-9)
        assertEquals(1.0, stateAt(effect, 1000).alpha, 1e-9)
    }

    @Test
    fun `grow-shrink scales each axis independently`() {
        val effect = EffectSpec.GrowShrink(toScaleX = 2.0, toScaleY = 0.5)
        val half = stateAt(effect, 500)
        assertEquals(1.5, half.scaleX, 1e-9)
        assertEquals(0.75, half.scaleY, 1e-9)
    }

    @Test
    fun `a pulse peaks in the middle and returns to rest`() {
        val effect = EffectSpec.Pulse()
        val start = stateAt(effect, 0)
        val peak = stateAt(effect, 500)
        assertEquals(1.0, start.scaleX, 1e-9)
        assertEquals(1.0, start.alpha, 1e-9)
        assertTrue(peak.scaleX > start.scaleX, "the pulse must swell, got ${peak.scaleX}")
        assertTrue(peak.alpha < start.alpha, "the pulse must dip in opacity, got ${peak.alpha}")
    }

    @Test
    fun `a spin sweeps its full angle`() {
        val effect = EffectSpec.Spin(degrees = 180.0)
        assertEquals(90.0, stateAt(effect, 500).rotationDeg, 1e-9)
    }

    // ── Visibility-only effects ───────────────────────────────────────────────

    @Test
    fun `set-visibility flips the layer without animating`() {
        val hide = EffectSpec.SetVisibility(EffectSpec.Role.EXIT, visible = false)
        assertEquals(LayerState.HIDDEN, stateAt(hide, 500, fill = FillMode.HOLD))

        val show = EffectSpec.SetVisibility(EffectSpec.Role.ENTRANCE, visible = true)
        assertEquals(LayerState.VISIBLE, stateAt(show, 500))
    }

    @Test
    fun `an appear used as an exit hides rather than shows`() {
        assertEquals(LayerState.HIDDEN, stateAt(EffectSpec.Appear(EffectSpec.Role.EXIT), 500))
        assertEquals(LayerState.VISIBLE, stateAt(EffectSpec.Appear(EffectSpec.Role.ENTRANCE), 500))
    }

    // ── Motion path ───────────────────────────────────────────────────────────

    @Test
    fun `a motion path walks the layer along it in slide points`() {
        val effect = EffectSpec.MotionPath(EffectSpec.Role.EMPHASIS, "M 0 0 L 0.5 0.25 E", relative = true)
        val start = stateAt(effect, 0)
        assertEquals(0.0, start.translateXPt, 1e-6)
        assertEquals(0.0, start.translateYPt, 1e-6)

        val end = stateAt(effect, 1000)
        assertEquals(0.5 * slideW, end.translateXPt, 1e-3)
        assertEquals(0.25 * slideH, end.translateYPt, 1e-3)
    }

    @Test
    fun `an unparseable motion path leaves the layer where it is`() {
        val effect = EffectSpec.MotionPath(EffectSpec.Role.EMPHASIS, "nonsense", relative = true)
        val state = stateAt(effect, 500)
        assertEquals(LayerState.VISIBLE, state)
        assertNull(state.clip)
    }

    // ── Cycling ───────────────────────────────────────────────────────────────

    @Test
    fun `auto-reverse runs a cycle out and back within its duration`() {
        val effect = EffectSpec.Fade(EffectSpec.Role.ENTRANCE)
        assertEquals(0.5, stateAt(effect, 250, autoReverse = true).alpha, 1e-9)
        assertEquals(1.0, stateAt(effect, 500, autoReverse = true).alpha, 1e-9)
        assertEquals(0.5, stateAt(effect, 750, autoReverse = true).alpha, 1e-9)
    }

    @Test
    fun `a counted repeat restarts each cycle and settles after the last`() {
        val effect = EffectSpec.Fade(EffectSpec.Role.ENTRANCE)
        val interval = EffectInterval("a", effect, 0, 400, RepeatSpec.Count(2.0), FillMode.HOLD)
        val evaluator = TimelineEvaluator(
            Timeline(listOf(Step(listOf(interval)))),
            slideW,
            slideH,
            bounds,
            initiallyHiddenLayerIds = setOf("a"),
        )
        assertEquals(0.5, evaluator.evaluate(0, 200).layerStates.getValue("a").alpha, 1e-9)
        assertEquals(0.5, evaluator.evaluate(0, 600).layerStates.getValue("a").alpha, 1e-9)

        val settled = evaluator.evaluate(0, 800).status
        assertTrue(settled.settled, "two 400ms cycles settle at 800ms")
        assertEquals(800L, settled.settleAtMs)
    }

    @Test
    fun `a removed emphasis snaps back to the layer's resting state when it finishes`() {
        val effect = EffectSpec.GrowShrink(toScaleX = 3.0, toScaleY = 3.0)
        val state = stateAt(effect, 2000, fill = FillMode.REMOVE, layer = "a")
        assertEquals(LayerState.HIDDEN, state, "layer 'a' rests hidden, so removing the effect hides it")
    }
}
