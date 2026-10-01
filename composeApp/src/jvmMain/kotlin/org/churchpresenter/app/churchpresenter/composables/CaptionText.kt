package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline

/**
 * Shows text clipped to the last N lines. Content is bottom-aligned —
 * when text exceeds maxLines, old lines are clipped off the top.
 * The text is shifted upward so the last line sits at the bottom of the clip area.
 *
 * Shared between `STTPresenter` (live captions) and `SubtitleOverlay` (video subtitles) — one
 * rendering of "styled, backdropped, outlined caption text" rather than two copies drifting apart.
 */
@Composable
fun BottomAlignedText(
    text: AnnotatedString,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    backdrop: TextBackdrop = TextBackdrop(),
    outline: TextOutline = TextOutline(),
    // The outline's stroke follows the same reference-resolution scale as [style]'s own font size,
    // so a caller drawing at other than 1x (see `presenterScale`) keeps the two in proportion.
    // 1f (the default) is right for a caller that never scales, e.g. STTPresenter today.
    scaleFactor: Float = 1f,
    // How long the lines take to slide up when a new one pushes them, or 0 to jump as they always
    // did. Only text taller than its clip moves, so a caption still filling up never slides.
    rollUpMillis: Int = 0,
) {
    // The painter goes on the content text in both branches, never on the invisible reference
    // below: that one exists to measure a fixed number of lines, and banding it would paint a
    // block of empty lines behind the captions. The *room* does go on both -- see the reference.
    //
    // [scaleFactor] reaches the painter for the same reason it reaches the outline: the caller has
    // already scaled the font size it passes, and a backdrop's measurements are in those same
    // units, so a plate drawn at 1x against half-size text comes out twice as heavy as it was set.
    val painter = rememberTextBackdropPainter(backdrop, scaleFactor)
    if (maxLines <= 0) {
        OutlinedText(
            text = text,
            outline = outline,
            scaleFactor = scaleFactor,
            color = Color.Unspecified,
            fontSize = TextUnit.Unspecified,
            style = style,
            modifier = modifier.fillMaxWidth().backdropRoom(backdrop, scaleFactor).then(painter.modifier),
            onTextLayout = painter::onTextLayout,
        )
        return
    }

    // Reference text with exactly maxLines lines — measured to get precise pixel height
    val referenceText = remember(maxLines) { "\n".repeat(maxLines - 1).ifEmpty { " " } }
    val roll = rememberRollUp(rollUpMillis)

    Layout(
        content = {
            // Invisible reference: measures exact height of maxLines lines -- plus the room the
            // plate needs, because that height becomes the clip. Without it a full N lines of text
            // measures taller than the clip the moment a backdrop is on, and the branch below
            // reads that as overflow and shifts the first line off the top.
            Text(
                text = referenceText,
                style = style,
                modifier = Modifier.fillMaxWidth().backdropRoom(backdrop, scaleFactor),
                maxLines = maxLines
            )
            // Actual content: measured unconstrained. One measurable either way -- an outlined
            // draw is a box holding both passes, and the layout below indexes by position.
            OutlinedText(
                text = text,
                outline = outline,
                scaleFactor = scaleFactor,
                color = Color.Unspecified,
                fontSize = TextUnit.Unspecified,
                style = style,
                modifier = Modifier.fillMaxWidth().backdropRoom(backdrop, scaleFactor).then(painter.modifier),
                onTextLayout = painter::onTextLayout,
            )
        },
        modifier = modifier.clipToBounds()
    ) { measurables, constraints ->
        val unconstrainedConstraints = Constraints(
            minWidth = constraints.minWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity
        )
        // Measure reference to get exact N-line height
        val refPlaceable = measurables[0].measure(unconstrainedConstraints)
        val clipHeightPx = refPlaceable.height

        // Measure actual text at full height
        val textPlaceable = measurables[1].measure(unconstrainedConstraints)

        val reportedHeight = clipHeightPx.coerceAtMost(textPlaceable.height)
        // Bottom-aligned: shifted up so the last lines are the visible ones
        val y = (reportedHeight - textPlaceable.height).coerceAtMost(0)
        roll.moved(y)
        layout(constraints.maxWidth, reportedHeight) {
            // Read here, not above, so the slide re-places the text without measuring it again
            textPlaceable.place(0, y + roll.offset())
            // Don't place reference — it's just for measurement
        }
    }
}

/**
 * The slide behind roll-up: when the text moves up by some amount, it is drawn that much lower and
 * eased back into place over [millis]. Does nothing at 0.
 */
private class RollUp(private val millis: Int, private val scope: CoroutineScope) {
    private val slide = Animatable(0f)
    private var lastY: Int? = null

    // The slide for the frame the text moved in, before the animation has picked it up
    private var pending: Float? = null

    fun moved(y: Int) {
        val previous = lastY
        lastY = y
        if (millis <= 0 || previous == null || y >= previous) return
        val shift = (previous - y).toFloat() + offset()
        pending = shift
        scope.launch {
            slide.snapTo(shift)
            pending = null
            slide.animateTo(0f, tween(millis))
        }
    }

    fun offset(): Int = (pending ?: slide.value).roundToInt()
}

@Composable
private fun rememberRollUp(millis: Int): RollUp {
    val scope = rememberCoroutineScope()
    return remember(millis, scope) { RollUp(millis, scope) }
}
