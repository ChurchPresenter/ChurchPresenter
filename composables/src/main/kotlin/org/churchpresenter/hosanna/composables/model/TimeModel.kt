package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class TimePart { Hour, Minute, Period }

internal const val MINUTES_PER_DAY = 24 * 60

internal const val MINUTES_PER_HOUR = 60

internal const val HALF_DAY = 12 * 60

internal const val MINUTE_STEP = 5

private const val HOURS_12 = 12

private const val DIGITS_10 = 10

private const val MAX_MINUTE_TENS = 5

private const val MAX_HOUR_TENS_24 = 2

private const val MAX_HOUR_24 = 23

internal fun timeParts(use24Hour: Boolean): List<TimePart> =
    if (use24Hour) listOf(TimePart.Hour, TimePart.Minute) else TimePart.entries

internal fun hourText(minutes: Int, use24Hour: Boolean): String {
    val hour = minutes / MINUTES_PER_HOUR
    return if (use24Hour) {
        hour.toString().padStart(2, '0')
    } else {
        ((hour + HOURS_12 - 1) % HOURS_12 + 1).toString()
    }
}

internal fun minuteText(minutes: Int): String = (minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')

internal fun isPm(minutes: Int): Boolean = minutes >= HALF_DAY

internal fun wrapDay(minutes: Int): Int = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

internal fun stepTime(minutes: Int, part: TimePart, up: Boolean): Int = when (part) {
    TimePart.Hour -> wrapDay(minutes + if (up) MINUTES_PER_HOUR else -MINUTES_PER_HOUR)
    TimePart.Minute -> {
        val hourStart = minutes - minutes % MINUTES_PER_HOUR
        val minute = minutes % MINUTES_PER_HOUR
        val next = if (up) {
            (minute / MINUTE_STEP + 1) * MINUTE_STEP
        } else {
            ((minute + MINUTE_STEP - 1) / MINUTE_STEP - 1) * MINUTE_STEP
        }
        hourStart + (next % MINUTES_PER_HOUR + MINUTES_PER_HOUR) % MINUTES_PER_HOUR
    }
    TimePart.Period -> wrapDay(minutes + HALF_DAY)
}

internal fun setPeriod(minutes: Int, pm: Boolean): Int = when {
    pm && !isPm(minutes) -> minutes + HALF_DAY
    !pm && isPm(minutes) -> minutes - HALF_DAY
    else -> minutes
}

@Immutable
internal data class TypedTime(val minutes: Int, val pending: Int?, val advance: Boolean)

internal fun typeDigit(minutes: Int, part: TimePart, pending: Int?, digit: Int, use24Hour: Boolean): TypedTime {
    val minute = minutes % MINUTES_PER_HOUR
    val hour = minutes / MINUTES_PER_HOUR
    return when (part) {
        TimePart.Minute -> if (pending == null) {
            val done = digit > MAX_MINUTE_TENS
            TypedTime(hour * MINUTES_PER_HOUR + digit, if (done) null else digit, done)
        } else {
            TypedTime(hour * MINUTES_PER_HOUR + pending * DIGITS_10 + digit, null, true)
        }
        TimePart.Hour -> typeHour(minute, hour, pending, digit, use24Hour)
        TimePart.Period -> TypedTime(minutes, null, false)
    }
}

private fun typeHour(minute: Int, hour: Int, pending: Int?, digit: Int, use24Hour: Boolean): TypedTime {
    val pm = hour >= HOURS_12
    fun at(hour12or24: Int): Int = if (use24Hour) {
        hour12or24
    } else {
        hour12or24 % HOURS_12 + if (pm) HOURS_12 else 0
    }
    if (pending == null) {
        val canGrow = if (use24Hour) digit <= MAX_HOUR_TENS_24 else digit <= 1
        val shown = if (!use24Hour && digit == 0) HOURS_12 else digit
        return TypedTime(at(shown) * MINUTES_PER_HOUR + minute, if (canGrow) digit else null, !canGrow)
    }
    val combined = pending * DIGITS_10 + digit
    val limit = if (use24Hour) MAX_HOUR_24 else HOURS_12
    val valid = if (use24Hour) combined <= limit else combined in 1..limit
    val hourValue = if (valid) combined else digit
    return TypedTime(at(hourValue) * MINUTES_PER_HOUR + minute, null, true)
}

internal fun nextPart(parts: List<TimePart>, part: TimePart, step: Int): TimePart =
    parts[(parts.indexOf(part) + step).coerceIn(0, parts.lastIndex)]
