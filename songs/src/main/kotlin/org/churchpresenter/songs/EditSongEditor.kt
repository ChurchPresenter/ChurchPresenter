package org.churchpresenter.songs

import androidx.compose.foundation.ExperimentalFoundationApi
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.MAX_SONG_EXTRA_TRANSLATIONS
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.composables.ConditionalTooltipArea
import org.churchpresenter.sharedui.composables.PaneTab
import org.churchpresenter.sharedui.composables.PaneTabRow
import org.churchpresenter.sharedui.composables.SectionInk
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.sharedui.utils.defaultSongLanguageName
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
import org.churchpresenter.strings.generated.resources.song_language_name_hint
import org.churchpresenter.strings.generated.resources.song_pane_lyrics
import org.churchpresenter.strings.generated.resources.song_syntax
import org.churchpresenter.strings.generated.resources.song_syntax_chord_hint
import org.churchpresenter.strings.generated.resources.song_translation_label
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raisedHover
import org.jetbrains.compose.resources.stringResource

private const val CHORD_PREVIEW_SCALE = 0.75f
private val LANGUAGE_NAME_FIELD_WIDTH = 220.dp

/** Test handle for the open pane's language-name field. */
internal const val LANGUAGE_NAME_FIELD_TAG = "song_editor_language_name"

/**
 * The editor's left-hand column: the pane tabs with the background and chord controls, the open
 * pane's language name, the section markers, the lyrics box and the syntax legend.
 */
@Composable
internal fun EditSongEditorColumn(
    state: EditSongState,
    names: SongLanguageNames,
    showLanguageNames: Boolean,
    toolbar: @Composable () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        toolbar()

        if (showLanguageNames) {
            LanguageNameField(
                name = names.names[state.pane],
                placeholder = defaultSongLanguageName(state.pane),
                onNameChange = { value -> names.rename(state.pane, value) },
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
            )
        }

        SectionMarkerChips(state)

        LyricsTextField(
            value = state.paneValue,
            onValueChange = { state.setPaneValue(it) },
            onPasteChordSheet = { sheet ->
                state.setPaneValue(
                    insertSnippet(state.paneValue, ChordSheetImporter.convert(sheet), ownLine = false)
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
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

        SyntaxLegend(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** Pane tabs, the Background button the app draws in [backgroundButton], and the chord switch. */
@Composable
internal fun EditSongToolbar(
    state: EditSongState,
    names: SongLanguageNames,
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

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaneTabRow {
            val primaryName = names.names[0].trim()
                .ifBlank { stringResource(Res.string.song_pane_lyrics) }
            PaneTab(primaryName, state.pane == 0) { state.pane = 0 }
            repeat(state.visibleTranslations) { index ->
                val paneLabel = translationPaneLabel(index, state.languageName(index, names.names))
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
        Spacer(Modifier.weight(1f))
        backgroundButton(EditorBackgroundButtonState(state, sectionSlots, scopeNames, onApplyBackgroundToSongbook))
        ChordsToggle(on = showChords, onToggle = onToggleChords)
    }
}

/** Section markers, inserted at the caret. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionMarkerChips(state: EditSongState) {
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
                state.setPaneValue(insertSnippet(state.paneValue, marker, ownLine = true))
            }
        }
        // Deliberately not in SONG_SECTION_MARKERS: that list is what the app treats as a section,
        // and a break is the opposite of one.
        InsertChip(stringResource(Res.string.song_insert_slide_break)) {
            state.setPaneValue(insertSnippet(state.paneValue, ChordTransposer.SLIDE_BREAK, ownLine = true))
        }
    }
}

/**
 * The open pane's language name: a small card like the metadata ones, with a line saying the name
 * is not this song's alone. Blank shows [placeholder], which is what an unnamed language is called.
 */
@Composable
private fun LanguageNameField(
    name: String,
    placeholder: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The whole card takes the click, not just the line of text inside it: an empty field is one
    // grey placeholder word in a box three times its height, and a click on the caption or the
    // padding around it otherwise did nothing -- no cursor, nowhere to type.
    val focus = remember { FocusRequester() }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .width(LANGUAGE_NAME_FIELD_WIDTH)
                .clip(EditSongCardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, EditSongCardShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, EditSongCardShape)
                .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
                .pointerHoverIcon(PointerIcon.Text)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            CardLabel(stringResource(Res.string.song_translation_label))
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = fieldValueStyle(),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(LANGUAGE_NAME_FIELD_TAG),
                decorationBox = { field ->
                    Box {
                        if (name.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = fieldValueStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        field()
                    }
                },
            )
        }
        Text(
            text = stringResource(Res.string.song_language_name_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The switch that decides whether the preview shows chords at all. */
@Composable
private fun ChordsToggle(on: Boolean, onToggle: () -> Unit) {
    HoverLabel(stringResource(Res.string.song_chords_toggle)) {
        Row(
            modifier = Modifier
                .guideTarget(GuideTargets.SONG_CHORDS_SWITCH)
                .clickable(onClick = onToggle)
                .padding(start = 6.dp),
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
            RaisedSwitch(
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
                shape = AppShape(6.dp),
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
    val palette = elevationPalette()
    Text(
        text = label,
        fontSize = 11.sp,
        color = palette.key.ink,
        modifier = Modifier
            .raisedHover(AppShape(6.dp), palette.key, palette, lift = 2.dp)
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
            .background(ink.copy(alpha = 0.14f), AppShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
