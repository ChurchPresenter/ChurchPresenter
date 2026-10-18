/*
 * The line breaking below is a port of Apache POI 5.5.1's DrawTextParagraph.breakText,
 * getAttributedString, processGlyphs and nextPart, licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changed: each run's font has kerning turned on, and a
 * line's trailing spaces may hang past the wrapping width, and each run's font is built from the
 * substituted family rather than the deck's own.
 */
package org.churchpresenter.presentationengine.pptx

import org.apache.poi.common.usermodel.fonts.FontGroup
import org.apache.poi.common.usermodel.fonts.FontInfo
import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.DrawFontManager
import org.apache.poi.sl.draw.DrawPaint
import org.apache.poi.sl.draw.DrawTextParagraph
import org.apache.poi.sl.usermodel.HighlightColorSupport
import org.apache.poi.sl.usermodel.TextParagraph
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign
import org.apache.poi.sl.usermodel.TextRun
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.text.AttributedCharacterIterator.Attribute
import java.text.AttributedString

/** A laid-out line and the index in the paragraph's text where it ends. */
private class Line(val layout: TextLayout, val end: Int)

/** One attribute over a range of the paragraph's text — POI's private `AttributedStringData`. */
private class RangeAttribute(val attribute: Attribute, val value: Any, val begin: Int, val end: Int)

/**
 * A paragraph drawable that lays its lines out with kerning, reports the descent of its last
 * line, and opens the two steps [stackParagraphs] calls from outside POI's package.
 *
 * PowerPoint kerns text at the sizes slides use (every stock template sets `kern="1200"`, kerning
 * from 12pt up); Java kerns only when the font asks for it, and POI never does. Unkerned, a line
 * runs a little wider than PowerPoint's — enough that a line PowerPoint fits in its box wraps its
 * last word here, the paragraph grows a line, and the slide no longer matches. Kerning is applied
 * at every size: below 12pt the difference it makes is a fraction of a point.
 *
 * Lines also break on their visible width, letting the spaces that end them hang past the edge as
 * PowerPoint does; see [nextHangingLayout].
 */
internal class KernedParagraph(paragraph: TextParagraph<*, *, *>) : DrawTextParagraph(paragraph) {
    val lastLineDescent: Double
        get() = lines.lastOrNull()?.layout?.descent?.toDouble() ?: 0.0

    fun layOut(graphics: Graphics2D) = breakText(graphics)

    fun markFirst(first: Boolean) = setFirstParagraph(first)

    override fun breakText(graphics: Graphics2D) {
        lines.clear()
        val factory = DrawFactory.getInstance(graphics)
        val text = StringBuilder()
        val attributes = attributedRuns(graphics, text)
        val plain = text.toString()
        val withBreaks = AttributedString(plain)
        val withoutBreaks = AttributedString(plain.replace(LINE_BREAKS, " "))
        for (range in attributes) {
            withBreaks.addAttribute(range.attribute, range.value, range.begin, range.end)
            withoutBreaks.addAttribute(range.attribute, range.value, range.begin, range.end)
        }
        val iterator = withBreaks.iterator
        val iteratorNoBreaks = withoutBreaks.iterator
        val measurer = LineBreakMeasurer(iterator, graphics.fontRenderContext)
        val largestSize = attributes.filter { it.attribute == TextAttribute.SIZE }
            .maxOfOrNull { (it.value as Number).toFloat() } ?: 0f
        var done = false
        while (!done) {
            val start = measurer.position
            // POI adds a point to absorb rounding; a box narrower than its insets still gets one.
            val wrappingWidth = (getWrappingWidth(lines.isEmpty(), graphics) + 1).coerceAtLeast(1.0).toFloat()
            val line = nextLine(measurer, plain, wrappingWidth, largestSize)
            if (line != null) {
                lines.add(factory.getTextFragment(line.layout, AttributedString(iteratorNoBreaks, start, line.end)))
            }
            done = line == null || line.end == iterator.endIndex
        }
        rawText = plain
    }

