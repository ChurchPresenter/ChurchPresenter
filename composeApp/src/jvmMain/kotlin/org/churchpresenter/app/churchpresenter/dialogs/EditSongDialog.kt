package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_new
import churchpresenter.composeapp.generated.resources.author
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.ccli_number
import churchpresenter.composeapp.generated.resources.composer
import churchpresenter.composeapp.generated.resources.duplicate_song_error
import churchpresenter.composeapp.generated.resources.edit_song
import churchpresenter.composeapp.generated.resources.enter_lyrics_here
import churchpresenter.composeapp.generated.resources.enter_secondary_lyrics_here
import churchpresenter.composeapp.generated.resources.new_song
import churchpresenter.composeapp.generated.resources.save
import churchpresenter.composeapp.generated.resources.secondary_title
import churchpresenter.composeapp.generated.resources.song_book
import churchpresenter.composeapp.generated.resources.song_capo
import churchpresenter.composeapp.generated.resources.song_chords
import churchpresenter.composeapp.generated.resources.song_chords_toggle
import churchpresenter.composeapp.generated.resources.song_insert_section
import churchpresenter.composeapp.generated.resources.song_number
import churchpresenter.composeapp.generated.resources.song_pane_lyrics
import churchpresenter.composeapp.generated.resources.song_pane_secondary
import churchpresenter.composeapp.generated.resources.song_stats
import churchpresenter.composeapp.generated.resources.song_syntax
import churchpresenter.composeapp.generated.resources.song_syntax_chord_hint
import churchpresenter.composeapp.generated.resources.song_tempo
import churchpresenter.composeapp.generated.resources.song_title
import churchpresenter.composeapp.generated.resources.tune
import churchpresenter.composeapp.generated.resources.unit_bpm
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.composables.ConditionalTooltipArea
import org.churchpresenter.app.churchpresenter.composables.PaneTab
import org.churchpresenter.app.churchpresenter.composables.PaneTabRow
import org.churchpresenter.app.churchpresenter.composables.SectionInk
import org.churchpresenter.app.churchpresenter.composables.SongChordPreview
import org.churchpresenter.app.churchpresenter.composables.SongSectionKind
import org.churchpresenter.app.churchpresenter.composables.buildPreviewSections
import org.churchpresenter.app.churchpresenter.composables.sectionKindOf
import org.churchpresenter.app.churchpresenter.composables.songStatsOf
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.songchords.ChordSheetImporter
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.utils.SystemClipboard

