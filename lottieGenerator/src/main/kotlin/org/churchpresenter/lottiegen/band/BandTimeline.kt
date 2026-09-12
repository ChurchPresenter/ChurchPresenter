package org.churchpresenter.lottiegen.band

import org.churchpresenter.lottiegen.lottie.LottieBuilder
import kotlin.math.roundToInt

/**
 * The five segments of a band template, in frames: the band arrives, the text arrives, both hold,
 * the text leaves, the band leaves. The player pins the hold and replays the two text segments on
 * every verse change, so the hold's length only matters to the generator's own preview.
 *
 * Written into the file as named markers so the player finds the segments without knowing how
 * the file was made.
 */
data class BandTimeline(
    val frameRate: Int = FRAME_RATE,
    val bgInFrames: Int,
    val textInFrames: Int,
    val holdFrames: Int,
    val textOutFrames: Int,
    val bgOutFrames: Int,
) {
    val textStart: Int get() = bgInFrames
    val holdStart: Int get() = bgInFrames + textInFrames
    val textOutStart: Int get() = holdStart + holdFrames
    val bgOutStart: Int get() = textOutStart + textOutFrames
    val totalFrames: Int get() = bgOutStart + bgOutFrames
    val totalSeconds: Float get() = totalFrames.toFloat() / frameRate

    /** The band's own hold spans everything between its two segments. */
    val bandHoldFrames: Int get() = textInFrames + holdFrames + textOutFrames

    fun emitMarkers(builder: LottieBuilder) {
        builder.addMarker(MARKER_BG_IN, 0, bgInFrames)
        builder.addMarker(MARKER_TEXT_IN, textStart, textInFrames)
        builder.addMarker(MARKER_HOLD, holdStart, holdFrames)
        builder.addMarker(MARKER_TEXT_OUT, textOutStart, textOutFrames)
        builder.addMarker(MARKER_BG_OUT, bgOutStart, bgOutFrames)
    }

    companion object {
        const val FRAME_RATE = 60
        const val MARKER_BG_IN = "bg_in"
        const val MARKER_TEXT_IN = "text_in"
        const val MARKER_HOLD = "hold"
        const val MARKER_TEXT_OUT = "text_out"
        const val MARKER_BG_OUT = "bg_out"

        /** Every segment is at least one frame long, so no keyframe pair ever shares a time. */
        fun from(cfg: BibleLottieGenConfig): BandTimeline = BandTimeline(
            bgInFrames = frames(cfg.bgInSeconds),
            textInFrames = frames(cfg.textInSeconds),
            holdFrames = frames(cfg.holdSeconds),
            textOutFrames = frames(cfg.textOutSeconds),
            bgOutFrames = frames(cfg.bgOutSeconds),
        )

        private fun frames(seconds: Float): Int = (seconds * FRAME_RATE).roundToInt().coerceAtLeast(1)
    }
}
