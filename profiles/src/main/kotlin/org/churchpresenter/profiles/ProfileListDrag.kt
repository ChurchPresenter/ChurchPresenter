package org.churchpresenter.profiles

import org.churchpresenter.settings.OutputProfile

/*
 * Where a dragged profile lands, worked out from where each row of the list was laid out. Plain
 * arithmetic over measured positions, kept apart from the list that draws it.
 */

/**
 * The gap -- 0 above the first row up to the count below the last -- the dragged row's middle is
 * over, or null while it has not left its own place.
 */
internal fun dropIndexFor(
    profiles: List<OutputProfile>,
    id: String,
    offset: Float,
    tops: Map<String, Float>,
    heights: Map<String, Float>,
): Int? {
    val from = profiles.indexOfFirst { it.id == id }
    val top = tops[id] ?: return null
    val middle = top + offset + (heights[id] ?: 0f) / 2
    val gap = profiles.count { p -> (tops[p.id] ?: 0f) + (heights[p.id] ?: 0f) / 2 < middle }
    return gap.takeUnless { it == from || it == from + 1 }
}

/** The y the landing line is drawn at for [gap]: the top of the row below it, or the last row's bottom. */
internal fun dropLineTop(
    profiles: List<OutputProfile>,
    gap: Int,
    tops: Map<String, Float>,
    heights: Map<String, Float>,
): Float {
    val below = profiles.getOrNull(gap)
    return if (below != null) {
        (tops[below.id] ?: 0f)
    } else {
        val last = profiles.last()
        (tops[last.id] ?: 0f) + (heights[last.id] ?: 0f)
    }
}
