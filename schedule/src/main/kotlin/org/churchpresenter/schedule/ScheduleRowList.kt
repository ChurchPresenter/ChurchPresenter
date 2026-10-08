package org.churchpresenter.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.churchpresenter.calendar.model.PlanDrift
import org.churchpresenter.calendar.model.RowClock
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.schedule_drop_hint
import org.churchpresenter.strings.generated.resources.schedule_drop_to_remove
import org.churchpresenter.strings.generated.resources.schedule_drop_unsupported
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** How every row is drawn: its density and layout, when it is due, and the live row's drift. */
internal class ScheduleRowLook(
    val density: ScheduleDensity,
    val legacyRowActions: Boolean,
    val rowClocks: Map<String, RowClock>,
    val drift: PlanDrift?,
)

/** The panel's rows, reordered by dragging and taking files dropped on it from outside the app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScheduleRowList(
    viewModel: ScheduleViewModel,
    look: ScheduleRowLook,
    onRowClick: (ScheduleItem) -> Unit,
    onPresent: (ScheduleItem) -> Unit,
    onEditLabel: (ScheduleItem.LabelItem) -> Unit,
    fileDrop: ScheduleFileDrop,
    modifier: Modifier = Modifier,
) {
    var listHeightPx by remember { mutableStateOf(0) }
    Box(
        modifier = modifier
            .onSizeChanged { listHeightPx = it.height }
            // Compose's own target, scoped to this panel. An AWT `DropTarget` reached for a
            // window by hand instead, which is not reliably the one holding this composable and
            // never received an event; the Compose target is attached to the node, so it cannot
            // be aimed at the wrong window.
            .dragAndDropTarget(shouldStartDragAndDrop = fileDrop::accepts, target = fileDrop)
    ) {
        val listState = rememberLazyListState()
        val drag = remember { ScheduleDragState() }
        val baseDensity = LocalDensity.current
        val geometry = remember(listState, baseDensity, viewModel) {
            ScheduleDragGeometry(
                listState = listState,
                thresholdPx = with(baseDensity) { DRAG_HANDLE_THRESHOLD.toPx() },
                deleteZonePx = with(baseDensity) { DELETE_ZONE_HEIGHT.toPx() },
                listHeightPx = { listHeightPx },
                onDropped = { index -> viewModel.finishRowDrag(index, drag.overDeleteZone, drag.targetIndex) },
            )
        }

        if (viewModel.scheduleItems.isEmpty() && !fileDrop.dragOver) {
            Text(
                text = stringResource(Res.string.schedule_drop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        }

        val rows = viewModel.scheduleItems.toList()
        val selectedItemId = viewModel.selectedItemId
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .padding(start = 4.dp, top = 6.dp, bottom = 10.dp)
        ) {
            itemsIndexed(rows, key = { _, item -> item.id }) { index, item ->
                val isDraggingThis = drag.isActive && drag.fromIndex == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .padding(bottom = 4.dp)
                        .alpha(if (isDraggingThis) DRAGGED_ITEM_ALPHA else 1f)
                        .reorderGesture(index, requireShift = true, drag, geometry)
                ) {
                    val drift = look.drift.takeIf { item.id == viewModel.liveRowId }
                    CompositionLocalProvider(LocalLiveDrift provides drift) {
                        ScheduleItemRow(
                            item = item,
                            timing = viewModel.timingFor(item.id),
                            clock = look.rowClocks[item.id],
                            dragHandleModifier = Modifier
                                .reorderGesture(index, requireShift = false, drag, geometry),
                            density = look.density,
                            legacyRowActions = look.legacyRowActions,
                            isSelected = item.id == selectedItemId,
                            note = viewModel.getNote(item.id),
                            onSelect = { if (!drag.isActive) onRowClick(item) },
                            onMoveUp   = { viewModel.moveItemUp(item.id) },
                            onMoveDown = { viewModel.moveItemDown(item.id) },
                            onRemove = {
                                viewModel.removeItem(item.id)
                                if (selectedItemId == item.id) viewModel.clearSelection()
                            },
                            onPresent = { onPresent(item) },
                            onEditLabel = {
                                if (item is ScheduleItem.LabelItem) onEditLabel(item)
                            },
                            onNoteChanged = { viewModel.setNote(item.id, it) },
                            actions = viewModel.actionsFor(item.id),
                            rows = rows,
                            onActionsChanged = { viewModel.setActions(item.id, it) },
                        )
                    }
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState)
        )

        if (drag.isActive) ScheduleDeleteZone(drag.overDeleteZone)
        // Drawn over the rows rather than instead of them, which is what makes this work on a
        // schedule that already has content — the hint below only ever appeared on an empty one,
        // so by the time anyone had a service to add to there was no affordance at all.
        if (fileDrop.dragOver) ScheduleFileDropOverlay()
        // What the drop could not take. Sits in the panel the file was aimed at rather than in
        // an app-level toast: the operator is looking here, and the answer is about this list.
        if (fileDrop.skippedFiles.isNotEmpty()) ScheduleSkippedFiles(fileDrop.skippedFiles)
        if (drag.isActive) {
            viewModel.scheduleItems.getOrNull(drag.fromIndex)?.let { ScheduleDragGhost(it, drag) }
        }
    }
}

/** The row being reordered, and where the pointer has carried it. */
@Stable
internal class ScheduleDragState {
    var fromIndex by mutableStateOf(-1)
    var targetIndex by mutableStateOf<Int?>(null)
    var isActive by mutableStateOf(false)
    var cursorY by mutableStateOf(0f)
    var itemHeight by mutableStateOf(FALLBACK_DRAG_ITEM_HEIGHT)
    var overDeleteZone by mutableStateOf(false)

