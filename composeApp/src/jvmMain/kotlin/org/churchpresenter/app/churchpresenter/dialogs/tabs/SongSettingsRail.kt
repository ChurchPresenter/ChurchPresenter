package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.bilingual_layout
import churchpresenter.composeapp.generated.resources.bilingual_left_right
import churchpresenter.composeapp.generated.resources.bilingual_top_bottom
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.enabled
import churchpresenter.composeapp.generated.resources.end_of_song_spacing
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.milliseconds_suffix
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.song_auto_repeat_chorus
import churchpresenter.composeapp.generated.resources.song_language_bilingual
import churchpresenter.composeapp.generated.resources.song_language_single
import churchpresenter.composeapp.generated.resources.song_languages
import churchpresenter.composeapp.generated.resources.song_lyrics_layout
import churchpresenter.composeapp.generated.resources.song_title_slide
import churchpresenter.composeapp.generated.resources.song_transition_and_markers
import churchpresenter.composeapp.generated.resources.text_margins
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.vertical_alignment
import churchpresenter.composeapp.generated.resources.word_wrap
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The Song settings tab's rail: the four cards of slide-wide settings standing beside the styling
 * pane -- the title slide, the lyrics' layout, transitions and markers, and the margins.
 *
 * Beside [SongSettingsTab] rather than in it. They are that tab's own rail and nothing else's, but
 * the file holding the tab was at the ceiling on how many functions one file may carry, and these
 * four are the part of it that reads as a group.
 */

/** Whether a song opens with a slide naming it, and where on the screen that slide's text sits. */
@Composable
internal fun SongTitleSlideSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /**
     * Off for an output that draws a band, which keeps the block at its own bottom whatever this
     * says -- so the row would be a control that does nothing.
     *
     * Only the per-output Customize dialog ever passes false: the global tab styles both shapes at
     * once and cannot know which one a given screen is.
     */
    showVerticalAlignment: Boolean = true,
) {
    SettingsSection(title = stringResource(Res.string.song_title_slide)) {
        LabeledCheckbox(
            checked = settings.songSettings.titleSlideEnabled,
            onCheckedChange = { on ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleSlideEnabled = on)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.enabled),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("song_titleSlideEnabled"),
            style = MaterialTheme.typography.bodyMedium,
        )
        // The whole block -- number, title and credits -- moves as one; the lower third keeps it
        // at the bottom of the band regardless, which is what [showVerticalAlignment] is for.
        if (showVerticalAlignment) Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (settings.songSettings.titleSlideEnabled) 1f else DISABLED_ALPHA),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.vertical_alignment).removeSuffix(":"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.testTag("song_titleSlideVerticalAlignment")) {
                VerticalAlignmentButtons(
                    selectedAlignment = settings.songSettings.titleSlideVerticalAlignment,
                    onAlignmentChange = { value ->
                        if (settings.songSettings.titleSlideEnabled) {
                            onSettingsChange { s ->
                                s.copy(songSettings = s.songSettings.copy(titleSlideVerticalAlignment = value))
                            }
                        }
                    },
                    topValue = Constants.TOP,
                    middleValue = Constants.MIDDLE,
                    bottomValue = Constants.BOTTOM,
                )
            }
        }
    }
}

/** How the lyrics sit on the slide, and how many languages they are shown in. */
@Composable
internal fun SongLyricsLayoutSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val song = settings.songSettings
    // Written to each output's own song mode rather than to SongSettings: the song-level language
    // fields are overridden by that mode at every real call site, so a control writing them would
    // restrict nothing. See SongOutputLanguage.kt.
    val bilingual = settings.songIsBilingual
    SettingsSection(title = stringResource(Res.string.song_lyrics_layout)) {
        LabeledCheckbox(
            checked = song.wordWrap,
            onCheckedChange = { on ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(wordWrap = on)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.word_wrap),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Off means "present the sections as the file has them", which is the only way to place a
        // chorus deliberately — before verse 1, or after verse 2 alone.
        LabeledCheckbox(
            checked = song.autoRepeatChorus,
            onCheckedChange = { on ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(autoRepeatChorus = on)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.song_auto_repeat_chorus),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("song_autoRepeatChorus"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.vertical_alignment).removeSuffix(":"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            VerticalAlignmentButtons(
                selectedAlignment = song.lyricsAlignment,
                onAlignmentChange = { value ->
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(lyricsAlignment = value)) }
                },
                topValue = Constants.TOP,
                middleValue = Constants.MIDDLE,
                bottomValue = Constants.BOTTOM,
            )
        }
        ControlColumn(stringResource(Res.string.song_languages), Modifier.fillMaxWidth()) {
            SegmentedButton(
                items = listOf(
                    SegmentedButtonItem(false, stringResource(Res.string.song_language_single)),
                    SegmentedButtonItem(true, stringResource(Res.string.song_language_bilingual)),
                ),
                selectedValue = bilingual,
                onValueChange = { wantsBoth ->
                    onSettingsChange { s -> s.withSongBilingual(wantsBoth) }
                },
                buttonWidth = 120.dp,
                buttonHeight = 32.dp,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
            )
        }
        // Only meaningful with two languages on screen, so it follows the switch above rather than
        // standing there offering a choice that changes nothing.
        if (bilingual) {
            ControlColumn(stringResource(Res.string.bilingual_layout), Modifier.fillMaxWidth()) {
                SegmentedButton(
                    items = listOf(
                        SegmentedButtonItem(
                            Constants.BILINGUAL_SIDE_BY_SIDE,
                            stringResource(Res.string.bilingual_left_right),
                        ),
                        SegmentedButtonItem(
                            Constants.BILINGUAL_TOP_BOTTOM,
                            stringResource(Res.string.bilingual_top_bottom),
                        ),
                    ),
                    selectedValue = song.bilingualLayout,
                    onValueChange = { value ->
                        onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(bilingualLayout = value)) }
                    },
                    buttonWidth = 120.dp,
                    buttonHeight = 32.dp,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                )
            }
        }
    }
}

