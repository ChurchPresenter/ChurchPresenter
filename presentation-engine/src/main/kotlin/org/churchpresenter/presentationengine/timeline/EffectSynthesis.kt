package org.churchpresenter.presentationengine.timeline

import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.LayerProperty
import org.churchpresenter.presentationengine.model.PropertyCurve
import org.churchpresenter.presentationengine.model.RectPt
import org.churchpresenter.presentationengine.pptx.TimeNode
import org.churchpresenter.presentationengine.pptx.TimingBehavior

/** The property curves PowerPoint's animate behaviors describe, in slide points. */
internal class CurveSynthesizer(
    private val slideWidthPt: Double,
    private val slideHeightPt: Double,
    private val warnings: MutableList<String>,
) {

    fun fadeCurve(role: EffectSpec.Role): PropertyCurve {
        val keyframes = if (role == EffectSpec.Role.EXIT) {
            listOf(0.0 to 1.0, 1.0 to 0.0)
        } else {
            listOf(0.0 to 0.0, 1.0 to 1.0)
        }
        return PropertyCurve(LayerProperty.ALPHA, keyframes)
    }

    /** ppt_x / ppt_y position curves → translate offsets in points from the resting center. */
    fun translateCurves(behavior: TimingBehavior.AnimateValue, boundsPt: RectPt): PropertyCurve? {
        val property = when (behavior.attribute) {
            "ppt_x" -> LayerProperty.TRANSLATE_X
            "ppt_y" -> LayerProperty.TRANSLATE_Y
            else -> null
        } ?: return null
        val geometry = geometryOf(boundsPt)
        val resting = if (property == LayerProperty.TRANSLATE_X) geometry.x else geometry.y
        val slideDim = if (property == LayerProperty.TRANSLATE_X) slideWidthPt else slideHeightPt
        val frames = behavior.keyframes.ifEmpty {
            listOfNotNull(
                behavior.from?.let { 0.0 to it },
                behavior.to?.let { 1.0 to it }
            )
        }
        // Every keyframe must evaluate: one that cannot leaves no curve at all.
        val resolved = frames.takeIf { it.size >= 2 }
            ?.map { (time, expr) -> MotionExpr.evaluate(expr, geometry)?.let { time to (it - resting) * slideDim } }
            ?.takeIf { points -> points.all { it != null } }
            ?.filterNotNull()
            ?: return null
        return PropertyCurve(property, resolved)
    }

    /** Flattens an animMotion path (normalized slide coords) into translate keyframes. */
    fun motionPathCurves(behavior: TimingBehavior.AnimateMotion, out: MutableList<PropertyCurve>) {
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

    fun scaleCurves(behavior: TimingBehavior.AnimateScale, role: EffectSpec.Role, out: MutableList<PropertyCurve>) {
        val defaultFrom = if (role == EffectSpec.Role.ENTRANCE) 0.0 else 1.0
        val fromX = behavior.fromX ?: defaultFrom
        val fromY = behavior.fromY ?: defaultFrom
        val toX = behavior.toX ?: behavior.byX?.let { fromX * it } ?: 1.0
        val toY = behavior.toY ?: behavior.byY?.let { fromY * it } ?: 1.0
        out.add(PropertyCurve(LayerProperty.SCALE_X, listOf(0.0 to fromX, 1.0 to toX)))
        out.add(PropertyCurve(LayerProperty.SCALE_Y, listOf(0.0 to fromY, 1.0 to toY)))
    }

    fun rotationCurve(behavior: TimingBehavior.AnimateRotation): PropertyCurve? {
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
}

/**
 * Behavior-first effect synthesis for one target: what its behaviors add up to, gathered with
 * [add] behavior by behavior, then decided by [effect]. PowerPoint serializes the full behavior
 * list for every preset, so interpreting behaviors directly covers most effects without a preset
 * table; the catalog refines named filters and backstops uninterpretable bundles.
 */
internal class BehaviorTally(
    private val role: EffectSpec.Role,
    private val layerBoundsPt: RectPt,
    private val curvesOf: CurveSynthesizer,
    private val warnings: MutableList<String>,
) {
    private val curves = mutableListOf<PropertyCurve>()
    private var clipEffect: EffectSpec? = null
    private var sawFade = false
    private var sawAppearOnly = true
    private var sawCommand = false
    private var sawSet = false

    fun add(behavior: TimingBehavior) {
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
            is TimingBehavior.AnimEffect -> addAnimEffect(behavior)
            is TimingBehavior.AnimateValue -> addAnimateValue(behavior)
            is TimingBehavior.AnimateMotion -> {
                sawAppearOnly = false
                curvesOf.motionPathCurves(behavior, curves)
            }
            is TimingBehavior.AnimateScale -> {
                sawAppearOnly = false
                curvesOf.scaleCurves(behavior, role, curves)
            }
            is TimingBehavior.AnimateRotation -> {
                sawAppearOnly = false
                curvesOf.rotationCurve(behavior)?.let { curves.add(it) }
            }
            // visibility sets accompany nearly every entrance/exit; they don't negate "appear
            // only" on their own.
            is TimingBehavior.SetValue -> sawSet = true
        }
    }

    private fun addAnimEffect(behavior: TimingBehavior.AnimEffect) {
        sawAppearOnly = false
        when (val mapped = PresetCatalog.fromFilter(behavior.filter, role)) {
            is EffectSpec.Fade -> sawFade = true
            null -> {
                sawFade = true // unknown filter → fade contribution (degrade)
                warnings.add("Unknown animEffect filter '${behavior.filter}' degraded to fade")
            }
            else -> clipEffect = mapped
        }
    }

    private fun addAnimateValue(behavior: TimingBehavior.AnimateValue) {
        sawAppearOnly = false
        val translate = curvesOf.translateCurves(behavior, layerBoundsPt)
        when {
            translate != null -> curves.add(translate)
            behavior.attribute == "style.opacity" -> sawFade = true
            behavior.attribute == "style.visibility" || behavior.attribute == null -> {}
            else -> warnings.add("Unhandled anim attribute '${behavior.attribute}' ignored")
        }
    }

    /** The one effect the behaviors added so far amount to. */
    fun effect(effectNode: TimeNode): EffectSpec {
        val moves = curves.any {
            it.property == LayerProperty.TRANSLATE_X || it.property == LayerProperty.TRANSLATE_Y ||
                it.property == LayerProperty.SCALE_X || it.property == LayerProperty.SCALE_Y ||
                it.property == LayerProperty.ROTATION
        }
        val clip = clipEffect
        return when {
            // A media command always wins outright — PowerPoint never combines a media call with
            // a visual effect on the same node, and it must never fall through to the preset
            // backstop below (presetClass="mediacall" matches none of its entries, which would
            // otherwise degrade to a spurious Fade with a "degraded to fade" warning).
            sawCommand -> EffectSpec.Appear(role)
            // Reveal-clip effects are exclusive (a wipe over a moving layer is not a thing
            // PowerPoint produces); translate/scale beat the clip when both appear.
            clip != null && !moves -> clip
            moves -> {
                if (sawFade) curves.add(curvesOf.fadeCurve(role))
                EffectSpec.Custom(role, curves.toList())
            }
            sawFade -> EffectSpec.Fade(role)
            sawAppearOnly && sawSet -> EffectSpec.Appear(role)
            else -> presetBackstop(effectNode)
        }
    }

    private fun presetBackstop(effectNode: TimeNode): EffectSpec =
        PresetCatalog.fromPreset(effectNode.presetClass, effectNode.presetId, effectNode.presetSubtype)
            ?: EffectSpec.Fade(role).also {
                warnings.add(
                    "Preset ${effectNode.presetClass}/${effectNode.presetId}/" +
                        "${effectNode.presetSubtype} degraded to fade"
                )
            }
}
