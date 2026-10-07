package org.churchpresenter.presentationengine.timeline

import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.EffectInterval
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.FillMode
import org.churchpresenter.presentationengine.model.LayerProperty
import org.churchpresenter.presentationengine.model.LayerState
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.model.RepeatSpec
import org.churchpresenter.presentationengine.model.Timeline
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure timeline evaluation: `(stepIndex, elapsedMs) → per-layer [LayerState]`. No clocks, no
 * rendering — the player samples this every frame.
 *
 * Resting visibility (which layers are visible when a step begins) is folded once at
 * construction: entrance effects leave their layer visible after settling, exits leave it
 * hidden, `fill="remove"` intervals leave it unchanged.
 */
class TimelineEvaluator(
    private val timeline: Timeline,
    private val slideWidthPt: Double,
    private val slideHeightPt: Double,
    /** Layer bounds in points — used for fly-style offscreen offsets. */
    private val layerBounds: Map<String, RectPt>,
    initiallyHiddenLayerIds: Set<String>
) {

    data class StepStatus(
        /** All finite intervals of the step have completed. */
        val settled: Boolean,
        /** An indefinite-repeat interval keeps animating (never blocks [settled]). */
        val indefiniteActive: Boolean,
        /** Elapsed ms at which the step settles (0 for an empty step). */
        val settleAtMs: Long
    )

    data class Frame(val layerStates: Map<String, LayerState>, val status: StepStatus)

    val stepCount: Int get() = timeline.stepCount

    /** All layer ids any interval touches. */
    private val animatedLayerIds: Set<String> =
        timeline.steps.flatMap { step -> step.intervals.map { it.layerId } }.toSet()

    /** restingVisible[s] = visibility of each animated layer at the instant step s begins. */
    private val restingVisible: List<Map<String, Boolean>> = buildList {
        var current = animatedLayerIds.associateWith { it !in initiallyHiddenLayerIds }
        add(current)
        for (step in timeline.steps) {
            val next = current.toMutableMap()
            for (interval in step.intervals.sortedBy { it.beginMs }) {
                if (interval.fill == FillMode.REMOVE) continue
                when (interval.effect.role) {
                    EffectSpec.Role.ENTRANCE -> next[interval.layerId] = true
                    EffectSpec.Role.EXIT -> next[interval.layerId] = false
                    EffectSpec.Role.EMPHASIS -> {}
                }
            }
            current = next
            add(current)
        }
    }

    /** The pre-click state of the slide: entrance targets hidden, everything else at rest. */
    fun initialFrame(): Frame {
        val resting = restingVisible[0]
        val states = animatedLayerIds.associateWith { id ->
            if (resting[id] != false) LayerState.VISIBLE else LayerState.HIDDEN
        }
        return Frame(states, StepStatus(settled = true, indefiniteActive = false, settleAtMs = 0))
    }

    fun evaluate(stepIndex: Int, elapsedMs: Long): Frame {
        require(stepIndex in timeline.steps.indices) { "step $stepIndex out of range" }
        val step = timeline.steps[stepIndex]
        val resting = restingVisible[stepIndex]
        val states = mutableMapOf<String, LayerState>()
        for (layerId in animatedLayerIds) {
            states[layerId] = if (resting[layerId] != false) LayerState.VISIBLE else LayerState.HIDDEN
        }

        var settleAt = 0L
        var indefiniteActive = false
        for (interval in step.intervals.sortedBy { it.beginMs }) {
            if (interval.repeat is RepeatSpec.Indefinite) indefiniteActive = true
            settleAt = maxOf(settleAt, finiteSettleTime(interval))
            states[interval.layerId] = evaluateInterval(
                interval,
                elapsedMs,
                restingVisible = resting[interval.layerId] != false
            )
        }
        return Frame(
            layerStates = states,
            status = StepStatus(
                settled = elapsedMs >= settleAt,
                indefiniteActive = indefiniteActive,
                settleAtMs = settleAt
            )
        )
    }

    /** The resting visibility of every animated layer once all steps have completed. */
    fun finalVisibility(): Map<String, Boolean> = restingVisible.last()

    private fun finiteSettleTime(interval: EffectInterval): Long {
        val cycles = when (val repeat = interval.repeat) {
            is RepeatSpec.Count -> repeat.times
            RepeatSpec.Indefinite -> 1.0
            RepeatSpec.Once -> 1.0
        }
        return interval.beginMs + (interval.durMs * cycles).toLong()
    }

    private fun evaluateInterval(interval: EffectInterval, elapsedMs: Long, restingVisible: Boolean): LayerState {
        val role = interval.effect.role
        val t = elapsedMs - interval.beginMs
        if (t < 0) {
            // Not started: an entrance target is still hidden; everything else rests.
            return when {
                role == EffectSpec.Role.ENTRANCE && !restingVisible -> LayerState.HIDDEN
                restingVisible -> LayerState.VISIBLE
                else -> LayerState.HIDDEN
            }
        }
        val duration = interval.durMs.coerceAtLeast(1)
        val totalCycles = when (val repeat = interval.repeat) {
            is RepeatSpec.Count -> repeat.times
            RepeatSpec.Indefinite -> Double.POSITIVE_INFINITY
            RepeatSpec.Once -> 1.0
        }
        val rawCycles = t.toDouble() / duration
        val finished = rawCycles >= totalCycles
        if (finished) {
            return when (interval.fill) {
                FillMode.REMOVE -> if (restingVisible) LayerState.VISIBLE else LayerState.HIDDEN
                FillMode.HOLD -> when (role) {
                    EffectSpec.Role.ENTRANCE -> sample(interval.effect, 1.0, interval.layerId)
                    EffectSpec.Role.EXIT -> LayerState.HIDDEN
                    EffectSpec.Role.EMPHASIS -> sample(interval.effect, 1.0, interval.layerId)
                }
            }
        }
        var progress = rawCycles - kotlin.math.floor(rawCycles)
        if (interval.autoReverse) {
            // Fold each cycle into a there-and-back ramp.
            progress = 1.0 - kotlin.math.abs(1.0 - 2.0 * progress)
        }
        return sample(interval.effect, progress, interval.layerId)
    }

    // ── Effect sampling ───────────────────────────────────────────────────────

    private fun sample(effect: EffectSpec, progress: Double, layerId: String): LayerState {
        val p = progress.coerceIn(0.0, 1.0)
        return when (effect) {
            is EffectSpec.Appear ->
                if (effect.role == EffectSpec.Role.EXIT) LayerState.HIDDEN else LayerState.VISIBLE

            is EffectSpec.Fade -> LayerState(
                alpha = if (effect.role == EffectSpec.Role.EXIT) 1.0 - p else p
            )

            is EffectSpec.Fly -> {
                if (effect.role == EffectSpec.Role.EXIT) {
                    // Exit: move toward [direction] until fully offscreen.
                    val (dx, dy) = flyOffset(effect.direction, layerId)
                    LayerState(translateXPt = dx * p, translateYPt = dy * p)
                } else {
                    // Entrance: start fully offscreen on the side opposite the movement
                    // direction (moving UP means arriving from below the slide).
                    val (dx, dy) = flyOffset(RevealGeometry.opposite(effect.direction), layerId)
                    val remaining = 1.0 - p
                    LayerState(translateXPt = dx * remaining, translateYPt = dy * remaining)
                }
            }

            is EffectSpec.Wipe -> LayerState(clip = RevealGeometry.wipeClip(effect.direction, effect.role, p))

            is EffectSpec.Split -> LayerState(clip = RevealGeometry.splitClip(effect.horizontal, effect.role, p))

            is EffectSpec.Zoom -> {
                val scale = if (effect.role == EffectSpec.Role.EXIT) {
                    1.0 + (effect.fromScale - 1.0) * p
                } else {
                    effect.fromScale + (1.0 - effect.fromScale) * p
                }
                LayerState(scaleX = scale, scaleY = scale, alpha = if (scale < 0.05) 0.0 else 1.0)
            }

            is EffectSpec.Spin -> LayerState(rotationDeg = effect.degrees * p)

            is EffectSpec.GrowShrink -> LayerState(
                scaleX = 1.0 + (effect.toScaleX - 1.0) * p,
                scaleY = 1.0 + (effect.toScaleY - 1.0) * p
            )

            is EffectSpec.Pulse -> {
                val bump = sin(PI * p)
                LayerState(scaleX = 1.0 + 0.08 * bump, scaleY = 1.0 + 0.08 * bump, alpha = 1.0 - 0.15 * bump)
            }

            is EffectSpec.SetVisibility -> if (effect.visible) LayerState.VISIBLE else LayerState.HIDDEN

            is EffectSpec.MotionPath -> {
                val points = MotionPathFlattener.flatten(effect.path)
                if (points.isNullOrEmpty()) {
                    LayerState.VISIBLE
                } else {
                    val index = (p * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                    LayerState(
                        translateXPt = points[index].first * slideWidthPt,
                        translateYPt = points[index].second * slideHeightPt
                    )
                }
            }

            is EffectSpec.Custom -> sampleCurves(effect, p)
        }
    }

    private fun sampleCurves(effect: EffectSpec.Custom, p: Double): LayerState {
        var state = LayerState.VISIBLE
        for (curve in effect.curves) {
            val value = RevealGeometry.interpolate(curve, p) ?: continue
            state = when (curve.property) {
                LayerProperty.ALPHA -> state.copy(alpha = value.coerceIn(0.0, 1.0))
                LayerProperty.TRANSLATE_X -> state.copy(translateXPt = value)
                LayerProperty.TRANSLATE_Y -> state.copy(translateYPt = value)
                LayerProperty.SCALE_X -> state.copy(scaleX = value)
                LayerProperty.SCALE_Y -> state.copy(scaleY = value)
                LayerProperty.ROTATION -> state.copy(rotationDeg = value)
            }
        }
        return state
    }

    /** Offset (in points) that moves the layer fully offscreen in [direction]. */
    private fun flyOffset(direction: Direction, layerId: String): Pair<Double, Double> {
        val bounds = layerBounds[layerId] ?: RectPt(0.0, 0.0, slideWidthPt, slideHeightPt)
        return when (direction) {
            Direction.UP -> 0.0 to -(bounds.y + bounds.h)
            Direction.DOWN -> 0.0 to (slideHeightPt - bounds.y)
            Direction.LEFT -> -(bounds.x + bounds.w) to 0.0
            Direction.RIGHT -> (slideWidthPt - bounds.x) to 0.0
            Direction.IN, Direction.OUT -> 0.0 to 0.0
        }
    }
}
