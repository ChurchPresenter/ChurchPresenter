package presentation.engine.pptx

/**
 * A faithful, format-agnostic snapshot of a slide's `<p:timing>` tree. Produced by
 * [TimingParser], consumed by the timeline compiler. Nothing here is interpreted yet —
 * durations may be null (unspecified) and node semantics stay PowerPoint's.
 */
internal data class TimeNode(
    val id: Long,
    val kind: TimeNodeKind,
    /** PowerPoint node type: tmRoot, mainSeq, interactiveSeq, clickEffect, withEffect, afterEffect, … */
    val nodeType: String?,
    val beginConditions: List<TimeCondition>,
    /** Active duration in ms; null = unspecified; [INDEFINITE_MS] = indefinite. */
    val durMs: Long?,
    /** Iterations; null = once; -1.0 = indefinite. */
    val repeatCount: Double?,
    val autoReverse: Boolean,
    /** hold / remove / freeze / transition — null = default (hold semantics for effects). */
    val fill: String?,
    val restart: String?,
    val presetId: Int?,
    val presetClass: String?,
    val presetSubtype: Int?,
    /** By-paragraph / by-letter iteration on the effect node. */
    val iterateType: String?,
    val children: List<TimeNode>,
    /** Non-null only when [kind] == [TimeNodeKind.BEHAVIOR]. */
    val behavior: TimingBehavior?
) {
    companion object {
        const val INDEFINITE_MS = -1L
    }
}

internal enum class TimeNodeKind { PAR, SEQ, EXCL, BEHAVIOR }

internal data class TimeCondition(
    /** Delay in ms; null when unspecified; [TimeNode.INDEFINITE_MS] = indefinite (click-gated). */
    val delayMs: Long?,
    /** Trigger event (onClick, onBegin, onEnd, …); null = plain delay. */
    val event: String?,
    /** Shape id of an interactive trigger target, when the condition references one. */
    val triggerShapeId: Long?,
    /** Time node id the condition references (for onEnd-of-node chains). */
    val triggerNodeId: Long?
)

/** The animation target: a shape, optionally narrowed to one paragraph. */
internal data class BehaviorTarget(
    val shapeId: Long,
    /** Paragraph index when the target is a text range (pRg with st == end). */
    val paragraphIndex: Int?,
    /** True when a paragraph range spanned more than one paragraph (degrades to whole shape). */
    val widensToShape: Boolean = false
)

internal sealed interface TimingBehavior {
    val target: BehaviorTarget?
    val durMs: Long?
    val delayMs: Long

    /** `<p:set>` — typically style.visibility. */
    data class SetValue(
        override val target: BehaviorTarget?,
        override val durMs: Long?,
        override val delayMs: Long,
        val attribute: String?,
        val toValue: String?
    ) : TimingBehavior

    /** `<p:animEffect>` — named transition filter (fade, wipe(down), blinds(horizontal), …). */
    data class AnimEffect(
        override val target: BehaviorTarget?,
        override val durMs: Long?,
        override val delayMs: Long,
        /** "in" or "out". */
        val transition: String,
        val filter: String?
    ) : TimingBehavior

    /** `<p:anim>` — raw attribute curve. */
    data class AnimateValue(
        override val target: BehaviorTarget?,
        override val durMs: Long?,
        override val delayMs: Long,
        val attribute: String?,
        val from: String?,
        val to: String?,
        val by: String?,
        /** (normalizedTime 0..1, expression) keyframes from tavLst. */
        val keyframes: List<Pair<Double, String>>
    ) : TimingBehavior

    /** `<p:animMotion>` — motion along a path in normalized slide space. */
    data class AnimateMotion(
        override val target: BehaviorTarget?,
        override val durMs: Long?,
        override val delayMs: Long,
        val path: String?
    ) : TimingBehavior

    /** `<p:animRot>` — rotation; degrees (converted from 60000ths). */
    data class AnimateRotation(
        override val target: BehaviorTarget?,
        override val durMs: Long?,
        override val delayMs: Long,
        val fromDeg: Double?,
        val toDeg: Double?,
        val byDeg: Double?
    ) : TimingBehavior

    /** `<p:animScale>` — scale; factors (converted from percent-thousandths). */
    data class AnimateScale(
        override val target: BehaviorTarget?,
        override val durMs: Long?,
        override val delayMs: Long,
        val fromX: Double?, val fromY: Double?,
        val toX: Double?, val toY: Double?,
        val byX: Double?, val byY: Double?
    ) : TimingBehavior
}
