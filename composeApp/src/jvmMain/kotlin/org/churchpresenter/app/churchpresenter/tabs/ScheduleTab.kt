package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.file_chooser_open_schedule
import churchpresenter.composeapp.generated.resources.file_chooser_save_schedule
import churchpresenter.composeapp.generated.resources.file_filter_schedule
import churchpresenter.composeapp.generated.resources.schedule_add_files
import churchpresenter.composeapp.generated.resources.schedule_drop_hint
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_down_double
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_arrow_up_double
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_folder
import churchpresenter.composeapp.generated.resources.ic_label
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_save
import churchpresenter.composeapp.generated.resources.ic_web
import churchpresenter.composeapp.generated.resources.schedule
import churchpresenter.composeapp.generated.resources.tooltip_add_label
import churchpresenter.composeapp.generated.resources.tooltip_add_to_schedule
import churchpresenter.composeapp.generated.resources.tooltip_add_website
import churchpresenter.composeapp.generated.resources.tooltip_clear_schedule
import churchpresenter.composeapp.generated.resources.tooltip_edit_label
import churchpresenter.composeapp.generated.resources.tooltip_go_live
import churchpresenter.composeapp.generated.resources.tooltip_move_down
import churchpresenter.composeapp.generated.resources.tooltip_move_to_bottom
import churchpresenter.composeapp.generated.resources.tooltip_move_to_top
import churchpresenter.composeapp.generated.resources.tooltip_move_up
import churchpresenter.composeapp.generated.resources.tooltip_new_schedule
import churchpresenter.composeapp.generated.resources.tooltip_open_schedule
import churchpresenter.composeapp.generated.resources.tooltip_remove
import churchpresenter.composeapp.generated.resources.tooltip_remove_from_schedule
import churchpresenter.composeapp.generated.resources.tooltip_save_schedule
import churchpresenter.composeapp.generated.resources.pause_duration_ms
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    val clearSchedule: () -> Unit = {},
    val moveSelectedToTop: () -> Unit = {},
    val moveSelectedUp: () -> Unit = {},
    val moveSelectedDown: () -> Unit = {},
    val moveSelectedToBottom: () -> Unit = {},
    val addLabel: (text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _ -> },
    val updateLabel: (id: String, text: String, textColor: String, backgroundColor: String) -> Unit = { _, _, _, _ -> },
    val addBibleVerse: (bookName: String, chapter: Int, verseNumber: Int, verseText: String) -> Unit = { _, _, _, _ -> },
    val addSong: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit = { _, _, _, _ -> },
    val addPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit = { _, _, _ -> },
    val addPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit = { _, _, _, _ -> },
    val addMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit = { _, _, _ -> },
    val addLowerThird: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> },
    val addAnnouncement: (text: String, textColor: String, backgroundColor: String, fontSize: Int, fontType: String, bold: Boolean, italic: Boolean, underline: Boolean, shadow: Boolean, horizontalAlignment: String, position: String, animationType: String, animationDuration: Int, isTimer: Boolean, timerHours: Int, timerMinutes: Int, timerSeconds: Int, timerTextColor: String, timerExpiredText: String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    val addWebsite: (url: String, title: String) -> Unit = { _, _ -> },
    val updateWebsiteTitle: (url: String, title: String) -> Unit = { _, _ -> }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleTab(
    modifier: Modifier = Modifier,
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
    onActionsReady: (ScheduleTabActions) -> Unit = {},
    onSelectedItemChanged: (String?) -> Unit = {},
    onScheduleChanged: ((List<ScheduleItem>) -> Unit)? = null,
    onAddLabel: () -> Unit = {},
    onAddWebsite: () -> Unit = {},
    onAddToSchedule: () -> Unit = {}
) {
    val onScheduleChangedState = rememberUpdatedState(onScheduleChanged)
    val viewModel = remember { ScheduleViewModel(onScheduleChanged = { items -> onScheduleChangedState.value?.invoke(items) }) }

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
                openSchedule     = { viewModel.loadSchedule(strOpenSchedule.value, strFileFilter.value) },
                saveSchedule     = { viewModel.saveSchedule(strSaveScheduleAs.value, strFileFilter.value) },
                saveScheduleAs   = { viewModel.saveScheduleAs(strSaveScheduleAs.value, strFileFilter.value) },
                removeSelected   = { viewModel.selectedItemId?.let { viewModel.removeItem(it) } },
                clearSchedule    = { viewModel.clearSchedule() },
                moveSelectedToTop    = { viewModel.selectedItemId?.let { viewModel.moveItemToTop(it) } },
                moveSelectedUp       = { viewModel.selectedItemId?.let { viewModel.moveItemUp(it) } },
                moveSelectedDown     = { viewModel.selectedItemId?.let { viewModel.moveItemDown(it) } },
                moveSelectedToBottom = { viewModel.selectedItemId?.let { viewModel.moveItemToBottom(it) } },
                addLabel    = { text, textColor, bg -> viewModel.addLabel(text, textColor, bg) },
                updateLabel = { id, text, textColor, bg -> viewModel.updateLabel(id, text, textColor, bg) },
                addBibleVerse    = { bookName, chapter, verseNumber, verseText -> viewModel.addBibleVerse(bookName, chapter, verseNumber, verseText) },
                addSong          = { songNumber, title, songbook, songId -> viewModel.addSong(songNumber, title, songbook, songId) },
                addPicture       = { folderPath, folderName, imageCount -> viewModel.addPicture(folderPath, folderName, imageCount) },
                addPresentation  = { filePath, fileName, slideCount, fileType -> viewModel.addPresentation(filePath, fileName, slideCount, fileType) },
                addMedia         = { mediaUrl, mediaTitle, mediaType -> viewModel.addMedia(mediaUrl, mediaTitle, mediaType) },
                addLowerThird    = { presetId, presetLabel, pauseAtFrame, pauseDurationMs -> viewModel.addLowerThird(presetId, presetLabel, pauseAtFrame, pauseDurationMs) },
                addAnnouncement  = { text, textColor, backgroundColor, fontSize, fontType, bold, italic, underline, shadow, horizontalAlignment, position, animationType, animationDuration, isTimer, timerHours, timerMinutes, timerSeconds, timerTextColor, timerExpiredText ->
                    viewModel.addAnnouncement(text, textColor, backgroundColor, fontSize, fontType, bold, italic, underline, shadow, horizontalAlignment, position, animationType, animationDuration, isTimer, timerHours, timerMinutes, timerSeconds, timerTextColor, timerExpiredText)
                },
                addWebsite       = { url, title -> viewModel.addWebsite(url, title) },
                updateWebsiteTitle = { url, title -> viewModel.updateWebsiteTitle(url, title) }
            )
        )
    }

    // Notify parent when selection changes
    LaunchedEffect(viewModel.selectedItemId) {
        onSelectedItemChanged(viewModel.selectedItemId)
    }

    val scheduleItems = viewModel.scheduleItems
    val selectedItemId = viewModel.selectedItemId

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
                onClick = { viewModel.loadSchedule(strOpenSchedule.value, strFileFilter.value) },
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_save),
                text = stringResource(Res.string.tooltip_save_schedule),
                onClick = { viewModel.saveSchedule(strSaveScheduleAs.value, strFileFilter.value) },
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
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

            Spacer(modifier = Modifier.width(4.dp))

            // Add / Remove / Clear
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_playlist_add),
                text = stringResource(Res.string.tooltip_add_to_schedule),
                onClick = onAddToSchedule,
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
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

        // Schedule header + move arrows (buttons wrap as a group)
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
            // Wrap all 4 buttons in a Row so FlowRow treats them as one item
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_arrow_up_double),
                    text = stringResource(Res.string.tooltip_move_to_top),
                    onClick = { viewModel.selectedItemId?.let { viewModel.moveItemToTop(it) } },
                    buttonSize = 32.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_arrow_up),
                    text = stringResource(Res.string.tooltip_move_up),
                    onClick = { viewModel.selectedItemId?.let { viewModel.moveItemUp(it) } },
                    buttonSize = 32.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_arrow_down),
                    text = stringResource(Res.string.tooltip_move_down),
                    onClick = { viewModel.selectedItemId?.let { viewModel.moveItemDown(it) } },
                    buttonSize = 32.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_arrow_down_double),
                    text = stringResource(Res.string.tooltip_move_to_bottom),
                    onClick = { viewModel.selectedItemId?.let { viewModel.moveItemToBottom(it) } },
                    buttonSize = 32.dp,
                    iconTint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Schedule items list with drag-and-drop support
        val viewModelState = rememberUpdatedState(viewModel)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val listState = rememberLazyListState()

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
                itemsIndexed(scheduleItems) { index, item ->
                    ScheduleItemRow(
                        item = item,
                        isSelected = item.id == selectedItemId,
                        onSelect = {
                            viewModel.selectItem(item.id)
                            onItemClick(item)
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
                                onPresentWebsite = onPresentWebsite
                            )
                        },
                        onEditLabel = {
                            if (item is ScheduleItem.LabelItem) onEditLabel(item)
                        }
                    )

                    if (index < scheduleItems.size - 1) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState)
            )
        }

        // Add Files button at the bottom
        Button(
            onClick = {
                SwingUtilities.invokeLater {
                    val chooser = createFileChooser {
                        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
                        isMultiSelectionEnabled = true
                        dialogTitle = "Add Files to Schedule"
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        handleDroppedFiles(chooser.selectedFiles.toList(), viewModel)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(stringResource(Res.string.schedule_add_files))
        }
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
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPresent: () -> Unit,
    onEditLabel: () -> Unit = {}
) {
    val rowBackgroundColor = if (item is ScheduleItem.LabelItem) {
        Utils.parseHexColor(item.backgroundColor)
    } else {
        if (isSelected) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackgroundColor)
            .then(
                if (item !is ScheduleItem.LabelItem) Modifier.clickable { onSelect() }
                else Modifier
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Type indicator
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
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp)
        )

        // Item content
        Column(modifier = Modifier.weight(1f)) {
            when (item) {
                is ScheduleItem.LabelItem -> {
                    // Display label text with custom text color
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Utils.parseHexColor(item.textColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    Text(
                        maxLines = 1,
                        text = item.displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            when (item) {
                is ScheduleItem.SongItem -> Text(
                    maxLines = 1,
                    text = item.songbook,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
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
                    if (item.isTimer) {
                        Text(
                            maxLines = 1,
                            text = "%02d:%02d".format(item.timerMinutes, item.timerSeconds),
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
            }
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Move up button
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_arrow_up),
                text = stringResource(Res.string.tooltip_move_up),
                onClick = onMoveUp,
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )

            // Move down button
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_arrow_down),
                text = stringResource(Res.string.tooltip_move_down),
                onClick = onMoveDown,
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurface
            )

            // Show Edit button for labels, Go Live button for other items
            if (item is ScheduleItem.LabelItem) {
                TooltipIconButton(
                    painter = painterResource(Res.drawable.ic_edit),
                    text = stringResource(Res.string.tooltip_edit_label),
                    onClick = onEditLabel,
                    buttonSize = 32.dp,
                    iconSize = 18.dp,
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
                    buttonSize = 32.dp,
                    iconSize = 18.dp,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    iconTint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Remove button
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_close),
                text = stringResource(Res.string.tooltip_remove),
                onClick = onRemove,
                buttonSize = 32.dp,
                iconTint = MaterialTheme.colorScheme.error
            )
        }
    }
}
