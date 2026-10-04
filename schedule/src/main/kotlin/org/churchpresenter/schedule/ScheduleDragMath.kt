package org.churchpresenter.schedule

/** A visible schedule row's geometry for drag hit-testing: its list [index], top [offset] and [size] in px. */
internal data class DragItemGeometry(val index: Int, val offset: Int, val size: Int)

/** The result of a drag move: whether the cursor is over the delete zone, and the row index it points at. */
internal data class DragDropTarget(val overDeleteZone: Boolean, val targetIndex: Int?)

/**
 * Where a reorder drag currently points, from the cursor's vertical position. Extracted from
 * ScheduleTab's pointer loop so this off-by-one-prone hit-testing is tested apart from the gesture.
 *
 * If [cursorY] has entered the delete zone — the [deleteZonePx]-tall strip at the bottom of a
 * [listHeightPx]-tall list — the drop removes the card, so [DragDropTarget.overDeleteZone] is true and
 * no target index is computed (a zero list height disables the zone). Otherwise the target is the
 * first visible row whose vertical span `[offset, offset + size]` contains the cursor, or null when
 * the cursor is in a gap or past the ends — in which case the caller keeps the previous target.
 */
internal fun dragDropTarget(
    cursorY: Float,
    listHeightPx: Int,
    deleteZonePx: Float,
    visibleItems: List<DragItemGeometry>,
): DragDropTarget {
    if (listHeightPx > 0 && cursorY >= listHeightPx - deleteZonePx) {
        return DragDropTarget(overDeleteZone = true, targetIndex = null)
    }
    val target = visibleItems.firstOrNull { cursorY >= it.offset && cursorY <= it.offset + it.size }
    return DragDropTarget(overDeleteZone = false, targetIndex = target?.index)
}