    /** The next line from [measurer]'s position, or null when not even part of a word fits. */
    private fun nextLine(measurer: LineBreakMeasurer, text: String, width: Float, largestSize: Float): Line? {
        // A paragraph opening with a line break gets an empty first line.
        if (measurer.position == 0 && text.startsWith("\n")) {
            return measurer.nextLayout(width, 1, false)?.let { Line(it, 1) }
        }
        val start = measurer.position
        val nextBreak = text.indexOf("\n", start + 1).takeIf { it != -1 } ?: text.length
        val layout = measurer.nextHangingLayout(text, width, nextBreak, largestSize)
            // A word wider than the whole line comes back null; break inside it instead.
            ?: measurer.nextLayout(width, nextBreak, false)
            ?: return null
        val end = measurer.position
        // The break character itself is drawn on neither line.
        if (end < text.length && text[end] == '\n') measurer.position = end + 1
        val align = paragraph.textAlign
        val justified = align == TextAlign.JUSTIFY || align == TextAlign.JUSTIFY_LOW
        return Line(if (justified) layout.getJustifiedLayout(width) else layout, end)
    }

    private fun attributedRuns(graphics: Graphics2D, text: StringBuilder): List<RangeAttribute> {
        val paint = DrawPaint(paragraph.parentShape)
        val fonts = DrawFactory.getInstance(graphics).getFontManager(graphics)
        val ranges = mutableListOf<RangeAttribute>()
        for (run in paragraph) {
            var runText = getRenderableText(graphics, run)
            if (runText.isEmpty()) continue
            val fontInfo = run.getFontInfo(null)
            runText = fonts.mapFontCharset(graphics, fontInfo, runText)
            val begin = text.length
            text.append(runText)
            val att = runAttributes(graphics, paint, run)
            // The font Java draws is the FONT attribute, built here from the *substituted* family:
            // POI built it from the deck's own name, which for a font this machine lacks is Java's
            // Dialog — the substitute it then added as FAMILY is ignored beside a FONT.
            val runFont = fontInfo ?: PlainFontInfo(paragraph.defaultFontFamily)
            att[TextAttribute.FAMILY] = mappedFamily(graphics, fonts, runFont)
            att[TextAttribute.KERNING] = TextAttribute.KERNING_ON
            att[TextAttribute.FONT] = Font(att)
            att.forEach { (key, value) -> ranges += RangeAttribute(key, value, begin, text.length) }
            ranges += partFonts(graphics, fonts, PlacedRun(run, runText, begin), att)
        }
        // A paragraph needs at least one character to be measured at all.
        if (text.isEmpty()) {
            text.append(" ")
            val att = mutableMapOf<Attribute, Any>(
                TextAttribute.SIZE to paragraph.defaultFontSize.toFloat(),
                TextAttribute.FAMILY to mappedFamily(graphics, fonts, PlainFontInfo(paragraph.defaultFontFamily)),
                TextAttribute.KERNING to TextAttribute.KERNING_ON,
            )
            att[TextAttribute.FONT] = Font(att)
            att.forEach { (key, value) -> ranges += RangeAttribute(key, value, 0, 1) }
        }
        return ranges
    }

    private fun mappedFamily(graphics: Graphics2D, fonts: DrawFontManager, font: FontInfo): String =
        fonts.getMappedFont(graphics, font)?.typeface ?: paragraph.defaultFontFamily

