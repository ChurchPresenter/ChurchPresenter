package org.churchpresenter.presentationengine.pptx

import org.apache.poi.sl.draw.DrawPaint
import org.apache.poi.sl.draw.DrawShape
import org.apache.poi.sl.usermodel.PaintStyle.GradientPaint
import org.apache.poi.sl.usermodel.PlaceableShape
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Paint

private const val HALFWAY = 0.5f

/**
 * POI's paint, except that a linear gradient on a zero-width shape — a vertical line — is one flat
 * color halfway along the gradient, which is how PowerPoint draws it. POI draws the last stop.
 */
internal class SlidePaint(shape: PlaceableShape<*, *>) : DrawPaint(shape) {
    override fun createLinearGradientPaint(fill: GradientPaint, graphics: Graphics2D): Paint? {
        val zeroWidth = DrawShape.getAnchor(graphics, shape)?.width == 0.0
        val fractions = fill.gradientFractions
        val colors = fill.gradientColors.mapNotNull { applyColorTransform(it) }
        return if (zeroWidth && fractions.isNotEmpty() && colors.size == fractions.size) {
            colorHalfwayAlong(fractions, colors)
        } else {
            super.createLinearGradientPaint(fill, graphics)
        }
    }
}

private fun colorHalfwayAlong(fractions: FloatArray, colors: List<Color>): Color {
    val next = fractions.indexOfFirst { it >= HALFWAY }
    return when (next) {
        -1 -> colors.last()
        0 -> colors.first()
        else -> {
            val position = (HALFWAY - fractions[next - 1]) / (fractions[next] - fractions[next - 1])
            blend(colors[next - 1], colors[next], position)
        }
    }
}

private fun blend(from: Color, to: Color, position: Float): Color {
    fun channel(a: Int, b: Int) = (a + (b - a) * position).toInt()
    return Color(
        channel(from.red, to.red),
        channel(from.green, to.green),
        channel(from.blue, to.blue),
        channel(from.alpha, to.alpha),
    )
}
