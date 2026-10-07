package org.churchpresenter.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.edit_song
import org.churchpresenter.strings.generated.resources.new_song
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.song_stats
import org.churchpresenter.strings.generated.resources.song_typical_live
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.stringResource

/** The Background button's slot: the app draws `:profiles`' `SongBackgroundButton` in it. */
typealias SongBackgroundButtonSlot = @Composable (SongBackgroundButtonState) -> Unit

@Composable
fun EditSongDialog(
    isVisible: Boolean,
    song: SongItem?,
    backgroundButton: SongBackgroundButtonSlot,
    songbooks: List<String> = emptyList(),
    existingSongs: List<SongItem> = emptyList(),
    isNewSong: Boolean = false,
    theme: ThemeMode,
    tuning: SongTuning = SongTuning(),
    showTuningFields: Boolean = false,
    chordsVisible: Boolean = true,
    /**
     * How long this song usually stays on screen here, in seconds, or null until it is known.
     *
     * Measured rather than typed -- see `LiveDurationLog`. Shown beside the section and word
     * counts because that is where somebody editing a song asks "how long does this one run".
     */
    typicalSeconds: Int? = null,
    onChordsVisibleChange: (Boolean) -> Unit = {},
    onApplyBackgroundToSongbook: ((songbook: String, background: SongBackground,
                                  lowerThirdBackground: SongBackground) -> Unit)? = null,
    /**
     * What the operator calls each of the song's languages, `0` being the primary -- blank where
     * unnamed. Install-wide rather than this song's: it is the name the profiles' song languages
     * and the output language switch show too.
     */
    languageNames: List<String> = emptyList(),
    /** Stores renamed [languageNames] on Save. Null leaves the names out of the editor. */
    onLanguageNamesChange: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (SongItem, SongTuning) -> Unit,
    /**
     * Wraps the window's content — the app passes its helper's spotlight, so a tour can ring the
     * editor's controls. Draws the content as it is by default.
     */
    windowContent: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
) {
    if (!isVisible || song == null) return

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 1120.dp, 760.dp),
            width = 1120.dp,
            height = 760.dp
        ),
        title = if (isNewSong) stringResource(Res.string.new_song) else stringResource(Res.string.edit_song),
        resizable = true
    ) {
        windowContent {
            EditSongContent(
                song = song,
                backgroundButton = backgroundButton,
                songbooks = songbooks,
                existingSongs = existingSongs,
                isNewSong = isNewSong,
                theme = theme,
                tuning = tuning,
                showTuningFields = showTuningFields,
                chordsVisible = chordsVisible,
                typicalSeconds = typicalSeconds,
                onChordsVisibleChange = onChordsVisibleChange,
                isVisible = isVisible,
                onApplyBackgroundToSongbook = onApplyBackgroundToSongbook,
                languageNames = languageNames,
                onLanguageNamesChange = onLanguageNamesChange,
                onDismiss = onDismiss,
                onSave = onSave
            )
        }
    }
}

/**
 * The song editor itself: the identifying fields, the lyric pane, the chord preview, and the Save
 * that rebuilds the song from them.
 *
 * Held apart from [EditSongDialog] because that function's only other statement is the
 * `DialogWindow` it opens, which cannot be composed on a headless machine. Keeping the window down
 * to that one call leaves the editing rules — the digits-only song number, the duplicate check, the
 * conditions under which Save is offered at all — reachable from a test.
 *
 * [isVisible] is taken only because the edit buffers are keyed on it, so that reopening the dialog
 * over the same song discards an abandoned edit rather than resuming it.
 *
 * [tuning] rides alongside rather than inside [SongItem] because tempo and capo are per-machine
 * settings (`AppSettings.songBpm`/`songCapo`, keyed by `songId`), not something written to the song
 * file. They are handed back through [onSave] so the caller commits everything in one place, and
 * only if the save took. [showTuningFields] hides them where they would mean nothing — there is no
 * stage monitor to read them.
 *
 * [chordsVisible] is the stored editor preference, reported back through [onChordsVisibleChange] so
 * the switch is remembered for the next song rather than reset with each one.
 */
