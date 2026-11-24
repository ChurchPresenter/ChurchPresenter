package org.churchpresenter.hosanna.composables.table

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import org.churchpresenter.hosanna.composables.defaults.ListDefaults
import org.churchpresenter.hosanna.composables.defaults.TableDefaults
import org.churchpresenter.hosanna.composables.model.TableBulk
import org.churchpresenter.hosanna.composables.model.TableColumn
import org.churchpresenter.hosanna.composables.model.TableRow
import org.churchpresenter.hosanna.composables.model.TableSort
import org.churchpresenter.hosanna.composables.model.jumpTarget
import org.churchpresenter.hosanna.composables.model.listKey
import org.churchpresenter.hosanna.composables.model.moveIndex

@Composable
fun Table(
    columns: List<TableColumn>,
    rows: List<TableRow>,
    modifier: Modifier = Modifier,
    selectedKeys: Set<String> = emptySet(),
    onRowClick: (String) -> Unit = {},
    onRowOpen: (String) -> Unit = {},
    sort: TableSort? = null,
    onSortChange: (TableSort) -> Unit = {},
    onColumnWidthChange: (String, Dp) -> Unit = { _, _ -> },
    compact: Boolean = false,
    bulk: TableBulk? = null,
    loading: Boolean = false,
    rowInteractionSources: Map<String, MutableInteractionSource> = emptyMap(),
    keepActions: Set<String> = emptySet(),
    actionsWidth: Dp = TableDefaults.MinColumn,
    rowActions: (@Composable RowScope.(String) -> Unit)? = null,
    empty: (@Composable () -> Unit)? = null,
) {
    val widths = remember(columns) {
        mutableStateMapOf<String, Dp>().apply {
            columns.forEach { column -> column.width?.let { put(column.key, it) } }
        }
    }
    val layout = TableLayout(columns, widths, bulk != null, if (rowActions != null) actionsWidth else null)
    Column(modifier) {
        TableHeader(
            layout = layout,
            rows = rows,
            sort = sort,
            onSortChange = onSortChange,
            bulk = bulk,
            onResize = { key, width ->
                widths[key] = width
                onColumnWidthChange(key, width)
            },
        )
        when {
            loading -> TableSkeleton(layout)
            rows.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(TableDefaults.EmptyPadding),
                contentAlignment = Alignment.Center,
            ) { empty?.invoke() }
            else -> TableBody(
                layout, rows, selectedKeys, onRowClick, onRowOpen, compact, bulk,
                rowInteractionSources, keepActions, rowActions,
            )
        }
    }
}

@Composable
private fun TableBody(
    layout: TableLayout,
    rows: List<TableRow>,
    selectedKeys: Set<String>,
    onRowClick: (String) -> Unit,
    onRowOpen: (String) -> Unit,
    compact: Boolean,
    bulk: TableBulk?,
    sources: Map<String, MutableInteractionSource>,
    keepActions: Set<String>,
    rowActions: (@Composable RowScope.(String) -> Unit)?,
) {
    val state = rememberLazyListState()
    var current by remember { mutableStateOf(selectedKeys.firstOrNull()) }
    var typed by remember { mutableStateOf("") }
    val keys by rememberUpdatedState(rows.map { it.key })
    val names by rememberUpdatedState(rows.map { layout.nameOf(it) })
    val click by rememberUpdatedState(onRowClick)
    val open by rememberUpdatedState(onRowOpen)
    val currentBulk by rememberUpdatedState(bulk)
    LaunchedEffect(typed) {
        if (typed.isNotEmpty()) {
            delay(ListDefaults.TYPE_RESET_MILLIS)
            typed = ""
        }
    }
    fun moveTo(index: Int?) {
        val key = index?.let { keys.getOrNull(it) } ?: return
        current = key
        click(key)
    }
    LaunchedEffect(current) {
        val index = keys.indexOf(current)
        val shown = state.layoutInfo.visibleItemsInfo.any { it.index == index && it.offset >= 0 }
        if (index >= 0 && !shown) state.scrollToItem(index)
    }
    LazyColumn(
        state = state,
        modifier = Modifier
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val index = keys.indexOf(current)
                val move = listKey(event.key)
                when {
                    move != null -> moveTo(moveIndex(index, move, keys.size, ListDefaults.PAGE))
                    event.key == Key.Enter -> current?.let(open)
                    event.key == Key.Spacebar && currentBulk != null ->
                        current?.let { key -> currentBulk?.let { it.onCheckedChange(key, key !in it.checked) } }
                    event.isSelectAll() -> currentBulk?.onAllChange(true) ?: return@onPreviewKeyEvent false
                    event.utf16CodePoint > ' '.code && !event.isCtrlPressed && !event.isMetaPressed -> {
                        typed += event.utf16CodePoint.toChar()
                        moveTo(jumpTarget(names, typed, maxOf(index, 0)))
                    }
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        items(rows, key = { it.key }) { row ->
            TableRowItem(
                row = row,
                layout = layout,
                selected = row.key in selectedKeys,
                checked = bulk?.let { row.key in it.checked },
                onCheckedChange = bulk?.let { b -> { checked: Boolean -> b.onCheckedChange(row.key, checked) } },
                compact = compact,
                interactionSource = sources[row.key] ?: remember(row.key) { MutableInteractionSource() },
                keepActions = row.key in keepActions,
                actions = rowActions,
                onClick = {
                    current = row.key
                    onRowClick(row.key)
                },
                onOpen = { onRowOpen(row.key) },
            )
        }
    }
}

private fun KeyEvent.isSelectAll(): Boolean = key == Key.A && (isCtrlPressed || isMetaPressed)

internal class TableLayout(
    val columns: List<TableColumn>,
    val widths: Map<String, Dp>,
    val checkbox: Boolean,
    val actions: Dp?,
) {
    private val nameIndex = columns.indexOfFirst { !it.numeric && !it.secondary }.coerceAtLeast(0)

    fun nameOf(row: TableRow): String = row.cells.getOrElse(nameIndex) { row.cells.firstOrNull().orEmpty() }
}

internal fun RowScope.cellWidth(layout: TableLayout, column: TableColumn): Modifier =
    layout.widths[column.key]?.let { Modifier.width(it) } ?: Modifier.weight(1f)
