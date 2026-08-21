package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.model.EffectInterval
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.model.RepeatSpec
import org.churchpresenter.presentationengine.pptx.BehaviorTarget
import org.churchpresenter.presentationengine.pptx.TimeCondition
import org.churchpresenter.presentationengine.pptx.TimeNode
import org.churchpresenter.presentationengine.pptx.TimeNodeKind
import org.churchpresenter.presentationengine.pptx.TimingBehavior
import org.churchpresenter.presentationengine.timeline.TimelineCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * How a click's nested timing tree becomes the intervals inside one step.
 *
 * PowerPoint nests effects under `par` (everything together) and `seq` (one after another), with a
 * delay on any node, and the delays compound: a behavior three levels deep begins at the sum of
 * every delay above it plus, inside a sequence, however long its siblings already take. Read that
 * wrong and every animation after the first in a click fires at the wrong moment — which looks
 * exactly like the deck being "fast" or "laggy" rather than like a bug.
 */
class TimelineCompilerNestingTest {

    private val bounds = RectPt(100.0, 100.0, 200.0, 100.0)

    private fun compiler(warnings: MutableList<String> = mutableListOf()) = TimelineCompiler(
        slideWidthPt = 960.0,
        slideHeightPt = 540.0,
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
        repeatCount: Double? = null,
    ) = TimeNode(
        id = 0, kind = kind, nodeType = nodeType,
        beginConditions = if (delayMs == null) emptyList() else listOf(TimeCondition(delayMs, null, null, null)),
        durMs = durMs, repeatCount = repeatCount, autoReverse = false, fill = null, restart = null,
        presetId = presetId, presetClass = presetClass, presetSubtype = null,
        iterateType = null, children = children, behavior = behavior,
    )

    private fun fade(shapeId: Long = 1L, durMs: Long? = 500) = node(
        TimeNodeKind.BEHAVIOR,
        behavior = TimingBehavior.AnimEffect(
            BehaviorTarget(shapeId, paragraphIndex = null), durMs, 0, "in", "fade",
        ),
    )

    /** An effect node (a par with preset metadata) wrapping one fade behavior. */
    private fun effect(shapeId: Long = 1L, durMs: Long? = 500, delayMs: Long? = null) = node(
        TimeNodeKind.PAR,
        presetClass = "entr",
        presetId = 10,
        durMs = durMs,
        delayMs = delayMs,
        children = listOf(fade(shapeId, durMs)),
    )

    private fun click(vararg children: TimeNode, delayMs: Long? = null) = node(
        TimeNodeKind.PAR,
        delayMs = TimeNode.INDEFINITE_MS,
        children = listOf(node(TimeNodeKind.PAR, delayMs = delayMs, children = children.toList())),
    )

    private fun slide(vararg groups: TimeNode) = node(
        TimeNodeKind.PAR,
        nodeType = "tmRoot",
        children = listOf(node(TimeNodeKind.SEQ, nodeType = "mainSeq", children = groups.toList())),
    )

    private fun intervalsOf(root: TimeNode): List<EffectInterval> {
        val result = assertNotNull(compiler().compile(root))
        return result.timeline.steps.single().intervals.sortedBy { it.beginMs }
    }

    // ── Parallel ──────────────────────────────────────────────────────────────

    @Test
    fun `effects under a par all start together`() {
        val intervals = intervalsOf(slide(click(effect(1L), effect(2L))))
        assertEquals(2, intervals.size)
        assertTrue(intervals.all { it.beginMs == 0L }, "got ${intervals.map { it.beginMs }}")
    }

    @Test
    fun `a delay on a wrapper pushes everything under it`() {
        val intervals = intervalsOf(slide(click(effect(1L), effect(2L), delayMs = 400)))
        assertTrue(intervals.all { it.beginMs == 400L }, "got ${intervals.map { it.beginMs }}")
    }

    @Test
    fun `delays at two levels add up`() {
        val intervals = intervalsOf(slide(click(effect(1L, delayMs = 250), delayMs = 400)))
        assertEquals(650L, intervals.single().beginMs, "400 on the group plus 250 on the effect")
    }

    // ── Sequential ────────────────────────────────────────────────────────────

    @Test
    fun `effects under a seq queue behind one another`() {
        val sequence = node(
            TimeNodeKind.SEQ,
            children = listOf(effect(1L, durMs = 500), effect(2L, durMs = 300), effect(3L, durMs = 200)),
        )
        val intervals = intervalsOf(slide(click(sequence)))

        assertEquals(3, intervals.size)
        assertEquals(0L, intervals[0].beginMs)
        assertEquals(500L, intervals[1].beginMs, "starts when the first one settles")
        assertEquals(800L, intervals[2].beginMs, "and the third after both")
    }

    @Test
    fun `a delay inside a sequence is added to the point the previous one settled`() {
        val sequence = node(
            TimeNodeKind.SEQ,
            children = listOf(effect(1L, durMs = 500), effect(2L, durMs = 300, delayMs = 200)),
        )
        val intervals = intervalsOf(slide(click(sequence)))
        assertEquals(700L, intervals[1].beginMs, "500ms of the first, then its own 200ms delay")
    }

