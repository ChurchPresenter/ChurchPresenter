package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.model.LottieGenConfig

/**
 * Compiles a spec's animation tracks into Lottie properties.
 *
 * Split out of SpecStyleGenerator because every one of these took the same three values --
 * the builder, the config and the layout -- as its first arguments, which is what a class is for.
 */
internal class SpecTracks(
    private val builder: LottieBuilder,
    private val cfg: LottieGenConfig,
    private val layout: SpecLayoutContext,
) {

    fun trackFor(element: ElementSpec, property: AnimProperty): AnimTrack? =
        element.tracks.firstOrNull { it.property == property }


    fun easingOf(kind: EasingKind): JsonObject = when (kind) {
        EasingKind.DEFAULT -> Easing.DEFAULT
        EasingKind.LINEAR -> Easing.LINEAR
    }

    /** Compiles a track's keyframes (align override applied) via the shared builder. */

    fun compileTrack(
        track: AnimTrack,
        toValue: (List<Double>) -> JsonArray
    ): JsonArray {
        val keyframes = track.alignOverrides[cfg.align] ?: track.keyframes
        val inputs = keyframes.map { KeyframeInput(it.pct, toValue(it.values)) }
        return buildKeyframes(
            inputs, builder.inFrames, builder.holdFrames, builder.outFrames, easingOf(track.easing)
        )
    }


    fun positionProp(
        element: ElementSpec,
        rest: SpecPoint,
        elementW: Double,
        elementH: Double
    ): JsonObject {
        val track = trackFor(element, AnimProperty.POSITION_OFFSET)
            ?: return LottieBuilder.staticPropArray(rest.x, rest.y, 0.0)
        val flow = layout.flowSign(element.placement)
        val kfs = compileTrack(track) { values ->
            val dx = offsetToPx(values.getOrElse(0) { 0.0 }, track.offsetUnit, elementW, elementH)
            val dy = offsetToPx(values.getOrElse(1) { 0.0 }, track.offsetUnit, elementW, elementH)
            jsonArrayOf(rest.x + flow * dx, rest.y + dy, 0.0)
        }
        return LottieBuilder.animatedProp(kfs)
    }


    fun offsetToPx(
        value: Double,
        unit: OffsetUnit,
        elementW: Double,
        elementH: Double
    ): Double = when (unit) {
        OffsetUnit.EM -> layout.em(value)
        OffsetUnit.ELEMENT_WIDTH -> value * elementW
        OffsetUnit.ELEMENT_HEIGHT -> value * elementH
    }

    /**
     * Layer opacity. Shapes rest at 100 (their fill already carries the role alpha);
     * text/logo rest at the field's alpha, and track values scale against it so a
     * keyframe value of 100 means "fully at the configured alpha".
     */

    fun opacityProp(
        element: ElementSpec,
        restOpacity: Double
    ): JsonObject {
        val track = trackFor(element, AnimProperty.OPACITY)
            ?: return LottieBuilder.staticProp(restOpacity)
        val kfs = compileTrack(track) { values ->
            jsonArrayOf(values.getOrElse(0) { 100.0 } * restOpacity / 100.0)
        }
        return LottieBuilder.animatedProp(kfs)
    }


    fun rotationProp(element: ElementSpec): JsonObject? {
        val track = trackFor(element, AnimProperty.ROTATION) ?: return null
        val kfs = compileTrack(track) { values ->
            jsonArrayOf(values.getOrElse(0) { 0.0 })
        }
        return LottieBuilder.animatedProp(kfs)
    }


    fun scaleProp(
        element: ElementSpec,
        baseScale: Double
    ): JsonObject = scalePropXY(element, baseScale, baseScale)

    /** Like [scaleProp] but with a per-axis base (image STRETCH sizing). */

    fun scalePropXY(
        element: ElementSpec,
        baseScaleX: Double,
        baseScaleY: Double
    ): JsonObject {
        val track = trackFor(element, AnimProperty.SCALE)
            ?: return LottieBuilder.staticPropArray(baseScaleX, baseScaleY, 100.0)
        val kfs = compileTrack(track) { values ->
            val sx = values.getOrElse(0) { 100.0 } * baseScaleX / 100.0
            val sy = values.getOrElse(1) { values.getOrElse(0) { 100.0 } } * baseScaleY / 100.0
            jsonArrayOf(sx, sy, 100.0)
        }
        return LottieBuilder.animatedProp(kfs)
    }
}
