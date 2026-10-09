package org.churchpresenter.songs

import androidx.compose.foundation.background
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_new
import org.churchpresenter.strings.generated.resources.author
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.ccli_number
import org.churchpresenter.strings.generated.resources.composer
import org.churchpresenter.strings.generated.resources.duplicate_song_error
import org.churchpresenter.strings.generated.resources.song_book
import org.churchpresenter.strings.generated.resources.song_capo
import org.churchpresenter.strings.generated.resources.song_number
import org.churchpresenter.strings.generated.resources.song_tempo
import org.churchpresenter.strings.generated.resources.tune
import org.churchpresenter.strings.generated.resources.unit_bpm
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyIconButton
import org.jetbrains.compose.resources.stringResource

internal val EditSongCardShape = AppShape(9.dp)

/**
 * The editor's metadata: two dense rows of labelled cards -- the titles and song book, then the
 * number, credits and, when [tuning] is given, capo and tempo -- and the duplicate-song warning.
 */
@Composable
internal fun EditSongMetadata(
    state: EditSongState,
    names: SongLanguageNames,
    tuning: EditSongTuning?,
    songbooks: List<String>,
    isVisible: Boolean,
    isDuplicate: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EditSongTitleRow(state, names, songbooks, isVisible)
        EditSongDetailsRow(state, tuning)
        if (isDuplicate) {
            Text(
                text = stringResource(Res.string.duplicate_song_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EditSongTitleRow(
    state: EditSongState,
    names: SongLanguageNames,
    songbooks: List<String>,
    isVisible: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldCard(
            label = primaryTitleLabel(names.names[0].trim()),
            value = state.title,
            onValueChange = { state.editTitle(it) },
            weight = 2.4f,
            emphasis = true,
        )
        // Every language that has a pane gets its own card, rather than one card following the
        // open pane: a song's titles are read together -- checking that the four languages are the
        // same song is the point of having them -- and a card that swaps its contents as the lyrics
        // pane changes shows one and hides the rest. A monolingual song still draws the single
        // Secondary card it always did, since it has exactly one pane.
        repeat(state.visibleTranslations) { slot ->
            FieldCard(
                label = translationTitleLabel(slot, state.languageName(slot, names.names)),
                value = state.translations[slot].title,
                onValueChange = { value -> state.editTranslationTitle(slot, value) },
                weight = 1f,
            )
        }
        SongbookCard(
            songbook = state.songbook,
            songbooks = songbooks,
            isVisible = isVisible,
            originalSongbook = state.originalSongbook,
            onSongbookChange = { state.songbook = it },
            weight = 1f,
            modifier = Modifier.guideTarget(GuideTargets.SONG_SONGBOOK),
        )
    }
}

@Composable
private fun EditSongDetailsRow(state: EditSongState, tuning: EditSongTuning?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldCard(
            label = stringResource(Res.string.song_number),
            value = state.number,
            onValueChange = { v -> if (v.all { it.isDigit() }) state.number = v },
            weight = 0.8f,
            modifier = Modifier.guideTarget(GuideTargets.SONG_NUMBER),
        )
        FieldCard(
            label = stringResource(Res.string.author),
            value = state.author,
            onValueChange = { state.author = it },
            weight = 1.6f,
            modifier = Modifier.guideTarget(GuideTargets.SONG_AUTHOR),
        )
        FieldCard(
            label = stringResource(Res.string.composer),
            value = state.composer,
            onValueChange = { state.composer = it },
            weight = 1.6f,
            modifier = Modifier.guideTarget(GuideTargets.SONG_COMPOSER),
        )
        FieldCard(
            label = stringResource(Res.string.ccli_number),
            value = state.ccli,
            onValueChange = { state.ccli = it },
            weight = 1f,
            modifier = Modifier.guideTarget(GuideTargets.SONG_CCLI),
        )
        FieldCard(
            label = stringResource(Res.string.tune),
            value = state.tune,
            onValueChange = { state.tune = it },
            weight = 0.8f,
            modifier = Modifier.guideTarget(GuideTargets.SONG_TUNE),
        )
        if (tuning != null) {
            FieldCard(
                label = stringResource(Res.string.song_capo),
                value = tuning.capo,
                onValueChange = { tuning.editCapo(it) },
                weight = 0.55f,
                modifier = Modifier.guideTarget(GuideTargets.SONG_CAPO),
            )
            TempoCard(
                bpm = tuning.bpm,
                onBpmChange = { tuning.editBpm(it) },
                weight = 0.9f,
            )
        }
    }
}

/**
 * The one type style every metadata card's value is set in.
 *
 * The line height is stated rather than inherited because the cards sit in a single row and are
 * read across: a value inheriting a taller line box than its neighbours — which is what happens
 * when a card puts its value next to an icon or a unit — sits visibly off the line they share.
 */
@Composable
internal fun fieldValueStyle(emphasis: Boolean = false): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    color = MaterialTheme.colorScheme.onSurface,
    // One size for every card, emphasis carried by the weight alone. The song title used to be set
    // two points larger, and it shares its row with up to three translation titles and the song
    // book -- five cards where there used to be three, and the largest text in the narrowest space.
    fontSize = 13.5.sp,
    fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
    lineHeight = 19.sp,
)

/** The tiny uppercase caption that names a metadata card. */
@Composable
internal fun CardLabel(text: String) {
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .weight(weight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, EditSongCardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, EditSongCardShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        CardLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = fieldValueStyle(emphasis),
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var isAddingNew by remember(isVisible) { mutableStateOf(false) }

    val cardModifier = Modifier
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, EditSongCardShape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, EditSongCardShape)
        .padding(horizontal = 11.dp, vertical = 6.dp)

    if (isAddingNew) {
        Column(
            modifier = modifier.weight(weight).then(cardModifier),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            CardLabel(stringResource(Res.string.song_book))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = songbook,
                    onValueChange = onSongbookChange,
                    singleLine = true,
                    textStyle = fieldValueStyle(),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                KeyIconButton(
                    onClick = { onSongbookChange(originalSongbook); isAddingNew = false },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.cancel),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    } else {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = modifier.weight(weight),
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
                        style = fieldValueStyle(),
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
            .guideTarget(GuideTargets.SONG_TEMPO)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, EditSongCardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, EditSongCardShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        CardLabel(stringResource(Res.string.song_tempo))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicTextField(
                value = bpm,
                onValueChange = onBpmChange,
                singleLine = true,
                textStyle = fieldValueStyle(),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(30.dp),
            )
            // Same line box as the value beside it, so the two sit on one line rather than the
            // taller default pushing the number off the row's baseline.
            Text(
                text = stringResource(Res.string.unit_bpm),
                style = fieldValueStyle().copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
