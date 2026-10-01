package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    // Pop-on: the window fills from its top line by line, and when the next line would not fit it
    // clears and starts again at the top, so nothing on screen moves while it is being read.
    paged: Boolean = false,
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
    val roll = rememberRollUp(rollUpMillis.takeIf { !paged } ?: 0)
    val textModifier = Modifier.fillMaxWidth().backdropRoom(backdrop, scaleFactor)
    // The copies laid out only to be measured are never seen, so nothing may read them out either
    val measureOnly = textModifier.clearAndSetSemantics {}

    // Only the lines in the window are drawn. Clipping the whole text at a line edge left ink from
    // the line above showing -- descenders, the outline's stroke, a shadow all hang below their own
    // line -- so the full text is laid out once to learn where its lines start, and only the part
    // from the window's first line is composed to be seen.
    SubcomposeLayout(modifier.clipToBounds()) { constraints ->
        val free = Constraints(minWidth = constraints.minWidth, maxWidth = constraints.maxWidth)
        // Invisible reference: measures exact height of maxLines lines -- plus the room the plate
        // needs, because that height becomes the clip. Without it a full N lines of text measures
        // taller than the clip the moment a backdrop is on, and reads as overflow.
        val clipHeightPx = subcompose(CaptionSlot.REFERENCE) {
            Text(text = referenceText, style = style, modifier = measureOnly, maxLines = maxLines)
        }.first().measure(free).height

        var full: TextLayoutResult? = null
        val fullHeight = subcompose(CaptionSlot.FULL) {
            OutlinedText(
                text = text,
                outline = outline,
                scaleFactor = scaleFactor,
                color = Color.Unspecified,
                fontSize = TextUnit.Unspecified,
                style = style,
                modifier = measureOnly,
                onTextLayout = { full = it },
            )
        }.first().measure(free).height
        val lines = full?.lineCount ?: 0
        roll.generation // a finished slide re-measures, to stop drawing the line that left
        val first = when {
            lines <= maxLines -> 0
            paged -> (lines - 1) / maxLines * maxLines
            else -> roll.moved(lines - maxLines) { line -> full?.getLineTop(line) ?: 0f }
        }
        val start = if (first == 0) 0 else full?.getLineStart(first) ?: 0
        val shown = if (start == 0) text else text.subSequence(start, text.length)
        val visible = subcompose(CaptionSlot.VISIBLE) {
            OutlinedText(
                text = shown,
                outline = outline,
                scaleFactor = scaleFactor,
                color = Color.Unspecified,
                fontSize = TextUnit.Unspecified,
                style = style,
                modifier = textModifier.then(painter.modifier),
                onTextLayout = painter::onTextLayout,
            )
        }.first().measure(free)

        // A page holds its whole window from its first line, so it only ever gains lines underneath
        val reportedHeight = if (paged && lines > 0) clipHeightPx else clipHeightPx.coerceAtMost(fullHeight)
        // Otherwise bottom-aligned: the last lines are the visible ones
        val y = if (paged) 0 else (reportedHeight - visible.height).coerceAtMost(0)
        layout(constraints.maxWidth, reportedHeight) {
            // Read here, not above, so the slide re-places the text without measuring it again
            visible.place(0, y + roll.offset())
        }
    }
}

private enum class CaptionSlot { REFERENCE, FULL, VISIBLE }

/**
 * The slide behind roll-up. When the window's first line moves down by some lines, the lines that
 * left are kept drawn above it and the text is eased up by their height over [millis]; once the
 * slide ends they are dropped. Does nothing at 0.
 */
private class RollUp(private val millis: Int, private val scope: CoroutineScope) {
    private val slide = Animatable(0f)
    private var lastFirst: Int? = null

    // The slide for the frame the window moved in, before the animation has picked it up
    private var pending: Float? = null

    // The first line still drawn while a slide runs
    private var holdFrom: Int? = null
    private var job: Job? = null

    /** Bumped when a slide ends; reading it in measure re-measures then. */
    var generation by mutableIntStateOf(0)
        private set

    /**
     * The window now starts at line [first]; [top] gives a line's top in pixels. Returns the line to
     * draw from, which is earlier than [first] while lines are still sliding out.
     */
    fun moved(first: Int, top: (Int) -> Float): Int {
        val previous = lastFirst
        lastFirst = first
        if (millis <= 0 || previous == null) return first
        if (first < previous) {
            // The text was trimmed or rewritten, so line numbers no longer match: no slide
            job?.cancel()
            holdFrom = null
            pending = null
            return first
        }
        if (first > previous) {
            val from = holdFrom?.let { minOf(it, previous) } ?: previous
            holdFrom = from
            pending = top(first) - top(previous) + offset()
            val shift = pending ?: 0f
            job?.cancel()
            job = scope.launch {
                slide.snapTo(shift)
                pending = null
                slide.animateTo(0f, tween(millis))
                holdFrom = null
                generation++
            }
        }
        return holdFrom?.let { minOf(it, first) } ?: first
    }

    fun offset(): Int = (pending ?: slide.value).roundToInt()
}

@Composable
private fun rememberRollUp(millis: Int): RollUp {
    val scope = rememberCoroutineScope()
    return remember(millis, scope) { RollUp(millis, scope) }
}
