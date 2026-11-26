package org.churchpresenter.hosanna.composables.table

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.churchpresenter.theme.StateAlpha
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.hosanna.composables.defaults.TableDefaults
import org.churchpresenter.hosanna.composables.model.SortDirection
import org.churchpresenter.hosanna.composables.model.TableBulk
import org.churchpresenter.hosanna.composables.model.TableColumn
import org.churchpresenter.hosanna.composables.model.TableRow
import org.churchpresenter.hosanna.composables.model.TableSort
import org.churchpresenter.hosanna.composables.model.bulkState
import org.churchpresenter.hosanna.composables.model.nextSort
import org.churchpresenter.hosanna.composables.model.resizedWidth
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun TableHeader(
    layout: TableLayout,
    rows: List<TableRow>,
    sort: TableSort?,
    onSortChange: (TableSort) -> Unit,
    bulk: TableBulk?,
    onResize: (String, Dp) -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(TableDefaults.HeaderHeight)
            .drawBehind {
                val line = TableDefaults.Divider.toPx()
                drawRect(colors.outlineVariant, Offset(0f, size.height - line), Size(size.width, line))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bulk?.let {
            BulkCell(bulkState(it.checked, rows.map { row -> row.key }), it.selectAllLabel) { on -> it.onAllChange(on) }
        }
        layout.columns.forEachIndexed { index, column ->
            HeaderCell(column, sort, onSortChange, cellWidth(layout, column))
            if (index < layout.columns.lastIndex) {
                HeaderDivider(column, layout, rows, onResize)
            }
        }
        layout.actions?.let { Box(Modifier.width(it)) }
    }
}

@Composable
private fun HeaderCell(column: TableColumn, sort: TableSort?, onSortChange: (TableSort) -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val colors = AppTheme.colors
    val sorted = sort?.column == column.key
    val ink = if (sorted) colors.onSurface else colors.onSurfaceVariant
    val layer = colors.onSurface.copy(alpha = StateAlpha.HOVER)
    val descending = sort?.direction == SortDirection.Descending
    val arrow = if (descending) Res.drawable.ic_arrow_down else Res.drawable.ic_arrow_up
    Row(
        modifier
            .fillMaxHeight()
            .background(if (hovered && column.sortable) layer else Color.Transparent)
            .then(
                if (column.sortable) {
                    Modifier.clickable(source, null, role = Role.Button) { onSortChange(nextSort(sort, column.key)) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = TableDefaults.CellPadding),
        horizontalArrangement = Arrangement.spacedBy(
            TableDefaults.SortGap,
            if (column.numeric) Alignment.End else Alignment.Start,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            column.label,
            Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = AppTheme.typography.captionStrong.copy(color = ink),
        )
        if (sorted) {
            Image(
                painterResource(arrow),
                null,
                Modifier.size(TableDefaults.SortArrow),
                colorFilter = ColorFilter.tint(ink),
            )
        }
    }
}

@Composable
private fun HeaderDivider(
    column: TableColumn,
    layout: TableLayout,
    rows: List<TableRow>,
    onResize: (String, Dp) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val body = AppTheme.typography.body
    val label = AppTheme.typography.captionStrong
    fun fitWidth(column: TableColumn, layout: TableLayout, rows: List<TableRow>): Dp {
        val index = layout.columns.indexOf(column)
        val cells = rows.map { measurer.measure(it.cells.getOrElse(index) { "" }, body).size.width }
        val widest = (cells + measurer.measure(column.label, label).size.width).max()
        val padding = TableDefaults.CellPadding * 2 + TableDefaults.SortArrow
        return maxOf(TableDefaults.MinColumn, with(density) { widest.toDp() } + padding)
    }
    val width = layout.widths[column.key]
    val current by rememberUpdatedState(width)
    val resizable = width != null
    Box(
        Modifier
            .width(TableDefaults.DividerGrip)
            .fillMaxHeight()
            .hoverable(source, resizable)
            .then(
                if (resizable) {
                    Modifier
                        .pointerInput(column.key) {
                            detectHorizontalDragGestures { change, amount ->
                                change.consume()
                                current?.let {
                                    onResize(column.key, resizedWidth(it, amount.toDp(), TableDefaults.MinColumn))
                                }
                            }
                        }
                        .pointerInput(column.key, rows) {
                            detectTapGestures(onDoubleTap = { onResize(column.key, fitWidth(column, layout, rows)) })
                        }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(TableDefaults.Divider)
                .height(TableDefaults.HeaderDivider)
                .background(if (hovered) colors.onSurfaceFaint else colors.outlineVariant),
        )
    }
}
