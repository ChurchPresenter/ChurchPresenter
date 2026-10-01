@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import churchpresenter.composeapp.generated.resources.songs_indexing
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_favorites
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.edit_song
import churchpresenter.composeapp.generated.resources.go_live
import androidx.compose.material.icons.filled.Tv
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.delete_saved_string
import churchpresenter.composeapp.generated.resources.ic_star
import churchpresenter.composeapp.generated.resources.ic_star_filled
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.remove_from_favorites
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.tune
import churchpresenter.composeapp.generated.resources.author
import churchpresenter.composeapp.generated.resources.composer
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.composables.finalPassCombinedClickable
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import churchpresenter.composeapp.generated.resources.song_search_match_lyrics
import churchpresenter.composeapp.generated.resources.song_search_match_translation
import org.churchpresenter.app.churchpresenter.utils.highlightedText
import org.churchpresenter.app.churchpresenter.viewmodel.SongMatchKind
import org.churchpresenter.app.churchpresenter.viewmodel.SongSearchMatch
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.MutableState

/** The song rows, their scroll-to-selection, and both scrollbars. */
@Composable
internal fun SongListScope.SongListBody(hScrollState: ScrollState, contentMinWidthDp: Dp, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
    Box(modifier = Modifier.weight(1f)) {
        if (isLoading && filteredSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.songs_indexing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val lazyListState = rememberLazyListState()

        LaunchedEffect(selectedSongIndex, filteredSongs.size) {
            if (selectedSongIndex >= 0 && selectedSongIndex < filteredSongs.size) {
                delay(SONG_LIST_SCROLL_SETTLE_MS)
                lazyListState.animateScrollToItem(selectedSongIndex)
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(hScrollState)
        ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .width(contentMinWidthDp)
                .fillMaxHeight()
                .padding(start = 6.dp, end = 8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(rowPad(1.dp)),
        ) {
            itemsIndexed(filteredSongs) { index, song ->
                    SongRow(index, song)
            }
        }
        } // end horizontalScroll Box
        } // end song list Row (spacer + scroll box)
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = lazyListState)
        )
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        HorizontalScrollbar(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            adapter = rememberScrollbarAdapter(hScrollState)
        )
    }
    } // end Column (song list + horizontal scrollbar)
}

@Composable
private fun SongListScope.SongRow(index: Int, song: SongItem) {
    val showContextMenuState = remember { mutableStateOf(false) }
    var showContextMenu by showContextMenuState
    val contextMenuOffsetState = remember { mutableStateOf(DpOffset.Zero) }
    var contextMenuOffset by contextMenuOffsetState
    val isRowSelected = index == selectedSongIndex
    val (rowHover, rowHovered) = rememberRowHover()
    val rowColors = bibleRowColors(isRowSelected, rowHovered)
    val match = searchMatchFor(song)
    Box {
    // The row and the search-match line under it are one target: selected, clicked,
    // double-clicked and right-clicked together.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BibleListRowShape)
            .background(rowColors.background)
            .hoverable(rowHover)
            .finalPassCombinedClickable(
                onClick = {
                    onSelectSong(index)
                    if (isPresenting && live.songId != null) {
                        onSelectSection(-1)
                    }
                    tabFocusRequester.requestFocus()
                },
                // Double-click sends it, the same four steps the context menu's
                // Go Live runs -- and the same convention the schedule rows and the
                // Bible panels already use.
                onDoubleClick = {
                    onSelectSong(index)
                    sendToPresenter(true)
                    onPresenting(Presenting.LYRICS)
                    tabFocusRequester.requestFocus()
                },
            )
            .padding(vertical = rowPad(8.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Press &&
                            event.button?.isSecondary == true
                        ) {
                            val pos = event.changes.first().position
                            contextMenuOffset = with(density) {
                                DpOffset(pos.x.toDp(), pos.y.toDp())
                            }
                            showContextMenu = true
                        }
                    }
                }
            },
    ) {
    val textColor = if (isRowSelected) rowColors.ink else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        // All columns in visibleCols order — data cols use per-cell initialPassClickable,
        // action cols are inline so reordering them is reflected in both header and rows
        visibleCols.forEach { colId ->
            if (colId !in actionCols) {
                SongDataCell(colId, index, song, match, textColor)
            } else {
                SongActionCell(colId, song)
            }
        }
    }
    if (match != null) {
        SongMatchLine(
            match = match,
            query = searchQuery,
            indent = titleOffset(visibleCols, actionCols) { with(density) { colWidth(it).toDp() } },
        )
    }
    }
    SongRowContextMenu(index, song, showContextMenuState, contextMenuOffset)
    } // Box
}

