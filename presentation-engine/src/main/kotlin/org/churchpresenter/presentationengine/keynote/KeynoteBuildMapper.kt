package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.keynote.KnFields as F
import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.EffectInterval
import org.churchpresenter.presentationengine.model.EffectSpec
import org.churchpresenter.presentationengine.model.SlideTransitionSpec
import org.churchpresenter.presentationengine.model.Step
import org.churchpresenter.presentationengine.model.Timeline
import org.churchpresenter.presentationengine.model.TransitionType

/**
 * Maps Keynote builds and transitions onto the engine's shared effect model.
 *
 * Builds: `KN.SlideArchive.buildChunks` (in document order) drive the click sequence — a chunk
 * with `automatic=true` joins the previous step after its delay, anything else opens a new
 * click step. Each chunk's build carries the effect name
 * (`apple:build-effect:…`), duration and direction. Text deliveries are read from
 * `BUILD_DELIVERY`: "By Paragraph"/"By Bullet" fan a multi-paragraph text build into one click
 * step per paragraph (see [isParagraphDelivery] — exact delivery string(s) are provisional,
 * validated per-deck via DumpKeynote, same discipline as the direction constants below); word/
 * character delivery still degrades to a whole-object build.
 *
 * Interval layer ids use the `kn-<drawableId>` convention shared with KeynoteLayerPlanner;
 * paragraph-fanned intervals use `kn-<drawableId>-p<paragraphIndex>` (see [paragraphLayerIdFor]).
 */
internal object KeynoteBuildMapper {

    fun layerIdFor(drawableId: Long): String = "kn-$drawableId"

    fun paragraphLayerIdFor(drawableId: Long, paragraphIndex: Int): String = "kn-$drawableId-p$paragraphIndex"

    class Result(
        val timeline: Timeline?,
        val builtDrawableIds: Set<Long>,
        val paragraphBuiltDrawableIds: Set<Long> = emptySet()
    )

    private class Build(
        val drawableId: Long,
        val role: EffectSpec.Role,
        val effect: String,
        val durationMs: Long,
        val direction: Long?,
        val delivery: String?
    )

    fun map(index: ObjectIndex, slide: IwaMessage, drawables: List<KnPlacedDrawable>): Result? {
        val paragraphCountOf: Map<Long, Int> = drawables.mapNotNull { placed ->
            (placed.drawable as? KnDrawable.Text)?.let { placed.id to it.paragraphs.size }
        }.toMap()

        val builds = slide.messages(F.SLIDE_BUILDS)
            .mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
            .mapNotNull { buildRef -> readBuild(index, buildRef)?.let { buildRef to it } }
            .toMap()
        if (builds.isEmpty()) return null

        val orderedBuilds = orderedBuilds(index, slide, builds)
        if (orderedBuilds.isEmpty()) return null

        val steps = mutableListOf<MutableList<EffectInterval>>()
        val paragraphBuiltDrawableIds = mutableSetOf<Long>()
        for ((build, automatic, delayMs) in orderedBuilds) {
            val paragraphCount = paragraphCountOf[build.drawableId]
            if (isParagraphDelivery(build.delivery) && paragraphCount != null && paragraphCount > 1) {
                paragraphBuiltDrawableIds.add(build.drawableId)
                appendParagraphBuild(steps, build, paragraphCount, automatic, delayMs)
            } else {
                val interval = EffectInterval(
                    layerId = layerIdFor(build.drawableId),
                    effect = mapEffect(build),
                    beginMs = 0,
                    durMs = build.durationMs
                )
                if (automatic && steps.isNotEmpty()) {
                    appendToCurrentStep(steps, interval, delayMs)
                } else {
                    steps.add(mutableListOf(interval.copy(beginMs = delayMs)))
                }
            }
        }

        return Result(
            timeline = Timeline(steps.map { Step(it.toList()) }),
            builtDrawableIds = orderedBuilds.map { it.first.drawableId }.toSet(),
            paragraphBuiltDrawableIds = paragraphBuiltDrawableIds
        )
    }

    /** The build archive [buildRef] names, or null when it is unreadable or targets no drawable. */
    private fun readBuild(index: ObjectIndex, buildRef: Long): Build? {
        val build = index.message(buildRef) ?: return null
        val drawableId = build.message(F.BUILD_DRAWABLE)?.varint(F.REFERENCE_IDENTIFIER) ?: return null
        val anim = build.message(F.BUILD_ATTRIBUTES)?.message(F.BUILD_ATTRS_ANIMATION)
        val effect = anim?.string(F.ANIM_ATTRS_EFFECT) ?: ""
        val durationMs = ((anim?.double(F.ANIM_ATTRS_DURATION) ?: 0.5) * 1000).toLong().coerceAtLeast(1)
        return Build(
            drawableId = drawableId,
            role = roleOf(effect, anim?.string(F.ANIM_ATTRS_TYPE)),
            effect = effect,
            durationMs = durationMs,
            direction = anim?.varint(F.ANIM_ATTRS_DIRECTION),
            delivery = build.string(F.BUILD_DELIVERY)
        )
    }

