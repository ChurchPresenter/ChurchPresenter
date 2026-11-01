package org.churchpresenter.presentationengine.pptx

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.text.AttributedString
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A stretch of a line with effects, and the outline of the glyphs it draws them around. */
private class EffectSpan(val effects: TextEffects, val glyphs: Shape)

/**
 * Draws [layout] at ([x], [y]) the way PowerPoint does when its runs carry [TextEffects]: shadows,
 * then glows, behind the text; the fill; then outlines, centred on the glyph edges, over it.
 * A line with no effects is drawn exactly as before.
 */
internal fun drawWithEffects(graphics: Graphics2D, layout: TextLayout, text: AttributedString, x: Float, y: Float) {
    val spans = effectSpans(layout, text, x, y)
    if (spans.isEmpty()) {
        layout.draw(graphics, x, y)
        return
    }
    spans.forEach { span ->
        span.effects.shadow?.let { shadow ->
            paintBlurred(graphics, span.glyphs, shadow.color, spreadPt = 0.0, blurPt = shadow.blurPt,
                offsetXPt = shadow.offsetXPt, offsetYPt = shadow.offsetYPt)
        }
    }
    spans.forEach { span ->
        span.effects.glow?.let { glow ->
            paintBlurred(graphics, span.glyphs, glow.color, spreadPt = glow.radiusPt * GLOW_SPREAD,
                blurPt = glow.radiusPt * (1 - GLOW_SPREAD))
        }
    }
    layout.draw(graphics, x, y)
    spans.forEach { span ->
        span.effects.outline?.let { outline -> strokeOutline(graphics, span.glyphs, outline) }
    }
}

/** Each run of [text] that carries effects, with the outline of its glyphs as drawn at ([x], [y]). */
private fun effectSpans(layout: TextLayout, text: AttributedString, x: Float, y: Float): List<EffectSpan> {
    val iterator = text.getIterator(arrayOf(TextEffectsKey))
    val count = layout.characterCount
    val found = mutableListOf<Triple<Int, Int, TextEffects>>()
    iterator.first()
    while (iterator.index < iterator.endIndex) {
        val limit = iterator.getRunLimit(TextEffectsKey)
        (iterator.getAttribute(TextEffectsKey) as? TextEffects)?.let { effects ->
            val end = minOf(limit, count)
            if (iterator.index < end) found += Triple(iterator.index, end, effects)
        }
        iterator.index = limit
    }
    if (found.isEmpty()) return emptyList()
    val glyphs = layout.getOutline(AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble()))
    return found.map { (begin, end, effects) ->
        val shape = if (begin == 0 && end >= count) glyphs else glyphsBetween(layout, glyphs, begin until end, x, y)
        EffectSpan(effects, shape)
    }
}

/**
 * The part of the line's [glyphs] that the characters in [range] draw: the glyphs cut to the
 * horizontal extent of their selection, and left their full height so no ascender is lost.
 */
private fun glyphsBetween(layout: TextLayout, glyphs: Shape, range: IntRange, x: Float, y: Float): Shape {
    val selection = layout.getLogicalHighlightShape(range.first, range.last + 1).bounds2D
    val all = glyphs.bounds2D
    val top = minOf(all.minY, y + selection.minY)
    val bottom = maxOf(all.maxY, y + selection.maxY)
    val band = Rectangle2D.Double(x + selection.minX, top, selection.width, bottom - top)
    return Area(glyphs).apply { intersect(Area(band)) }
}

