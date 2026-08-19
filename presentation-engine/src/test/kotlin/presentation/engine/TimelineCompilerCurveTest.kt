package presentation.engine

import presentation.engine.model.EffectSpec
import presentation.engine.model.LayerProperty
import presentation.engine.model.PropertyCurve
import presentation.engine.model.RectPt
import presentation.engine.model.Timeline
import presentation.engine.pptx.BehaviorTarget
import presentation.engine.pptx.TimeCondition
import presentation.engine.pptx.TimeNode
import presentation.engine.pptx.TimeNodeKind
import presentation.engine.pptx.TimingBehavior
import presentation.engine.timeline.TimelineCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The curves a behavior bundle synthesizes.
 *
 * [TimelineCompilerTest] covers the tree shape — which click opens which step, what degrades to a
 * fade. This one covers what comes out the other side when PowerPoint serializes an effect as raw
 * `animate`/`animScale`/`animRot`/`animMotion` behaviors instead of a recognizable preset, which is
 * what every custom-path and grow/shrink animation in a real deck looks like. The values matter in
 * points on the slide: `ppt_x` is a fraction of the slide measured to the shape's *center*, so a
 * curve that forgets to subtract the resting position moves the shape to the wrong place entirely
 * rather than by a little.
 */
class TimelineCompilerCurveTest {

    private val slideW = 960.0
    private val slideH = 540.0

    /** A 200×100 shape whose center rests at (200, 150) — a quarter across, a bit above middle. */
    private val bounds = RectPt(100.0, 100.0, 200.0, 100.0)

    private fun compiler(warnings: MutableList<String> = mutableListOf()) = TimelineCompiler(
        slideWidthPt = slideW,
        slideHeightPt = slideH,
        resolveLayers = { shapeId, paragraph ->
            listOf(
                TimelineCompiler.LayerIdWithBounds(
                    layerId = if (paragraph == null) "shape-$shapeId" else "shape-$shapeId-p$paragraph",
                    boundsPt = bounds,
                )
            )
        },
        warnings = warnings,
    )

    private fun node(
        kind: TimeNodeKind,
        nodeType: String? = null,
        children: List<TimeNode> = emptyList(),
        behavior: TimingBehavior? = null,
        delayMs: Long? = null,
        durMs: Long? = null,
        presetId: Int? = null,
        presetClass: String? = null,
    ) = TimeNode(
        id = 0, kind = kind, nodeType = nodeType,
        beginConditions = if (delayMs == null) emptyList() else listOf(TimeCondition(delayMs, null, null, null)),
        durMs = durMs, repeatCount = null, autoReverse = false, fill = null, restart = null,
        presetId = presetId, presetClass = presetClass, presetSubtype = null,
        iterateType = null, children = children, behavior = behavior,
    )

    private fun behaviorNode(behavior: TimingBehavior) = node(TimeNodeKind.BEHAVIOR, behavior = behavior)

    private fun target(shapeId: Long = 1L) = BehaviorTarget(shapeId, paragraphIndex = null)

    /** Compiles one click carrying [behaviors] and returns the single interval's effect. */
    private fun effectOf(
        vararg behaviors: TimingBehavior,
        presetClass: String = "entr",
        warnings: MutableList<String> = mutableListOf(),
    ): EffectSpec {
        val effect = node(
            TimeNodeKind.PAR,
            presetClass = presetClass,
            presetId = 10,
            durMs = 500,
            children = behaviors.map { behaviorNode(it) },
        )
        val root = node(
            TimeNodeKind.PAR,
            nodeType = "tmRoot",
            children = listOf(
                node(
                    TimeNodeKind.SEQ,
                    nodeType = "mainSeq",
                    children = listOf(
                        node(TimeNodeKind.PAR, delayMs = TimeNode.INDEFINITE_MS, children = listOf(effect))
                    ),
                )
            ),
        )
        val result = assertNotNull(compiler(warnings).compile(root))
        return single(result.timeline).effect
    }

    private fun single(timeline: Timeline) = timeline.steps.single().intervals.single()

    private fun curve(effect: EffectSpec, property: LayerProperty): PropertyCurve {
        val custom = assertIs<EffectSpec.Custom>(effect)
        return assertNotNull(
            custom.curves.firstOrNull { it.property == property },
            "expected a $property curve, got ${custom.curves.map { it.property }}",
        )
    }

