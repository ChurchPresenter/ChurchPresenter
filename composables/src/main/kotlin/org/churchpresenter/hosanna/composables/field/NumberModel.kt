package org.churchpresenter.hosanna.composables.field

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

@Immutable
class NumberFieldActions(val increase: String, val decrease: String)

@Immutable
data class NumberRange(val min: Double, val max: Double, val step: Double = 1.0, val decimals: Int = 0)

internal object NumberFieldDefaults {
    val ArrowWidth: Dp = 20.dp
    val ArrowHeight: Dp = 16.dp
    val Arrow: Dp = 10.dp
    val ArrowInset: Dp = 4.dp
    val UnitWidth: Dp = 76.dp
    val UnitGap: Dp = 4.dp
    const val BIG_STEP: Int = 10
}

internal fun formatNumber(value: Double, decimals: Int): String =
    BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString()

internal fun parseNumber(text: String): Double? = text.trim().toDoubleOrNull()

internal fun inRange(value: Double, range: NumberRange): Boolean = value >= range.min && value <= range.max

internal fun clampNumber(value: Double, range: NumberRange): Double = value.coerceIn(range.min, range.max)

internal fun canStepNumber(value: Double, range: NumberRange, up: Boolean): Boolean =
    if (up) value < range.max else value > range.min

internal fun stepNumber(value: Double, range: NumberRange, up: Boolean, big: Boolean = false): Double {
    val size = range.step * if (big) NumberFieldDefaults.BIG_STEP else 1
    val next = clampNumber(value + if (up) size else -size, range)
    return BigDecimal.valueOf(next).setScale(range.decimals, RoundingMode.HALF_UP).toDouble()
}

internal fun filterNumberInput(text: String, range: NumberRange): String {
    val builder = StringBuilder()
    text.forEachIndexed { index, char ->
        val allowed = when {
            char.isDigit() -> true
            char == '-' -> index == 0 && range.min < 0
            char == '.' -> range.decimals > 0 && '.' !in builder
            else -> false
        }
        if (allowed) builder.append(char)
    }
    return builder.toString()
}
