package org.churchpresenter.presentationengine.timeline

import org.churchpresenter.presentationengine.model.EffectInterval
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.FillMode
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.model.RepeatSpec
import org.churchpresenter.presentationengine.model.Step
import org.churchpresenter.presentationengine.model.Timeline
import org.churchpresenter.presentationengine.pptx.TimeNode
import org.churchpresenter.presentationengine.pptx.TimeNodeKind
import org.churchpresenter.presentationengine.pptx.TimingBehavior

/**
 * Flattens a parsed timing tree into a click-driven [Timeline].
 *
 * Each child of the main sequence whose begin condition is interactive (`delay="indefinite"`)
 * opens a new [Step]; non-interactive siblings (onEnd chains some exporters emit) merge into the
 * previous step after its settle point. Interactive sequences (shape-click triggers) compile to
 * extra steps appended in document order — output windows are not clickable, so a trigger is
 * reachable via next-click, never hover/click-on-shape (documented limitation).
 *
 * Within a step, with/after-previous nesting resolves to absolute [EffectInterval.beginMs]
 * offsets. Every behavior bundle reduces to one [EffectSpec] per target via behavior-first
 * synthesis ([BehaviorTally]); anything unrecognizable degrades to Fade ([warnings] records each
 * degrade).
 */
internal class TimelineCompiler(
    slideWidthPt: Double,
    slideHeightPt: Double,
    /** (shapeId, paragraphIndex or null) → layer ids the interval applies to. */
    private val resolveLayers: (Long, Int?) -> List<LayerIdWithBounds>,
    private val warnings: MutableList<String> = mutableListOf()
) {

    data class LayerIdWithBounds(val layerId: String, val boundsPt: RectPt)

    data class Result(
        val timeline: Timeline,
        /** Layers whose first effect is an entrance — they start hidden. */
        val initiallyHiddenLayerIds: Set<String>,
        val warnings: List<String>
    )

    private companion object {
        const val DEFAULT_EFFECT_DUR_MS = 500L
    }

    private val curves = CurveSynthesizer(slideWidthPt, slideHeightPt, warnings)

    fun compile(root: TimeNode?): Result? {
        if (root == null) return null
        val sequences = findSequences(root)
        val mainSeq = sequences.firstOrNull { it.nodeType == "mainSeq" || it.nodeType == "main_seq" }
            ?: sequences.firstOrNull()
            ?: return null
        val steps = mutableListOf<MutableList<EffectInterval>>()

        for (group in mainSeq.children) {
            val interactive = group.beginConditions.any { it.delayMs == TimeNode.INDEFINITE_MS }
            if (interactive || steps.isEmpty()) {
                steps.add(mutableListOf())
                collectIntervals(group, 0L, steps.last())
            } else {
                val offset = steps.last().maxOfOrNull { settleTime(it) } ?: 0L
                collectIntervals(group, offset, steps.last())
            }
        }

        // Interactive (shape-trigger) sequences: each click group becomes an appended step.
        for (seq in sequences.filter { it !== mainSeq && it.nodeType == "interactiveSeq" }) {
            for (group in seq.children) {
                val intervals = mutableListOf<EffectInterval>()
                collectIntervals(group, 0L, intervals)
                if (intervals.isNotEmpty()) {
                    steps.add(intervals)
                    warnings.add("Shape-click trigger compiled as click step (outputs are not interactive)")
                }
            }
        }

        val nonEmpty = steps.filter { it.isNotEmpty() }.map { Step(it.toList()) }
        if (nonEmpty.isEmpty()) return null
        return Result(
            timeline = Timeline(nonEmpty),
            initiallyHiddenLayerIds = computeInitiallyHidden(nonEmpty),
            warnings = warnings
        )
    }

    private fun findSequences(root: TimeNode): List<TimeNode> {
        val result = mutableListOf<TimeNode>()
        fun walk(node: TimeNode) {
            if (node.kind == TimeNodeKind.SEQ) result.add(node)
            // Sequences sit directly under the root par in every known exporter; one level of
            // recursion tolerates a wrapping par.
            if (node.kind == TimeNodeKind.PAR) node.children.forEach(::walk)
        }
        walk(root)
        return result
    }

    // ── Interval collection ───────────────────────────────────────────────────

    private fun collectIntervals(node: TimeNode, parentBeginMs: Long, out: MutableList<EffectInterval>) {
        val begin = parentBeginMs + nodeDelay(node)
        if (isEffectNode(node)) {
            synthesizeEffect(node, begin, out)
            return
        }
        when (node.kind) {
            TimeNodeKind.PAR, TimeNodeKind.EXCL -> {
                for (child in node.children) collectIntervals(child, begin, out)
            }
            TimeNodeKind.SEQ -> {
                var cursor = begin
                for (child in node.children) {
                    val collected = mutableListOf<EffectInterval>()
                    collectIntervals(child, cursor, collected)
                    out.addAll(collected)
                    cursor = collected.maxOfOrNull { settleTime(it) } ?: cursor
                }
            }
            TimeNodeKind.BEHAVIOR -> {
                // Stray behavior without an effect wrapper — synthesize from it alone.
                synthesizeFromBehaviors(node, listOf(node), begin, out)
            }
        }
    }

    // ── Effect synthesis ──────────────────────────────────────────────────────

    private fun synthesizeEffect(node: TimeNode, beginMs: Long, out: MutableList<EffectInterval>) {
        val behaviors = node.children.filter { it.kind == TimeNodeKind.BEHAVIOR }
        synthesizeFromBehaviors(node, behaviors, beginMs, out)
    }

    private fun synthesizeFromBehaviors(
        effectNode: TimeNode,
        behaviorNodes: List<TimeNode>,
        beginMs: Long,
        out: MutableList<EffectInterval>
    ) {
        val byTarget = behaviorNodes.mapNotNull { n -> n.behavior?.let { it.target?.let { t -> t to it } } }
            .groupBy({ it.first }, { it.second })
        if (byTarget.isEmpty()) return

        val role = roleOf(effectNode)
        val repeat = repeatOf(effectNode)
        val fill = if (effectNode.fill == "remove") FillMode.REMOVE else FillMode.HOLD

        for ((target, behaviors) in byTarget) {
            val duration = behaviors.mapNotNull { b ->
                b.durMs?.takeIf { it > 0 }?.plus(b.delayMs)
            }.maxOrNull() ?: DEFAULT_EFFECT_DUR_MS
            val layers = resolveLayers(target.shapeId, target.paragraphIndex.takeIf { !target.widensToShape })
            if (layers.isEmpty()) {
                warnings.add("Animation target shape ${target.shapeId} has no layer — interval dropped")
                continue
            }
            for (layer in layers) {
                val effect = synthesizeSpec(role, behaviors, layer.boundsPt, effectNode)
                out.add(
                    EffectInterval(
                        layerId = layer.layerId,
                        effect = effect,
                        beginMs = beginMs,
                        durMs = duration,
                        repeat = repeat,
                        fill = fill,
                        autoReverse = effectNode.autoReverse
                    )
                )
            }
        }
    }

    /** One target's behaviors reduced to the effect they describe -- see [BehaviorTally]. */
    private fun synthesizeSpec(
        role: EffectSpec.Role,
        behaviors: List<TimingBehavior>,
        layerBoundsPt: RectPt,
        effectNode: TimeNode
    ): EffectSpec {
        val tally = BehaviorTally(role, layerBoundsPt, curves, warnings)
        behaviors.forEach { tally.add(it) }
        return tally.effect(effectNode)
    }
}

