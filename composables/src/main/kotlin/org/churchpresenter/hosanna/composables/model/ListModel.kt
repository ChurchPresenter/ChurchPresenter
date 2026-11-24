package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key

@Immutable
class NavigationItem(val key: String, val label: String)

internal enum class ListKey { Up, Down, Home, End, PageUp, PageDown }

internal fun listKey(key: Key): ListKey? = when (key) {
    Key.DirectionUp -> ListKey.Up
    Key.DirectionDown -> ListKey.Down
    Key.MoveHome -> ListKey.Home
    Key.MoveEnd -> ListKey.End
    Key.PageUp -> ListKey.PageUp
    Key.PageDown -> ListKey.PageDown
    else -> null
}

internal fun moveIndex(index: Int, key: ListKey, count: Int, page: Int): Int {
    if (count == 0) return -1
    val next = when (key) {
        ListKey.Up -> if (index < 0) count - 1 else index - 1
        ListKey.Down -> index + 1
        ListKey.Home -> 0
        ListKey.End -> count - 1
        ListKey.PageUp -> index - page
        ListKey.PageDown -> index + page
    }
    return next.coerceIn(0, count - 1)
}

internal fun jumpTarget(labels: List<String>, typed: String, from: Int): Int? {
    if (typed.isBlank()) return null
    val order = labels.indices.map { (from + it) % labels.size }
    return order.firstOrNull { labels[it].startsWith(typed, ignoreCase = true) }
}
