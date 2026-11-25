package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

internal sealed interface TimeKey {
    data object Open : TimeKey
    data object Close : TimeKey
    data class Move(val step: Int) : TimeKey
    data class Step(val up: Boolean) : TimeKey
    data class Digit(val value: Int) : TimeKey
    data class Period(val pm: Boolean) : TimeKey
    data object None : TimeKey
}

internal fun timeKey(event: KeyEvent, open: Boolean, use24Hour: Boolean): TimeKey {
    if (event.type != KeyEventType.KeyDown) return TimeKey.None
    val char = event.utf16CodePoint.toChar()
    return when {
        event.key == Key.DirectionDown && event.isAltPressed -> TimeKey.Open
        (event.key == Key.Escape || event.key == Key.Enter) && open -> TimeKey.Close
        event.key == Key.DirectionLeft -> TimeKey.Move(-1)
        event.key == Key.DirectionRight -> TimeKey.Move(1)
        event.key == Key.DirectionUp -> TimeKey.Step(up = true)
        event.key == Key.DirectionDown -> TimeKey.Step(up = false)
        char.isDigit() -> TimeKey.Digit(char.digitToInt())
        !use24Hour && char.equals('a', ignoreCase = true) -> TimeKey.Period(pm = false)
        !use24Hour && char.equals('p', ignoreCase = true) -> TimeKey.Period(pm = true)
        else -> TimeKey.None
    }
}
