package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOptions
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
    /** The block's vertical alignment; writing one snaps it there, taking its vertical offset back to none. */
    val alignment: Adjustable<String>,
    /** The narrower region the block can be confined to -- null on a lower third, whose band is one. */
    val region: Adjustable<ContentRegion>?,
    val textSize: Adjustable<Int>,
    /** The band's height in percent of the screen, on a lower third; null on a full screen. */
    val band: Adjustable<Int>?,
    /** The translations or languages drawn as blocks of their own, to pick and move one; null where there are none. */
    val blocks: BlockTargets? = null,
    /** Every element moved on its own put back where the layout puts it; null where nothing moves. */
    val positions: PositionsReset? = null,
    /** The page's text boxes on this output, to pick, move and resize; null on a page without any. */
    val boxes: BoxTargets? = null,
    /**
     * A page with nothing to adjust but its boxes -- captions, subtitles, Q&A, the dictionary --
     * which has no margins, block or text size of its own to offer handles for.
     */
    val boxesOnly: Boolean = false,
) {
    /** More than one block to pick from, or a reference to drag. */
    val hasBlocks: Boolean get() = blocks != null && (blocks.keys.size > 1 || blocks.reference != null)
}

/**
 * The blocks a page's text is drawn in -- each Bible translation, or each element of a song slide --
 * as the handles address them: which is picked (null for All), how to pick one, and how far the
 * picked one is moved on its own. [reference] is the picked (or first) translation's reference, on
 * the Bible page.
 */
internal class BlockTargets(
    val kind: PresentedBlock.Kind,
    /** Each block's key, in the order the "Applies to" strip lists them. */
    val keys: List<String>,
    val selected: Int?,
    val onSelect: (Int) -> Unit,
    /** The picked block's own move, x to y in output pixels; null under All. */
    val shift: Adjustable<Pair<Int, Int>>?,
    /** The reference as a block of its own; null on a page with none. */
    val reference: ReferenceTarget? = null,
)

/**
 * A Bible reference on the preview: dragged anywhere on its own ([shift], output pixels), and
 * clicked -- or dragged -- to point the Text rows at it. [picked] while they are.
 */
internal class ReferenceTarget(
    val shift: Adjustable<Pair<Int, Int>>,
    val picked: Boolean,
    val onPick: () -> Unit,
)

/**
 * A page's text boxes on this output: each one turned on, as [handles], and the key of the one the
 * Text rows point at -- [selected], which gets the move and resize handles. [options] say what the
 * boxes are measured against and whether a dragged box snaps.
 */
internal class BoxTargets(
    val handles: List<BoxHandle>,
    val selected: String?,
    val options: TextBoxOptions,
)

/** One text box on the preview: its key, the box as stored, how to write it, and how to point the rows at it. */
internal class BoxHandle(
    val key: String,
    val box: TextBox,
    val onChange: (TextBox) -> Unit,
    val onPick: () -> Unit,
)

/**
 * The Adjust model of a page with only [boxes] to offer: every other handle stands aside, and its
 * values are inert placeholders nothing draws.
 */
internal fun boxesOnlyAdjustModel(boxes: BoxTargets?): AdjustModel = AdjustModel(
    margins = Adjustable(Margins(0, 0, 0, 0)) {},
    alignment = Adjustable(Constants.MIDDLE) {},
    region = null,
    textSize = Adjustable(0) {},
    band = null,
    boxes = boxes,
    boxesOnly = true,
)

/** Reset positions under the preview: [moved] while anything on this output has been moved on its own. */
internal class PositionsReset(val moved: Boolean, val onReset: () -> Unit)

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
