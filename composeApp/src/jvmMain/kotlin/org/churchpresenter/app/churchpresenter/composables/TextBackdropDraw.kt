package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.text.TextBackdrop

/**
 * Draws [backdrop] behind text already laid out as [layout].
 *
 * Everything here is painted from the *measured* text rather than around it: the bands follow each
 * line's own box, and the border follows the block those lines make up. Nothing is a layout of its
 * own, so an element gains a band or a box without its text moving by a pixel — which matters
 * because the presenters fit their type to the space they are given, and a wrapper that took up
 * room would change the size the fit arrives at.
 *
 * The measurements are read as `sp`, so they scale with the type exactly as the font size does.
 */
internal fun DrawScope.drawTextBackdrop(layout: TextLayoutResult, backdrop: TextBackdrop) {
    if (backdrop.isEmpty || layout.lineCount == 0) return
    if (backdrop.lineBackground) drawLineBands(layout, backdrop)
    if (backdrop.border) layout.drawnTextBounds()?.let { drawBorderAround(it, backdrop) }
}

private fun DrawScope.drawLineBands(layout: TextLayoutResult, backdrop: TextBackdrop) {
    val grow = backdrop.lineBackgroundHeight.sp.toPx()
    val shift = backdrop.lineBackgroundOffset.sp.toPx()
    val fill = backdrop.lineBackgroundColor.toBackdropColor(backdrop.lineBackgroundOpacity)
    for (line in 0 until layout.lineCount) {
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
        val top = layout.getLineTop(line) - grow + shift
        val bottom = layout.getLineBottom(line) + grow + shift
        // A blank line measures zero wide, and a height offset can be negative enough to invert the
        // band. Painting either would leave a mark floating between two verses with no text on it.
        if (right - left > 0f && bottom > top) {
            drawRect(color = fill, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
        }
    }
}

/** A stored `#RRGGBB` at a stored 0-100 opacity. */
private fun String.toBackdropColor(opacity: Int): Color =
    parseHexColor(this).copy(alpha = (opacity / 100f).coerceIn(0f, 1f))