private fun strokeOutline(graphics: Graphics2D, glyphs: Shape, outline: TextOutline) {
    val oldStroke = graphics.stroke
    val oldPaint = graphics.paint
    graphics.stroke = BasicStroke(outline.widthPt.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    graphics.paint = outline.color
    graphics.draw(glyphs)
    graphics.stroke = oldStroke
    graphics.paint = oldPaint
}

/**
 * Paints [shape], grown by [spreadPt] and softened by [blurPt], in [color] — a glow, or with an
 * offset a shadow. Java2D has no blur, so it is drawn as a mask in device pixels, blurred there and
 * put back unscaled: blurring in user space and scaling up would smear it at 4K.
 */
private fun paintBlurred(
    graphics: Graphics2D,
    shape: Shape,
    color: Color,
    spreadPt: Double,
    blurPt: Double,
    offsetXPt: Double = 0.0,
    offsetYPt: Double = 0.0,
) {
    val toDevice = graphics.transform
    val scale = sqrt(abs(toDevice.determinant))
    if (scale == 0.0 || color.alpha == 0) return
    val back = try {
        toDevice.createInverse()
    } catch (_: NoninvertibleTransformException) {
        return
    }
    val moved = AffineTransform.getTranslateInstance(offsetXPt, offsetYPt).createTransformedShape(shape)
    val device = toDevice.createTransformedShape(moved)
    val spreadPx = spreadPt * scale
    val blurRadius = (blurPt * scale / BOX_PASSES).roundToInt()
    val margin = ceil(spreadPx).toInt() + blurRadius * BOX_PASSES + 2
    val bounds = device.bounds
    val left = bounds.x - margin
    val top = bounds.y - margin
    val width = bounds.width + 2 * margin
    val height = bounds.height + 2 * margin
    if (width <= 0 || height <= 0 || width.toLong() * height > MAX_EFFECT_PIXELS) return

    val mask = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    mask.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        translate(-left, -top)
        paint = Color.WHITE
        fill(device)
        if (spreadPx > 0) {
            stroke = BasicStroke((2 * spreadPx).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            draw(device)
        }
        dispose()
    }
    val pixels = mask.getRGB(0, 0, width, height, null, 0, width)
    val alpha = IntArray(pixels.size) { pixels[it] ushr ALPHA_SHIFT }
    repeat(BOX_PASSES) { boxBlur(alpha, width, height, blurRadius) }
    val rgb = color.rgb and RGB_MASK
    for (i in pixels.indices) {
        pixels[i] = (alpha[i] * color.alpha / CHANNEL_MAX shl ALPHA_SHIFT) or rgb
    }
    mask.setRGB(0, 0, width, height, pixels, 0, width)
    back.translate(left.toDouble(), top.toDouble())
    graphics.drawImage(mask, back, null)
}

/** One box blur of [radius] across, then down, [values] (a [width] × [height] grid), in place. */
internal fun boxBlur(values: IntArray, width: Int, height: Int, radius: Int) {
    if (radius <= 0) return
    val line = IntArray(maxOf(width, height))
    for (row in 0 until height) blurLine(values, row * width until (row + 1) * width, radius, line)
    for (column in 0 until width) blurLine(values, column until width * height step width, radius, line)
}

/** A running-sum box blur over the [cells] of [values], using [line] as scratch; outside counts as zero. */
private fun blurLine(values: IntArray, cells: IntProgression, radius: Int, line: IntArray) {
    val start = cells.first
    val step = cells.step
    val length = cells.count()
    for (i in 0 until length) line[i] = values[start + i * step]
    val window = 2 * radius + 1
    var sum = 0
    for (i in 0..minOf(radius, length - 1)) sum += line[i]
    for (i in 0 until length) {
        values[start + i * step] = sum / window
        val entering = i + radius + 1
        val leaving = i - radius
        if (entering < length) sum += line[entering]
        if (leaving >= 0) sum -= line[leaving]
    }
}

/** How much of a glow's radius is solid before it starts to fade, as PowerPoint draws one. */
private const val GLOW_SPREAD = 0.5

/** Three box blurs come within a few percent of a Gaussian. */
private const val BOX_PASSES = 3

/** A mask past this many pixels is not drawn rather than allocated: 4K is 8.3 million. */
private const val MAX_EFFECT_PIXELS = 16_000_000L

private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0xFFFFFF
private const val CHANNEL_MAX = 255
