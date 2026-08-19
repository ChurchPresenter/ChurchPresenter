package presentation.engine

import presentation.engine.model.Direction
import presentation.engine.model.EffectSpec
import presentation.engine.model.FillMode
import presentation.engine.model.RectPt
import presentation.engine.model.RepeatSpec
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning PowerPoint's timing tree into the engine's own timeline.
 *
 * The compiler takes a parsed [TimeNode] tree, so these tests build the tree directly rather than
 * round-tripping a deck — the XML side is `TimingParser`'s job.
 *
 * Two rules run through everything. **A click opens a step**: PowerPoint marks an operator click
 * with an indefinite begin delay, and getting that wrong either merges every animation into one
 * click or demands a click per behavior. And **anything unrecognisable degrades to a fade with a
 * warning**, never to nothing — a slide must always show its content.
 */
class TimelineCompilerTest {

    private val bounds = RectPt(100.0, 100.0, 200.0, 100.0)

    /** Resolves every shape id to one layer named after it. */
    private fun compiler(warnings: MutableList<String> = mutableListOf()) = TimelineCompiler(
        slideWidthPt = 960.0,
        slideHeightPt = 540.0,
        resolveLayers = { shapeId, paragraph ->
            listOf(TimelineCompiler.LayerIdWithBounds(
                layerId = if (paragraph == null) "shape-$shapeId" else "shape-$shapeId-p$paragraph",
                boundsPt = bounds,
            ))
        },
        warnings = warnings,
    )

    // ── Tree builders ─────────────────────────────────────────────────────────

    private fun node(
        kind: TimeNodeKind,
        nodeType: String? = null,
        children: List<TimeNode> = emptyList(),
        behavior: TimingBehavior? = null,
        delayMs: Long? = null,
        durMs: Long? = null,
        presetId: Int? = null,
        presetClass: String? = null,
        presetSubtype: Int? = null,
        repeatCount: Double? = null,
        fill: String? = null,
        autoReverse: Boolean = false,
    ) = TimeNode(
        id = 0, kind = kind, nodeType = nodeType,
        beginConditions = if (delayMs == null) emptyList() else listOf(TimeCondition(delayMs, null, null, null)),
        durMs = durMs, repeatCount = repeatCount, autoReverse = autoReverse, fill = fill, restart = null,
        presetId = presetId, presetClass = presetClass, presetSubtype = presetSubtype,
        iterateType = null, children = children, behavior = behavior,
    )

    private fun target(shapeId: Long = 1L, paragraph: Int? = null) = BehaviorTarget(shapeId, paragraph)

    private fun animEffect(
        shapeId: Long = 1L,
        filter: String? = "fade",
        durMs: Long? = 500,
        transition: String = "in",
    ) =
        node(TimeNodeKind.BEHAVIOR, behavior = TimingBehavior.AnimEffect(target(shapeId), durMs, 0, transition, filter))

    /** One effect node (a par carrying preset metadata) wrapping the given behaviors. */
    private fun effect(
        presetClass: String = "entr",
        presetId: Int = 10,
        presetSubtype: Int? = null,
        durMs: Long? = 500,
        delayMs: Long? = null,
        repeatCount: Double? = null,
        fill: String? = null,
        behaviors: List<TimeNode> = listOf(animEffect()),
    ) = node(
        TimeNodeKind.PAR, presetClass = presetClass, presetId = presetId, presetSubtype = presetSubtype,
        durMs = durMs, delayMs = delayMs, repeatCount = repeatCount, fill = fill, children = behaviors,
    )

    /** A click group — an indefinite begin delay is how PowerPoint marks "wait for the operator". */
    private fun clickGroup(vararg effects: TimeNode) =
        node(TimeNodeKind.PAR, delayMs = TimeNode.INDEFINITE_MS, children = effects.toList())

    private fun slide(vararg groups: TimeNode) = node(
        TimeNodeKind.PAR, nodeType = "tmRoot",
        children = listOf(node(TimeNodeKind.SEQ, nodeType = "mainSeq", children = groups.toList())),
    )

    // ── Nothing to compile ────────────────────────────────────────────────────

    @Test
    fun `a slide with no timing has no timeline`() {
        assertNull(compiler().compile(null))
    }

    @Test
    fun `a timing tree with no sequence has no timeline`() {
        assertNull(compiler().compile(node(TimeNodeKind.PAR, nodeType = "tmRoot")))
    }