    private fun runAttributes(graphics: Graphics2D, paint: DrawPaint, run: TextRun): MutableMap<Attribute, Any> {
        val att = mutableMapOf<Attribute, Any>()
        paint.getPaint(graphics, run.fontColor)?.let { att[TextAttribute.FOREGROUND] = it }
        if (run is HighlightColorSupport) {
            run.highlightColor
                ?.let { style -> paint.getPaint(graphics, style) }
                ?.let { att[TextAttribute.BACKGROUND] = it }
        }
        att[TextAttribute.SIZE] = (run.fontSize ?: paragraph.defaultFontSize).toFloat()
        if (run.isBold) att[TextAttribute.WEIGHT] = TextAttribute.WEIGHT_BOLD
        if (run.isItalic) att[TextAttribute.POSTURE] = TextAttribute.POSTURE_OBLIQUE
        if (run.isUnderlined) {
            att[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
            att[TextAttribute.INPUT_METHOD_UNDERLINE] = TextAttribute.UNDERLINE_LOW_TWO_PIXEL
        }
        if (run.isStrikethrough) att[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
        if (run.isSubscript) att[TextAttribute.SUPERSCRIPT] = TextAttribute.SUPERSCRIPT_SUB
        if (run.isSuperscript) att[TextAttribute.SUPERSCRIPT] = TextAttribute.SUPERSCRIPT_SUPER
        run.hyperlink?.let { link ->
            link.address?.let { att[HYPERLINK_HREF] = it }
            link.label?.let { att[HYPERLINK_LABEL] = it }
        }
        return att
    }

    /**
     * POI's `processGlyphs`: per font group of the run (Latin, East Asian, symbol, …), the font for
     * the characters its mapped font can show and the fallback font for those it cannot — each as
     * the FONT of its own stretch, built on the run's [base] attributes, since a FONT is the only
     * font attribute Java honours beside one.
     */
    private fun partFonts(
        graphics: Graphics2D,
        fonts: DrawFontManager,
        placed: PlacedRun,
        base: Map<Attribute, Any>,
    ): List<RangeAttribute> {
        val run = placed.run
        val ranges = mutableListOf<RangeAttribute>()
        fun font(family: String?, from: Int, to: Int) {
            val font = Font(base + (TextAttribute.FAMILY to (family ?: paragraph.defaultFontFamily)))
            ranges += RangeAttribute(TextAttribute.FONT, font, placed.begin + from, placed.begin + to)
        }
        var groupBegin = 0
        for (group in FontGroup.getFontGroupRanges(placed.text)) {
            val runFont = run.getFontInfo(group.fontGroup) ?: run.getFontInfo(FontGroup.LATIN)
            val fallbackInfo = fonts.getFallbackFont(graphics, runFont)
            val mappedInfo = fonts.getMappedFont(graphics, runFont)
                ?: fonts.getMappedFont(graphics, PlainFontInfo(paragraph.defaultFontFamily))
                ?: fallbackInfo
            val mapped = fonts.createAWTFont(graphics, mappedInfo, FONT_PROBE_SIZE, run.isBold, run.isItalic)
            val groupEnd = groupBegin + group.length
            var partEnd = groupBegin
            while (partEnd < groupEnd) {
                var partBegin = partEnd
                partEnd = nextPart(mapped, placed.text, partBegin, groupEnd, displayed = true)
                if (partBegin < partEnd) font(mappedInfo.typeface, partBegin, partEnd)
                partBegin = partEnd
                partEnd = nextPart(mapped, placed.text, partBegin, groupEnd, displayed = false)
                if (partBegin < partEnd) font(fallbackInfo.typeface, partBegin, partEnd)
            }
            groupBegin = groupEnd
        }
        return ranges
    }
}

/** A run's text and where it starts in its paragraph's. */
private class PlacedRun(val run: TextRun, val text: String, val begin: Int)

/**
 * The next line, wrapped on its visible width: the spaces that end it may run past [width], by up
 * to [slack] (one em of the paragraph's largest size), as they do in PowerPoint.
 *
 * Java's `LineBreakMeasurer` counts trailing spaces into the width, so a line whose words fit
 * exactly lost its last word to the next line. Here the line is measured with the slack added
 * and, while its visible part is still too wide, re-measured with the limit pulled back to the start
 * of its last word — one word at a time, ending at worst on the line POI's own call would give.
 */
private fun LineBreakMeasurer.nextHangingLayout(
    text: String,
    width: Float,
    limit: Int,
    slack: Float,
): TextLayout? {
    val start = position
    var cap = limit
    while (true) {
        val layout = nextLayout(width + slack, cap, true) ?: return null
        if (layout.visibleAdvance <= width) return layout
        val end = position
        position = start
        // A limit inside a word cuts it there, so pull it back to the start of the line's last word.
        val lastWordStart = text.substring(start, end).trimEnd().indexOfLast { it.isWhitespace() } + 1
        if (lastWordStart <= 0) return nextLayout(width, limit, true)
        cap = start + lastWordStart
    }
}

/** The size POI probes a font at to ask which characters it can show; any size answers the same. */
private const val FONT_PROBE_SIZE = 10.0

private val LINE_BREAKS = Regex("[\\r\\n]")

/** The end of the run of characters from [begin] that [font] can ([displayed]) or cannot show. */
private fun nextPart(font: Font, text: String, begin: Int, end: Int, displayed: Boolean): Int {
    var index = begin
    while (index < end) {
        val codePoint = text.codePointAt(index)
        if (font.canDisplay(codePoint) != displayed) break
        index += Character.charCount(codePoint)
    }
    return index
}

/** A font known only by its typeface — POI's `DrawFontInfo`, which is internal to its package. */
private class PlainFontInfo(private val name: String) : FontInfo {
    override fun getTypeface(): String = name
}
