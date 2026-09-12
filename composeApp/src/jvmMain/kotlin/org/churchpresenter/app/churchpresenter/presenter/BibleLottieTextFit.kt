package org.churchpresenter.app.churchpresenter.presenter

/**
 * The lines a Lottie player breaks [text] into inside a box [boxWidth] wide.
 *
 * This is the player's own algorithm, not Compose's paragraph wrap: characters are summed one by
 * one — each [charWidth] plus [tracking] — and the line breaks at the last word boundary once the
 * running width reaches the box, mid-word only when a single word is wider than the box. The fit
 * below has to count the same lines the player will draw, or a verse that fits here overflows
 * there.
 */
internal fun wrapLikeLottie(
    text: String,
    boxWidth: Float,
    tracking: Float,
    charWidth: (Char) -> Float,
): List<String> {
    if (text.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var lineWidth = 0f
    var lineStart = 0
    var wordStart = 0
    var wordWidth = 0f
    var nextStartsWord = false
    for (i in text.indices) {
        val c = text[i]
        val cw = charWidth(c) + tracking
        if (c == ' ') {
            nextStartsWord = true
        } else if (nextStartsWord) {
            nextStartsWord = false
            wordStart = i
            wordWidth = cw
        } else {
            wordWidth += cw
        }
        lineWidth += cw
        if (boxWidth > 0f && lineWidth >= boxWidth) {
            if (c == ' ') continue
            if (wordStart == lineStart) {
                lines += text.substring(lineStart, i).trim()
                lineStart = i
                lineWidth = cw
                wordStart = lineStart
                wordWidth = cw
            } else {
                lines += text.substring(lineStart, wordStart - 1).trim()
                lineStart = wordStart
                lineWidth = wordWidth
            }
        }
    }
    if (lineWidth > 0f) lines += text.substring(lineStart)
    return lines
}

/** A slot's fitted type: the size and the lines it wraps to at that size. */
internal data class FittedSlot(val fontSize: Float, val lines: List<String>, val lineWidthPx: Float)

/** What a slot has to fit: its text, its box, the size it would like, and the tracking at that size. */
internal data class SlotFitRequest(
    val text: String,
    val box: LottieSlotBox,
    val baseSize: Float,
    val trackingAtBase: Float,
    /** A reference or a ticker fits its width on one line instead of wrapping to its height. */
    val singleLine: Boolean,
)

/**
 * The largest size at or below the request's base size whose wrapped lines fit inside the box,
 * found by the same binary search the classic band uses. [charWidthAtBase] measures a character
 * at the base size; glyph widths scale with the size, so smaller sizes are derived rather than
 * re-measured.
 */
internal fun fitLottieSlot(request: SlotFitRequest, charWidthAtBase: (Char) -> Float): FittedSlot {
    val text = request.text
    val box = request.box
    val baseSize = request.baseSize
    val trackingAtBase = request.trackingAtBase
    val singleLine = request.singleLine
    if (text.isEmpty() || baseSize <= 0f) return FittedSlot(baseSize.coerceAtLeast(1f), emptyList(), 0f)
    // A lyric keeps its own line breaks: each written line wraps on its own, as the player does.
    fun linesAt(scale: Float): List<String> {
        val width = if (singleLine) 0f else box.w
        return text.split('\n').flatMap { line ->
            wrapLikeLottie(line, width, trackingAtBase * scale) { charWidthAtBase(it) * scale }.ifEmpty { listOf("") }
        }
    }
    fun widthAt(scale: Float, line: String): Float =
        line.sumOf { (charWidthAtBase(it) * scale + trackingAtBase * scale).toDouble() }.toFloat()
    fun fits(scale: Float): Boolean {
        val size = baseSize * scale
        val lines = linesAt(scale)
        return if (singleLine) {
            lines.size <= 1 && widthAt(scale, lines.firstOrNull() ?: "") <= box.w && size * LINE_HEIGHT_FACTOR <= box.h
        } else {
            lines.size * size * LINE_HEIGHT_FACTOR <= box.h
        }
    }
    val scale = binarySearchFitScale(iterations = FIT_ITERATIONS) { fits(it) }
    val lines = linesAt(scale)
    val widest = lines.maxOfOrNull { widthAt(scale, it) } ?: 0f
    return FittedSlot(baseSize * scale, lines, widest)
}

/** Lottie's own default line height, which the generator writes and the player assumes. */
internal const val LINE_HEIGHT_FACTOR = 1.2f

private const val FIT_ITERATIONS = 10
