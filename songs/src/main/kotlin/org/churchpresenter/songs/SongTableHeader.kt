@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.songs

import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import org.churchpresenter.settings.SongColumnId
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.components.RaisedCheckbox
import org.churchpresenter.theme.keyboardFocusRing
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.sharedui.composables.TooltipIconButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.strings.generated.resources.filter
import org.churchpresenter.icons.generated.resources.ic_star
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import org.churchpresenter.strings.generated.resources.song_columns
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.ScrollState
import org.churchpresenter.sharedui.composables.DragHandle
import org.churchpresenter.sharedui.composables.bibleListCardFill

/**
 * The column header row: sortable, resizable and reorderable cells that scroll with the list, the
 * columns button, and the right-click column menu.
 */
@Composable
internal fun SongListScope.SongTableHeader(
    hScrollState: ScrollState,
    contentMinWidthDp: Dp,
    allColLabels: Map<String, String>,
) {
    // Column header row — scrolls horizontally with the song list
    // Wrapped in a Box so the right-click DropdownMenu can anchor here
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press &&
                            event.button?.isSecondary == true
                        ) {
                            val pos = event.changes.firstOrNull()?.position
                            if (pos != null) columns.menuOffset = with(density) { DpOffset(pos.x.toDp(), pos.y.toDp()) }
                            columns.showMenu = true
                        }
                    }
                }
            }
    ) {
    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .horizontalScroll(hScrollState)
    ) {
    Row(
        modifier = Modifier
            .width(contentMinWidthDp)
            .fillMaxHeight()
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleCols.forEach { colId ->
            SongHeaderCell(colId, allColLabels)
        }
    }
    } // end inner scrollable header Box
    } // end header Row

        SongColumnsButton(allColLabels, Modifier.align(Alignment.CenterEnd))

        SongColumnsContextMenu(allColLabels)
    } // end outer header Box (right-click + dropdown wrapper)
}

