package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import org.churchpresenter.app.churchpresenter.composables.HIDDEN_TILE_ALPHA
import org.churchpresenter.app.churchpresenter.composables.HiddenBadge
import org.churchpresenter.app.churchpresenter.composables.finalPassCombinedClickable
import org.churchpresenter.app.churchpresenter.composables.SlideshowHideToggle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.grid.items
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.loading
import org.churchpresenter.strings.generated.resources.picture_thumbnail_unreadable
import org.jetbrains.compose.resources.stringResource
import java.io.File
import androidx.compose.ui.text.style.TextOverflow

/** The thumbnail grid, its drag-to-reorder ghost, and the scroll to the selected picture. */
@Composable
internal fun PicturesTabScope.PicturesGrid(viewModel: PicturesViewModel) {
    val drag = remember { PictureDragState() }

    // The grid is given an immutable copy, never the view model's live SnapshotStateList.
    // `items(list)` captures the list by reference and indexes into it from the item
    // provider on a later measure pass, so a folder watcher removing a file between the
    // count being read and `contentType` being asked for indexes past the end — an
    // IndexOutOfBoundsException raised inside Compose's measure, where the tab cannot catch
    // it. Copying here makes the count and the indexing come from the same list.
    val shownImages = viewModel.images.toList()

    Box(
        modifier = Modifier.fillMaxSize()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .bibleListCard()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 18.dp)
        ) {
            items(shownImages, key = { it.absolutePath }) { imageFile ->
                PictureThumbnail(viewModel, shownImages, imageFile, drag, Modifier.animateItem())
            }
        }

        // Floating drag preview — follows cursor, rendered above the grid
        PictureDragPreview(viewModel, drag)
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(gridState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp),
        )
    }

    // Auto-scroll to selected item in grid
    LaunchedEffect(viewModel.selectedImageIndex) {
        if (viewModel.selectedImageIndex in viewModel.images.indices) {
            gridState.animateScrollToItem(viewModel.selectedImageIndex)
        }
    }
}

@Composable
private fun PicturesTabScope.PictureThumbnail(
    viewModel: PicturesViewModel,
    shownImages: List<File>,
    imageFile: File,
    drag: PictureDragState,
    modifier: Modifier,
) {
    with(drag) {
        val index = shownImages.indexOf(imageFile)
        val isSelected = index == viewModel.selectedImageIndex
        val isHidden = viewModel.isHidden(imageFile)
        val isDraggingThis = draggingFile == imageFile
        val isDropTarget = isDragActive && dropTargetIndex == index && !isDraggingThis

        val borderColor = when {
            isDropTarget -> MaterialTheme.colorScheme.tertiary
            isSelected -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outlineVariant
        }

        Column(
            modifier = modifier
                .alpha(if (isDraggingThis) PICTURES_DRAGGED_ITEM_ALPHA else 1f)
                .hoverLift(AppShape(8.dp))
                .border(2.dp, borderColor, AppShape(8.dp))
                .clip(AppShape(8.dp))
                .then(reorderDrag(viewModel, imageFile, drag))
                // Final pass, so the hide eye in the nameplate gets its own click
                // first: taken in the initial pass, a click on the eye selected the
                // picture instead, and put it on screen when live.
                .finalPassCombinedClickable(
                    onClick = {
                        if (!isDragActive) viewModel.selectImage(viewModel.images.indexOf(imageFile))
                    },
                    onDoubleClick = {
                        if (!isDragActive) {
                            viewModel.selectImage(viewModel.images.indexOf(imageFile))
                            if (presenterManager != null) {
                                viewModel.goLive(presenterManager, onInstanceLinkSendProject, wentLive)
                            }
                        }
                    }
                )
        ) {
            PictureTileImage(viewModel, imageFile, isHidden)
            PictureNameplate(viewModel, imageFile, index, isSelected, isHidden)
        }
    }
}

