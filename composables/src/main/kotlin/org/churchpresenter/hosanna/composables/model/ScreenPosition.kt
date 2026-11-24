package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

enum class ScreenPosition {
    TopLeft,
    TopCenter,
    TopRight,
    MiddleLeft,
    Center,
    MiddleRight,
    BottomLeft,
    BottomCenter,
    BottomRight,
}

internal const val GRID = 3

internal fun ScreenPosition.row(): Int = ordinal / GRID

internal fun ScreenPosition.column(): Int = ordinal % GRID

internal fun positionAt(row: Int, column: Int): ScreenPosition =
    ScreenPosition.entries[row.coerceIn(0, GRID - 1) * GRID + column.coerceIn(0, GRID - 1)]

internal fun movePosition(position: ScreenPosition, event: KeyEvent): ScreenPosition? {
    if (event.type != KeyEventType.KeyDown) return null
    return when (event.key) {
        Key.DirectionLeft -> positionAt(position.row(), position.column() - 1)
        Key.DirectionRight -> positionAt(position.row(), position.column() + 1)
        Key.DirectionUp -> positionAt(position.row() - 1, position.column())
        Key.DirectionDown -> positionAt(position.row() + 1, position.column())
        Key.MoveHome -> ScreenPosition.TopLeft
        Key.MoveEnd -> ScreenPosition.BottomRight
        else -> null
    }
}