@Composable
private fun SongListScope.SongHeaderCell(colId: String, colHeaderLabels: Map<String, String>) {
    val isBeingDragged = colId == columns.draggingId
    val sk = sortKey(colId)
    val isSortable = sk.isNotEmpty() && colId != SongColumnId.ADD_TO_SCHEDULE
    val isSorted = isSortable && currentSortColumn == sk
    val reorderDragMod = columnReorderDrag(colId)
    val cellColor = when {
        isBeingDragged -> MaterialTheme.colorScheme.primary
        isSorted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val cellBg = if (isSorted)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else
        Color.Transparent

    if (colId in actionCols) {
        SongActionHeaderCell(colId, colHeaderLabels[colId], sk, isSortable, isSorted, cellBg, cellColor, reorderDragMod)
    } else {
        SongDataHeaderCell(colId, colHeaderLabels, sk, isSortable, isSorted, cellBg, cellColor, reorderDragMod)
    }
}

/** Dragging a header cell sideways moves its column. */
private fun SongListScope.columnReorderDrag(colId: String): Modifier = Modifier.pointerInput(colId) {
    detectHorizontalDragGestures(
        onDragEnd = {
            val vc = columns.order.filter { it !in columns.hidden }
            val newVisIdx = computeNewIdx(colId, columns.dragAccumX, vc)
            val targetId = vc.getOrNull(newVisIdx)
            if (targetId != null) {
                val reordered = moveColumn(columns.order, colId, targetId)
                if (reordered !== columns.order) {
                    columns.order = reordered
                    onSaveColumnOrder()
                }
            }
            columns.draggingId = null
            columns.dragAccumX = 0f
        },
        onDragCancel = { columns.draggingId = null; columns.dragAccumX = 0f }
    ) { _, amount ->
        if (columns.draggingId != colId) { columns.draggingId = colId; columns.dragAccumX = 0f }
        columns.dragAccumX += amount
    }
}

/** Action column: an icon header, and no resize handle. */
@Composable
private fun SongListScope.SongActionHeaderCell(
    colId: String,
    label: String?,
    sk: String,
    isSortable: Boolean,
    isSorted: Boolean,
    cellBg: Color,
    cellColor: Color,
    reorderDragMod: Modifier,
) {
    Box(
        modifier = Modifier
            .width(with(density) { colWidth(colId).toDp() })
            .fillMaxHeight()
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            .background(cellBg, shape = MaterialTheme.shapes.extraSmall)
            .then(
                if (isSortable) {
                    Modifier.keyboardFocusRing(MaterialTheme.shapes.extraSmall).clickable { onSortChange(sk) }
                } else {
                    Modifier
                },
            )
            // A sortable header is named for its column, as the data columns' text names theirs.
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .then(reorderDragMod)
            .then(
                if (colId == SongColumnId.FAVORITES) Modifier.guideTarget(GuideTargets.SONG_FAVORITES) else Modifier,
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // The cell carries the column's name; the icon only draws it.
            Icon(
                painter = painterResource(
                    if (colId == SongColumnId.FAVORITES) IconRes.drawable.ic_star
                    else IconRes.drawable.ic_playlist_add
                ),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = cellColor
            )
            if (isSorted) {
                Icon(
                    painter = painterResource(
                        if (currentSortAscending) IconRes.drawable.ic_arrow_up else IconRes.drawable.ic_arrow_down,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(8.dp),
                    tint = cellColor
                )
            }
        }
    }
}

/** Data column: a text label and a resize handle. */
@Composable
private fun SongListScope.SongDataHeaderCell(
    colId: String,
    colHeaderLabels: Map<String, String>,
    sk: String,
    isSortable: Boolean,
    isSorted: Boolean,
    cellBg: Color,
    cellColor: Color,
    reorderDragMod: Modifier,
) {
    Box(
        modifier = Modifier
            .width(with(density) { colWidth(colId).toDp() })
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .background(cellBg, shape = MaterialTheme.shapes.extraSmall)
            .then(
                if (isSortable) {
                    Modifier.keyboardFocusRing(MaterialTheme.shapes.extraSmall).clickable { onSortChange(sk) }
                } else {
                    Modifier
                },
            )
            .then(reorderDragMod),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = colHeaderLabels[colId] ?: colId,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = cellColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isSorted) {
                Icon(
                    painter = painterResource(
                        if (currentSortAscending) IconRes.drawable.ic_arrow_up else IconRes.drawable.ic_arrow_down,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = cellColor
                )
            }
        }
    }
    DragHandle(
        colId = colId,
        onDrag = { setColWidth(colId, colWidth(colId) + it) },
        onDragEnd = onSaveColumnWidths
    )
}

/** The floating column filter button, on the right. */
@Composable
private fun SongListScope.SongColumnsButton(allColLabels: Map<String, String>, modifier: Modifier) {
    // Floating column filter button — right side
    Box(modifier = modifier.background(bibleListCardFill())) {
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Default.Tune),
            text = stringResource(Res.string.song_columns),
            onClick = { columns.showColumnsMenu = true },
            buttonSize = 36.dp,
            iconTint = MaterialTheme.colorScheme.onSurface
        )
        DropdownMenu(
            expanded = columns.showColumnsMenu,
            onDismissRequest = { columns.showColumnsMenu = false }
        ) {
            ColumnVisibilityItems(allColLabels)
        }
    }
}

/** Right-click dropdown — toggle column visibility. */
@Composable
private fun SongListScope.SongColumnsContextMenu(allColLabels: Map<String, String>) {
    DropdownMenu(
        expanded = columns.showMenu,
        onDismissRequest = { columns.showMenu = false },
        offset = columns.menuOffset
    ) {
        ColumnVisibilityItems(allColLabels)
    }
}

/** One checkable item per column; Title cannot be hidden. */
@Composable
private fun SongListScope.ColumnVisibilityItems(allColLabels: Map<String, String>) {
    availableCols.forEach { colId ->
        val isVisible = colId !in columns.hidden
        val isProtected = colId == SongColumnId.TITLE
        DropdownMenuItem(
            text = { Text(allColLabels[colId] ?: colId) },
            leadingIcon = {
                RaisedCheckbox(
                    checked = isVisible,
                    onCheckedChange = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = {
                if (!(isProtected && isVisible)) {
                    columns.hidden = if (isVisible) columns.hidden + colId else columns.hidden - colId
                    onSaveHiddenColumns()
                }
            },
            enabled = !(isProtected && isVisible)
        )
    }
}
