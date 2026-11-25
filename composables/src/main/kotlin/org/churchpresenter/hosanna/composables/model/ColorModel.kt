package org.churchpresenter.hosanna.composables.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.roundToInt
import org.churchpresenter.hosanna.composables.defaults.ColorFieldDefaults

private const val CHANNEL_MAX = 255

private const val PERCENT = 100

private const val HEX_RADIX = 16

private const val SHORT_HEX = 3

private const val RGB_HEX = 6

private const val RGBA_HEX = 8

private const val RGB_CHANNELS = 3

private const val RED = 0

private const val GREEN = 1

private const val BLUE = 2

private const val ALPHA = 3

internal fun parseColor(text: String): Color? {
    val raw = text.trim().lowercase()
    return when {
        raw.startsWith("rgb") -> parseRgb(raw)
        else -> parseHex(raw.removePrefix("#"))
    }
}

private fun parseHex(hex: String): Color? {
    if (hex.any { it.digitToIntOrNull(HEX_RADIX) == null }) return null
    val full = when (hex.length) {
        SHORT_HEX -> hex.map { "$it$it" }.joinToString("") + "ff"
        RGB_HEX -> hex + "ff"
        RGBA_HEX -> hex
        else -> return null
    }
    val channels = full.chunked(2).map { it.toInt(HEX_RADIX) }
    return Color(channels[RED], channels[GREEN], channels[BLUE], channels[ALPHA])
}

private fun parseRgb(raw: String): Color? {
    val parts = raw.substringAfter('(', "").substringBefore(')', "").split(',').map { it.trim() }
    val rgb = parts.take(RGB_CHANNELS).mapNotNull { part -> part.toIntOrNull()?.takeIf { it in 0..CHANNEL_MAX } }
    val alpha = if (parts.size == RGB_CHANNELS) 1f else parts.getOrNull(RGB_CHANNELS)?.toFloatOrNull()
    val shaped = parts.size in RGB_CHANNELS..RGB_CHANNELS + 1 && rgb.size == RGB_CHANNELS
    if (!shaped || alpha == null || alpha !in 0f..1f) return null
    return Color(rgb[RED], rgb[GREEN], rgb[BLUE], (alpha * CHANNEL_MAX).roundToInt())
}

internal fun hexText(color: Color): String {
    fun channel(value: Float) = (value * CHANNEL_MAX).roundToInt().toString(HEX_RADIX).padStart(2, '0')
    return "#" + (channel(color.red) + channel(color.green) + channel(color.blue)).uppercase()
}

internal fun opacityPercent(color: Color): Int = (color.alpha * PERCENT).roundToInt()

internal fun opacityText(color: Color): String? = opacityPercent(color).takeIf { it < PERCENT }?.let { "$it%" }

internal fun withOpacityPercent(color: Color, percent: Int): Color =
    color.copy(alpha = percent.coerceIn(0, PERCENT) / PERCENT.toFloat())

internal fun sameRgb(a: Color, b: Color?): Boolean = b != null && hexText(a) == hexText(b)

internal fun DrawScope.drawCheckerboard(light: Color, dark: Color) {
    val cell = ColorFieldDefaults.Check.toPx()
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = 0
        while (x < size.width) {
            drawRect(
                if ((row + col) % 2 == 0) light else dark,
                Offset(x, y),
                Size(cell, cell),
            )
            x += cell
            col++
        }
        y += cell
        row++
    }
}
