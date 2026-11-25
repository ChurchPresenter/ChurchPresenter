package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp

/**
 * Which way a table column is sorted: [Ascending] or [Descending].
 */
enum class SortDirection { Ascending, Descending }

/**
 * The column a table is sorted by.
 *
 * @property column the column's key.
 * @property direction which way it is sorted.
 */
@Immutable
data class TableSort(val column: String, val direction: SortDirection)

/**
 * One column of a [org.churchpresenter.hosanna.composables.table.Table].
 *
 * @property key the column's key, reported when it is sorted or resized.
 * @property label the header text.
 * @property width a fixed width, which can be resized; null takes the rest of the row.
 * @property numeric right-aligns the header and cells.
 * @property secondary draws the cells in the quieter text color.
 * @property sortable whether a header click sorts by this column.
 */
@Immutable
class TableColumn(
    val key: String,
    val label: String,
    val width: Dp? = null,
    val numeric: Boolean = false,
    val secondary: Boolean = false,
    val sortable: Boolean = true,
)

/**
 * One row of a [org.churchpresenter.hosanna.composables.table.Table].
 *
 * @property key the row's key, reported when it is clicked, opened or checked.
 * @property cells the cell texts, in column order.
 */
@Immutable
class TableRow(val key: String, val cells: List<String>)

/**
 * Turns on a table's checkbox column.
 *
 * @property checked the keys of the checked rows.
 * @property onCheckedChange called with a row's key and its new checked state.
 * @property onAllChange called with true to check every row, false to clear them.
 * @property selectAllLabel the header checkbox's screen-reader name.
 */
@Immutable
class TableBulk(
    val checked: Set<String>,
    val onCheckedChange: (String, Boolean) -> Unit,
    val onAllChange: (Boolean) -> Unit,
    val selectAllLabel: String,
)

internal fun nextSort(current: TableSort?, column: String): TableSort = when {
    current?.column != column -> TableSort(column, SortDirection.Ascending)
    current.direction == SortDirection.Ascending -> TableSort(column, SortDirection.Descending)
    else -> TableSort(column, SortDirection.Ascending)
}

internal fun bulkState(checked: Set<String>, keys: List<String>): ToggleableState {
    val count = keys.count { it in checked }
    return when {
        count == 0 -> ToggleableState.Off
        count == keys.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}

internal fun resizedWidth(current: Dp, delta: Dp, min: Dp): Dp = maxOf(min, current + delta)
