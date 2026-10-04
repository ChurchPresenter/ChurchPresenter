package org.churchpresenter.schedule

import org.churchpresenter.calendar.model.UpcomingLoad
import org.churchpresenter.calendar.ScheduleServiceLink
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.jetbrains.compose.resources.getString
import org.churchpresenter.strings.generated.resources.schedule_add_files_title
import org.churchpresenter.strings.generated.resources.tooltip_open_schedule
import org.churchpresenter.strings.generated.resources.file_chooser_save_schedule
import org.churchpresenter.strings.generated.resources.file_filter_schedule
import org.churchpresenter.strings.generated.resources.autosave_restore_confirm
import org.churchpresenter.strings.generated.resources.autosave_restore_discard
import org.churchpresenter.strings.generated.resources.autosave_restore_message
import org.churchpresenter.strings.generated.resources.autosave_restore_title
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.calendar.model.planDrift
import kotlinx.coroutines.delay
import org.churchpresenter.calendar.model.scheduleClocks
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import org.churchpresenter.calendar.model.RowClock
import org.churchpresenter.calendar.model.PlanDrift
import androidx.compose.runtime.Stable

/**
 * `awtTransferable` is the one part of a drop a test cannot reach, so it is all these two do — the
 * reading itself is `carriesFileList`/`fileListOrNull`, which a test drives with a stand-in
 * `Transferable`. Both swallow: a drag whose data cannot be read is simply a drag this panel does
 * not take, and there is nothing to tell the operator at that point.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("SwallowedException")
private fun DragAndDropEvent.carriesFiles(): Boolean = try {
    awtTransferable.carriesFileList()
} catch (_: Exception) {
    false
}

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("SwallowedException")
private fun DragAndDropEvent.droppedFiles(): List<File>? = try {
    awtTransferable.fileListOrNull()
} catch (_: Exception) {
    null
}

/** How often the live row's behind/ahead badge is re-reckoned. */
private const val DRIFT_TICK_MS = 1_000L

/** Matches the app's other toasts, which clear themselves rather than wait to be dismissed. */
private const val SKIPPED_FILES_DISMISS_MS = 6_000L

/** Test handle for the message naming what a drop could not add. */
internal const val SCHEDULE_SKIPPED_FILES_TAG = "schedule_skipped_files"

private const val ZOOM_DEFAULT = 100

internal val CARD_SHAPE = AppShape(12.dp)

