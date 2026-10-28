package org.churchpresenter.sharedui.composables

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
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shadow
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
    // Text before this offset may be left out: the window never starts above the line holding it.
    // A line is kept whole, so the words before it on that line stay put rather than being cut out.
    // Pop-on pages ignore it.
    keepFrom: Int = 0,
    // The text [text] will grow into, [text] being a prefix of it -- a caption being typed out. Lines
    // break where this text's lines do, so a part-typed word already sits on the line it ends on,
    // and a centred or right-aligned line is placed at its finished width.
    layoutText: AnnotatedString? = null,
    // The box is held by its bottom edge, so it grows upward: a line added while it is still filling
    // slides the lines above it up, as a line added to a full box does.
    slideWhenGrowing: Boolean = false,
    // Told the start of the earliest line that will not be drawn again: the caller may drop the text
    // before it. The lines after a former line start wrap exactly as they did.
    onSafeCut: ((Int) -> Unit)? = null,
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
    val laidOut = layoutText ?: text
    // The layout of the text on screen, for its semantics when part of it is there only to be laid out
    val shownLayout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val finishedWidth = layoutText != null && style.textAlign.let {
        it == TextAlign.Center || it == TextAlign.Right || it == TextAlign.End
    }

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
                text = laidOut,
                outline = outline,
                scaleFactor = scaleFactor,
                color = Color.Unspecified,
                fontSize = TextUnit.Unspecified,
                style = style,
                modifier = measureOnly,
                onTextLayout = { full = it },
            )
        }.first().measure(free).height
        val layout = full
        val lines = shownLines(layout, text, layoutText)
        roll.generation // a finished slide re-measures, to stop drawing the line that left
        // As many whole lines as the room it is given holds, up to [maxLines]: a text box shorter
        // than that shows fewer lines rather than having its first and last cut through
        val (window, clip) = if (layout == null) {
            maxLines to clipHeightPx
        } else {
            linesThatFit(layout, maxLines, clipHeightPx, fullHeight, constraints.maxHeight)
        }
        val first = firstLine(layout, lines, keepFrom, window, paged)
        val reportedHeight = when {
            // A page holds its whole window from its first line, so it only ever gains lines underneath
            paged && lines > 0 -> clip
            layout == null -> clip.coerceAtMost(fullHeight)
            lines - first < window -> fillingHeight(layout, lines, first, clip, fullHeight)
            else -> clip
        }
        val drawFrom = if (layout == null || lines == 0 || paged) {
            first
        } else {
            roll.moved(layout, first, lines - 1, reportedHeight, slideWhenGrowing)
        }
        if (layout != null && lines > 0 && onSafeCut != null) {
            // A page is cut at its own first line, so the pages after it still start where they did;
            // otherwise a window's worth of lines is left above, for a caption corrected shorter
            val cutLine = if (paged) first else (minOf(drawFrom, first) - window).coerceAtLeast(0)
            if (cutLine > 0) onSafeCut(layout.getLineStart(cutLine))
        }
        val shown = when {
            layout == null || lines == 0 -> AnnotatedString("")
            layoutText == null -> {
                val start = if (drawFrom == 0) 0 else layout.getLineStart(drawFrom)
                if (start == 0) text else text.subSequence(start, text.length)
            }
            else -> brokenAsLaidOut(laidOut, layout, drawFrom until lines, text.length, finishedWidth)
        }
        val semantics = seenSemantics(shown, shownLayout)
        val visible = subcompose(CaptionSlot.VISIBLE) {
            OutlinedText(
                text = shown,
                outline = outline,
                scaleFactor = scaleFactor,
                color = Color.Unspecified,
                fontSize = TextUnit.Unspecified,
                style = style,
                modifier = textModifier.then(semantics).then(painter.modifier),
                onTextLayout = {
                    shownLayout[0] = it
                    painter.onTextLayout(it)
                },
            )
        }.first().measure(free)

        // Otherwise bottom-aligned: the last lines are the visible ones
        val y = if (paged) 0 else (reportedHeight - visible.height).coerceAtMost(0)
        layout(constraints.maxWidth, reportedHeight) {
            // Read here, not above, so the slide re-places the text without measuring it again
            visible.place(0, y + roll.offset())
        }
    }
}

/**
 * The window's first line: the line holding [keepFrom] while every line from it fits, the start of
 * the page the last line is on when [paged], and otherwise the line [maxLines] above the end.
 */
