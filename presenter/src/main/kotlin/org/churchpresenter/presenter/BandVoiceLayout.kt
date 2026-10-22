package org.churchpresenter.presenter

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import kotlin.math.roundToInt

/*
 * A song's second voice on a Lottie band.
 *
 * The player draws a text layer in one colour — a range selector's colour, in the file, applies to
 * the whole layer — so a word in another colour cannot be asked of it. The band draws its text
 * twice instead: once as always, and once more in the second voice's colour with everything but
 * the text hidden, cut to where the second voice's words sit. Those places are known because the
 * band lays its own text out — it wraps every line before the player sees it, measuring each
 * character in the layer's own font — so the cut follows the same arithmetic the words were set by.
 *
 * The cut moves with the words. It is worked out each frame from what the player draws that
 * frame: the typed part of a typewriter's text, a ticker's scroll, and the text layer's own
 * transform as the file keyframes it (see LayerMotion.kt).
 */

/** What [secondVoiceRects] needs of a laid-out slot: its text, the wrapped lines and where they sit. */
internal data class BandVoiceLayout(
    /** The slot's text as written, line breaks and all — what [voiceRanges] index into. */
    val text: String,
    val voiceRanges: List<IntRange>,
    /** The lines the band handed the player, in order. */
    val lines: List<String>,
    val justify: TextJustify,
    val box: LottieSlotBox,
    /** The first line's top, in template pixels. */
    val top: Float,
    val lineHeight: Float,
)

/**
 * The rectangles, in template pixels, the second voice's characters of [layout] occupy — one per
 * run of them on each line. [advance] is a character's width at the fitted size, tracking included,
 * which is the measure the lines were wrapped by.
 */
internal fun secondVoiceRects(layout: BandVoiceLayout, advance: (Char) -> Float): List<Rect> {
    val rects = mutableListOf<Rect>()
    var cursor = 0
    layout.lines.forEachIndexed { row, line ->
        if (line.isEmpty()) return@forEachIndexed
        // A wrapped line is a run of the text from where the last one stopped; the space a wrap
        // broke at is not in either.
        val start = layout.text.indexOf(line, cursor).takeIf { it >= 0 } ?: return@forEachIndexed
        cursor = start + line.length
        val widths = line.map(advance)
        val lineWidth = widths.sum()
        val box = layout.box
        val left = when (layout.justify) {
            TextJustify.Left -> box.x
            TextJustify.Right -> box.x + box.w - lineWidth
            else -> box.x + (box.w - lineWidth) / 2f
        }
        val y = layout.top + row * layout.lineHeight
        var x = left
        var runStart = Float.NaN
        line.indices.forEach { i ->
            val sung = layout.voiceRanges.any { start + i in it }
            if (sung && runStart.isNaN()) runStart = x
            if (!sung && !runStart.isNaN()) {
                rects += Rect(runStart, y, x, y + layout.lineHeight)
                runStart = Float.NaN
            }
            x += widths[i]
        }
        if (!runStart.isNaN()) rects += Rect(runStart, y, x, y + layout.lineHeight)
    }
    return rects
}

/** A slot's second voice: where its words sit and each character's advance, to cut to at any frame. */
internal data class BandVoice(val layout: BandVoiceLayout, val advances: Map<Char, Float>) {
    /** The rectangles for [shown] — the part of the slot's text the player draws this frame. */
    fun rectsFor(shown: String): List<Rect> =
        secondVoiceRects(layout.copy(lines = shown.split('\n'))) { c -> advances[c] ?: 0f }
}

/**
 * Each of [rects] as the four corners it lands on: moved by [shift] — a ticker's scroll — and then
 * through the layer's [matrix], when there is one.
 */
internal fun voiceShapes(rects: List<Rect>, shift: Offset, matrix: Matrix?): List<List<Offset>> =
    rects.map { r ->
        val moved = r.translate(shift)
        listOf(moved.topLeft, moved.topRight, moved.bottomRight, moved.bottomLeft)
            .map { corner -> matrix?.map(corner) ?: corner }
    }

/**
 * Draws the voice pass cut to [shapes], given in the template's pixels, where an image of the
 * template is drawn with [contentScale] at the bottom centre of this scope — as the band draws it.
 */
internal fun ContentDrawScope.drawCutToVoices(
    template: BibleLottieTemplate,
    shapes: List<List<Offset>>,
    contentScale: ContentScale,
) {
    if (shapes.isEmpty()) return
    val source = Size(template.width, template.height)
    val scale = contentScale.computeScaleFactor(source, size)
    val scaled = IntSize((source.width * scale.scaleX).roundToInt(), (source.height * scale.scaleY).roundToInt())
    val origin = Alignment.BottomCenter.align(
        scaled,
        IntSize(size.width.roundToInt(), size.height.roundToInt()),
        layoutDirection,
    )
    fun place(p: Offset) = Offset(origin.x + p.x * scale.scaleX, origin.y + p.y * scale.scaleY)
    val cut = Path()
    shapes.filter { it.isNotEmpty() }.forEach { corners ->
        val first = place(corners.first())
        cut.moveTo(first.x, first.y)
        corners.drop(1).map(::place).forEach { cut.lineTo(it.x, it.y) }
        cut.close()
    }
    clipPath(cut) { this@drawCutToVoices.drawContent() }
}
