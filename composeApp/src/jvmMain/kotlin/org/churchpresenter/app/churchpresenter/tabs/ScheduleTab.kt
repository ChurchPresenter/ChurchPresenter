package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.clear_schedule
import churchpresenter.composeapp.generated.resources.file_chooser_open_schedule
import churchpresenter.composeapp.generated.resources.file_chooser_save_schedule
import churchpresenter.composeapp.generated.resources.file_filter_schedule
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.schedule
import churchpresenter.composeapp.generated.resources.tooltip_edit_label
import churchpresenter.composeapp.generated.resources.tooltip_go_live
import churchpresenter.composeapp.generated.resources.tooltip_move_down
import churchpresenter.composeapp.generated.resources.tooltip_move_up
import churchpresenter.composeapp.generated.resources.tooltip_remove
import org.churchpresenter.app.churchpresenter.composables.LivePreviewPanel
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
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
    val addSong: (songNumber: Int, title: String, songbook: String) -> Unit = { _, _, _ -> },
    val addPicture: (folderPath: String, folderName: String, imageCount: Int) -> Unit = { _, _, _ -> },
    val addPresentation: (filePath: String, fileName: String, slideCount: Int, fileType: String) -> Unit = { _, _, _, _ -> },
    val addMedia: (mediaUrl: String, mediaTitle: String, mediaType: String) -> Unit = { _, _, _ -> },
    val addLowerThird: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit = { _, _, _, _ -> },
    val addAnnouncement: (text: String, textColor: String, backgroundColor: String, fontSize: Int, fontType: String, bold: Boolean, italic: Boolean, underline: Boolean, shadow: Boolean, position: String, animationType: String, animationDuration: Int, isTimer: Boolean, timerMinutes: Int, timerSeconds: Int, timerTextColor: String, timerExpiredText: String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    val addWebsite: (url: String, title: String) -> Unit = { _, _ -> }
)

@Composable
fun ScheduleTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings? = null,
    presenterManager: PresenterManager? = null,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    onItemClick: (ScheduleItem) -> Unit = {},
    onEditLabel: (ScheduleItem.LabelItem) -> Unit = {},
    onPresentSong: ((ScheduleItem.SongItem) -> Unit)? = null,
    onPresentBible: ((ScheduleItem.BibleVerseItem) -> Unit)? = null,
    onPresentPresentation: ((ScheduleItem.PresentationItem) -> Unit)? = null,
    onPresentPictures: ((ScheduleItem.PictureItem) -> Unit)? = null,
    onPresentMedia: ((ScheduleItem.MediaItem) -> Unit)? = null,
    onActionsReady: (ScheduleTabActions) -> Unit = {},
    onSelectedItemChanged: (String?) -> Unit = {}
) {
    val viewModel = remember { ScheduleViewModel() }

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
                addSong          = { songNumber, title, songbook -> viewModel.addSong(songNumber, title, songbook) },
                addPicture       = { folderPath, folderName, imageCount -> viewModel.addPicture(folderPath, folderName, imageCount) },
                addPresentation  = { filePath, fileName, slideCount, fileType -> viewModel.addPresentation(filePath, fileName, slideCount, fileType) },
                addMedia         = { mediaUrl, mediaTitle, mediaType -> viewModel.addMedia(mediaUrl, mediaTitle, mediaType) },
                addLowerThird    = { presetId, presetLabel, pauseAtFrame, pauseDurationMs -> viewModel.addLowerThird(presetId, presetLabel, pauseAtFrame, pauseDurationMs) },
                addAnnouncement  = { text, textColor, backgroundColor, fontSize, fontType, bold, italic, underline, shadow, position, animationType, animationDuration, isTimer, timerMinutes, timerSeconds, timerTextColor, timerExpiredText ->
                    viewModel.addAnnouncement(text, textColor, backgroundColor, fontSize, fontType, bold, italic, underline, shadow, position, animationType, animationDuration, isTimer, timerMinutes, timerSeconds, timerTextColor, timerExpiredText)
                },
                addWebsite       = { url, title -> viewModel.addWebsite(url, title) }
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

        // Live preview — scaled-down replica of the presenter window
        if (presenterManager != null && appSettings != null) {
            LivePreviewPanel(
                presenterManager = presenterManager,
                appSettings = appSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // Schedule list header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.schedule),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = { viewModel.clearSchedule() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = stringResource(Res.string.clear_schedule),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondary,
                    maxLines = 1
                )
            }
        }

        // Schedule items list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val listState = rememberLazyListState()

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
                                onPresentMedia = onPresentMedia
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
                    text = if (item.pauseAtFrame) "Pause ${item.pauseDurationMs}ms" else "",
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
