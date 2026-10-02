package org.churchpresenter.sharedui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.utils.MIN_AUTO_FIT_FONT_SIZE
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOptions
import org.churchpresenter.settings.TextBoxOverflow
import org.churchpresenter.settings.utils.Constants

/** The largest size "Fill the box" grows text to, in the same reference points as every font size. */
private const val FILL_MAX_FONT_SIZE = 400

/**
 * The rectangle a page's boxes are measured against, in whatever unit the caller works in: the whole
 * output, or the part of it inside the margins, and on a lower third the band or the output.
 *
 * [outputWidth] × [outputHeight] is the whole output. [band] is the lower third's band as a
 * rectangle of it, null on a full screen. The margins are in the same unit as the output.
 */
fun textBoxArea(
    outputWidth: Float,
    outputHeight: Float,
    options: TextBoxOptions,
    margins: BoxMargins,
    band: Rect? = null,
): Rect {
    val base = if (band != null && !options.lowerThirdWholeScreen) band else Rect(0f, 0f, outputWidth, outputHeight)
    if (!options.insideMargins) return base
    val inside = Rect(
        left = base.left + margins.left,
        top = base.top + margins.top,
        right = base.right - margins.right,
        bottom = base.bottom - margins.bottom,
    )
    return if (inside.width > 0f && inside.height > 0f) inside else base
}

/** A page's four margins, in the unit of the area they are taken from. */
data class BoxMargins(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f)

/** Where [this] box lies in [area], in [area]'s own unit. */
fun TextBox.rectIn(area: Rect): Rect {
    val full = TextBox.FULL_PERCENT
    return Rect(
        left = area.left + area.width * xPercent / full,
        top = area.top + area.height * yPercent / full,
        right = area.left + area.width * rightPercent / full,
        bottom = area.top + area.height * bottomPercent / full,
    )
}

/** The boxes among [others] that [rect] overlaps, by more than a hairline. */
fun Rect.overlapsOf(others: List<Rect>): List<Rect> =
    others.filter { other -> other != this && intersect(other).let { it.width > 0f && it.height > 0f } }

/**
 * [this] made to stop short of every one of [others] it overlaps -- the Keep clear rule.
 *
 * Each overlap is cut away from whichever side of [this] loses the least, so a reference box
 * sitting across the top of a verse box takes the top off the verse box rather than its middle.
 * What is left can only shrink; an overlap that would leave nothing is ignored rather than
 * removing the box altogether.
 */
fun Rect.clearOf(others: List<Rect>): Rect = overlapsOf(others).fold(this) { room, other ->
    val cuts = listOf(
        // What is left if the overlap is cut from each side in turn.
        Rect(room.left, maxOf(room.top, other.bottom), room.right, room.bottom),
        Rect(room.left, room.top, room.right, minOf(room.bottom, other.top)),
        Rect(maxOf(room.left, other.right), room.top, room.right, room.bottom),
        Rect(room.left, room.top, minOf(room.right, other.left), room.bottom),
    ).filter { it.width > 0f && it.height > 0f }
    cuts.maxByOrNull { it.width * it.height } ?: room
}

/**
 * What one item says and how, for fitting it in its box: [configuredSize] is the size it is set to,
 * and [softWrap] whether it may wrap onto more lines to fit the width, as the item itself would.
 */
data class BoxFitText(
    val text: AnnotatedString,
    val style: TextStyle,
    val configuredSize: Int,
    val softWrap: Boolean = true,
)

/**
 * The size an item's text is drawn at in a box of [room], in reference points (1 px per point, as every
 * presenter measures).
 *
 * Shrink to fit takes the largest size that fits, never above the configured size unless the box
 * asks to [TextBox.fill] it. Cut off and Spill over draw at the configured size whatever happens.
 */
fun fitInBox(measurer: TextMeasurer, fitText: BoxFitText, box: TextBox, room: IntSize): Int {
    val shrinks = box.overflow == TextBoxOverflow.SHRINK && fitText.text.isNotEmpty()
    if (!shrinks || room.width <= 0 || room.height <= 0) return fitText.configuredSize
    val ceiling = if (box.fill) FILL_MAX_FONT_SIZE else fitText.configuredSize
    val density = Density(1f)
    fun fits(size: Int): Boolean {
        val constraints = if (fitText.softWrap) Constraints(maxWidth = room.width) else Constraints()
        val measured = measurer.measure(
            text = fitText.text,
            style = fitText.style.copy(fontSize = size.sp),
            constraints = constraints,
            density = density,
            softWrap = fitText.softWrap,
        ).size
        return measured.height <= room.height && measured.width <= room.width
    }
    if (fits(ceiling)) return ceiling
    var low = MIN_AUTO_FIT_FONT_SIZE
    var high = ceiling
    while (high - low > 1) {
        val mid = (low + high) / 2
        if (fits(mid)) low = mid else high = mid
    }
    return low
}

/** The vertical bias a box's [TextBox.vertical] places its text at. */
internal fun TextBox.verticalBias(): Float = when (vertical) {
    Constants.TOP -> -1f
    Constants.BOTTOM -> 1f
    else -> 0f
}

/** The horizontal bias an item's own alignment -- Left, Center, Right -- places it at in its box. */
internal fun horizontalBias(alignment: String): Float = when (alignment) {
    Constants.LEFT -> -1f
    Constants.RIGHT -> 1f
    else -> 0f
}

/**
 * One item drawn in its box: placed at [rect] (in dp, inside a parent that fills the output from its
 * top-left corner), aligned in it by the box's vertical setting and the item's own [horizontal]
 * alignment, cut off at the box's edge when the box says so, and reported to the preview as [key].
 *
 * Spill over lets [content] be taller than the box: it is laid out at its own height and runs past
 * the edge the vertical setting points away from.
 */
@Composable
fun BoxedItem(
    rect: Rect,
    box: TextBox,
    horizontal: String,
    key: String,
    content: @Composable BoxScope.() -> Unit,
) {
    val cut = box.overflow == TextBoxOverflow.CUT
    // Filling the parent first, so the offset is measured from its top-left corner whatever the
    // parent aligns its children to -- a centred or bottom-aligned presenter would otherwise add its
    // own alignment to the box's place.
    Box(Modifier.fillMaxSize()) { Box(
        modifier = Modifier
            .absoluteOffset(rect.left.dp, rect.top.dp)
            .size(rect.width.dp, rect.height.dp)
            .then(if (cut) Modifier.clipToBounds() else Modifier)
            .reportsBlock(PresentedBlock(PresentedBlock.Kind.BOX, key)),
        contentAlignment = BiasAlignment(horizontalBias(horizontal), box.verticalBias()),
    ) {
        if (box.overflow == TextBoxOverflow.SPILL) {
            Box(
                Modifier.wrapContentHeight(align = verticalAlignmentOf(box), unbounded = true),
                content = content,
            )
        } else {
            content()
        }
    } }
}

private fun verticalAlignmentOf(box: TextBox): Alignment.Vertical = when (box.vertical) {
    Constants.TOP -> Alignment.Top
    Constants.BOTTOM -> Alignment.Bottom
    else -> Alignment.CenterVertically
}