/** Shift+press starts a drag; moves track the drop target, and release moves the picture there. */
private fun PicturesTabScope.reorderDrag(
    viewModel: PicturesViewModel,
    imageFile: File,
    drag: PictureDragState,
): Modifier =
    Modifier.pointerInput(imageFile) {
        with(drag) {
            awaitPointerEventScope {
                while (true) {
                    val pressEvent = awaitPointerEvent(PointerEventPass.Initial)
                    if (pressEvent.type != PointerEventType.Press ||
                        !pressEvent.keyboardModifiers.isShiftPressed
                    ) continue

                    pressEvent.changes.forEach { it.consume() }
                    val startPos = pressEvent.changes.first().position

                    val idx = viewModel.images.indexOf(imageFile)
                    val itemInfo = gridState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == idx }
                    draggingFile = imageFile
                    draggingFromIndex = idx
                    isDragActive = true
                    dropTargetIndex = idx
                    dragCursorInGrid = if (itemInfo != null) {
                        Offset(
                            itemInfo.offset.x + startPos.x,
                            itemInfo.offset.y + startPos.y
                        )
                    } else startPos

                    followReorderDrag(this@reorderDrag, viewModel, drag, startPos)
                }
            }
        }
    }

/** Follows one shift-drag from [startPos] to its release, then moves the picture to where it was dropped. */
private suspend fun AwaitPointerEventScope.followReorderDrag(
    tab: PicturesTabScope,
    viewModel: PicturesViewModel,
    drag: PictureDragState,
    startPos: Offset,
) {
    var lastPos = startPos
    var dragging = true
    while (dragging) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        when (event.type) {
            PointerEventType.Move -> {
                val pos = event.changes.firstOrNull()?.position ?: continue
                drag.dragCursorInGrid += pos - lastPos
                lastPos = pos
                val target = tab.gridState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info ->
                        drag.dragCursorInGrid.x >= info.offset.x &&
                        drag.dragCursorInGrid.x <= info.offset.x + info.size.width &&
                        drag.dragCursorInGrid.y >= info.offset.y &&
                        drag.dragCursorInGrid.y <= info.offset.y + info.size.height
                    }
                if (target != null) drag.dropTargetIndex = target.index
            }
            PointerEventType.Release -> {
                val from = drag.draggingFromIndex
                val to = drag.dropTargetIndex ?: from
                if (from >= 0 && from != to) viewModel.moveImage(from, to)
                drag.draggingFile = null
                drag.draggingFromIndex = -1
                drag.dropTargetIndex = null
                drag.isDragActive = false
                drag.dragCursorInGrid = Offset.Zero
                dragging = false
            }
            else -> {}
        }
    }
}

@Composable
private fun PicturesTabScope.PictureTileImage(viewModel: PicturesViewModel, imageFile: File, isHidden: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val thumbnail = viewModel.thumbnails[imageFile]
        when {
            thumbnail != null -> Image(
                bitmap = thumbnail,
                contentDescription = imageFile.name,
                modifier = Modifier.fillMaxSize()
                    .alpha(if (isHidden) HIDDEN_TILE_ALPHA else 1f),
                contentScale = ContentScale.Crop
            )
            // A decode that failed used to leave "Loading..." on the tile for
            // the rest of the session, so a corrupt file looked like a slow
            // one for ever. Say so instead.
            imageFile in viewModel.thumbnailFailures -> Text(
                text = stringResource(Res.string.picture_thumbnail_unreadable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            else -> Text(
                text = stringResource(Res.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isHidden) HiddenBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

@Composable
private fun PicturesTabScope.PictureNameplate(
    viewModel: PicturesViewModel,
    imageFile: File,
    index: Int,
    isSelected: Boolean,
    isHidden: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = imageFile.nameWithoutExtension,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = TextUnit(PICTURES_SMALL_LABEL_FONT_SP, TextUnitType.Sp),
                fontWeight = if (isSelected) FontWeight.SemiBold
                             else FontWeight.Medium
            ),
            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        SlideshowHideToggle(
            hidden = isHidden,
            position = index,
            onToggle = { viewModel.toggleHidden(imageFile) },
        )
    }
}

/** The floating preview that follows the cursor, drawn above the grid. */
@Composable
private fun PicturesTabScope.PictureDragPreview(viewModel: PicturesViewModel, drag: PictureDragState) {
    with(drag) {
        if (isDragActive) {
            draggingFile?.let { file ->
                viewModel.thumbnails[file]?.let { bitmap ->
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .zIndex(PICTURES_DRAGGED_ITEM_Z_INDEX)
                            .graphicsLayer {
                                translationX = dragCursorInGrid.x - 75.dp.toPx()
                                translationY = dragCursorInGrid.y - 75.dp.toPx()
                                scaleX = PICTURES_DRAGGED_ITEM_SCALE
                                scaleY = PICTURES_DRAGGED_ITEM_SCALE
                                shadowElevation = PICTURES_DRAGGED_ITEM_ELEVATION
                            }
                            .background(MaterialTheme.colorScheme.surface, AppShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, AppShape(8.dp))
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}