    @Test
    fun `a sequence inside a parallel group runs alongside its siblings`() {
        val sequence = node(
            TimeNodeKind.SEQ,
            children = listOf(effect(2L, durMs = 400), effect(3L, durMs = 400)),
        )
        val intervals = intervalsOf(slide(click(effect(1L, durMs = 1000), sequence)))

        assertEquals(3, intervals.size)
        assertEquals(listOf(0L, 0L, 400L), intervals.map { it.beginMs }, "the sequence starts with its sibling")
    }

    @Test
    fun `an empty sequence contributes nothing rather than a phantom interval`() {
        val intervals = intervalsOf(slide(click(node(TimeNodeKind.SEQ), effect(1L))))
        assertEquals(1, intervals.size)
    }

    // ── Other node kinds ──────────────────────────────────────────────────────

    @Test
    fun `an exclusive container's children are treated as parallel`() {
        val exclusive = node(TimeNodeKind.EXCL, children = listOf(effect(1L), effect(2L)))
        val intervals = intervalsOf(slide(click(exclusive)))
        assertEquals(2, intervals.size)
        assertTrue(intervals.all { it.beginMs == 0L })
    }

    @Test
    fun `a behavior with no effect wrapper still animates`() {
        // Some exporters emit a bare behavior; it has to synthesize on its own rather than be
        // dropped, or the shape it targets never appears.
        val intervals = intervalsOf(slide(click(fade(shapeId = 7L))))
        val interval = intervals.single()
        assertEquals("shape-7", interval.layerId)
        assertIs<EffectSpec.Fade>(interval.effect)
    }

    @Test
    fun `a repeat on the effect node reaches the interval`() {
        val repeated = node(
            TimeNodeKind.PAR,
            presetClass = "emph",
            presetId = 26,
            durMs = 400,
            repeatCount = 3000.0,
            children = listOf(fade(1L, 400)),
        )
        val interval = intervalsOf(slide(click(repeated))).single()
        assertEquals(RepeatSpec.Count(3000.0), interval.repeat)
    }

    @Test
    fun `an indefinite repeat is carried as such`() {
        val repeated = node(
            TimeNodeKind.PAR,
            presetClass = "emph",
            presetId = 26,
            durMs = 400,
            repeatCount = -1.0,
            children = listOf(fade(1L, 400)),
        )
        assertEquals(RepeatSpec.Indefinite, intervalsOf(slide(click(repeated))).single().repeat)
    }

    @Test
    fun `a repeat of exactly one is no repeat at all`() {
        val once = node(
            TimeNodeKind.PAR,
            presetClass = "emph",
            presetId = 26,
            durMs = 400,
            repeatCount = 1.0,
            children = listOf(fade(1L, 400)),
        )
        assertEquals(RepeatSpec.Once, intervalsOf(slide(click(once))).single().repeat)
    }

    @Test
    fun `a repeat declared on a behavior rather than on its effect node still counts`() {
        val behavior = node(
            TimeNodeKind.BEHAVIOR,
            repeatCount = 2000.0,
            behavior = TimingBehavior.AnimEffect(BehaviorTarget(1L, null), 400, 0, "in", "fade"),
        )
        val wrapper = node(
            TimeNodeKind.PAR,
            presetClass = "emph",
            presetId = 26,
            durMs = 400,
            children = listOf(behavior),
        )
        assertEquals(RepeatSpec.Count(2000.0), intervalsOf(slide(click(wrapper))).single().repeat)
    }

    // ── Targets that resolve to nothing ───────────────────────────────────────

    @Test
    fun `an effect targeting a shape with no layer is dropped, not crashed`() {
        val compiler = TimelineCompiler(
            slideWidthPt = 960.0,
            slideHeightPt = 540.0,
            resolveLayers = { _, _ -> emptyList() },
        )
        assertTrue(
            compiler.compile(slide(click(effect(1L))))?.timeline?.steps.orEmpty().all { it.intervals.isEmpty() },
            "a target with no layer contributes no interval",
        )
    }

    @Test
    fun `one effect fanning out to several layers gives each its own interval`() {
        val compiler = TimelineCompiler(
            slideWidthPt = 960.0,
            slideHeightPt = 540.0,
            resolveLayers = { shapeId, _ ->
                listOf(
                    TimelineCompiler.LayerIdWithBounds("shape-$shapeId-a", bounds),
                    TimelineCompiler.LayerIdWithBounds("shape-$shapeId-b", bounds),
                )
            },
        )
        val result = assertNotNull(compiler.compile(slide(click(effect(1L)))))
        val intervals = result.timeline.steps.single().intervals
        assertEquals(
            listOf("shape-1-a", "shape-1-b"),
            intervals.map { it.layerId },
            "a paragraph range covering a whole shape animates every layer it owns",
        )
        assertEquals(setOf("shape-1-a", "shape-1-b"), result.initiallyHiddenLayerIds)
    }
}
