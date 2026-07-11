package presentation.engine.model

/**
 * The animation program of one slide, compiled from the source file's timing tree
 * (PPTX `<p:timing>` or Keynote build archives) into a click-driven step list.
 *
 * One [Step] corresponds to one operator "advance" action; within a step every
 * [EffectInterval] has an absolute begin time so with-previous/after-previous semantics are
 * already resolved at compile time.
 */
data class Timeline(val steps: List<Step>) {
    val stepCount: Int get() = steps.size
}

data class Step(val intervals: List<EffectInterval>)

data class EffectInterval(
    val layerId: String,
    val effect: EffectSpec,
    /** Absolute offset from step start, ms. */
    val beginMs: Long,
    val durMs: Long,
    val repeat: RepeatSpec = RepeatSpec.Once,
    val fill: FillMode = FillMode.HOLD,
    val autoReverse: Boolean = false
)

sealed interface RepeatSpec {
    data object Once : RepeatSpec
    data class Count(val times: Double) : RepeatSpec
    /** Loops until the step is advanced past; never blocks a step from settling. */
    data object Indefinite : RepeatSpec
}

enum class FillMode {
    /** Layer keeps the effect's end state (PowerPoint `fill="hold"`, the default). */
    HOLD,
    /** Layer snaps back to its pre-effect state when the interval completes (`fill="remove"`). */
    REMOVE
}
