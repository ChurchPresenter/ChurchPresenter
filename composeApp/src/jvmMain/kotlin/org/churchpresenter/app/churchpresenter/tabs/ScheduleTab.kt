package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.file_chooser_open_schedule
import churchpresenter.composeapp.generated.resources.file_chooser_save_schedule
import churchpresenter.composeapp.generated.resources.file_filter_schedule
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.autosave_restore_confirm
import churchpresenter.composeapp.generated.resources.autosave_restore_discard
import churchpresenter.composeapp.generated.resources.autosave_restore_message
import churchpresenter.composeapp.generated.resources.autosave_restore_title
import churchpresenter.composeapp.generated.resources.schedule_drop_hint
import churchpresenter.composeapp.generated.resources.schedule_drop_to_remove
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.settings.PlanningCenterSettings
import org.churchpresenter.app.churchpresenter.dialogs.PlanningCenterImportDialog
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.DragItemGeometry
import org.churchpresenter.app.churchpresenter.utils.dragDropTarget
import org.churchpresenter.app.churchpresenter.utils.scheduleCanZoomIn
import org.churchpresenter.app.churchpresenter.utils.scheduleCanZoomOut
import org.churchpresenter.app.churchpresenter.utils.scheduleDensityFor
import org.churchpresenter.app.churchpresenter.utils.scheduleZoomIn
import org.churchpresenter.app.churchpresenter.utils.scheduleZoomOut
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.scheduleItemGlyph
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date

private const val FALLBACK_DRAG_ITEM_HEIGHT = 50f
private const val DRAGGED_ITEM_ALPHA = 0.35f
private const val DRAG_TARGET_Z_INDEX = 5f
private const val DRAGGED_ITEM_Z_INDEX = 10f
private const val DRAGGED_ITEM_SCALE = 1.04f
private const val DRAGGED_ITEM_ELEVATION = 20f

