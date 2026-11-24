package org.churchpresenter.songs

import org.churchpresenter.theme.semantic
import org.churchpresenter.songchords.SecondVoice
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.MAX_SONG_EXTRA_TRANSLATIONS
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.composables.PaneTab
import org.churchpresenter.sharedui.composables.PaneTabRow
import org.churchpresenter.sharedui.composables.SectionInk
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.songchords.ChordSheetImporter
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.enter_lyrics_here
import org.churchpresenter.strings.generated.resources.enter_secondary_lyrics_here
import org.churchpresenter.strings.generated.resources.enter_translation_lyrics_here
import org.churchpresenter.strings.generated.resources.song_add_translation
import org.churchpresenter.strings.generated.resources.song_background_untitled_section
import org.churchpresenter.strings.generated.resources.song_background_whole_song
import org.churchpresenter.strings.generated.resources.song_chords
import org.churchpresenter.strings.generated.resources.song_chords_toggle
import org.churchpresenter.strings.generated.resources.song_insert_section
import org.churchpresenter.strings.generated.resources.song_insert_slide_break
import org.churchpresenter.strings.generated.resources.song_pane_lyrics
import org.churchpresenter.strings.generated.resources.song_syntax
import org.churchpresenter.strings.generated.resources.song_syntax_chord_hint
import org.churchpresenter.strings.generated.resources.song_insert_second_voice
import org.churchpresenter.strings.generated.resources.song_insert_second_voice_hint
import org.churchpresenter.strings.generated.resources.song_insert_third_voice
import org.churchpresenter.strings.generated.resources.song_insert_color
import org.churchpresenter.strings.generated.resources.song_insert_color_hint
import org.churchpresenter.songchords.VoiceKey
import org.churchpresenter.sharedui.composables.ColorPickerDialog
import org.churchpresenter.strings.generated.resources.song_insert_third_voice_hint
import org.churchpresenter.strings.generated.resources.song_language_names_where
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.hosanna.composables.chip.ActionChip
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.selection.Switch
import org.churchpresenter.hosanna.composables.model.ChipSize
import org.churchpresenter.hosanna.composables.model.SwitchLayout
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

/**
 * The editor's left-hand column: the pane tabs with the background and chord controls, the section
 * markers, the lyrics box and the syntax legend.
 */
@Composable
internal fun EditSongEditorColumn(
    state: EditSongState,
    toolbar: @Composable () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        toolbar()

        // Where the old name card was: names are install-wide, so they are renamed from the Songs tab.
        Text(
            text = stringResource(Res.string.song_language_names_where),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 8.dp),
        )

        SectionMarkerChips(state)

        LyricsTextField(
            value = state.paneValue,
            onValueChange = { state.setPaneValue(it) },
            onPasteChordSheet = { sheet ->
                state.setPaneValue(
                    insertSnippet(state.paneValue, ChordSheetImporter.convert(sheet), ownLine = false)
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 14.dp)
                .guideTarget(GuideTargets.SONG_LYRICS),
            placeholder = {
                Text(
                    when (state.pane) {
                        0 -> stringResource(Res.string.enter_lyrics_here)
                        1 -> stringResource(Res.string.enter_secondary_lyrics_here)
                        else -> stringResource(Res.string.enter_translation_lyrics_here, state.pane + 1)
                    }
                )
            },
            visualTransformation = rememberLyricsHighlight(),
        )

        SyntaxLegend(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        )
    }
}

/** Pane tabs, the Background button the app draws in [backgroundButton], and the chord switch. */
@Composable
internal fun EditSongToolbar(
    state: EditSongState,
    names: List<String>,
    backgroundButton: @Composable (SongBackgroundButtonState) -> Unit,
    onApplyBackgroundToSongbook: ((String, SongBackground, SongBackground) -> Unit)?,
    showChords: Boolean,
    onToggleChords: () -> Unit,
) {
    // The sections a background can be pinned to, read back out of the lyrics box on every edit so
    // the list follows what is written there. Only the primary lyrics: a section is one section in
    // both languages, and its background belongs to the section rather than to a translation.
    val sectionSlots = remember(state.lyrics.text) { sectionBackgroundSlots(state.lyrics.text.split("\n")) }
    val untitledSection = stringResource(Res.string.song_background_untitled_section)
    val scopeNames = listOf(stringResource(Res.string.song_background_whole_song)) +
        sectionSlots.map { it.label.ifBlank { untitledSection } }

    // One line: the tabs take what the background button and the chords switch leave, and scroll
    // sideways when there are more languages than fit.
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            PaneTabRow(Modifier.horizontalScroll(rememberScrollState())) {
                val primaryName = names[0].trim()
                    .ifBlank { stringResource(Res.string.song_pane_lyrics) }
                PaneTab(primaryName, state.pane == 0) { state.pane = 0 }
                repeat(state.visibleTranslations) { index ->
                    val paneLabel = translationPaneLabel(index, state.languageName(index, names))
                    PaneTab(paneLabel, state.pane == index + 1) {
                        state.pane = index + 1
                    }
                }
                if (state.visibleTranslations < MAX_SONG_EXTRA_TRANSLATIONS) {
                    PaneTab(
                        stringResource(Res.string.song_add_translation),
                        selected = false,
                        modifier = Modifier.guideTarget(GuideTargets.ADD_SONG_LANGUAGE),
                    ) {
                        state.addTranslationPane()
                    }
                }
            }
        }
        // Kept together at the end of the line.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.guideTarget(GuideTargets.SONG_BACKGROUND)) {
                backgroundButton(
                    EditorBackgroundButtonState(state, sectionSlots, scopeNames, onApplyBackgroundToSongbook),
                )
            }
            ChordsToggle(on = showChords, onToggle = onToggleChords)
        }
    }
}

