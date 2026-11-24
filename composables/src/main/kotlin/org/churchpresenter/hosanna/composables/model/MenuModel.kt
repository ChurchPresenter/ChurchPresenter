package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

/**
 * One line of a menu: an [Item] to pick, a [Header] over a group, or a [Divider] between groups.
 */
sealed interface MenuEntry {
    /**
     * A line that can be picked.
     *
     * @property key what [org.churchpresenter.hosanna.composables.field.Dropdown] reports when this is picked.
     * @property label the line's text.
     * @property supporting an optional second line.
     * @property count an optional count shown at the end of the line.
     * @property enabled whether the line can be picked.
 * @property checked draws a checkbox at the end, ticked or not, for a menu of things to show or hide;
 * null draws none.
 * @property icon an optional icon at the start.
     */
    @Immutable
    data class Item(
        val key: String,
        val label: String,
        val supporting: String? = null,
        val count: String? = null,
        val enabled: Boolean = true,
        val checked: Boolean? = null,
        val icon: Painter? = null,
    ) : MenuEntry

    /**
     * A title over a group of items.
     *
     * @property title the group's title.
     */
    @Immutable
    data class Header(val title: String) : MenuEntry

    /**
     * A line between groups of items.
     */
    data object Divider : MenuEntry
}

/**
 * Turns on the search field at the top of a menu.
 *
 * @property placeholder the empty field's hint.
 * @property clearContentDescription the screen-reader name of the clear button.
 * @property noMatches what the menu says when nothing matches.
 */
@Immutable
class MenuSearch(val placeholder: String, val clearContentDescription: String, val noMatches: String)

internal fun MenuEntry.isPickable(): Boolean = this is MenuEntry.Item && enabled

internal fun nextPickable(entries: List<MenuEntry>, from: Int, step: Int): Int {
    var index = from + step
    while (index in entries.indices) {
        if (entries[index].isPickable()) return index
        index += step
    }
    return from
}

internal fun firstMatch(entries: List<MenuEntry>, typed: Char): Int = entries.indexOfFirst {
    it is MenuEntry.Item && it.enabled && it.label.startsWith(typed, ignoreCase = true)
}

internal fun filterEntries(entries: List<MenuEntry>, query: String): List<MenuEntry> =
    if (query.isBlank()) {
        entries
    } else {
        entries.filter { it is MenuEntry.Item && it.label.contains(query.trim(), ignoreCase = true) }
    }

internal fun itemCount(entries: List<MenuEntry>): Int = entries.count { it is MenuEntry.Item }

internal fun ThemeColors.menuRowContainer(selected: Boolean, highlighted: Boolean): Color {
    val base = if (selected) primaryContainer else Color.Transparent
    return if (highlighted) onSurface.copy(alpha = HosannaStateAlpha.HOVER).compositeOver(base) else base
}

internal class MenuPositionProvider(
    private val gap: Int,
    private val alignEnd: Boolean = false,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val fitsBelow = below + popupContentSize.height <= windowSize.height
        val y = if (fitsBelow || above < 0) below else above
        val start = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val x = start.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        return IntOffset(x, y)
    }
}

internal enum class MenuKey { Up, Down, Pick, Close, None }

internal fun menuKey(event: KeyEvent): MenuKey = when {
    event.type != KeyEventType.KeyDown -> MenuKey.None
    event.key == Key.DirectionUp -> MenuKey.Up
    event.key == Key.DirectionDown -> MenuKey.Down
    event.key == Key.Enter || event.key == Key.NumPadEnter -> MenuKey.Pick
    event.key == Key.Escape -> MenuKey.Close
    else -> MenuKey.None
}
