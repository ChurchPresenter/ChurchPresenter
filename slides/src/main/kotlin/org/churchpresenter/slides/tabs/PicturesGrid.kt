package org.churchpresenter.slides.tabs

import org.churchpresenter.sharedui.composables.ContextMenuDivider
import org.churchpresenter.sharedui.composables.SetAsBackgroundItems
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.picture_thumbnail_unreadable
import org.jetbrains.compose.resources.stringResource
import java.io.File
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.isSecondary
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_go_live
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import org.churchpresenter.sharedui.composables.ContextMenu
import org.churchpresenter.sharedui.composables.ContextMenuHeader
import org.churchpresenter.sharedui.composables.ContextMenuItem
import org.churchpresenter.sharedui.composables.contextMenuShortcut
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.fileDragData
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.hosanna.composables.media.Thumbnail
import org.churchpresenter.hosanna.composables.model.ThumbnailLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailFileLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailSize
import org.churchpresenter.hosanna.composables.model.ThumbnailStatus
import androidx.compose.ui.graphics.painter.BitmapPainter
import org.churchpresenter.strings.generated.resources.hide_from_slideshow
import org.churchpresenter.strings.generated.resources.show_in_slideshow
import org.churchpresenter.strings.generated.resources.slideshow_hidden
import org.churchpresenter.strings.generated.resources.thumbnail_live
import org.churchpresenter.strings.generated.resources.thumbnail_locate
import org.churchpresenter.strings.generated.resources.thumbnail_offline
import androidx.compose.foundation.layout.fillMaxWidth
import org.churchpresenter.sharedui.models.Presenting

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
            columns = GridCells.Fixed(PICTURES_PER_ROW),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 18.dp)
        ) {
            items(shownImages, key = { it.absolutePath }) { imageFile ->
                val cell = Modifier.animateItem().fillMaxWidth()
                PictureThumbnail(viewModel, shownImages, imageFile, drag, cell)
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

        val menuOpen = remember { mutableStateOf(false) }
        var menuAt by remember { mutableStateOf(IntOffset.Zero) }
        val thumbnail = viewModel.thumbnails[imageFile]
        val painter = remember(thumbnail) { thumbnail?.let { BitmapPainter(it) } }
        Box {
        Thumbnail(
            image = painter,
            onClick = { if (!isDragActive) viewModel.selectImage(viewModel.images.indexOf(imageFile)) },
            labels = pictureThumbnailLabels(),
            modifier = modifier
                .alpha(if (isDraggingThis) PICTURES_DRAGGED_ITEM_ALPHA else 1f)
                .then(if (isDropTarget) Modifier.dropTargetMark() else Modifier)
                .then(reorderDrag(viewModel, imageFile, drag))
                // A plain drag carries the picture out as a file, which the schedule takes as one
                // row; Shift+drag is still the reorder above, which takes the press first. The drag
                // image is the thumbnail itself: the default one records the tile into a picture that
                // is what gets drawn, and the tile's own layers then change without it being redrawn
                // -- a tile stayed on its placeholder after loading, and hover never showed.
                .dragAndDropSource(
                    drawDragDecoration = { painter?.run { draw(size) } },
                ) { _ -> if (isDragActive) null else fileDragData(listOf(imageFile)) }
                .onSecondaryPress { pos ->
                    menuAt = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                    menuOpen.value = true
                },
            size = ThumbnailSize.Large,
            caption = imageFile.nameWithoutExtension,
            selected = isSelected,
            // The tab shows the selected picture while it is live.
            live = isSelected && presenterManager?.isLive(Presenting.PICTURES) == true,
            number = (index + 1).toString(),
            status = when {
                thumbnail != null -> ThumbnailStatus.Ready
                imageFile in viewModel.thumbnailFailures -> ThumbnailStatus.Missing
                else -> ThumbnailStatus.Loading
            },
            hidden = isHidden,
            onGoLive = presenterManager?.let { manager ->
                {
                    if (!isDragActive) {
                        viewModel.selectImage(viewModel.images.indexOf(imageFile))
                        viewModel.goLive(manager, onInstanceLinkSendProject, wentLive)
                    }
                }
            },
            onHiddenChange = { viewModel.toggleHidden(imageFile) },
        )
        // A menu opens below what it is anchored to, so it is anchored to the point that was
        // pressed: anchored to the whole tile, it opened a tile's height below the cursor.
        Box(Modifier.offset { menuAt }.size(0.dp)) {
            PictureContextMenu(viewModel, imageFile, index, menuOpen)
        }
        }
    }
}