internal object ScheduleToolbarTags {
    const val UNDO = "schedule_undo"
    const val REDO = "schedule_redo"
    const val OPTIONS = "schedule_options"
    const val OPTIONS_LEGACY_ACTIONS = "schedule_options_legacy_actions"
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
    onPresentCue: ((ScheduleItem.CueItem) -> Unit)? = null,
    onActionsReady: (ScheduleTabActions) -> Unit = {},
    onSelectedItemChanged: (String?) -> Unit = {},
    onScheduleChanged: ((List<ScheduleItem>) -> Unit)? = null,
    onAddLabel: () -> Unit = {},
    /** The planned service the calendar will load here by itself next -- announced under Add Files. */
    upcomingServiceLoad: UpcomingLoad? = null,
    /** Loads [upcomingServiceLoad] now: into a cleared Schedule, or after what is there. */
    onLoadServiceNow: (replace: Boolean) -> Unit = {},
    /** The planned service these rows came from, offered a save when they have changed. */
    scheduleService: ScheduleServiceLink? = null,
    onSaveScheduleToCalendar: () -> Unit = {},
    /** Offers to put a Schedule built here by hand on the calendar; null where there is no calendar. */
    onAddScheduleToCalendar: (() -> Unit)? = null,
    itemZoomPercent: Int = ZOOM_DEFAULT,
    onItemZoomChange: (Int) -> Unit = {},
    legacyRowActions: Boolean = false,
    onLegacyRowActionsChange: (Boolean) -> Unit = {},
    hiddenToolbarButtons: Set<String> = emptySet(),
    onToggleToolbarButton: (ScheduleToolbarButton) -> Unit = {},
    toolbarIconSize: ScheduleToolbarIconSize = ScheduleToolbarIconSize.SMALL,
    onToolbarIconSizeChange: (ScheduleToolbarIconSize) -> Unit = {},
    /** The Planning Center import dialog, which the app supplies; shown while `isVisible`. */
    planningCenterImport: @Composable (isVisible: Boolean, onDismiss: () -> Unit) -> Unit = { _, _ -> },
) {
    val onScheduleChangedState = rememberUpdatedState(onScheduleChanged)

    val viewModel = scheduleViewModel ?: remember {
        ScheduleViewModel(onScheduleChanged = { items -> onScheduleChangedState.value?.invoke(items) })
    }
    viewModel.openFailure?.let { failure -> ScheduleOpenFailedDialog(failure) { viewModel.openFailure = null } }
    ScheduleAutoRestorePrompt(viewModel)
    val files = rememberScheduleFileCommands(viewModel)

    LaunchedEffect(Unit) {
        onActionsReady(viewModel.tabActions(files))
    }

    LaunchedEffect(viewModel.selectedItemId) {
        onSelectedItemChanged(viewModel.selectedItemId)
    }

    val scheduleItems = viewModel.scheduleItems
    var showPlanningCenterImport by remember { mutableStateOf(false) }
    val density = scheduleDensityFor(itemZoomPercent)
    val fileDrop = rememberScheduleFileDrop(viewModel)

    Column(modifier = modifier.fillMaxSize()) {

        ScheduleHeader(
            itemCount = scheduleItems.count { it !is ScheduleItem.LabelItem },
            density = density,
            onZoomOut = { onItemZoomChange(scheduleZoomOut(itemZoomPercent)) },
            onZoomIn = { onItemZoomChange(scheduleZoomIn(itemZoomPercent)) },
            canZoomOut = scheduleCanZoomOut(itemZoomPercent),
            canZoomIn = scheduleCanZoomIn(itemZoomPercent),
            onNewSchedule = { viewModel.newSchedule() },
            onOpenSchedule = files.open,
            onSaveSchedule = files.save,
            canUndo = viewModel.canUndo,
            canRedo = viewModel.canRedo,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onAddLabel = onAddLabel,
            onImportPlanningCenter = { showPlanningCenterImport = true },
            onOpenCalendar = LocalOpenCalendar.current,
            onClearSchedule = { viewModel.clearSchedule() },
            canClear = scheduleItems.isNotEmpty(),
            legacyRowActions = legacyRowActions,
            onLegacyRowActionsChange = onLegacyRowActionsChange,
            hiddenButtons = hiddenToolbarButtons,
            onToggleButton = onToggleToolbarButton,
            toolbarIconSize = toolbarIconSize,
            onToolbarIconSizeChange = onToolbarIconSizeChange
        )

        // When each row is expected to go live, reckoned from the first pinned row across the
        // whole schedule -- so every row shows a time, not only the ones carrying a pin.
        val rowClocks = remember(scheduleItems, viewModel.timing, viewModel.serviceStartTime) {
            scheduleClocks(scheduleItems, viewModel.timing, viewModel.serviceStartTime)
        }
        ScheduleRowList(
            viewModel = viewModel,
            look = ScheduleRowLook(
                density = density,
                legacyRowActions = legacyRowActions,
                rowClocks = rowClocks,
                drift = rememberLiveDrift(viewModel, rowClocks),
            ),
            onRowClick = { item ->
                viewModel.selectItem(item.id)
                onItemClick(item)
            },
            onPresent = { item ->
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
                    onPresentScene = onPresentScene,
                    onPresentCue = onPresentCue,
                )
            },
            onEditLabel = onEditLabel,
            fileDrop = fileDrop,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        ScheduleTabFooter(
            viewModel = viewModel,
            fileDrop = fileDrop,
            upcomingServiceLoad = upcomingServiceLoad,
            onLoadServiceNow = onLoadServiceNow,
            scheduleService = scheduleService,
            onSaveScheduleToCalendar = onSaveScheduleToCalendar,
            onAddScheduleToCalendar = onAddScheduleToCalendar,
        )

        planningCenterImport(showPlanningCenterImport) { showPlanningCenterImport = false }
    }
}

