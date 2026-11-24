package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp

enum class SortDirection { Ascending, Descending }

@Immutable
data class TableSort(val column: String, val direction: SortDirection)

@Immutable
class TableColumn(
    val key: String,
    val label: String,
    val width: Dp? = null,
    val numeric: Boolean = false,
    val secondary: Boolean = false,
    val sortable: Boolean = true,
)

@Immutable
class TableRow(val key: String, val cells: List<String>)

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