private const val BPM_MAX_DIGITS = 3
private const val MAX_BPM = 300
private const val MAX_CAPO = 12
private const val CHORD_PREVIEW_SCALE = 0.75f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSongDialog(
    isVisible: Boolean,
    song: SongItem?,
    songbooks: List<String> = emptyList(),
    existingSongs: List<SongItem> = emptyList(),
    isNewSong: Boolean = false,
    theme: ThemeMode,
    tuning: SongTuning = SongTuning(),
    showTuningFields: Boolean = false,
    chordsVisible: Boolean = true,
    onChordsVisibleChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (SongItem, SongTuning) -> Unit
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
        EditSongContent(
            song = song,
            songbooks = songbooks,
            existingSongs = existingSongs,
            isNewSong = isNewSong,
            theme = theme,
            tuning = tuning,
            showTuningFields = showTuningFields,
            chordsVisible = chordsVisible,
            onChordsVisibleChange = onChordsVisibleChange,
            isVisible = isVisible,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

/** Which set of lyrics the single editor pane is currently showing. */
internal enum class LyricPane { PRIMARY, SECONDARY }

/**
 * Puts [snippet] in at the caret, replacing whatever is selected, and leaves the caret after it.
 *
 * With [ownLine] the snippet is given a blank line above and a line below unless it already has
 * them, which is what a section marker needs: markers are only read as headers when they stand
 * alone on their line.
 */
internal fun insertSnippet(value: TextFieldValue, snippet: String, ownLine: Boolean): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val before = value.text.take(start)
    val after = value.text.drop(end)
    val piece = if (!ownLine) snippet else buildString {
        if (before.isNotEmpty() && !before.endsWith("\n\n")) {
            append(if (before.endsWith("\n")) "\n" else "\n\n")
        }
        append(snippet)
        if (!after.startsWith("\n")) append("\n")
    }
    return TextFieldValue(before + piece + after, TextRange(start + piece.length))
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditSongContent(
    song: SongItem,
    songbooks: List<String>,
    existingSongs: List<SongItem>,
    isNewSong: Boolean,
    theme: ThemeMode,
    tuning: SongTuning = SongTuning(),
    showTuningFields: Boolean = false,
    chordsVisible: Boolean = true,
    onChordsVisibleChange: (Boolean) -> Unit = {},
    isVisible: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (SongItem, SongTuning) -> Unit
) {
    // Filter out non-digits from song number (handles cases like "3.1" -> "3" or "31")
    var editedNumber by remember(isVisible, song) { mutableStateOf(song.number.filter { it.isDigit() }) }
    var editedTitle by remember(isVisible, song) { mutableStateOf(song.title) }
    var titleManuallyEdited by remember(isVisible, song) { mutableStateOf(song.title.isNotBlank()) }
    var editedSongbook by remember(isVisible, song) { mutableStateOf(song.songbook) }
    var editedTune by remember(isVisible, song) { mutableStateOf(song.tune) }
    var editedAuthor by remember(isVisible, song) { mutableStateOf(song.author) }
    var editedComposer by remember(isVisible, song) { mutableStateOf(song.composer) }
    var editedCcli by remember(isVisible, song) { mutableStateOf(song.ccliNumber) }
    // Blank rather than "0" when unset: 0 is the off value, and an empty box reads as "not set".
    var editedBpm by remember(isVisible, song, tuning) {
        mutableStateOf(if (tuning.bpm > 0) tuning.bpm.toString() else "")
    }
    var editedCapo by remember(isVisible, song, tuning) {
        mutableStateOf(if (tuning.capo > 0) tuning.capo.toString() else "")
    }
    var editedLyrics by remember(isVisible, song) {
        mutableStateOf(TextFieldValue(song.lyrics.joinToString("\n")))
    }
    var editedSecondaryTitle by remember(isVisible, song) { mutableStateOf(song.secondaryTitle) }
    var editedSecondaryLyrics by remember(isVisible, song) {
        mutableStateOf(TextFieldValue(song.secondaryLyrics.joinToString("\n")))
    }

    var pane by remember(isVisible, song) { mutableStateOf(LyricPane.PRIMARY) }
    // Keyed on the setting rather than on the song: the switch is remembered across songs, so it
    // resyncs when the stored preference changes and survives opening the next song.
    var showChords by remember(chordsVisible) { mutableStateOf(chordsVisible) }
    var steps by remember(isVisible, song) { mutableStateOf(0) }

    val paneValue = if (pane == LyricPane.PRIMARY) editedLyrics else editedSecondaryLyrics
    fun setPaneValue(v: TextFieldValue) {
        if (pane == LyricPane.SECONDARY) {
            editedSecondaryLyrics = v
            return
        }
        editedLyrics = v
        // Auto-fill title from first non-header, non-blank lyric line
        if (isNewSong && !titleManuallyEdited) {
            editedTitle = v.text.lines()
                .firstOrNull { it.isNotBlank() && !it.trim().startsWith("[") }
                ?.trim()
                ?: ""
        }
    }

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

    AppThemeWrapper(theme = theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Metadata: two dense rows of labelled cards ──────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldCard(
                            label = stringResource(Res.string.song_title),
                            value = editedTitle,
                            onValueChange = {
                                editedTitle = it
                                titleManuallyEdited = it.isNotBlank()
                            },
                            weight = 2.4f,
                            emphasis = true,
                        )
                        FieldCard(
                            label = stringResource(Res.string.secondary_title),
                            value = editedSecondaryTitle,
                            onValueChange = { editedSecondaryTitle = it },
                            weight = 1f,
                        )
                        SongbookCard(
                            songbook = editedSongbook,
                            songbooks = songbooks,
                            isVisible = isVisible,
                            originalSongbook = song.songbook,
                            onSongbookChange = { editedSongbook = it },
                            weight = 1f,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldCard(
                            label = stringResource(Res.string.song_number),
                            value = editedNumber,
                            onValueChange = { v -> if (v.all { it.isDigit() }) editedNumber = v },
                            weight = 0.8f,
                        )
                        FieldCard(
                            label = stringResource(Res.string.author),
                            value = editedAuthor,
                            onValueChange = { editedAuthor = it },
                            weight = 1.6f,
                        )
                        FieldCard(
                            label = stringResource(Res.string.composer),
                            value = editedComposer,
                            onValueChange = { editedComposer = it },
                            weight = 1.6f,
                        )
                        FieldCard(
                            label = stringResource(Res.string.ccli_number),
                            value = editedCcli,
                            onValueChange = { editedCcli = it },
                            weight = 1f,
                        )
                        FieldCard(
                            label = stringResource(Res.string.tune),
                            value = editedTune,
                            onValueChange = { editedTune = it },
                            weight = 0.8f,
                        )
                        if (showTuningFields) {
                            FieldCard(
                                label = stringResource(Res.string.song_capo),
                                value = editedCapo,
                                onValueChange = { v -> editedCapo = v.filter { it.isDigit() }.take(2) },
                                weight = 0.55f,
                            )
                            TempoCard(
                                bpm = editedBpm,
                                onBpmChange = { editedBpm = it.filter { c -> c.isDigit() }.take(BPM_MAX_DIGITS) },
                                weight = 0.9f,
                            )
                        }
                    }
                    if (isDuplicate) {
                        Text(
                            text = stringResource(Res.string.duplicate_song_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Body: editor on the left, the song as the band reads it on the right ────
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                        // Pane tabs and the chord switch.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PaneTabRow {
                                PaneTab(stringResource(Res.string.song_pane_lyrics), pane == LyricPane.PRIMARY) {
                                    pane = LyricPane.PRIMARY
                                }
                                PaneTab(stringResource(Res.string.song_pane_secondary), pane == LyricPane.SECONDARY) {
                                    pane = LyricPane.SECONDARY
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            ChordsToggle(on = showChords) {
                                showChords = !showChords
                                onChordsVisibleChange(showChords)
                            }
                        }

                        // Section markers, inserted at the caret.
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.song_insert_section).uppercase(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.0.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp),
                            )
                            Constants.SONG_SECTION_MARKERS.forEach { marker ->
                                InsertChip(marker.trim('[', ']', '{', '}')) {
                                    setPaneValue(insertSnippet(paneValue, marker, ownLine = true))
                                }
                            }
                        }

                        LyricsTextField(
                            value = paneValue,
                            onValueChange = { setPaneValue(it) },
                            onPasteChordSheet = { sheet ->
                                setPaneValue(insertSnippet(paneValue, ChordSheetImporter.convert(sheet), ownLine = false))
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                            placeholder = {
                                Text(
                                    stringResource(
                                        if (pane == LyricPane.PRIMARY) Res.string.enter_lyrics_here
                                        else Res.string.enter_secondary_lyrics_here
                                    )
                                )
                            },
                            visualTransformation = rememberLyricsHighlight(),
                        )

                        SyntaxLegend(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp))
                    }

                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SongChordPreview(
                        text = paneValue.text,
                        showChords = showChords,
                        steps = steps,
                        onTransposeUp = { steps += 1 },
                        onTransposeDown = { steps -= 1 },
                        onTransposeReset = { steps = 0 },
                        onInsertChord = { chord ->
                            setPaneValue(insertSnippet(paneValue, "[$chord]", ownLine = false))
                        },
                        modifier = Modifier.width(452.dp).fillMaxHeight(),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Footer ─────────────────────────────────────────────────────────────────
                val stats = remember(paneValue.text) {
                    songStatsOf(buildPreviewSections(paneValue.text, showChords = false))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .height(58.dp)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.song_stats, stats.sections, stats.lines, stats.words),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(shape = RoundedCornerShape(9.dp), onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Button(
                        shape = RoundedCornerShape(9.dp),
                        enabled = !isDuplicate && (!isNewSong || (editedSongbook.isNotBlank() && editedTitle.isNotBlank())),
                        onClick = {
                            val updatedSong = SongItem(
                                number = editedNumber,
                                title = editedTitle,
                                songbook = editedSongbook,
                                tune = editedTune,
                                author = editedAuthor,
                                composer = editedComposer,
                                lyrics = editedLyrics.text.split("\n"),
                                secondaryTitle = editedSecondaryTitle,
                                secondaryLyrics = editedSecondaryLyrics.text.split("\n").let {
                                    if (it.all { line -> line.isBlank() || line.trim().startsWith("[") }) emptyList() else it
                                },
                                sourceFile = song.sourceFile,
                                ccliNumber = editedCcli
                            )
                            onSave(
                                updatedSong,
                                SongTuning(
                                    bpm = editedBpm.toIntOrNull()?.coerceIn(0, MAX_BPM) ?: 0,
                                    capo = editedCapo.toIntOrNull()?.coerceIn(0, MAX_CAPO) ?: 0,
                                ),
                            )
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

private val CardShape = RoundedCornerShape(9.dp)

/**
 * The one type style every metadata card's value is set in.
 *
 * The line height is stated rather than inherited because the cards sit in a single row and are
 * read across: a value inheriting a taller line box than its neighbours — which is what happens
 * when a card puts its value next to an icon or a unit — sits visibly off the line they share.
 */
@Composable
private fun FieldValueStyle(emphasis: Boolean = false) = MaterialTheme.typography.bodyMedium.copy(
    color = MaterialTheme.colorScheme.onSurface,
    fontSize = if (emphasis) 15.sp else 13.5.sp,
    fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
    lineHeight = 19.sp,
)

/** The tiny uppercase caption that names a metadata card. */
@Composable
private fun CardLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** A metadata field: its caption above, its value typed straight into the card. */
@Composable
private fun RowScope.FieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    weight: Float,
    emphasis: Boolean = false,
) {
    Column(
        modifier = Modifier
            .weight(weight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        CardLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = FieldValueStyle(emphasis),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The song book card: the one metadata field that picks from what already exists rather than
 * accepting free text, with an escape hatch for naming a new book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.SongbookCard(
    songbook: String,
    songbooks: List<String>,
    isVisible: Boolean,
    originalSongbook: String,
    onSongbookChange: (String) -> Unit,
    weight: Float,
) {
    var expanded by remember { mutableStateOf(false) }
    var isAddingNew by remember(isVisible) { mutableStateOf(false) }

    val cardModifier = Modifier
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CardShape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
        .padding(horizontal = 11.dp, vertical = 6.dp)

    if (isAddingNew) {
        Column(
            modifier = Modifier.weight(weight).then(cardModifier),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            CardLabel(stringResource(Res.string.song_book))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = songbook,
                    onValueChange = onSongbookChange,
                    singleLine = true,
                    textStyle = FieldValueStyle(),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onSongbookChange(originalSongbook); isAddingNew = false },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel), modifier = Modifier.size(14.dp))
                }
            }
        }
    } else {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(weight),
        ) {
            // The anchor is the whole card, not the line of text inside it: a card that looks like
            // one control has to answer a click anywhere on it, including its label and its margins.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .then(cardModifier),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                CardLabel(stringResource(Res.string.song_book))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = songbook,
                        style = FieldValueStyle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { editableText = AnnotatedString(songbook) },
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                songbooks.forEach { book ->
                    DropdownMenuItem(
                        text = { Text(book) },
                        onClick = { onSongbookChange(book); expanded = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.add_new)) },
                    onClick = { expanded = false; onSongbookChange(""); isAddingNew = true },
                )
            }
        }
    }
}

/** Tempo: the number the stage monitor's metronome flashes at. */
@Composable
private fun RowScope.TempoCard(bpm: String, onBpmChange: (String) -> Unit, weight: Float) {
    Column(
        modifier = Modifier
            .weight(weight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        CardLabel(stringResource(Res.string.song_tempo))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicTextField(
                value = bpm,
                onValueChange = onBpmChange,
                singleLine = true,
                textStyle = FieldValueStyle(),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(30.dp),
            )
            // Same line box as the value beside it, so the two sit on one line rather than the
            // taller default pushing the number off the row's baseline.
            Text(
                text = stringResource(Res.string.unit_bpm),
                style = FieldValueStyle().copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** The switch that decides whether the preview shows chords at all. */
@Composable
private fun ChordsToggle(on: Boolean, onToggle: () -> Unit) {
    HoverLabel(stringResource(Res.string.song_chords_toggle)) {
        Row(
            modifier = Modifier.clickable(onClick = onToggle).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.song_chords),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = on,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(CHORD_PREVIEW_SCALE),
            )
        }
    }
}

/** A plain hover label, matching the ones the tabs use. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoverLabel(text: String, content: @Composable () -> Unit) {
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        content = content,
    )
}

/** A section marker offered for insertion. */
@Composable
private fun InsertChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Reminds the writer of the three things brackets can mean, in the colours the editor uses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyntaxLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.song_syntax),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        LegendChip("[Verse 1]", SectionInk.of(SongSectionKind.VERSE))
        LegendChip("{Chorus}", SectionInk.of(SongSectionKind.CHORUS))
        LegendChip("[G]lyric", MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(Res.string.song_syntax_chord_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendChip(text: String, ink: Color) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.5.sp,
        color = ink,
        modifier = Modifier
            .background(ink.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Colours what brackets mean while they are being typed: a verse header, a chorus header, and the
 * chords sitting inside the line. The three match the legend under the editor.
 */
@Composable
private fun rememberLyricsHighlight(): VisualTransformation {
    val verse = SectionInk.of(SongSectionKind.VERSE)
    val chorus = SectionInk.of(SongSectionKind.CHORUS)
    val chord = MaterialTheme.colorScheme.primary
    return remember(verse, chorus, chord) {
        VisualTransformation { text ->
            val annotated = buildAnnotatedString {
                text.text.split("\n").forEachIndexed { i, line ->
                    if (i > 0) append("\n")
                    if (ChordTransposer.isSectionHeader(line)) {
                        val ink = if (sectionKindOf(line.trim().trim('[', ']', '{', '}')) == SongSectionKind.CHORUS) {
                            chorus
                        } else {
                            verse
                        }
                        pushStyle(SpanStyle(color = ink, background = ink.copy(alpha = 0.16f), fontWeight = FontWeight.Bold))
                        append(line)
                        pop()
                    } else {
                        appendChordHighlighted(line, chord)
                    }
                }
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }
}

/** Appends [line] with each `[chord]` marker tinted, leaving the words plain. */
private fun AnnotatedString.Builder.appendChordHighlighted(line: String, ink: Color) {
    var cursor = 0
    Regex("\\[[^\\]]*\\]").findAll(line).forEach { match ->
        if (!ChordTransposer.isChord(match.value.substring(1, match.value.length - 1))) return@forEach
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        pushStyle(SpanStyle(color = ink, background = ink.copy(alpha = 0.14f), fontWeight = FontWeight.Bold))
        append(match.value)
        pop()
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}

/**
 * The clipboard's text, or null when it holds none.
 *
 * Read straight from AWT rather than through a Compose clipboard API: this runs inside a key
 * handler that has to decide, before the keystroke is consumed, whether the paste is a chord sheet.
 */
private fun clipboardText(): String? = SystemClipboard.paste()

private val LyricsFieldShape = RoundedCornerShape(10.dp)

/**
 * Dedicated multi-line, scrollable lyrics editor — kept separate from SettingsTextField
 * (which is tuned for compact single-line settings rows) so it can grow to fill the
 * available height with its own scrollbar and a monospaced face, which is what keeps a
 * chord over the syllable it was typed against.
 */
@Composable
private fun LyricsTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onPasteChordSheet: (String) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            // A pasted chords-over-lyrics sheet is converted on the way in; anything else pastes
            // as it always has, so this is invisible until it is wanted.
            .onPreviewKeyEvent { event ->
                val paste = event.type == KeyEventType.KeyDown &&
                    event.key == Key.V &&
                    (event.isCtrlPressed || event.isMetaPressed)
                val sheet = if (paste) clipboardText() else null
                if (sheet != null && ChordSheetImporter.looksLikeChordSheet(sheet)) {
                    onPasteChordSheet(sheet)
                    true
                } else {
                    false
                }
            }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, LyricsFieldShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LyricsFieldShape)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 18.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box {
                    if (placeholder != null && value.text.isEmpty()) {
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