/** Open, Save and Save As, each launched behind its dialog with the tab's own strings. */
@Composable
private fun rememberScheduleFileCommands(viewModel: ScheduleViewModel): ScheduleFileCommands {
    val scope = rememberCoroutineScope()
    val strSaveScheduleAs = rememberUpdatedState(stringResource(Res.string.file_chooser_save_schedule))
    val strOpenSchedule   = rememberUpdatedState(stringResource(Res.string.tooltip_open_schedule))
    val strFileFilter     = rememberUpdatedState(stringResource(Res.string.file_filter_schedule))
    return remember(viewModel) {
        ScheduleFileCommands(
            open = { scope.launch { viewModel.loadSchedule(strOpenSchedule.value, strFileFilter.value) } },
            save = { scope.launch { viewModel.saveSchedule(strSaveScheduleAs.value, strFileFilter.value) } },
            saveAs = { scope.launch { viewModel.saveScheduleAs(strSaveScheduleAs.value, strFileFilter.value) } },
        )
    }
}

/** Add Files, and the calendar's notices under it: the next service, and saving these rows to it. */
@Composable
private fun ScheduleTabFooter(
    viewModel: ScheduleViewModel,
    fileDrop: ScheduleFileDrop,
    upcomingServiceLoad: UpcomingLoad?,
    onLoadServiceNow: (replace: Boolean) -> Unit,
    scheduleService: ScheduleServiceLink?,
    onSaveScheduleToCalendar: () -> Unit,
    onAddScheduleToCalendar: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScheduleAddFilesButton(
            onClick = {
                scope.launch {
                    val picked = FileChooser.platformInstance.chooseMultiple(
                        path = null,
                        title = getString(Res.string.schedule_add_files_title),
                        filters = emptyList(),
                        selectDirectory = false
                    )
                    if (picked != null) {
                        // Picked by hand or dragged in, an unusable file gets the same answer.
                        fileDrop.skippedFiles = handleDroppedFiles(picked.map(Path::toFile), viewModel)
                    }
                }
            }
        )
        if (upcomingServiceLoad != null) {
            ScheduleLoadNowNotice(
                upcoming = upcomingServiceLoad,
                // Counted as the header counts them, so the two never disagree.
                itemCount = viewModel.scheduleItems.count { it !is ScheduleItem.LabelItem },
                scheduleIsEmpty = viewModel.scheduleItems.isEmpty(),
                onLoadServiceNow = onLoadServiceNow,
            )
        }
        if (scheduleService?.hasChanges == true) {
            ScheduleSaveToCalendarNotice(service = scheduleService, onSave = onSaveScheduleToCalendar)
        }
        // Built here by hand rather than loaded from the calendar: offer to put it there.
        if (onAddScheduleToCalendar != null && scheduleService == null && viewModel.scheduleItems.isNotEmpty()) {
            ScheduleAddToCalendarNotice(onAdd = onAddScheduleToCalendar)
        }
    }
}

/** Offers this morning's autosave back, once per session -- see `shouldPromptAutoRestore`. */
@Composable
private fun ScheduleAutoRestorePrompt(viewModel: ScheduleViewModel) {
    var showAutoRestoreDialog by remember { mutableStateOf(viewModel.shouldPromptAutoRestore()) }
    if (!showAutoRestoreDialog) return
    val savedAt = remember { viewModel.autoSaveSavedAt() }
    val timeStr = remember(savedAt) {
        SimpleDateFormat("h:mm a").format(Date(savedAt))
    }
    AlertDialog(
        onDismissRequest = { showAutoRestoreDialog = false },
        title = { Text(stringResource(Res.string.autosave_restore_title)) },
        text = { Text(stringResource(Res.string.autosave_restore_message, timeStr)) },
        confirmButton = {
            RaisedButton(
                shape = AppShape(6.dp),
                onClick = {
                viewModel.restoreAutoSave()
                showAutoRestoreDialog = false
            }) { Text(stringResource(Res.string.autosave_restore_confirm)) }
        },
        dismissButton = {
            GhostButton(
                shape = AppShape(6.dp),
                onClick = {
                viewModel.clearAutoSave()
                showAutoRestoreDialog = false
            }) { Text(stringResource(Res.string.autosave_restore_discard)) }
        }
    )
}

