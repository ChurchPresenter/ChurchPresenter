package org.churchpresenter.hosanna.composables.model

import androidx.compose.ui.input.key.Key

internal enum class SliderKey { Down, Up, Home, End, Reset }

internal fun sliderKey(key: Key, centered: Boolean): SliderKey? = when (key) {
    Key.DirectionLeft, Key.DirectionDown -> SliderKey.Down
    Key.DirectionRight, Key.DirectionUp -> SliderKey.Up
    Key.MoveHome -> SliderKey.Home
    Key.MoveEnd -> SliderKey.End
    Key.Zero, Key.NumPad0, Key.Delete, Key.Backspace -> if (centered) SliderKey.Reset else null
    else -> null
}