private fun firstLine(layout: TextLayoutResult?, lines: Int, keepFrom: Int, maxLines: Int, paged: Boolean): Int {
    val keeps = layout != null && lines > 0 && keepFrom > 0 && !paged
    val keepLine = if (keeps && layout != null) {
        layout.getLineForOffset(keepFrom.coerceAtMost(layout.layoutInput.text.length - 1)).coerceAtMost(lines - 1)
    } else {
        0
    }
    return when {
        lines - keepLine <= maxLines -> keepLine
        paged -> (lines - 1) / maxLines * maxLines
        else -> lines - maxLines
    }
}

/** The lines of [layout] with something of [text] on them, [layoutText] breaking them where it does. */
private fun shownLines(layout: TextLayoutResult?, text: AnnotatedString, layoutText: AnnotatedString?): Int = when {
    layout == null || text.isEmpty() -> 0
    layoutText == null -> layout.lineCount
    else -> layout.getLineForOffset(minOf(text.length, layoutText.length) - 1) + 1
}

/**
 * How many lines fit in [maxHeight], up to [maxLines], and how tall they are: [clipHeight] is
 * [maxLines] lines with the plate's room, which is what [fullHeight] has over [layout]'s own text.
 * At least one line is always shown, even in a box too short for it.
 */
private fun linesThatFit(
    layout: TextLayoutResult,
    maxLines: Int,
    clipHeight: Int,
    fullHeight: Int,
    maxHeight: Int,
): Pair<Int, Int> {
    if (maxHeight == Constraints.Infinity || clipHeight <= maxHeight) return maxLines to clipHeight
    val room = fullHeight - layout.size.height
    val lineHeight = (clipHeight - room) / maxLines.toFloat()
    val fit = ((maxHeight - room) / lineHeight).toInt().coerceIn(1, maxLines)
    return fit to (room + fit * lineHeight).roundToInt()
}

/**
 * How tall a box still filling is: its own lines from [first] -- an empty one is a line tall -- and
 * the room the plate needs, which is what [fullHeight] has over the text's own; never past the clip.
 */
private fun fillingHeight(layout: TextLayoutResult, lines: Int, first: Int, clipHeight: Int, fullHeight: Int): Int {
    val last = (lines - 1).coerceAtLeast(first)
    val window = layout.getLineBottom(last) - layout.getLineTop(first)
    val room = fullHeight - layout.size.height
    return clipHeight.coerceAtMost(room + window.roundToInt())
}

/**
 * The semantics of the text on screen: its own, unless [shown] carries the end of a line laid out
 * unseen -- then only what is seen, with [shownLayout] still there to be measured.
 */
private fun seenSemantics(shown: AnnotatedString, shownLayout: Array<TextLayoutResult?>): Modifier {
    val seen = shown.spanStyles.firstOrNull { it.item == HIDDEN }?.let { shown.subSequence(0, it.start) }
        ?: return Modifier
    return Modifier.clearAndSetSemantics {
        text = seen
        getTextLayoutResult { results -> shownLayout[0]?.let(results::add) != null }
    }
}

/**
 * The [lines] of [laidOut] as far as its first [shownLength] characters reach into them, each
 * ending in a line break where [layout] broke it, so the shown part wraps exactly as the whole will.
 * With [finishedWidth] the rest of the last line follows, invisible, so a centred line sits where it
 * will when it is finished.
 */
internal fun brokenAsLaidOut(
    laidOut: AnnotatedString,
    layout: TextLayoutResult,
    lines: IntRange,
    shownLength: Int,
    finishedWidth: Boolean,
): AnnotatedString = buildAnnotatedString {
    for (line in lines) {
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true).coerceAtLeast(start)
        if (line < lines.last) {
            append(laidOut.subSequence(start, end.coerceAtMost(shownLength)))
            append('\n')
        } else {
            val shownEnd = end.coerceIn(start, shownLength)
            append(laidOut.subSequence(start, shownEnd))
            if (finishedWidth && shownEnd < end) {
                val hiddenFrom = length
                append(laidOut.subSequence(shownEnd, end))
                addStyle(HIDDEN, hiddenFrom, length)
            }
        }
    }
}

/** Laid out but not seen: no fill, no shadow, and no stroke (see `inColor`). */
private val HIDDEN = SpanStyle(color = Color.Transparent, shadow = Shadow.None)

private enum class CaptionSlot { REFERENCE, FULL, VISIBLE }