@Composable
private fun SongListScope.SongDataCell(
    colId: String,
    index: Int,
    song: SongItem,
    match: SongSearchMatch?,
    textColor: Color,
) {
    val cellText = when (colId) {
        SongColumnId.NUMBER     -> song.number
        SongColumnId.TITLE      -> song.title
        SongColumnId.SONGBOOK   -> song.songbook
        SongColumnId.TUNE       -> song.tune
        SongColumnId.PLAY_COUNT -> {
            val count = playCountFor(song.songId) ?: 0
            if (count > 0) count.toString() else ""
        }
        SongColumnId.AUTHOR     -> song.author
        SongColumnId.COMPOSER   -> song.composer
        else         -> ""
    }
    TooltipArea(
        tooltip = {
            if (cellText.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        cellText,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomStart,
            offset = DpOffset(0.dp, 4.dp)
        )
    ) {
        Text(
            if (colId == SongColumnId.TITLE && match.isOwnTitle()) {
                highlightedText(cellText, searchQuery)
            } else {
                AnnotatedString(cellText)
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = if (colId == SongColumnId.PLAY_COUNT) TextAlign.End
                        else TextAlign.Start,
            modifier = Modifier
                .width(with(density) { colWidth(colId).toDp() })
                .initialPassCombinedClickable(
                    onClick = {
                        onSelectSong(index)
                        if (isPresenting && live.songId != null) {
                            onSelectSection(-1)
                        }
                        tabFocusRequester.requestFocus()
                    },
                    // The cell consumes on the Initial pass, so a click
                    // on the title never reaches the row behind it --
                    // without this, double-clicking a song's name would
                    // do nothing.
                    onDoubleClick = {
                        onSelectSong(index)
                        sendToPresenter(true)
                        onPresenting(Presenting.LYRICS)
                        tabFocusRequester.requestFocus()
                    },
                )
                .padding(horizontal = 8.dp),
            maxLines = if (colId == SongColumnId.NUMBER) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            color = textColor
        )
    }
    Box(modifier = Modifier.width(6.dp))
}

@Composable
private fun SongListScope.SongActionCell(colId: String, song: SongItem) {
    Box(modifier = Modifier.width(6.dp))
    when (colId) {
        SongColumnId.ADD_TO_SCHEDULE -> KeyIconButton(
            onClick = { onAddToSchedule?.invoke(
                song.number.toIntOrNull() ?: 0,
                song.title,
                song.songbook,
                song.songId,
            ) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_playlist_add),
                contentDescription = stringResource(Res.string.add_to_schedule),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        SongColumnId.FAVORITES -> {
            val isFav = song.songId in favorites
            KeyIconButton(
                onClick = {
                    onToggleFavorite(song.songId)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (isFav) Res.drawable.ic_star_filled else Res.drawable.ic_star
                    ),
                    contentDescription = if (isFav)
                        stringResource(Res.string.remove_from_favorites)
                    else
                        stringResource(Res.string.add_to_favorites),
                    modifier = Modifier.size(16.dp),
                    tint = if (isFav) MaterialTheme.semantic.favorite
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Add to Schedule, favourite, edit, delete and Go Live, from a right-click on the row. */
@Composable
private fun SongListScope.SongRowContextMenu(
    index: Int,
    song: SongItem,
    showContextMenuState: MutableState<Boolean>,
    contextMenuOffset: DpOffset,
) {
    var showContextMenu by showContextMenuState
    DropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { showContextMenu = false },
        offset = contextMenuOffset
    ) {
        if (onAddToSchedule != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.add_to_schedule)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_playlist_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                onClick = {
                    onAddToSchedule(song.number.toIntOrNull() ?: 0, song.title, song.songbook, song.songId)
                    showContextMenu = false
                }
            )
        }
        DropdownMenuItem(
            text = {
                val isFav = song.songId in favorites
                Text(stringResource(if (isFav) Res.string.remove_from_favorites else Res.string.add_to_favorites))
            },
            leadingIcon = {
                val isFav = song.songId in favorites
                Icon(
                    painter = painterResource(if (isFav) Res.drawable.ic_star_filled else Res.drawable.ic_star),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isFav) MaterialTheme.semantic.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = {
                onToggleFavorite(song.songId)
                showContextMenu = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.edit_song)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            onClick = {
                dialogs.edit(song)
                tabFocusRequester.requestFocus()
                showContextMenu = false
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.delete_saved_string), color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                dialogs.delete(song)
                showContextMenu = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.go_live)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            onClick = {
                onSelectSong(index)
                sendToPresenter(true)
                onPresenting(Presenting.LYRICS)
                tabFocusRequester.requestFocus()
                showContextMenu = false
            }
        )
    }
}

/** True when the search matched the song's own title, which the title cell then highlights. */
private fun SongSearchMatch?.isOwnTitle(): Boolean = this?.kind == SongMatchKind.TITLE && languageIndex == 0

/**
 * How far the title column starts from the row's left edge, so the match line lines up under it: each
 * data cell is its width plus the 6dp gap after it, each action cell a 6dp gap and its 24dp button.
 */
private fun titleOffset(visibleCols: List<String>, actionCols: Set<String>, widthOf: (String) -> Dp): Dp {
    var offset = 0.dp
    for (col in visibleCols) {
        if (col == SongColumnId.TITLE) break
        offset += if (col in actionCols) {
            SONG_LIST_CELL_GAP + SONG_LIST_ACTION_CELL
        } else {
            widthOf(col) + SONG_LIST_CELL_GAP
        }
    }
    return offset
}

/**
 * The line under a matching song: a chip saying where the search found it -- Title, the section's own
 * name, or Lyrics, with the language when it was a translation -- and the words around the match.
 */
@Composable
private fun SongMatchLine(match: SongSearchMatch, query: String, indent: Dp) {
    val (container, onContainer) = when (match.kind) {
        SongMatchKind.TITLE -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        SongMatchKind.VERSE -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        SongMatchKind.CHORUS -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        SongMatchKind.OTHER_SECTION, SongMatchKind.LYRICS -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    val name = when (match.kind) {
        SongMatchKind.TITLE -> stringResource(Res.string.title)
        else -> match.sectionName ?: stringResource(Res.string.song_search_match_lyrics)
    }
    val language = when {
        match.languageIndex == 0 -> null
        match.languageLabel.isNotBlank() -> match.languageLabel
        else -> stringResource(Res.string.song_search_match_translation, match.languageIndex + 1)
    }
    Row(
        modifier = Modifier.padding(start = indent + 8.dp, end = 8.dp, top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(AppShape(6.dp))
                .background(container)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, color = onContainer, maxLines = 1)
            if (language != null) {
                Text(
                    language,
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(AppShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = SONG_LIST_LANGUAGE_TAG_ALPHA))
                        .padding(horizontal = 4.dp),
                )
            }
        }
        if (match.snippet != null) {
            Text(
                highlightedText(match.snippet, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