/** Calls [onPress] with the position of each right-button press, leaving the press to the others. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.onSecondaryPress(onPress: (Offset) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type == PointerEventType.Press && event.button?.isSecondary == true) {
                    onPress(event.changes.first().position)
                }
            }
        }
    }

/** Go Live and Add to Schedule for one thumbnail, from a right-click on it. */
@Composable
private fun PicturesTabScope.PictureContextMenu(
    viewModel: PicturesViewModel,
    imageFile: File,
    index: Int,
    menuOpen: MutableState<Boolean>,
) {
    ContextMenu(
        expanded = menuOpen.value,
        onDismissRequest = { menuOpen.value = false },
        header = {
            ContextMenuHeader(
                badge = (index + 1).toString(),
                title = imageFile.nameWithoutExtension,
                subtitle = imageFile.parentFile?.name.orEmpty(),
                accent = MaterialTheme.semantic.contentPictures,
            )
        },
    ) {
        if (presenterManager != null) {
            ContextMenuItem(
                label = stringResource(Res.string.go_live),
                icon = painterResource(IconRes.drawable.ic_go_live),
                accent = MaterialTheme.colorScheme.primary,
                shortcut = contextMenuShortcut(ShortcutAction.GO_LIVE),
                emphasized = true,
                onClick = {
                    menuOpen.value = false
                    viewModel.selectImage(viewModel.images.indexOf(imageFile))
                    viewModel.goLive(presenterManager, onInstanceLinkSendProject, wentLive)
                },
            )
        }
        onAddPictureToSchedule?.let { add ->
            ContextMenuItem(
                label = stringResource(Res.string.add_to_schedule),
                icon = painterResource(IconRes.drawable.ic_playlist_add),
                accent = MaterialTheme.semantic.success,
                onClick = {
                    menuOpen.value = false
                    add(imageFile, viewModel.images.size)
                },
            )
        }
        onSetAsBackground?.let { set ->
            ContextMenuDivider()
            SetAsBackgroundItems(
                backgroundTargets,
                isCurrent = { profileId, content -> isBackground(imageFile, profileId, content) },
            ) { profileId, content ->
                menuOpen.value = false
                set(imageFile, profileId, content)
            }
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

/** The texts of a picture's tile; a picture that would not decode says so where the image would be. */
@Composable
private fun pictureThumbnailLabels() = ThumbnailLabels(
    goLive = stringResource(Res.string.go_live),
    hide = stringResource(Res.string.hide_from_slideshow),
    showAgain = stringResource(Res.string.show_in_slideshow),
    hiddenNote = stringResource(Res.string.slideshow_hidden),
    live = stringResource(Res.string.thumbnail_live),
    file = ThumbnailFileLabels(
        missing = stringResource(Res.string.picture_thumbnail_unreadable),
        locate = stringResource(Res.string.thumbnail_locate),
        offline = stringResource(Res.string.thumbnail_offline),
    ),
)

/** Where a Shift-dragged picture will land: a ring in the drop color around the tile. */
@Composable
private fun Modifier.dropTargetMark(): Modifier =
    border(2.dp, MaterialTheme.colorScheme.tertiary, AppShape(8.dp))

private const val PICTURES_PER_ROW = 4

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