    // ── Position (ppt_x / ppt_y) ──────────────────────────────────────────────

    private fun animateValue(
        attribute: String,
        from: String?,
        to: String?,
        keyframes: List<Pair<Double, String>> = emptyList(),
    ) = TimingBehavior.AnimateValue(target(), 500, 0, attribute, from, to, null, keyframes)

    @Test
    fun `a ppt_x animation becomes a translation measured from the shape's resting center`() {
        // The shape's center rests at x = 200/960 = 0.2083…; animating to 0.75 of the slide moves
        // it by the difference, in points.
        val effect = effectOf(animateValue("ppt_x", from = "0.2083333", to = "0.75"))
        val translate = curve(effect, LayerProperty.TRANSLATE_X)
        assertEquals(0.0, translate.keyframes.first().second, 0.5, "it starts where the shape already is")
        assertEquals((0.75 - 200.0 / slideW) * slideW, translate.keyframes.last().second, 0.5)
    }

    @Test
    fun `a ppt_y animation translates on the other axis`() {
        val effect = effectOf(animateValue("ppt_y", from = "0.2777778", to = "0.5"))
        val translate = curve(effect, LayerProperty.TRANSLATE_Y)
        assertEquals((0.5 - 150.0 / slideH) * slideH, translate.keyframes.last().second, 0.5)
    }

    @Test
    fun `tavLst keyframes drive the curve when from and to are absent`() {
        val effect = effectOf(
            animateValue(
                "ppt_x",
                from = null,
                to = null,
                keyframes = listOf(0.0 to "0.2083333", 0.5 to "0.5", 1.0 to "0.2083333"),
            )
        )
        val translate = curve(effect, LayerProperty.TRANSLATE_X)
        assertEquals(3, translate.keyframes.size, "every keyframe survives")
        assertEquals(0.0, translate.keyframes.first().second, 0.5)
        assertEquals(0.0, translate.keyframes.last().second, 0.5, "it returns to where it started")
        assertTrue(translate.keyframes[1].second > 100.0, "and swings out in between")
    }

    @Test
    fun `an animation of an attribute the engine does not model degrades rather than translating`() {
        // ppt_w is not a translation; the bundle has nothing else to synthesize from, so the
        // engine falls back rather than inventing a movement.
        val warnings = mutableListOf<String>()
        val effect = effectOf(animateValue("ppt_w", from = "0.1", to = "0.9"), warnings = warnings)
        assertTrue(
            effect is EffectSpec.Fade || effect is EffectSpec.Appear || effect is EffectSpec.Wipe,
            "expected a degrade, got $effect",
        )
    }

    @Test
    fun `a single-ended position animation is not enough to build a curve`() {
        val effect = effectOf(animateValue("ppt_x", from = "0.2", to = null))
        assertTrue(effect !is EffectSpec.Custom, "one keyframe cannot describe a movement, got $effect")
    }

    // ── Scale ─────────────────────────────────────────────────────────────────

    private fun animateScale(
        fromX: Double? = null, fromY: Double? = null,
        toX: Double? = null, toY: Double? = null,
        byX: Double? = null, byY: Double? = null,
    ) = TimingBehavior.AnimateScale(target(), 500, 0, fromX, fromY, toX, toY, byX, byY)

    @Test
    fun `a scale animation produces both axis curves`() {
        val effect = effectOf(animateScale(fromX = 0.5, fromY = 0.5, toX = 1.0, toY = 1.0))
        assertEquals(listOf(0.0 to 0.5, 1.0 to 1.0), curve(effect, LayerProperty.SCALE_X).keyframes)
        assertEquals(listOf(0.0 to 0.5, 1.0 to 1.0), curve(effect, LayerProperty.SCALE_Y).keyframes)
    }

    @Test
    fun `an entrance scale with no from grows from nothing, an emphasis holds full size`() {
        val entrance = effectOf(animateScale(toX = 1.0, toY = 1.0), presetClass = "entr")
        assertEquals(0.0, curve(entrance, LayerProperty.SCALE_X).keyframes.first().second, 1e-9)

        val emphasis = effectOf(animateScale(toX = 2.0, toY = 2.0), presetClass = "emph")
        assertEquals(1.0, curve(emphasis, LayerProperty.SCALE_X).keyframes.first().second, 1e-9)
    }

