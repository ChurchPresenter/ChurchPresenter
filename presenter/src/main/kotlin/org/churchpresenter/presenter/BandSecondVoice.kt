package org.churchpresenter.presenter

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
 * The cut is where the words sit when still, so a template whose text slides, scales or types in
 * shows the second voice's colour once the text has arrived, and drops it as the text leaves.
 */

/** What [secondVoiceRects] needs of a laid-out slot: its text, the wrapped lines and where they sit. */
internal data class VoiceLayout(
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
internal fun secondVoiceRects(layout: VoiceLayout, advance: (Char) -> Float): List<Rect> {
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

/**
 * Whether the voice pass shows at [frame]: always on a template whose text stands still, else only
 * while it is held — from the end of `text_in` to the start of `text_out`.
 */
internal fun voicePassShows(template: BibleLottieTemplate, layers: Set<String>, frame: Float): Boolean {
    val still = template.meta.textMotion == BandTextMotion.NONE && layers.none { it in template.movingTextLayers }
    if (still) return true
    val textIn = template.segment(BibleLottieTemplate.SEGMENT_TEXT_IN)
    val textOut = template.segment(BibleLottieTemplate.SEGMENT_TEXT_OUT)
    return frame >= textIn.endFrame && frame <= textOut.startFrame
}

/**
 * Draws the voice pass cut to [rects], given in the template's pixels, where an image of the
 * template is drawn with [contentScale] at the bottom centre of this scope — as the band draws it.
 */
internal fun ContentDrawScope.drawCutToVoices(template: BibleLottieTemplate, rects: List<Rect>, contentScale: ContentScale) {
    if (rects.isEmpty()) return
    val source = Size(template.width, template.height)
    val scale = contentScale.computeScaleFactor(source, size)
    val scaled = IntSize((source.width * scale.scaleX).roundToInt(), (source.height * scale.scaleY).roundToInt())
    val origin = Alignment.BottomCenter.align(
        scaled,
        IntSize(size.width.roundToInt(), size.height.roundToInt()),
        layoutDirection,
    )
    val cut = Path()
    rects.forEach { r ->
        cut.addRect(
            Rect(
                origin.x + r.left * scale.scaleX,
                origin.y + r.top * scale.scaleY,
                origin.x + r.right * scale.scaleX,
                origin.y + r.bottom * scale.scaleY,
            ),
        )
    }
    clipPath(cut) { this@drawCutToVoices.drawContent() }
}