/** Section markers, inserted at the caret. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionMarkerChips(state: EditSongState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
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
                state.setPaneValue(insertSnippet(state.paneValue, marker, ownLine = true))
            }
        }
        // Deliberately not in SONG_SECTION_MARKERS: that list is what the app treats as a section,
        // and a break is the opposite of one.
        InsertChip(stringResource(Res.string.song_insert_slide_break)) {
            state.setPaneValue(insertSnippet(state.paneValue, ChordTransposer.SLIDE_BREAK, ownLine = true))
        }
        HoverLabel(stringResource(Res.string.song_insert_second_voice_hint)) {
            InsertChip(stringResource(Res.string.song_insert_second_voice)) {
                state.setPaneValue(toggleVoice(state.paneValue, VoiceKey(SecondVoice.SECOND)))
            }
        }
        HoverLabel(stringResource(Res.string.song_insert_third_voice_hint)) {
            InsertChip(stringResource(Res.string.song_insert_third_voice)) {
                state.setPaneValue(toggleVoice(state.paneValue, VoiceKey(SecondVoice.THIRD)))
            }
        }
        ColorMarkChip(state)
    }
}

/**
 * The Color… button: words or lines in a colour of their own, for a part no voice covers. On lines
 * already in one colour it takes the colour off; otherwise it asks for a colour and marks the
 * selected words, or the lines the cursor is on, with it.
 */
@Composable
private fun ColorMarkChip(state: EditSongState) {
    var picking by remember { mutableStateOf(false) }
    var lastColor by remember { mutableStateOf(DEFAULT_MARK_COLOR) }
    HoverLabel(stringResource(Res.string.song_insert_color_hint)) {
        InsertChip(stringResource(Res.string.song_insert_color)) {
            val current = lineColorAt(state.paneValue)
            if (current != null) {
                state.setPaneValue(toggleVoice(state.paneValue, current))
            } else {
                picking = true
            }
        }
    }
    if (picking) {
        ColorPickerDialog(
            initialHex = lastColor,
            onDismiss = { picking = false },
            onColorSelected = { hex ->
                lastColor = hex
                picking = false
                state.setPaneValue(toggleVoice(state.paneValue, VoiceKey.color(hex)))
            },
        )
    }
}

/** The colour the Color… button offers first, until one is picked: a rose that reads on dark and light. */
private const val DEFAULT_MARK_COLOR = "#E91E63"

/** The switch that decides whether the preview shows chords at all. */
@Composable
private fun ChordsToggle(on: Boolean, onToggle: () -> Unit) {
    Tooltip(stringResource(Res.string.song_chords_toggle), Modifier.guideTarget(GuideTargets.SONG_CHORDS_SWITCH)) {
        Switch(
            checked = on,
            onCheckedChange = { onToggle() },
            label = stringResource(Res.string.song_chords),
            layout = SwitchLayout.Inline,
        )
    }
}

/** A plain hover label, matching the ones the tabs use. */
@Composable
private fun HoverLabel(text: String, content: @Composable () -> Unit) {
    Tooltip(text, content = content)
}

/** A section marker offered for insertion. */
@Composable
private fun InsertChip(label: String, onClick: () -> Unit) {
    ActionChip(label, onClick, size = ChipSize.Small)
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
        LegendChip("2>line", MaterialTheme.colorScheme.tertiary)
        LegendChip("2>(words)", MaterialTheme.colorScheme.tertiary)
        LegendChip("3>line", MaterialTheme.semantic.success)
        LegendChip("#hex>line", MaterialTheme.colorScheme.secondary)
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
            .background(ink.copy(alpha = 0.14f), AppShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
