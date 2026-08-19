package presentation.engine.model

/**
 * A concrete animation effect. Every effect reduces to a sampled [LayerState] — the renderer
 * only ever sees alpha/transform/clip, never effect names. Unknown source effects are mapped to
 * [Fade] by the compilers (the engine-wide degrade rule: content always appears).
 */
sealed interface EffectSpec {

    /** Entrance/exit role decides the layer's resting visibility outside the interval. */
    enum class Role { ENTRANCE, EMPHASIS, EXIT }

    val role: Role

    data class Appear(override val role: Role = Role.ENTRANCE) : EffectSpec

    data class Fade(override val role: Role = Role.ENTRANCE) : EffectSpec

    /** Flies from/to outside the slide bounds in [direction]. */
    data class Fly(
        override val role: Role,
        val direction: Direction
    ) : EffectSpec

    /** Reveal wipe toward [direction]. */
    data class Wipe(
        override val role: Role,
        val direction: Direction
    ) : EffectSpec

    /** Reveal from/to the middle, vertically or horizontally. */
    data class Split(
        override val role: Role,
        val horizontal: Boolean,
        val outward: Boolean
    ) : EffectSpec

    data class Zoom(
        override val role: Role,
        val fromScale: Double
    ) : EffectSpec

    data class Spin(
        override val role: Role = Role.EMPHASIS,
        val degrees: Double = 360.0
    ) : EffectSpec

    data class GrowShrink(
        override val role: Role = Role.EMPHASIS,
        val toScaleX: Double,
        val toScaleY: Double
    ) : EffectSpec

    data class Pulse(override val role: Role = Role.EMPHASIS) : EffectSpec

    /** Instant visibility flip (PowerPoint `<p:set>` on style.visibility). */
    data class SetVisibility(
        override val role: Role,
        val visible: Boolean
    ) : EffectSpec

    /** Motion along a path in normalized slide coordinates (PowerPoint `animMotion`). */
    data class MotionPath(
        override val role: Role,
        /** SVG-like path string in PowerPoint's normalized 0..1 slide space. */
        val path: String,
        val relative: Boolean
    ) : EffectSpec

    /**
     * Raw attribute-curve animation for behaviors the named effects don't cover.
     * Each curve animates one [LayerState] channel between keyframes.
     */
    data class Custom(
        override val role: Role,
        val curves: List<PropertyCurve>
    ) : EffectSpec
}

/** One animated channel of [LayerState]. */
data class PropertyCurve(
    val property: LayerProperty,
    /** (normalizedTime 0..1, value) keyframes, sorted by time. */
    val keyframes: List<Pair<Double, Double>>
)

enum class LayerProperty { ALPHA, TRANSLATE_X, TRANSLATE_Y, SCALE_X, SCALE_Y, ROTATION }

/**
 * The fully evaluated state of one layer at one instant. Translation is in slide points,
 * scale/rotation are about the layer's center, [clip] restricts drawing to a sub-rect of the
 * layer for reveal effects.
 */
data class LayerState(
    val visible: Boolean = true,
    val alpha: Double = 1.0,
    val translateXPt: Double = 0.0,
    val translateYPt: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotationDeg: Double = 0.0,
    val clip: RevealClip? = null
) {
    companion object {
        val VISIBLE = LayerState()
        val HIDDEN = LayerState(visible = false)
    }
}

/**
 * A reveal rectangle in the layer's own normalized space (0..1 on both axes) — the portion of
 * the layer currently shown by wipe/split-style effects.
 */
data class RevealClip(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double
)
