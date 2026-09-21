package org.churchpresenter.calendar.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Drag-to-reorder for a [androidx.compose.foundation.lazy.LazyColumn].
 *
 * The rows are moved **as the pointer crosses them** rather than on release, so the list under the
 * finger is always the list that will be kept — which is what makes a reorder feel like moving a
 * thing rather than aiming at a gap.
 *
 * Two details carry the whole thing:
 *
 * - **Rows are tracked by key, not index.** The dragged row's index changes the moment it swaps
 *   with a neighbour, so holding an index would mean chasing the row that replaced it. This is why
 *   the list's keys must be stable and unique per row — see
 *   [org.churchpresenter.calendar.model.withUniqueRowIds].
 * - **The offset is rebased after every swap.** A swap moves the row's laid-out position under the
 *   pointer; without rebasing, the row would visibly jump by its own height each time.
 *
 * Not every row of the list is a drop target: it ends with an "add" row, and the cues merged into
 * it are placed by the clock rather than by hand. [isTarget] says which keys a row can be dropped
 * on, and [onMove] is told the two keys -- the caller knows what position each stands for.
 */
class ReorderState(
    private val listState: LazyListState,
    private val isTarget: (key: Any) -> Boolean,
    private val onMove: (fromKey: Any, toKey: Any) -> Unit,
) {
    /** The key of the row being dragged, or null. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    private var offset by mutableFloatStateOf(0f)

    /** Where the dragged row's top sits relative to where the layout put it. */
    private var anchor = 0

    /** How far to translate [key]'s row while it is being dragged. */
    fun translationFor(key: Any): Float = if (key == draggingKey) offset else 0f

    fun isDragging(key: Any): Boolean = key == draggingKey

    fun start(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        anchor = info.offset
        offset = 0f
    }

    fun drag(deltaY: Float) {
        val key = draggingKey ?: return
        offset += deltaY
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val top = anchor + offset
        val centre = (top + info.size / 2f).toInt()
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.index != info.index &&
                isTarget(candidate.key) &&
                centre in candidate.offset..(candidate.offset + candidate.size)
        } ?: return

        onMove(key, target.key)
        // The row will be laid out where the target currently is. Keep `anchor + offset` at the
        // same screen position so nothing jumps under the pointer.
        anchor = target.offset
        offset = top - target.offset
    }

    fun end() {
        draggingKey = null
        offset = 0f
    }
}

/** A [ReorderState] that survives recomposition and always calls the latest [isTarget] and [onMove]. */
@Composable
fun rememberReorderState(
    listState: LazyListState,
    isTarget: (key: Any) -> Boolean,
    onMove: (fromKey: Any, toKey: Any) -> Unit,
): ReorderState {
    val currentIsTarget by rememberUpdatedState(isTarget)
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) {
        ReorderState(listState, { currentIsTarget(it) }, { from, to -> currentOnMove(from, to) })
    }
}