/** The calendar's next service, with Load now -- asking first when there are rows to lose. */
@Composable
private fun ScheduleLoadNowNotice(
    upcoming: UpcomingLoad,
    itemCount: Int,
    scheduleIsEmpty: Boolean,
    onLoadServiceNow: (replace: Boolean) -> Unit,
) {
    var confirmLoadNow by remember { mutableStateOf(false) }
    ScheduleAutoLoadNotice(
        upcoming = upcoming,
        onLoadNow = {
            // Nothing to lose, nothing to ask.
            if (scheduleIsEmpty) onLoadServiceNow(true) else confirmLoadNow = true
        },
    )
    if (confirmLoadNow) {
        LoadServiceNowConfirm(
            serviceName = upcoming.serviceName,
            itemCount = itemCount,
            onChoose = { replace ->
                confirmLoadNow = false
                onLoadServiceNow(replace)
            },
            onDismiss = { confirmLoadNow = false },
        )
    }
}

/**
 * How far the service is from its plan, on the row that is live: reckoned from when it went live
 * against when the plan said, and growing once it overruns its length -- so it ticks. Null when
 * nothing is live or the plan has no time for it.
 */
@Composable
private fun rememberLiveDrift(viewModel: ScheduleViewModel, rowClocks: Map<String, RowClock>): PlanDrift? {
    val liveRowId = viewModel.liveRowId
    val liveSince = viewModel.liveSince
    var driftNow by remember { mutableStateOf(viewModel.clock()) }
    LaunchedEffect(liveRowId, liveSince) {
        while (liveRowId != null) {
            driftNow = viewModel.clock()
            delay(DRIFT_TICK_MS)
        }
    }
    return remember(liveRowId, liveSince, driftNow, rowClocks, viewModel.timing) {
        if (liveRowId == null || liveSince == null) {
            null
        } else {
            planDrift(liveRowId, liveSince, driftNow, rowClocks, viewModel.timing)
        }
    }
}

/**
 * Files dragged in from outside the app: whether one is over the panel, and the names the last
 * drop could not use, which clear themselves like the app's other toasts.
 */
@Composable
private fun rememberScheduleFileDrop(viewModel: ScheduleViewModel): ScheduleFileDrop {
    val viewModelState = rememberUpdatedState(viewModel)
    val drop = remember { ScheduleFileDrop { viewModelState.value } }
    // A message about a drop is stale the moment the next one lands, and keying on the list
    // restarts the countdown when it does.
    LaunchedEffect(drop.skippedFiles) {
        if (drop.skippedFiles.isNotEmpty()) {
            delay(SKIPPED_FILES_DISMISS_MS)
            drop.skippedFiles = emptyList()
        }
    }
    return drop
}

/** Compose's own drop target, attached to the panel's node so it cannot aim at the wrong window. */
@Stable
internal class ScheduleFileDrop(private val viewModel: () -> ScheduleViewModel) : DragAndDropTarget {
    /** True while a file from outside the app is being dragged over this panel. */
    var dragOver by mutableStateOf(false)

    /** The files the last drop could not use, named so the message can say which. */
    var skippedFiles by mutableStateOf(emptyList<String>())

    override fun onEntered(event: DragAndDropEvent) { dragOver = true }
    override fun onExited(event: DragAndDropEvent) { dragOver = false }
    override fun onEnded(event: DragAndDropEvent) { dragOver = false }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        dragOver = false
        val files = event.droppedFiles() ?: return false
        skippedFiles = handleDroppedFiles(files, viewModel())
        return true
    }

    fun accepts(event: DragAndDropEvent): Boolean = event.carriesFiles()
}

internal val ACTION_BUTTON_SIZE = 30.dp
internal val ACTION_ICON_SIZE = 13.dp
internal val SECTION_ACTION_BUTTON_SIZE = 22.dp
internal val SECTION_ACTION_ICON_SIZE = 12.dp

internal val SECTION_ROW_PADDING = 3.dp