/**
 * The slide behind roll-up. It follows the window's first line by its words, not its number: when
 * the same words are found again higher up -- new lines arrived under them, or old ones left above
 * -- the text is eased up from where they were, over [millis], with the lines that left still drawn
 * above until it ends. Words that cannot be found again were rewritten, and jump. Does nothing at 0.
 */
private class RollUp(private val millis: Int, private val scope: CoroutineScope) {
    private val slide = Animatable(0f)

    // What the window showed last: the laid-out text, where its first line started and ended, how
    // far that line's top sat above the last line's bottom, and how tall the box was
    private var last: Shown? = null

    // The slide for the frame the window moved in, before the animation has picked it up
    private var pending: Float? = null

    // The first line still drawn while a slide runs
    private var holdFrom: Int? = null
    private var job: Job? = null

    /** Bumped when a slide ends; reading it in measure re-measures then. */
    var generation by mutableIntStateOf(0)
        private set

    private class Shown(
        val text: String,
        val first: Int,
        val start: Int,
        val end: Int,
        val rise: Float,
        val height: Int,
    )

    /**
     * The window of [layout] now runs from line [first] to [lastLine] in a box [height] tall, held by
     * its bottom when [heldByBottom]. Returns the line to draw from, which is earlier than [first]
     * while lines are still sliding out.
     */
    fun moved(layout: TextLayoutResult, first: Int, lastLine: Int, height: Int, heldByBottom: Boolean): Int {
        val text = layout.layoutInput.text.text
        val bottom = layout.getLineBottom(lastLine)
        val previous = last
        last = Shown(
            text, first, layout.getLineStart(first), layout.getLineEnd(first, visibleEnd = true),
            bottom - layout.getLineTop(first), height,
        )
        if (millis <= 0 || previous == null) return first
        val match = matchingLine(previous.text, previous.start until previous.end, text, layout, first)
        // Where the old first line is now, against where it was: on screen the box's bottom stays
        // put when it is held by it, its top otherwise
        val grown = if (heldByBottom) 0 else height - previous.height
        val shift = match?.let { bottom - layout.getLineTop(it) - previous.rise - grown }
        // The lines still sliding out are numbered afresh as the old first line is
        val held = if (match == null) null else holdFrom?.let { (it + match - previous.first).coerceAtLeast(0) }
        when {
            // Rewritten, or moved down -- only a rewrite does that: no slide
            match == null || shift == null || shift < -SETTLED_PX -> stop()
            shift <= SETTLED_PX -> holdFrom = held
            else -> slideBy(shift, minOf(held ?: match, match))
        }
        return drawFrom(first)
    }

    private fun slideBy(shift: Float, from: Int) {
        holdFrom = from
        val start = shift + offset()
        pending = start
        job?.cancel()
        job = scope.launch {
            slide.snapTo(start)
            pending = null
            slide.animateTo(0f, tween(millis))
            holdFrom = null
            generation++
        }
    }

    private fun drawFrom(first: Int): Int = holdFrom?.let { minOf(it, first) } ?: first

    private fun stop() {
        job?.cancel()
        holdFrom = null
        pending = null
    }

    fun offset(): Int = (pending ?: slide.value).roundToInt()

    private companion object {
        // Less than this is the same place: line tops are fractional
        const val SETTLED_PX = 0.5f
    }
}

/**
 * The line of [layout] (laying out [text]) that the old window's first line -- [firstLine] of
 * [previous] -- became. Preferred is the line whose start carries on with ALL of the old window's
 * text from there, so a repeated line ("Amen.") is not mistaken for another; failing that, the
 * nearest line at or above [first] that starts with the old first line's own words, which a caption
 * corrected further down still has. Null when neither is there.
 */
internal fun matchingLine(
    previous: String,
    firstLine: IntRange,
    text: String,
    layout: TextLayoutResult,
    first: Int,
): Int? {
    val rest = previous.length - firstLine.first
    val ownLength = firstLine.last + 1 - firstLine.first
    var whole: Int? = null
    var fallback: Int? = null
    var line = minOf(first, layout.lineCount - 1)
    while (whole == null && line >= 0 && rest > 0) {
        val start = layout.getLineStart(line)
        if (text.regionMatches(start, previous, firstLine.first, rest)) {
            whole = line
        } else if (fallback == null && ownLength > 0) {
            if (text.regionMatches(start, previous, firstLine.first, ownLength)) fallback = line
        }
        line--
    }
    return whole ?: fallback
}

@Composable
private fun rememberRollUp(millis: Int): RollUp {
    val scope = rememberCoroutineScope()
    return remember(millis, scope) { RollUp(millis, scope) }
}