    @Test
    fun `a sequence with no effects has no timeline`() {
        assertNull(compiler().compile(slide()))
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    @Test
    fun `each click group becomes its own step`() {
        val result = assertNotNull(compiler().compile(slide(clickGroup(effect()), clickGroup(effect()))))
        assertEquals(2, result.timeline.stepCount, "two clicks, two steps")
    }

    @Test
    fun `a group with no click condition merges into the previous step`() {
        // Some exporters emit onEnd chains as plain siblings; they continue the same click.
        val result = assertNotNull(
            compiler().compile(slide(clickGroup(effect()), node(TimeNodeKind.PAR, children = listOf(effect()))))
        )
        assertEquals(1, result.timeline.stepCount, "the second group did not need its own click")
        assertEquals(2, result.timeline.steps.single().intervals.size, "but both effects are present")
    }

    @Test
    fun `a merged group starts after the previous one settles`() {
        val result = assertNotNull(
            compiler().compile(
                slide(
                    clickGroup(effect(behaviors = listOf(animEffect(durMs = 400)))),
                    node(TimeNodeKind.PAR, children = listOf(effect(behaviors = listOf(animEffect(durMs = 400))))),
                )
            )
        )
        val intervals = result.timeline.steps.single().intervals.sortedBy { it.beginMs }
        assertEquals(0L, intervals.first().beginMs)
        assertEquals(400L, intervals.last().beginMs, "the second waits for the first to finish")
    }

    @Test
    fun `effects inside one group all begin together`() {
        val result = assertNotNull(
            compiler().compile(
                slide(
                    clickGroup(
                        effect(behaviors = listOf(animEffect(1L))),
                        effect(behaviors = listOf(animEffect(2L))),
                    )
                )
            )
        )
        val intervals = result.timeline.steps.single().intervals
        assertEquals(2, intervals.size)
        assertTrue(intervals.all { it.beginMs == 0L }, "with-previous means simultaneous")
    }

    @Test
    fun `a delay on an effect offsets its start`() {
        val result = assertNotNull(compiler().compile(slide(clickGroup(effect(delayMs = 250)))))
        assertEquals(250L, result.timeline.steps.single().intervals.single().beginMs)
    }

    // ── Effect synthesis ──────────────────────────────────────────────────────

    @Test
    fun `an entrance targets the right layer, for the behavior's duration`() {
        // The length comes from the *behavior*, not from the effect node wrapping it — PowerPoint
        // puts the real timing on the behavior and the effect node's own dur is often absent.
        val result = assertNotNull(
            compiler().compile(slide(clickGroup(effect(durMs = 700, behaviors = listOf(animEffect(durMs = 700))))))
        )
        val interval = result.timeline.steps.single().intervals.single()
        assertEquals("shape-1", interval.layerId)
        assertEquals(700L, interval.durMs)
        assertEquals(EffectSpec.Role.ENTRANCE, interval.effect.role)
    }

    @Test
    fun `the behavior's duration wins over the effect node's`() {
        val result = assertNotNull(
            compiler().compile(slide(clickGroup(effect(durMs = 9999, behaviors = listOf(animEffect(durMs = 300))))))
        )
        assertEquals(300L, result.timeline.steps.single().intervals.single().durMs)
    }

    @Test
    fun `an effect with no duration gets a sane default rather than zero`() {
        // A zero-length effect would flash rather than animate.
        val result = assertNotNull(
            compiler().compile(slide(clickGroup(effect(durMs = null, behaviors = listOf(animEffect(durMs = null))))))
        )
        assertTrue(result.timeline.steps.single().intervals.single().durMs > 0)
    }

    @Test
    fun `the exit preset class produces an exit role`() {
        val result = assertNotNull(compiler().compile(slide(clickGroup(effect(presetClass = "exit")))))
        assertEquals(EffectSpec.Role.EXIT, result.timeline.steps.single().intervals.single().effect.role)
    }

    @Test
    fun `an emphasis preset class produces an emphasis role`() {
        val result = assertNotNull(
            compiler().compile(slide(clickGroup(effect(presetClass = "emph", presetId = 26))))
        )
        assertEquals(EffectSpec.Role.EMPHASIS, result.timeline.steps.single().intervals.single().effect.role)
    }

    @Test
    fun `a wipe filter becomes a wipe with its direction`() {
        val result = assertNotNull(
            compiler().compile(slide(clickGroup(effect(behaviors = listOf(animEffect(filter = "wipe(down)"))))))
        )
        val effect = result.timeline.steps.single().intervals.single().effect
        assertIs<EffectSpec.Wipe>(effect)
        assertEquals(Direction.DOWN, effect.direction)
    }

    @Test
    fun `an unknown filter degrades to a fade and says so`() {
        val warnings = mutableListOf<String>()
        val result = assertNotNull(
            compiler(warnings).compile(slide(clickGroup(effect(behaviors = listOf(animEffect(filter = "sparkle"))))))
        )
        assertIs<EffectSpec.Fade>(result.timeline.steps.single().intervals.single().effect)
        assertTrue(warnings.any { it.contains("sparkle") }, "the degrade is recorded: $warnings")
    }

    @Test
    fun `an opacity curve reads as a fade`() {
        val behavior = node(
            TimeNodeKind.BEHAVIOR,
            behavior = TimingBehavior.AnimateValue(target(), 500, 0, "style.opacity", "0", "1", null, emptyList()),
        )
        val result = assertNotNull(compiler().compile(slide(clickGroup(effect(behaviors = listOf(behavior))))))
        assertIs<EffectSpec.Fade>(result.timeline.steps.single().intervals.single().effect)
    }

    @Test
    fun `an unhandled animation attribute is reported rather than silently dropped`() {
        val warnings = mutableListOf<String>()
        val behavior = node(
            TimeNodeKind.BEHAVIOR,
            behavior = TimingBehavior.AnimateValue(
                target(), 500, 0, "style.fontWeight", "400", "700", null, emptyList(),
            ),
        )
        compiler(warnings).compile(slide(clickGroup(effect(behaviors = listOf(behavior)))))
        assertTrue(warnings.any { it.contains("fontWeight") }, "got $warnings")
    }

    @Test
    fun `a rotation behavior produces a spin`() {
        val behavior = node(
            TimeNodeKind.BEHAVIOR,
            behavior = TimingBehavior.AnimateRotation(target(), 500, 0, 0.0, 360.0, null),
        )
        val result = assertNotNull(
            compiler().compile(
                slide(clickGroup(effect(presetClass = "emph", presetId = 8, behaviors = listOf(behavior))))
            )
        )
        assertNotNull(result.timeline.steps.single().intervals.single().effect)
    }

    // ── Repeat, fill and reverse ──────────────────────────────────────────────

    @Test
    fun `an indefinite repeat is carried onto the interval`() {
        val result = assertNotNull(
            compiler().compile(slide(clickGroup(effect(presetClass = "emph", presetId = 26, repeatCount = -1.0))))
        )
        assertEquals(RepeatSpec.Indefinite, result.timeline.steps.single().intervals.single().repeat)
    }

    @Test
    fun `no repeat count means play once`() {
        val result = assertNotNull(compiler().compile(slide(clickGroup(effect()))))
        assertEquals(RepeatSpec.Once, result.timeline.steps.single().intervals.single().repeat)
    }

    @Test
    fun `fill remove is carried through, and the default is hold`() {
        val removed = assertNotNull(compiler().compile(slide(clickGroup(effect(fill = "remove")))))
        assertEquals(FillMode.REMOVE, removed.timeline.steps.single().intervals.single().fill)

        val held = assertNotNull(compiler().compile(slide(clickGroup(effect()))))
        assertEquals(FillMode.HOLD, held.timeline.steps.single().intervals.single().fill)
    }

    // ── Initially hidden layers ───────────────────────────────────────────────

    @Test
    fun `a layer whose first effect is an entrance starts hidden`() {
        val result = assertNotNull(compiler().compile(slide(clickGroup(effect(presetClass = "entr")))))
        assertTrue("shape-1" in result.initiallyHiddenLayerIds, "it must not be on screen before its click")
    }

    @Test
    fun `a layer whose first effect is an exit starts visible`() {
        val result = assertNotNull(compiler().compile(slide(clickGroup(effect(presetClass = "exit")))))
        assertTrue("shape-1" !in result.initiallyHiddenLayerIds, "it has to be there in order to leave")
    }

    @Test
    fun `a layer that only ever pulses starts visible`() {
        val result = assertNotNull(
            compiler().compile(slide(clickGroup(effect(presetClass = "emph", presetId = 26))))
        )
        assertTrue("shape-1" !in result.initiallyHiddenLayerIds)
    }

    @Test
    fun `an entrance later in the deck still hides its layer from the start`() {
        val result = assertNotNull(
            compiler().compile(
                slide(
                    clickGroup(effect(presetClass = "entr", behaviors = listOf(animEffect(1L)))),
                    clickGroup(effect(presetClass = "entr", behaviors = listOf(animEffect(2L)))),
                )
            )
        )
        assertTrue("shape-2" in result.initiallyHiddenLayerIds, "it appears on click two, so it starts hidden")
    }

    // ── Paragraph targets ─────────────────────────────────────────────────────

    @Test
    fun `a paragraph-targeted behavior resolves to that paragraph's own layer`() {
        val behavior = node(
            TimeNodeKind.BEHAVIOR,
            behavior = TimingBehavior.AnimEffect(target(paragraph = 2), 500, 0, "in", "fade"),
        )
        val result = assertNotNull(compiler().compile(slide(clickGroup(effect(behaviors = listOf(behavior))))))
        assertEquals("shape-1-p2", result.timeline.steps.single().intervals.single().layerId)
    }

    // ── Interactive sequences ─────────────────────────────────────────────────

    @Test
    fun `a shape-click trigger becomes an appended step, with a warning`() {
        // Output windows are not clickable, so the trigger is reachable by next-click instead.
        val warnings = mutableListOf<String>()
        val root = node(
            TimeNodeKind.PAR, nodeType = "tmRoot",
            children = listOf(
                node(TimeNodeKind.SEQ, nodeType = "mainSeq", children = listOf(clickGroup(effect()))),
                node(
                    TimeNodeKind.SEQ, nodeType = "interactiveSeq",
                    children = listOf(
                        node(TimeNodeKind.PAR, children = listOf(effect(behaviors = listOf(animEffect(2L)))))
                    ),
                ),
            ),
        )
        val result = assertNotNull(compiler(warnings).compile(root))

        assertEquals(2, result.timeline.stepCount, "the trigger got its own step")
        assertTrue(warnings.any { it.contains("trigger") }, "and the limitation is recorded: $warnings")
    }
}
