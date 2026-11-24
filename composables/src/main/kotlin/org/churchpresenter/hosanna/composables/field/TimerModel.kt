package org.churchpresenter.hosanna.composables.field

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class TimerUnit { Hours, Minutes, Seconds }

@Immutable
class TimerSpinnerLabels(
    val hours: String,
    val minutes: String,
    val seconds: String,
    val increase: String,
    val decrease: String,
)

@Immutable
data class TimerPreset(val label: String, val seconds: Int)

internal const val SECONDS_PER_MINUTE = 60
internal const val SECONDS_PER_HOUR = 3600
internal const val MAX_HOURS = 99
internal const val MAX_TIMER_SECONDS = MAX_HOURS * SECONDS_PER_HOUR + 59 * SECONDS_PER_MINUTE + 59
internal const val FAST_SECONDS_STEP = 5
private const val MAX_MINUTES_OR_SECONDS = 59

internal fun unitSeconds(unit: TimerUnit): Int = when (unit) {
    TimerUnit.Hours -> SECONDS_PER_HOUR
    TimerUnit.Minutes -> SECONDS_PER_MINUTE
    TimerUnit.Seconds -> 1
}

internal fun unitValue(total: Int, unit: TimerUnit): Int = when (unit) {
    TimerUnit.Hours -> total / SECONDS_PER_HOUR
    TimerUnit.Minutes -> total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    TimerUnit.Seconds -> total % SECONDS_PER_MINUTE
}

internal fun unitText(total: Int, unit: TimerUnit): String = unitValue(total, unit).toString().padStart(2, '0')

internal fun canStep(total: Int, unit: TimerUnit, up: Boolean): Boolean =
    if (up) total + unitSeconds(unit) <= MAX_TIMER_SECONDS else total - unitSeconds(unit) >= 0

internal fun stepTimer(total: Int, unit: TimerUnit, up: Boolean, fast: Boolean = false): Int {
    val size = unitSeconds(unit) * if (fast && unit == TimerUnit.Seconds) FAST_SECONDS_STEP else 1
    return (total + if (up) size else -size).coerceIn(0, MAX_TIMER_SECONDS)
}

internal fun setUnit(total: Int, unit: TimerUnit, value: Int): Int {
    val cap = if (unit == TimerUnit.Hours) MAX_HOURS else MAX_MINUTES_OR_SECONDS
    val current = unitValue(total, unit)
    return total + (value.coerceIn(0, cap) - current) * unitSeconds(unit)
}

internal fun parseUnit(text: String): Int? = text.filter { it.isDigit() }.take(2).toIntOrNull()

internal object TimerSpinnerDefaults {
    val Width: Dp = 84.dp
    val ButtonHeight: Dp = 36.dp
    val NumberHeight: Dp = 60.dp
    val Icon: Dp = 16.dp
    val LabelGap: Dp = 6.dp
    val Dot: Dp = 5.dp
    val DotGap: Dp = 12.dp
    val ColonPadding: Dp = 14.dp
    val PresetGap: Dp = 12.dp
    val ChipGap: Dp = 6.dp
    const val HOLD_DELAY_MILLIS: Long = 400
    const val REPEAT_MILLIS: Long = 80
    const val FAST_AFTER_MILLIS: Long = 2000
}
