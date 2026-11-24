package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter

/**
 * How top-navigation tabs are drawn: [IconAndText], [Text] or [Icon].
 */
enum class TabStyle { IconAndText, Text, Icon }

/**
 * One tab of [org.churchpresenter.hosanna.composables.navigation.TopNavigationBar].
 *
 * @property key what the bar reports when this tab is picked.
 * @property label the tab's name, shown as text or in its tooltip.
 * @property icon the tab's icon.
 * @property badge an optional count shown on the tab.
 */
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