private fun nodeDelay(node: TimeNode): Long =
    node.beginConditions.firstOrNull { it.delayMs != null && it.delayMs != TimeNode.INDEFINITE_MS }
        ?.delayMs ?: 0L

/** An effect node is the par carrying presetClass/presetID whose children are behaviors. */
private fun isEffectNode(node: TimeNode): Boolean =
    node.presetClass != null ||
        (node.children.isNotEmpty() && node.children.all { it.kind == TimeNodeKind.BEHAVIOR })

private fun settleTime(interval: EffectInterval): Long {
    val repeats = when (val r = interval.repeat) {
        is RepeatSpec.Count -> r.times
        RepeatSpec.Indefinite -> 1.0 // indefinite loops never delay settling
        RepeatSpec.Once -> 1.0
    }
    return interval.beginMs + (interval.durMs * repeats).toLong()
}

private fun roleOf(node: TimeNode): EffectSpec.Role = when (node.presetClass?.lowercase()) {
    "entr" -> EffectSpec.Role.ENTRANCE
    "exit" -> EffectSpec.Role.EXIT
    else -> EffectSpec.Role.EMPHASIS
}

private fun repeatOf(node: TimeNode): RepeatSpec {
    // The repeat lives on the effect par or on its behavior children, whichever is set.
    val count = node.repeatCount
        ?: node.children.firstNotNullOfOrNull { it.repeatCount }
        ?: return RepeatSpec.Once
    return when {
        count < 0 -> RepeatSpec.Indefinite
        count == 1.0 -> RepeatSpec.Once
        else -> RepeatSpec.Count(count)
    }
}

// ── Initial visibility ────────────────────────────────────────────────────────

private fun computeInitiallyHidden(steps: List<Step>): Set<String> {
    val firstRole = mutableMapOf<String, EffectSpec.Role>()
    for (step in steps) {
        for (interval in step.intervals.sortedBy { it.beginMs }) {
            firstRole.putIfAbsent(interval.layerId, interval.effect.role)
        }
    }
    return firstRole.filterValues { it == EffectSpec.Role.ENTRANCE }.keys
}