    /**
     * What a build does to its drawable. Matched case-insensitively, as [mapEffect] matches the same
     * string: the role check deciding a movie build is an entrance is what leaves the video area
     * blank until the first click, and a capitalised effect name must not reintroduce that.
     */
    private fun roleOf(effect: String, animationType: String?): EffectSpec.Role {
        val type = animationType?.lowercase() ?: ""
        return when {
            // Movie start/pause/stop builds are Keynote "actions," not entrances — the
            // poster/video is visible on the slide from the moment it appears; the build
            // only triggers playback. animation_type still reports "In" for these, so they
            // must be special-cased ahead of that check or the layer would incorrectly stay
            // hidden until the click (validated hands-on: a movie build classified as
            // ENTRANCE left the video area blank until the first click).
            effect.lowercase().contains("movie") -> EffectSpec.Role.EMPHASIS
            type.contains("out") -> EffectSpec.Role.EXIT
            type.contains("in") -> EffectSpec.Role.ENTRANCE
            type.contains("action") -> EffectSpec.Role.EMPHASIS
            else -> EffectSpec.Role.ENTRANCE
        }
    }

    /** Builds in click order, each with whether it runs automatically and after what delay. */
    private fun orderedBuilds(
        index: ObjectIndex,
        slide: IwaMessage,
        builds: Map<Long, Build>,
    ): List<Triple<Build, Boolean, Long>> {
        val chunkMessages = slide.messages(F.SLIDE_BUILD_CHUNKS)
            .mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
            .mapNotNull { index.message(it) }
        // No chunk list (older documents): every build is its own click step.
        if (chunkMessages.isEmpty()) return builds.values.map { Triple(it, false, 0L) }
        return chunkMessages.mapNotNull { chunk ->
            val build = chunk.message(F.BUILD_CHUNK_BUILD)?.varint(F.REFERENCE_IDENTIFIER)
                ?.let { builds[it] } ?: return@mapNotNull null
            val automatic = chunk.bool(F.BUILD_CHUNK_AUTOMATIC) == true
            val delayMs = ((chunk.double(F.BUILD_CHUNK_DELAY) ?: 0.0) * 1000).toLong()
            Triple(build, automatic, delayMs)
        }
    }

    /**
     * Each paragraph/bullet gets its own click step, matching Keynote's real on-stage behavior
     * (each bullet needs its own click/keypress) — only the first paragraph respects the source
     * chunk's own automatic/delay placement (standing in for the build's actual click-step
     * position); the rest always open fresh steps.
     */
    private fun appendParagraphBuild(
        steps: MutableList<MutableList<EffectInterval>>,
        build: Build,
        paragraphCount: Int,
        automatic: Boolean,
        delayMs: Long,
    ) {
        for (paragraphIndex in 0 until paragraphCount) {
            val interval = EffectInterval(
                layerId = paragraphLayerIdFor(build.drawableId, paragraphIndex),
                effect = mapEffect(build),
                beginMs = 0,
                durMs = build.durationMs
            )
            if (paragraphIndex == 0 && automatic && steps.isNotEmpty()) {
                appendToCurrentStep(steps, interval, delayMs)
            } else {
                steps.add(mutableListOf(interval))
            }
        }
    }

    /** Adds [interval] to the open step, [delayMs] after everything already in it has finished. */
    private fun appendToCurrentStep(
        steps: MutableList<MutableList<EffectInterval>>,
        interval: EffectInterval,
        delayMs: Long,
    ) {
        val current = steps.last()
        val begin = (current.maxOfOrNull { it.beginMs + it.durMs } ?: 0L) + delayMs
        current.add(interval.copy(beginMs = begin))
    }

    /** The effect families an `apple:build-effect:…` name can belong to, in the order they are tried. */
    private enum class EffectFamily(vararg val words: String) {
        // Movie start/pause/stop: no visual reveal (the poster is already on screen) — a
        // constant "present" state, not a fade-in, matches Appear's non-animated semantics.
        APPEAR("appear", "movie"),
        FADE("dissolve", "fade"),
        FLY("move", "fly", "drift"),
        WIPE("wipe", "reveal"),
        ZOOM("pop", "scale", "zoom", "compress"),
        SPIN("spin", "twirl", "rotate", "pivot"),
        PULSE("pulse", "blink", "flash"),
        SHIMMER("typewriter", "shimmer", "sparkle"),
        // "drop" implies falling in from above — the motion is inherent to the effect name, not
        // the parsed direction field.
        DROP("drop"),
    }

