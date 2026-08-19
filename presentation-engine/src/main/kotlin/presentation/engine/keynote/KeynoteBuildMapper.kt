package presentation.engine.keynote

import presentation.engine.keynote.KnFields as F
import presentation.engine.model.Direction
import presentation.engine.model.EffectInterval
import presentation.engine.model.EffectSpec
import presentation.engine.model.SlideTransitionSpec
import presentation.engine.model.Step
import presentation.engine.model.Timeline
import presentation.engine.model.TransitionType

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

    /** "By Paragraph"/"By Bullet" deliveries fan out into per-paragraph click steps; "By Word"/
     *  "By Character" deliveries are a known, separate gap — not attempted here, degrade to the
     *  existing whole-object build. */
    private fun isParagraphDelivery(delivery: String?): Boolean =
        delivery != null && (delivery.contains("paragraph", ignoreCase = true) || delivery.contains("bullet", ignoreCase = true))

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

        val builds = mutableMapOf<Long, Build>()
        for (buildRef in slide.messages(F.SLIDE_BUILDS).mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }) {
            val build = index.message(buildRef) ?: continue
            val drawableId = build.message(F.BUILD_DRAWABLE)?.varint(F.REFERENCE_IDENTIFIER) ?: continue
            val anim = build.message(F.BUILD_ATTRIBUTES)?.message(F.BUILD_ATTRS_ANIMATION)
            val effect = anim?.string(F.ANIM_ATTRS_EFFECT) ?: ""
            val animationType = anim?.string(F.ANIM_ATTRS_TYPE)?.lowercase() ?: ""
            val role = when {
                // Movie start/pause/stop builds are Keynote "actions," not entrances — the
                // poster/video is visible on the slide from the moment it appears; the build
                // only triggers playback. animation_type still reports "In" for these, so they
                // must be special-cased ahead of that check or the layer would incorrectly stay
                // hidden until the click (validated hands-on: a movie build classified as
                // ENTRANCE left the video area blank until the first click).
                effect.contains("movie") -> EffectSpec.Role.EMPHASIS
                animationType.contains("out") -> EffectSpec.Role.EXIT
                animationType.contains("in") -> EffectSpec.Role.ENTRANCE
                animationType.contains("action") -> EffectSpec.Role.EMPHASIS
                else -> EffectSpec.Role.ENTRANCE
            }
            val durationMs = ((anim?.double(F.ANIM_ATTRS_DURATION) ?: 0.5) * 1000).toLong().coerceAtLeast(1)
            builds[buildRef] = Build(
                drawableId = drawableId,
                role = role,
                effect = effect,
                durationMs = durationMs,
                direction = anim?.varint(F.ANIM_ATTRS_DIRECTION),
                delivery = build.string(F.BUILD_DELIVERY)
            )
        }
        if (builds.isEmpty()) return null

        val steps = mutableListOf<MutableList<EffectInterval>>()
        val paragraphBuiltDrawableIds = mutableSetOf<Long>()
        val chunkRefs = slide.messages(F.SLIDE_BUILD_CHUNKS).mapNotNull { it.varint(F.REFERENCE_IDENTIFIER) }
        val chunkMessages = chunkRefs.mapNotNull { index.message(it) }
        val orderedBuilds: List<Triple<Build, Boolean, Long>> = if (chunkMessages.isNotEmpty()) {
            chunkMessages.mapNotNull { chunk ->
                val build = chunk.message(F.BUILD_CHUNK_BUILD)?.varint(F.REFERENCE_IDENTIFIER)
                    ?.let { builds[it] } ?: return@mapNotNull null
                val automatic = chunk.bool(F.BUILD_CHUNK_AUTOMATIC) == true
                val delayMs = ((chunk.double(F.BUILD_CHUNK_DELAY) ?: 0.0) * 1000).toLong()
                Triple(build, automatic, delayMs)
            }
        } else {
            // No chunk list (older documents): every build is its own click step.
            builds.values.map { Triple(it, false, 0L) }
        }
        if (orderedBuilds.isEmpty()) return null

        for ((build, automatic, delayMs) in orderedBuilds) {
            val paragraphCount = paragraphCountOf[build.drawableId]
            if (isParagraphDelivery(build.delivery) && paragraphCount != null && paragraphCount > 1) {
                paragraphBuiltDrawableIds.add(build.drawableId)
                // Each paragraph/bullet gets its own click step, matching Keynote's real on-stage
                // behavior (each bullet needs its own click/keypress) — only the first paragraph
                // respects the source chunk's own automatic/delay placement (standing in for the
                // build's actual click-step position); the rest always open fresh steps.
                for (paragraphIndex in 0 until paragraphCount) {
                    val interval = EffectInterval(
                        layerId = paragraphLayerIdFor(build.drawableId, paragraphIndex),
                        effect = mapEffect(build),
                        beginMs = 0,
                        durMs = build.durationMs
                    )
                    if (paragraphIndex == 0 && automatic && steps.isNotEmpty()) {
                        val current = steps.last()
                        val begin = (current.maxOfOrNull { it.beginMs + it.durMs } ?: 0L) + delayMs
                        current.add(interval.copy(beginMs = begin))
                    } else {
                        steps.add(mutableListOf(interval))
                    }
                }
            } else {
                val interval = EffectInterval(
                    layerId = layerIdFor(build.drawableId),
                    effect = mapEffect(build),
                    beginMs = 0,
                    durMs = build.durationMs
                )
                if (automatic && steps.isNotEmpty()) {
                    val current = steps.last()
                    val begin = (current.maxOfOrNull { it.beginMs + it.durMs } ?: 0L) + delayMs
                    current.add(interval.copy(beginMs = begin))
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

    /**
     * `apple:build-effect:…` name → nearest effect primitive. Unknown names degrade to Fade —
     * the engine-wide rule. Direction uses Keynote's uint constants; the mapping below is
     * provisional (validated per-deck via DumpKeynote).
     */
    private fun mapEffect(build: Build): EffectSpec {
        val name = build.effect.substringAfterLast(':').lowercase()
        val role = build.role
        return when {
            // Movie start/pause/stop: no visual reveal (the poster is already on screen) — a
            // constant "present" state, not a fade-in, matches Appear's non-animated semantics.
            name.isEmpty() || name == "none" || name.contains("appear") || name.contains("movie") ->
                EffectSpec.Appear(role)
            name.contains("dissolve") || name.contains("fade") -> EffectSpec.Fade(role)
            name.contains("move") || name.contains("fly") || name.contains("drift") ->
                EffectSpec.Fly(role, direction(build.direction, role))
            name.contains("wipe") || name.contains("reveal") -> EffectSpec.Wipe(role, direction(build.direction, role))
            name.contains("pop") || name.contains("scale") || name.contains("zoom") || name.contains("compress") ->
                EffectSpec.Zoom(role, if (role == EffectSpec.Role.EXIT) 1.0 else 0.0)
            name.contains("spin") || name.contains("twirl") || name.contains("rotate") || name.contains("pivot") ->
                EffectSpec.Spin(role)
            name.contains("pulse") || name.contains("blink") || name.contains("flash") -> EffectSpec.Pulse(role)
            name.contains("typewriter") || name.contains("shimmer") || name.contains("sparkle") ->
                EffectSpec.Fade(role)
            // "drop" implies falling in from above — the motion is inherent to the effect name,
            // not the parsed direction field.
            name.contains("drop") -> EffectSpec.Fly(role, Direction.DOWN)
            else -> EffectSpec.Fade(role)
        }
    }

    /**
     * Keynote direction constants (provisional): 1=right-to-left, 2=left-to-right,
     * 3=bottom-to-top, 4=top-to-bottom. The value names the incoming movement.
     */
    private fun direction(value: Long?, role: EffectSpec.Role): Direction {
        val moving = when (value?.toInt()) {
            1 -> Direction.LEFT
            2 -> Direction.RIGHT
            3 -> Direction.UP
            4 -> Direction.DOWN
            else -> if (role == EffectSpec.Role.EXIT) Direction.DOWN else Direction.UP
        }
        return moving
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