data class ScheduleTabActions(
    val newSchedule: () -> Unit = {},
    val openSchedule: () -> Unit = {},
    val saveSchedule: () -> Unit = {},
    val saveScheduleAs: () -> Unit = {},
    val removeSelected: () -> Unit = {},

    val removeById: (id: String) -> Unit = {},
    val clearSchedule: () -> Unit = {},
    val moveSelectedToTop: () -> Unit = {},
    val moveSelectedUp: () -> Unit = {},
    val moveSelectedDown: () -> Unit = {},
    val moveSelectedToBottom: () -> Unit = {},
    val addLabel: (text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _ -> },
    val updateLabel: (id: String, text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _, _ -> },
    val addBibleVerse: (
        bookName: String,
        chapter: Int,
        verseNumber: Int,
        verseText: String,
        verseRange: String,
        bookId: Int
    ) -> Unit = { _, _, _, _, _, _ -> },
    val addSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit = { _, _, _, _ -> },
    val addPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit = { _, _, _ -> },
    val addPresentation: (
        filePath: String,
        fileName: String,
        slideCount: Int,
        fileType: String
    ) -> Unit = { _, _, _, _ -> },
    val addMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit = { _, _, _ -> },
    val addLowerThird: (
        presetId: String,
        presetLabel: String,
        pauseAtFrame: Boolean,
        pauseDurationMs: Long
    ) -> Unit = { _, _, _, _ -> },
    val addAnnouncement: (
        text: String,
        textColor: String,
        backgroundColor: String,
        fontSize: Int,
        fontType: String,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        shadow: Boolean,
        shadowColor: String,
        shadowSize: Int,
        shadowOpacity: Int,
        horizontalAlignment: String,
        position: String,
        animationType: String,
        animationDuration: Int,
        loopCount: Int,
        isTimer: Boolean,
        timerHours: Int,
        timerMinutes: Int,
        timerSeconds: Int,
        timerTextColor: String,
        timerExpiredText: String,
        timerMode: String,
        targetHour: Int,
        targetMinute: Int,
        targetSecond: Int,
        liveClockFormat: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    val addWebsite: (url: String, title: String) -> Unit = { _, _ -> },
    val updateWebsiteTitle: (url: String, title: String) -> Unit = { _, _ -> },
    val addScene: (sceneId: String, sceneName: String) -> Unit = { _, _ -> },
    val addDictionary: (
        number: String,
        word: String,
        transliteration: String,
        definition: String
    ) -> Unit = { _, _, _, _ -> }
)

private const val ZOOM_DEFAULT = 100

private val DRAG_HANDLE_THRESHOLD = 4.dp

private val DELETE_ZONE_HEIGHT = 56.dp

internal val CARD_SHAPE = RoundedCornerShape(9.dp)

internal object ScheduleToolbarTags {
    const val UNDO = "schedule_undo"
    const val REDO = "schedule_redo"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleTab(
    modifier: Modifier = Modifier,

    scheduleViewModel: ScheduleViewModel? = null,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    onItemClick: (ScheduleItem) -> Unit = {},
    onEditLabel: (ScheduleItem.LabelItem) -> Unit = {},
    onPresentSong: ((ScheduleItem.SongItem) -> Unit)? = null,
    onPresentBible: ((ScheduleItem.BibleVerseItem) -> Unit)? = null,
    onPresentPresentation: ((ScheduleItem.PresentationItem) -> Unit)? = null,
    onPresentPictures: ((ScheduleItem.PictureItem) -> Unit)? = null,
    onPresentMedia: ((ScheduleItem.MediaItem) -> Unit)? = null,
    onPresentAnnouncement: ((ScheduleItem.AnnouncementItem) -> Unit)? = null,
    onPresentLowerThird: ((ScheduleItem.LowerThirdItem) -> Unit)? = null,
    onPresentWebsite: ((ScheduleItem.WebsiteItem) -> Unit)? = null,
    onPresentDictionary: ((ScheduleItem.DictionaryItem) -> Unit)? = null,
    onPresentScene: ((ScheduleItem.SceneItem) -> Unit)? = null,
    onActionsReady: (ScheduleTabActions) -> Unit = {},
    onSelectedItemChanged: (String?) -> Unit = {},
    onScheduleChanged: ((List<ScheduleItem>) -> Unit)? = null,
    onAddLabel: () -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM,
    itemZoomPercent: Int = ZOOM_DEFAULT,
    onItemZoomChange: (Int) -> Unit = {},
    planningCenterSettings: PlanningCenterSettings = PlanningCenterSettings(),
    onPlanningCenterTokensRefreshed: (
        accessToken: String,
        refreshToken: String,
        expiresAtEpochMs: Long
    ) -> Unit = { _, _, _ -> },
    onPlanningCenterConnected: (
        accessToken: String,
        refreshToken: String,
        expiresAtEpochMs: Long,
        personName: String
    ) -> Unit = { _, _, _, _ -> },
    onPlanningCenterDisconnect: () -> Unit = {}
) {
    val onScheduleChangedState = rememberUpdatedState(onScheduleChanged)

    val viewModel =
        scheduleViewModel ?: remember {
            ScheduleViewModel(onScheduleChanged = { items -> onScheduleChangedState.value?.invoke(items) })
        }
    val scope = rememberCoroutineScope()

    var showAutoRestoreDialog by remember { mutableStateOf(viewModel.shouldPromptAutoRestore()) }
    if (showAutoRestoreDialog) {
        val savedAt = remember { viewModel.autoSaveSavedAt() }
        val timeStr = remember(savedAt) {
            SimpleDateFormat("h:mm a").format(Date(savedAt))
        }
        AlertDialog(
            onDismissRequest = { showAutoRestoreDialog = false },
            title = { Text(stringResource(Res.string.autosave_restore_title)) },
            text = { Text(stringResource(Res.string.autosave_restore_message, timeStr)) },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(6.dp),
                    onClick = {
                    viewModel.restoreAutoSave()
                    showAutoRestoreDialog = false
                }) { Text(stringResource(Res.string.autosave_restore_confirm)) }
            },
            dismissButton = {
                TextButton(
                    shape = RoundedCornerShape(6.dp),
                    onClick = {
                    viewModel.clearAutoSave()
                    showAutoRestoreDialog = false
                }) { Text(stringResource(Res.string.autosave_restore_discard)) }
            }
        )
    }

    val strSaveScheduleAs = rememberUpdatedState(stringResource(Res.string.file_chooser_save_schedule))
    val strOpenSchedule   = rememberUpdatedState(stringResource(Res.string.file_chooser_open_schedule))
    val strFileFilter     = rememberUpdatedState(stringResource(Res.string.file_filter_schedule))

    LaunchedEffect(Unit) {
        onActionsReady(
            ScheduleTabActions(
                newSchedule      = { viewModel.newSchedule() },
                openSchedule     = { scope.launch { viewModel.loadSchedule(
                    strOpenSchedule.value,
                    strFileFilter.value
                ) } },
                saveSchedule     = { scope.launch { viewModel.saveSchedule(
                    strSaveScheduleAs.value,
                    strFileFilter.value
                ) } },
                saveScheduleAs   = { scope.launch { viewModel.saveScheduleAs(
                    strSaveScheduleAs.value,
                    strFileFilter.value
                ) } },
                removeSelected   = { viewModel.selectedItemId?.let { viewModel.removeItem(it) } },
                removeById       = { id -> viewModel.removeItem(id) },
                clearSchedule    = { viewModel.clearSchedule() },
                moveSelectedToTop    = { viewModel.selectedItemId?.let { viewModel.moveItemToTop(it) } },
                moveSelectedUp       = { viewModel.selectedItemId?.let { viewModel.moveItemUp(it) } },
                moveSelectedDown     = { viewModel.selectedItemId?.let { viewModel.moveItemDown(it) } },
                moveSelectedToBottom = { viewModel.selectedItemId?.let { viewModel.moveItemToBottom(it) } },
                addLabel    = { text, textColor, bg -> viewModel.addLabel(text, textColor, bg) },
                updateLabel = { id, text, textColor, bg -> viewModel.updateLabel(id, text, textColor, bg) },
                addBibleVerse    =
                    { bookName, chapter, verseNumber, verseText, verseRange, bookId -> viewModel.addBibleVerse(
                    bookName,
                    chapter,
                    verseNumber,
                    verseText,
                    verseRange,
                    bookId
                ) },
                addSong          = { songNumber, title, songbook, songId -> viewModel.addSong(
                    songNumber,
                    title,
                    songbook,
                    songId
                ) },
                addPicture       = { folderPath, folderName, imageCount -> viewModel.addPicture(
                    folderPath,
                    folderName,
                    imageCount
                ) },
                addPresentation  = { filePath, fileName, slideCount, fileType -> viewModel.addPresentation(
                    filePath,
                    fileName,
                    slideCount,
                    fileType
                ) },
                addMedia         = { mediaUrl, mediaTitle, mediaType -> viewModel.addMedia(
                    mediaUrl,
                    mediaTitle,
                    mediaType
                ) },
                addLowerThird    = { presetId, presetLabel, pauseAtFrame, pauseDurationMs -> viewModel.addLowerThird(
                    presetId,
                    presetLabel,
                    pauseAtFrame,
                    pauseDurationMs
                ) },
                addAnnouncement  =
                    {
                        text, textColor, backgroundColor, fontSize, fontType, bold, italic, underline, shadow,
                        shadowColor, shadowSize, shadowOpacity, horizontalAlignment, position, animationType,
                        animationDuration, loopCount, isTimer, timerHours, timerMinutes, timerSeconds,
                        timerTextColor, timerExpiredText, timerMode, targetHour, targetMinute, targetSecond,
                        liveClockFormat ->
                    viewModel.addAnnouncement(
                        text,
                        textColor,
                        backgroundColor,
                        fontSize,
                        fontType,
                        bold,
                        italic,
                        underline,
                        shadow,
                        shadowColor,
                        shadowSize,
                        shadowOpacity,
                        horizontalAlignment,
                        position,
                        animationType,
                        animationDuration,
                        loopCount,
                        isTimer,
                        timerHours,
                        timerMinutes,
                        timerSeconds,
                        timerTextColor,
                        timerExpiredText,
                        timerMode,
                        targetHour,
                        targetMinute,
                        targetSecond,
                        liveClockFormat
                    )
                },
                addWebsite       = { url, title -> viewModel.addWebsite(url, title) },
                updateWebsiteTitle = { url, title -> viewModel.updateWebsiteTitle(url, title) },
                addScene         = { sceneId, sceneName -> viewModel.addScene(sceneId, sceneName) },
                addDictionary    = { number, word, transliteration, definition -> viewModel.addDictionary(
                    number,
                    word,
                    transliteration,
                    definition
                ) }
            )
        )
    }

    LaunchedEffect(viewModel.selectedItemId) {
        onSelectedItemChanged(viewModel.selectedItemId)
    }

    val scheduleItems = viewModel.scheduleItems
    val selectedItemId = viewModel.selectedItemId
    var showPlanningCenterImport by remember { mutableStateOf(false) }
    val density = scheduleDensityFor(itemZoomPercent)

    Column(modifier = modifier.fillMaxSize()) {

        ScheduleHeader(
            itemCount = scheduleItems.count { it !is ScheduleItem.LabelItem },
            density = density,
            onZoomOut = { onItemZoomChange(scheduleZoomOut(itemZoomPercent)) },
            onZoomIn = { onItemZoomChange(scheduleZoomIn(itemZoomPercent)) },
            canZoomOut = scheduleCanZoomOut(itemZoomPercent),
            canZoomIn = scheduleCanZoomIn(itemZoomPercent),
            onNewSchedule = { viewModel.newSchedule() },
            onOpenSchedule = { scope.launch { viewModel.loadSchedule(strOpenSchedule.value, strFileFilter.value) } },
            onSaveSchedule = { scope.launch { viewModel.saveSchedule(strSaveScheduleAs.value, strFileFilter.value) } },
            canUndo = viewModel.canUndo,
            canRedo = viewModel.canRedo,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onAddLabel = onAddLabel,
            onImportPlanningCenter = { showPlanningCenterImport = true },
            onClearSchedule = { viewModel.clearSchedule() }
        )

        val viewModelState = rememberUpdatedState(viewModel)
        var listHeightPx by remember { mutableStateOf(0) }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { listHeightPx = it.height }
        ) {
            val listState = rememberLazyListState()

            var draggingFromIndex by remember { mutableStateOf(-1) }
            var dropTargetIndex by remember { mutableStateOf<Int?>(null) }
            var isDragActive by remember { mutableStateOf(false) }
            var dragCursorY by remember { mutableStateOf(0f) }
            var dragItemHeight by remember { mutableStateOf(50f) }
            var isOverDeleteZone by remember { mutableStateOf(false) }

            val baseDensity = LocalDensity.current

            val dragThresholdPx = with(baseDensity) { DRAG_HANDLE_THRESHOLD.toPx() }
            val deleteZonePx = with(baseDensity) { DELETE_ZONE_HEIGHT.toPx() }
            fun Modifier.reorderGesture(index: Int, requireShift: Boolean): Modifier =
                pointerInput(index, requireShift) {
                    awaitPointerEventScope {
                        while (true) {
                            val pressEvent = awaitPointerEvent(PointerEventPass.Initial)
                            if (pressEvent.type != PointerEventType.Press ||
                                (requireShift && !pressEvent.keyboardModifiers.isShiftPressed)
                            ) continue
                            if (requireShift) pressEvent.changes.forEach { it.consume() }

                            var lastPos = pressEvent.changes.first().position
                            var travelled = if (requireShift) dragThresholdPx else 0f
                            var armed = false
                            var dragging = true

                            fun endDrag() {
                                if (draggingFromIndex == index) draggingFromIndex = -1
                                dropTargetIndex = null
                                isDragActive = false
                                isOverDeleteZone = false
                                dragCursorY = 0f
                            }
                            try {
                            while (dragging) {

                                if (!armed && travelled >= dragThresholdPx && !isDragActive) {
                                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == index }
                                    draggingFromIndex = index
                                    isDragActive = true
                                    dropTargetIndex = index
                                    dragItemHeight = itemInfo?.size?.toFloat() ?: FALLBACK_DRAG_ITEM_HEIGHT
                                    dragCursorY = if (itemInfo != null) {
                                        itemInfo.offset + itemInfo.size / 2f
                                    } else lastPos.y
                                    armed = true
                                }
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (armed) event.changes.forEach { it.consume() }

                                val finished = event.type == PointerEventType.Release ||
                                    event.changes.none { it.pressed }
                                if (finished) {
                                    if (armed && draggingFromIndex == index) {
                                        val droppedId = scheduleItems.getOrNull(index)?.id
                                        if (isOverDeleteZone && droppedId != null) {
                                            viewModel.removeItem(droppedId)
                                            if (viewModel.selectedItemId == droppedId) {
                                                viewModel.clearSelection()
                                            }
                                        } else {
                                            val to = dropTargetIndex ?: index
                                            if (index != to) viewModel.moveItem(index, to)
                                        }
                                    }
                                    dragging = false
                                } else if (event.type == PointerEventType.Move) {
                                    val pos = event.changes.firstOrNull()?.position
                                    val deltaY = if (pos != null) (pos - lastPos).y else 0f
                                    if (pos != null) lastPos = pos

                                    if (pos != null && !armed) {
                                        travelled += abs(deltaY)
                                    } else if (pos != null) {
                                        dragCursorY += deltaY

                                        val hit = dragDropTarget(
                                            cursorY = dragCursorY,
                                            listHeightPx = listHeightPx,
                                            deleteZonePx = deleteZonePx,
                                            visibleItems = listState.layoutInfo.visibleItemsInfo.map {
                                                DragItemGeometry(it.index, it.offset, it.size)
                                            },
                                        )
                                        isOverDeleteZone = hit.overDeleteZone
                                        if (!hit.overDeleteZone) {
                                            hit.targetIndex?.let { dropTargetIndex = it }
                                        }
                                    }
                                }
                            }
                            } finally {
                                if (armed) endDrag()
                            }
                        }
                    }
                }

            DisposableEffect(Unit) {
                val awtWindow = java.awt.Window.getWindows().firstOrNull { it.isShowing }
                val dropTarget = awtWindow?.let { win ->
                    DropTarget(win, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
                        override fun drop(event: DropTargetDropEvent) {
                            event.acceptDrop(DnDConstants.ACTION_COPY)
                            try {
                                val transferable = event.transferable
                                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                    @Suppress("UNCHECKED_CAST")
                                    val files =
                                        transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                                    val vm = viewModelState.value
                                    handleDroppedFiles(files, vm)
                                }
                                event.dropComplete(true)
                            } catch (e: Exception) {
                                event.dropComplete(false)
                            }
                        }
                    }, true)
                }
                onDispose {
                    if (dropTarget != null) {
                        awtWindow.dropTarget = null
                    }
                }
            }

            if (scheduleItems.isEmpty()) {

                Text(
                    text = stringResource(Res.string.schedule_drop_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }

            val rows = scheduleItems.toList()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .padding(top = 6.dp, bottom = 10.dp, end = 4.dp)
            ) {

                itemsIndexed(rows, key = { _, item -> item.id }) { index, item ->
                    val isDraggingThis = isDragActive && draggingFromIndex == index

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .padding(bottom = 3.dp)
                            .alpha(if (isDraggingThis) DRAGGED_ITEM_ALPHA else 1f)
                            .reorderGesture(index, requireShift = true)
                    ) {
                        ScheduleItemRow(
                            item = item,
                            dragHandleModifier = Modifier.reorderGesture(index, requireShift = false),
                            density = density,
                            isSelected = item.id == selectedItemId,
                            note = viewModel.getNote(item.id),
                            onSelect = {
                                if (!isDragActive) {
                                    viewModel.selectItem(item.id)
                                    onItemClick(item)
                                }
                            },
                            onMoveUp   = { viewModel.moveItemUp(item.id) },
                            onMoveDown = { viewModel.moveItemDown(item.id) },
                            onRemove = {
                                viewModel.removeItem(item.id)
                                if (selectedItemId == item.id) viewModel.clearSelection()
                            },
                            onPresent = {
                                viewModel.presentItem(
                                    item = item,
                                    onPresenting = onPresenting,
                                    onPresentSong = onPresentSong,
                                    onPresentBible = onPresentBible,
                                    onPresentPresentation = onPresentPresentation,
                                    onPresentPictures = onPresentPictures,
                                    onPresentMedia = onPresentMedia,
                                    onPresentAnnouncement = onPresentAnnouncement,
                                    onPresentLowerThird = onPresentLowerThird,
                                    onPresentWebsite = onPresentWebsite,
                                    onPresentDictionary = onPresentDictionary,
                                    onPresentScene = onPresentScene
                                )
                            },
                            onEditLabel = {
                                if (item is ScheduleItem.LabelItem) onEditLabel(item)
                            },
                            onNoteChanged = { viewModel.setNote(item.id, it) }
                        )
                    }
                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState)
            )

            if (isDragActive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(DELETE_ZONE_HEIGHT)
                        .zIndex(DRAG_TARGET_Z_INDEX)
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = if (isOverDeleteZone) 0.9f else 0.25f),
                            RoundedCornerShape(4.dp)
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete),
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

            if (isDragActive) {
                val dragItem = scheduleItems.getOrNull(draggingFromIndex)
                dragItem?.let { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(DRAGGED_ITEM_Z_INDEX)
                            .graphicsLayer {
                                translationY = dragCursorY - dragItemHeight / 2
                                scaleX = DRAGGED_ITEM_SCALE
                                scaleY = DRAGGED_ITEM_SCALE
                                shadowElevation = DRAGGED_ITEM_ELEVATION
                            }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CARD_SHAPE)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = scheduleItemGlyph(item),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp)
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
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            ScheduleAddFilesButton(
                onClick = {
                    scope.launch {
                        val files = FileChooser.platformInstance.chooseMultiple(
                            path = null,
                            title = "Add Files to Schedule",
                            filters = emptyList(),
                            selectDirectory = false
                        )
                        if (files != null) {
                            handleDroppedFiles(files.map(Path::toFile), viewModel)
                        }
                    }
                }
            )
        }

        PlanningCenterImportDialog(
            isVisible = showPlanningCenterImport,
            theme = theme,
            settings = planningCenterSettings,
            onDismiss = { showPlanningCenterImport = false },
            onTokensRefreshed = onPlanningCenterTokensRefreshed,
            onAddSong = { songNumber, title, songbook, songId ->
                viewModel.addSong(songNumber, title, songbook, songId)
            },
            onAddLabel = { text, textColor, backgroundColor ->
                viewModel.addLabel(text, textColor, backgroundColor)
            },
            onAddPresentation = { filePath, fileName, slideCount, fileType ->
                viewModel.addPresentation(filePath, fileName, slideCount, fileType)
            },
            onAddPicture = { folderPath, folderName, imageCount ->
                viewModel.addPicture(folderPath, folderName, imageCount)
            },
            onAddMedia = { mediaUrl, mediaTitle, mediaType ->
                viewModel.addMedia(mediaUrl, mediaTitle, mediaType)
            },
            onAddAnnouncement = { text ->
                viewModel.addAnnouncement(text = text)
            },
            onAddBibleVerse = { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
                viewModel.addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange, bookId)
            },
            onConnected = onPlanningCenterConnected,
            onDisconnect = onPlanningCenterDisconnect
        )
    }
}

internal val ACTION_BUTTON_SIZE = 30.dp
internal val ACTION_ICON_SIZE = 13.dp
internal val SECTION_ACTION_BUTTON_SIZE = 22.dp
internal val SECTION_ACTION_ICON_SIZE = 12.dp

internal val SECTION_ROW_PADDING = 3.dp
