package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.text.TextBackdrop

/**
 * Draws [backdrop] behind text already laid out as [layout].
 *
 * Everything here is painted from the *measured* text rather than around it. Nothing is a layout of
 * its own, so an element gains a backing without its text moving by a pixel — which matters because
 * the presenters fit their type to the space they are given, and a wrapper that took up room would
 * change the size the fit arrives at.
 *
 * The three states are three different shapes, not two drawings stacked:
 *
 *  * **Fill only** — a band per line, sized from that line's own text and grown by
 *    [TextBackdrop.lineBackgroundWidth]/[TextBackdrop.lineBackgroundHeight], rounded by
 *    [TextBackdrop.lineBackgroundRadius]. A highlighter.
 *  * **Border only** — one box around the whole block, [TextBackdrop.borderPadding] off the text.
 *  * **Both** — that same box, filled *and* stroked: one plate behind the paragraph.
 *
 * **Both is deliberately not "a band per line with an outline on it".** That was tried, and on a
 * wrapped verse it draws a stack of rounded rectangles whose edges overlap and whose strokes run
 * straight through the lines above and below. One shape for the block is the only version of this
 * that survives three lines of text.
 *
 * The measurements are read as `sp`, so they scale with the type exactly as the font size does.
 *
 * [scale] is for the call sites that draw the same text smaller than the output does — the tab
 * previews, which shrink the font by the ratio between the preview box and the presenter screen.
 * The measurements are multiplied by it in `Float` px, so a 3sp outline stays a hairline in a
 * preview instead of rounding away to nothing or being drawn at full output size around tiny type.
 */
internal fun DrawScope.drawTextBackdrop(
    layout: TextLayoutResult,
    backdrop: TextBackdrop,
    scale: Float = 1f,
) {
    if (backdrop.isEmpty || layout.lineCount == 0) return
    if (backdrop.lineBackground && !backdrop.border) {
        drawLineBands(layout, backdrop, scale)
    } else {
        layout.drawnTextBounds()?.let { drawBlockBacking(it, backdrop, scale) }
    }
}

private fun DrawScope.drawLineBands(layout: TextLayoutResult, backdrop: TextBackdrop, scale: Float) {
    val fill = backdrop.lineBackgroundColor.toBackdropColor(backdrop.lineBackgroundOpacity)
    val grow = backdrop.lineBackgroundHeight.sp.toPx() * scale
    val growX = backdrop.lineBackgroundWidth.sp.toPx() * scale
    val shift = backdrop.lineBackgroundOffset.sp.toPx() * scale
    val radiusPx = backdrop.lineBackgroundRadius.sp.toPx() * scale
    val radius = CornerRadius(radiusPx, radiusPx)
    // One pair of edges for every band, or null to let each line keep its own. The union of the
    // lines that have text, so it reads correctly whichever way the paragraph is aligned.
    val shared = if (backdrop.lineBackgroundUniformWidth) layout.drawnLineExtent() else null
    for (line in 0 until layout.lineCount) {
        // Measured *before* the horizontal grow, on purpose. A blank line is zero wide, and growing
        // it would paint a band twice the grow wide with no text on it — a mark floating between two
        // verses. What decides whether a line gets a band is the text on it, not the band's width —
        // which is why this reads the line's own extent even when the width is shared.
        if (layout.getLineRight(line) - layout.getLineLeft(line) <= 0f) continue
        val textLeft = shared?.first ?: layout.getLineLeft(line)
        val textRight = shared?.second ?: layout.getLineRight(line)
        val left = textLeft - growX
        val right = textRight + growX
        val top = layout.getLineTop(line) - grow + shift
        val bottom = layout.getLineBottom(line) + grow + shift
        // Either grow can be negative enough to invert the band; painting an inverted one is a
        // rectangle drawn back to front.
        if (right > left && bottom > top) {
            drawRoundRect(
                color = fill,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * The one shape that backs a whole block: [bounds] padded out, filled if there is a fill, stroked
 * if there is a border.
 *
 * Fill and border are the same rectangle by construction, so they cannot disagree about size, and
 * the fill is drawn *as* the rounded rectangle rather than clipped to one — a corner radius takes
 * the same bite out of both and no fill is left showing outside the outline. The stroke then runs
 * down the inside of that edge, so the outline's outer edge is the plate's outer edge.
 *
 * [TextBackdrop.borderPadding] opens the gap on all four sides. The fill's own height and vertical
 * offset still apply on top of it, which is how a plate is grown or slid onto a face whose glyphs
 * do not sit centred in their line box.
 */
internal fun DrawScope.drawBlockBacking(bounds: Rect, backdrop: TextBackdrop, scale: Float = 1f) {
    val stroke = if (backdrop.border) backdrop.borderWidth.sp.toPx() * scale else 0f
    val pad = backdrop.borderPadding.sp.toPx() * scale + stroke / 2f
    val growY = if (backdrop.lineBackground) backdrop.lineBackgroundHeight.sp.toPx() * scale else 0f
    val shift = if (backdrop.lineBackground) backdrop.lineBackgroundOffset.sp.toPx() * scale else 0f
    // Deliberately NOT clamped to the draw area. `size` here is the text's own box, not the
    // output's, so pinning the plate inside it eats the padding on every side that the text
    // reaches -- which is all four of them, since the box is measured from the text. That is how
    // the plate ends up flush against the glyphs with an outline sitting on the first letter.
    // Drawing outside a node's bounds is allowed; only an ancestor that clips would cut it off.
    val rect = Rect(
        left = bounds.left - pad,
        top = bounds.top - pad - growY + shift,
        right = bounds.right + pad,
        bottom = bounds.bottom + pad + growY + shift,
    )
    if (rect.width <= 0f || rect.height <= 0f) return
    val radiusPx = backdrop.borderRadius.sp.toPx() * scale
    val radius = CornerRadius(radiusPx, radiusPx)
    if (backdrop.lineBackground) {
        drawRoundRect(
            color = backdrop.lineBackgroundColor.toBackdropColor(backdrop.lineBackgroundOpacity),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = radius,
        )
    }
    if (stroke <= 0f) return
    val inner = rect.deflate(stroke / 2f)
    if (inner.width <= 0f || inner.height <= 0f) return
    drawRoundRect(
        color = backdrop.borderColor.toBackdropColor(backdrop.borderOpacity),
        topLeft = inner.topLeft,
        size = inner.size,
        cornerRadius = radius,
        style = Stroke(width = stroke),
    )
}

/**
 * The leftmost left and rightmost right across every line that has text, or null when none has.
 *
 * What "as wide as the widest line" means in practice. Taking the union rather than the widest
 * line's own pair is what makes it right under any alignment: centred text has a symmetric union, so
 * the bands come out centred too; left-aligned text shares its left edge and the bands end together.
 */
private fun TextLayoutResult.drawnLineExtent(): Pair<Float, Float>? {
    var left = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    for (line in 0 until lineCount) {
        val lineLeft = getLineLeft(line)
        val lineRight = getLineRight(line)
        if (lineRight - lineLeft <= 0f) continue
        if (lineLeft < left) left = lineLeft
        if (lineRight > right) right = lineRight
    }
    return if (right > left) left to right else null
}

/** A stored `#RRGGBB` at a stored 0-100 opacity. */
private fun String.toBackdropColor(opacity: Int): Color =
    parseHexColor(this).copy(alpha = (opacity / 100f).coerceIn(0f, 1f))
