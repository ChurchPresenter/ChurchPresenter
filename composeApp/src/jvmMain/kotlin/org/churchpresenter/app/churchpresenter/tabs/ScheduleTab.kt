package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.app.churchpresenter.composables.finalPassCombinedClickable
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.file_chooser_open_schedule
import churchpresenter.composeapp.generated.resources.file_chooser_save_schedule
import churchpresenter.composeapp.generated.resources.file_filter_schedule
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_drag_dots
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.ic_label
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_check
import churchpresenter.composeapp.generated.resources.ic_note
import churchpresenter.composeapp.generated.resources.ic_redo
import churchpresenter.composeapp.generated.resources.ic_save
import churchpresenter.composeapp.generated.resources.ic_undo
import churchpresenter.composeapp.generated.resources.ic_zoom_in
import churchpresenter.composeapp.generated.resources.ic_zoom_out
import churchpresenter.composeapp.generated.resources.pause_duration_ms
import churchpresenter.composeapp.generated.resources.planning_center_import_title
import churchpresenter.composeapp.generated.resources.schedule
import churchpresenter.composeapp.generated.resources.schedule_note_placeholder
import churchpresenter.composeapp.generated.resources.tooltip_note
import churchpresenter.composeapp.generated.resources.tooltip_note_clear
import churchpresenter.composeapp.generated.resources.tooltip_note_done
import churchpresenter.composeapp.generated.resources.tooltip_redo
import churchpresenter.composeapp.generated.resources.tooltip_undo
import churchpresenter.composeapp.generated.resources.autosave_restore_confirm
import churchpresenter.composeapp.generated.resources.autosave_restore_discard
import churchpresenter.composeapp.generated.resources.autosave_restore_message
import churchpresenter.composeapp.generated.resources.autosave_restore_title
import churchpresenter.composeapp.generated.resources.schedule_add_files
import churchpresenter.composeapp.generated.resources.schedule_drop_hint
import churchpresenter.composeapp.generated.resources.schedule_drop_to_remove
import churchpresenter.composeapp.generated.resources.tooltip_add_label
import churchpresenter.composeapp.generated.resources.tooltip_clear_schedule
import churchpresenter.composeapp.generated.resources.tooltip_edit_label
import churchpresenter.composeapp.generated.resources.tooltip_go_live
import churchpresenter.composeapp.generated.resources.tooltip_move_down
import churchpresenter.composeapp.generated.resources.tooltip_schedule_zoom_in
import churchpresenter.composeapp.generated.resources.tooltip_schedule_zoom_out
import churchpresenter.composeapp.generated.resources.tooltip_move_up
import churchpresenter.composeapp.generated.resources.tooltip_new_schedule
import churchpresenter.composeapp.generated.resources.tooltip_open_schedule
import churchpresenter.composeapp.generated.resources.tooltip_remove
import churchpresenter.composeapp.generated.resources.tooltip_remove_from_schedule
import churchpresenter.composeapp.generated.resources.tooltip_save_schedule
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.data.settings.PlanningCenterSettings
import org.churchpresenter.app.churchpresenter.dialogs.PlanningCenterImportDialog
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
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

/**
 * Actions exposed to the parent composable so toolbar/menu can drive the schedule
 * without holding a reference to [ScheduleViewModel].
 */
