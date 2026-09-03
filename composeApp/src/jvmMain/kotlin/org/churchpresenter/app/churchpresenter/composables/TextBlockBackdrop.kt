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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import org.churchpresenter.core.models.text.TextBackdrop

/**
 * A backdrop for text drawn as several `Text`s that are one block to the eye — song lyrics, which
 * are laid out a line at a time so each can carry its own alignment, chords and look-ahead styling.
 *
 * The bands are per line either way. A border *on its own* is not: a box drawn per `Text` would put
 * one around every lyric line rather than one around the verse, so it is painted by the container,
 * around the union of what its lines actually drew, while each line reports where it ended up.
 *
 * With a fill under it the border stops being a box of its own and becomes the edge of the plate —
 * one shape per line, drawn by [drawTextBackdrop] like any other text. There is no union to take
 * then, and no block box: a square box around the verse with the fill hugging each line inside it
 * is the look this exists to avoid.
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
        // A fill on its own is per line, and each line paints its own. Anything with a border in it
        // is one shape for the block, which only the container can place -- so it takes the union
        // of where the lines landed and paints that instead.
        if (backdrop.lineBackground && !backdrop.border) {
            for ((key, layout) in lineLayouts) {
                val offset = offsetOf(key) ?: continue
                translate(offset.x, offset.y) { drawTextBackdrop(layout, backdrop) }
            }
            return
        }
        var union: Rect? = null
        for ((key, layout) in lineLayouts) {
            val bounds = offsetOf(key)?.let { layout.drawnTextBounds()?.translate(it) }
            if (bounds != null) union = union?.expandToInclude(bounds) ?: bounds
        }
        union?.let { drawBlockBacking(it, backdrop) }
    }
}

/** Remembers a block backdrop for [backdrop], rebuilt when the settings behind it change. */
@Composable
fun rememberTextBlockBackdrop(backdrop: TextBackdrop): TextBlockBackdrop =
    remember(backdrop) { TextBlockBackdrop(backdrop) }

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
