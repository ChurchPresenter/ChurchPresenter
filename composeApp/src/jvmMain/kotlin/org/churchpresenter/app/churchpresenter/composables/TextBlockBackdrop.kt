package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.text.TextBackdrop

/**
 * A backdrop for text drawn as several `Text`s that are one block to the eye — song lyrics, which
 * are laid out a line at a time so each can carry its own alignment, chords and look-ahead styling.
 *
 * The bands are per line either way, but the border is not: a box drawn per `Text` would put one
 * around every lyric line rather than one around the verse. So the box is painted by the container,
 * around the union of what its lines actually drew, while each line reports where it ended up.
 *
 * A line reports twice, because neither answer alone locates it: [onTextLayout] gives the text's
 * shape within its own `Text` (a centred line does not start at that composable's left edge), and
 * the modifier from [lineModifier] gives where that `Text` sits inside the container.
 */
@Stable
class TextBlockBackdrop internal constructor(internal val backdrop: TextBackdrop) {
    private var container by mutableStateOf<LayoutCoordinates?>(null)
    private val lineCoordinates = mutableStateMapOf<Any, LayoutCoordinates>()
    private val lineLayouts = mutableStateMapOf<Any, TextLayoutResult>()

    /** Put on the container that should carry the border. */
    val containerModifier: Modifier = if (backdrop.isEmpty) {
        Modifier
    } else {
        Modifier
            .onGloballyPositioned { container = it }
            .drawBehind { drawBlockBackdrop() }
    }

    /** Put on each `Text` of the block, with a [key] stable for that line. */
    fun lineModifier(key: Any): Modifier = if (backdrop.isEmpty) {
        Modifier
    } else {
        Modifier.onGloballyPositioned { lineCoordinates[key] = it }
    }

    /** Pass each `Text`'s own layout result, under the same [key]. */
    fun onTextLayout(key: Any, result: TextLayoutResult) {
        if (!backdrop.isEmpty) lineLayouts[key] = result
    }

    /** Where [key]'s text sits in the container, or null while either half is still missing. */
    private fun offsetOf(key: Any): Offset? {
        val box = container?.takeIf { it.isAttached } ?: return null
        val line = lineCoordinates[key]?.takeIf { it.isAttached } ?: return null
        return box.localBoundingBoxOf(line, clipBounds = false).topLeft
    }

    private fun DrawScope.drawBlockBackdrop() {
        var union: Rect? = null
        for ((key, layout) in lineLayouts) {
            val offset = offsetOf(key)
            val bounds = offset?.let { layout.drawnTextBounds()?.translate(it) }
            if (offset != null && backdrop.lineBackground) {
                translate(offset.x, offset.y) { drawTextBackdrop(layout, backdrop.bandsOnly()) }
            }
            if (bounds != null) union = union?.expandToInclude(bounds) ?: bounds
        }
        if (backdrop.border) union?.let { drawBorderAround(it, backdrop) }
    }
}

/** Remembers a block backdrop for [backdrop], rebuilt when the settings behind it change. */
@Composable
fun rememberTextBlockBackdrop(backdrop: TextBackdrop): TextBlockBackdrop =
    remember(backdrop) { TextBlockBackdrop(backdrop) }

/** The same record with the border switched off — the container draws that part itself. */
private fun TextBackdrop.bandsOnly(): TextBackdrop = if (border) copy(border = false) else this

/** The rectangle a laid-out text actually covers, or null when it drew nothing. */
internal fun TextLayoutResult.drawnTextBounds(): Rect? {
    var left = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (line in 0 until lineCount) {
        val lineLeft = getLineLeft(line)
        val lineRight = getLineRight(line)
        if (lineRight - lineLeft <= 0f) continue
        left = minOf(left, lineLeft)
        right = maxOf(right, lineRight)
        top = minOf(top, getLineTop(line))
        bottom = maxOf(bottom, getLineBottom(line))
    }
    return if (right <= left || bottom <= top) null else Rect(left, top, right, bottom)
}

private fun Rect.expandToInclude(other: Rect) = Rect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)

/** The box around [bounds], padded and stroked as [backdrop] asks. */
internal fun DrawScope.drawBorderAround(bounds: Rect, backdrop: TextBackdrop) {
    val width = backdrop.borderWidth.sp.toPx()
    if (width <= 0f) return
    // The stroke straddles the path, so half of it would eat into the padding just opened.
    val outset = backdrop.borderPadding.sp.toPx() + width / 2f
    // Kept inside what is being drawn on. The box sits outside the text, and text that reaches the
    // edge of its output -- a verse filling the frame, a reference in the bottom corner -- would
    // otherwise have the box drawn past the edge and clipped to three sides. Pulling it in keeps it
    // a box; the operator moves the text with the margin settings if they want the gap back.
    val edge = width / 2f
    val left = maxOf(bounds.left - outset, edge)
    val top = maxOf(bounds.top - outset, edge)
    val right = minOf(bounds.right + outset, size.width - edge)
    val bottom = minOf(bounds.bottom + outset, size.height - edge)
    if (right <= left || bottom <= top) return
    val radius = backdrop.borderRadius.sp.toPx()
    drawRoundRect(
        color = parseHexColor(backdrop.borderColor)
            .copy(alpha = (backdrop.borderOpacity / 100f).coerceIn(0f, 1f)),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = width),
    )
}