data class ScheduleTabActions(
    val newSchedule: () -> Unit = {},
    val openSchedule: () -> Unit = {},
    val saveSchedule: () -> Unit = {},
    val saveScheduleAs: () -> Unit = {},
    val removeSelected: () -> Unit = {},
    /** Removes a specific item by id, regardless of current UI selection — used to apply an
     *  approved remote "remove from schedule" request (mobile companion or Instance Link). */
    val removeById: (id: String) -> Unit = {},
    val clearSchedule: () -> Unit = {},
    val moveSelectedToTop: () -> Unit = {},
    val moveSelectedUp: () -> Unit = {},
    val moveSelectedDown: () -> Unit = {},
    val moveSelectedToBottom: () -> Unit = {},
    val addLabel: (text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _ -> },
    val updateLabel: (id: String, text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _, _ -> },
    val addBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String, bookId: Int) -> Unit = { _, _, _, _, _, _ -> },
    val addSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit = { _, _, _, _ -> },
    val addPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit = { _, _, _ -> },
    val addPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit = { _, _, _, _ -> },
    val addMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit = { _, _, _ -> },
    val addLowerThird: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> },
    val addAnnouncement: (text: String, textColor: String, backgroundColor: String, fontSize: Int, fontType: String, bold: Boolean, italic: Boolean, underline: Boolean, shadow: Boolean, shadowColor: String, shadowSize: Int, shadowOpacity: Int, horizontalAlignment: String, position: String, animationType: String, animationDuration: Int, loopCount: Int, isTimer: Boolean, timerHours: Int, timerMinutes: Int, timerSeconds: Int, timerTextColor: String, timerExpiredText: String, timerMode: String, targetHour: Int, targetMinute: Int, targetSecond: Int, liveClockFormat: String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    val addWebsite: (url: String, title: String) -> Unit = { _, _ -> },
    val updateWebsiteTitle: (url: String, title: String) -> Unit = { _, _ -> },
    val addScene: (sceneId: String, sceneName: String) -> Unit = { _, _ -> },
    val addDictionary: (number: String, word: String, transliteration: String, definition: String) -> Unit = { _, _, _, _ -> }
)

/**
 * Card zoom rungs, as a percentage of the normal card size. The two rungs just below 100 exist
 * to strip the card down before it starts shrinking: 99 drops the action buttons, 98 collapses
 * the card to a single line (see [ZOOM_HIDE_ACTIONS_BELOW] / [ZOOM_SINGLE_LINE_AT_OR_BELOW]).
 */
private val ZOOM_LEVELS = listOf(70, 80, 90, 98, 99, 100, 110, 120, 130, 140, 150)
private const val ZOOM_DEFAULT = 100
private const val ZOOM_HIDE_ACTIONS_BELOW = 100
private const val ZOOM_SINGLE_LINE_AT_OR_BELOW = 98

/** How far the pointer must travel on a card's icon before it becomes a reorder drag. */
private val DRAG_HANDLE_THRESHOLD = 4.dp

/** Height of the drop-here-to-remove zone at the bottom of the list, shown while dragging. */
private val DELETE_ZONE_HEIGHT = 56.dp