    /**
     * `apple:build-effect:…` name → nearest effect primitive. Unknown names degrade to Fade —
     * the engine-wide rule. Direction uses Keynote's uint constants; the mapping below is
     * provisional (validated per-deck via DumpKeynote).
     */
    private fun mapEffect(build: Build): EffectSpec {
        val name = build.effect.substringAfterLast(':').lowercase()
        val role = build.role
        val family = if (name.isEmpty() || name == "none") {
            EffectFamily.APPEAR
        } else {
            EffectFamily.entries.firstOrNull { family -> family.words.any { name.contains(it) } }
        }
        return when (family) {
            EffectFamily.APPEAR -> EffectSpec.Appear(role)
            EffectFamily.FLY -> EffectSpec.Fly(role, direction(build.direction, role))
            EffectFamily.WIPE -> EffectSpec.Wipe(role, direction(build.direction, role))
            EffectFamily.ZOOM -> EffectSpec.Zoom(role, if (role == EffectSpec.Role.EXIT) 1.0 else 0.0)
            EffectFamily.SPIN -> EffectSpec.Spin(role)
            EffectFamily.PULSE -> EffectSpec.Pulse(role)
            EffectFamily.DROP -> EffectSpec.Fly(role, Direction.DOWN)
            EffectFamily.FADE, EffectFamily.SHIMMER, null -> EffectSpec.Fade(role)
        }
    }

    fun mapTransition(slide: IwaMessage): SlideTransitionSpec? {
        val anim = slide.message(F.SLIDE_TRANSITION)
            ?.message(F.TRANSITION_ATTRIBUTES)
            ?.message(F.TRANSITION_ATTRS_ANIMATION)
            ?: return null
        val effect = anim.string(F.ANIM_ATTRS_EFFECT)?.substringAfterLast(':')?.lowercase() ?: return null
        if (effect.isEmpty() || effect == "none") return null
        val durationMs = ((anim.double(F.ANIM_ATTRS_DURATION) ?: 0.5) * 1000).toLong().coerceAtLeast(1)
        val dir = direction(anim.varint(F.ANIM_ATTRS_DIRECTION), EffectSpec.Role.ENTRANCE)
        val type = when {
            effect.contains("dissolve") || effect.contains("fade") -> TransitionType.FADE
            effect.contains("push") -> TransitionType.PUSH
            effect.contains("wipe") || effect.contains("reveal") -> TransitionType.WIPE
            effect.contains("move") -> TransitionType.COVER
            // Swoosh/swing/twist/reflection all have real Keynote motion where content is
            // displaced onto the frame from a direction — COVER (incoming translates in over a
            // static outgoing) preserves that displacement character far better than a flat
            // cross-fade, even though it isn't the literal 3D effect.
            effect.contains("swoosh") || effect.contains("swing") ||
                effect.contains("twist") || effect.contains("reflection") -> TransitionType.COVER
            // magic move, cube, scale, confetti, flip, doorway, … — no motion equivalent, fade
            // preserves timing and content.
            else -> TransitionType.FADE
        }
        return SlideTransitionSpec(type, durationMs, dir)
    }
}

/** "By Paragraph"/"By Bullet" deliveries fan out into per-paragraph click steps; "By Word"/
 *  "By Character" deliveries are a known, separate gap — not attempted here, degrade to the
 *  existing whole-object build. */
private fun isParagraphDelivery(delivery: String?): Boolean =
    delivery != null && (
        delivery.contains("paragraph", ignoreCase = true) ||
            delivery.contains("bullet", ignoreCase = true)
        )

/**
 * Keynote direction constants (provisional): 1=right-to-left, 2=left-to-right,
 * 3=bottom-to-top, 4=top-to-bottom. The value names the incoming movement.
 */
private fun direction(value: Long?, role: EffectSpec.Role): Direction = when (value?.toInt()) {
    KN_DIRECTION_RIGHT_TO_LEFT -> Direction.LEFT
    KN_DIRECTION_LEFT_TO_RIGHT -> Direction.RIGHT
    KN_DIRECTION_BOTTOM_TO_TOP -> Direction.UP
    KN_DIRECTION_TOP_TO_BOTTOM -> Direction.DOWN
    else -> if (role == EffectSpec.Role.EXIT) Direction.DOWN else Direction.UP
}

private const val KN_DIRECTION_RIGHT_TO_LEFT = 1
private const val KN_DIRECTION_LEFT_TO_RIGHT = 2
private const val KN_DIRECTION_BOTTOM_TO_TOP = 3
private const val KN_DIRECTION_TOP_TO_BOTTOM = 4