    /** Picks up row [index], centered on its own slot when it is on screen, else at [fallbackY]. */
    fun arm(index: Int, itemInfo: LazyListItemInfo?, fallbackY: Float) {
        fromIndex = index
        isActive = true
        targetIndex = index
        itemHeight = itemInfo?.size?.toFloat() ?: FALLBACK_DRAG_ITEM_HEIGHT
        cursorY = if (itemInfo != null) itemInfo.offset + itemInfo.size / 2f else fallbackY
    }

    /** Carries the row [deltaY] further, retargeting the slot or the delete zone under it. */
    fun moveBy(deltaY: Float, geometry: ScheduleDragGeometry) {
        cursorY += deltaY
        val hit = dragDropTarget(
            cursorY = cursorY,
            listHeightPx = geometry.listHeightPx(),
            deleteZonePx = geometry.deleteZonePx,
            visibleItems = geometry.listState.layoutInfo.visibleItemsInfo.map {
                DragItemGeometry(it.index, it.offset, it.size)
            },
        )
        overDeleteZone = hit.overDeleteZone
        if (!hit.overDeleteZone) {
            hit.targetIndex?.let { targetIndex = it }
        }
    }

    /** Puts the row down, whether or not it moved. */
    fun end(index: Int) {
        if (fromIndex == index) fromIndex = -1
        targetIndex = null
        isActive = false
        overDeleteZone = false
        cursorY = 0f
    }
}

/**
 * The list a drag measures against: how far to travel before it lifts, and where delete starts.
 * [onDropped] is told when a row is put down while still the one being dragged.
 */
internal class ScheduleDragGeometry(
    val listState: LazyListState,
    val thresholdPx: Float,
    val deleteZonePx: Float,
    val listHeightPx: () -> Int,
    val onDropped: (index: Int) -> Unit,
)

/**
 * Reorders row [index] by dragging: from its handle at once, or from anywhere on the row with
 * Shift held.
 */
internal fun Modifier.reorderGesture(
    index: Int,
    requireShift: Boolean,
    drag: ScheduleDragState,
    geometry: ScheduleDragGeometry,
): Modifier = pointerInput(index, requireShift) {
    awaitPointerEventScope {
        while (true) {
            val pressEvent = awaitPointerEvent(PointerEventPass.Initial)
            if (pressEvent.type != PointerEventType.Press ||
                (requireShift && !pressEvent.keyboardModifiers.isShiftPressed)
            ) continue
            if (requireShift) pressEvent.changes.forEach { it.consume() }
            val travelled = if (requireShift) geometry.thresholdPx else 0f
            trackRowDrag(index, pressEvent.changes.first().position, travelled, drag, geometry)
        }
    }
}

private suspend fun AwaitPointerEventScope.trackRowDrag(
    index: Int,
    pressedAt: Offset,
    alreadyTravelled: Float,
    drag: ScheduleDragState,
    geometry: ScheduleDragGeometry,
) {
    val tracker = RowDragTracker(index, drag, geometry, pressedAt, alreadyTravelled)
    try {
        do {
            tracker.armIfReady()
        } while (tracker.handle(awaitPointerEvent(PointerEventPass.Initial)))
    } finally {
        if (tracker.armed) drag.end(index)
    }
}

