package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import org.churchpresenter.app.churchpresenter.composables.SettingsTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.shape.RoundedCornerShape
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.author
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.composer
import churchpresenter.composeapp.generated.resources.add_new
import churchpresenter.composeapp.generated.resources.duplicate_song_error
import churchpresenter.composeapp.generated.resources.edit_song
import churchpresenter.composeapp.generated.resources.enter_lyrics_here
import churchpresenter.composeapp.generated.resources.enter_secondary_lyrics_here
import churchpresenter.composeapp.generated.resources.lyrics
import churchpresenter.composeapp.generated.resources.lyrics_format_help
import churchpresenter.composeapp.generated.resources.new_song
import churchpresenter.composeapp.generated.resources.save
import churchpresenter.composeapp.generated.resources.secondary_lyrics
import churchpresenter.composeapp.generated.resources.secondary_title
import churchpresenter.composeapp.generated.resources.song_book
import churchpresenter.composeapp.generated.resources.song_number
import churchpresenter.composeapp.generated.resources.song_title
import churchpresenter.composeapp.generated.resources.tune
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSongDialog(
    isVisible: Boolean,
    song: SongItem?,
    songbooks: List<String> = emptyList(),
    existingSongs: List<SongItem> = emptyList(),
    isNewSong: Boolean = false,
    theme: ThemeMode,
    onDismiss: () -> Unit,
    onSave: (SongItem) -> Unit
) {
    if (!isVisible || song == null) return

    // Filter out non-digits from song number (handles cases like "3.1" -> "3" or "31")
    var editedNumber by remember(isVisible, song) { mutableStateOf(song.number.filter { it.isDigit() }) }
    var editedTitle by remember(isVisible, song) { mutableStateOf(song.title) }
    var titleManuallyEdited by remember(isVisible, song) { mutableStateOf(song.title.isNotBlank()) }
    var editedSongbook by remember(isVisible, song) { mutableStateOf(song.songbook) }
    var editedTune by remember(isVisible, song) { mutableStateOf(song.tune) }
    var editedAuthor by remember(isVisible, song) { mutableStateOf(song.author) }
    var editedComposer by remember(isVisible, song) { mutableStateOf(song.composer) }
    var editedLyrics by remember(isVisible, song) { mutableStateOf(song.lyrics.joinToString("\n")) }
    var editedSecondaryTitle by remember(isVisible, song) { mutableStateOf(song.secondaryTitle) }
    var editedSecondaryLyrics by remember(isVisible, song) { mutableStateOf(song.secondaryLyrics.joinToString("\n")) }

    // Duplicate song check
    val isDuplicate = remember(editedNumber, editedTitle, editedSongbook) {
        if (editedTitle.isBlank() || editedSongbook.isBlank()) false
        else existingSongs.any {
            it.number == editedNumber &&
            it.title.equals(editedTitle, ignoreCase = true) &&
            it.songbook.equals(editedSongbook, ignoreCase = true) &&
            it.sourceFile != song.sourceFile
        }
    }

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 800.dp, 700.dp),
            width = 800.dp,
            height = 700.dp
        ),
        title = if (isNewSong) stringResource(Res.string.new_song) else stringResource(Res.string.edit_song),
        resizable = true
    ) {
        AppThemeWrapper(theme = theme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    // Content area: fixed-height fields scroll if needed, lyrics fill the rest
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 16.dp)
                    ) {
                    val separatorColor = MaterialTheme.colorScheme.primary
                    val separatorBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    val lyricsHighlightTransformation = remember(separatorColor, separatorBg) {
                        VisualTransformation { text ->
                            val annotated = buildAnnotatedString {
                                val lines = text.text.split("\n")
                                lines.forEachIndexed { i, line ->
                                    val trimmed = line.trim()
                                    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                                        pushStyle(
                                            SpanStyle(
                                                color = separatorColor,
                                                background = separatorBg,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        append(line)
                                        pop()
                                    } else {
                                        append(line)
                                    }
                                    if (i < lines.size - 1) append("\n")
                                }
                            }
                            TransformedText(annotated, OffsetMapping.Identity)
                        }
                    }
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        // First row: Song Number, Songbook, Tune
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingsTextField(
                                value = editedNumber,
                                onValueChange = { newValue ->
                                    // Only allow digits
                                    if (newValue.all { it.isDigit() }) {
                                        editedNumber = newValue
                                    }
                                },
                                modifier = Modifier.weight(0.2f),
                                label = stringResource(Res.string.song_number),
                                fillWidth = true,
                                singleLine = true,
                            )

                            var songbookExpanded by remember { mutableStateOf(false) }
                            var isAddingNew by remember(isVisible) { mutableStateOf(false) }

                            if (isAddingNew) {
                                SettingsTextField(
                                    value = editedSongbook,
                                    onValueChange = { editedSongbook = it },
                                    modifier = Modifier.weight(0.5f),
                                    label = stringResource(Res.string.song_book),
                                    fillWidth = true,
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                editedSongbook = song.songbook
                                                isAddingNew = false
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                )
                            } else {
                                ExposedDropdownMenuBox(
                                    expanded = songbookExpanded,
                                    onExpandedChange = { songbookExpanded = it },
                                    modifier = Modifier.weight(0.5f)
                                ) {
                                    SettingsTextField(
                                        value = editedSongbook,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = songbookExpanded) },
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                        label = stringResource(Res.string.song_book),
                                        fillWidth = true,
                                        singleLine = true,
                                    )
                                    ExposedDropdownMenu(
                                        expanded = songbookExpanded,
                                        onDismissRequest = { songbookExpanded = false }
                                    ) {
                                        songbooks.forEach { songbook ->
                                            DropdownMenuItem(
                                                text = { Text(songbook) },
                                                onClick = {
                                                    editedSongbook = songbook
                                                    songbookExpanded = false
                                                }
                                            )
                                        }
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.add_new)) },
                                            onClick = {
                                                songbookExpanded = false
                                                editedSongbook = ""
                                                isAddingNew = true
                                            }
                                        )
                                    }
                                }
                            }

                            SettingsTextField(
                                value = editedTune,
                                onValueChange = { editedTune = it },
                                modifier = Modifier.weight(0.3f),
                                label = stringResource(Res.string.tune),
                                fillWidth = true,
                                singleLine = true,
                            )
                        }

                        // Second row: Primary Title and Secondary Title
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingsTextField(
                                value = editedTitle,
                                onValueChange = {
                                    editedTitle = it
                                    titleManuallyEdited = it.isNotBlank()
                                },
                                modifier = Modifier.weight(0.5f),
                                label = stringResource(Res.string.song_title),
                                fillWidth = true,
                                singleLine = true,
                            )

                            SettingsTextField(
                                value = editedSecondaryTitle,
                                onValueChange = { editedSecondaryTitle = it },
                                modifier = Modifier.weight(0.5f),
                                label = stringResource(Res.string.secondary_title),
                                fillWidth = true,
                                singleLine = true,
                            )
                        }

                        if (isDuplicate) {
                            Text(
                                text = stringResource(Res.string.duplicate_song_error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // Third row: Author and Composer
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingsTextField(
                                value = editedAuthor,
                                onValueChange = { editedAuthor = it },
                                modifier = Modifier.weight(0.5f),
                                label = stringResource(Res.string.author),
                                fillWidth = true,
                                singleLine = true,
                            )

                            SettingsTextField(
                                value = editedComposer,
                                onValueChange = { editedComposer = it },
                                modifier = Modifier.weight(0.5f),
                                label = stringResource(Res.string.composer),
                                fillWidth = true,
                                singleLine = true,
                            )
                        }

                        // Lyrics section - side by side
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.lyrics),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(bottom = 4.dp)
                            )
                            Text(
                                text = stringResource(Res.string.secondary_lyrics),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(bottom = 4.dp)
                            )
                        }
                    } // end top fields (scrollable if window is shrunk)

                    // Lyrics fields fill the rest of the available height
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsTextField(
                            value = editedLyrics,
                            onValueChange = { newLyrics ->
                                editedLyrics = newLyrics
                                // Auto-fill title from first non-header, non-blank lyric line
                                if (isNewSong && !titleManuallyEdited) {
                                    val firstContentLine = newLyrics.lines().firstOrNull { line ->
                                        val trimmed = line.trim()
                                        trimmed.isNotBlank() && !trimmed.startsWith("[")
                                    }?.trim() ?: ""
                                    editedTitle = firstContentLine
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            placeholder = { Text(stringResource(Res.string.enter_lyrics_here)) },
                            visualTransformation = lyricsHighlightTransformation
                        )
                        LyricsTextField(
                            value = editedSecondaryLyrics,
                            onValueChange = { editedSecondaryLyrics = it },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            placeholder = { Text(stringResource(Res.string.enter_secondary_lyrics_here)) },
                            visualTransformation = lyricsHighlightTransformation
                        )
                    }

                    // Help text
                    Text(
                        text = stringResource(Res.string.lyrics_format_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            shape = RoundedCornerShape(6.dp),
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(Res.string.cancel))
                        }

                        Button(
                            shape = RoundedCornerShape(6.dp),
                            enabled = !isDuplicate && (!isNewSong || (editedSongbook.isNotBlank() && editedTitle.isNotBlank())),
                            onClick = {
                                val updatedSong = SongItem(
                                    number = editedNumber,
                                    title = editedTitle,
                                    songbook = editedSongbook,
                                    tune = editedTune,
                                    author = editedAuthor,
                                    composer = editedComposer,
                                    lyrics = editedLyrics.split("\n"),
                                    secondaryTitle = editedSecondaryTitle,
                                    secondaryLyrics = editedSecondaryLyrics.split("\n").let {
                                        if (it.all { line -> line.isBlank() || line.trim().startsWith("[") }) emptyList() else it
                                    },
                                    sourceFile = song.sourceFile
                                )
                                onSave(updatedSong)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(stringResource(Res.string.save))
                        }
                    }
                }
            }
        }
    }
}

private val LyricsFieldShape = RoundedCornerShape(6.dp)

/**
 * Dedicated multi-line, scrollable lyrics editor — kept separate from SettingsTextField
 * (which is tuned for compact single-line settings rows) so it can grow to fill the
 * available height with its own scrollbar and a larger, more readable font.
 */
@Composable
private fun LyricsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val scrollState = rememberScrollState()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, LyricsFieldShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LyricsFieldShape)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 9.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box {
                    if (placeholder != null && value.isEmpty()) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ) {
                            placeholder()
                        }
                    }
                    innerTextField()
                }
            }
        )
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

