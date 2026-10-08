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

    /** What every line of one text box is laid out against. */
    private class LineLayoutContext(
        val graphics: Graphics2D,
        val width: Float,
        val autoSized: Boolean,
        val frc: FontRenderContext,
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
        val width = if (autoSized) (slideWidthPt - g.x).toFloat().coerceAtLeast(10f) else g.w.toFloat()
        val context = LineLayoutContext(graphics, width, autoSized, graphics.fontRenderContext)
        var y = 0f
        // Paragraphs past [onlyIndex] can neither be painted nor move it, so the walk stops there.
        val laidOut = if (onlyIndex == null) drawable.paragraphs else drawable.paragraphs.take(onlyIndex + 1)
        for ((index, paragraph) in laidOut.withIndex()) {
            y = if (paragraph.text.isBlank()) {
                y + (paragraph.fontSizePt * EMPTY_LINE_HEIGHT_FACTOR).toFloat()
            } else {
                layOutParagraph(context, paragraph, y, paint = onlyIndex == null || onlyIndex == index)
            }
        }
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
        while (measurer.position < paragraph.text.length) {
            val layout = measurer.nextLayout(context.width) ?: break
            y += layout.ascent
            val lineX = when {
                context.autoSized -> 0f
                paragraph.alignment == 1 -> context.width - layout.advance      // right
                paragraph.alignment == 2 -> (context.width - layout.advance) / 2f // center
                else -> 0f                                                      // left / justified
            }
            if (paint) layout.draw(graphics, lineX, y)
            y += layout.descent + layout.leading
        }
        return y
    }

    /**
     * Keynote names the typeface in PostScript form, so the weight can live in the name
     * ("Arial-BoldMT") rather than in CHAR_PROPS_BOLD. Take both: the name's style bits are OR-ed
     * on top of the parsed flags, never substituted for them.
     */
    private fun fontFor(paragraph: KnParagraph): Font {
        val face = paragraph.fontFamily?.let { SlideFontRegistry.resolveFace(it) }
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
