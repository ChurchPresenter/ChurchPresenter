package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.text.AttributedString

/**
 * Lays out and paints a text box's paragraphs. Text is laid out by the engine itself
 * (LineBreakMeasurer over [SlideFontRegistry]-resolved fonts) — the acknowledged fidelity risk of
 * the native Keynote path.
 */
internal object KeynoteTextPainter {

    /** An empty paragraph still takes a line; Keynote's own leading is 1.2× the font size. */
    private const val EMPTY_LINE_HEIGHT_FACTOR = 1.2

    /** The size a font is created at before it is derived to the paragraph's own. */
    private const val BASE_FONT_SIZE = 12

    /** Shrink-to-fit never goes below this share of the text's size, however much there is. */
    private const val MIN_SHRINK_SCALE = 0.05

    /** Halvings of the shrink search: twelve put the scale within 0.03% of the best fit. */
    private const val SHRINK_SEARCH_STEPS = 12

    private val ITALIC_IN_NAME = Regex("Italic|Oblique", RegexOption.IGNORE_CASE)

    /** Keynote's soft return: a new line inside the same paragraph. */
    private const val LINE_SEPARATOR = '\u2028'

    private const val VERTICAL_MIDDLE = 1
    private const val VERTICAL_BOTTOM = 2

    /** What every line of one text box is laid out against. */
    private class LineLayoutContext(
        val graphics: Graphics2D,
        val width: Float,
        val autoSized: Boolean,
        val frc: FontRenderContext,
        /** Where lines start across the box: its left padding. */
        val left: Float = 0f,
    )

    /**
     * @param onlyIndex when non-null, only that paragraph is actually painted — every paragraph
     *   is still measured and advances `y`, so later paragraphs (and the stop-early check below)
     *   land at the same vertical position they would in the full render. This is the per-layer
     *   path for By-Paragraph/By-Bullet Keynote builds: unlike PPTX (which must mutate run XML
     *   through opaque POI drawing code to isolate one paragraph), Keynote already lays out text
     *   itself, so isolating one paragraph is just "skip painting, still advance" — the exact
     *   same loop drives both the whole-object render and the per-paragraph one, so they can't
     *   drift out of sync.
     */
    fun drawParagraphs(
        graphics: Graphics2D,
        drawable: KnDrawable.Text,
        slideWidthPt: Double,
        onlyIndex: Int? = null,
    ) {
        val g = drawable.geometry
        // Auto-sized text boxes persist size (0,0): lay out unwrapped from the anchor instead
        // (alignment offsets need a real box width, so they only apply to sized boxes).
        val autoSized = g.w <= 1.0
        // An auto-sized box grows around its text, so only a sized box insets it by its padding.
        val padding = if (autoSized) KnPadding.NONE else drawable.padding
        val width = if (autoSized) {
            (slideWidthPt - g.x).toFloat().coerceAtLeast(10f)
        } else {
            (g.w - padding.left - padding.right).toFloat().coerceAtLeast(1f)
        }
        val context = LineLayoutContext(graphics, width, autoSized, graphics.fontRenderContext, padding.left.toFloat())
        // A box with no height grows to its text: there is nothing to shrink into or align within.
        val boxHeight = (g.h - padding.top - padding.bottom).toFloat().takeIf { !autoSized && g.h > 1.0 }
        val scale = if (drawable.shrinkToFit && boxHeight != null) {
            fitScale(context, drawable.paragraphs, boxHeight)
        } else {
            1.0
        }
        val paragraphs = scaled(drawable.paragraphs, scale)
        var y = padding.top.toFloat() +
            (boxHeight?.let { verticalOffset(drawable.verticalAlignment, it, textHeight(context, paragraphs)) } ?: 0f)
        // Paragraphs past [onlyIndex] can neither be painted nor move it, so the walk stops there.
        val laidOut = if (onlyIndex == null) paragraphs else paragraphs.take(onlyIndex + 1)
        for ((index, paragraph) in laidOut.withIndex()) {
            y = advance(context, paragraph, y, paint = onlyIndex == null || onlyIndex == index)
        }
    }

    /**
     * The largest scale, up to 1, at which [paragraphs] fit [boxHeight] — Keynote's "Shrink text to
     * fit", which scales every font in the box together. Wrapping changes as the text narrows, so
     * the scale is searched for rather than computed.
     */
    private fun fitScale(context: LineLayoutContext, paragraphs: List<KnParagraph>, boxHeight: Float): Double {
        if (textHeight(context, paragraphs) <= boxHeight) return 1.0
        var fits = MIN_SHRINK_SCALE
        var overflows = 1.0
        repeat(SHRINK_SEARCH_STEPS) {
            val middle = (fits + overflows) / 2
            if (textHeight(context, scaled(paragraphs, middle)) <= boxHeight) fits = middle else overflows = middle
        }
        return fits
    }

    private fun scaled(paragraphs: List<KnParagraph>, scale: Double): List<KnParagraph> =
        if (scale == 1.0) paragraphs else paragraphs.map { it.copy(fontSizePt = it.fontSizePt * scale) }

