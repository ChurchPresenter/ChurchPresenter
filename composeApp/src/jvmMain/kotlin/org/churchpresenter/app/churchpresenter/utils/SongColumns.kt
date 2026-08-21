package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.utils.Constants
/**
 * Table-column configuration for the song list — which columns exist, in what order, how a drag
 * reorders them, and which sort key each drives. Pure list/string logic extracted from SongsTab so
 * the table-config decisions can be tested without the Compose table around them.
 */

/**
 * The columns available given how many songbooks are loaded and whether the add-to-schedule action is
 * wired: `"songbook"` only appears with more than one book, `"add_to_schedule"` only when the callback
 * is present.
 */
internal fun availableSongColumns(songbookCount: Int, hasAddToSchedule: Boolean): List<String> = buildList {
    add("number"); add("title")
    if (songbookCount > 1) add("songbook")
    add("tune")
    add("play_count")
    add("author")
    add("composer")
    if (hasAddToSchedule) add("add_to_schedule")
    add("favorites")
}

/**
 * Reconciles a [saved] column order against the currently [available] columns: keeps the saved order
 * for columns that still exist, drops ones that no longer do, and appends any newly-available columns
 * at the end. Falls back to [available] as-is when nothing was saved.
 */
internal fun mergeColumnOrder(saved: List<String>, available: List<String>): List<String> {
    if (saved.isEmpty()) return available
    val filtered = saved.filter { it in available }
    val missing = available.filter { it !in filtered }
    return filtered + missing
}

/**
 * Moves [colId] to sit at [targetId]'s position within [order]. Returns [order] unchanged (same
 * reference) when either id is absent or they already share a position, so callers can detect a no-op
 * with `!==`.
 */
internal fun moveColumn(order: List<String>, colId: String, targetId: String): List<String> {
    val fromIdx = order.indexOf(colId)
    val toIdx = order.indexOf(targetId)
    if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) return order
    val mutable = order.toMutableList()
    mutable.removeAt(fromIdx)
    mutable.add(toIdx.coerceIn(0, mutable.size), colId)
    return mutable
}

/** The sort key a column header drives, or `""` for a column that isn't sortable. */
internal fun songColumnSortKey(colId: String): String = when (colId) {
    "number" -> Constants.SORT_NUMBER
    "title" -> Constants.SORT_TITLE
    "songbook" -> Constants.SORT_SONGBOOK
    "tune" -> Constants.SORT_TUNE
    "play_count" -> Constants.SORT_PLAY_COUNT
    "favorites" -> Constants.SORT_FAVORITES
    "author" -> Constants.SORT_AUTHOR
    "composer" -> Constants.SORT_COMPOSER
    else -> ""
}

/**
 * The new index for a column being drag-reordered. Starting from [draggedId]'s slot in [visibleCols],
 * walks outward in the drag direction ([accumulatedDragPx] > 0 is rightward), advancing past each
 * neighbour once the drag has covered half of that column's width plus a handle. [columnWidthPx] gives
 * a column's pixel width and [handleWidthPx] the drag-handle width. Returns 0 if [draggedId] is absent.
 */
internal fun draggedColumnIndex(
    draggedId: String,
    accumulatedDragPx: Float,
    visibleCols: List<String>,
    columnWidthPx: (String) -> Float,
    handleWidthPx: Float,
): Int {
    val currentIdx = visibleCols.indexOf(draggedId)
    if (currentIdx < 0) return 0
    var remaining = accumulatedDragPx
    var newIdx = currentIdx
    if (remaining > 0f) {
        var i = currentIdx + 1
        while (i < visibleCols.size) {
            val w = columnWidthPx(visibleCols[i]) + handleWidthPx
            if (remaining >= w / 2f) { newIdx = i; remaining -= w } else break
            i++
        }
    } else {
        var i = currentIdx - 1
        while (i >= 0) {
            val w = columnWidthPx(visibleCols[i]) + handleWidthPx
            if (-remaining >= w / 2f) { newIdx = i; remaining += w } else break
            i--
        }
    }
    return newIdx
}
