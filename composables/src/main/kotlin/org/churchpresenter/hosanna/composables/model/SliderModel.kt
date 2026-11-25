package org.churchpresenter.hosanna.composables.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong
import org.churchpresenter.hosanna.composables.defaults.SliderDefaults

internal fun sliderOrigin(range: NumberRange): Double =
    if (range.min < 0 && range.max > 0) 0.0 else (range.min + range.max) / 2

internal fun sliderFraction(value: Double, range: NumberRange): Float =
    if (range.max <= range.min) 0f else ((value - range.min) / (range.max - range.min)).coerceIn(0.0, 1.0).toFloat()

internal fun sliderValueAt(fraction: Float, range: NumberRange): Double {
    val raw = range.min + fraction.coerceIn(0f, 1f) * (range.max - range.min)
    val steps = ((raw - range.min) / range.step).roundToLong()
    val snapped = clampNumber(range.min + steps * range.step, range)
    return BigDecimal.valueOf(snapped).setScale(range.decimals, RoundingMode.HALF_UP).toDouble()
}

internal fun snapToOrigin(value: Double, range: NumberRange): Double {
    val origin = sliderOrigin(range)
    return if (abs(value - origin) <= (range.max - range.min) * SliderDefaults.ORIGIN_SNAP) origin else value
}

internal fun sliderStepDots(range: NumberRange): List<Float> {
    val count = ((range.max - range.min) / range.step).roundToLong()
    if (count < 1 || count > SliderDefaults.MAX_DOTS) return emptyList()
    return (0..count).map { it.toFloat() / count }
}

internal fun applySliderKey(key: SliderKey, big: Boolean, value: Double, range: NumberRange): Double = when (key) {
    SliderKey.Down -> stepNumber(value, range, up = false, big = big)
    SliderKey.Up -> stepNumber(value, range, up = true, big = big)
    SliderKey.Home -> range.min
    SliderKey.End -> range.max
    SliderKey.Reset -> sliderOrigin(range)
}

internal fun signedText(value: Double, decimals: Int): String {
    val text = formatNumber(abs(value), decimals)
    return when {
        value > 0 -> "+$text"
        value < 0 -> "−$text"
        else -> text
    }
}
