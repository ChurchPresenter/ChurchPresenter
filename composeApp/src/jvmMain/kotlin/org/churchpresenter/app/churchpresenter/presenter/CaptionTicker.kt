package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.core.models.text.TextOutline
import kotlin.math.roundToInt

private const val NANOS_PER_SECOND = 1_000_000_000f

/** The gap, in widths of the font size, between pieces that do not run on from each other. */
private const val PIECE_GAP_EMS = 1.5f

/** One piece of the tape: its words and where on the tape its left edge is, in pixels. */
private class TickerPiece(val text: AnnotatedString, val x: Float, val width: Float) {
    val end: Float get() = x + width
}

/**
 * Captions as a ticker: one line crawling right to left at [speed] pixels a second.
 *
 * Each time [text] grows, only what is new is added to the tape -- straight after the words before
 * it when it runs on from them, or a gap later when the caption was rewritten -- and it always
 * enters from the right edge, never in the middle of the screen. Words that have left on the left
 * are dropped. When the tape backs up by more than a screen, the crawl speeds up to catch up rather
 * than falling minutes behind the room; a quiet room empties it.
 *
 * The clock is an infinite-animation frame wait, so under a test clock the ticker holds still.
 */
@Composable
internal fun CaptionTicker(
    text: AnnotatedString,
    style: TextStyle,
    outline: TextOutline,
    speed: Int,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val pieces = remember { mutableStateListOf<TickerPiece>() }
    val fed = remember { arrayOfNulls<String>(1) }
    // How far the tape has moved left; a piece is drawn at its x less this
    val scroll = remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        val viewWidth = constraints.maxWidth.toFloat()
        val gap = style.fontSize.value * PIECE_GAP_EMS
        LaunchedEffect(text) {
            val previous = fed[0].orEmpty()
            fed[0] = text.text
            val (added, runsOn) = newTickerText(previous, text)
            if (added.text.isBlank()) return@LaunchedEffect
            val width = measurer.measure(added, style, softWrap = false).size.width.toFloat()
            val end = pieces.lastOrNull()?.end ?: Float.NEGATIVE_INFINITY
            val x = maxOf(if (runsOn) end else end + gap, scroll.floatValue + viewWidth)
            pieces.add(TickerPiece(added, x, width))
        }
        LaunchedEffect(speed, viewWidth) {
            var last = withInfiniteAnimationFrameNanos { it }
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    val seconds = (now - last) / NANOS_PER_SECOND
                    last = now
                    val left = scroll.floatValue
                    val backlog = (pieces.lastOrNull()?.end ?: left) - left - viewWidth
                    val catchUp = 1f + (backlog - viewWidth).coerceAtLeast(0f) / viewWidth.coerceAtLeast(1f)
                    if (pieces.isNotEmpty()) scroll.floatValue = left + speed.coerceAtLeast(1) * catchUp * seconds
                    pieces.removeAll { it.end < scroll.floatValue }
                }
            }
        }
        // Taller than nothing even when empty, so the band holds its height between sentences
        Box(Modifier.fillMaxWidth()) {
            OutlinedText(
                text = AnnotatedString(" "),
                outline = outline,
                scaleFactor = 1f,
                color = Color.Transparent,
                fontSize = TextUnit.Unspecified,
                style = style,
            )
            pieces.forEach { piece ->
                OutlinedText(
                    text = piece.text,
                    outline = outline,
                    scaleFactor = 1f,
                    color = Color.Unspecified,
                    fontSize = TextUnit.Unspecified,
                    style = style,
                    softWrap = false,
                    fillWidth = false,
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .offset { IntOffset((piece.x - scroll.floatValue).roundToInt(), 0) },
                )
            }
        }
    }
}

/**
 * What of [current] is new since the ticker was last fed [previous], and whether it runs straight
 * on from it. A caption that only grew gives its new tail; one whose start was trimmed gives what
 * follows the words they still share; one rewritten past recognition is new from the start. Line
 * breaks become spaces, since a ticker is one line.
 */
internal fun newTickerText(previous: String, current: AnnotatedString): Pair<AnnotatedString, Boolean> {
    val now = current.text
    val shared = when {
        previous.isEmpty() -> 0
        now.startsWith(previous) -> previous.length
        else -> tailOverlap(previous, now)
    }
    val runsOn = shared > 0
    val tail = current.subSequence(shared, now.length)
    val flat = if ('\n' in tail.text) {
        AnnotatedString(tail.text.replace('\n', ' '), tail.spanStyles, tail.paragraphStyles)
    } else {
        tail
    }
    return flat to runsOn
}

/** The largest number of characters by which the tail of [a] and the head of [b] coincide. */
private fun tailOverlap(a: String, b: String): Int {
    var length = minOf(a.length, b.length)
    while (length > 0 && !a.endsWith(b.take(length))) length--
    return length
}
