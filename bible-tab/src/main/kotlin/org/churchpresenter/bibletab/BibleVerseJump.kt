package org.churchpresenter.bibletab

import androidx.compose.foundation.lazy.LazyListLayoutInfo

/**
 * Where to jump the list so [selectedIndex] shows with the verse before it, or null while any part
 * of it is already on screen -- [scrollAheadAmount] handles those. Returns null before the first
 * layout, when nothing is visible yet.
 */
internal fun jumpTargetFor(layoutInfo: LazyListLayoutInfo, selectedIndex: Int): Int? {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty() || visible.any { it.index == selectedIndex }) return null
    return (selectedIndex - 1).coerceAtLeast(0)
}
