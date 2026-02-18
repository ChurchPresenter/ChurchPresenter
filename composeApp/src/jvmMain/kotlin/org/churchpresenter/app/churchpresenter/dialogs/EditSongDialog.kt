package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.author
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.composer
import churchpresenter.composeapp.generated.resources.edit_song
import churchpresenter.composeapp.generated.resources.enter_lyrics_here
import churchpresenter.composeapp.generated.resources.lyrics
import churchpresenter.composeapp.generated.resources.lyrics_format_help
import churchpresenter.composeapp.generated.resources.save
import churchpresenter.composeapp.generated.resources.song_book
import churchpresenter.composeapp.generated.resources.song_number
import churchpresenter.composeapp.generated.resources.song_title
import churchpresenter.composeapp.generated.resources.tune
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditSongDialog(
    isVisible: Boolean,
    song: SongItem?,
    theme: ThemeMode,
    onDismiss: () -> Unit,
    onSave: (SongItem) -> Unit
) {
    if (!isVisible || song == null) return

    // Filter out non-digits from song number (handles cases like "3.1" -> "3" or "31")
    var editedNumber by remember(song) { mutableStateOf(song.number.filter { it.isDigit() }) }
    var editedTitle by remember(song) { mutableStateOf(song.title) }
    var editedSongbook by remember(song) { mutableStateOf(song.songbook) }
    var editedTune by remember(song) { mutableStateOf(song.tune) }
    var editedAuthor by remember(song) { mutableStateOf(song.author) }
    var editedComposer by remember(song) { mutableStateOf(song.composer) }
    var editedLyrics by remember(song) { mutableStateOf(song.lyrics.joinToString("\n")) }

    Dialog(
        onCloseRequest = onDismiss,
        state = rememberDialogState(width = 800.dp, height = 700.dp),
        title = stringResource(Res.string.edit_song),
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
                    // Scrollable content area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp)
                    ) {
                        // First row: Song Number and Title
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editedNumber,
                                onValueChange = { newValue ->
                                    // Only allow digits
                                    if (newValue.all { it.isDigit() }) {
                                        editedNumber = newValue
                                    }
                                },
                                label = { Text(stringResource(Res.string.song_number), style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(0.3f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = editedTitle,
                                onValueChange = { editedTitle = it },
                                label = { Text(stringResource(Res.string.song_title), style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(0.7f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Second row: Songbook and Tune
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editedSongbook,
                                onValueChange = { editedSongbook = it },
                                label = { Text(stringResource(Res.string.song_book), style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(0.7f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = editedTune,
                                onValueChange = { editedTune = it },
                                label = { Text(stringResource(Res.string.tune), style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(0.3f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Third row: Author and Composer
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editedAuthor,
                                onValueChange = { editedAuthor = it },
                                label = { Text(stringResource(Res.string.author), style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(0.5f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = editedComposer,
                                onValueChange = { editedComposer = it },
                                label = { Text(stringResource(Res.string.composer), style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.weight(0.5f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Lyrics section - takes remaining space
                        Text(
                            text = stringResource(Res.string.lyrics),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = editedLyrics,
                            onValueChange = { editedLyrics = it },
                            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 400.dp),
                            placeholder = { Text(stringResource(Res.string.enter_lyrics_here)) },
                            maxLines = Int.MAX_VALUE
                        )

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
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(Res.string.cancel))
                        }

                        Button(
                            onClick = {
                                val updatedSong = SongItem(
                                    number = editedNumber,
                                    title = editedTitle,
                                    songbook = editedSongbook,
                                    tune = editedTune,
                                    author = editedAuthor,
                                    composer = editedComposer,
                                    lyrics = editedLyrics.split("\n")
                                )
                                onSave(updatedSong)
                                onDismiss()
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

