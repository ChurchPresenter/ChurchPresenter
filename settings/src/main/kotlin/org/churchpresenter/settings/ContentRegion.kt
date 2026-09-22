package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * Shrinks and repositions a presenter's whole content block, as a fraction of the output.
 *
 * [widthPercent] narrows the block symmetrically around the centerline, which leaves some slack --
 * the width the narrowing freed up -- on each side. [xOffsetPercent] and [yOffsetPercent] are a
 * signed -100..100: a percentage of that slack (not of the whole output) to shift the centered
 * block by, so -100/100 always lands the block flush against the left/top or right/bottom edge
 * exactly, whatever [widthPercent] is, rather than a fixed pixel amount that reaches the edge at
 * one width and falls short or overshoots at another. All three default to the block filling the
 * screen exactly as it always has -- an operator who wants the lyrics or verse text to leave room
 * beside it for a video mixer narrows the width and leaves the rest of the presenter (margins,
 * alignment, auto-fit) working exactly as it does today, just against a smaller box.
 */
@Serializable
data class ContentRegion(
    val xOffsetPercent: Int = 0,
    val yOffsetPercent: Int = 0,
    val widthPercent: Int = 100,
) {
    companion object {
        val OFFSET_RANGE = -100..100
        val WIDTH_RANGE = 20..100
    }
}