/** The rung nearest [percent], so a value persisted before the ladder existed still resolves. */
private fun nearestZoomIndex(percent: Int): Int =
    ZOOM_LEVELS.indices.minBy { abs(ZOOM_LEVELS[it] - percent) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleTab(
    modifier: Modifier = Modifier,
    /** Optional pre-created view model. Pass one from a parent composable to survive panel
     *  collapse/expand (i.e. when this composable leaves the composition inside AnimatedVisibility).
     *  If null a local view model is created and owned by this composable. */
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
    onAddWebsite: () -> Unit = {},
    theme: ThemeMode = ThemeMode.SYSTEM,
    itemZoomPercent: Int = ZOOM_DEFAULT,
    onItemZoomChange: (Int) -> Unit = {},
    planningCenterSettings: PlanningCenterSettings = PlanningCenterSettings(),
    onPlanningCenterTokensRefreshed: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long) -> Unit = { _, _, _ -> },
    onPlanningCenterConnected: (accessToken: String, refreshToken: String, expiresAtEpochMs: Long, personName: String) -> Unit = { _, _, _, _ -> },
    onPlanningCenterDisconnect: () -> Unit = {}
) {
    val onScheduleChangedState = rememberUpdatedState(onScheduleChanged)
    // Use the provided view model, or fall back to a locally-owned one.
    val viewModel = scheduleViewModel ?: remember { ScheduleViewModel(onScheduleChanged = { items -> onScheduleChangedState.value?.invoke(items) }) }
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

    // State holders — lambdas capture the State object, not the string value,
    // so they always read the latest value via .value without re-registering.
    val strSaveScheduleAs = rememberUpdatedState(stringResource(Res.string.file_chooser_save_schedule))
    val strOpenSchedule   = rememberUpdatedState(stringResource(Res.string.file_chooser_open_schedule))
    val strFileFilter     = rememberUpdatedState(stringResource(Res.string.file_filter_schedule))

    // Register actions once — no recomposition cycle.
    LaunchedEffect(Unit) {
        onActionsReady(
            ScheduleTabActions(
                newSchedule      = { viewModel.newSchedule() },
                openSchedule     = { scope.launch { viewModel.loadSchedule(strOpenSchedule.value, strFileFilter.value) } },
                saveSchedule     = { scope.launch { viewModel.saveSchedule(strSaveScheduleAs.value, strFileFilter.value) } },
                saveScheduleAs   = { scope.launch { viewModel.saveScheduleAs(strSaveScheduleAs.value, strFileFilter.value) } },
                removeSelected   = { viewModel.selectedItemId?.let { viewModel.removeItem(it) } },
                removeById       = { id -> viewModel.removeItem(id) },
                clearSchedule    = { viewModel.clearSchedule() },
                moveSelectedToTop    = { viewModel.selectedItemId?.let { viewModel.moveItemToTop(it) } },
                moveSelectedUp       = { viewModel.selectedItemId?.let { viewModel.moveItemUp(it) } },
                moveSelectedDown     = { viewModel.selectedItemId?.let { viewModel.moveItemDown(it) } },
                moveSelectedToBottom = { viewModel.selectedItemId?.let { viewModel.moveItemToBottom(it) } },
                addLabel    = { text, textColor, bg -> viewModel.addLabel(text, textColor, bg) },
                updateLabel = { id, text, textColor, bg -> viewModel.updateLabel(id, text, textColor, bg) },
                addBibleVerse    = { bookName, chapter, verseNumber, verseText, verseRange, bookId -> viewModel.addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange, bookId) },
                addSong          = { songNumber, title, songbook, songId -> viewModel.addSong(songNumber, title, songbook, songId) },
                addPicture       = { folderPath, folderName, imageCount -> viewModel.addPicture(folderPath, folderName, imageCount) },
                addPresentation  = { filePath, fileName, slideCount, fileType -> viewModel.addPresentation(filePath, fileName, slideCount, fileType) },
                addMedia         = { mediaUrl, mediaTitle, mediaType -> viewModel.addMedia(mediaUrl, mediaTitle, mediaType) },
                addLowerThird    = { presetId, presetLabel, pauseAtFrame, pauseDurationMs -> viewModel.addLowerThird(presetId, presetLabel, pauseAtFrame, pauseDurationMs) },
                addAnnouncement  = { text, textColor, backgroundColor, fontSize, fontType, bold, italic, underline, shadow, shadowColor, shadowSize, shadowOpacity, horizontalAlignment, position, animationType, animationDuration, loopCount, isTimer, timerHours, timerMinutes, timerSeconds, timerTextColor, timerExpiredText, timerMode, targetHour, targetMinute, targetSecond, liveClockFormat ->
                    viewModel.addAnnouncement(text, textColor, backgroundColor, fontSize, fontType, bold, italic, underline, shadow, shadowColor, shadowSize, shadowOpacity, horizontalAlignment, position, animationType, animationDuration, loopCount, isTimer, timerHours, timerMinutes, timerSeconds, timerTextColor, timerExpiredText, timerMode, targetHour, targetMinute, targetSecond, liveClockFormat)
                },
                addWebsite       = { url, title -> viewModel.addWebsite(url, title) },
                updateWebsiteTitle = { url, title -> viewModel.updateWebsiteTitle(url, title) },
                addScene         = { sceneId, sceneName -> viewModel.addScene(sceneId, sceneName) },
                addDictionary    = { number, word, transliteration, definition -> viewModel.addDictionary(number, word, transliteration, definition) }
            )
        )
    }

    // Notify parent when selection changes
    LaunchedEffect(viewModel.selectedItemId) {
        onSelectedItemChanged(viewModel.selectedItemId)
    }

    val scheduleItems = viewModel.scheduleItems
    val selectedItemId = viewModel.selectedItemId
    var showPlanningCenterImport by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {

        // Schedule toolbar buttons — row 1: file ops, label/website, add/remove/clear
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // File operations
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_add),
                text = stringResource(Res.string.tooltip_new_schedule),
                onClick = { viewModel.newSchedule() },
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_folder),
                text = stringResource(Res.string.tooltip_open_schedule),
                onClick = { scope.launch { viewModel.loadSchedule(strOpenSchedule.value, strFileFilter.value) } },
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_save),
                text = stringResource(Res.string.tooltip_save_schedule),
                onClick = { scope.launch { viewModel.saveSchedule(strSaveScheduleAs.value, strFileFilter.value) } },
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Undo / Redo
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_undo),
                text = stringResource(Res.string.tooltip_undo),
                onClick = { viewModel.undo() },
                enabled = viewModel.canUndo,
                buttonSize = 32.dp,
                iconTint = if (viewModel.canUndo) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_redo),
                text = stringResource(Res.string.tooltip_redo),
                onClick = { viewModel.redo() },
                enabled = viewModel.canRedo,
                buttonSize = 32.dp,
                iconTint = if (viewModel.canRedo) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Label
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_label),
                text = stringResource(Res.string.tooltip_add_label),
                onClick = onAddLabel,
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )

            // Import from Planning Center
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Default.CloudDownload),
                text = stringResource(Res.string.planning_center_import_title),
                onClick = { showPlanningCenterImport = true },
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Remove / Clear
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_close),
                text = stringResource(Res.string.tooltip_remove_from_schedule),
                onClick = { viewModel.selectedItemId?.let { viewModel.removeItem(it) } },
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_delete),
                text = stringResource(Res.string.tooltip_clear_schedule),
                onClick = { viewModel.clearSchedule() },
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Schedule header + zoom controls (buttons wrap as a group)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.schedule),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 6.dp)
            )
            // Card zoom controls (own Row so FlowRow wraps them as one group)
            val zoomIndex = nearestZoomIndex(itemZoomPercent)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_zoom_out),
                    text = stringResource(Res.string.tooltip_schedule_zoom_out),
                    onClick = { onItemZoomChange(ZOOM_LEVELS[zoomIndex - 1]) },
                    enabled = zoomIndex > 0,
                    buttonSize = 32.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_zoom_in),
                    text = stringResource(Res.string.tooltip_schedule_zoom_in),
                    onClick = { onItemZoomChange(ZOOM_LEVELS[zoomIndex + 1]) },
                    enabled = zoomIndex < ZOOM_LEVELS.lastIndex,
                    buttonSize = 32.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Schedule items list with drag-and-drop support
        val viewModelState = rememberUpdatedState(viewModel)
        var listHeightPx by remember { mutableStateOf(0) }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .onSizeChanged { listHeightPx = it.height }
        ) {
            val listState = rememberLazyListState()

            // Drag-to-reorder state: shift+click+drag anywhere on a card, or plain drag on its icon
            var draggingFromIndex by remember { mutableStateOf(-1) }
            var dropTargetIndex by remember { mutableStateOf<Int?>(null) }
            var isDragActive by remember { mutableStateOf(false) }
            var dragCursorY by remember { mutableStateOf(0f) }
            var dragItemHeight by remember { mutableStateOf(50f) }
            var isOverDeleteZone by remember { mutableStateOf(false) }

            // Card zoom: applied per item by overriding LocalDensity around ScheduleItemRow.
            // Below 100% the card also sheds parts, before it starts shrinking.
            val baseDensity = LocalDensity.current
            val zoomedDensity = remember(baseDensity, itemZoomPercent) {
                Density(baseDensity.density * itemZoomPercent / 100f, baseDensity.fontScale)
            }
            val showCardActions = itemZoomPercent >= ZOOM_HIDE_ACTIONS_BELOW
            val singleLineCards = itemZoomPercent <= ZOOM_SINGLE_LINE_AT_OR_BELOW

            // Reorder gesture, shared by the whole-card shift+drag and the per-card icon handle.
            // The handle path arms only after real movement so a plain click still selects the card.
            val dragThresholdPx = with(baseDensity) { DRAG_HANDLE_THRESHOLD.toPx() }
            val deleteZonePx = with(baseDensity) { DELETE_ZONE_HEIGHT.toPx() }
            fun Modifier.reorderGesture(index: Int, requireShift: Boolean): Modifier =
                pointerInput(index, requireShift) {
                    awaitPointerEventScope {
                        while (true) {
                            val pressEvent = awaitPointerEvent(PointerEventPass.Initial)
                            if (pressEvent.type != PointerEventType.Press) continue
                            if (requireShift && !pressEvent.keyboardModifiers.isShiftPressed) continue
                            if (requireShift) pressEvent.changes.forEach { it.consume() }

                            var lastPos = pressEvent.changes.first().position
                            var travelled = if (requireShift) dragThresholdPx else 0f
                            var armed = false
                            var dragging = true
                            // Idempotent: called on the normal drop path AND from finally, so a
                            // cancelled gesture (node disposed mid-drag) can never strand the
                            // state — a stuck isDragActive freezes the whole sidebar.
                            fun endDrag() {
                                if (draggingFromIndex == index) draggingFromIndex = -1
                                dropTargetIndex = null
                                isDragActive = false
                                isOverDeleteZone = false
                                dragCursorY = 0f
                            }
                            try {
                            while (dragging) {
                                // Another card already owns this drag (a shift+press on the grip
                                // reaches both gestures) — don't arm a second one over it
                                if (!armed && travelled >= dragThresholdPx && !isDragActive) {
                                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == index }
                                    draggingFromIndex = index
                                    isDragActive = true
                                    dropTargetIndex = index
                                    dragItemHeight = itemInfo?.size?.toFloat() ?: 50f
                                    dragCursorY = if (itemInfo != null) {
                                        itemInfo.offset + itemInfo.size / 2f
                                    } else lastPos.y
                                    armed = true
                                }
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (armed) event.changes.forEach { it.consume() }
                                // A drop outside every card's bounds can arrive as something
                                // other than Release, so treat "nothing pressed" as the end too
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
                                    val pos = event.changes.firstOrNull()?.position ?: continue
                                    val deltaY = (pos - lastPos).y
                                    lastPos = pos
                                    if (!armed) {
                                        travelled += abs(deltaY)
                                        continue
                                    }
                                    dragCursorY += deltaY
                                    // Over the delete zone the card is being removed, not reordered
                                    isOverDeleteZone = listHeightPx > 0 &&
                                        dragCursorY >= listHeightPx - deleteZonePx
                                    if (!isOverDeleteZone) {
                                        val target = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { info ->
                                                dragCursorY >= info.offset &&
                                                dragCursorY <= info.offset + info.size
                                            }
                                        if (target != null) dropTargetIndex = target.index
                                    }
                                }
                            }
                            } finally {
                                if (armed) endDrag()
                            }
                        }
                    }
                }

            // Register AWT DropTarget on the window for file drag-and-drop
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
                                    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
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
                // Empty state hint
                Text(
                    text = stringResource(Res.string.schedule_drop_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(end = 12.dp)
            ) {
                itemsIndexed(scheduleItems, key = { _, item -> item.id }) { index, item ->
                    val isDraggingThis = isDragActive && draggingFromIndex == index

                    Column(modifier = Modifier.animateItem()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isDraggingThis) 0.35f else 1f)
                            .reorderGesture(index, requireShift = true)
                    ) {
                        // Scaling the density scales every dp and sp inside the card at once
                        CompositionLocalProvider(LocalDensity provides zoomedDensity) {
                        ScheduleItemRow(
                            item = item,
                            dragHandleModifier = Modifier.reorderGesture(index, requireShift = false),
                            showActions = showCardActions,
                            singleLine = singleLineCards,
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

                    if (index < scheduleItems.size - 1) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                    } // Column
                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState)
            )

            // Drop-here-to-remove zone, only while a card is being dragged
            if (isDragActive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(DELETE_ZONE_HEIGHT)
                        .zIndex(5f)
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

            // Floating drag preview — elevated card follows cursor
            if (isDragActive) {
                val dragItem = scheduleItems.getOrNull(draggingFromIndex)
                dragItem?.let { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(10f)
                            .graphicsLayer {
                                translationY = dragCursorY - dragItemHeight / 2
                                scaleX = 1.04f
                                scaleY = 1.04f
                                shadowElevation = 20f
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (item) {
                                is ScheduleItem.SongItem -> "♪"
                                is ScheduleItem.BibleVerseItem -> "✝"
                                is ScheduleItem.LabelItem -> "🏷"
                                is ScheduleItem.PictureItem -> "📷"
                                is ScheduleItem.PresentationItem -> "📊"
                                is ScheduleItem.MediaItem -> "🎬"
                                is ScheduleItem.LowerThirdItem -> "▼"
                                is ScheduleItem.AnnouncementItem -> "📢"
                                is ScheduleItem.WebsiteItem -> "🌐"
                                is ScheduleItem.SceneItem -> "🎬"
                                is ScheduleItem.DictionaryItem -> "📖"
                            },
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

        // Add Files button at the bottom
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    shape = RoundedCornerShape(6.dp),
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
                    },
                    modifier = Modifier.width(200.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(stringResource(Res.string.schedule_add_files))
                }
            }
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

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
private val VIDEO_EXTENSIONS = setOf("mp4", "avi", "mov", "mkv", "webm")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac")
private val PRESENTATION_EXTENSIONS = setOf("ppt", "pptx", "key", "pdf")

private fun handleDroppedFiles(files: List<File>, viewModel: ScheduleViewModel) {
    for (file in files) {
        if (file.isDirectory) {
            // Folder dropped — count image files inside and add as pictures
            val imageCount = file.listFiles()?.count { child ->
                child.isFile && child.extension.lowercase() in IMAGE_EXTENSIONS
            } ?: 0
            if (imageCount > 0) {
                viewModel.addPicture(file.absolutePath, file.name, imageCount)
            }
            continue
        }

        val ext = file.extension.lowercase()
        when {
            ext in PRESENTATION_EXTENSIONS -> {
                viewModel.addPresentation(file.absolutePath, file.nameWithoutExtension, 0, ext)
            }
            ext in VIDEO_EXTENSIONS || ext in AUDIO_EXTENSIONS -> {
                viewModel.addMedia(file.absolutePath, file.nameWithoutExtension, "local")
            }
            ext in IMAGE_EXTENSIONS -> {
                // Single image dropped — add parent folder as picture source
                val parentFolder = file.parentFile
                val imageCount = parentFolder?.listFiles()?.count { child ->
                    child.isFile && child.extension.lowercase() in IMAGE_EXTENSIONS
                } ?: 1
                viewModel.addPicture(
                    parentFolder?.absolutePath ?: file.absolutePath,
                    parentFolder?.name ?: file.name,
                    imageCount
                )
            }
            ext == "json" -> {
                viewModel.addLowerThird(file.nameWithoutExtension, file.nameWithoutExtension, false, 0L)
            }
        }
    }
}

@Composable
private fun ScheduleItemRow(
    item: ScheduleItem,
    /** Applied to the type-indicator icon, which doubles as the drag-to-reorder handle. */
    dragHandleModifier: Modifier = Modifier,
    /** Below 100% zoom the action-button row (and with it the note editor) is dropped. */
    showActions: Boolean = true,
    /** At 98% zoom and below the card collapses to just its icon and primary line. */
    singleLine: Boolean = false,
    isSelected: Boolean,
    note: String,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPresent: () -> Unit,
    onEditLabel: () -> Unit = {},
    onNoteChanged: (String) -> Unit = {}
) {
    val rowBackgroundColor = if (item is ScheduleItem.LabelItem) {
        Utils.parseHexColor(item.backgroundColor)
    } else {
        if (isSelected) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
    }

    var noteExpanded by remember(item.id) { mutableStateOf(false) }
    var noteText by remember(item.id) { mutableStateOf(note) }

    // Sync local noteText when external note changes (e.g. after undo/redo)
    LaunchedEffect(note) {
        if (noteText != note) noteText = note
    }

    // The row-select/present click lives on two separately-scoped modifiers rather than one
    // wrapping the whole row: the content area uses Initial-pass (survives the LazyColumn
    // scroll-gesture-eats-clicks issue on ARM Mac, see ClickModifiers.kt), while the button
    // toolbar row below uses Final-pass so its own IconButtons (Remove, Move Up/Down, etc.) win
    // the hit-test first — an Initial-pass handler wrapping the whole row consumes every click
    // before a nested button's own Main-pass clickable ever sees it, which silently breaks every
    // button in the toolbar.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackgroundColor)
    ) {
        // The trailing Go Live button lives OUTSIDE the content row's clickable (an Initial-pass
        // handler would eat its clicks — see the comment above), so they sit side by side here.
        Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(
                    // Tight on the left so the grip dots sit at the very edge of the card
                    start = 2.dp,
                    end = if (showActions) 12.dp else 4.dp,
                    top = if (singleLine) 4.dp else 8.dp,
                    // Without the action row below, the card needs its own bottom padding
                    bottom = if (showActions) 2.dp else if (singleLine) 4.dp else 8.dp
                )
                .then(
                    if (item !is ScheduleItem.LabelItem) {
                        Modifier.initialPassCombinedClickable(
                            onClick = { onSelect() },
                            onDoubleClick = { onPresent() }
                        )
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grip dots + type indicator — together they form the drag-to-reorder handle
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .then(dragHandleModifier)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_drag_dots),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.width(4.dp).height(16.dp)
                )
                Text(
                    text = when (item) {
                        is ScheduleItem.SongItem -> "♪"
                        is ScheduleItem.BibleVerseItem -> "✝"
                        is ScheduleItem.LabelItem -> "🏷"
                        is ScheduleItem.PictureItem -> "📷"
                        is ScheduleItem.PresentationItem -> "📊"
                        is ScheduleItem.MediaItem -> "🎬"
                        is ScheduleItem.LowerThirdItem -> "▼"
                        is ScheduleItem.AnnouncementItem -> "📢"
                        is ScheduleItem.WebsiteItem -> "🌐"
                        is ScheduleItem.SceneItem -> "🎬"
                        is ScheduleItem.DictionaryItem -> "📖"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp)
                )
            }

            // Item content (row-wide click handling now lives on the outer Column above,
            // so both this content area and the button toolbar row below are selectable)
            Column(modifier = Modifier.weight(1f)) {
                when (item) {
                    is ScheduleItem.LabelItem -> {
                        // Display label text with custom text color
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Utils.parseHexColor(item.textColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is ScheduleItem.SongItem -> {
                        val textColor = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                        if (item.songNumber > 0) {
                            Text(
                                maxLines = 1,
                                text = item.songNumber.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                        Text(
                            maxLines = 1,
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                        if (item.songbook.isNotBlank() && !singleLine) {
                            Text(
                                maxLines = 1,
                                // Songbook names are often long paths/editions whose distinguishing
                                // part is at the END, so clip the front instead of the tail
                                overflow = TextOverflow.StartEllipsis,
                                text = item.songbook,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    else -> {
                        Text(
                            maxLines = 1,
                            text = item.displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                when {
                    singleLine -> {} // collapsed card: primary line only
                    else -> when (item) {
                    is ScheduleItem.SongItem -> {} // already handled above
                    is ScheduleItem.BibleVerseItem -> Text(
                        maxLines = 1,
                        text = item.verseText.take(100) + if (item.verseText.length > 100) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    is ScheduleItem.PictureItem -> Text(
                        maxLines = 1,
                        text = item.folderPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    is ScheduleItem.PresentationItem -> Text(
                        maxLines = 1,
                        text = "${item.fileType.uppercase()} - ${item.filePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    is ScheduleItem.MediaItem -> Text(
                        maxLines = 1,
                        text = "${item.mediaType.uppercase()} - ${item.mediaUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    is ScheduleItem.LowerThirdItem -> Text(
                        maxLines = 1,
                        text = if (item.pauseAtFrame) stringResource(Res.string.pause_duration_ms, item.pauseDurationMs) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    is ScheduleItem.LabelItem -> { /* no secondary text */ }
                    is ScheduleItem.AnnouncementItem -> {
                        // Duration (count-up) and the live Clock have no fixed h:m:s to preview here —
                        // their live value only exists once the item is triggered, so show nothing.
                        val timerSubtext = when (item.timerMode) {
                            "clock" -> "%02d:%02d:%02d".format(item.targetHour, item.targetMinute, item.targetSecond)
                            "count_up", "clock_display" -> null
                            else -> "%02d:%02d".format(item.timerMinutes, item.timerSeconds)
                        }
                        if (item.isTimer && timerSubtext != null) {
                            Text(
                                maxLines = 1,
                                text = timerSubtext,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    is ScheduleItem.WebsiteItem -> Text(
                        maxLines = 1,
                        text = item.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    is ScheduleItem.SceneItem -> { /* no secondary text */ }
                    is ScheduleItem.DictionaryItem -> Text(
                        maxLines = 1,
                        text = item.transliteration,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                }

                // Note preview when collapsed
                if (note.isNotEmpty() && !noteExpanded && !singleLine) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        // With the action row hidden, Go Live (Edit for labels) stays on the content row itself
        if (!showActions) {
            if (item is ScheduleItem.LabelItem) {
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_edit),
                    text = stringResource(Res.string.tooltip_edit_label),
                    onClick = onEditLabel,
                    modifier = Modifier.padding(end = 8.dp),
                    buttonSize = 24.dp,
                    iconSize = 14.dp,
                    iconTint = MaterialTheme.colorScheme.primary
                )
            } else {
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_play),
                    text = stringResource(Res.string.tooltip_go_live),
                    onClick = onPresent,
                    modifier = Modifier.padding(end = 8.dp),
                    buttonSize = 24.dp,
                    iconSize = 16.dp,
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }
        }
        }

        // Action buttons (second, more compact line) — dropped below 100% zoom
        if (showActions) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 12.dp, bottom = 4.dp)
                .then(
                    if (item !is ScheduleItem.LabelItem) {
                        Modifier.finalPassCombinedClickable(
                            onClick = { onSelect() },
                            onDoubleClick = { onPresent() }
                        )
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Remove button
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_close),
                text = stringResource(Res.string.tooltip_remove),
                onClick = onRemove,
                buttonSize = 28.dp,
                iconSize = 16.dp,
                iconTint = MaterialTheme.colorScheme.error
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move up button
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_arrow_up),
                    text = stringResource(Res.string.tooltip_move_up),
                    onClick = onMoveUp,
                    buttonSize = 28.dp,
                    iconSize = 16.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )

                // Move down button
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_arrow_down),
                    text = stringResource(Res.string.tooltip_move_down),
                    onClick = onMoveDown,
                    buttonSize = 28.dp,
                    iconSize = 16.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )

                // Note toggle button
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_note),
                    text = stringResource(Res.string.tooltip_note),
                    onClick = { noteExpanded = !noteExpanded },
                    buttonSize = 28.dp,
                    iconSize = 16.dp,
                    iconTint = if (note.isNotEmpty() || noteExpanded) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                // Show Edit button for labels, Go Live button for other items
                if (item is ScheduleItem.LabelItem) {
                    TooltipIconButton(
                        painter = painterResource(Res.drawable.ic_edit),
                        text = stringResource(Res.string.tooltip_edit_label),
                        onClick = onEditLabel,
                        buttonSize = 28.dp,
                        iconSize = 14.dp,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        iconTint = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    TooltipIconButton(
                        painter = painterResource(Res.drawable.ic_play),
                        text = stringResource(Res.string.tooltip_go_live),
                        onClick = onPresent,
                        buttonSize = 28.dp,
                        iconSize = 16.dp,
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Inline note editor
        AnimatedVisibility(visible = noteExpanded) {
            val noteInteractionSource = remember { MutableInteractionSource() }
            val noteFieldFocused by noteInteractionSource.collectIsFocusedAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, end = 12.dp, bottom = 8.dp)
                    .border(
                        width = 1.dp,
                        color = if (noteFieldFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(4.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 3,
                    interactionSource = noteInteractionSource,
                    decorationBox = { innerTextField ->
                        Box {
                            if (noteText.isEmpty()) {
                                Text(
                                    stringResource(Res.string.schedule_note_placeholder),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    thickness = 1.dp
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_check),
                    text = stringResource(Res.string.tooltip_note_done),
                    onClick = {
                        onNoteChanged(noteText)
                        noteExpanded = false
                    },
                    buttonSize = 36.dp,
                    iconSize = 18.dp,
                    iconTint = MaterialTheme.colorScheme.inverseSurface
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    thickness = 1.dp
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_close),
                    text = stringResource(Res.string.tooltip_note_clear),
                    onClick = {
                        noteText = ""
                        onNoteChanged("")
                    },
                    buttonSize = 36.dp,
                    iconSize = 18.dp,
                    iconTint = MaterialTheme.colorScheme.error
                )
            }
        }
        }
    }
}
