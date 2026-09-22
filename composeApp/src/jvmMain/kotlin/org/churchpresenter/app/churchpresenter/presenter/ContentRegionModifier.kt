package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.SongNumberOffset
import org.churchpresenter.settings.utils.Constants
import kotlin.math.roundToInt

private const val FULL_WIDTH_PERCENT = 100
private const val FULL_PERCENT = 100f

/**
 * Narrows the presenter it wraps to [region]'s width, centered, then shifts that centered box by
 * its X/Y offsets -- letting an operator free up screen space beside the lyrics or verse text for a
 * video mixer, without either presenter's own margins, alignment or auto-fit sizing needing to know
 * about it: they read whatever box `BoxWithConstraints` inside `SongPresenter`/`BiblePresenter`
 * reports, and this is what narrows that box before they ever see it.
 *
 * [ContentRegion.xOffsetPercent] is a percentage of the *slack* narrowing the width freed up (not
 * of the whole output), so -100/100 always reaches the left/right edge exactly regardless of
 * [ContentRegion.widthPercent]. [ContentRegion.yOffsetPercent] is the same idea against the box's
 * own full height, since nothing narrows that axis. Both are mathematically bounded by their own
 * 100% reference, so the block can never be pushed off screen by any configured value.
 *
 * A no-op at the default [ContentRegion] -- full width, no offset -- so a presenter with nothing
 * configured pays for no extra layout pass.
 */
internal fun Modifier.contentRegion(region: ContentRegion): Modifier {
    if (region.xOffsetPercent == 0 && region.yOffsetPercent == 0 && region.widthPercent >= FULL_WIDTH_PERCENT) {
        return this
    }
    return this.then(
        Modifier.layout { measurable, constraints ->
            val widthFraction = region.widthPercent.coerceIn(1, FULL_WIDTH_PERCENT) / FULL_WIDTH_PERCENT.toFloat()
            val regionWidth = (constraints.maxWidth * widthFraction).roundToInt().coerceAtLeast(1)
            val childConstraints = constraints.copy(minWidth = 0, maxWidth = regionWidth)
            val placeable = measurable.measure(childConstraints)

            val centeredX = (constraints.maxWidth - regionWidth) / 2
            val maxY = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
            val xOffset = centeredX + (centeredX * (region.xOffsetPercent / FULL_PERCENT)).roundToInt()
            val yOffset = (maxY * (region.yOffsetPercent / FULL_PERCENT)).roundToInt().coerceIn(0, maxY)

            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(xOffset.coerceIn(0, constraints.maxWidth - placeable.width), yOffset)
            }
        },
    )
}

/**
 * Pins the song number to [corner], then walks it inward by [offset] -- the replacement for a
 * plain `.align(corner).offset(...)` pair, which had no sense of how far "inward" could safely go.
 *
 * [offset] is an unsigned 0-100: a percentage of the *entire* room between the corner and the far
 * side of the box the number sits in, same scale [Modifier.contentRegion] uses for its own offsets.
 * 0 is flush against the corner (today's default); 100 is flush against the opposite edge -- the
 * full width or height away. Which edge counts as "opposite," on both axes, is decided by [corner]
 * alone, so there is no sign to apply in the wrong direction: raising either number always walks
 * the number further into the frame.
 */
internal fun Modifier.songNumberCornerOffset(corner: String, offset: SongNumberOffset): Modifier {
    return this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

            val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
            val maxY = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
            val isLeft = corner == Constants.TOP_LEFT || corner == Constants.BOTTOM_LEFT
            val isTop = corner == Constants.TOP_LEFT || corner == Constants.TOP_RIGHT
            val baseX = if (isLeft) 0 else maxX
            val baseY = if (isTop) 0 else maxY

            val xTravel = (maxX * (offset.xPercent / FULL_PERCENT)).roundToInt()
            val yTravel = (maxY * (offset.yPercent / FULL_PERCENT)).roundToInt()
            val xOffset = (baseX + (if (isLeft) xTravel else -xTravel)).coerceIn(0, maxX)
            val yOffset = (baseY + (if (isTop) yTravel else -yTravel)).coerceIn(0, maxY)

            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(xOffset, yOffset)
            }
        },
    )
}
