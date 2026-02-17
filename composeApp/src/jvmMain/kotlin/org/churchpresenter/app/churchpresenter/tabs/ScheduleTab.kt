package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ScheduleTab(
    modifier: Modifier = Modifier,
    scheduleViewModel: ScheduleViewModel,
    songsViewModel: org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel,
    bibleViewModel: org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel,
    onSongItemSelected: (org.churchpresenter.app.churchpresenter.models.LyricSection) -> Unit,
    onVerseSelected: (List<org.churchpresenter.app.churchpresenter.models.SelectedVerse>) -> Unit,
    onPresenting: (Presenting) -> Unit = { Presenting.NONE },
    onItemClick: (ScheduleItem) -> Unit = {}
) {
    var selectedItemIndex by remember { mutableStateOf(-1) }

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
                        isSelected = index == selectedItemIndex,
                        onSelect = { selectedItemIndex = if (selectedItemIndex == index) -1 else index },
                        onClick = { onItemClick(item) },
                        onMoveUp = { scheduleViewModel.moveItemUp(item.id) },
                        onMoveDown = { scheduleViewModel.moveItemDown(item.id) },
                        onRemove = { scheduleViewModel.removeItem(item.id) },
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
    onPresent: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.surface
            )
            .clickable {
                onSelect()
                onClick()
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Type indicator
        Text(
            text = when (item) {
                is ScheduleItem.SongItem -> "♪"
                is ScheduleItem.BibleVerseItem -> "✝"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp)
        )

        // Item content
        Column(modifier = Modifier.weight(1f)) {
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
            }
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Move up button
            IconButton(
                onClick = onMoveUp,
                modifier = Modifier.size(32.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_arrow_up),
                    contentDescription = "Move up",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Move down button
            IconButton(
                onClick = onMoveDown,
                modifier = Modifier.size(32.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_arrow_down),
                    contentDescription = "Move down",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Go Live button
            IconButton(
                onClick = onPresent,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_play),
                    contentDescription = "Go Live",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = "Remove",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

