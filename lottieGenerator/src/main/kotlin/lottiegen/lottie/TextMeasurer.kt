package lottiegen.lottie

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import kotlin.math.ceil

data class TextSize(val width: Int, val height: Int)

object TextMeasurer {

    private val frc = FontRenderContext(AffineTransform(), true, true)

    fun measure(
        text: String,
        fontFamily: String,
        fontSizePx: Float,
        fontWeight: Int,
        transform: String
    ): TextSize {
        val displayText = if (transform == "uppercase") text.uppercase() else text
        val awtStyle = if (fontWeight >= 700) Font.BOLD else Font.PLAIN
        val font = FontRegistry.getFont(fontFamily, awtStyle, fontSizePx)

        val metrics = font.getLineMetrics(displayText, frc)
        val bounds = font.getStringBounds(displayText, frc)

        val w = ceil(bounds.width).toInt() + 4
        val h = ceil(metrics.ascent + metrics.descent).toInt() + 4
        return TextSize(w, h)
    }
}
