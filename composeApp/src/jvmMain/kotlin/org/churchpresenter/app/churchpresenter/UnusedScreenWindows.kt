package org.churchpresenter.app.churchpresenter

// Which attached displays the output windows must leave alone: the monitors marked "Don't use".

/**
 * Whether the display at [index] is a monitor marked "Don't use" -- see
 * `ProjectionSettings.unusedScreens`. [screenKeys] is each attached display's `screenKey`, by device
 * index; an index past it, or a display with no geometry, is never unused.
 */
internal fun isUnusedScreenIndex(index: Int, screenKeys: List<String>, unusedScreens: Collection<String>): Boolean {
    val key = screenKeys.getOrNull(index) ?: return false
    return key.isNotEmpty() && key in unusedScreens
}

/**
 * The displays an output may be handed by position: [candidates] without the monitors marked unused.
 * Only the positional pick skips them; the slot count still includes them, so a row set to None on an
 * unused monitor keeps its place.
 */
internal fun usableScreenIndices(
    candidates: List<Int>,
    screenKeys: List<String>,
    unusedScreens: Collection<String>,
): List<Int> = candidates.filterNot { isUnusedScreenIndex(it, screenKeys, unusedScreens) }
