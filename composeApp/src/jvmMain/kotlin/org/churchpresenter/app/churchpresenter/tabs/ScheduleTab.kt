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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.clear_schedule
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
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun ScheduleTab(
    modifier: Modifier = Modifier,
    scheduleViewModel: ScheduleViewModel,
    songsViewModel: SongsViewModel,
    bibleViewModel: BibleViewModel,
    picturesViewModel: PicturesViewModel? = null,
    presentationViewModel: PresentationViewModel? = null,
    presenterManager: PresenterManager? = null,
    onSongItemSelected: (LyricSection) -> Unit,
    onVerseSelected: (List<SelectedVerse>) -> Unit,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    onItemClick: (ScheduleItem) -> Unit = {},
    onEditLabel: (ScheduleItem.LabelItem) -> Unit = {}
) {
    var selectedItemId by remember { mutableStateOf<String?>(null) }

    // Get schedule items
    val scheduleItems = scheduleViewModel.scheduleItems

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
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
                onClick = { scheduleViewModel.clearSchedule() },
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
                            selectedItemId = if (selectedItemId == item.id) null else item.id
                            onItemClick(item)
                        },
                        onClick = { onItemClick(item) },
                        onMoveUp = {
                            scheduleViewModel.moveItemUp(item.id)
                        },
                        onMoveDown = {
                            scheduleViewModel.moveItemDown(item.id)
                        },
                        onRemove = {
                            scheduleViewModel.removeItem(item.id)
                            if (selectedItemId == item.id) {
                                selectedItemId = null
                            }
                        },
                        onPresent = {
                            when (item) {
                                is ScheduleItem.SongItem -> {
                                    // Select the song in ViewModel
                                    songsViewModel.selectSongByDetails(
                                        songNumber = item.songNumber,
                                        title = item.title,
                                        songbook = item.songbook
                                    )
                                    // Get the selected song section
                                    songsViewModel.getSelectedLyricSection()?.let { section ->
                                        onSongItemSelected(section)
                                    }
                                    // Present it
                                    onPresenting(Presenting.LYRICS)
                                }
                                is ScheduleItem.BibleVerseItem -> {
                                    // Select the verse in ViewModel
                                    bibleViewModel.selectVerseByDetails(
                                        bookName = item.bookName,
                                        chapter = item.chapter,
                                        verseNumber = item.verseNumber
                                    )
                                    // Get the selected verses
                                    val verses = bibleViewModel.getSelectedVerses()
                                    onVerseSelected(verses)
                                    // Present it
                                    onPresenting(Presenting.BIBLE)
                                }
                                is ScheduleItem.LabelItem -> {
                                    // Labels are not presentable, do nothing
                                }
                                is ScheduleItem.PictureItem -> {
                                    // Load the folder and present the first image
                                    if (picturesViewModel != null && presenterManager != null) {
                                        val folder = File(item.folderPath)
                                        if (folder.exists() && folder.isDirectory) {
                                            // Load the folder into the pictures view model
                                            picturesViewModel.selectFolder(folder)

                                            // Get the first image
                                            val firstImage = picturesViewModel.getCurrentImageFile()
                                            if (firstImage != null) {
                                                presenterManager.setSelectedImagePath(firstImage.absolutePath)
                                                presenterManager.setPresentingMode(Presenting.PICTURES)
                                                presenterManager.setShowPresenterWindow(true)
                                            }
                                        }
                                    }
                                }
                                is ScheduleItem.PresentationItem -> {
                                    // Load the presentation and present the first slide
                                    if (presentationViewModel != null && presenterManager != null) {
                                        val file = File(item.filePath)
                                        if (file.exists()) {
                                            // Load the presentation into the view model
                                            presentationViewModel.loadPresentationByPath(item.filePath)

                                            // TODO: Present the first slide
                                            // For now, just switch to presentation mode
                                            // This will be implemented when presentation display is ready
                                            presenterManager.setPresentingMode(Presenting.NONE)
                                            presenterManager.setShowPresenterWindow(false)
                                        }
                                    }
                                }
                            }
                        },
                        onEditLabel = {
                            if (item is ScheduleItem.LabelItem) {
                                onEditLabel(item)
                            }
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
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPresent: () -> Unit,
    onEditLabel: () -> Unit = {}
) {
    // For labels, use the label's background color, otherwise use default row background
    val rowBackgroundColor = if (item is ScheduleItem.LabelItem) {
        org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor(item.backgroundColor)
    } else {
        if (isSelected)
            MaterialTheme.colorScheme.surfaceVariant
        else
            MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackgroundColor)
            .then(
                // Only make non-label items clickable
                if (item !is ScheduleItem.LabelItem) {
                    Modifier.clickable {
                        onSelect()
                        onClick()
                    }
                } else {
                    Modifier
                }
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
                        color = org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor(item.textColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    Text(
                        maxLines = 1,
                        text = item.displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            when (item) {
                is ScheduleItem.SongItem -> {
                    Text(
                        maxLines = 1,
                        text = item.songbook,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                is ScheduleItem.BibleVerseItem -> {
                    Text(
                        maxLines = 1,
                        text = item.verseText.take(100) + if (item.verseText.length > 100) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                is ScheduleItem.LabelItem -> {
                    // Labels don't have secondary text
                }
                is ScheduleItem.PictureItem -> {
                    Text(
                        maxLines = 1,
                        text = item.folderPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                is ScheduleItem.PresentationItem -> {
                    Text(
                        maxLines = 1,
                        text = "${item.fileType.uppercase()} - ${item.filePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
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