@Composable
internal fun EditSongContent(
    song: SongItem,
    backgroundButton: SongBackgroundButtonSlot,
    songbooks: List<String>,
    existingSongs: List<SongItem>,
    isNewSong: Boolean,
    theme: ThemeMode,
    tuning: SongTuning = SongTuning(),
    showTuningFields: Boolean = false,
    chordsVisible: Boolean = true,
    /** How long this song usually runs here -- measured, see [EditSongDialog]. */
    typicalSeconds: Int? = null,
    onChordsVisibleChange: (Boolean) -> Unit = {},
    isVisible: Boolean = true,
    onApplyBackgroundToSongbook: ((songbook: String, background: SongBackground,
                                  lowerThirdBackground: SongBackground) -> Unit)? = null,
    /** What each language is called install-wide -- see [EditSongDialog]. */
    languageNames: List<String> = emptyList(),
    onLanguageNamesChange: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (SongItem, SongTuning) -> Unit
) {
    val state = remember(isVisible, song) { EditSongState(song) }
    state.isNewSong = isNewSong
    val tuningDraft = remember(isVisible, song, tuning) { EditSongTuning(tuning) }
    val names = remember(isVisible, song, languageNames) { SongLanguageNames(languageNames) }
    // Keyed on the setting rather than on the song: the switch is remembered across songs, so it
    // resyncs when the stored preference changes and survives opening the next song.
    var showChords by remember(chordsVisible) { mutableStateOf(chordsVisible) }
    val isDuplicate = remember(state.number, state.title, state.songbook) { state.isDuplicateIn(existingSongs) }

    AppWindowRoot(theme = theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                EditSongMetadata(
                    state = state,
                    names = names,
                    tuning = tuningDraft.takeIf { showTuningFields },
                    songbooks = songbooks,
                    isVisible = isVisible,
                    isDuplicate = isDuplicate,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Body: editor on the left, the song as the band reads it on the right ────
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    EditSongEditorColumn(
                        state = state,
                        names = names,
                        showLanguageNames = onLanguageNamesChange != null,
                        toolbar = {
                            EditSongToolbar(
                                state = state,
                                names = names,
                                backgroundButton = backgroundButton,
                                onApplyBackgroundToSongbook = onApplyBackgroundToSongbook,
                                showChords = showChords,
                                onToggleChords = {
                                    showChords = !showChords
                                    onChordsVisibleChange(showChords)
                                },
                            )
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EditSongChordPreview(state, showChords)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                EditSongFooter(
                    stats = songStatsOf(buildPreviewSections(state.paneValue.text, showChords = false)),
                    typicalSeconds = typicalSeconds,
                    saveEnabled = !isDuplicate &&
                        (!isNewSong || (state.songbook.isNotBlank() && state.title.isNotBlank())),
                    onDismiss = onDismiss,
                    onSave = {
                        val updatedSong = state.toSongItem()
                        names.changed()?.let { changed -> onLanguageNamesChange?.invoke(changed) }
                        onSave(updatedSong, tuningDraft.toSongTuning())
                    },
                )
            }
        }
    }
}

/** The open pane as the band reads it, with the key, transposition and chord palette. */
@Composable
private fun EditSongChordPreview(state: EditSongState, showChords: Boolean) {
    SongChordPreview(
        text = state.paneValue.text,
        showChords = showChords,
        songKey = state.songKey,
        transposed = state.transposed,
        onKeyUp = { state.stepKey(1) },
        onKeyDown = { state.stepKey(-1) },
        onTransposeUp = { state.transposeBy(1) },
        onTransposeDown = { state.transposeBy(-1) },
        onTransposeReset = { state.transposeBy(-state.transposed) },
        onInsertChord = { chord ->
            state.setPaneValue(insertSnippet(state.paneValue, "[$chord]", ownLine = false))
        },
        modifier = Modifier.width(452.dp).fillMaxHeight(),
    )
}

/**
 * The dialog's footer: what the song adds up to, and the two buttons.
 *
 * [typicalSeconds] is how long this song usually stays on screen, measured from the times it has
 * been presented — absent until there are enough readings to mean anything, and then shown beside
 * the counts so a planner can time a service by what actually happens rather than by a guess.
 */
@Composable
private fun EditSongFooter(
    stats: SongStats,
    typicalSeconds: Int?,
    saveEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .height(58.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val counts = stringResource(Res.string.song_stats, stats.sections, stats.lines, stats.words)
        val typical = typicalSeconds?.let { stringResource(Res.string.song_typical_live, formatDuration(it)) }
        Text(
            text = listOfNotNull(counts, typical).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        GhostButton(shape = AppShape(9.dp), onClick = onDismiss) {
            Text(stringResource(Res.string.cancel))
        }
        RaisedButton(
            shape = AppShape(9.dp),
            enabled = saveEnabled,
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(Res.string.save))
        }
    }
}