/** One press on row [index], from the press until the pointer is up: lifting it, then carrying it. */
private class RowDragTracker(
    private val index: Int,
    private val drag: ScheduleDragState,
    private val geometry: ScheduleDragGeometry,
    private var lastPos: Offset,
    private var travelled: Float,
) {
    var armed = false
        private set

    /** Lifts the row once the pointer has travelled far enough, unless another row is already up. */
    fun armIfReady() {
        if (armed || travelled < geometry.thresholdPx || drag.isActive) return
        val itemInfo = geometry.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        drag.arm(index, itemInfo, lastPos.y)
        armed = true
    }

    /** Follows [event]; false once the pointer is up, after dropping the row if it was carried. */
    fun handle(event: PointerEvent): Boolean {
        if (armed) event.changes.forEach { it.consume() }
        val finished = event.type == PointerEventType.Release || event.changes.none { it.pressed }
        if (finished) {
            if (armed && drag.fromIndex == index) geometry.onDropped(index)
            return false
        }
        if (event.type == PointerEventType.Move) move(event)
        return true
    }

    private fun move(event: PointerEvent) {
        val pos = event.changes.firstOrNull()?.position ?: return
        val deltaY = (pos - lastPos).y
        lastPos = pos
        if (!armed) travelled += abs(deltaY) else drag.moveBy(deltaY, geometry)
    }
}

@Composable
private fun BoxScope.ScheduleDeleteZone(isOverDeleteZone: Boolean) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(DELETE_ZONE_HEIGHT)
            .zIndex(DRAG_TARGET_Z_INDEX)
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = if (isOverDeleteZone) 0.9f else 0.25f),
                AppShape(4.dp)
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_delete),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError
        )
        Text(
            text = stringResource(Res.string.schedule_drop_to_remove),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onError
        )
    }
}

@Composable
private fun BoxScope.ScheduleFileDropOverlay() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .zIndex(FILE_DROP_OVERLAY_Z_INDEX)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = FILE_DROP_SCRIM_ALPHA),
                CARD_SHAPE,
            )
            .border(FILE_DROP_BORDER, MaterialTheme.colorScheme.primary, CARD_SHAPE),
        contentAlignment = Alignment.Center,
    ) {
        // A solid callout rather than text straight onto the scrim: over a schedule that
        // already has rows, translucent text on translucent wash is unreadable exactly
        // when it matters most.
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CARD_SHAPE,
            shadowElevation = FILE_DROP_CALLOUT_ELEVATION,
        ) {
            Text(
                text = stringResource(Res.string.schedule_drop_hint),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(
                    horizontal = FILE_DROP_CALLOUT_PADDING_H,
                    vertical = FILE_DROP_CALLOUT_PADDING_V,
                ),
            )
        }
    }
}

@Composable
private fun BoxScope.ScheduleSkippedFiles(skippedFiles: List<String>) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(SKIPPED_FILES_MARGIN)
            .zIndex(FILE_DROP_OVERLAY_Z_INDEX)
            .testTag(SCHEDULE_SKIPPED_FILES_TAG),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = CARD_SHAPE,
        shadowElevation = FILE_DROP_CALLOUT_ELEVATION,
    ) {
        Text(
            text = stringResource(Res.string.schedule_drop_unsupported, skippedFiles.joinToString()),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = SKIPPED_FILES_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = FILE_DROP_CALLOUT_PADDING_H,
                vertical = FILE_DROP_CALLOUT_PADDING_V,
            ),
        )
    }
}

/** The lifted row, drawn under the pointer while it is carried. */
@Composable
private fun ScheduleDragGhost(item: ScheduleItem, drag: ScheduleDragState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(DRAGGED_ITEM_Z_INDEX)
            .graphicsLayer {
                translationY = drag.cursorY - drag.itemHeight / 2
                scaleX = DRAGGED_ITEM_SCALE
                scaleY = DRAGGED_ITEM_SCALE
                shadowElevation = DRAGGED_ITEM_ELEVATION
            }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CARD_SHAPE)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = scheduleItemIcon(item),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = item.displayText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private const val FALLBACK_DRAG_ITEM_HEIGHT = 50f

private const val DRAGGED_ITEM_ALPHA = 0.35f

private const val DRAG_TARGET_Z_INDEX = 5f

private const val DRAGGED_ITEM_Z_INDEX = 10f

private const val DRAGGED_ITEM_SCALE = 1.04f

private const val DRAGGED_ITEM_ELEVATION = 20f

/** Above the rows, below the row being reordered — a file drop and a reorder never overlap. */
private const val FILE_DROP_OVERLAY_Z_INDEX = 5f

/** Enough tint to read as a target without hiding the schedule underneath it. */
private const val FILE_DROP_SCRIM_ALPHA = 0.16f

private val FILE_DROP_BORDER = 3.dp

private val FILE_DROP_CALLOUT_ELEVATION = 6.dp

private val FILE_DROP_CALLOUT_PADDING_H = 20.dp

private val FILE_DROP_CALLOUT_PADDING_V = 14.dp

private val SKIPPED_FILES_MARGIN = 16.dp

private const val SKIPPED_FILES_MAX_LINES = 2

private val DRAG_HANDLE_THRESHOLD = 4.dp

private val DELETE_ZONE_HEIGHT = 56.dp
