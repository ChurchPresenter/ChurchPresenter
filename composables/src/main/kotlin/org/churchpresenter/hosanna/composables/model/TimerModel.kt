package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable

enum class TimerUnit { Hours, Minutes, Seconds }

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
