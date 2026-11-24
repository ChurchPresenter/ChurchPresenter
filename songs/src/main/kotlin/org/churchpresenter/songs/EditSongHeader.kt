package org.churchpresenter.songs

import androidx.compose.foundation.background
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import androidx.compose.ui.platform.testTag
import org.churchpresenter.sharedui.composables.tabMovesFocus
import org.churchpresenter.strings.generated.resources.song_comments
import org.churchpresenter.strings.generated.resources.song_comments_placeholder
import org.churchpresenter.strings.generated.resources.author
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.ccli_number
import org.churchpresenter.strings.generated.resources.composer
import org.churchpresenter.strings.generated.resources.duplicate_song_error
import org.churchpresenter.strings.generated.resources.song_book
import org.churchpresenter.strings.generated.resources.song_book_new
import org.churchpresenter.strings.generated.resources.song_book_none
import org.churchpresenter.strings.generated.resources.song_capo
import org.churchpresenter.strings.generated.resources.song_alias
import org.churchpresenter.strings.generated.resources.song_number
import org.churchpresenter.strings.generated.resources.song_tempo
import org.churchpresenter.strings.generated.resources.tune
import org.churchpresenter.strings.generated.resources.unit_bpm
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.field.Dropdown
import org.churchpresenter.hosanna.composables.field.TextField
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.model.MenuEntry
import androidx.compose.ui.graphics.vector.rememberVectorPainter

internal val EditSongCardShape = AppShape(9.dp)

/**
 * The editor's metadata: two dense rows of labelled cards -- the titles and song book, then the
 * number, credits and, when [tuning] is given, capo and tempo -- and the duplicate-song warning.
 */
@Composable
internal fun EditSongMetadata(
    state: EditSongState,
    names: List<String>,
    tuning: EditSongTuning?,
    songbooks: List<String>,
    isVisible: Boolean,
    isDuplicate: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
    names: List<String>,
    songbooks: List<String>,
    isVisible: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldCard(
            label = primaryTitleLabel(names[0].trim()),
            value = state.title,
            onValueChange = { state.editTitle(it) },
            weight = 2.4f,
        )
        // Every language that has a pane gets its own card, rather than one card following the
        // open pane: a song's titles are read together -- checking that the four languages are the
        // same song is the point of having them -- and a card that swaps its contents as the lyrics
        // pane changes shows one and hides the rest. A monolingual song still draws the single
        // Secondary card it always did, since it has exactly one pane.
        repeat(state.visibleTranslations) { slot ->
            FieldCard(
                label = translationTitleLabel(slot, state.languageName(slot, names)),
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
            label = stringResource(Res.string.song_alias),
            value = state.alias,
            onValueChange = { state.alias = it },
            weight = 0.8f,
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

/** A metadata field: its caption above, its value typed straight into the field. */
@Composable
private fun RowScope.FieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    weight: Float,
    modifier: Modifier = Modifier,
) {
    TextField(value, onValueChange, modifier.weight(weight), label = label)
}

/**
 * The song's comments, the full width under the editor: for the team, never presented. Up to
 * three lines show; Tab leaves the field rather than typing into it.
 */
@Composable
internal fun EditSongComments(notes: String, onNotesChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        TextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth().tabMovesFocus().testTag(EDIT_SONG_NOTES_TAG),
            label = stringResource(Res.string.song_comments),
            placeholder = stringResource(Res.string.song_comments_placeholder),
            multiline = true,
        )
    }
}

/** The editor's comments field. */
const val EDIT_SONG_NOTES_TAG = "editSongNotes"

/**
 * The song book card: the one metadata field that picks from what already exists rather than
 * accepting free text, with an escape hatch for naming a new book. *No song book* is always offered
 * first: the song is kept at the top of the songs folder, where imported songs already sit, so a
 * library with no song book folders can still take a new song.
 */
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
    var isAddingNew by remember(isVisible) { mutableStateOf(false) }
    val label = stringResource(Res.string.song_book)
    if (isAddingNew) {
        Row(
            modifier.weight(weight),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(songbook, onSongbookChange, Modifier.weight(1f), label = label)
            val cancel = stringResource(Res.string.cancel)
            Tooltip(cancel) {
                IconButton(
                    rememberVectorPainter(Icons.Default.Close),
                    cancel,
                    { onSongbookChange(originalSongbook); isAddingNew = false },
                    size = IconButtonSize.Small,
                    hierarchy = ButtonHierarchy.Tertiary,
                    intent = ButtonIntent.Neutral,
                )
            }
        }
    } else {
        // The library lists its top-level songs as "/"; here that is the first entry.
        val books = songbooks.filter { it.isNotBlank() && it != ROOT_SONGBOOK }
        Dropdown(
            entries = listOf(MenuEntry.Item(NO_SONGBOOK_KEY, stringResource(Res.string.song_book_none))) +
                books.map { MenuEntry.Item(it, it) } +
                listOf(MenuEntry.Divider, MenuEntry.Item(NEW_SONGBOOK_KEY, stringResource(Res.string.song_book_new))),
            selectedKey = songbook.ifBlank { NO_SONGBOOK_KEY },
            onSelect = { key ->
                when (key) {
                    NO_SONGBOOK_KEY -> onSongbookChange("")
                    NEW_SONGBOOK_KEY -> {
                        onSongbookChange("")
                        isAddingNew = true
                    }
                    else -> onSongbookChange(key)
                }
            },
            modifier = modifier.weight(weight),
            label = label,
        )
    }
}

// Keys no song book can have: a book name never starts with a NUL.
private const val NO_SONGBOOK_KEY = "\u0000none"
private const val NEW_SONGBOOK_KEY = "\u0000new"

/** Tempo: the number the stage monitor's metronome flashes at. */
@Composable
private fun RowScope.TempoCard(bpm: String, onBpmChange: (String) -> Unit, weight: Float) {
    TextField(
        bpm,
        onBpmChange,
        Modifier.weight(weight).guideTarget(GuideTargets.SONG_TEMPO),
        label = stringResource(Res.string.song_tempo),
        suffix = stringResource(Res.string.unit_bpm),
    )
}
