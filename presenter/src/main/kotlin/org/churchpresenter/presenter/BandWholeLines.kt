package org.churchpresenter.presenter

import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import org.churchpresenter.lottiegen.lottie.ShapedLines

/*
 * The band's own placement of text the player draws as whole lines (see `TextShaping`).
 *
 * Compottie places a whole line by the sum of its letters measured one at a time, which for a
 * script like Tamil is wider than the line it draws, so a centred line lands left of centre. The
 * band wraps by shaped widths instead and tells the player to draw each line from the left, in a
 * box too wide to wrap again, with padding in front that puts it where its justification would.
 */

/**
 * The box width a whole-line slot hands the player: wide enough that its letter-by-letter sum of
 * a padded line, however far above the shaped width, never makes it wrap again.
 */
private const val NO_WRAP_WIDTH = 1_000_000f

/** Each line's padding (see SlotRender.pads) and the left edge it puts the line at, in template pixels. */
internal class PlacedLines(val pads: List<String>, val lefts: List<Float>)

/**
 * Where each of [lines], laid out by [shaped] at the base size and fitted at [scale] of it, sits in
 * [box] by [justify]: the padding that puts it there, and its left edge in template pixels.
 */
internal fun placeLines(
    shaped: ShapedLines,
    lines: List<String>,
    box: LottieSlotBox,
    justify: TextJustify,
    scale: Float,
): PlacedLines {
    val alignment = ShapedLines.alignmentOf(justify)
    val boxAtBase = box.w / scale
    return PlacedLines(
        pads = lines.map { shaped.padding(it, boxAtBase, alignment) },
        lefts = lines.map { box.x + shaped.offset(shaped.width(it), boxAtBase, alignment) * scale },
    )
}

/** What the player is told to justify by: from the left for a ticker or lines the band placed. */
internal fun playerJustify(justify: TextJustify, ticker: Boolean, placed: PlacedLines?): TextJustify =
    if (ticker || placed != null) TextJustify.Left else justify

/** The wrap box the player is given: wide enough for a ticker's whole line, or for placed lines never to wrap. */
internal fun playerBox(box: LottieSlotBox, ticker: Boolean, placed: PlacedLines?, lineWidth: Float): LottieSlotBox =
    when {
        ticker -> box.copy(w = lineWidth + box.w)
        placed != null -> box.copy(w = NO_WRAP_WIDTH)
        else -> box
    }

/**
 * How wide a run of a line is at [scale] of the base size, for the second voice's cut: shaped
 * when the player draws whole lines, and otherwise each character's [advanceAtBase] summed.
 */
internal fun voiceRunWidth(
    shaped: ShapedLines?,
    text: String,
    scale: Float,
    advanceAtBase: (Char) -> Float,
): (String) -> Float {
    if (shaped != null) return { run -> shaped.width(run) * scale }
    val advances = text.toSet().associateWith { c -> advanceAtBase(c) * scale }
    return { run -> run.sumOf { (advances[it] ?: 0f).toDouble() }.toFloat() }
}
