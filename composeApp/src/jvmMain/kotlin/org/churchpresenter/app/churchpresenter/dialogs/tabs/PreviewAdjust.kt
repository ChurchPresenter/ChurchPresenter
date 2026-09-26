package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.utils.Constants

/**
 * What the Adjust handles over the preview change, for the page being edited: its margins, where
 * its block sits, how wide it may be, the size of the text the rows are pointed at, and -- on a
 * lower third -- the band's height.
 *
 * Every write is one call, carrying all it changes: the page's writes are each computed from the
 * document as it was drawn, so two in a row would have the second undo the first.
 */
internal class AdjustModel(
    val margins: Adjustable<Margins>,
    val verticalAlignment: String,
    /** Snaps the block to a vertical alignment, taking its own vertical offset back to none. */
    val onSnap: (String) -> Unit,
    /** The narrower region the block can be confined to -- null on a lower third, whose band is one. */
    val region: Adjustable<ContentRegion>?,
    val textSize: Adjustable<Int>,
    /** The band's height in percent of the screen, on a lower third; null on a full screen. */
    val band: Adjustable<Int>?,
)

/** One value a handle changes, and how to write it. */
internal class Adjustable<T>(val value: T, val onChange: (T) -> Unit)

/** The three places a block snaps to, as fractions down its region, and the alignment each stands for. */
internal val SNAP_GUIDES = listOf(
    SNAP_TOP to Constants.TOP,
    SNAP_MIDDLE to Constants.MIDDLE,
    SNAP_BOTTOM to Constants.BOTTOM,
)
private const val SNAP_TOP = 0.12f
private const val SNAP_MIDDLE = 0.5f
private const val SNAP_BOTTOM = 0.88f

/** How close to a guide, as a fraction of the region's height, a release has to be to snap to it. */
internal const val SNAP_DISTANCE = 0.10f

/** The alignment a block released at [fraction] of its region's height snaps to, or null for none. */
internal fun snapFor(fraction: Float): String? =
    SNAP_GUIDES.minByOrNull { (line, _) -> kotlin.math.abs(line - fraction) }
        ?.takeIf { (line, _) -> kotlin.math.abs(line - fraction) <= SNAP_DISTANCE }
        ?.second

/** Where a block of [alignment] rests in its region, as a fraction of its height. */
internal fun restingFraction(alignment: String): Float =
    SNAP_GUIDES.firstOrNull { it.second == alignment }?.first ?: SNAP_MIDDLE

/**
 * The content width after the side dot moved [deltaPercent] of the region: both sides move together
 * while the box is centred, so the dot follows the pointer; off centre only the side dragged does.
 */
internal fun draggedWidth(region: ContentRegion, startWidth: Int, deltaPercent: Float, rightSide: Boolean): Int {
    val signed = if (rightSide) deltaPercent else -deltaPercent
    val change = if (region.xOffsetPercent == 0) signed * 2 else signed
    return (startWidth + change).toInt().coerceIn(ContentRegion.WIDTH_RANGE)
}

/** The rectangle the margins leave, in preview dp. */
internal data class AdjustFrame(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp) {
    val width: Dp get() = (right - left).coerceAtLeast(0.dp)
    val height: Dp get() = (bottom - top).coerceAtLeast(0.dp)
}

/** The content box inside [frame]: [region]'s width, placed by its horizontal offset. */
internal fun innerBox(frame: AdjustFrame, region: ContentRegion?): AdjustFrame {
    if (region == null) return frame
    val width = frame.width * (region.widthPercent / FULL_PERCENT)
    val left = frame.left + (frame.width - width) * ((region.xOffsetPercent + FULL_PERCENT) / (2 * FULL_PERCENT))
    return AdjustFrame(left, frame.top, left + width, frame.bottom)
}
