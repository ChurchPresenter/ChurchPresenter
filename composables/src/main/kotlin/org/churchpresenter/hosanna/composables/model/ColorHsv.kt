package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import kotlin.math.abs
import org.churchpresenter.hosanna.composables.defaults.ColorFieldDefaults

private const val DEGREES = 360f

private const val SECTOR = 60f

private const val SECTORS = 6

private const val GREEN_SECTOR = 2

private const val BLUE_SECTOR = 4

@Immutable
internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

internal fun Color.toHsv(): Hsv {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> SECTOR * (((green - blue) / delta).mod(SECTORS.toFloat()))
        max == green -> SECTOR * ((blue - red) / delta + GREEN_SECTOR)
        else -> SECTOR * ((red - green) / delta + BLUE_SECTOR)
    }
    return Hsv(hue, if (max == 0f) 0f else delta / max, max)
}

internal fun Hsv.toColor(alpha: Float = 1f): Color {
    val chroma = value * saturation
    val sector = (hue.mod(DEGREES)) / SECTOR
    val x = chroma * (1 - abs(sector.mod(2f) - 1))
    val (r, g, b) = listOf(
        Triple(chroma, x, 0f),
        Triple(x, chroma, 0f),
        Triple(0f, chroma, x),
        Triple(0f, x, chroma),
        Triple(x, 0f, chroma),
        Triple(chroma, 0f, x),
    )[sector.toInt().coerceIn(0, SECTORS - 1)]
    val m = value - chroma
    return Color(r + m, g + m, b + m, alpha)
}

internal fun hueColor(hue: Float): Color = Hsv(hue, 1f, 1f).toColor()

internal val HueStops: List<Color> = (0..SECTORS).map { hueColor(it * SECTOR) }

internal fun areaHsv(hsv: Hsv, at: Offset, width: Float, height: Float): Hsv =
    hsv.copy(saturation = (at.x / width).coerceIn(0f, 1f), value = (1f - at.y / height).coerceIn(0f, 1f))

internal fun nudgeHsv(hsv: Hsv, key: Key, large: Boolean): Hsv? {
    val step = if (large) ColorFieldDefaults.KEY_STEP_LARGE else ColorFieldDefaults.KEY_STEP
    return when (key) {
        Key.DirectionLeft -> hsv.copy(saturation = (hsv.saturation - step).coerceIn(0f, 1f))
        Key.DirectionRight -> hsv.copy(saturation = (hsv.saturation + step).coerceIn(0f, 1f))
        Key.DirectionUp -> hsv.copy(value = (hsv.value + step).coerceIn(0f, 1f))
        Key.DirectionDown -> hsv.copy(value = (hsv.value - step).coerceIn(0f, 1f))
        else -> null
    }
}
