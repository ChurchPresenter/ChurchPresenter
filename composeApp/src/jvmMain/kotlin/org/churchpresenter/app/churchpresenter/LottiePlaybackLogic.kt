package org.churchpresenter.app.churchpresenter

import kotlin.math.roundToInt

private const val MILLIS_PER_SECOND_F = 1000f
private const val MILLIS_PER_SECOND_L = 1000L

/** How long a Lottie composition runs, from its own frame count and rate. Never zero. */
internal fun lottieCompositionDurationMs(durationFrames: Float, frameRate: Float): Long =
    ((durationFrames / frameRate) * MILLIS_PER_SECOND_F).toLong().coerceAtLeast(1L)

/** How long pre-rendered frames run, from how many there are and the rate they were rendered at. */
internal fun lottiePrerenderDurationMs(frameCount: Int, fps: Int): Long =
    (frameCount * MILLIS_PER_SECOND_L / fps).coerceAtLeast(1L)

/**
 * Whether the clip holds on a frame partway through.
 *
 * The frame is stored as a fraction of the clip, so a value outside 0..1 names no frame in it and
 * is treated as no hold at all rather than clamped to the start or the end.
 */
internal fun lottieHasPause(pauseAtFrame: Boolean, pauseFrame: Float): Boolean =
    pauseAtFrame && pauseFrame in 0f..1f

/** When the hold starts, or -1 when there is none — an instant no elapsed time ever reaches. */
internal fun lottiePauseAtMs(totalDurationMs: Long, pauseFrame: Float, hasPause: Boolean): Long =
    if (hasPause) (totalDurationMs * pauseFrame).toLong() else -1L

/** The clip's whole length on the wall clock, the hold included. */
internal fun lottieGrandTotalMs(
    totalDurationMs: Long,
    hasPause: Boolean,
    pauseDurationMs: Long,
): Long = totalDurationMs + if (hasPause) pauseDurationMs else 0L

/**
 * How far through the clip [elapsedMs] is, as a fraction.
 *
 * Three stretches when the clip holds: play up to the hold, sit on it, then play what is left over
 * whatever time remains. The last stretch is re-scaled rather than resumed at the original rate,
 * because the hold has already consumed wall-clock time the clip's own timeline does not know
 * about — playing on at the old rate would run past the end.
 */
internal fun lottieProgressAt(elapsedMs: Long, totalDurationMs: Long, hold: LottieHold?): Float {
    if (hold == null) return (elapsedMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
    return when {
        elapsedMs < hold.atMs -> (elapsedMs.toFloat() / totalDurationMs).coerceIn(0f, hold.frame)
        elapsedMs < hold.atMs + hold.durationMs -> hold.frame
        else -> {
            val postElapsed = elapsedMs - hold.atMs - hold.durationMs
            val postTotalMs = (totalDurationMs - hold.atMs).coerceAtLeast(1L)
            (hold.frame + (postElapsed.toFloat() / postTotalMs) * (1f - hold.frame)).coerceIn(0f, 1f)
        }
    }
}

/** Where a clip holds: on [frame] (a fraction of the clip), from [atMs] on the wall clock, for [durationMs]. */
internal data class LottieHold(val frame: Float, val atMs: Long, val durationMs: Long)

/** Which pre-rendered frame a fraction of the way through the clip lands on. */
internal fun lottieFrameIndexFor(progress: Float, frameCount: Int): Int =
    (progress * (frameCount - 1)).roundToInt().coerceIn(0, frameCount - 1)
