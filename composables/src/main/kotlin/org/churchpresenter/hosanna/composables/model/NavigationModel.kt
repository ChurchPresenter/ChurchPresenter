package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter

enum class TabStyle { IconAndText, Text, Icon }

@Immutable
class NavigationTab(val key: String, val label: String, val icon: Painter, val badge: String? = null)

internal fun fittedTabStyle(requested: TabStyle, requestedWidth: Float, available: Float): TabStyle =
    if (requested == TabStyle.Icon || requestedWidth <= available) requested else TabStyle.Icon

internal fun tabsScroll(style: TabStyle, iconWidth: Float, available: Float): Boolean =
    style == TabStyle.Icon && iconWidth > available

internal fun neighbourTab(keys: List<String>, current: String, forward: Boolean): String? {
    val index = keys.indexOf(current)
    if (index < 0 || keys.isEmpty()) return null
    val step = if (forward) 1 else keys.size - 1
    return keys[(index + step) % keys.size]
}
