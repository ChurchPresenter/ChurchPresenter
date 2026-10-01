package org.churchpresenter.lottiegen.render

/**
 * [progress] as every Lottie painter should be handed it: never exactly the first frame.
 *
 * Compottie 2.3.2 draws no text at frame 0 for a text layer whose document has a single keyframe --
 * the shape of all static text, and of every file this generator writes. Looking up the value at or
 * before the first keyframe, it wants a second keyframe to pair with the first, finds none, and
 * falls back to an empty document (alexzhirkevich/compottie#90). Any later frame takes the last
 * keyframe's value and draws normally, so a preview parked on frame 0 showed its band with no title,
 * and so did the first frame of every playback and pre-rendered clip.
 *
 * Nudged by [FIRST_FRAME_NUDGE] of the animation: about a hundredth of a frame for a three-second
 * lower third, nothing anyone can see, and past the frame the lookup gets wrong. Remove this once a
 * Compottie release carries the fix.
 */
fun lottieDrawProgress(progress: Float): Float = progress.coerceAtLeast(FIRST_FRAME_NUDGE)

/** How far past the first frame [lottieDrawProgress] keeps a painter, as a fraction of the animation. */
const val FIRST_FRAME_NUDGE = 0.0001f