    @Test
    fun `a by-factor scale multiplies the starting size`() {
        val effect = effectOf(animateScale(fromX = 1.0, fromY = 1.0, byX = 1.5, byY = 3.0), presetClass = "emph")
        assertEquals(1.5, curve(effect, LayerProperty.SCALE_X).keyframes.last().second, 1e-9)
        assertEquals(3.0, curve(effect, LayerProperty.SCALE_Y).keyframes.last().second, 1e-9)
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    @Test
    fun `a by-degrees rotation adds to where it started`() {
        val effect = effectOf(
            TimingBehavior.AnimateRotation(target(), 500, 0, fromDeg = 45.0, toDeg = null, byDeg = 90.0),
            presetClass = "emph",
        )
        assertEquals(listOf(0.0 to 45.0, 1.0 to 135.0), curve(effect, LayerProperty.ROTATION).keyframes)
    }

    // ── Motion path ───────────────────────────────────────────────────────────

    @Test
    fun `a motion path becomes translation curves in slide points`() {
        val effect = effectOf(
            TimingBehavior.AnimateMotion(target(), 500, 0, path = "M 0 0 L 0.5 0.25 E"),
            presetClass = "path",
        )
        val xs = curve(effect, LayerProperty.TRANSLATE_X).keyframes
        val ys = curve(effect, LayerProperty.TRANSLATE_Y).keyframes
        assertEquals(xs.size, ys.size, "both axes are sampled at the same times")
        assertEquals(0.0, xs.first().first, 1e-9)
        assertEquals(1.0, xs.last().first, 1e-9)
        assertEquals(0.5 * slideW, xs.last().second, 1.0)
        assertEquals(0.25 * slideH, ys.last().second, 1.0)
    }

    @Test
    fun `an unparseable motion path is recorded as a degrade instead of moving the shape`() {
        val warnings = mutableListOf<String>()
        val effect = effectOf(
            TimingBehavior.AnimateMotion(target(), 500, 0, path = "not a path"),
            presetClass = "path",
            warnings = warnings,
        )
        assertTrue(effect !is EffectSpec.Custom, "nothing to animate, got $effect")
        assertTrue(
            warnings.any { it.contains("motion path", ignoreCase = true) },
            "the degrade must be reported, got $warnings",
        )
    }

    @Test
    fun `a motion behavior with no path at all is simply ignored`() {
        val effect = effectOf(
            TimingBehavior.AnimateMotion(target(), 500, 0, path = null),
            presetClass = "path",
        )
        assertTrue(effect !is EffectSpec.Custom, "got $effect")
    }

    // ── Mixed bundles ─────────────────────────────────────────────────────────

    @Test
    fun `a fade alongside a movement contributes an alpha curve to the same effect`() {
        val effect = effectOf(
            TimingBehavior.AnimEffect(target(), 500, 0, "in", "fade"),
            animateValue("ppt_x", from = "0.2083333", to = "0.75"),
        )
        val alpha = curve(effect, LayerProperty.ALPHA)
        assertEquals(listOf(0.0 to 0.0, 1.0 to 1.0), alpha.keyframes, "an entrance fades up")
        curve(effect, LayerProperty.TRANSLATE_X)
    }

    @Test
    fun `an exit fade runs its alpha the other way`() {
        val effect = effectOf(
            TimingBehavior.AnimEffect(target(), 500, 0, "out", "fade"),
            animateValue("ppt_x", from = "0.2083333", to = "0.75"),
            presetClass = "exit",
        )
        assertEquals(listOf(0.0 to 1.0, 1.0 to 0.0), curve(effect, LayerProperty.ALPHA).keyframes)
    }

    @Test
    fun `a media command is not a visual effect`() {
        // Clicking a video's "Start: On Click" entry must not fade or reveal anything — the poster
        // frame is already on screen.
        val effect = effectOf(
            TimingBehavior.Command(target(), 0, 0, "playFrom(0.0)"),
            presetClass = "mediacall",
        )
        assertTrue(
            effect !is EffectSpec.Fade,
            "a playback command must not degrade to a fade, got $effect",
        )
    }
}
