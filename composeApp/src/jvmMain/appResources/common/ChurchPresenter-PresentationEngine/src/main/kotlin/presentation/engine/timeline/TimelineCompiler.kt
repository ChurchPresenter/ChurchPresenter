package presentation.engine.timeline

import presentation.engine.model.EffectInterval
import presentation.engine.model.EffectSpec
import presentation.engine.model.FillMode
import presentation.engine.model.LayerProperty
import presentation.engine.model.PropertyCurve
import presentation.engine.model.RectPt
import presentation.engine.model.RepeatSpec
import presentation.engine.model.Step
import presentation.engine.model.Timeline
import presentation.engine.pptx.BehaviorTarget
import presentation.engine.pptx.TimeNode
import presentation.engine.pptx.TimeNodeKind
import presentation.engine.pptx.TimingBehavior

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
 * synthesis; anything unrecognizable degrades to Fade ([warnings] records each degrade).
 */
internal class TimelineCompiler(
    private val slideWidthPt: Double,
    private val slideHeightPt: Double,
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

    /**
     * Behavior-first effect synthesis. PowerPoint serializes the full behavior list for every
     * preset, so interpreting behaviors directly covers most effects without a preset table;
     * the catalog refines named filters and backstops uninterpretable bundles.
     */
    private fun synthesizeSpec(
        role: EffectSpec.Role,
        behaviors: List<TimingBehavior>,
        layerBoundsPt: RectPt,
        effectNode: TimeNode
    ): EffectSpec {
        val curves = mutableListOf<PropertyCurve>()
        var clipEffect: EffectSpec? = null
        var sawFade = false
        var sawAppearOnly = true
        var sawCommand = false

        for (behavior in behaviors) {
            when (behavior) {
                is TimingBehavior.Command -> {
                    // A media command (playFrom/togglePause/pause/stop — embedded video/audio
                    // "Start: On Click" and its secondary interactions) is never a visual effect —
                    // the poster/first frame is already on screen, so a click should not
                    // fade/reveal anything. playFrom is the one that matters (it only needs to
                    // exist as a timeline entry so playback gating, PresentationPlayer's
                    // movieStepIndex, can find it); the others aren't drivers of anything yet, but
                    // must still be treated as non-visual rather than falling through to the
                    // preset backstop, which doesn't recognize presetClass="mediacall" and would
                    // otherwise degrade them to a spurious, misleadingly-worded Fade.
                    sawCommand = true
                }
                is TimingBehavior.AnimEffect -> {
                    sawAppearOnly = false
                    val mapped = PresetCatalog.fromFilter(behavior.filter, role)
                    when (mapped) {
                        is EffectSpec.Fade -> sawFade = true
                        null -> {
                            sawFade = true // unknown filter → fade contribution (degrade)
                            warnings.add("Unknown animEffect filter '${behavior.filter}' degraded to fade")
                        }
                        else -> clipEffect = mapped
                    }
                }
                is TimingBehavior.AnimateValue -> {
                    sawAppearOnly = false
                    val translate = translateCurves(behavior, layerBoundsPt)
                    when {
                        translate != null -> curves.add(translate)
                        behavior.attribute == "style.opacity" -> sawFade = true
                        behavior.attribute == "style.visibility" || behavior.attribute == null -> {}
                        else -> warnings.add("Unhandled anim attribute '${behavior.attribute}' ignored")
                    }
                }
                is TimingBehavior.AnimateMotion -> {
                    sawAppearOnly = false
                    motionPathCurves(behavior, curves)
                }
                is TimingBehavior.AnimateScale -> {
                    sawAppearOnly = false
                    scaleCurves(behavior, role, curves)
                }
                is TimingBehavior.AnimateRotation -> {
                    sawAppearOnly = false
                    rotationCurve(behavior)?.let { curves.add(it) }
                }
                is TimingBehavior.SetValue -> {
                    // visibility sets accompany nearly every entrance/exit; they don't negate
                    // "appear only" on their own.
                }
            }
        }

        val hasTranslate = curves.any { it.property == LayerProperty.TRANSLATE_X || it.property == LayerProperty.TRANSLATE_Y }
        val hasScale = curves.any { it.property == LayerProperty.SCALE_X || it.property == LayerProperty.SCALE_Y }
        val hasRotate = curves.any { it.property == LayerProperty.ROTATION }

        return when {
            // A media command always wins outright — PowerPoint never combines a media call with
            // a visual effect on the same node, and it must never fall through to the preset
            // backstop below (presetClass="mediacall" matches none of its entries, which would
            // otherwise degrade to a spurious Fade with a "degraded to fade" warning).
            sawCommand -> EffectSpec.Appear(role)
            // Reveal-clip effects are exclusive (a wipe over a moving layer is not a thing
            // PowerPoint produces); translate/scale beat the clip when both appear.
            clipEffect != null && !hasTranslate && !hasScale && !hasRotate -> clipEffect
            hasTranslate || hasScale || hasRotate -> {
                if (sawFade) curves.add(fadeCurve(role))
                EffectSpec.Custom(role, curves.toList())
            }
            sawFade -> EffectSpec.Fade(role)
            sawAppearOnly && behaviors.any { it is TimingBehavior.SetValue } -> EffectSpec.Appear(role)
            else -> PresetCatalog.fromPreset(effectNode.presetClass, effectNode.presetId, effectNode.presetSubtype)
                ?: EffectSpec.Fade(role).also {
                    warnings.add(
                        "Preset ${effectNode.presetClass}/${effectNode.presetId}/${effectNode.presetSubtype} degraded to fade"
                    )
                }
        }
    }

    private fun fadeCurve(role: EffectSpec.Role): PropertyCurve {
        val keyframes = if (role == EffectSpec.Role.EXIT) {
            listOf(0.0 to 1.0, 1.0 to 0.0)
        } else {
            listOf(0.0 to 0.0, 1.0 to 1.0)
        }
        return PropertyCurve(LayerProperty.ALPHA, keyframes)
    }

    /** ppt_x / ppt_y position curves → translate offsets in points from the resting center. */
    private fun translateCurves(behavior: TimingBehavior.AnimateValue, boundsPt: RectPt): PropertyCurve? {
        val property = when (behavior.attribute) {
            "ppt_x" -> LayerProperty.TRANSLATE_X
            "ppt_y" -> LayerProperty.TRANSLATE_Y
            else -> return null
        }
        val geometry = geometryOf(boundsPt)
        val resting = if (property == LayerProperty.TRANSLATE_X) geometry.x else geometry.y
        val slideDim = if (property == LayerProperty.TRANSLATE_X) slideWidthPt else slideHeightPt
        val frames = behavior.keyframes.ifEmpty {
            listOfNotNull(
                behavior.from?.let { 0.0 to it },
                behavior.to?.let { 1.0 to it }
            )
        }
        if (frames.size < 2) return null
        val resolved = frames.map { (time, expr) ->
            val value = MotionExpr.evaluate(expr, geometry) ?: return null
            time to (value - resting) * slideDim
        }
        return PropertyCurve(property, resolved)
    }

    /** Flattens an animMotion path (normalized slide coords) into translate keyframes. */
    private fun motionPathCurves(behavior: TimingBehavior.AnimateMotion, out: MutableList<PropertyCurve>) {
        val path = behavior.path ?: return
        val points = MotionPathFlattener.flatten(path) ?: run {
            warnings.add("Unparseable motion path degraded to fade")
            return
        }
        if (points.size < 2) return
        val xs = points.mapIndexed { i, p -> (i.toDouble() / (points.size - 1)) to p.first * slideWidthPt }
        val ys = points.mapIndexed { i, p -> (i.toDouble() / (points.size - 1)) to p.second * slideHeightPt }
        out.add(PropertyCurve(LayerProperty.TRANSLATE_X, xs))
        out.add(PropertyCurve(LayerProperty.TRANSLATE_Y, ys))
    }

    private fun scaleCurves(
        behavior: TimingBehavior.AnimateScale,
        role: EffectSpec.Role,
        out: MutableList<PropertyCurve>
    ) {
        val defaultFrom = if (role == EffectSpec.Role.ENTRANCE) 0.0 else 1.0
        val fromX = behavior.fromX ?: defaultFrom
        val fromY = behavior.fromY ?: defaultFrom
        val toX = behavior.toX ?: behavior.byX?.let { fromX * it } ?: 1.0
        val toY = behavior.toY ?: behavior.byY?.let { fromY * it } ?: 1.0
        out.add(PropertyCurve(LayerProperty.SCALE_X, listOf(0.0 to fromX, 1.0 to toX)))
        out.add(PropertyCurve(LayerProperty.SCALE_Y, listOf(0.0 to fromY, 1.0 to toY)))
    }

    private fun rotationCurve(behavior: TimingBehavior.AnimateRotation): PropertyCurve? {
        val from = behavior.fromDeg ?: 0.0
        val to = behavior.toDeg ?: behavior.byDeg?.plus(from) ?: return null
        return PropertyCurve(LayerProperty.ROTATION, listOf(0.0 to from, 1.0 to to))
    }

    private fun geometryOf(boundsPt: RectPt): MotionExpr.Geometry = MotionExpr.Geometry(
        x = (boundsPt.x + boundsPt.w / 2) / slideWidthPt,
        y = (boundsPt.y + boundsPt.h / 2) / slideHeightPt,
        w = boundsPt.w / slideWidthPt,
        h = boundsPt.h / slideHeightPt
    )

    // ── Initial visibility ────────────────────────────────────────────────────

    private fun computeInitiallyHidden(steps: List<Step>): Set<String> {
        val firstRole = mutableMapOf<String, EffectSpec.Role>()
        for (step in steps) {
            for (interval in step.intervals.sortedBy { it.beginMs }) {
                firstRole.putIfAbsent(interval.layerId, interval.effect.role)
            }
        }
        return firstRole.filterValues { it == EffectSpec.Role.ENTRANCE }.keys
    }
}
