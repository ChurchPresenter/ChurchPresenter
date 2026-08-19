package songlibrary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import songlibrary.generated.resources.Res
import songlibrary.generated.resources.cancel
import songlibrary.generated.resources.editor_author
import songlibrary.generated.resources.editor_ccli
import songlibrary.generated.resources.editor_composer
import songlibrary.generated.resources.editor_lyrics
import songlibrary.generated.resources.editor_lyrics_hint
import songlibrary.generated.resources.editor_number
import songlibrary.generated.resources.editor_save
import songlibrary.generated.resources.editor_secondary_lyrics
import songlibrary.generated.resources.editor_secondary_title
import songlibrary.generated.resources.editor_song_book
import songlibrary.generated.resources.editor_song_title
import songlibrary.generated.resources.editor_title
import songlibrary.generated.resources.editor_tune
import songlibrary.generated.resources.no_song_book

/**
 * One song opened in full: its fields, its lyrics, and the second language beside them.
 *
 * The grid edits a field at a time, which is what it is for; this is for the song itself, where the
 * lyrics are — and it edits into the same pending changes as the grid, so Save still means one save
 * and Revert still puts everything back.
 */
@Composable
fun SongEditorDialog(request: SongEditorRequest) {
    val song = request.song
    val songbooks = request.songbooks
    val onDismiss = request.onDismiss
    val onSave = request.onSave
    var draft by remember(song.sourceFile) { mutableStateOf(song) }
    var lyrics by remember(song.sourceFile) { mutableStateOf(song.lyrics.joinToString("\n")) }
    var secondary by remember(song.sourceFile) { mutableStateOf(song.secondaryLyrics.joinToString("\n")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(920.dp),
        title = { Text(stringResource(Res.string.editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(stringResource(Res.string.editor_song_title), draft.title, Modifier.weight(2f)) {
                        draft = draft.copy(title = it)
                    }
                    Field(stringResource(Res.string.editor_secondary_title), draft.secondaryTitle, Modifier.weight(2f)) {
                        draft = draft.copy(secondaryTitle = it)
                    }
                    Field(stringResource(Res.string.editor_number), draft.number, Modifier.weight(1f)) {
                        draft = draft.copy(number = it)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SongbookField(
                        value = draft.songbook,
                        songbooks = songbooks,
                        modifier = Modifier.weight(1f),
                        onPick = { draft = draft.copy(songbook = it) },
                    )
                    Field(stringResource(Res.string.editor_author), draft.author, Modifier.weight(1f)) {
                        draft = draft.copy(author = it)
                    }
                    Field(stringResource(Res.string.editor_composer), draft.composer, Modifier.weight(1f)) {
                        draft = draft.copy(composer = it)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(stringResource(Res.string.editor_tune), draft.tune, Modifier.weight(1f)) {
                        draft = draft.copy(tune = it)
                    }
                    Field(stringResource(Res.string.editor_ccli), draft.ccliNumber, Modifier.weight(1f)) {
                        draft = draft.copy(ccliNumber = it)
                    }
                }
                Text(stringResource(Res.string.editor_lyrics_hint), style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lyrics,
                        onValueChange = { lyrics = it },
                        label = { Text(stringResource(Res.string.editor_lyrics)) },
                        modifier = Modifier.weight(1f).height(280.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = secondary,
                        onValueChange = { secondary = it },
                        label = { Text(stringResource(Res.string.editor_secondary_lyrics)) },
                        modifier = Modifier.weight(1f).height(280.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        draft.copy(
                            lyrics = lyrics.lines().dropLastWhile { it.isBlank() },
                            secondaryLyrics = secondary.lines().dropLastWhile { it.isBlank() },
                        )
                    )
                }
            ) { Text(stringResource(Res.string.editor_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SongbookField(
    value: String,
    songbooks: List<String>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.editor_song_book), style = MaterialTheme.typography.labelSmall)
                Text(
                    value.ifBlank { stringResource(Res.string.no_song_book) },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.no_song_book)) }, onClick = { onPick(""); open = false })
            songbooks.forEach { book ->
                DropdownMenuItem(
                    text = { Text(book, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onPick(book); open = false },
                )
            }
        }
    }
}