/** How a slide arrives and leaves, and how far the end-of-song marker sits from the last line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongTransitionSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val song = settings.songSettings
    val msSuffix = stringResource(Res.string.milliseconds_suffix)
    SettingsSection(title = stringResource(Res.string.song_transition_and_markers)) {
        ControlColumn(stringResource(Res.string.transition_duration), Modifier.fillMaxWidth()) {
            SlimSlider(
                value = song.transitionDuration,
                onValueChange = { raw ->
                    val snapped = (raw / TRANSITION_STEP_MS).toInt() * TRANSITION_STEP_MS
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(transitionDuration = snapped))
                    }
                },
                valueRange = TRANSITION_MIN_MS..TRANSITION_MAX_MS,
                modifier = Modifier.fillMaxWidth(),
                trailingLabel = "${song.transitionDuration.toInt()}$msSuffix",
            )
        }
        // Wraps rather than one hard row: "Fade In / Fade Out / Crossfade" is three short
        // labels in English and three long ones in most translations -- in Russian the first
        // two took the whole width and squeezed the third into a single-character column,
        // which drew its label vertically.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledCheckbox(
                checked = song.fadeIn,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fadeIn = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_in),
                style = MaterialTheme.typography.bodySmall,
            )
            LabeledCheckbox(
                checked = song.fadeOut,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(fadeOut = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.fade_out),
                style = MaterialTheme.typography.bodySmall,
            )
            LabeledCheckbox(
                checked = song.crossfade,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(crossfade = it)) }
                },
                controlModifier = Modifier.size(24.dp),
                label = stringResource(Res.string.animation_crossfade),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = song.showEndOfSongIndicator,
                onCheckedChange = {
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(showEndOfSongIndicator = it)) }
                },
                modifier = Modifier.size(24.dp).testTag("song_showEndOfSongIndicator"),
            )
            Text(
                text = stringResource(Res.string.end_of_song_spacing).removeSuffix(":"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            NumberSettingsTextField(
                initialText = song.endOfSongIndicatorSpacing,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(songSettings = s.songSettings.copy(endOfSongIndicatorSpacing = value))
                    }
                },
                range = 0..END_OF_SONG_MAX,
            )
        }
    }
}

/** The four margins, as a plain grid; the preview above shows what they do to the text. */
@Composable
internal fun SongMarginsSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val song = settings.songSettings
    fun field(label: String, value: Int, apply: (SongSettings, Int) -> SongSettings): @Composable () -> Unit = {
        ControlColumn(label) {
            NumberSettingsTextField(
                modifier = Modifier.fillMaxWidth(),
                initialText = value,
                onValueChange = { typed ->
                    onSettingsChange { s -> s.copy(songSettings = apply(s.songSettings, typed)) }
                },
                range = 0..MARGIN_MAX,
            )
        }
    }
    SettingsSection(title = stringResource(Res.string.text_margins)) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    field(stringResource(Res.string.top), song.marginTop) { s, v -> s.copy(marginTop = v) }()
                }
                Box(Modifier.weight(1f)) {
                    field(stringResource(Res.string.left), song.marginLeft) { s, v -> s.copy(marginLeft = v) }()
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    field(stringResource(Res.string.right), song.marginRight) { s, v -> s.copy(marginRight = v) }()
                }
                Box(Modifier.weight(1f)) {
                    field(stringResource(Res.string.bottom), song.marginBottom) { s, v -> s.copy(marginBottom = v) }()
                }
            }
        }
    }
}

private const val MARGIN_MAX = 500
private const val END_OF_SONG_MAX = 20
private const val TRANSITION_MIN_MS = 100f
private const val TRANSITION_MAX_MS = 2000f
private const val TRANSITION_STEP_MS = 50f
private const val DISABLED_ALPHA = 0.38f
