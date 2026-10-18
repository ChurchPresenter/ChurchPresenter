package org.churchpresenter.presentationengine.pptx

import org.apache.poi.sl.draw.DrawFactory
import org.apache.poi.sl.draw.DrawFreeformShape
import org.apache.poi.sl.draw.DrawPictureShape
import org.apache.poi.sl.draw.DrawTextBox
import org.apache.poi.sl.draw.DrawTextFragment
import org.apache.poi.sl.draw.DrawTextShape
import org.apache.poi.sl.usermodel.FreeformShape
import org.apache.poi.sl.usermodel.PictureShape
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.sl.usermodel.TextBox
import org.apache.poi.sl.usermodel.TextShape
import java.awt.Graphics2D
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.Path2D
import java.text.AttributedString
import java.text.CharacterIterator

/**
 * POI's draw factory with the slide-drawing corrections this engine needs.
 *
 * - A picture cropped to a shape is drawn inside that shape. PowerPoint lets a picture carry any
 *   geometry (`<a:custGeom>`, or a preset other than `rect` — "Crop to Shape", and what Designer
 *   writes for its angled photo layouts), and draws the image only inside it. POI's
 *   `DrawPictureShape.drawContent` draws the image over its whole anchor and ignores the geometry,
 *   so the photo covers whatever the shape was cut away to show.
 * - Paragraphs stack one line height apart. POI starts each paragraph after the first from the
 *   bottom of the previous line's descender rather than its baseline, so every paragraph break adds
 *   an extra descent — a fifth of the font size for Arial. Lyrics and verse slides put every line in
 *   a paragraph of its own, so the text grows a fifth taller than PowerPoint's, spills out of its
 *   box, and a bottom-anchored box pushes it up into the title above.
 * - A justified line is drawn justified (see [PowerPointLine.draw]).
 * - A line is 1.2 times its largest font size tall, as PowerPoint draws single spacing whatever the
 *   font. POI takes the height from the font's own metrics, which for Arial and Times New Roman is
 *   1.15 — so a verse slide's lines sit closer together than on the screen it was made on.
 *
 * Installed per graphics as the [org.apache.poi.sl.draw.Drawable.DRAW_FACTORY] hint, which
 * `DrawFactory.getInstance` reads before anything else.
 */
internal object SlideDrawFactory : DrawFactory() {
    override fun getDrawable(shape: PictureShape<*, *>): DrawPictureShape = ShapedPictureShape(shape)

    override fun getDrawable(shape: TextBox<*, *>): DrawTextBox = StackedTextBox(shape)

    override fun getDrawable(shape: FreeformShape<*, *>): DrawFreeformShape = StackedFreeformShape(shape)

    override fun getDrawable(shape: TextShape<*, *>): DrawTextShape = StackedTextShape(shape)

    override fun getTextFragment(layout: TextLayout, str: AttributedString): DrawTextFragment =
        PowerPointLine(layout, str)
}

private class ShapedPictureShape(private val picture: PictureShape<*, *>) : DrawPictureShape(picture) {

    override fun drawContent(graphics: Graphics2D) {
        // A plain rectangle is the anchor itself: clipping to it would change nothing but the
        // edge pixels, so those pictures keep POI's own path exactly.
        val outline = if (picture.shapeType == ShapeType.RECT) null else outlineOf(graphics)
        if (outline == null) {
            super.drawContent(graphics)
            return
        }
        val oldClip = graphics.clip
        graphics.clip(outline)
        try {
            super.drawContent(graphics)
        } finally {
            graphics.clip = oldClip
        }
    }

    /** The filled parts of the picture's geometry, in the graphics' user space; null when it has none. */
    private fun outlineOf(graphics: Graphics2D): Path2D? {
        val filled = runCatching { computeOutlines(graphics) }.getOrNull()
            ?.filter { it.path.isFilled }
            .orEmpty()
        if (filled.isEmpty()) return null
        val area = Path2D.Double()
        filled.forEach { area.append(it.outline, false) }
        return area
    }
}

/** POI reports a percentage spacing as its percent value, 120.0 for 120%. */
private const val PERCENT = 100.0

/** PowerPoint's single line height, as a multiple of the line's largest font size. */
private const val SINGLE_LINE_HEIGHT = 1.2

/**
 * A line whose height is [SINGLE_LINE_HEIGHT] times its largest font size.
 *
 * POI's line height is ascent + descent + [getLeading], so the leading is what absorbs the
 * difference. PowerPoint puts that space above the glyphs, with the descent at the foot of the line,
 * so the line draws its glyphs one leading below the position it is given — which is also why
 * [stackParagraphs] adds no leading of its own above the first line. A line that carries no size
 * (an empty one) keeps POI's leading.
 */