    /** Where the text starts in a box [boxHeight] tall: top, middle or bottom aligned. */
    private fun verticalOffset(alignment: Int, boxHeight: Float, textHeight: Float): Float = when (alignment) {
        VERTICAL_MIDDLE -> (boxHeight - textHeight) / 2f
        VERTICAL_BOTTOM -> boxHeight - textHeight
        else -> 0f
    }

    /** How tall [paragraphs] lay out in [context], without painting them. */
    private fun textHeight(context: LineLayoutContext, paragraphs: List<KnParagraph>): Float =
        paragraphs.fold(0f) { y, paragraph -> advance(context, paragraph, y, paint = false) }

    /** Lays out (and when [paint], draws) one paragraph from [y], returning the `y` below it. */
    private fun advance(context: LineLayoutContext, paragraph: KnParagraph, y: Float, paint: Boolean): Float =
        if (paragraph.text.isBlank()) {
            y + (paragraph.fontSizePt * EMPTY_LINE_HEIGHT_FACTOR).toFloat()
        } else {
            layOutParagraph(context, paragraph, y, paint)
        }

    /** Measures and (optionally) paints one paragraph, returning the advanced `y`. */
    private fun layOutParagraph(
        context: LineLayoutContext,
        paragraph: KnParagraph,
        startY: Float,
        paint: Boolean,
    ): Float {
        var y = startY
        val graphics = context.graphics
        val font = fontFor(paragraph)
        val attributed = AttributedString(paragraph.text)
        attributed.addAttribute(TextAttribute.FONT, font)
        if (isRtl(paragraph.text)) {
            attributed.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL)
        }
        val measurer = LineBreakMeasurer(attributed.iterator, context.frc)
        if (paint) graphics.color = paragraph.color
        val text = paragraph.text
        while (measurer.position < text.length) {
            // A soft return (Shift-Return, U+2028) ends the line without ending the paragraph.
            val lineEnd = text.indexOf(LINE_SEPARATOR, measurer.position).takeIf { it >= 0 } ?: text.length
            if (lineEnd == measurer.position) {
                y += (paragraph.fontSizePt * EMPTY_LINE_HEIGHT_FACTOR).toFloat()
                measurer.position = lineEnd + 1
            } else {
                val layout = measurer.nextLayout(context.width, lineEnd, false)
                y += layout.ascent
                val lineX = when {
                    context.autoSized -> 0f
                    paragraph.alignment == 1 -> context.width - layout.advance      // right
                    paragraph.alignment == 2 -> (context.width - layout.advance) / 2f // center
                    else -> 0f                                                      // left / justified
                }
                if (paint) layout.draw(graphics, context.left + lineX, y)
                y += layout.descent + layout.leading
                if (measurer.position == lineEnd && lineEnd < text.length) measurer.position = lineEnd + 1
            }
        }
        // A soft return at the very end still opens a line, empty, that Keynote counts in the box.
        if (text.endsWith(LINE_SEPARATOR)) y += (paragraph.fontSizePt * EMPTY_LINE_HEIGHT_FACTOR).toFloat()
        return y
    }

    /**
     * Keynote names the typeface in PostScript form, so the face it means may be on the machine
     * under exactly that name — weight and all: "HelveticaNeue-Medium" is neither bold nor plain,
     * and the family lookup would draw it in the narrower Regular, fitting a line Keynote wraps.
     * The exact face is used when it is there; otherwise the weight comes from the name ("Arial-BoldMT")
     * as well as from CHAR_PROPS_BOLD — the name's style bits are OR-ed on top of the parsed
     * flags, never substituted for them.
     */
    private fun fontFor(paragraph: KnParagraph): Font {
        val name = paragraph.fontFamily
        val exact = name?.let { Font(it, Font.PLAIN, BASE_FONT_SIZE) }
            ?.takeIf { it.psName.equals(name, ignoreCase = true) }
        if (exact != null) {
            // The face carries its own weight and slant; only what it lacks is added.
            var style = Font.PLAIN
            if (paragraph.bold && !name.contains("Bold", ignoreCase = true)) style = style or Font.BOLD
            if (paragraph.italic && !ITALIC_IN_NAME.containsMatchIn(name)) style = style or Font.ITALIC
            return exact.deriveFont(style, paragraph.fontSizePt.toFloat())
        }
        val face = name?.let { SlideFontRegistry.resolveFace(it) }
        val family = face?.family ?: Font.SANS_SERIF
        var style = Font.PLAIN
        if (paragraph.bold || face?.bold == true) style = style or Font.BOLD
        if (paragraph.italic || face?.italic == true) style = style or Font.ITALIC
        return Font(family, style, BASE_FONT_SIZE).deriveFont(paragraph.fontSizePt.toFloat())
    }

    /** True when the paragraph's first strong-directional character is right-to-left. */
    private fun isRtl(text: String): Boolean {
        for (char in text) {
            when (Character.getDirectionality(char)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                else -> {}
            }
        }
        return false
    }
}