private class PowerPointLine(layout: TextLayout, str: AttributedString) : DrawTextFragment(layout, str) {
    private val leading: Float? = largestFontSize(str)?.let { size ->
        (size * SINGLE_LINE_HEIGHT - layout.ascent - layout.descent).toFloat()
    }

    private var drawX = 0.0
    private var drawY = 0.0

    override fun getLeading(): Float = leading ?: super.getLeading()

    override fun setPosition(x: Double, y: Double) {
        drawX = x
        drawY = y + getLeading()
        super.setPosition(drawX, drawY)
    }

    /**
     * Draws the laid-out line itself. POI draws the line's attributed text with `drawString`,
     * which lays it out afresh and so drops the justification `getJustifiedLayout` gave it — a
     * justified paragraph came out ragged. Drawing the layout draws exactly what was measured.
     */
    override fun draw(graphics: Graphics2D) {
        layout.draw(graphics, drawX.toFloat(), (drawY + layout.ascent).toFloat())
    }
}

private fun largestFontSize(str: AttributedString): Double? {
    val text = str.iterator
    var largest: Double? = null
    var c = text.first()
    while (c != CharacterIterator.DONE) {
        val size = (text.getAttribute(TextAttribute.SIZE) as? Number)?.toDouble()
        if (size != null && size > (largest ?: 0.0)) largest = size
        text.index = text.runLimit
        c = text.current()
    }
    return largest
}

private class StackedTextShape(private val text: TextShape<*, *>) : DrawTextShape(text) {
    override fun drawParagraphs(graphics: Graphics2D, x: Double, y: Double): Double =
        stackParagraphs(text, graphics, x, y)
}

private class StackedTextBox(private val text: TextBox<*, *>) : DrawTextBox(text) {
    override fun drawParagraphs(graphics: Graphics2D, x: Double, y: Double): Double =
        stackParagraphs(text, graphics, x, y)
}

private class StackedFreeformShape(private val text: FreeformShape<*, *>) : DrawFreeformShape(text) {
    override fun drawParagraphs(graphics: Graphics2D, x: Double, y: Double): Double =
        stackParagraphs(text, graphics, x, y)
}

/**
 * POI's `DrawTextShape.drawParagraphs` (Apache POI 5.5.1, Apache License 2.0) with one change:
 * before each paragraph after the first, step back by the previous paragraph's last descent.
 *
 * `DrawTextParagraph.draw` walks from one line's baseline to the next by subtracting the previous
 * line's descent, but for a paragraph's first line there is no previous line in that paragraph, so
 * it subtracts nothing — while the pen it was handed already sits below the previous paragraph's
 * descender. Moving the paragraph up by that descent puts its first baseline one line height
 * (times the line spacing) below the previous one, as it is inside a paragraph.
 */
private fun stackParagraphs(shape: TextShape<*, *>, graphics: Graphics2D, x: Double, y0: Double): Double {
    var y = y0
    var previousDescent: Double? = null
    var autoNumberIndex = 0
    val paragraphs = shape.iterator()
    while (paragraphs.hasNext()) {
        val paragraph = paragraphs.next()
        val drawable = KernedParagraph(paragraph)
        val bullets = paragraph.bulletStyle
        if (bullets?.autoNumberingScheme == null) {
            autoNumberIndex = -1
        } else {
            val startAt = bullets.autoNumberingStartAt ?: 1
            if (startAt > autoNumberIndex) autoNumberIndex = startAt
        }
        drawable.setAutoNumberingIdx(autoNumberIndex)
        drawable.layOut(graphics)

        if (previousDescent != null) {
            y += spacingPoints(paragraph.spaceBefore, drawable.firstLineHeight.toDouble())
            y -= previousDescent
        }

        drawable.setPosition(x, y)
        drawable.markFirst(previousDescent == null)
        drawable.draw(graphics)
        y += drawable.y
        previousDescent = drawable.lastLineDescent

        if (paragraphs.hasNext()) {
            y += spacingPoints(paragraph.spaceAfter, drawable.lastLineHeight.toDouble())
        }
        autoNumberIndex++
    }
    return y - y0
}

/** OOXML paragraph spacing: positive is a percentage of [lineHeight], negative is absolute points. */
private fun spacingPoints(spacing: Double?, lineHeight: Double): Double {
    val value = spacing ?: 0.0
    return if (value > 0) value / PERCENT * lineHeight else -value
}
